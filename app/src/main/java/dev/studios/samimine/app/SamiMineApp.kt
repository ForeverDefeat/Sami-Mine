package dev.studios.samimine.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.studios.samimine.app.navigation.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.studios.samimine.ui.screens.home.WorkerCatalogScreen
import dev.studios.samimine.ui.screens.home.RecommendationsScreen
import dev.studios.samimine.data.demo.DemoWorkers
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.screens.identification.IdentificationScreen
import dev.studios.samimine.ui.screens.shift.ShiftScreen
import dev.studios.samimine.ui.screens.home.HomeScreen
import dev.studios.samimine.ui.screens.instructions.InstructionsScreen
import dev.studios.samimine.ui.screens.pvt.PvtScreen
import dev.studios.samimine.ui.screens.result.ResultScreen
import dev.studios.samimine.ui.screens.history.HistoryScreen
import dev.studios.samimine.ui.screens.cognitive.*
import dev.studios.samimine.ui.theme.*

/**
 * Renderiza el destino de AppState y traduce acciones de pantalla a eventos del ViewModel.
 * Captura ID e índice por ensayo: un callback tardío debe conservar su identidad original.
 */
@Composable
fun SamiMineApp(model: SamiMineViewModel, lockedWorker: Worker? = null, signOut: (() -> Unit)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.ready, state.destination, lockedWorker) {
        if (lockedWorker != null && state.ready && state.destination == Destination.IDENTIFICATION) model.selectWorker(lockedWorker)
    }
    BackHandler(state.destination != Destination.IDENTIFICATION) { model.back() }
    val dark = state.destination == Destination.PVT
    SamiMineTheme {
        val mainScreen = state.destination.isWorkerRoot
        val navigationVisible = state.destination.showsWorkerNavigation
        val enabled = state.ready && !state.busy && state.error == null
        Scaffold(containerColor = if (dark) TestBackground else PageBackground,
            bottomBar = {
                if (navigationVisible) WorkerNavigation(state.selectedTab, enabled, model::selectTab)
            }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
                if (mainScreen) {
                    WorkerProfileMenu(enabled, model::changeShift, signOut ?: model::changeWorker, if (signOut == null) "Cambiar perfil" else "Cerrar sesión")
                } else if (state.destination != Destination.IDENTIFICATION) {
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        val abandoning = state.destination in setOf(Destination.PVT, Destination.COGNITIVE_RUN, Destination.COGNITIVE_PRACTICE, Destination.COGNITIVE_READY)
                        BackAction(if (abandoning) "Abandonar prueba" else "Volver", dark, !state.busy && state.error == null, model::back)
                    }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = if (dark) TestRing else BrandBlue)
                state.error?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                            PrimaryAction("Reintentar", enabled = !state.busy) {
                                if (state.destination == Destination.HISTORY) model.retryHistory() else model.retry()
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    if (!state.ready) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Preparando almacenamiento local…") }
                    } else key(state.worker?.id, state.shift) {
                        val screenState = rememberSaveableStateHolder()
                        screenState.SaveableStateProvider(state.destination.name) {
                            when (state.destination) {
                                Destination.IDENTIFICATION -> IdentificationScreen(DemoWorkers.all, !state.busy && state.error == null, model::selectWorker)
                                Destination.SHIFT -> ShiftScreen(state.worker!!, model::selectShift)
                                Destination.HOME -> HomeScreen(state.worker!!, state.shift!!, model::instructions, model.instruments, model::startGuidedBrief, enabled,
                                    model::openBatteries, state.guidedRuns.any { !it.terminal })
                                Destination.RECOMMENDATIONS -> RecommendationsScreen()
                                Destination.TESTS, Destination.QUESTIONNAIRES -> WorkerCatalogScreen(
                                    state.destination == Destination.QUESTIONNAIRES, model.instruments, enabled,
                                    model::instructions, model::chooseCognitiveTest, model::openInstrument)
                                Destination.INSTRUCTIONS -> InstructionsScreen(!state.busy && state.error == null, model::start)
                                Destination.PVT -> {
                                    val evaluationId = state.evaluation!!.id
                                    val index = state.attemptNumber
                                    PvtScreen(state.phase, index, state.evaluation!!.configuration.attempts,
                                        !state.busy && state.error == null,
                                        { model.stimulusDrawn(evaluationId, index) }, { model.respond(evaluationId, index) },
                                        { model.continuePvt(evaluationId, index) })
                                }
                                Destination.RESULT -> ResultScreen(state.evaluation!!, state.resultFromHistory, model::finishPvtResult, model::restartPvt,
                                    if (state.guidedActive) "Ver avance de la batería" else if (state.briefActive) "Ver resumen del recorrido" else state.activityOrigin.returnLabel)
                                Destination.HISTORY -> HistoryScreen(state.worker!!, state.history, !state.busy && state.error == null, model::openCognitiveDetail, state.cognitiveHistory,
                                    state.assessmentHistory, state.selfReportHistory, { model.openAssessment(it) }, model::openReport,
                                    state.guidedRuns, model::openGuided, model::detail)
                                Destination.COGNITIVE_INSTRUCTIONS -> CognitiveInstructionsScreen(state.cognitiveType!!, model::home, model::beginCognitivePractice, !state.busy && state.error == null)
                                Destination.COGNITIVE_PRACTICE -> {
                                    val sessionId = state.cognitiveSession!!.id
                                    val trialIndex = state.cognitiveTrials[state.cognitiveTrialPosition].index
                                    CognitivePracticeScreen(
                                    state.cognitiveType!!, state.cognitiveTrials[state.cognitiveTrialPosition], state.cognitiveTrialPosition,
                                    state.cognitiveTrials.count { it.isPractice }, state.cognitiveFeedback, !state.busy && state.error == null && state.cognitivePhase == CognitivePhase.PRACTICE,
                                    { model.answerCognitivePractice(it, sessionId, trialIndex) },
                                    seed = state.cognitiveSession!!.randomSeed,
                                )
                                }
                                Destination.COGNITIVE_READY -> CognitiveReadyScreen(state.cognitiveType!!, model::startCognitiveScoredTrials, !state.busy && state.error == null)
                                Destination.COGNITIVE_RUN -> {
                                    val sessionId = state.cognitiveSession!!.id
                                    val trialIndex = state.cognitiveTrials[state.cognitiveTrialPosition].index
                                    CognitiveRunScreen(
                                    state.cognitiveType!!, state.cognitiveTrials[state.cognitiveTrialPosition],
                                    state.cognitiveTrialPosition - state.cognitiveTrials.count { it.isPractice } + 1,
                                    CognitiveTestConfig.forType(state.cognitiveType!!).scoredTrials,
                                    state.cognitivePhase, !state.busy && state.error == null,
                                    { model.cognitiveStimulusDrawn(sessionId, trialIndex, it) },
                                    { answer, time -> model.answerCognitive(sessionId, trialIndex, answer, time) },
                                    seed = state.cognitiveSession!!.randomSeed, stimulusVisible = state.stimulusVisible,
                                    continueTest = { model.continueCognitive(sessionId, trialIndex) },
                                )
                                }
                                Destination.COGNITIVE_RESULT -> state.cognitiveSession?.let { session ->
                                    CognitiveResultScreen(session, state.resultFromHistory,
                                        model::finishActivityResult,
                                        { if (state.guidedActive) model.finishGuidedModule() else model.prepareCognitiveChoice(session.type) },
                                        if (state.guidedActive) "Ver avance de la batería" else state.activityOrigin.returnLabel)
                                }
                                Destination.QUESTIONNAIRE -> state.instrument?.let { definition ->
                                    QuestionnaireScreen(definition, state.briefActive, !state.busy && state.error == null, model::submitReport, model::home)
                                }
                                Destination.SELF_REPORT_RESULT -> state.selfReport?.let { report ->
                                    SelfReportResultScreen(report, state.resultFromHistory, if (state.guidedActive) "Ver avance de la batería" else "Volver a cuestionarios", model::finishActivityResult)
                                }
                                Destination.ASSESSMENT_RESULT -> state.assessment?.let { parent ->
                                    AssessmentResultScreen(parent, state.assessmentReports, state.evaluation, state.resultFromHistory) { if (state.resultFromHistory) model.history() else model.home() }
                                }
                                Destination.BATTERY_OVERVIEW -> GuidedBatteryScreen(state.guidedRun, state.batteryDefinition, state.batteryMessage,
                                    enabled, model::startPublishedBattery, model::continueGuided, model::pauseGuided, model::abandonGuided,
                                    model::refreshBatteryDefinition, model::openGuidedAttempt)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkerNavigation(selected: WorkerTab, enabled: Boolean, select: (WorkerTab) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.25f
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        WorkerTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab, enabled = enabled, onClick = { select(tab) },
                modifier = Modifier.testTag("tab-${tab.name}").semantics { contentDescription = tab.label },
                icon = { Icon(when (tab) {
                    WorkerTab.HOME -> SamiIcons.Home
                    WorkerTab.TESTS -> SamiIcons.Grid
                    WorkerTab.QUESTIONNAIRES -> SamiIcons.Questionnaire
                    WorkerTab.HISTORY -> SamiIcons.Clock
                    WorkerTab.RECOMMENDATIONS -> SamiIcons.Guidance
                }, null) },
                label = {
                    val label = when (tab) {
                        WorkerTab.HOME -> "Inicio"
                        WorkerTab.TESTS -> if (largeText) "Tests" else "Pruebas"
                        WorkerTab.QUESTIONNAIRES -> "Escalas"
                        WorkerTab.HISTORY -> if (largeText) "Hist." else "Historial"
                        WorkerTab.RECOMMENDATIONS -> if (largeText) "Guía" else "Consejos"
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = BrandBlue,
                    selectedTextColor = BrandBlue, indicatorColor = SoftBlue),
            )
        }
    }
}


@Composable
internal fun WorkerProfileMenu(enabled: Boolean, changeShift: () -> Unit, changeWorker: () -> Unit, changeLabel: String = "Cambiar perfil") {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
        TextButton({ menuExpanded = true }, enabled = enabled) {
            Icon(SamiIcons.Person, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Perfil y turno")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(text = { Text("Cambiar turno") }, enabled = enabled,
                onClick = { menuExpanded = false; changeShift() })
            DropdownMenuItem(text = { Text(changeLabel) }, enabled = enabled,
                onClick = { menuExpanded = false; changeWorker() })
        }
    }
}
