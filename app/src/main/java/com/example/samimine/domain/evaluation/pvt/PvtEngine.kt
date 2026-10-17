package com.example.samimine.domain.evaluation.pvt

import com.example.samimine.domain.model.*

fun interface MonotonicClock { fun nowNanos(): Long }

/** One attempt, one outcome. Called on the UI dispatcher; no Android dependencies. */
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

object PvtScoring {
    fun calculate(attempts: List<Attempt>): Metrics {
        val responses = attempts.filter { it.outcome == AttemptOutcome.RESPONSE }
        return Metrics(
            responses.takeIf { it.isNotEmpty() }?.map { it.reactionNanos!! / 1_000_000.0 }?.average(),
            responses.size,
            attempts.count { it.outcome == AttemptOutcome.ANTICIPATION },
            attempts.count { it.outcome == AttemptOutcome.OMISSION },
        )
    }
}
