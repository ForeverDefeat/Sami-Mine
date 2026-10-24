package dev.studios.samimine.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.remote.FirebaseSyncRepository
import dev.studios.samimine.data.repository.RoomEvaluationRepository
import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PilotPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun versionFourMigrationPreservesResultsAndQueuesOnlyClosedRecords() = runBlocking {
        val name = "migration5-${UUID.randomUUID()}.db"
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("dev.studios.samimine.data.local.database.SamiMineDatabase/4.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val entities = json.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
            for (i in 0 until indices.length()) legacy.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        legacy.execSQL("INSERT INTO assessment_sessions VALUES ('closed','a','DAY',100,'INCOMPLETE',NULL,200,'Demo','v1')")
        legacy.execSQL("INSERT INTO assessment_sessions VALUES ('open','a','DAY',100,'IN_PROGRESS',NULL,NULL,NULL,'v1')")
        legacy.version = 4; legacy.close()
        val db = Room.databaseBuilder(context, SamiMineDatabase::class.java, name).addMigrations(SamiMineDatabase.MIGRATION_4_5, SamiMineDatabase.MIGRATION_5_6).build()
        try {
            assertNotNull(db.battery().assessment("closed", "a"))
            assertEquals(listOf("closed"), db.sync().pending("a").map { it.runId })
            val repository = RoomEvaluationRepository(db)
            repository.recoverAssessments()
            assertEquals(setOf("closed", "open"), db.sync().pending("a").map { it.runId }.toSet())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun terminalWriteAndQueueAreDurableIdempotentAndProfileScoped() = runBlocking {
        val name = "queue-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, SamiMineDatabase::class.java, name).build()
        var db = open()
        try {
            val repository = RoomEvaluationRepository(db)
            val evaluation = Evaluation("result", Worker("a", "Demo", "Area", "Demo"), Shift.DAY, 100, PvtConfiguration())
            repository.start(evaluation)
            assertTrue(db.sync().pending("a").isEmpty())
            repeat(3) { repository.saveAttempt(evaluation.id, Attempt(it + 1, AttemptOutcome.OMISSION)) }
            repository.finish(evaluation.id); repository.finish(evaluation.id)
            assertEquals(1, db.sync().pending("a").size)
            assertTrue(db.sync().pending("b").isEmpty())
            db.sync().update("PVT", evaluation.id, "SYNCED")
            db.close(); db = open()
            val reopened = RoomEvaluationRepository(db)
            reopened.recoverInterrupted()
            assertEquals("SYNCED", db.sync().observe("a").first().single().state)
            assertEquals(EvaluationStatus.COMPLETED, reopened.get(evaluation.id, "a")!!.status)
            assertEquals(1, reopened.observeHistory("a").first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun failedReportTransactionCannotLeakIntoOutbox() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SamiMineDatabase::class.java).build()
        try {
            val repository = RoomEvaluationRepository(db)
            val report = SelfReport("report", "a", Shift.DAY, "missing-parent", InstrumentId.KSS, "fixture", "es-PE", 100,
                emptyMap(), true, null, null)
            assertTrue(runCatching { repository.saveSelfReport(report) }.isFailure)
            assertTrue(db.sync().pending("a").isEmpty())
            assertNull(repository.getSelfReport("report", "a"))
        } finally { db.close() }
    }
}
