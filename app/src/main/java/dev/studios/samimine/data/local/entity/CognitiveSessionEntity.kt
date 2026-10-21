package dev.studios.samimine.data.local.entity

import androidx.room.*
import dev.studios.samimine.domain.model.*

@Entity(tableName = "cognitive_sessions", indices = [Index("workerId"), Index("startedAtMillis")])
data class CognitiveSessionEntity(
    @PrimaryKey val id: String,
    val workerId: String,
    val workerName: String,
    val area: String,
    val employment: String,
    val shift: String,
    val testType: String,
    val protocolVersion: String,
    val configSnapshot: String,
    val randomSeed: Long,
    val startedAtMillis: Long,
    val finishedAtMillis: Long?,
    val status: String,
    val interruptionReason: String?,
    @ColumnInfo(defaultValue = "'legacy-v1'") val calculationVersion: String = "legacy-v1",
    val executionStartedUptimeMillis: Long? = null,
    val executionFinishedUptimeMillis: Long? = null,
)

@Entity(
    tableName = "cognitive_trials",
    primaryKeys = ["sessionId", "trialIndex"],
    foreignKeys = [ForeignKey(entity = CognitiveSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class CognitiveTrialEntity(
    val sessionId: String,
    val trialIndex: Int,
    val isPractice: Boolean,
    val stimulusCode: String,
    val expectedResponse: String?,
    val actualResponse: String?,
    val outcomeCode: String,
    val reactionTimeMillis: Long?,
    val onsetUptimeMillis: Long?,
    val responseUptimeMillis: Long?,
    val interTrialDelayMillis: Long? = null,
)

data class CognitiveSessionWithTrials(
    @Embedded val session: CognitiveSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val trials: List<CognitiveTrialEntity>,
    @Relation(parentColumn = "id", entityColumn = "runId") val metricResults: List<MetricEntity> = emptyList(),
) {
    fun toDomain() = CognitiveTestSession(
        session.id, session.workerId, session.workerName, session.area, session.employment,
        Shift.valueOf(session.shift), CognitiveTestType.valueOf(session.testType), session.protocolVersion,
        session.configSnapshot, session.randomSeed, session.startedAtMillis, session.finishedAtMillis,
        CognitiveSessionStatus.valueOf(session.status), session.interruptionReason,
        trials.sortedBy { it.trialIndex }.map {
            CognitiveTrialRecord(it.trialIndex, it.isPractice, it.stimulusCode, it.expectedResponse,
                it.actualResponse, it.outcomeCode, it.reactionTimeMillis, it.onsetUptimeMillis, it.responseUptimeMillis, it.interTrialDelayMillis)
        },
        session.calculationVersion, session.executionStartedUptimeMillis, session.executionFinishedUptimeMillis,
        metricResults.takeIf { it.isNotEmpty() }?.map { MetricResult(it.key, it.value, it.unit, it.denominator, it.methodVersion) },
    )
}

fun CognitiveTestSession.toEntity() = CognitiveSessionEntity(
    id, workerId, workerName, area, employment, shift.name, type.name, protocolVersion, configSnapshot,
    randomSeed, startedAtMillis, finishedAtMillis, status.name, interruptionReason,
    calculationVersion, executionStartedUptimeMillis, executionFinishedUptimeMillis,
)

fun CognitiveTrialRecord.toEntity(sessionId: String) = CognitiveTrialEntity(
    sessionId, index, isPractice, stimulusCode, expectedResponse, actualResponse, outcomeCode,
    reactionTimeMillis, onsetUptimeMillis, responseUptimeMillis, interTrialDelayMillis,
)
