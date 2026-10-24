package dev.studios.samimine.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.studios.samimine.app.navigation.*
import dev.studios.samimine.application.usecase.EvaluationUseCases
import dev.studios.samimine.domain.evaluation.pvt.*
import dev.studios.samimine.domain.evaluation.cognitive.CognitiveTrialEngine
import dev.studios.samimine.domain.evaluation.cognitive.TimedAssociationEngine
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.repository.EvaluationRepository
import dev.studios.samimine.domain.repository.CognitiveTestRepository
import dev.studios.samimine.domain.repository.BatteryRepository
import dev.studios.samimine.domain.repository.GuidedBatteryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import java.util.UUID

enum class PvtPhase { WAITING, SIGNAL, SAVING, AWAITING_CONTINUE, INTERRUPTED }
/** Estado de UI en memoria; Room conserva las sesiones, no el destino ni el perfil seleccionado. */
data class AppState(
    val destination: Destination = Destination.IDENTIFICATION,
    val selectedTab: WorkerTab = WorkerTab.HOME,
    val activityOrigin: WorkerTab = WorkerTab.HOME,
    val ready: Boolean = false, val busy: Boolean = false,
    val worker: Worker? = null, val shift: Shift? = null,
    val evaluation: Evaluation? = null,
    val history: List<Evaluation> = emptyList(),
    val phase: PvtPhase = PvtPhase.WAITING, val attemptNumber: Int = 1,
    val error: String? = null, val resultFromHistory: Boolean = false,
    val cognitiveHistory: List<CognitiveTestSession> = emptyList(),
    val cognitiveSession: CognitiveTestSession? = null,
    val cognitiveType: CognitiveTestType? = null,
    val cognitiveTrials: List<CognitiveTrial> = emptyList(),
    val cognitiveTrialPosition: Int = 0,
    val cognitiveFeedback: String? = null,
    val cognitivePhase: CognitivePhase = CognitivePhase.INSTRUCTIONS,
    val stimulusVisible: Boolean = true,
    val instrument: InstrumentDefinition? = null,
    val selfReport: SelfReport? = null,
    val selfReportHistory: List<SelfReport> = emptyList(),
    val assessment: AssessmentSession? = null,
    val assessmentHistory: List<AssessmentSession> = emptyList(),
    val assessmentReports: List<SelfReport> = emptyList(),
    val briefActive: Boolean = false,
    val batteryDefinition: BatteryDefinition? = null,
    val batteryMessage: String? = null,
    val guidedRun: GuidedBatteryRun? = null,
    val guidedRuns: List<GuidedBatteryRun> = emptyList(),
    val guidedActive: Boolean = false,
    val guidedDetail: Boolean = false,
)

/**
 * Coordina navegación, temporizadores y almacenamiento de las cuatro demostraciones.
 * Las pantallas envían eventos; los motores resuelven ensayos y los repositorios los persisten.
 * Las entradas se procesan en el contexto principal de UI; los motores no son thread-safe.
 * No reanuda pruebas tras interrupciones ni restaura AppState después de morir el proceso.
 */
