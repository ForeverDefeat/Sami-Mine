package com.example.samimine.domain.repository

import com.example.samimine.domain.model.*
import kotlinx.coroutines.flow.Flow

interface CognitiveTestRepository {
    suspend fun recoverCognitiveTests()
    suspend fun startCognitiveTest(session: CognitiveTestSession)
    suspend fun saveCognitiveTrial(sessionId: String, trial: CognitiveTrialRecord)
    suspend fun finishCognitiveTest(sessionId: String)
    suspend fun interruptCognitiveTest(sessionId: String, reason: String)
    suspend fun getCognitiveTest(sessionId: String, workerId: String): CognitiveTestSession?
    fun observeCognitiveHistory(workerId: String): Flow<List<CognitiveTestSession>>
}
