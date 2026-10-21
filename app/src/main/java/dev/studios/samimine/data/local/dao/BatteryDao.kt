package dev.studios.samimine.data.local.dao

import androidx.room.*
import dev.studios.samimine.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAssessment(row: AssessmentEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReport(row: SelfReportEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMetrics(rows: List<MetricEntity>)
    @Query("SELECT * FROM assessment_sessions WHERE id = :id AND workerId = :workerId") suspend fun assessment(id: String, workerId: String): AssessmentEntity?
    @Query("SELECT * FROM self_reports WHERE id = :id AND workerId = :workerId") suspend fun report(id: String, workerId: String): SelfReportEntity?
    @Query("SELECT * FROM metric_results WHERE runId = :id") suspend fun metrics(id: String): List<MetricEntity>
    @Query("SELECT * FROM assessment_sessions WHERE workerId = :workerId ORDER BY startedAtMillis DESC") fun assessments(workerId: String): Flow<List<AssessmentEntity>>
    @Query("SELECT * FROM self_reports WHERE workerId = :workerId ORDER BY answeredAtMillis DESC") fun reports(workerId: String): Flow<List<SelfReportEntity>>
    @Query("UPDATE assessment_sessions SET pvtId = :pvtId WHERE id = :id AND workerId = :workerId AND status = 'IN_PROGRESS' AND pvtId IS NULL") suspend fun linkPvt(id: String, workerId: String, pvtId: String)
    @Query("UPDATE assessment_sessions SET status = :status, finishedAtMillis = :now, reason = :reason WHERE pvtId = :pvtId AND status = 'IN_PROGRESS'") suspend fun finishByPvt(pvtId: String, status: String, now: Long, reason: String?)
    @Query("UPDATE assessment_sessions SET status = 'INCOMPLETE', finishedAtMillis = :now, reason = :reason WHERE id = :id AND workerId = :workerId AND status = 'IN_PROGRESS'") suspend fun interrupt(id: String, workerId: String, now: Long, reason: String)
    @Query("UPDATE assessment_sessions SET status = 'INCOMPLETE', finishedAtMillis = :now, reason = 'La aplicación se cerró durante el recorrido' WHERE status = 'IN_PROGRESS'") suspend fun recover(now: Long)
}
