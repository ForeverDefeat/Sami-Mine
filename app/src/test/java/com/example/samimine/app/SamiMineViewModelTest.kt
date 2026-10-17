package com.example.samimine.app

import com.example.samimine.app.navigation.Destination
import com.example.samimine.application.usecase.EvaluationUseCases
import com.example.samimine.domain.evaluation.pvt.*
import com.example.samimine.domain.model.*
import com.example.samimine.domain.repository.EvaluationRepository
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
    @After fun teardown() { Dispatchers.resetMain() }

    private fun start() { model.start(); dispatcher.scheduler.runCurrent() }

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
