package dev.studios.samimine.app

import androidx.lifecycle.viewModelScope
import dev.studios.samimine.app.navigation.*
import dev.studios.samimine.application.usecase.EvaluationUseCases
import dev.studios.samimine.domain.evaluation.pvt.*
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Synthetic instrument content is confined to test fixtures, never production. */
@OptIn(ExperimentalCoroutinesApi::class)
class BriefViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var model: SamiMineViewModel
    private lateinit var store: Store
    private fun fixture(id: InstrumentId): InstrumentDefinition {
        val range = if (id == InstrumentId.BORG) 6..20 else 1..9
        return InstrumentDefinition(id, "synthetic-fixture-v1", "es-PE", "Fixture instructions", "Fixture period",
            listOf(InstrumentQuestion("fixture-question", "Fixture question", range.map { AnswerOption("$it", "Fixture $it", it) })),
            true, "Fixture permission", "Fixture approval")
    }
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        store = Store()
        model = SamiMineViewModel(store, EvaluationUseCases(store), MonotonicClock { dispatcher.scheduler.currentTime * 1_000_000 },
            cognitiveUptimeMillis = { dispatcher.scheduler.currentTime }, instruments = InstrumentId.entries.map { fixture(it) })
        tick()
        model.selectWorker(Worker("a", "Demo", "Area", "Demo")); model.selectShift(Shift.NIGHT)
    }
    @After fun teardown() { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    private fun tick(ms: Long = 0) { dispatcher.scheduler.advanceTimeBy(ms); dispatcher.scheduler.runCurrent() }

    @Test fun omittedKssLinksToPvtAndCompletedBriefWithoutCombiningScores() {
        model.startBrief(); tick()
        assertEquals(Destination.QUESTIONNAIRE, model.state.value.destination)
        model.submitReport(emptyMap(), null, null, omitted = true); tick()
        val report = store.reports.single()
        assertTrue(report.omitted)
        assertTrue(report.answers.isEmpty())
        assertEquals(store.parent!!.id, report.assessmentId)
        assertEquals(Destination.INSTRUCTIONS, model.state.value.destination)
        model.start(); tick()
        assertEquals(store.pvt!!.id, store.parent!!.pvtId)
        repeat(3) {
            tick(5_000)
            model.stimulusDrawn(); tick(200)
            model.respond(); tick(400)
        }
        assertEquals(Destination.RESULT, model.state.value.destination)
        assertEquals("COMPLETED", store.parent!!.status)
        model.finishPvtResult(); tick()
        assertEquals(Destination.ASSESSMENT_RESULT, model.state.value.destination)
        assertEquals(report, model.state.value.assessmentReports.single())
        assertEquals(3, model.state.value.evaluation!!.metrics!!.responses)
        model.selectTab(WorkerTab.HISTORY); tick()
        assertEquals("COMPLETED", store.parent!!.status)
    }

    @Test fun leavingCompletedPvtResultDoesNotReclassifyBriefAsIncomplete() {
        model.startBrief(); tick()
        model.submitReport(emptyMap(), null, null, omitted = true); tick()
        model.start(); tick()
        repeat(3) { tick(5_000); model.stimulusDrawn(); tick(200); model.respond(); tick(400) }
        assertEquals(Destination.RESULT, model.state.value.destination)
        model.selectTab(WorkerTab.HISTORY); tick()
        assertEquals("COMPLETED", store.parent!!.status)
        assertEquals(Destination.HISTORY, model.state.value.destination)
    }

    @Test fun leavingBeforePvtKeepsBriefIncompleteAndSelfReportIntact() {
        model.startBrief(); tick()
        model.submitReport(mapOf("fixture-question" to "3"), null, null); tick()
        assertEquals(3, store.reports.single().answers["fixture-question"])
        model.home(); tick()
        assertEquals("INCOMPLETE", store.parent!!.status)
        assertNull(store.parent!!.pvtId)
        assertEquals(Destination.HOME, model.state.value.destination)
        assertEquals(1, store.reports.size)
    }

    @Test fun borgIsOutsideScopeAndCannotStartOrSaveAQuestionnaire() {
        model.openInstrument(InstrumentId.BORG)
        model.submitReport(mapOf("fixture-question" to "8"), null, null); tick()
        assertTrue(store.reports.isEmpty())
        model.submitReport(emptyMap(), "Fixture task", "Fixture moment", omitted = true); tick()
        assertTrue(store.reports.isEmpty())
        model.submitReport(mapOf("fixture-question" to "8"), "Fixture task", "Fixture moment"); tick()
        assertEquals(Destination.HOME, model.state.value.destination)
        assertTrue(store.reports.isEmpty())
        assertFalse(BatteryCatalog.modules(model.instruments).any { it.id == "BORG" })
    }

    @Test fun invalidOptionNeverCreatesSelfReport() {
        model.openInstrument(InstrumentId.KSS)
        model.submitReport(mapOf("fixture-question" to "100"), null, null); tick()
        assertTrue(store.reports.isEmpty())
        assertEquals(Destination.QUESTIONNAIRE, model.state.value.destination)
    }

    @Test fun switchingTabFromBriefInstructionsPersistsInterruptionBeforeShowingHistory() {
        model.startBrief(); tick()
        model.submitReport(mapOf("fixture-question" to "3"), null, null); tick()
        model.selectTab(WorkerTab.HISTORY)
        assertTrue(model.state.value.busy)
        assertEquals(Destination.INSTRUCTIONS, model.state.value.destination)
        tick()
        assertEquals("INCOMPLETE", store.parent!!.status)
        assertEquals(Destination.HISTORY, model.state.value.destination)
        assertEquals(1, model.state.value.selfReportHistory.size)
        assertFalse(model.state.value.briefActive)
    }

    @Test fun availableQuestionnaireResultReturnsToItsCatalog() {
        model.selectTab(WorkerTab.QUESTIONNAIRES)
        model.openInstrument(InstrumentId.KSS)
        model.submitReport(mapOf("fixture-question" to "3"), null, null); tick()
        assertEquals(Destination.SELF_REPORT_RESULT, model.state.value.destination)
        model.home()
        assertEquals(Destination.QUESTIONNAIRES, model.state.value.destination)
    }

    private class Store : EvaluationRepository, BatteryRepository {
        var parent: AssessmentSession? = null
        var pvt: Evaluation? = null
        val reports = mutableListOf<SelfReport>()
        override suspend fun recoverInterrupted() = Unit
        override suspend fun recoverAssessments() = Unit
        override suspend fun start(evaluation: Evaluation) { pvt = evaluation }
        override suspend fun saveAttempt(evaluationId: String, attempt: Attempt) { pvt = pvt!!.copy(attempts = pvt!!.attempts + attempt) }
        override suspend fun finish(evaluationId: String) {
            pvt = pvt!!.copy(status = EvaluationStatus.COMPLETED, metrics = PvtScoring.calculate(pvt!!.attempts))
            parent = parent?.copy(status = "COMPLETED")
        }
        override suspend fun interrupt(evaluationId: String, reason: String) { pvt = pvt!!.copy(status = EvaluationStatus.INTERRUPTED, interruptionReason = reason); parent = parent?.copy(status = "INCOMPLETE") }
        override suspend fun get(evaluationId: String, workerId: String) = pvt?.takeIf { it.id == evaluationId && it.worker.id == workerId }
        override fun observeHistory(workerId: String) = flowOf(listOfNotNull(pvt?.takeIf { it.worker.id == workerId }))
        override suspend fun startAssessment(session: AssessmentSession) { parent = session }
        override suspend fun linkAssessmentPvt(id: String, workerId: String, pvtId: String) { parent = parent!!.copy(pvtId = pvtId) }
        override suspend fun interruptAssessment(id: String, workerId: String, reason: String) { parent = parent!!.copy(status = "INCOMPLETE", reason = reason) }
        override suspend fun getAssessment(id: String, workerId: String) = parent?.takeIf { it.id == id && it.workerId == workerId }
        override fun observeAssessments(workerId: String) = flowOf(listOfNotNull(parent?.takeIf { it.workerId == workerId }))
        override suspend fun saveSelfReport(report: SelfReport) { reports += report }
        override suspend fun getSelfReport(id: String, workerId: String) = reports.firstOrNull { it.id == id && it.workerId == workerId }
        override fun observeSelfReports(workerId: String) = flowOf(reports.filter { it.workerId == workerId })
    }
}
