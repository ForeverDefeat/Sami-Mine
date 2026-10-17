package com.example.samimine.data.local.entity

import androidx.room.*
import com.example.samimine.domain.model.*

@Entity(tableName = "evaluations", indices = [Index("workerId")])
data class EvaluationEntity(
    @PrimaryKey val id: String,
    val workerId: String, val workerName: String, val area: String, val employment: String,
    val shift: String, val startedAtMillis: Long,
    val configurationVersion: String, val attemptCount: Int,
    val minWaitMillis: Long, val maxWaitMillis: Long, val responseWindowMillis: Long,
    val status: String = EvaluationStatus.IN_PROGRESS.name,
    val syncStatus: String = SyncStatus.NOT_ENABLED.name,
    val meanReactionMillis: Double? = null,
    val responses: Int? = null, val anticipations: Int? = null, val omissions: Int? = null,
    val interruptionReason: String? = null,
)

@Entity(
    tableName = "attempts", primaryKeys = ["evaluationId", "attemptIndex"],
    foreignKeys = [ForeignKey(entity = EvaluationEntity::class, parentColumns = ["id"], childColumns = ["evaluationId"], onDelete = ForeignKey.RESTRICT)],
)
data class AttemptEntity(val evaluationId: String, val attemptIndex: Int, val outcome: String, val reactionNanos: Long?)

data class EvaluationWithAttempts(
    @Embedded val evaluation: EvaluationEntity,
    @Relation(parentColumn = "id", entityColumn = "evaluationId") val attempts: List<AttemptEntity>,
) {
    fun toDomain(): Evaluation = with(evaluation) {
        Evaluation(
            id, Worker(workerId, workerName, area, employment), Shift.valueOf(shift), startedAtMillis,
            PvtConfiguration(configurationVersion, attemptCount, minWaitMillis, maxWaitMillis, responseWindowMillis),
            EvaluationStatus.valueOf(status), SyncStatus.valueOf(syncStatus),
            attempts.sortedBy { it.attemptIndex }.map { Attempt(it.attemptIndex, AttemptOutcome.valueOf(it.outcome), it.reactionNanos) },
            responses?.let { Metrics(meanReactionMillis, it, anticipations!!, omissions!!) }, interruptionReason,
        )
    }
}

fun Evaluation.toEntity() = EvaluationEntity(
    id, worker.id, worker.name, worker.area, worker.employment, shift.name, startedAtMillis,
    configuration.version, configuration.attempts, configuration.minWaitMillis,
    configuration.maxWaitMillis, configuration.responseWindowMillis,
)
