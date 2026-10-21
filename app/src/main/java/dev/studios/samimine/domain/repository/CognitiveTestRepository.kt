package dev.studios.samimine.domain.repository

import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Persiste sesiones y ensayos consecutivos, incluidas prácticas, con reintento idempotente.
 * El cierre exige todos los registros; la recuperación marca sesiones en curso incompletas.
 * Los cierres descriptive-v2 conservan un snapshot de métricas, unidad y denominador.
 * DSST exige un minuto completo; las demás tareas exigen todos los ensayos configurados.
 */
interface CognitiveTestRepository {
    suspend fun recoverCognitiveTests()
    suspend fun startCognitiveTest(session: CognitiveTestSession)
    suspend fun saveCognitiveTrial(sessionId: String, trial: CognitiveTrialRecord)
    suspend fun finishCognitiveTest(sessionId: String)
    suspend fun recordExecutionTimes(sessionId: String, started: Long, finished: Long? = null) { }
    suspend fun invalidateCognitiveTest(sessionId: String, reason: String) = interruptCognitiveTest(sessionId, reason)
    suspend fun interruptCognitiveTest(sessionId: String, reason: String)
    suspend fun getCognitiveTest(sessionId: String, workerId: String): CognitiveTestSession?
    fun observeCognitiveHistory(workerId: String): Flow<List<CognitiveTestSession>>
}
