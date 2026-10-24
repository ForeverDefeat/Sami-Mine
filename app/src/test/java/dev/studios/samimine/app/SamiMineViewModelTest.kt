package dev.studios.samimine.app

import androidx.lifecycle.viewModelScope
import dev.studios.samimine.app.navigation.*
import dev.studios.samimine.application.usecase.EvaluationUseCases
import dev.studios.samimine.domain.evaluation.pvt.*
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.repository.EvaluationRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SamiMineViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository
    private lateinit var model: SamiMineViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        model = SamiMineViewModel(repository, EvaluationUseCases(repository), MonotonicClock { dispatcher.scheduler.currentTime * 1_000_000 })
        dispatcher.scheduler.runCurrent()
        model.selectWorker(Worker("a", "Demo", "Demo", "Demo"))
        model.selectShift(Shift.DAY)
        model.instructions()
    }
    @After fun teardown() { model.viewModelScope.cancel(); Dispatchers.resetMain() }

    private fun start() { model.start(); dispatcher.scheduler.runCurrent() }

    @Test fun unansweredSignalsRequireExplicitContinuationEvenAtTheLastSignal() {
        start()
        val id = repository.value!!.id
        repeat(3) { position ->
            dispatcher.scheduler.advanceTimeBy(5_000); dispatcher.scheduler.runCurrent()
            model.stimulusDrawn()
            dispatcher.scheduler.advanceTimeBy(3_000); dispatcher.scheduler.runCurrent()
            assertEquals(PvtPhase.AWAITING_CONTINUE, model.state.value.phase)
            dispatcher.scheduler.advanceTimeBy(60_000); dispatcher.scheduler.runCurrent()
            assertEquals(Destination.PVT, model.state.value.destination)
            assertEquals(position + 1, model.state.value.attemptNumber)
            assertEquals(position + 1, repository.value!!.attempts.size)
            model.respond(id, position + 1)
            model.continuePvt("old-evaluation", position + 1)
            assertEquals(PvtPhase.AWAITING_CONTINUE, model.state.value.phase)
            model.continuePvt(id, position + 1)
            dispatcher.scheduler.runCurrent()
            model.continuePvt(id, position + 1)
            dispatcher.scheduler.runCurrent()
        }
        assertEquals(Destination.RESULT, model.state.value.destination)
        assertEquals(3, repository.value!!.attempts.size)
        assertTrue(repository.value!!.attempts.all { it.outcome == AttemptOutcome.OMISSION })
        assertEquals(0, model.state.value.evaluation!!.metrics!!.responses)
    }

    @Test fun failedWriteRetriesSameAttemptWithoutFalseResultOrDuplicate() {
        start()
        repository.failAfterWrite = true
        model.respond()
        dispatcher.scheduler.runCurrent()
        assertNotNull(model.state.value.error)
        assertEquals(Destination.PVT, model.state.value.destination)
        assertEquals(1, repository.value!!.attempts.size)
        model.respond()
        model.retry()
        dispatcher.scheduler.advanceTimeBy(401)
        dispatcher.scheduler.runCurrent()
        assertNull(model.state.value.error)
        assertEquals(1, repository.value!!.attempts.size)
        assertEquals(2, model.state.value.attemptNumber)
        model.interrupt("test end")
        dispatcher.scheduler.runCurrent()
    }

    @Test fun interruptDuringFailedSaveRetainsAttemptAndNeverResumes() {
        start()
        repository.failAfterWrite = true
        model.respond()
        dispatcher.scheduler.runCurrent()
        model.onBackground()
        model.retry()
        dispatcher.scheduler.runCurrent()
        assertEquals(EvaluationStatus.INTERRUPTED, model.state.value.evaluation!!.status)
        assertEquals(1, repository.value!!.attempts.size)
        assertEquals(Destination.RESULT, model.state.value.destination)
        assertNull(model.state.value.evaluation!!.metrics)
    }

    @Test fun completionRequiresThreeSavedAttemptsAndNoResponsesMeansNullMean() {
        start()
        repeat(3) {
            model.respond()
            dispatcher.scheduler.advanceTimeBy(401)
            dispatcher.scheduler.runCurrent()
        }
        assertEquals(Destination.RESULT, model.state.value.destination)
        assertEquals(Metrics(null, 0, 3, 0), model.state.value.evaluation!!.metrics)
        assertEquals(EvaluationStatus.COMPLETED, repository.value!!.status)
    }

    @Test fun startFailureDoesNotCreateANewIdOnRetry() {
        repository.failStart = true
        start()
        val id = repository.value!!.id
        assertEquals(Destination.INSTRUCTIONS, model.state.value.destination)
        model.onBackground()
        model.retry()
        dispatcher.scheduler.runCurrent()
        assertEquals(id, repository.value!!.id)
        assertEquals(EvaluationStatus.INTERRUPTED, repository.value!!.status)
    }

    @Test fun stalePvtCallbacksCannotRespondToOrDrawTheNextAttempt() {
        start()
        val id = repository.value!!.id
        model.respond(id, 1)
        dispatcher.scheduler.advanceTimeBy(401)
        dispatcher.scheduler.runCurrent()
        model.respond(id, 1)
        model.respond("old-evaluation", 2)
        model.stimulusDrawn(id, 1)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, repository.value!!.attempts.size)
        assertEquals(2, model.state.value.attemptNumber)
        assertEquals(PvtPhase.WAITING, model.state.value.phase)
        model.interrupt("test end")
        dispatcher.scheduler.runCurrent()
    }

    @Test fun activitiesReturnToOriginAndAndroidBackGoesHomeThenShift() {
        model.selectTab(WorkerTab.TESTS)
        model.instructions()
        model.back()
        assertEquals(Destination.TESTS, model.state.value.destination)
        model.back()
        assertEquals(Destination.HOME, model.state.value.destination)
        model.back()
        assertEquals(Destination.SHIFT, model.state.value.destination)
    }

    @Test fun recommendationsOpenWithoutResultsAndBackReturnsHomeKeepingShift() {
        val shift = model.state.value.shift
        model.selectTab(WorkerTab.RECOMMENDATIONS)
        assertEquals(Destination.RECOMMENDATIONS, model.state.value.destination)
        assertEquals(WorkerTab.RECOMMENDATIONS, model.state.value.selectedTab)
        assertTrue(model.state.value.history.isEmpty())
        model.back()
        assertEquals(Destination.HOME, model.state.value.destination)
        assertEquals(shift, model.state.value.shift)
    }

    @Test fun pendingQuestionnaireReturnsToQuestionnairesAndChangingProfileClearsContext() {
        model.selectTab(WorkerTab.QUESTIONNAIRES)
        model.openInstrument(InstrumentId.KSS)
        model.back()
        assertEquals(Destination.QUESTIONNAIRES, model.state.value.destination)
        model.changeShift()
        assertNull(model.state.value.shift)
        model.selectShift(Shift.NIGHT)
        assertEquals(WorkerTab.HOME, model.state.value.selectedTab)
        model.changeWorker()
        assertEquals(Destination.IDENTIFICATION, model.state.value.destination)
        assertNull(model.state.value.worker)
        assertTrue(model.state.value.history.isEmpty())
    }

    @Test fun navigationIsBlockedDuringExecutionBusyAndStorageFailure() {
        model.selectTab(WorkerTab.TESTS)
        model.instructions()
        repository.failStart = true
        model.start()
        model.selectTab(WorkerTab.HOME)
        assertEquals(Destination.INSTRUCTIONS, model.state.value.destination)
        dispatcher.scheduler.runCurrent()
        model.selectTab(WorkerTab.HOME)
        assertEquals(WorkerTab.TESTS, model.state.value.selectedTab)
        assertNotNull(model.state.value.error)
        model.retry()
        dispatcher.scheduler.runCurrent()
        model.selectTab(WorkerTab.HOME)
        assertEquals(Destination.PVT, model.state.value.destination)
        model.interrupt("test end")
        dispatcher.scheduler.runCurrent()
        model.finishPvtResult()
        assertEquals(Destination.TESTS, model.state.value.destination)
    }

    @Test fun historyDetailsReturnToHistoryAndRemainFilteredAfterProfileSwitch() {
        start()
        model.interrupt("test end")
        dispatcher.scheduler.runCurrent()
        val id = model.state.value.evaluation!!.id
        model.selectTab(WorkerTab.HISTORY)
        dispatcher.scheduler.runCurrent()
        model.detail(id)
        dispatcher.scheduler.runCurrent()
        model.back()
        assertEquals(Destination.HISTORY, model.state.value.destination)
        model.selectTab(WorkerTab.HOME)
        model.changeWorker()
        model.selectWorker(Worker("b", "Other", "Demo", "Demo"))
        model.selectShift(Shift.DAY)
        model.selectTab(WorkerTab.HISTORY)
        dispatcher.scheduler.runCurrent()
        assertTrue(model.state.value.history.isEmpty())
    }

    @Test fun repeatingPvtKeepsTestsAsTheReturnDestination() {
        model.selectTab(WorkerTab.TESTS)
        model.instructions()
        start()
        model.interrupt("test end")
        dispatcher.scheduler.runCurrent()
        model.restartPvt()
        assertEquals(WorkerTab.TESTS, model.state.value.activityOrigin)
        assertEquals("Volver a pruebas", model.state.value.activityOrigin.returnLabel)
        model.start()
        dispatcher.scheduler.runCurrent()
        model.interrupt("test end")
        dispatcher.scheduler.runCurrent()
        model.finishPvtResult()
        assertEquals(Destination.TESTS, model.state.value.destination)
    }

    private class FakeRepository : EvaluationRepository {
        var value: Evaluation? = null
        var failAfterWrite = false
        var failStart = false
        override suspend fun recoverInterrupted() = Unit
        override suspend fun start(evaluation: Evaluation) {
            value = evaluation
            if (failStart) { failStart = false; error("disk") }
        }
        override suspend fun saveAttempt(evaluationId: String, attempt: Attempt) {
            if (value!!.attempts.none { it.index == attempt.index }) value = value!!.copy(attempts = value!!.attempts + attempt)
            if (failAfterWrite) { failAfterWrite = false; error("disk") }
        }
        override suspend fun finish(evaluationId: String) {
            check(value!!.attempts.size == 3)
            value = value!!.copy(status = EvaluationStatus.COMPLETED, metrics = PvtScoring.calculate(value!!.attempts))
        }
        override suspend fun interrupt(evaluationId: String, reason: String) { value = value!!.copy(status = EvaluationStatus.INTERRUPTED, interruptionReason = reason) }
        override suspend fun get(evaluationId: String, workerId: String) = value?.takeIf { it.worker.id == workerId }
        override fun observeHistory(workerId: String): Flow<List<Evaluation>> = flowOf(listOfNotNull(value?.takeIf { it.worker.id == workerId }))
    }
}
