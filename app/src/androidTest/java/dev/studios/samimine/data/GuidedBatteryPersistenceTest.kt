package dev.studios.samimine.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.repository.RoomEvaluationRepository
import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class GuidedBatteryPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "guided-${UUID.randomUUID()}.db"
    private lateinit var db: SamiMineDatabase
    private lateinit var repo: RoomEvaluationRepository
    private val definition = BatteryDefinition("default", "Demo", 1, "mine", listOf("PVT", "FLANKER"),
        mapOf("PVT" to "demo-pvt-v1", "FLANKER" to "demo-flanker-v2"))
    private fun open() {
        db = Room.databaseBuilder(context, SamiMineDatabase::class.java, name).addMigrations(SamiMineDatabase.MIGRATION_5_6).build()
        repo = RoomEvaluationRepository(db)
    }
    @Before fun setup() { open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private fun run() = GuidedBatteryRun("r", "a", Shift.DAY, 100, definition)
    private fun pvt(id: String = "p") = Evaluation(id, Worker("a", "Demo", "Area", "Demo"), Shift.DAY, 101, PvtConfiguration())

    @Test fun completedStepsSurviveRecoveryAndNewAttemptKeepsInterruptedAttempt() = runBlocking {
        repo.startGuided(run()); repo.startGuided(run())
        repo.startGuidedPvt("r", 0, pvt()); repo.startGuidedPvt("r", 0, pvt())
        repeat(3) { repo.saveAttempt("p", Attempt(it + 1, AttemptOutcome.RESPONSE, 200_000_000)) }
        repo.finish("p")
        val partial = repo.refreshGuided("r", "a")
        assertEquals(1, partial.nextStep)
        val cognitive = CognitiveTestSession("c", "a", "Demo", "Area", "Demo", Shift.DAY,
            CognitiveTestType.FLANKER, "demo-flanker-v2", "{}", 42, 103)
        repo.startGuidedCognitive("r", 1, cognitive)
        db.close(); open()
        repo.recoverInterrupted(); repo.recoverCognitiveTests(); repo.recoverGuidedBatteries()
        val restored = repo.guided("r", "a")!!
        assertEquals(BatteryRunStatus.PAUSED, restored.status)
        assertEquals(BatteryStepStatus.COMPLETED, restored.steps[0].status)
        assertEquals(BatteryStepStatus.INTERRUPTED, restored.steps[1].status)
        repo.startGuidedCognitive("r", 1, cognitive.copy(id = "c2"))
        assertEquals(listOf("c", "c2"), repo.guided("r", "a")!!.steps[1].attempts.map { it.runId })
        assertNull(repo.guided("r", "b"))
    }
    @Test fun wrongOwnerShiftStepOrDuplicateActiveSessionRollsBackAtomicStart() = runBlocking {
        repo.startGuided(run())
        assertTrue(runCatching { repo.startGuided(run().copy(id = "other")) }.isFailure)
        assertTrue(runCatching { repo.startGuidedPvt("r", 1, pvt("wrong-step")) }.isFailure)
        assertNull(repo.get("wrong-step", "a"))
        assertTrue(runCatching { repo.startGuidedPvt("r", 0, pvt("wrong-shift").copy(shift = Shift.NIGHT)) }.isFailure)
        assertNull(repo.get("wrong-shift", "a"))
        repo.startGuidedPvt("r", 0, pvt())
        repo.setGuidedState("r", "a", BatteryRunStatus.PAUSED)
        assertEquals(EvaluationStatus.INTERRUPTED, repo.get("p", "a")!!.status)
        repo.setGuidedState("r", "a", BatteryRunStatus.ABANDONED)
        assertTrue(repo.guided("r", "a")!!.terminal)
        assertEquals("PENDING", db.sync().state("BATTERY", "r", "a"))
        assertTrue(runCatching { repo.startGuidedPvt("r", 0, pvt("late")) }.isFailure)
    }
    @Test fun lastStepCompletionEnqueuesSummaryOnlyAfterEveryStepResolves() = runBlocking {
        val single = run().copy(definition = definition.copy(modules = listOf("PVT"), protocols = mapOf("PVT" to "demo-pvt-v1")), steps = listOf(BatteryStep("PVT")))
        repo.startGuided(single); repo.startGuidedPvt("r", 0, pvt())
        assertNull(db.sync().state("BATTERY", "r", "a"))
        repeat(3) { repo.saveAttempt("p", Attempt(it + 1, AttemptOutcome.OMISSION)) }
        repo.finish("p")
        assertEquals(BatteryRunStatus.COMPLETED, repo.refreshGuided("r", "a").status)
        assertEquals("PENDING", db.sync().state("BATTERY", "r", "a"))
        repo.refreshGuided("r", "a")
        assertEquals(1, db.sync().pending("a").count { it.kind == "BATTERY" })
    }
    @Test fun versionFiveMigrationPreservesLegacyRecords() = runBlocking {
        db.close()
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("dev.studios.samimine.data.local.database.SamiMineDatabase/5.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            fun sql(value: String) = value.replace("\${TABLE_NAME}", entity.getString("tableName"))
            legacy.execSQL(sql(entity.getString("createSql")))
            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
            for (j in 0 until indices.length()) legacy.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
        }
        legacy.execSQL("INSERT INTO assessment_sessions VALUES ('old','a','DAY',100,'COMPLETED',NULL,200,NULL,'v1')")
        legacy.version = 5; legacy.close(); open()
        assertEquals("COMPLETED", repo.getAssessment("old", "a")!!.status)
        assertTrue(db.guided().all("a").isEmpty())
    }
}
