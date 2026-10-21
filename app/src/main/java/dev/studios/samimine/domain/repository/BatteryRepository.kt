package dev.studios.samimine.domain.repository

import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.flow.Flow

interface BatteryRepository {
    suspend fun recoverAssessments()
    suspend fun startAssessment(session: AssessmentSession)
    suspend fun linkAssessmentPvt(id: String, workerId: String, pvtId: String)
    suspend fun interruptAssessment(id: String, workerId: String, reason: String)
    suspend fun getAssessment(id: String, workerId: String): AssessmentSession?
    fun observeAssessments(workerId: String): Flow<List<AssessmentSession>>
    suspend fun saveSelfReport(report: SelfReport)
    suspend fun getSelfReport(id: String, workerId: String): SelfReport?
    fun observeSelfReports(workerId: String): Flow<List<SelfReport>>
}
