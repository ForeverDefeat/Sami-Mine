package dev.studios.samimine.data.repository

import androidx.room.withTransaction
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.local.entity.*
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.repository.GuidedBatteryRepository
import kotlinx.coroutines.flow.map

class RoomGuidedBatteryRepository(private val db: SamiMineDatabase) : GuidedBatteryRepository {
    private val dao = db.guided()
    override suspend fun guided(id: String, uid: String) = dao.get(id, uid)?.toDomain()
    override fun guidedHistory(uid: String) = dao.observe(uid).map { rows -> rows.map { it.toDomain() } }
    override suspend fun startGuided(run: GuidedBatteryRun) = db.withTransaction {
        run.validate()
        val existing = guided(run.id, run.workerId)
        if (existing != null) {
            check(existing.definition == run.definition && existing.shift == run.shift && existing.startedAtMillis == run.startedAtMillis)
        } else {
            check(dao.all(run.workerId).none { !it.toDomain().terminal }) { "Ya hay una batería pendiente" }
            check(run.status == BatteryRunStatus.PREPARED && run.steps.all { it.attempts.isEmpty() })
            dao.insert(run.toEntity())
        }
    }
    override suspend fun recoverGuidedBatteries() = db.withTransaction {
        dao.unfinished().forEach { row ->
            val refreshed = refreshGuided(row.id, row.workerId)
            if (!refreshed.terminal) setGuidedState(row.id, row.workerId, BatteryRunStatus.PAUSED)
        }
    }
    override suspend fun setGuidedState(id: String, uid: String, status: BatteryRunStatus) = db.withTransaction {
        require(status in setOf(BatteryRunStatus.PAUSED, BatteryRunStatus.IN_PROGRESS, BatteryRunStatus.ABANDONED))
        var run = checkNotNull(guided(id, uid))
        if (run.terminal) { check(run.status == status || status == BatteryRunStatus.PAUSED); return@withTransaction }
        if (status != BatteryRunStatus.IN_PROGRESS) {
            run.steps.flatMap { it.attempts }.filter { it.status == "IN_PROGRESS" }.forEach { attempt ->
                when (attempt.kind) {
                    "PVT" -> db.evaluations().interrupt(attempt.runId, "El recorrido se pausó")
                    "COGNITIVE" -> db.cognitiveTests().interrupt(attempt.runId, System.currentTimeMillis(), "El recorrido se pausó")
                }
            }
            run = refreshGuided(id, uid)
            if (run.terminal) return@withTransaction
        }
        persist(run.copy(status = status, finishedAtMillis = if (status == BatteryRunStatus.ABANDONED) System.currentTimeMillis() else null))
        enqueueAttempts(run)
    }
    private suspend fun attach(id: String, uid: String, shift: Shift, index: Int, module: String, attempt: BatteryAttempt) {
        val run = checkNotNull(guided(id, uid))
        if (run.steps.getOrNull(index)?.attempts?.any { it.runId == attempt.runId && it.kind == attempt.kind } == true) return
        check(!run.terminal && run.shift == shift && run.nextStep == index && run.steps[index].moduleId == module)
        check(run.steps[index].status != BatteryStepStatus.IN_PROGRESS)
        val steps = run.steps.toMutableList()
        steps[index] = steps[index].copy(status = BatteryStepStatus.IN_PROGRESS, attempts = steps[index].attempts + attempt)
        persist(run.copy(steps = steps, status = BatteryRunStatus.IN_PROGRESS))
    }
    override suspend fun startGuidedPvt(id: String, index: Int, evaluation: Evaluation) = db.withTransaction {
        attach(id, evaluation.worker.id, evaluation.shift, index, "PVT", BatteryAttempt("PVT", evaluation.id, "IN_PROGRESS"))
        RoomEvaluationRepository(db).start(evaluation)
    }
    override suspend fun startGuidedCognitive(id: String, index: Int, session: CognitiveTestSession) = db.withTransaction {
        attach(id, session.workerId, session.shift, index, session.type.name, BatteryAttempt("COGNITIVE", session.id, "IN_PROGRESS"))
        RoomEvaluationRepository(db).startCognitiveTest(session)
    }
    override suspend fun saveGuidedReport(id: String, index: Int, report: SelfReport) = db.withTransaction {
        check(report.assessmentId == null)
        attach(id, report.workerId, report.shift, index, report.instrumentId.name,
            BatteryAttempt("REPORT", report.id, if (report.omitted) "OMITTED" else "COMPLETED"))
        RoomEvaluationRepository(db).saveSelfReport(report)
        refreshGuided(id, report.workerId)
        Unit
    }
    override suspend fun refreshGuided(id: String, uid: String): GuidedBatteryRun = db.withTransaction {
        val run = checkNotNull(guided(id, uid))
        if (run.terminal) return@withTransaction run
        val steps = run.steps.map { step ->
            val attempts = step.attempts.map { attempt ->
                val status = when (attempt.kind) {
                    "PVT" -> db.evaluations().get(attempt.runId)?.evaluation?.takeIf { it.workerId == uid }?.status
                    "COGNITIVE" -> db.cognitiveTests().get(attempt.runId)?.session?.takeIf { it.workerId == uid }?.status
                    "REPORT" -> db.battery().report(attempt.runId, uid)?.let { if (it.omitted) "OMITTED" else "COMPLETED" }
                    else -> null
                }
                attempt.copy(status = checkNotNull(status) { "Falta un resultado vinculado" })
            }
            val status = when (attempts.lastOrNull()?.status) {
                "COMPLETED" -> BatteryStepStatus.COMPLETED
                "OMITTED" -> { check(step.moduleId == "KSS"); BatteryStepStatus.OMITTED }
                "IN_PROGRESS" -> BatteryStepStatus.IN_PROGRESS
                null -> BatteryStepStatus.PENDING
                else -> BatteryStepStatus.INTERRUPTED
            }
            step.copy(status = status, attempts = attempts)
        }
        val completed = steps.all { it.status in setOf(BatteryStepStatus.COMPLETED, BatteryStepStatus.OMITTED) }
        val next = run.copy(steps = steps, status = if (completed) BatteryRunStatus.COMPLETED else run.status,
            finishedAtMillis = if (completed) System.currentTimeMillis() else null)
        persist(next)
        next
    }
    private suspend fun persist(run: GuidedBatteryRun) {
        run.validate()
        dao.update(run.toEntity())
        if (run.terminal) db.sync().enqueue(SyncEntry("BATTERY", run.id, run.workerId, "PENDING", 0, null))
    }
    private suspend fun enqueueAttempts(run: GuidedBatteryRun) {
        run.steps.flatMap { it.attempts }.filter { it.status != "IN_PROGRESS" }.forEach {
            db.sync().enqueue(SyncEntry(it.kind, it.runId, run.workerId, "PENDING", 0, null))
        }
    }
}
