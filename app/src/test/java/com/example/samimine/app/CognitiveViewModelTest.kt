package com.example.samimine.app

import androidx.lifecycle.viewModelScope
import com.example.samimine.app.navigation.Destination
import com.example.samimine.application.usecase.EvaluationUseCases
import com.example.samimine.domain.evaluation.pvt.MonotonicClock
import com.example.samimine.domain.model.*
import com.example.samimine.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CognitiveViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: Store
    private lateinit var model: SamiMineViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        store = Store()
        model = SamiMineViewModel(store, EvaluationUseCases(store), MonotonicClock { 0 }, store,
            { dispatcher.scheduler.currentTime })
        dispatcher.scheduler.runCurrent()
        model.selectWorker(Worker("a", "Demo", "Area", "Demo"))
        model.selectShift(Shift.DAY)
    }
    @After fun teardown() { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    private fun tick(ms: Long = 0) { dispatcher.scheduler.advanceTimeBy(ms); dispatcher.scheduler.runCurrent() }
    private fun prepare(type: CognitiveTestType) {
        model.chooseCognitiveTest(type)
        model.beginCognitivePractice(); tick()
        repeat(CognitiveTestConfig.forType(type).practiceTrials) {
            val trial = model.state.value.cognitiveTrials[model.state.value.cognitiveTrialPosition]
            model.answerCognitivePractice(trial.expectedResponse); tick()
        }
        assertEquals(Destination.COGNITIVE_READY, model.state.value.destination)
        model.startCognitiveScoredTrials(); tick()
    }
    private fun stimulus(): CognitiveTrial {
        val s = model.state.value
        val trial = s.cognitiveTrials[s.cognitiveTrialPosition]
        tick(CognitiveTestConfig.forType(s.cognitiveType!!).fixationMillis)
        assertEquals(CognitivePhase.STIMULUS, model.state.value.cognitivePhase)
        model.cognitiveStimulusDrawn(store.session!!.id, trial.index, dispatcher.scheduler.currentTime)
        tick()
        return trial
    }
    private fun respond(trial: CognitiveTrial) {
        model.answerCognitive(store.session!!.id, trial.index, trial.expectedResponse ?: "RESPOND", dispatcher.scheduler.currentTime)
        tick()
    }
    @Test fun unattendedRunsNeverInventTouchResponses() {
        for (type in CognitiveTestType.values()) {
            prepare(type)
            val config = CognitiveTestConfig.forType(type)
            repeat(config.scoredTrials) {
                val trial = stimulus()
                tick(config.responseWindowMillis - 1)
                assertEquals(CognitivePhase.STIMULUS, model.state.value.cognitivePhase)
                tick(1)
                if (it < config.scoredTrials - 1) tick(trial.interTrialDelayMillis!!)
            }
            assertEquals(Destination.COGNITIVE_RESULT, model.state.value.destination)
            val scored = store.session!!.trials.filterNot { it.isPractice }
            assertEquals(config.scoredTrials, scored.size)
            assertTrue(scored.all { it.actualResponse == null && it.reactionTimeMillis == null })
            if (type == CognitiveTestType.GO_NO_GO) {
                assertEquals(16, scored.count { it.outcomeCode == "GO_OMISSION" })
                assertEquals(4, scored.count { it.outcomeCode == "NO_GO_CORRECT_REJECTION" })
            } else assertTrue(scored.all { it.outcomeCode == "OMISSION" })
        }
    }
    @Test fun staleDrawTouchDuplicateAndOldSessionCannotResolveNextTrial() {
        prepare(CognitiveTestType.FLANKER)
        val first = stimulus()
        val id = store.session!!.id
        respond(first)
        val count = store.session!!.trials.size
        model.answerCognitive(id, first.index, "LEFT", dispatcher.scheduler.currentTime); tick()
        assertEquals(count, store.session!!.trials.size)
        tick(first.interTrialDelayMillis!!)
        val next = model.state.value.cognitiveTrials[model.state.value.cognitiveTrialPosition]
        model.cognitiveStimulusDrawn(id, first.index, dispatcher.scheduler.currentTime)
        model.answerCognitive(id, first.index, "LEFT", dispatcher.scheduler.currentTime)
        model.answerCognitive("old-session", next.index, "LEFT", dispatcher.scheduler.currentTime)
        tick(400)
        assertEquals(count, store.session!!.trials.size)
        model.cognitiveStimulusDrawn(id, next.index, dispatcher.scheduler.currentTime)
        tick(30); respond(next)
        assertEquals(30L, store.session!!.trials.last().reactionTimeMillis)
    }
    @Test fun failedWriteRetriesOriginalRecordAndBackgroundNeverResumes() {
        prepare(CognitiveTestType.GO_NO_GO)
        val trial = stimulus()
        store.failAfterWrite = true
        respond(trial)
        assertNotNull(model.state.value.error)
        val count = store.session!!.trials.size
        model.answerCognitive(store.session!!.id, trial.index, "RESPOND", dispatcher.scheduler.currentTime)
        model.onBackground()
        model.retry(); tick()
        assertEquals(count, store.session!!.trials.size)
        assertEquals(CognitiveSessionStatus.INCOMPLETE, store.session!!.status)
        assertEquals(Destination.COGNITIVE_RESULT, model.state.value.destination)
        assertNull(store.session!!.metrics)
        model.onForeground(); tick(10_000)
        assertEquals(Destination.COGNITIVE_RESULT, model.state.value.destination)
    }
    @Test fun interruptionDuringSuspendedSaveStopsTransitionAndCompletion() {
        prepare(CognitiveTestType.FLANKER)
        val trial = stimulus()
        store.saveDelay = 100
        respond(trial)
        model.onBackground(); tick(100)
        assertEquals(CognitiveSessionStatus.INCOMPLETE, store.session!!.status)
        assertEquals(3, store.session!!.trials.size)
        assertNull(store.session!!.metrics)
        tick(10_000)
        assertEquals(Destination.COGNITIVE_RESULT, model.state.value.destination)
    }
    @Test fun anticipationAndTimeoutRaceHaveExactlyOneOutcome() {
        prepare(CognitiveTestType.GO_NO_GO)
        val trial = model.state.value.cognitiveTrials[model.state.value.cognitiveTrialPosition]
        respond(trial)
        assertEquals("ANTICIPATION", store.session!!.trials.last().outcomeCode)
        tick(trial.interTrialDelayMillis!!)
        val second = stimulus()
        tick(3_000)
        val count = store.session!!.trials.size
        model.answerCognitive(store.session!!.id, second.index, "RESPOND", dispatcher.scheduler.currentTime)
        tick()
        assertEquals(count, store.session!!.trials.size)
    }
    @Test fun practiceErrorBlocksInputsAndRetryDoesNotDuplicateOrSkip() {
        model.chooseCognitiveTest(CognitiveTestType.FLANKER)
        model.beginCognitivePractice(); tick()
        val first = model.state.value.cognitiveTrials.first()
        store.failAfterWrite = true
        model.answerCognitivePractice(first.expectedResponse); tick()
        model.answerCognitivePractice(first.expectedResponse); tick()
        assertEquals(0, model.state.value.cognitiveTrialPosition)
        assertEquals(1, store.session!!.trials.size)
        model.retry(); tick()
        model.answerCognitivePractice("LEFT", store.session!!.id, first.index); tick()
        assertEquals(1, model.state.value.cognitiveTrialPosition)
        assertEquals(1, store.session!!.trials.size)
    }
    @Test fun backgroundDuringStartPersistsIncompletePractice() {
        model.chooseCognitiveTest(CognitiveTestType.FLANKER)
        store.startDelay = 100
        model.beginCognitivePractice(); tick()
        model.onBackground(); tick(100)
        assertEquals(CognitiveSessionStatus.INCOMPLETE, store.session!!.status)
        assertEquals(Destination.COGNITIVE_RESULT, model.state.value.destination)
    }

    private class Store : EvaluationRepository, CognitiveTestRepository {
        var session: CognitiveTestSession? = null
        var failAfterWrite = false
        var saveDelay = 0L
        var startDelay = 0L
        override suspend fun recoverInterrupted() = Unit
        override suspend fun recoverCognitiveTests() = Unit
        override suspend fun startCognitiveTest(session: CognitiveTestSession) { delay(startDelay); this.session = session }
        override suspend fun saveCognitiveTrial(sessionId: String, trial: CognitiveTrialRecord) {
            delay(saveDelay)
            val s = session!!
            val old = s.trials.find { it.index == trial.index }
            if (old != null) check(old == trial) else {
                check(s.status == CognitiveSessionStatus.IN_PROGRESS)
                check(trial.index == s.trials.size + 1)
                session = s.copy(trials = s.trials + trial)
            }
            if (failAfterWrite) { failAfterWrite = false; error("disk") }
        }
        override suspend fun finishCognitiveTest(sessionId: String) {
            check(session!!.trials.size == CognitiveTestConfig.forType(session!!.type).let { it.practiceTrials + it.scoredTrials })
            session = session!!.copy(status = CognitiveSessionStatus.COMPLETED)
        }
        override suspend fun interruptCognitiveTest(sessionId: String, reason: String) {
            if (session!!.status == CognitiveSessionStatus.IN_PROGRESS)
                session = session!!.copy(status = CognitiveSessionStatus.INCOMPLETE, interruptionReason = reason)
        }
        override suspend fun getCognitiveTest(sessionId: String, workerId: String) = session
        override fun observeCognitiveHistory(workerId: String) = flowOf(listOfNotNull(session))
        override suspend fun start(evaluation: Evaluation) = Unit
        override suspend fun saveAttempt(evaluationId: String, attempt: Attempt) = Unit
        override suspend fun finish(evaluationId: String) = Unit
        override suspend fun interrupt(evaluationId: String, reason: String) = Unit
        override suspend fun get(evaluationId: String, workerId: String): Evaluation? = null
        override fun observeHistory(workerId: String): Flow<List<Evaluation>> = flowOf(emptyList())
    }
}
