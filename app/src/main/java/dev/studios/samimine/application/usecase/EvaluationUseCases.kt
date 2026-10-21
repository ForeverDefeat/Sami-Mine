package dev.studios.samimine.application.usecase

import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.repository.EvaluationRepository
import java.util.UUID

/**
 * Operaciones PVT sobre el contrato de persistencia, sin dependencias de UI ni Room.
 * prepare crea una sola identidad y fecha de calendario para reutilizarlas ante reintentos;
 * la hora de calendario no se utiliza para calcular tiempos de reacción.
 */
class EvaluationUseCases(private val repository: EvaluationRepository) {
    fun prepare(worker: Worker, shift: Shift) = Evaluation(
        UUID.randomUUID().toString(), worker, shift, System.currentTimeMillis(), PvtConfiguration(),
    )
    suspend fun start(evaluation: Evaluation) = repository.start(evaluation)
    suspend fun record(id: String, attempt: Attempt) = repository.saveAttempt(id, attempt)
    suspend fun finish(id: String) = repository.finish(id)
    suspend fun interrupt(id: String, reason: String) = repository.interrupt(id, reason)
}
