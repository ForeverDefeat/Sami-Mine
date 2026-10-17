package com.example.samimine.data.repository

import androidx.room.withTransaction
import com.example.samimine.data.local.database.SamiMineDatabase
import com.example.samimine.data.local.entity.*
import com.example.samimine.domain.evaluation.pvt.PvtScoring
import com.example.samimine.domain.model.*
import com.example.samimine.domain.repository.EvaluationRepository
import com.example.samimine.domain.repository.CognitiveTestRepository
import kotlinx.coroutines.flow.map

class RoomEvaluationRepository(private val database: SamiMineDatabase) : EvaluationRepository, CognitiveTestRepository {
    private val dao = database.evaluations()
    override suspend fun recoverInterrupted() = dao.recover("La aplicación se cerró durante la prueba")

    override suspend fun start(evaluation: Evaluation) {
        database.withTransaction {
            dao.insert(evaluation.toEntity())
            check(dao.get(evaluation.id)?.evaluation == evaluation.toEntity()) { "Identificador de evaluación en conflicto" }
        }
    }

    override suspend fun saveAttempt(evaluationId: String, attempt: Attempt) {
        database.withTransaction {
            val existing = checkNotNull(dao.get(evaluationId)).toDomain()
            val previous = existing.attempts.find { it.index == attempt.index }
            if (previous != null) {
                check(previous == attempt) { "El intento ya existe con otros datos" }
                return@withTransaction
            }
            check(existing.status == EvaluationStatus.IN_PROGRESS)
            require(attempt.index == existing.attempts.size + 1 && attempt.index <= existing.configuration.attempts)
            if (attempt.reactionNanos != null) require(attempt.reactionNanos < existing.configuration.responseWindowMillis * 1_000_000)
            dao.insertAttempt(AttemptEntity(evaluationId, attempt.index, attempt.outcome.name, attempt.reactionNanos))
        }
    }

    override suspend fun finish(evaluationId: String) {
        database.withTransaction {
            val evaluation = checkNotNull(dao.get(evaluationId)).toDomain()
            if (evaluation.status == EvaluationStatus.COMPLETED) return@withTransaction
            check(evaluation.status == EvaluationStatus.IN_PROGRESS)
            check(evaluation.attempts.map { it.index } == (1..evaluation.configuration.attempts).toList())
            val metrics = PvtScoring.calculate(evaluation.attempts)
            dao.complete(evaluationId, metrics.meanReactionMillis, metrics.responses, metrics.anticipations, metrics.omissions)
        }
    }

    override suspend fun interrupt(evaluationId: String, reason: String) = dao.interrupt(evaluationId, reason)
    override suspend fun get(evaluationId: String, workerId: String): Evaluation? =
        dao.get(evaluationId)?.toDomain()?.takeIf { it.worker.id == workerId }
    override fun observeHistory(workerId: String) = dao.history(workerId).map { rows -> rows.map { it.toDomain() } }

    private val cognitiveDao = database.cognitiveTests()
    override suspend fun recoverCognitiveTests() = cognitiveDao.recover(System.currentTimeMillis(), "La aplicación se cerró durante la prueba")

    override suspend fun startCognitiveTest(session: CognitiveTestSession) {
        database.withTransaction {
            cognitiveDao.insert(session.toEntity())
            check(cognitiveDao.get(session.id)?.session == session.toEntity()) { "Identificador de sesión en conflicto" }
        }
    }

    override suspend fun saveCognitiveTrial(sessionId: String, trial: CognitiveTrialRecord) {
        database.withTransaction {
            val session = checkNotNull(cognitiveDao.get(sessionId)).toDomain()
            val previous = session.trials.find { it.index == trial.index }
            if (previous != null) {
                check(previous == trial) { "El ensayo ya existe con otros datos" }
                return@withTransaction
            }
            check(session.status == CognitiveSessionStatus.IN_PROGRESS)
            require(trial.index == session.trials.size + 1)
            if (trial.reactionTimeMillis != null) require(trial.reactionTimeMillis in 0 until
                CognitiveTestConfig.forVersion(session.type, session.protocolVersion).responseWindowMillis)
            cognitiveDao.insertTrial(trial.toEntity(sessionId))
        }
    }

    override suspend fun finishCognitiveTest(sessionId: String) {
        database.withTransaction {
            val session = checkNotNull(cognitiveDao.get(sessionId)).toDomain()
            if (session.status == CognitiveSessionStatus.COMPLETED) return@withTransaction
            check(session.status == CognitiveSessionStatus.IN_PROGRESS)
            val expected = when (session.type) {
                CognitiveTestType.FLANKER -> 14
                CognitiveTestType.GO_NO_GO -> 22
                CognitiveTestType.VISUAL_SEARCH -> 9
            }
            check(session.trials.size == expected && session.trials.count { !it.isPractice } == expected - when (session.type) {
                CognitiveTestType.FLANKER, CognitiveTestType.GO_NO_GO -> 2
                CognitiveTestType.VISUAL_SEARCH -> 1
            })
            cognitiveDao.complete(sessionId, System.currentTimeMillis())
        }
    }

    override suspend fun interruptCognitiveTest(sessionId: String, reason: String) =
        cognitiveDao.interrupt(sessionId, System.currentTimeMillis(), reason)

    override suspend fun getCognitiveTest(sessionId: String, workerId: String): CognitiveTestSession? =
        cognitiveDao.get(sessionId)?.toDomain()?.takeIf { it.workerId == workerId }

    override fun observeCognitiveHistory(workerId: String) = cognitiveDao.history(workerId).map { rows -> rows.map { it.toDomain() } }

}
