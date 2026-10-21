package dev.studios.samimine.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.repository.RoomEvaluationRepository
import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class BatteryPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SamiMineDatabase
    private lateinit var repository: RoomEvaluationRepository
    private val name = "battery-${UUID.randomUUID()}.db"
    @Before fun setup() { open() }
    private fun open() {
        database = Room.databaseBuilder(context, SamiMineDatabase::class.java, name).addMigrations(SamiMineDatabase.MIGRATION_3_4, SamiMineDatabase.MIGRATION_4_5).build()
        repository = RoomEvaluationRepository(database)
    }
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }

    @Test fun versionThreeMigrationPreservesOldCognitiveMetricsAndAddsNewTables() = runBlocking {
        database.close()
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("dev.studios.samimine.data.local.database.SamiMineDatabase/3.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            fun sql(value: String) = value.replace("\${TABLE_NAME}", entity.getString("tableName"))
            legacy.execSQL(sql(entity.getString("createSql")))
            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
            for (j in 0 until indices.length()) legacy.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
        }
        legacy.execSQL("INSERT INTO cognitive_sessions VALUES ('old','a','Demo','Area','Demo','DAY','FLANKER','demo-flanker-v1','{}',42,123,456,'COMPLETED',NULL)")
        legacy.execSQL("INSERT INTO cognitive_trials VALUES ('old',1,0,'CONGRUENT_LEFT','LEFT','LEFT','HIT',300,100,400,NULL)")
        legacy.version = 3
        legacy.close()
        open()
        val saved = repository.getCognitiveTest("old", "a")!!
        assertEquals("legacy-v1", saved.calculationVersion)
        assertEquals("demo-flanker-v1", saved.protocolVersion)
        assertEquals(300.0, saved.metrics!!.values["congruentMeanRtMs"]!!, 0.0)
        assertFalse(saved.metrics!!.values.containsKey("congruentMedianRtMs"))
        assertTrue(repository.observeAssessments("a").first().isEmpty())
        assertTrue(repository.observeSelfReports("a").first().isEmpty())
    }

    @Test fun briefReportAndPvtCloseAtomicallyAndStayProfileScoped() = runBlocking {
        val parent = AssessmentSession("brief", "a", Shift.NIGHT, 100)
        val report = SelfReport("kss", "a", Shift.NIGHT, parent.id, InstrumentId.KSS, "fixture-v1", "es-PE", 101, emptyMap(), true, null, null)
        val pvt = Evaluation("pvt", Worker("a", "Demo", "Area", "Demo"), Shift.NIGHT, 102, PvtConfiguration())
        repository.startAssessment(parent)
        repository.saveSelfReport(report); repository.saveSelfReport(report)
        repository.start(pvt); repository.linkAssessmentPvt(parent.id, "a", pvt.id)
        repeat(3) { repository.saveAttempt(pvt.id, Attempt(it + 1, AttemptOutcome.RESPONSE, 200_000_000)) }
        repository.finish(pvt.id)
        database.close(); open()
        assertEquals("COMPLETED", repository.getAssessment(parent.id, "a")!!.status)
        assertEquals(pvt.id, repository.getAssessment(parent.id, "a")!!.pvtId)
        assertTrue(repository.getSelfReport(report.id, "a")!!.omitted)
        assertNull(repository.getSelfReport(report.id, "b"))
        assertNull(repository.getAssessment(parent.id, "b"))
        assertTrue(repository.observeAssessments("b").first().isEmpty())
        assertEquals(1, repository.observeSelfReports("a").first().size)
    }

    @Test fun associationCannotCompleteBeforeFullMinuteOrAcceptLateResponses() = runBlocking {
        val session = CognitiveTestSession("dsst", "a", "Demo", "Area", "Demo", Shift.DAY, CognitiveTestType.DSST,
            CognitiveTestType.DSST.version, "{}", 42, 100, calculationVersion = "descriptive-v2")
        repository.startCognitiveTest(session)
        repeat(6) { i ->
            val trial = CognitiveTrials.dsstTrial(i, 42)
            repository.saveCognitiveTrial(session.id, CognitiveTrialRecord(i + 1, true, trial.stimulusCode, trial.expectedResponse, trial.expectedResponse, "PRACTICE_CORRECT", null, null, null))
        }
        repository.recordExecutionTimes(session.id, 1_000, 60_999)
        assertTrue(runCatching { repository.finishCognitiveTest(session.id) }.isFailure)
        repository.recordExecutionTimes(session.id, 1_000, 61_000)
        repository.finishCognitiveTest(session.id); repository.finishCognitiveTest(session.id)
        repository.recordExecutionTimes(session.id, 1_000, 61_000)
        assertTrue(runCatching { repository.recordExecutionTimes(session.id, 1_000, 62_000) }.isFailure)
        assertEquals(CognitiveSessionStatus.COMPLETED, repository.getCognitiveTest(session.id, "a")!!.status)
        val snapshots = database.battery().metrics(session.id)
        assertEquals(0.0, snapshots.first { it.key == "correctPerMinute" }.value!!, 0.0)
        assertTrue(snapshots.all { it.methodVersion == "descriptive-v2" })
    }

    @Test fun technicalInvalidationAndRecoveryDoNotFabricateMetrics() = runBlocking {
        val parent = AssessmentSession("partial", "a", Shift.DAY, 100)
        repository.startAssessment(parent)
        val session = CognitiveTestSession("partial-test", "a", "Demo", "Area", "Demo", Shift.DAY,
            CognitiveTestType.N_BACK, CognitiveTestType.N_BACK.version, "{}", 42, 100, calculationVersion = "descriptive-v2")
        repository.startCognitiveTest(session)
        repository.invalidateCognitiveTest(session.id, "Pérdida de foco")
        repository.recoverAssessments(); repository.recoverCognitiveTests()
        assertEquals("INCOMPLETE", repository.getAssessment(parent.id, "a")!!.status)
        assertEquals(CognitiveSessionStatus.INVALID, repository.getCognitiveTest(session.id, "a")!!.status)
        assertNull(repository.getCognitiveTest(session.id, "a")!!.metrics)
        assertTrue(database.battery().metrics(session.id).isEmpty())
    }
}
