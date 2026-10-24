package dev.studios.samimine.domain.repository

import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.flow.Flow

interface GuidedBatteryRepository {
    suspend fun recoverGuidedBatteries()
    suspend fun startGuided(run: GuidedBatteryRun)
    suspend fun guided(id: String, uid: String): GuidedBatteryRun?
    fun guidedHistory(uid: String): Flow<List<GuidedBatteryRun>>
    suspend fun setGuidedState(id: String, uid: String, status: BatteryRunStatus)
    suspend fun startGuidedPvt(id: String, index: Int, evaluation: Evaluation)
    suspend fun startGuidedCognitive(id: String, index: Int, session: CognitiveTestSession)
    suspend fun saveGuidedReport(id: String, index: Int, report: SelfReport)
    suspend fun refreshGuided(id: String, uid: String): GuidedBatteryRun
}
