package com.example.samimine.application.usecase

import com.example.samimine.domain.model.*
import com.example.samimine.domain.repository.EvaluationRepository
import java.util.UUID

class EvaluationUseCases(private val repository: EvaluationRepository) {
    fun prepare(worker: Worker, shift: Shift) = Evaluation(
        UUID.randomUUID().toString(), worker, shift, System.currentTimeMillis(), PvtConfiguration(),
    )
    suspend fun start(evaluation: Evaluation) = repository.start(evaluation)
    suspend fun record(id: String, attempt: Attempt) = repository.saveAttempt(id, attempt)
    suspend fun finish(id: String) = repository.finish(id)
    suspend fun interrupt(id: String, reason: String) = repository.interrupt(id, reason)
}
