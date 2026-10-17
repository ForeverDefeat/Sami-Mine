package com.example.samimine.data.local.dao

import androidx.room.*
import com.example.samimine.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CognitiveTestDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: CognitiveSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrial(trial: CognitiveTrialEntity): Long

    @Transaction @Query("SELECT * FROM cognitive_sessions WHERE id = :id")
    suspend fun get(id: String): CognitiveSessionWithTrials?

    @Transaction @Query("SELECT * FROM cognitive_sessions WHERE workerId = :workerId ORDER BY startedAtMillis DESC, id DESC")
    fun history(workerId: String): Flow<List<CognitiveSessionWithTrials>>

    @Query("UPDATE cognitive_sessions SET status = 'INCOMPLETE', finishedAtMillis = :now, interruptionReason = :reason WHERE status = 'IN_PROGRESS'")
    suspend fun recover(now: Long, reason: String)

    @Query("UPDATE cognitive_sessions SET status = 'INCOMPLETE', finishedAtMillis = :now, interruptionReason = :reason WHERE id = :id AND status = 'IN_PROGRESS'")
    suspend fun interrupt(id: String, now: Long, reason: String)

    @Query("UPDATE cognitive_sessions SET status = 'COMPLETED', finishedAtMillis = :now WHERE id = :id AND status = 'IN_PROGRESS'")
    suspend fun complete(id: String, now: Long)
}
