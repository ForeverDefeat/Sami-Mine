package dev.studios.samimine.data.repository

import androidx.room.withTransaction
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.local.entity.*
import dev.studios.samimine.domain.evaluation.pvt.PvtScoring
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.repository.EvaluationRepository
import dev.studios.samimine.domain.repository.CognitiveTestRepository
import dev.studios.samimine.domain.repository.BatteryRepository
import kotlinx.coroutines.flow.map

/**
 * Adaptador Room de ambos contratos de dominio sobre la misma base local.
 * Dentro de transacciones compara datos existentes: IGNORE no basta para detectar conflictos.
 * Conserva orden de intentos/ensayos y no confirma sesiones con registros faltantes.
 */
class RoomEvaluationRepository(private val database: SamiMineDatabase) : EvaluationRepository, CognitiveTestRepository, BatteryRepository {
    private val dao = database.evaluations()
    override suspend fun recoverInterrupted() = database.withTransaction { dao.recover("La aplicación se cerró durante la prueba"); enqueueClosed() }

    override suspend fun start(evaluation: Evaluation) {
        require(evaluation.calculationVersion in setOf("legacy-v1", "descriptive-v2"))
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
            dao.complete(evaluationId, metrics.meanReactionMillis, metrics.responses, metrics.anticipations, metrics.omissions,
                if (evaluation.calculationVersion == "descriptive-v2") metrics.medianReactionMillis else null,
                if (evaluation.calculationVersion == "descriptive-v2") metrics.meanResponseSpeed else null)
            if (evaluation.calculationVersion == "descriptive-v2") database.battery().insertMetrics(listOf(
                MetricEntity(evaluationId, "medianReactionMs", metrics.medianReactionMillis, "ms", metrics.responses, evaluation.calculationVersion),
                MetricEntity(evaluationId, "meanReactionMs", metrics.meanReactionMillis, "ms", metrics.responses, evaluation.calculationVersion),
                MetricEntity(evaluationId, "meanResponseSpeed", metrics.meanResponseSpeed, "responses/s", evaluation.attempts.count { (it.reactionNanos ?: 0) > 0 }, evaluation.calculationVersion)))
            database.battery().finishByPvt(evaluationId, "COMPLETED", System.currentTimeMillis(), null)
            enqueueClosed()
        }
    }

    override suspend fun interrupt(evaluationId: String, reason: String) {
        database.withTransaction {
            dao.interrupt(evaluationId, reason)
            database.battery().finishByPvt(evaluationId, "INCOMPLETE", System.currentTimeMillis(), reason)
            enqueueClosed()
        }
    }
    override suspend fun invalidate(evaluationId: String, reason: String) {
        database.withTransaction {
            dao.invalidate(evaluationId, reason)
            database.battery().finishByPvt(evaluationId, "INVALID", System.currentTimeMillis(), reason)
            enqueueClosed()
        }
    }
    override suspend fun get(evaluationId: String, workerId: String): Evaluation? =
        dao.get(evaluationId)?.toDomain()?.takeIf { it.worker.id == workerId }
    override fun observeHistory(workerId: String) = dao.history(workerId).map { rows -> rows.map { it.toDomain() } }

    private val cognitiveDao = database.cognitiveTests()
    override suspend fun recoverCognitiveTests() = database.withTransaction { cognitiveDao.recover(System.currentTimeMillis(), "La aplicación se cerró durante la prueba"); enqueueClosed() }

    override suspend fun startCognitiveTest(session: CognitiveTestSession) {
        require(session.calculationVersion in setOf("legacy-v1", "descriptive-v2"))
        CognitiveTestConfig.forVersion(session.type, session.protocolVersion)
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

    /** Confirma el cierre; las métricas cognitivas se calculan al leer el modelo de dominio. */
    override suspend fun finishCognitiveTest(sessionId: String) {
        database.withTransaction {
            val session = checkNotNull(cognitiveDao.get(sessionId)).toDomain()
            if (session.status == CognitiveSessionStatus.COMPLETED) return@withTransaction
            check(session.status == CognitiveSessionStatus.IN_PROGRESS)
            val config = CognitiveTestConfig.forVersion(session.type, session.protocolVersion)
            check(session.trials.count { it.isPractice } == config.practiceTrials)
            check(session.trials.map { it.index } == (1..session.trials.size).toList())
            if (session.type == CognitiveTestType.DSST) {
                val start = checkNotNull(session.executionStartedUptimeMillis)
                check(checkNotNull(session.executionFinishedUptimeMillis) - start == 60_000L)
                check(session.trials.filterNot { it.isPractice }.all { it.responseUptimeMillis != null && it.responseUptimeMillis in start until start + 60_000 })
            } else check(session.trials.count { !it.isPractice } == config.scoredTrials)
            if (session.calculationVersion == "descriptive-v2") database.battery().insertMetrics(
                BatteryScoring.describe(session.type, session.trials).map { MetricEntity(session.id, it.key, it.value, it.unit, it.denominator, it.methodVersion) })
            cognitiveDao.complete(sessionId, System.currentTimeMillis())
            enqueueClosed()
        }
    }

    override suspend fun interruptCognitiveTest(sessionId: String, reason: String) =
        database.withTransaction { cognitiveDao.interrupt(sessionId, System.currentTimeMillis(), reason); enqueueClosed() }

    override suspend fun getCognitiveTest(sessionId: String, workerId: String): CognitiveTestSession? =
        cognitiveDao.get(sessionId)?.toDomain()?.takeIf { it.workerId == workerId }

    override fun observeCognitiveHistory(workerId: String) = cognitiveDao.history(workerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun recordExecutionTimes(sessionId: String, started: Long, finished: Long?) {
        database.withTransaction {
            val session = checkNotNull(cognitiveDao.get(sessionId)).session
            // A committed finish followed by a failed read must be safe to retry.
            if (session.executionStartedUptimeMillis == started && session.executionFinishedUptimeMillis == finished) return@withTransaction
            check(session.status == "IN_PROGRESS")
            check(session.executionStartedUptimeMillis == null || session.executionStartedUptimeMillis == started)
            require(finished == null || finished >= started)
            cognitiveDao.executionTimes(sessionId, started, finished)
        }
    }

    override suspend fun invalidateCognitiveTest(sessionId: String, reason: String) = database.withTransaction { cognitiveDao.invalidate(sessionId, System.currentTimeMillis(), reason); enqueueClosed() }
    override suspend fun recoverAssessments() = database.withTransaction { database.battery().recover(System.currentTimeMillis()); enqueueClosed() }
    override suspend fun startAssessment(session: AssessmentSession) {
        database.withTransaction {
            database.battery().insertAssessment(session.toEntity())
            check(database.battery().assessment(session.id, session.workerId) == session.toEntity())
        }
    }
    override suspend fun linkAssessmentPvt(id: String, workerId: String, pvtId: String) {
        database.withTransaction {
            val parent = checkNotNull(database.battery().assessment(id, workerId))
            val pvt = checkNotNull(dao.get(pvtId)).evaluation
            check(parent.status == "IN_PROGRESS" && pvt.workerId == workerId && pvt.shift == parent.shift)
            check(parent.pvtId == null || parent.pvtId == pvtId)
            database.battery().linkPvt(id, workerId, pvtId)
        }
    }
    override suspend fun interruptAssessment(id: String, workerId: String, reason: String) = database.withTransaction { database.battery().interrupt(id, workerId, System.currentTimeMillis(), reason); enqueueClosed() }
    override suspend fun getAssessment(id: String, workerId: String) = database.battery().assessment(id, workerId)?.toDomain()
    override fun observeAssessments(workerId: String) = database.battery().assessments(workerId).map { rows -> rows.map { it.toDomain() } }
    override suspend fun saveSelfReport(report: SelfReport) {
        database.withTransaction {
            report.assessmentId?.let { id ->
                val parent = checkNotNull(database.battery().assessment(id, report.workerId))
                check(parent.status == "IN_PROGRESS" && parent.shift == report.shift.name)
            }
            database.battery().insertReport(report.toEntity())
            check(database.battery().report(report.id, report.workerId) == report.toEntity())
            enqueueClosed()
        }
    }
    override suspend fun getSelfReport(id: String, workerId: String) = database.battery().report(id, workerId)?.toDomain()
    override fun observeSelfReports(workerId: String) = database.battery().reports(workerId).map { rows -> rows.map { it.toDomain() } }

    /** Runs inside the same transaction as every terminal write; IGNORE preserves acknowledgments. */
    private suspend fun enqueueClosed() {
        val uidRows = database.openHelper.writableDatabase
        uidRows.execSQL("INSERT OR IGNORE INTO sync_queue SELECT 'PVT', id, workerId, 'PENDING', 0, NULL FROM evaluations WHERE status != 'IN_PROGRESS'")
        uidRows.execSQL("INSERT OR IGNORE INTO sync_queue SELECT 'COGNITIVE', id, workerId, 'PENDING', 0, NULL FROM cognitive_sessions WHERE status != 'IN_PROGRESS'")
        uidRows.execSQL("INSERT OR IGNORE INTO sync_queue SELECT 'ASSESSMENT', id, workerId, 'PENDING', 0, NULL FROM assessment_sessions WHERE status != 'IN_PROGRESS'")
        uidRows.execSQL("INSERT OR IGNORE INTO sync_queue SELECT 'REPORT', id, workerId, 'PENDING', 0, NULL FROM self_reports")
    }
}
