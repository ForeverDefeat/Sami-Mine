package com.example.samimine.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samimine.app.navigation.Destination
import com.example.samimine.data.demo.DemoWorkers
import com.example.samimine.domain.model.*
import com.example.samimine.ui.components.*
import com.example.samimine.ui.screens.identification.IdentificationScreen
import com.example.samimine.ui.screens.shift.ShiftScreen
import com.example.samimine.ui.screens.home.HomeScreen
import com.example.samimine.ui.screens.instructions.InstructionsScreen
import com.example.samimine.ui.screens.pvt.PvtScreen
import com.example.samimine.ui.screens.result.ResultScreen
import com.example.samimine.ui.screens.history.HistoryScreen
import com.example.samimine.ui.screens.cognitive.*
import com.example.samimine.ui.theme.*

@Composable
fun SamiMineApp(model: SamiMineViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler(state.destination != Destination.IDENTIFICATION) { model.back() }
    val dark = state.destination == Destination.PVT
    SamiMineTheme {
        Scaffold(containerColor = if (dark) TestBackground else PageBackground) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                if (state.destination != Destination.IDENTIFICATION) {
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
                    } else when (state.destination) {
                        Destination.IDENTIFICATION -> IdentificationScreen(DemoWorkers.all, !state.busy && state.error == null, model::selectWorker)
                        Destination.SHIFT -> ShiftScreen(state.worker!!, model::selectShift)
                        Destination.HOME -> HomeScreen(state.worker!!, state.shift!!, model::instructions, model::history, model::changeWorker, model::chooseCognitiveTest)
                        Destination.INSTRUCTIONS -> InstructionsScreen(!state.busy && state.error == null, model::start)
                        Destination.PVT -> {
                            val evaluationId = state.evaluation!!.id
                            val index = state.attemptNumber
                            PvtScreen(state.phase, index, state.evaluation!!.configuration.attempts,
                                !state.busy && state.error == null,
                                { model.stimulusDrawn(evaluationId, index) }, { model.respond(evaluationId, index) })
                        }
                        Destination.RESULT -> ResultScreen(state.evaluation!!, state.resultFromHistory, { if (state.resultFromHistory) model.history() else model.home() }, model::instructions)
                        Destination.HISTORY -> HistoryScreen(state.worker!!, state.history, !state.busy && state.error == null, model::openCognitiveDetail, state.cognitiveHistory, model::detail)
                        Destination.COGNITIVE_INSTRUCTIONS -> CognitiveInstructionsScreen(state.cognitiveType!!, model::home, model::beginCognitivePractice, !state.busy && state.error == null)
                        Destination.COGNITIVE_PRACTICE -> {
                            val sessionId = state.cognitiveSession!!.id
                            val trialIndex = state.cognitiveTrials[state.cognitiveTrialPosition].index
                            CognitivePracticeScreen(
                            state.cognitiveType!!, state.cognitiveTrials[state.cognitiveTrialPosition], state.cognitiveTrialPosition,
                            state.cognitiveTrials.count { it.isPractice }, state.cognitiveFeedback, !state.busy && state.error == null && state.cognitivePhase == CognitivePhase.PRACTICE,
                            { model.answerCognitivePractice(it, sessionId, trialIndex) },
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
                        )
                        }
                        Destination.COGNITIVE_RESULT -> state.cognitiveSession?.let { session ->
                            CognitiveResultScreen(session, state.resultFromHistory,
                                { if (state.resultFromHistory) model.history() else model.home() },
                                { model.prepareCognitiveChoice(session.type) })
                        }
                    }
                }
            }
        }
    }
}
