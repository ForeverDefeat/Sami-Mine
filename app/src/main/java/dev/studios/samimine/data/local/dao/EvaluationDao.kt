package dev.studios.samimine.data.local.dao

import androidx.room.*
import dev.studios.samimine.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EvaluationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(evaluation: EvaluationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttempt(attempt: AttemptEntity): Long

    @Transaction
    @Query("SELECT * FROM evaluations WHERE id = :id")
    suspend fun get(id: String): EvaluationWithAttempts?

    @Transaction
    @Query("SELECT * FROM evaluations WHERE workerId = :workerId ORDER BY startedAtMillis DESC, id DESC")
    fun history(workerId: String): Flow<List<EvaluationWithAttempts>>

    @Query("UPDATE evaluations SET status = 'INTERRUPTED', interruptionReason = :reason WHERE status = 'IN_PROGRESS'")
    suspend fun recover(reason: String)

    @Query("UPDATE evaluations SET status = 'INTERRUPTED', interruptionReason = :reason WHERE id = :id AND status = 'IN_PROGRESS'")
    suspend fun interrupt(id: String, reason: String)

    @Query("UPDATE evaluations SET status = 'INVALID', interruptionReason = :reason WHERE id = :id AND status = 'IN_PROGRESS'")
    suspend fun invalidate(id: String, reason: String)

    @Query("UPDATE evaluations SET status = 'COMPLETED', meanReactionMillis = :mean, responses = :responses, anticipations = :anticipations, omissions = :omissions, medianReactionMillis = :median, meanResponseSpeed = :speed WHERE id = :id AND status = 'IN_PROGRESS'")
    suspend fun complete(id: String, mean: Double?, responses: Int, anticipations: Int, omissions: Int, median: Double? = null, speed: Double? = null)
}
