package com.example.samimine.domain.repository

import com.example.samimine.domain.model.*
import kotlinx.coroutines.flow.Flow

interface EvaluationRepository {
    suspend fun recoverInterrupted()
    suspend fun start(evaluation: Evaluation)
    suspend fun saveAttempt(evaluationId: String, attempt: Attempt)
    suspend fun finish(evaluationId: String)
    suspend fun interrupt(evaluationId: String, reason: String)
    suspend fun get(evaluationId: String, workerId: String): Evaluation?
    fun observeHistory(workerId: String): Flow<List<Evaluation>>
}
