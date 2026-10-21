package dev.studios.samimine.domain.repository

import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Persistencia PVT: repetir el mismo ID/intento con datos iguales debe ser idempotente.
 * finish requiere todos los intentos y confirma estado y métricas de forma atómica.
 * get e historial se limitan al perfil indicado; esto no constituye autenticación.
 */
interface EvaluationRepository {
    suspend fun recoverInterrupted()
    suspend fun start(evaluation: Evaluation)
    suspend fun saveAttempt(evaluationId: String, attempt: Attempt)
    suspend fun finish(evaluationId: String)
    suspend fun interrupt(evaluationId: String, reason: String)
    suspend fun invalidate(evaluationId: String, reason: String) = interrupt(evaluationId, reason)
    suspend fun get(evaluationId: String, workerId: String): Evaluation?
    fun observeHistory(workerId: String): Flow<List<Evaluation>>
}
