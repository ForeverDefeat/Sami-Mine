package com.example.samimine.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samimine.app.navigation.Destination
import com.example.samimine.application.usecase.EvaluationUseCases
import com.example.samimine.domain.evaluation.pvt.*
import com.example.samimine.domain.evaluation.cognitive.CognitiveTrialEngine
import com.example.samimine.domain.model.*
import com.example.samimine.domain.repository.EvaluationRepository
import com.example.samimine.domain.repository.CognitiveTestRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import java.util.UUID

enum class PvtPhase { WAITING, SIGNAL, SAVING, INTERRUPTED }
data class AppState(
    val destination: Destination = Destination.IDENTIFICATION,
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
)

class SamiMineViewModel(
    private val repository: EvaluationRepository,
    private val useCases: EvaluationUseCases,
    private val clock: MonotonicClock,
    private val cognitiveRepository: CognitiveTestRepository? = repository as? CognitiveTestRepository,
    private val cognitiveUptimeMillis: () -> Long = { android.os.SystemClock.uptimeMillis() },
) : ViewModel() {
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

    init { operation { repository.recoverInterrupted(); cognitiveRepository?.recoverCognitiveTests(); mutableState.update { it.copy(ready = true) } } }

    /** Serialize writes and retain their original data for an idempotent retry. */
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
        }
    }

    fun retry() { retryAction?.let { operation(it) } }
    fun selectWorker(worker: Worker) {
        if (!state.value.ready || state.value.busy) return
        historyJob?.cancel()
        mutableState.update { it.copy(worker = worker, shift = null, history = emptyList(), evaluation = null, destination = Destination.SHIFT) }
    }
    fun selectShift(shift: Shift) { mutableState.update { it.copy(shift = shift, destination = Destination.HOME) } }
    fun instructions() { mutableState.update { it.copy(destination = Destination.INSTRUCTIONS) } }
    fun home() { mutableState.update { it.copy(destination = Destination.HOME, error = null, resultFromHistory = false) } }
    fun changeWorker() {
        historyJob?.cancel()
        mutableState.value = AppState(ready = true)
    }
    fun back() {
        if (state.value.busy || state.value.error != null) return
        when (state.value.destination) {
            Destination.PVT -> interrupt("El usuario abandonó la prueba")
            Destination.COGNITIVE_RUN -> interruptCognitive("El usuario abandonó la prueba")
            Destination.SHIFT -> changeWorker()
            Destination.HOME -> mutableState.update { it.copy(destination = Destination.SHIFT) }
            Destination.INSTRUCTIONS, Destination.HISTORY -> home()
            Destination.COGNITIVE_INSTRUCTIONS -> home()
            Destination.COGNITIVE_PRACTICE -> interruptCognitive("El usuario abandonó la práctica")
            Destination.COGNITIVE_READY -> interruptCognitive("El usuario abandonó la prueba antes de comenzar")
            Destination.RESULT -> if (state.value.resultFromHistory) history() else home()
            Destination.COGNITIVE_RESULT -> if (state.value.resultFromHistory) history() else home()
            Destination.IDENTIFICATION -> Unit
        }
    }

    fun start() {
        val current = state.value
        if (current.busy || current.error != null) return
        val evaluation = useCases.prepare(current.worker ?: return, current.shift ?: return)
        interruptionRequested = null
        operation {
            useCases.start(evaluation)
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

    /** Mark onset at stimulus drawing rather than when scheduling its frame. */
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

    fun interrupt(reason: String) {
        if (state.value.destination != Destination.PVT) return
        interruptionRequested = reason
        timer?.cancel()
        engine?.stop()
        mutableState.update { it.copy(phase = PvtPhase.INTERRUPTED) }
        if (!state.value.busy && state.value.error == null) persistInterruption()
    }

    fun onForeground() { foreground = true }
    fun onBackground() {
        foreground = false
        interrupt("La prueba pasó a segundo plano o cambió la pantalla")
        interruptCognitive("La prueba pasó a segundo plano o cambió la pantalla")
    }

    private fun persistInterruption() {
        val evaluation = state.value.evaluation ?: return
        val reason = interruptionRequested ?: return
        operation {
            useCases.interrupt(evaluation.id, reason)
            val saved = checkNotNull(repository.get(evaluation.id, evaluation.worker.id))
            interruptionRequested = null
            mutableState.update { it.copy(evaluation = saved, destination = Destination.RESULT) }
        }
    }

    fun history() {
        val worker = state.value.worker ?: return
        mutableState.update { it.copy(destination = Destination.HISTORY, history = emptyList(), cognitiveHistory = emptyList()) }
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            combine(repository.observeHistory(worker.id), cognitiveRepositoryOrThrow().observeCognitiveHistory(worker.id)) { pvt, cognitive -> pvt to cognitive }
                .catch { mutableState.update { it.copy(error = "No se pudo leer el historial. Reintenta para continuar.") } }
                .collect { (pvt, cognitive) -> mutableState.update { it.copy(history = pvt, cognitiveHistory = cognitive) } }
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
            "{\"practiceTrials\":${config.practiceTrials},\"scoredTrials\":${config.scoredTrials},\"fixationMs\":${config.fixationMillis},\"responseWindowMs\":${config.responseWindowMillis},\"interTrialMinMs\":${config.interTrialMinMillis},\"interTrialMaxMs\":${config.interTrialMaxMillis}}",
            seed, System.currentTimeMillis(),
        )
        operation {
            cognitiveRepositoryOrThrow().startCognitiveTest(session)
            mutableState.update { it.copy(cognitiveSession = session, cognitiveTrialPosition = 0, destination = Destination.COGNITIVE_PRACTICE, cognitivePhase = CognitivePhase.PRACTICE) }
            if (!foreground) interruptCognitive("La aplicación pasó a segundo plano al iniciar")
        }
    }

    private var cognitiveSeed: Long? = null

    fun prepareCognitiveChoice(type: CognitiveTestType) {
        if (!state.value.ready || state.value.busy) return
        val config = CognitiveTestConfig.forType(type)
        val seed = Random.nextLong()
        timer?.cancel()
        cognitiveEngine?.interrupt()
        cognitiveResolved = true
        cognitiveInterruptionRequested = null
        cognitiveSeed = seed
        cognitiveSessionId = UUID.randomUUID().toString()
        mutableState.update { it.copy(cognitiveType = type, cognitiveTrials = CognitiveTrials.generate(config, seed), cognitiveTrialPosition = 0, cognitiveSession = null, cognitiveFeedback = null, resultFromHistory = false, destination = Destination.COGNITIVE_INSTRUCTIONS, cognitivePhase = CognitivePhase.INSTRUCTIONS) }
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
        beginCognitiveTrial()
    }

    private fun beginCognitiveTrial() {
        val s = state.value
        if (s.destination != Destination.COGNITIVE_RUN || cognitiveInterruptionRequested != null ||
            s.error != null || !foreground) return
        cognitiveResolved = false
        val trial = s.cognitiveTrials[s.cognitiveTrialPosition]
        val trialEngine = CognitiveTrialEngine(s.cognitiveType!!, trial)
        cognitiveEngine = trialEngine
        mutableState.update { it.copy(cognitivePhase = CognitivePhase.FIXATION) }
        val config = CognitiveTestConfig.forType(s.cognitiveType)
        timer?.cancel()
        timer = viewModelScope.launch {
            delay(config.fixationMillis)
            if (cognitiveEngine !== trialEngine || !acceptsCognitive(s.cognitiveSession!!.id, trial.index)) return@launch
            mutableState.update { it.copy(cognitivePhase = CognitivePhase.STIMULUS) }
        }
    }

    private fun acceptsCognitive(sessionId: String, trialIndex: Int): Boolean {
        val s = state.value
        return foreground && s.destination == Destination.COGNITIVE_RUN && !s.busy && s.error == null &&
            cognitiveInterruptionRequested == null && !cognitiveResolved && cognitiveSessionId == sessionId &&
            s.cognitiveTrials.getOrNull(s.cognitiveTrialPosition)?.index == trialIndex
    }

    fun cognitiveStimulusDrawn(sessionId: String, trialIndex: Int, uptimeMillis: Long) {
        if (!acceptsCognitive(sessionId, trialIndex) || state.value.cognitivePhase != CognitivePhase.STIMULUS) return
        val trialEngine = cognitiveEngine ?: return
        if (!trialEngine.stimulusDrawn(uptimeMillis)) return
        val window = CognitiveTestConfig.forType(state.value.cognitiveType!!).responseWindowMillis
        timer = viewModelScope.launch {
            delay(window)
            while (isActive && cognitiveEngine === trialEngine && acceptsCognitive(sessionId, trialIndex)) {
                val record = trialEngine.timeout(cognitiveUptimeMillis())
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
        cognitiveEngine?.respond(answer, uptimeMillis)?.let(::recordCognitiveOutcome)
    }

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
            val next = s.cognitiveTrialPosition + 1
            val total = s.cognitiveTrials.size
            if (next >= total) {
                cognitiveRepositoryOrThrow().finishCognitiveTest(sessionId)
                val saved = checkNotNull(cognitiveRepositoryOrThrow().getCognitiveTest(sessionId, s.worker!!.id))
                if (cognitiveInterruptionRequested != null || !foreground) return@operation
                mutableState.update { it.copy(cognitiveTrialPosition = next, cognitiveSession = saved, destination = Destination.COGNITIVE_RESULT, cognitiveFeedback = null, cognitivePhase = CognitivePhase.COMPLETED) }
            } else {
                val updated = cognitiveRepositoryOrThrow().getCognitiveTest(sessionId, state.value.worker!!.id)
                if (cognitiveInterruptionRequested != null || !foreground) return@operation
                mutableState.update { it.copy(cognitiveTrialPosition = next, cognitiveSession = updated, cognitiveFeedback = null) }
                val gap = s.cognitiveTrials[s.cognitiveTrialPosition].interTrialDelayMillis ?: 0L
                timer = viewModelScope.launch { delay(gap); beginCognitiveTrial() }
            }
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
        cognitiveResolved = true
        mutableState.update { it.copy(cognitivePhase = CognitivePhase.INCOMPLETE) }
        cognitiveEngine?.interrupt()
        if (!state.value.busy && state.value.error == null) persistCognitiveInterruption()
    }

    private fun persistCognitiveInterruption() {
        val id = cognitiveSessionId ?: return
        val reason = cognitiveInterruptionRequested ?: return
        operation {
            cognitiveRepositoryOrThrow().interruptCognitiveTest(id, reason)
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
    private fun practiceAnswerLabel(code: String?) = when (code) { "LEFT" -> "Izquierda"; "RIGHT" -> "Derecha"; "RESPOND" -> "Responder"; else -> code?.toIntOrNull()?.let { "la casilla ${it + 1}" } ?: "no responder" }
}
