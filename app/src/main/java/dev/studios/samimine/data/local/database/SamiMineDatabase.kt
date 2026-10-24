package dev.studios.samimine.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.studios.samimine.data.local.dao.EvaluationDao
import dev.studios.samimine.data.local.dao.CognitiveTestDao
import dev.studios.samimine.data.local.dao.BatteryDao
import dev.studios.samimine.data.local.entity.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.studios.samimine.data.local.dao.SyncDao
import dev.studios.samimine.data.local.dao.GuidedBatteryDao

/**
 * v1 PVT, v2 cognitivas, v3 intervalos, v4 batería, autorreportes y métricas versionadas.
 * Las migraciones conservan el historial; el contenedor no habilita recreación destructiva.
 * Los JSON exportados en app/schemas permiten revisar cada versión del contrato SQLite.
 */
@Database(entities = [EvaluationEntity::class, AttemptEntity::class, CognitiveSessionEntity::class, CognitiveTrialEntity::class,
    AssessmentEntity::class, SelfReportEntity::class, MetricEntity::class, SyncEntry::class,
    GuidedBatteryEntity::class, BatteryConfigCache::class], version = 6, exportSchema = true)
abstract class SamiMineDatabase : RoomDatabase() {
    abstract fun sync(): SyncDao
    abstract fun evaluations(): EvaluationDao
    abstract fun cognitiveTests(): CognitiveTestDao
    abstract fun battery(): BatteryDao
    abstract fun guided(): GuidedBatteryDao

    companion object {
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS battery_runs (id TEXT NOT NULL, workerId TEXT NOT NULL, status TEXT NOT NULL, startedAtMillis INTEGER NOT NULL, snapshot TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_runs_workerId ON battery_runs(workerId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS battery_config_cache (organizationId TEXT NOT NULL, snapshot TEXT NOT NULL, PRIMARY KEY(organizationId))")
            }
        }
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_queue (kind TEXT NOT NULL, runId TEXT NOT NULL, workerId TEXT NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, error TEXT, PRIMARY KEY(kind, runId))")
                db.execSQL("INSERT OR IGNORE INTO sync_queue SELECT 'PVT', id, workerId, 'PENDING', 0, NULL FROM evaluations WHERE status != 'IN_PROGRESS'")
                db.execSQL("INSERT OR IGNORE INTO sync_queue SELECT 'COGNITIVE', id, workerId, 'PENDING', 0, NULL FROM cognitive_sessions WHERE status != 'IN_PROGRESS'")
                db.execSQL("INSERT OR IGNORE INTO sync_queue SELECT 'ASSESSMENT', id, workerId, 'PENDING', 0, NULL FROM assessment_sessions WHERE status != 'IN_PROGRESS'")
                db.execSQL("INSERT OR IGNORE INTO sync_queue SELECT 'REPORT', id, workerId, 'PENDING', 0, NULL FROM self_reports")
            }
        }
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cognitive_sessions ADD COLUMN calculationVersion TEXT NOT NULL DEFAULT 'legacy-v1'")
                db.execSQL("ALTER TABLE cognitive_sessions ADD COLUMN executionStartedUptimeMillis INTEGER")
                db.execSQL("ALTER TABLE cognitive_sessions ADD COLUMN executionFinishedUptimeMillis INTEGER")
                db.execSQL("ALTER TABLE evaluations ADD COLUMN calculationVersion TEXT NOT NULL DEFAULT 'legacy-v1'")
                db.execSQL("ALTER TABLE evaluations ADD COLUMN medianReactionMillis REAL")
                db.execSQL("ALTER TABLE evaluations ADD COLUMN meanResponseSpeed REAL")
                db.execSQL("CREATE TABLE IF NOT EXISTS assessment_sessions (id TEXT NOT NULL, workerId TEXT NOT NULL, shift TEXT NOT NULL, startedAtMillis INTEGER NOT NULL, status TEXT NOT NULL, pvtId TEXT, finishedAtMillis INTEGER, reason TEXT, bundleVersion TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assessment_sessions_workerId ON assessment_sessions(workerId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS self_reports (id TEXT NOT NULL, workerId TEXT NOT NULL, shift TEXT NOT NULL, assessmentId TEXT, instrumentId TEXT NOT NULL, instrumentVersion TEXT NOT NULL, language TEXT NOT NULL, answeredAtMillis INTEGER NOT NULL, answersJson TEXT NOT NULL, omitted INTEGER NOT NULL, context TEXT, moment TEXT, total INTEGER, scoringMethod TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_self_reports_workerId ON self_reports(workerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_self_reports_assessmentId ON self_reports(assessmentId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS metric_results (runId TEXT NOT NULL, `key` TEXT NOT NULL, value REAL, unit TEXT NOT NULL, denominator INTEGER, methodVersion TEXT NOT NULL, PRIMARY KEY(runId, `key`))")
            }
        }
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS cognitive_sessions (id TEXT NOT NULL, workerId TEXT NOT NULL, workerName TEXT NOT NULL, area TEXT NOT NULL, employment TEXT NOT NULL, shift TEXT NOT NULL, testType TEXT NOT NULL, protocolVersion TEXT NOT NULL, configSnapshot TEXT NOT NULL, randomSeed INTEGER NOT NULL, startedAtMillis INTEGER NOT NULL, finishedAtMillis INTEGER, status TEXT NOT NULL, interruptionReason TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cognitive_sessions_workerId ON cognitive_sessions(workerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cognitive_sessions_startedAtMillis ON cognitive_sessions(startedAtMillis)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cognitive_trials (sessionId TEXT NOT NULL, trialIndex INTEGER NOT NULL, isPractice INTEGER NOT NULL, stimulusCode TEXT NOT NULL, expectedResponse TEXT, actualResponse TEXT, outcomeCode TEXT NOT NULL, reactionTimeMillis INTEGER, onsetUptimeMillis INTEGER, responseUptimeMillis INTEGER, PRIMARY KEY(sessionId, trialIndex), FOREIGN KEY(sessionId) REFERENCES cognitive_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cognitive_trials_sessionId ON cognitive_trials(sessionId)")
            }
        }
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cognitive_trials ADD COLUMN interTrialDelayMillis INTEGER")
            }
        }
    }
}
