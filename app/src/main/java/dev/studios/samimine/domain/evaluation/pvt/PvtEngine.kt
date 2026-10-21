package dev.studios.samimine.domain.evaluation.pvt

import dev.studios.samimine.domain.model.*

/** Fuente monotónica inyectable; todas las marcas de un intento deben usar esta misma base. */
fun interface MonotonicClock { fun nowNanos(): Long }

/**
 * Resuelve un intento una sola vez; se invoca de forma serial desde UI y no depende de Android.
 * Antes del dibujo una respuesta es anticipación; en el límite de ventana ya es omisión.
 * Tras responder, vencer o detenerse, no genera otro registro hasta llamar a begin.
 */
class PvtEngine(private val clock: MonotonicClock, private val responseWindowMillis: Long) {
    private var stimulusAt: Long? = null
    private var index = 0
    private var resolved = true

    fun begin(index: Int) {
        require(index > 0)
        this.index = index
        stimulusAt = null
        resolved = false
    }

    fun stimulusPresented() {
        if (!resolved && stimulusAt == null) stimulusAt = clock.nowNanos()
    }

    fun respond(): Attempt? {
        if (resolved) return null
        val onset = stimulusAt
        val elapsed = onset?.let { (clock.nowNanos() - it).coerceAtLeast(0) }
        val attempt = when {
            elapsed == null -> Attempt(index, AttemptOutcome.ANTICIPATION)
            elapsed >= responseWindowMillis * 1_000_000 -> Attempt(index, AttemptOutcome.OMISSION)
            else -> Attempt(index, AttemptOutcome.RESPONSE, elapsed)
        }
        resolved = true
        return attempt
    }

    fun expire(): Attempt? {
        val onset = stimulusAt ?: return null
        if (resolved || clock.nowNanos() - onset < responseWindowMillis * 1_000_000) return null
        resolved = true
        return Attempt(index, AttemptOutcome.OMISSION)
    }

    fun stop() { resolved = true }
}

/** Promedia solo respuestas válidas; una lista sin respuestas conserva media nula. */
object PvtScoring {
    fun calculate(attempts: List<Attempt>): Metrics {
        val responses = attempts.filter { it.outcome == AttemptOutcome.RESPONSE }
        return Metrics(
            responses.takeIf { it.isNotEmpty() }?.map { it.reactionNanos!! / 1_000_000.0 }?.average(),
            responses.size,
            attempts.count { it.outcome == AttemptOutcome.ANTICIPATION },
            attempts.count { it.outcome == AttemptOutcome.OMISSION },
            BatteryScoring.median(responses.map { it.reactionNanos!! / 1_000_000.0 }),
            responses.mapNotNull { it.reactionNanos?.takeIf { ns -> ns > 0 }?.let { ns -> 1_000_000_000.0 / ns } }.takeIf { it.isNotEmpty() }?.average(),
        )
    }
}
