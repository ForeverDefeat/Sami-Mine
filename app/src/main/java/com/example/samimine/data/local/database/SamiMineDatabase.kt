package com.example.samimine.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.samimine.data.local.dao.EvaluationDao
import com.example.samimine.data.local.dao.CognitiveTestDao
import com.example.samimine.data.local.entity.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EvaluationEntity::class, AttemptEntity::class, CognitiveSessionEntity::class, CognitiveTrialEntity::class], version = 3, exportSchema = true)
abstract class SamiMineDatabase : RoomDatabase() {
    abstract fun evaluations(): EvaluationDao
    abstract fun cognitiveTests(): CognitiveTestDao

    companion object {
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
