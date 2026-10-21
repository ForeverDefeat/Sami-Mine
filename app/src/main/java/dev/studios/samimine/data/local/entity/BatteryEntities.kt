package dev.studios.samimine.data.local.entity

import androidx.room.*
import dev.studios.samimine.domain.model.*
import org.json.JSONObject

@Entity(tableName = "assessment_sessions", indices = [Index("workerId")])
data class AssessmentEntity(@PrimaryKey val id: String, val workerId: String, val shift: String, val startedAtMillis: Long,
    val status: String, val pvtId: String?, val finishedAtMillis: Long?, val reason: String?, val bundleVersion: String) {
    fun toDomain() = AssessmentSession(id, workerId, Shift.valueOf(shift), startedAtMillis, status, pvtId, finishedAtMillis, reason, bundleVersion)
}
fun AssessmentSession.toEntity() = AssessmentEntity(id, workerId, shift.name, startedAtMillis, status, pvtId, finishedAtMillis, reason, bundleVersion)

@Entity(tableName = "self_reports", indices = [Index("workerId"), Index("assessmentId")])
data class SelfReportEntity(@PrimaryKey val id: String, val workerId: String, val shift: String, val assessmentId: String?,
    val instrumentId: String, val instrumentVersion: String, val language: String, val answeredAtMillis: Long,
    val answersJson: String, val omitted: Boolean, val context: String?, val moment: String?, val total: Int?, val scoringMethod: String) {
    fun toDomain(): SelfReport {
        val json = JSONObject(answersJson)
        return SelfReport(id, workerId, Shift.valueOf(shift), assessmentId, InstrumentId.valueOf(instrumentId), instrumentVersion,
            language, answeredAtMillis, json.keys().asSequence().associateWith { json.getInt(it) }, omitted, context, moment, total, scoringMethod)
    }
}
fun SelfReport.toEntity() = SelfReportEntity(id, workerId, shift.name, assessmentId, instrumentId.name, instrumentVersion, language,
    answeredAtMillis, JSONObject(answers).toString(), omitted, context, moment, total, scoringMethod)

@Entity(tableName = "metric_results", primaryKeys = ["runId", "key"])
data class MetricEntity(val runId: String, val key: String, val value: Double?, val unit: String, val denominator: Int?, val methodVersion: String)