class SamiMineViewModel(
    private val repository: EvaluationRepository,
    private val useCases: EvaluationUseCases,
    private val clock: MonotonicClock,
    private val cognitiveRepository: CognitiveTestRepository? = repository as? CognitiveTestRepository,
    private val cognitiveUptimeMillis: () -> Long = { android.os.SystemClock.uptimeMillis() },
    private val batteryRepository: BatteryRepository? = repository as? BatteryRepository,
    val instruments: List<InstrumentDefinition> = InstrumentCatalog.definitions,
    private val organizationId: String = "local-demo",
    private val batteryConfigLoader: suspend () -> Pair<BatteryDefinition?, String?> = { null to "Conéctate para descargar una batería publicada." },
) : ViewModel() {
    private val guidedRepository = repository as? GuidedBatteryRepository
    private var guidedHistoryJob: Job? = null
    private var pauseGuidedRequested = false
    private val mutableState = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    private var engine: PvtEngine? = null
    private var timer: Job? = null
    private var historyJob: Job? = null
    private var retryAction: (suspend () -> Unit)? = null
    private var interruptionRequested: String? = null
    private var signalBound = false
    private var foreground = true
    private var cognitiveResolved = true
    private var cognitiveEngine: CognitiveTrialEngine? = null
    private var cognitiveSessionId: String? = null
    private var cognitiveInterruptionRequested: String? = null
    private var technicalInterruption = false
    private var associationEngine: TimedAssociationEngine? = null
    private var deadlineTimer: Job? = null
    private var associationExpired = false
    private var nBackPending: CognitiveTrialRecord? = null
    private var referenceBound = false
    private var frameWatchdog: Job? = null

    init { operation { repository.recoverInterrupted(); cognitiveRepository?.recoverCognitiveTests(); batteryRepository?.recoverAssessments(); guidedRepository?.recoverGuidedBatteries(); mutableState.update { it.copy(ready = true) } } }

    /**
     * Serializa escrituras mediante busy y conserva la acción original para reintentar.
     * UUID, fechas y registros deben prepararse antes de esta llamada para no cambiarlos
     * al repetirla. Una interrupción pendiente se persiste al terminar sin error.
     */
    private fun operation(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        retryAction = action
        viewModelScope.launch {
            try {
                action()
                retryAction = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                timer?.cancel()
                mutableState.update { it.copy(error = "No se pudo acceder al almacenamiento. Tus datos no se han confirmado. Reintenta para continuar.") }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
            if (state.value.error == null && interruptionRequested != null) persistInterruption()
            else if (state.value.error == null && cognitiveInterruptionRequested != null) persistCognitiveInterruption()
            else if (state.value.error == null && associationExpired) awaitAssociationConfirmation()
            else if (state.value.error == null && pauseGuidedRequested) pauseGuided()
        }
    }

    fun retry() { retryAction?.let { operation(it) } }
    /** Persist interruption before releasing a user-scoped ViewModel on logout. */
    suspend fun prepareSignOut(): Boolean {
        withTimeoutOrNull(15_000) { state.first { !it.busy } } ?: return false
        if (state.value.error != null) return false
        onBackground()
        if (state.value.assessment?.status == "IN_PROGRESS" && state.value.briefActive) {
            val current = state.value
            operation { batteryRepository?.interruptAssessment(current.assessment!!.id, current.worker!!.id, "El usuario cerró sesión") }
        }
        withTimeoutOrNull(15_000) { state.first { !it.busy } } ?: return false
        val guided = state.value.guidedRuns.firstOrNull { !it.terminal }
        if (guided != null) {
            try { guidedRepository?.setGuidedState(guided.id, guided.workerId, BatteryRunStatus.PAUSED) }
            catch (_: Exception) { return false }
        }
        return state.value.error == null
    }
    fun selectWorker(worker: Worker) {
        if (!state.value.ready || state.value.busy) return
        historyJob?.cancel()
        mutableState.update { it.copy(worker = worker, shift = null, history = emptyList(), evaluation = null, cognitiveHistory = emptyList(),
            selfReportHistory = emptyList(), assessmentHistory = emptyList(), assessment = null, selfReport = null, briefActive = false,
            guidedRun = null, guidedRuns = emptyList(), guidedActive = false, selectedTab = WorkerTab.HOME, activityOrigin = WorkerTab.HOME, destination = Destination.SHIFT) }
        guidedHistoryJob?.cancel()
        guidedHistoryJob = viewModelScope.launch {
            guidedRepository?.guidedHistory(worker.id)?.collect { runs -> mutableState.update { it.copy(guidedRuns = runs) } }
        }
        refreshBatteryDefinition()
    }
    fun selectShift(shift: Shift) {
        if (state.value.busy || state.value.error != null || !state.value.ready) return
        val pending = state.value.guidedRuns.firstOrNull { !it.terminal }
        if (pending != null && pending.shift != shift) {
            mutableState.update { it.copy(shift = pending.shift, guidedRun = pending, guidedActive = false,
                batteryMessage = "Continúa o abandona la batería pendiente antes de cambiar de turno.", destination = Destination.BATTERY_OVERVIEW) }
            return
        }
        mutableState.update { it.copy(shift = shift, selectedTab = WorkerTab.HOME, activityOrigin = WorkerTab.HOME, destination = Destination.HOME) }
    }
    fun instructions() {
        if (state.value.busy || state.value.error != null) return
        mutableState.update { it.copy(destination = Destination.INSTRUCTIONS, activityOrigin = it.selectedTab) }
    }
    /** Return from an activity to its launching tab, preserving brief interruption semantics. */
    fun home() = returnToTab(state.value.activityOrigin)

    fun selectTab(tab: WorkerTab) {
        val current = state.value
        if (!current.destination.showsWorkerNavigation) return
        returnToTab(tab)
    }

    private fun returnToTab(tab: WorkerTab) {
        val current = state.value
        if (!current.ready || current.busy || current.error != null || current.worker == null || current.shift == null) return
        if (current.guidedActive) { pauseGuided(); return }
        val navigate = {
            historyJob?.cancel()
            mutableState.update { it.copy(destination = tab.destination, selectedTab = tab, activityOrigin = tab,
                resultFromHistory = false, briefActive = false, assessment = null, guidedDetail = false) }
            if (tab == WorkerTab.HISTORY) observeHistory()
        }
        if (current.briefActive && current.assessment?.status == "IN_PROGRESS") {
            operation {
                val repository = checkNotNull(batteryRepository)
                val parent = checkNotNull(repository.getAssessment(current.assessment!!.id, current.worker.id))
                if (parent.status == "IN_PROGRESS") {
                    repository.interruptAssessment(parent.id, current.worker.id, "El usuario abandonó el recorrido")
                }
                navigate()
            }
        } else navigate()
    }

    fun changeShift() {
        val current = state.value
        if (!current.destination.isWorkerRoot || current.busy || current.error != null) return
        current.guidedRuns.firstOrNull { !it.terminal }?.let { pending ->
            openGuided(pending.id)
            return
        }
        historyJob?.cancel()
        mutableState.update { it.copy(destination = Destination.SHIFT, shift = null) }
    }

    fun changeWorker() {
        if (state.value.busy || state.value.error != null) return
        historyJob?.cancel()
        mutableState.value = AppState(ready = true)
    }
    fun back() {
        if (state.value.busy || state.value.error != null) return
        if ((state.value.guidedActive || state.value.guidedDetail) && state.value.destination in setOf(Destination.RESULT, Destination.COGNITIVE_RESULT, Destination.SELF_REPORT_RESULT)) {
            finishActivityResult(); return
        }
        when (state.value.destination) {
            Destination.PVT -> interrupt("El usuario abandonó la prueba")
            Destination.COGNITIVE_RUN -> interruptCognitive("El usuario abandonó la prueba")
            Destination.SHIFT -> changeWorker()
            Destination.HOME -> changeShift()
            Destination.TESTS, Destination.QUESTIONNAIRES, Destination.HISTORY, Destination.RECOMMENDATIONS -> selectTab(WorkerTab.HOME)
            Destination.INSTRUCTIONS -> home()
            Destination.COGNITIVE_INSTRUCTIONS -> home()
            Destination.COGNITIVE_PRACTICE -> interruptCognitive("El usuario abandonó la práctica")
            Destination.COGNITIVE_READY -> interruptCognitive("El usuario abandonó la prueba antes de comenzar")
            Destination.RESULT -> if (state.value.resultFromHistory) history() else home()
            Destination.COGNITIVE_RESULT -> if (state.value.resultFromHistory) history() else home()
            Destination.QUESTIONNAIRE -> home()
            Destination.SELF_REPORT_RESULT, Destination.ASSESSMENT_RESULT -> if (state.value.resultFromHistory) history() else home()
            Destination.IDENTIFICATION -> Unit
            Destination.BATTERY_OVERVIEW -> if (state.value.guidedRun?.terminal == false) pauseGuided() else home()
        }
    }

    fun start() {
        val current = state.value
        if (current.busy || current.error != null) return
        val evaluation = useCases.prepare(current.worker ?: return, current.shift ?: return)
        interruptionRequested = null
        technicalInterruption = false
        operation {
            val guided = current.guidedRun
            if (current.guidedActive && guided != null) checkNotNull(guidedRepository).startGuidedPvt(guided.id, guided.nextStep, evaluation)
            else useCases.start(evaluation)
            if (current.briefActive) batteryRepository?.linkAssessmentPvt(checkNotNull(current.assessment).id, evaluation.worker.id, evaluation.id)
            mutableState.update { it.copy(evaluation = evaluation, destination = Destination.PVT, resultFromHistory = false) }
            engine = PvtEngine(clock, evaluation.configuration.responseWindowMillis)
            if (foreground) beginAttempt(1)
            else interrupt("La aplicación pasó a segundo plano al iniciar")
        }
    }

    private fun beginAttempt(index: Int) {
        if (interruptionRequested != null) return
        val config = state.value.evaluation!!.configuration
        signalBound = false
        engine!!.begin(index)
        mutableState.update { it.copy(phase = PvtPhase.WAITING, attemptNumber = index) }
        timer?.cancel()
        timer = viewModelScope.launch {
            delay(Random.nextLong(config.minWaitMillis, config.maxWaitMillis + 1))
            if (interruptionRequested == null) mutableState.update { it.copy(phase = PvtPhase.SIGNAL) }
        }
    }

    /** Vincula una sola vez el inicio PVT al callback de dibujo, validando evaluación e índice. */
    fun stimulusDrawn(evaluationId: String? = state.value.evaluation?.id, index: Int = state.value.attemptNumber) {
        if (state.value.destination != Destination.PVT || state.value.evaluation?.id != evaluationId || state.value.attemptNumber != index) return
        if (state.value.phase != PvtPhase.SIGNAL || signalBound || interruptionRequested != null) return
        signalBound = true
        engine?.stimulusPresented()
        timer = viewModelScope.launch {
            delay(state.value.evaluation!!.configuration.responseWindowMillis)
            while (isActive && state.value.phase == PvtPhase.SIGNAL) {
                val attempt = engine?.expire()
                if (attempt != null) { record(attempt); break }
                delay(1)
            }
        }
    }

    fun respond(evaluationId: String? = state.value.evaluation?.id, index: Int = state.value.attemptNumber) {
        if (state.value.destination != Destination.PVT || state.value.evaluation?.id != evaluationId || state.value.attemptNumber != index) return
        val current = state.value
        if (current.busy || current.error != null || interruptionRequested != null ||
            current.phase !in listOf(PvtPhase.WAITING, PvtPhase.SIGNAL)) return
        engine?.respond()?.let(::record)
    }

    private fun record(attempt: Attempt) {
        timer?.cancel()
        mutableState.update { it.copy(phase = PvtPhase.SAVING) }
        val evaluation = state.value.evaluation ?: return
        operation {
            useCases.record(evaluation.id, attempt)
            if (interruptionRequested == null) {
                if (attempt.outcome == AttemptOutcome.OMISSION) {
                    mutableState.update { it.copy(phase = PvtPhase.AWAITING_CONTINUE) }
                    return@operation
                }
                if (attempt.index == evaluation.configuration.attempts) {
                    useCases.finish(evaluation.id)
                    val saved = checkNotNull(repository.get(evaluation.id, evaluation.worker.id))
                    mutableState.update { it.copy(evaluation = saved, destination = Destination.RESULT) }
                } else {
                    // Suppress trailing/double taps during the transition to the next attempt.
                    delay(400)
                    beginAttempt(attempt.index + 1)
                }
            }
        }
    }

    fun continuePvt(evaluationId: String, index: Int) {
        val s = state.value
        if (s.destination != Destination.PVT || s.phase != PvtPhase.AWAITING_CONTINUE ||
            s.busy || s.error != null || !foreground || interruptionRequested != null ||
            s.evaluation?.id != evaluationId || s.attemptNumber != index) return
        val evaluation = s.evaluation
        operation {
            if (index == evaluation.configuration.attempts) {
                useCases.finish(evaluationId)
                val saved = checkNotNull(repository.get(evaluationId, evaluation.worker.id))
                mutableState.update { it.copy(evaluation = saved, destination = Destination.RESULT) }
            } else beginAttempt(index + 1)
        }
    }

    fun interrupt(reason: String) {
        if (state.value.destination != Destination.PVT) return
        interruptionRequested = reason
        timer?.cancel()
        engine?.stop()
        mutableState.update { it.copy(phase = PvtPhase.INTERRUPTED) }
        if (!state.value.busy && state.value.error == null) persistInterruption()
    }

    fun onForeground() { foreground = true }
    /** Detiene la medición aunque la escritura en curso deba finalizar antes de interrumpir. */
    fun onBackground() {
        foreground = false
        technicalInterruption = true
        interrupt("La prueba pasó a segundo plano o cambió la pantalla")
        interruptCognitive("La prueba pasó a segundo plano o cambió la pantalla")
        if (state.value.guidedActive) {
            pauseGuidedRequested = true
            if (!state.value.busy && state.value.error == null) pauseGuided()
        }
    }

    private fun persistInterruption() {
        val evaluation = state.value.evaluation ?: return
        val reason = interruptionRequested ?: return
        operation {
            if (technicalInterruption) repository.invalidate(evaluation.id, reason) else useCases.interrupt(evaluation.id, reason)
            val saved = checkNotNull(repository.get(evaluation.id, evaluation.worker.id))
            interruptionRequested = null
            mutableState.update { it.copy(evaluation = saved, destination = Destination.RESULT) }
        }
    }

    fun history() {
        if (state.value.busy || state.value.error != null) return
        observeHistory()
    }

    private fun observeHistory() {
        val worker = state.value.worker ?: return
        mutableState.update { it.copy(destination = Destination.HISTORY, selectedTab = WorkerTab.HISTORY, activityOrigin = WorkerTab.HISTORY) }
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            combine(repository.observeHistory(worker.id), cognitiveRepository?.observeCognitiveHistory(worker.id) ?: flowOf(emptyList()),
                batteryRepository?.observeAssessments(worker.id) ?: flowOf(emptyList()),
                batteryRepository?.observeSelfReports(worker.id) ?: flowOf(emptyList())) { pvt, cognitive, assessments, reports ->
                    AppHistory(pvt, cognitive, assessments, reports)
                }
                .catch { mutableState.update { it.copy(error = "No se pudo leer el historial. Reintenta para continuar.") } }
                .collect { rows -> mutableState.update { it.copy(history = rows.pvt, cognitiveHistory = rows.cognitive, assessmentHistory = rows.assessments, selfReportHistory = rows.reports) } }
        }
    }
    fun retryHistory() { mutableState.update { it.copy(error = null) }; history() }
    fun detail(id: String) {
        val worker = state.value.worker ?: return
        operation {
            val evaluation = checkNotNull(repository.get(id, worker.id))
            mutableState.update { it.copy(evaluation = evaluation, destination = Destination.RESULT, resultFromHistory = true) }
        }
    }

    fun chooseCognitiveTest(type: CognitiveTestType) = prepareCognitiveChoice(type)

    fun beginCognitivePractice() {
        val current = state.value
        if (current.destination != Destination.COGNITIVE_INSTRUCTIONS || current.busy || current.error != null) return
        val worker = current.worker ?: return
        val shift = current.shift ?: return
        val type = current.cognitiveType ?: return
        val config = CognitiveTestConfig.forType(type)
        val seed = current.cognitiveTrials.firstOrNull()?.let { cognitiveSeed } ?: return
        val session = CognitiveTestSession(
            checkNotNull(cognitiveSessionId), worker.id, worker.name, worker.area, worker.employment, shift,
            type, config.protocolVersion,
            "{\"practiceTrials\":${config.practiceTrials},\"scoredTrials\":${if (type == CognitiveTestType.DSST) "null" else config.scoredTrials},\"fixationMs\":${config.fixationMillis},\"responseWindowMs\":${config.responseWindowMillis},\"interTrialMinMs\":${config.interTrialMinMillis},\"interTrialMaxMs\":${config.interTrialMaxMillis},\"durationMs\":${if (type == CognitiveTestType.DSST) 60000 else "null"},\"stimulusDurationMs\":${if (type == CognitiveTestType.N_BACK) 500 else "null"},\"symbolMapping\":[${CognitiveTrials.symbolMapping(seed).joinToString(",")}],\"calculationVersion\":\"descriptive-v2\"}",
            seed, System.currentTimeMillis(),
            calculationVersion = "descriptive-v2",
        )
        operation {
            val guided = current.guidedRun
            if (current.guidedActive && guided != null) checkNotNull(guidedRepository).startGuidedCognitive(guided.id, guided.nextStep, session)
            else cognitiveRepositoryOrThrow().startCognitiveTest(session)
            mutableState.update { it.copy(cognitiveSession = session, cognitiveTrialPosition = 0, destination = Destination.COGNITIVE_PRACTICE, cognitivePhase = CognitivePhase.PRACTICE) }
            if (!foreground) interruptCognitive("La aplicación pasó a segundo plano al iniciar")
        }
    }

    private var cognitiveSeed: Long? = null

    /** Fija ID, semilla y secuencia para toda la sesión, incluyendo sus prácticas no puntuables. */
    fun prepareCognitiveChoice(type: CognitiveTestType) {
        if (!state.value.ready || state.value.busy || state.value.error != null) return
        val config = CognitiveTestConfig.forType(type)
        val seed = Random.nextLong()
        timer?.cancel()
        cognitiveEngine?.interrupt()
        cognitiveResolved = true
        cognitiveInterruptionRequested = null
        technicalInterruption = false
        deadlineTimer?.cancel()
        associationExpired = false
        associationEngine = if (type == CognitiveTestType.DSST) TimedAssociationEngine() else null
        nBackPending = null
        referenceBound = false
        cognitiveSeed = seed
        cognitiveSessionId = UUID.randomUUID().toString()
        mutableState.update { it.copy(activityOrigin = it.selectedTab, cognitiveType = type, cognitiveTrials = CognitiveTrials.generate(config, seed), cognitiveTrialPosition = 0, cognitiveSession = null, cognitiveFeedback = null, resultFromHistory = false, destination = Destination.COGNITIVE_INSTRUCTIONS, cognitivePhase = CognitivePhase.INSTRUCTIONS) }
    }

    fun answerCognitivePractice(answer: String?, sessionId: String? = cognitiveSessionId, trialIndex: Int? = state.value.cognitiveTrials.getOrNull(state.value.cognitiveTrialPosition)?.index) {
        val s = state.value
        if (s.destination != Destination.COGNITIVE_PRACTICE || s.busy || s.error != null ||
            cognitiveInterruptionRequested != null || sessionId != cognitiveSessionId) return
        val trial = s.cognitiveTrials.getOrNull(s.cognitiveTrialPosition) ?: return
        if (trial.index != trialIndex) return
        val outcome = if (answer == trial.expectedResponse) "PRACTICE_CORRECT" else "PRACTICE_ERROR"
        val record = CognitiveTrialRecord(trial.index, true, trial.stimulusCode, trial.expectedResponse, answer, outcome, null, null, null)
        saveCognitiveRecord(record) {
            val next = s.cognitiveTrialPosition + 1
            mutableState.update { it.copy(cognitiveTrialPosition = next, cognitiveFeedback = if (outcome == "PRACTICE_CORRECT") "Correcto" else "La respuesta era ${practiceAnswerLabel(trial.expectedResponse)}") }
            if (next >= (s.cognitiveTrials.count { it.isPractice })) mutableState.update { it.copy(destination = Destination.COGNITIVE_READY, cognitivePhase = CognitivePhase.READY) }
        }
    }

    fun startCognitiveScoredTrials() {
        if (state.value.destination != Destination.COGNITIVE_READY || state.value.busy || state.value.error != null ||
            cognitiveInterruptionRequested != null || !foreground) return
        mutableState.update { it.copy(destination = Destination.COGNITIVE_RUN, cognitiveFeedback = null) }
        if (state.value.cognitiveType == CognitiveTestType.N_BACK) {
            mutableState.update { it.copy(cognitivePhase = CognitivePhase.REFERENCE, stimulusVisible = true) }
            frameWatchdog = viewModelScope.launch {
                delay(5_000)
                if (!referenceBound && state.value.destination == Destination.COGNITIVE_RUN) technicalFailure("No se confirmó el dibujo del símbolo inicial")
            }
        }
        else beginCognitiveTrial()
    }

    private fun beginCognitiveTrial() {
        val s = state.value
        if (s.destination != Destination.COGNITIVE_RUN || cognitiveInterruptionRequested != null ||
            s.error != null || !foreground) return
        cognitiveResolved = false
        nBackPending = null
        val trial = s.cognitiveTrials[s.cognitiveTrialPosition]
        val trialEngine = CognitiveTrialEngine(s.cognitiveType!!, trial)
        cognitiveEngine = trialEngine
        mutableState.update { it.copy(cognitivePhase = CognitivePhase.FIXATION, stimulusVisible = true) }
        val config = CognitiveTestConfig.forType(s.cognitiveType)
        timer?.cancel()
        timer = viewModelScope.launch {
            delay(config.fixationMillis)
            if (cognitiveEngine !== trialEngine || !acceptsCognitive(s.cognitiveSession!!.id, trial.index)) return@launch
            mutableState.update { it.copy(cognitivePhase = CognitivePhase.STIMULUS) }
            frameWatchdog?.cancel()
            frameWatchdog = viewModelScope.launch {
                delay(5_000)
                if (cognitiveEngine === trialEngine && !cognitiveResolved) technicalFailure("No se confirmó el dibujo del estímulo")
            }
        }
    }

    /** Rechaza eventos de sesiones/ensayos anteriores, resueltos, bloqueados o fuera de primer plano. */
    private fun acceptsCognitive(sessionId: String, trialIndex: Int): Boolean {
        val s = state.value
        return foreground && s.destination == Destination.COGNITIVE_RUN && !s.busy && s.error == null &&
            cognitiveInterruptionRequested == null && !cognitiveResolved && cognitiveSessionId == sessionId &&
            s.cognitiveTrials.getOrNull(s.cognitiveTrialPosition)?.index == trialIndex
    }

    /** Recibe uptime del dibujo; el motor ignora redibujos y decide cuándo vence la ventana. */
    fun cognitiveStimulusDrawn(sessionId: String, trialIndex: Int, uptimeMillis: Long) {
        if (state.value.destination == Destination.COGNITIVE_RUN && state.value.cognitivePhase == CognitivePhase.REFERENCE && sessionId == cognitiveSessionId &&
            state.value.cognitiveTrials.getOrNull(state.value.cognitiveTrialPosition)?.index == trialIndex && !state.value.busy && !referenceBound && foreground) {
            referenceBound = true
            frameWatchdog?.cancel()
            timer = viewModelScope.launch { delay(500); beginCognitiveTrial() }
            return
        }
        if (!acceptsCognitive(sessionId, trialIndex) || state.value.cognitivePhase != CognitivePhase.STIMULUS) return
        val trialEngine = cognitiveEngine ?: return
        if (!trialEngine.stimulusDrawn(uptimeMillis)) return
        frameWatchdog?.cancel()
        if (state.value.cognitiveType == CognitiveTestType.DSST) {
            val timed = checkNotNull(associationEngine)
            if (timed.presented(uptimeMillis)) {
                deadlineTimer = viewModelScope.launch {
                    delay((uptimeMillis + 60_000 - cognitiveUptimeMillis()).coerceAtLeast(0))
                    while (isActive && !timed.expire(cognitiveUptimeMillis())) delay(1)
                    associationExpired = true
                    cognitiveResolved = true
                    if (!state.value.busy && state.value.error == null) awaitAssociationConfirmation()
                }
            }
            return
        }
        val window = CognitiveTestConfig.forType(state.value.cognitiveType!!).responseWindowMillis
        timer = viewModelScope.launch {
            if (state.value.cognitiveType == CognitiveTestType.N_BACK) {
                delay((uptimeMillis + 500 - cognitiveUptimeMillis()).coerceAtLeast(0))
                mutableState.update { it.copy(stimulusVisible = false) }
                delay((uptimeMillis + window - cognitiveUptimeMillis()).coerceAtLeast(0))
            } else delay(window)
            while (isActive && cognitiveEngine === trialEngine && acceptsCognitive(sessionId, trialIndex)) {
                val record = if (cognitiveUptimeMillis() >= uptimeMillis + window) nBackPending ?: trialEngine.timeout(cognitiveUptimeMillis()) else null
                if (record != null) { recordCognitiveOutcome(record); break }
                delay(1)
            }
        }
    }

    fun answerCognitive(sessionId: String, trialIndex: Int, answer: String, uptimeMillis: Long) {
        if (!acceptsCognitive(sessionId, trialIndex)) return
        val s = state.value
        if (s.cognitivePhase != CognitivePhase.STIMULUS &&
            !(s.cognitivePhase == CognitivePhase.FIXATION && s.cognitiveType == CognitiveTestType.GO_NO_GO)) return
        if (s.cognitiveType == CognitiveTestType.DSST && associationEngine?.accepts(uptimeMillis) != true) return
        cognitiveEngine?.respond(answer, uptimeMillis)?.let {
            if (s.cognitiveType == CognitiveTestType.N_BACK) nBackPending = it else recordCognitiveOutcome(it)
        }
    }

    /** Bloquea respuestas de inmediato, persiste el registro y solo entonces avanza o finaliza. */
    private fun recordCognitiveOutcome(record: CognitiveTrialRecord) {
        if (cognitiveResolved) return
        val s = state.value
        s.cognitiveType ?: return
        cognitiveResolved = true
        mutableState.update { it.copy(cognitivePhase = CognitivePhase.INTER_TRIAL) }
        timer?.cancel()
        val sessionId = cognitiveSessionId ?: return
        operation {
            cognitiveRepositoryOrThrow().saveCognitiveTrial(sessionId, record)
            if (cognitiveInterruptionRequested != null || !foreground) return@operation
            if (record.actualResponse == null && record.outcomeCode != "NO_GO_CORRECT_REJECTION") {
                mutableState.update { it.copy(cognitivePhase = CognitivePhase.AWAITING_CONTINUE) }
                return@operation
            }
            advanceCognitive(s, sessionId)
        }
    }

    fun continueCognitive(sessionId: String, trialIndex: Int) {
        val s = state.value
        if (s.destination != Destination.COGNITIVE_RUN || s.busy || s.error != null || !foreground ||
            cognitiveInterruptionRequested != null || sessionId != cognitiveSessionId ||
            s.cognitiveTrials.getOrNull(s.cognitiveTrialPosition)?.index != trialIndex) return
        if (s.cognitivePhase == CognitivePhase.AWAITING_FINISH) { finishAssociation(); return }
        if (s.cognitivePhase != CognitivePhase.AWAITING_CONTINUE) return
        operation { advanceCognitive(s, sessionId) }
    }

    private suspend fun advanceCognitive(s: AppState, sessionId: String) {
        mutableState.update { it.copy(cognitivePhase = CognitivePhase.INTER_TRIAL) }
        val next = s.cognitiveTrialPosition + 1
        if (s.cognitiveType == CognitiveTestType.DSST) {
            if (associationExpired || associationEngine?.accepts(cognitiveUptimeMillis()) != true) return
            val trials = s.cognitiveTrials + CognitiveTrials.dsstTrial(next, checkNotNull(cognitiveSeed))
            mutableState.update { it.copy(cognitiveTrialPosition = next, cognitiveTrials = trials) }
            // Resume after busy is cleared so draw callbacks cannot be lost.
            timer = viewModelScope.launch { yield(); beginCognitiveTrial() }
            return
        }
        val total = s.cognitiveTrials.size
        if (next >= total) {
            cognitiveRepositoryOrThrow().finishCognitiveTest(sessionId)
            val saved = checkNotNull(cognitiveRepositoryOrThrow().getCognitiveTest(sessionId, s.worker!!.id))
            if (cognitiveInterruptionRequested != null || !foreground) return
            mutableState.update { it.copy(cognitiveTrialPosition = next, cognitiveSession = saved, destination = Destination.COGNITIVE_RESULT, cognitiveFeedback = null, cognitivePhase = CognitivePhase.COMPLETED) }
        } else {
            val updated = cognitiveRepositoryOrThrow().getCognitiveTest(sessionId, state.value.worker!!.id)
            if (cognitiveInterruptionRequested != null || !foreground) return
            mutableState.update { it.copy(cognitiveTrialPosition = next, cognitiveSession = updated, cognitiveFeedback = null) }
            val gap = s.cognitiveTrials[s.cognitiveTrialPosition].interTrialDelayMillis ?: 0L
            timer = viewModelScope.launch { delay(gap); beginCognitiveTrial() }
        }
    }

    private fun saveCognitiveRecord(record: CognitiveTrialRecord, after: () -> Unit) {
        val sessionId = cognitiveSessionId ?: return
        operation {
            cognitiveRepositoryOrThrow().saveCognitiveTrial(sessionId, record)
            val updated = cognitiveRepositoryOrThrow().getCognitiveTest(sessionId, state.value.worker!!.id)
            if (updated != null) mutableState.update { it.copy(cognitiveSession = updated) }
            if (cognitiveInterruptionRequested == null && foreground) after()
        }
    }

    fun interruptCognitive(reason: String) {
        if (state.value.destination !in setOf(Destination.COGNITIVE_RUN, Destination.COGNITIVE_PRACTICE, Destination.COGNITIVE_READY)) return
        cognitiveInterruptionRequested = reason
        timer?.cancel()
        deadlineTimer?.cancel()
        frameWatchdog?.cancel()
        associationExpired = false
        associationEngine?.stop()
        cognitiveResolved = true
        mutableState.update { it.copy(cognitivePhase = CognitivePhase.INCOMPLETE) }
        cognitiveEngine?.interrupt()
        if (!state.value.busy && state.value.error == null) persistCognitiveInterruption()
    }

    private fun persistCognitiveInterruption() {
        val id = cognitiveSessionId ?: return
        val reason = cognitiveInterruptionRequested ?: return
        operation {
            if (technicalInterruption) cognitiveRepositoryOrThrow().invalidateCognitiveTest(id, reason)
            else cognitiveRepositoryOrThrow().interruptCognitiveTest(id, reason)
            val saved = cognitiveRepositoryOrThrow().getCognitiveTest(id, state.value.worker!!.id)
            cognitiveInterruptionRequested = null
            mutableState.update { it.copy(cognitiveSession = saved, destination = Destination.COGNITIVE_RESULT) }
        }
    }

    fun openCognitiveDetail(id: String) {
        val worker = state.value.worker ?: return
        operation {
            val session = cognitiveRepositoryOrThrow().getCognitiveTest(id, worker.id)
            checkNotNull(session) { "No se encontró la demostración" }
            cognitiveSessionId = id
            mutableState.update { it.copy(cognitiveSession = session, cognitiveType = session.type, resultFromHistory = true, destination = Destination.COGNITIVE_RESULT) }
        }
    }

    private fun cognitiveRepositoryOrThrow() = checkNotNull(cognitiveRepository) { "Persistencia de pruebas cognitivas no configurada" }
    private fun practiceAnswerLabel(code: String?) = when (code) {
        "LEFT" -> "Izquierda"; "RIGHT" -> "Derecha"; "RESPOND" -> "Responder"; "SAME" -> "Igual"; "DIFFERENT" -> "Diferente"
        else -> if (state.value.cognitiveType == CognitiveTestType.DSST) "el dígito $code" else code?.toIntOrNull()?.let { "la casilla ${it + 1}" } ?: "no responder"
    }

    private fun awaitAssociationConfirmation() {
        if (!associationExpired || state.value.destination != Destination.COGNITIVE_RUN) return
        timer?.cancel()
        cognitiveEngine?.interrupt()
        mutableState.update { it.copy(cognitivePhase = CognitivePhase.AWAITING_FINISH, stimulusVisible = false) }
    }

    private fun finishAssociation() {
        val timed = associationEngine ?: return
        val id = cognitiveSessionId ?: return
        val worker = state.value.worker ?: return
        val start = timed.startedAt ?: return
        val finish = timed.finishedAt ?: return
        if (!associationExpired || state.value.busy || cognitiveInterruptionRequested != null) return
        associationExpired = false
        timer?.cancel()
        cognitiveEngine?.interrupt()
        mutableState.update { it.copy(cognitivePhase = CognitivePhase.INTER_TRIAL) }
        operation {
            cognitiveRepositoryOrThrow().recordExecutionTimes(id, start, finish)
            cognitiveRepositoryOrThrow().finishCognitiveTest(id)
            val saved = checkNotNull(cognitiveRepositoryOrThrow().getCognitiveTest(id, worker.id))
            mutableState.update { it.copy(cognitiveSession = saved, destination = Destination.COGNITIVE_RESULT, cognitivePhase = CognitivePhase.COMPLETED) }
        }
    }

    fun openInstrument(id: InstrumentId) {
        if (state.value.busy || state.value.error != null || !state.value.ready) return
        if (id == InstrumentId.BORG) return
        mutableState.update { it.copy(activityOrigin = it.selectedTab, instrument = instruments.first { d -> d.id == id }, destination = Destination.QUESTIONNAIRE, resultFromHistory = false, briefActive = false) }
    }

    fun startBrief() {
        val current = state.value
        val kss = instruments.first { it.id == InstrumentId.KSS }
        if (!kss.available || current.busy || !current.ready || current.error != null || current.worker == null || current.shift == null) return
        val parent = AssessmentSession(UUID.randomUUID().toString(), current.worker!!.id, current.shift!!, System.currentTimeMillis())
        operation {
            checkNotNull(batteryRepository).startAssessment(parent)
            mutableState.update { it.copy(activityOrigin = it.selectedTab, assessment = parent, briefActive = true, instrument = kss, destination = Destination.QUESTIONNAIRE, resultFromHistory = false) }
        }
    }

    fun submitReport(answers: Map<String, String>, context: String?, moment: String?, omitted: Boolean = false) {
        val current = state.value
        val definition = current.instrument ?: return
        if (!definition.available || current.busy || current.error != null || current.destination != Destination.QUESTIONNAIRE) return
        if (omitted && definition.id != InstrumentId.KSS) return
        val values = if (omitted) emptyMap() else definition.questions.associate { q ->
            q.id to (q.options.firstOrNull { it.code == answers[q.id] }?.ordinal ?: return)
        }
        if (definition.id == InstrumentId.BORG && (context.isNullOrBlank() || moment.isNullOrBlank())) return
        val report = SelfReport(UUID.randomUUID().toString(), current.worker!!.id, current.shift!!,
            if (current.briefActive) current.assessment?.id else null, definition.id, definition.version, definition.language,
            System.currentTimeMillis(), values, omitted, context, moment,
            if (!omitted && definition.id == InstrumentId.CHALDER && definition.scoringMethod == "AUTHORIZED_ORDINAL_SUM") values.values.sum() else null, definition.scoringMethod)
        operation {
            if (current.guidedActive && current.guidedRun != null) checkNotNull(guidedRepository).saveGuidedReport(current.guidedRun.id, current.guidedRun.nextStep, report)
            else checkNotNull(batteryRepository).saveSelfReport(report)
            mutableState.update { it.copy(selfReport = report, destination = if (current.briefActive) Destination.INSTRUCTIONS else Destination.SELF_REPORT_RESULT) }
        }
    }

    fun openReport(id: String) {
        val worker = state.value.worker ?: return
        operation {
            val report = checkNotNull(batteryRepository).getSelfReport(id, worker.id)
            mutableState.update { it.copy(selfReport = checkNotNull(report), destination = Destination.SELF_REPORT_RESULT, resultFromHistory = true) }
        }
    }

    fun openAssessment(id: String, fromHistory: Boolean = true) {
        val worker = state.value.worker ?: return
        operation {
            val parent = checkNotNull(checkNotNull(batteryRepository).getAssessment(id, worker.id))
            val reports = batteryRepository.observeSelfReports(worker.id).first().filter { it.assessmentId == id }
            val pvt = parent.pvtId?.let { repository.get(it, worker.id) }
            mutableState.update { it.copy(assessment = parent, assessmentReports = reports, evaluation = pvt, briefActive = false, resultFromHistory = fromHistory, destination = Destination.ASSESSMENT_RESULT) }
        }
    }

    fun finishPvtResult() {
        if (state.value.guidedActive || state.value.guidedDetail) { finishActivityResult(); return }
        val parent = state.value.assessment
        if (state.value.briefActive && parent != null) openAssessment(parent.id, false)
        else if (state.value.resultFromHistory) history() else home()
    }

    fun restartPvt() { if (state.value.guidedActive) finishGuidedModule() else if (state.value.briefActive) startBrief() else instructions() }

    fun refreshBatteryDefinition() {
        viewModelScope.launch {
            try {
                val (definition, message) = batteryConfigLoader()
                mutableState.update { it.copy(batteryDefinition = definition, batteryMessage = message) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(batteryMessage = "No se pudo descargar la configuración. Reintenta con conexión.") }
            }
        }
    }
    fun openBatteries() {
        if (state.value.busy || state.value.error != null) return
        val pending = state.value.guidedRuns.firstOrNull { !it.terminal }
        if (pending != null) openGuided(pending.id)
        else mutableState.update { it.copy(guidedRun = null, guidedActive = false, destination = Destination.BATTERY_OVERVIEW) }
    }
    fun startGuidedBrief() {
        if (instruments.none { it.id == InstrumentId.KSS && it.available }) return
        startGuided(BatteryDefinition.brief(organizationId, instruments))
    }
    fun startPublishedBattery() { state.value.batteryDefinition?.let(::startGuided) }
    private fun startGuided(definition: BatteryDefinition) {
        val current = state.value
        if (current.busy || current.error != null || current.worker == null || current.shift == null || guidedRepository == null) return
        current.guidedRuns.firstOrNull { !it.terminal }?.let { openGuided(it.id); return }
        try { definition.validate(instruments) } catch (_: IllegalArgumentException) {
            mutableState.update { it.copy(batteryMessage = "La batería contiene módulos no habilitados o incompatibles. Actualiza la aplicación.") }; return
        }
        val run = GuidedBatteryRun(UUID.randomUUID().toString(), current.worker.id, current.shift, System.currentTimeMillis(), definition)
        operation {
            guidedRepository.startGuided(run)
            mutableState.update { it.copy(guidedRun = run, guidedActive = false, guidedDetail = false, briefActive = false, destination = Destination.BATTERY_OVERVIEW) }
        }
    }
    fun openGuided(id: String) {
        val uid = state.value.worker?.id ?: return
        operation {
            val run = checkNotNull(guidedRepository).refreshGuided(id, uid)
            mutableState.update { it.copy(guidedRun = run, guidedActive = false, briefActive = false, destination = Destination.BATTERY_OVERVIEW) }
        }
    }
    fun continueGuided() {
        val current = state.value
        val run = current.guidedRun ?: return
        if (current.busy || current.error != null || run.terminal || run.shift != current.shift) return
        try { run.definition.validate(instruments) } catch (_: IllegalArgumentException) {
            mutableState.update { it.copy(batteryMessage = "La versión de este recorrido no es compatible. Actualiza la aplicación o consulta al administrador.") }; return
        }
        operation {
            val saved = checkNotNull(guidedRepository).refreshGuided(run.id, run.workerId)
            if (saved.terminal) { mutableState.update { it.copy(guidedRun = saved) }; return@operation }
            guidedRepository.setGuidedState(run.id, run.workerId, BatteryRunStatus.IN_PROGRESS)
            val active = checkNotNull(guidedRepository.guided(run.id, run.workerId))
            val module = active.steps[active.nextStep].moduleId
            mutableState.update { it.copy(guidedRun = active, guidedActive = true, briefActive = false,
                resultFromHistory = false, evaluation = null, cognitiveSession = null, selfReport = null) }
            when (module) {
                "PVT" -> mutableState.update { it.copy(destination = Destination.INSTRUCTIONS) }
                "KSS", "BORG", "CHALDER" -> mutableState.update { it.copy(instrument = instruments.first { d -> d.id.name == module }, destination = Destination.QUESTIONNAIRE) }
                else -> {
                    prepareGuidedCognitive(CognitiveTestType.valueOf(module))
                }
            }
        }
    }
    private fun prepareGuidedCognitive(type: CognitiveTestType) {
        val config = CognitiveTestConfig.forType(type)
        cognitiveSeed = Random.nextLong()
        cognitiveSessionId = UUID.randomUUID().toString()
        technicalInterruption = false
        cognitiveInterruptionRequested = null
        associationExpired = false
        timer?.cancel(); deadlineTimer?.cancel(); frameWatchdog?.cancel()
        cognitiveEngine?.interrupt()
        cognitiveResolved = true
        associationEngine = if (type == CognitiveTestType.DSST) TimedAssociationEngine() else null
        nBackPending = null
        referenceBound = false
        mutableState.update { it.copy(cognitiveType = type, cognitiveTrials = CognitiveTrials.generate(config, checkNotNull(cognitiveSeed)),
            cognitiveTrialPosition = 0, cognitiveFeedback = null, cognitivePhase = CognitivePhase.INSTRUCTIONS,
            destination = Destination.COGNITIVE_INSTRUCTIONS) }
    }
    fun finishGuidedModule() {
        val run = state.value.guidedRun ?: return
        openGuided(run.id)
    }
    fun finishActivityResult() {
        if (state.value.guidedActive || state.value.guidedDetail) {
            mutableState.update { it.copy(guidedDetail = false) }
            finishGuidedModule()
        } else if (state.value.resultFromHistory) history() else home()
    }
    fun openGuidedAttempt(attempt: BatteryAttempt) {
        if (state.value.busy || state.value.error != null) return
        mutableState.update { it.copy(guidedDetail = true, guidedActive = false) }
        when (attempt.kind) {
            "PVT" -> detail(attempt.runId)
            "COGNITIVE" -> openCognitiveDetail(attempt.runId)
            "REPORT" -> openReport(attempt.runId)
        }
    }
    fun pauseGuided() {
        if (state.value.busy || state.value.error != null) { pauseGuidedRequested = true; return }
        pauseGuidedRequested = false
        val run = state.value.guidedRun ?: return
        timer?.cancel(); deadlineTimer?.cancel(); frameWatchdog?.cancel()
        operation {
            if (!run.terminal) checkNotNull(guidedRepository).setGuidedState(run.id, run.workerId, BatteryRunStatus.PAUSED)
            val saved = checkNotNull(guidedRepository).guided(run.id, run.workerId)
            mutableState.update { it.copy(guidedRun = saved, guidedActive = false, guidedDetail = false, briefActive = false, destination = Destination.HOME) }
        }
    }
    fun abandonGuided() {
        val run = state.value.guidedRun ?: return
        operation {
            checkNotNull(guidedRepository).setGuidedState(run.id, run.workerId, BatteryRunStatus.ABANDONED)
            mutableState.update { it.copy(guidedRun = guidedRepository.guided(run.id, run.workerId), guidedActive = false) }
        }
    }

    fun technicalFailure(reason: String) {
        technicalInterruption = true
        interruptCognitive(reason)
        interrupt(reason)
    }
}

private data class AppHistory(val pvt: List<Evaluation>, val cognitive: List<CognitiveTestSession>, val assessments: List<AssessmentSession>, val reports: List<SelfReport>)
