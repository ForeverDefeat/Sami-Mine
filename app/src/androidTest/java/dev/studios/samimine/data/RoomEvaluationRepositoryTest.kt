package dev.studios.samimine.data

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.repository.RoomEvaluationRepository
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.repository.CognitiveTestRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomEvaluationRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SamiMineDatabase
    private lateinit var repository: RoomEvaluationRepository
    private lateinit var filename: String
    @Before fun setup() {
        filename = "test-${UUID.randomUUID()}.db"
        open()
    }
    private fun open() {
        database = Room.databaseBuilder(context, SamiMineDatabase::class.java, filename).build()
        repository = RoomEvaluationRepository(database)
    }
    @After fun cleanup() { database.close(); context.deleteDatabase(filename) }
    private fun evaluation(workerId: String = "a") = Evaluation(UUID.randomUUID().toString(), Worker(workerId, "Demo", "Area", "Demo"), Shift.DAY, 100, PvtConfiguration())

    @Test fun retryIsIdempotentAndCompletionSurvivesReopen() = runBlocking {
        val evaluation = evaluation()
        repository.start(evaluation)
        repository.start(evaluation)
        val attempts = listOf(Attempt(1, AttemptOutcome.RESPONSE, 200_000_000), Attempt(2, AttemptOutcome.ANTICIPATION), Attempt(3, AttemptOutcome.OMISSION))
        attempts.forEach { repository.saveAttempt(evaluation.id, it); repository.saveAttempt(evaluation.id, it) }
        repository.finish(evaluation.id)
        repository.finish(evaluation.id)
        database.close(); open()
        repository.recoverInterrupted()
        val saved = repository.get(evaluation.id, "a")!!
        assertEquals(EvaluationStatus.COMPLETED, saved.status)
        assertEquals(Metrics(200.0, 1, 1, 1, 200.0, 5.0), saved.metrics)
        assertEquals(3, saved.attempts.size)
        assertEquals(evaluation.configuration, saved.configuration)
        assertEquals(1, repository.observeHistory("a").first().size)
        assertNull(repository.get(evaluation.id, "b"))
        assertTrue(repository.observeHistory("b").first().isEmpty())
    }

    @Test fun incompleteFinishIsRejectedAndRecoveryPreservesPartialData() = runBlocking {
        val evaluation = evaluation()
        repository.start(evaluation)
        repository.saveAttempt(evaluation.id, Attempt(1, AttemptOutcome.OMISSION))
        assertTrue(runCatching { repository.finish(evaluation.id) }.isFailure)
        assertEquals(EvaluationStatus.IN_PROGRESS, repository.get(evaluation.id, "a")!!.status)
        database.close(); open()
        repository.recoverInterrupted()
        val saved = repository.get(evaluation.id, "a")!!
        assertEquals(EvaluationStatus.INTERRUPTED, saved.status)
        assertEquals(1, saved.attempts.size)
        assertNull(saved.metrics)
        assertTrue(runCatching { repository.finish(evaluation.id) }.isFailure)
    }

    @Test fun conflictingRetryCannotOverwriteExistingAttempt() = runBlocking {
        val evaluation = evaluation()
        repository.start(evaluation)
        repository.saveAttempt(evaluation.id, Attempt(1, AttemptOutcome.OMISSION))
        assertTrue(runCatching { repository.saveAttempt(evaluation.id, Attempt(1, AttemptOutcome.ANTICIPATION)) }.isFailure)
        assertEquals(AttemptOutcome.OMISSION, repository.get(evaluation.id, "a")!!.attempts.single().outcome)
    }

    @Test fun flankerSessionPersistsPracticesAndTrialsAndCompletionIsIdempotent() = runBlocking {
        val store = repository as CognitiveTestRepository
        val session = CognitiveTestSession(
            "cognitive-test", "a", "Demo", "Area", "Demo", Shift.NIGHT,
            CognitiveTestType.FLANKER, "demo-flanker-v1", "{\"seed\":42}", 42L, 123L,
        )
        store.startCognitiveTest(session)
        store.startCognitiveTest(session)
        val generated = CognitiveTrials.generate(CognitiveTestConfig.forType(CognitiveTestType.FLANKER), 42L)
        generated.forEach { trial ->
            val result = if (trial.isPractice) "PRACTICE_CORRECT" else "HIT"
            val record = CognitiveTrialRecord(trial.index, trial.isPractice, trial.stimulusCode, trial.expectedResponse,
                trial.expectedResponse, result, if (trial.isPractice) null else 300L, null, null)
            store.saveCognitiveTrial(session.id, record)
            store.saveCognitiveTrial(session.id, record)
        }
        store.finishCognitiveTest(session.id)
        store.finishCognitiveTest(session.id)
        val saved = store.getCognitiveTest(session.id, "a")!!
        assertEquals(CognitiveSessionStatus.COMPLETED, saved.status)
        assertEquals(14, saved.trials.size)
        assertEquals(12, saved.trials.count { !it.isPractice })
        assertEquals("demo-flanker-v1", saved.protocolVersion)
        assertNotNull(saved.metrics)
        assertNull(store.getCognitiveTest(session.id, "other"))
    }

    @Test fun interruptedCognitiveSessionRecoversAsIncompleteWithoutMetrics() = runBlocking {
        val store = repository as CognitiveTestRepository
        val session = CognitiveTestSession(
            "cognitive-partial", "a", "Demo", "Area", "Demo", Shift.DAY,
            CognitiveTestType.GO_NO_GO, "demo-gonogo-v1", "{}", 9L, 456L,
        )
        store.startCognitiveTest(session)
        store.saveCognitiveTrial(session.id, CognitiveTrialRecord(1, true, "GO", "RESPOND", "RESPOND", "PRACTICE_CORRECT", null, null, null))
        store.recoverCognitiveTests()
        val recovered = store.getCognitiveTest(session.id, "a")!!
        assertEquals(CognitiveSessionStatus.INCOMPLETE, recovered.status)
        assertEquals(1, recovered.trials.size)
        assertNull(recovered.metrics)
        assertTrue(recovered.interruptionReason!!.contains("cerró"))
    }

    @Test fun protocolVersionsKeepTheirWindowsAndHistoryAfterReopen() = runBlocking {
        for (type in listOf(CognitiveTestType.FLANKER, CognitiveTestType.GO_NO_GO)) {
            for (version in listOf(type.version.replace("v2", "v1"), type.version)) {
                val config = CognitiveTestConfig.forVersion(type, version)
                val id = "window-$version"
                val snapshot = "{\"responseWindowMs\":${config.responseWindowMillis}}"
                val session = CognitiveTestSession(id, "a", "Demo", "Area", "Demo", Shift.DAY,
                    type, version, snapshot, 42, 100)
                repository.startCognitiveTest(session)
                val trials = CognitiveTrials.generate(config, 42)
                for (trial in trials) {
                    val outcome = when {
                        trial.isPractice -> "PRACTICE_CORRECT"
                        trial.stimulusCode == "NO_GO" -> "NO_GO_CORRECT_REJECTION"
                        type == CognitiveTestType.GO_NO_GO -> "GO_HIT"
                        else -> "HIT"
                    }
                    val rt = if (trial.isPractice || trial.stimulusCode == "NO_GO") null else config.responseWindowMillis - 1
                    val record = CognitiveTrialRecord(trial.index, trial.isPractice, trial.stimulusCode,
                        trial.expectedResponse, trial.expectedResponse, outcome, rt, null, null, trial.interTrialDelayMillis)
                    if (rt != null) assertTrue(runCatching {
                        repository.saveCognitiveTrial(id, record.copy(reactionTimeMillis = config.responseWindowMillis))
                    }.isFailure)
                    repository.saveCognitiveTrial(id, record)
                    repository.saveCognitiveTrial(id, record)
                }
                repository.finishCognitiveTest(id)
                database.close(); open()
                val saved = repository.getCognitiveTest(id, "a")!!
                assertEquals(version, saved.protocolVersion)
                assertEquals(snapshot, saved.configSnapshot)
                assertEquals(CognitiveSessionStatus.COMPLETED, saved.status)
                assertEquals(trials.size, saved.trials.size)
                assertNotNull(saved.metrics)
            }
        }
        assertEquals(4, repository.observeCognitiveHistory("a").first().size)
        assertTrue(repository.observeCognitiveHistory("other").first().isEmpty())
    }

    @Test fun schemaV1MigrationPreservesExistingPvtHistory() = runBlocking {
        val legacyName = "legacy-${UUID.randomUUID()}.db"
        val legacyFile = context.getDatabasePath(legacyName)
        val legacy = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)
        legacy.execSQL("CREATE TABLE evaluations (id TEXT NOT NULL, workerId TEXT NOT NULL, workerName TEXT NOT NULL, area TEXT NOT NULL, employment TEXT NOT NULL, shift TEXT NOT NULL, startedAtMillis INTEGER NOT NULL, configurationVersion TEXT NOT NULL, attemptCount INTEGER NOT NULL, minWaitMillis INTEGER NOT NULL, maxWaitMillis INTEGER NOT NULL, responseWindowMillis INTEGER NOT NULL, status TEXT NOT NULL, syncStatus TEXT NOT NULL, meanReactionMillis REAL, responses INTEGER, anticipations INTEGER, omissions INTEGER, interruptionReason TEXT, PRIMARY KEY(id))")
        legacy.execSQL("CREATE INDEX index_evaluations_workerId ON evaluations(workerId)")
        legacy.execSQL("CREATE TABLE attempts (evaluationId TEXT NOT NULL, attemptIndex INTEGER NOT NULL, outcome TEXT NOT NULL, reactionNanos INTEGER, PRIMARY KEY(evaluationId, attemptIndex), FOREIGN KEY(evaluationId) REFERENCES evaluations(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        legacy.execSQL("INSERT INTO evaluations VALUES ('legacy-pvt','a','Demo','Area','Demo','DAY',123,'demo-pvt-v1',3,2000,5000,3000,'COMPLETED','NOT_ENABLED',250.0,1,1,1,NULL)")
        legacy.execSQL("INSERT INTO attempts VALUES ('legacy-pvt',1,'RESPONSE',250000000)")
        legacy.version = 1
        legacy.close()

        val migrated = Room.databaseBuilder(context, SamiMineDatabase::class.java, legacyName)
            .addMigrations(SamiMineDatabase.MIGRATION_1_2, SamiMineDatabase.MIGRATION_2_3, SamiMineDatabase.MIGRATION_3_4, SamiMineDatabase.MIGRATION_4_5, SamiMineDatabase.MIGRATION_5_6).build()
        try {
            val saved = RoomEvaluationRepository(migrated).get("legacy-pvt", "a")!!
            assertEquals(EvaluationStatus.COMPLETED, saved.status)
            assertEquals(1, saved.attempts.size)
            assertEquals("demo-pvt-v1", saved.configuration.version)
        } finally {
            migrated.close()
            context.deleteDatabase(legacyName)
        }
    }
}
