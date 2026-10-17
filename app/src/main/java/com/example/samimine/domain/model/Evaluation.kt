package com.example.samimine.domain.model

data class Worker(val id: String, val name: String, val area: String, val employment: String)
enum class Shift(val label: String) { DAY("Día"), NIGHT("Noche") }
enum class EvaluationStatus { IN_PROGRESS, COMPLETED, INTERRUPTED }
enum class SyncStatus { NOT_ENABLED }
enum class AttemptOutcome { RESPONSE, ANTICIPATION, OMISSION }

/** Demonstration only. Never use these parameters to classify fitness for work. */
data class PvtConfiguration(
    val version: String = "demo-pvt-v1",
    val attempts: Int = 3,
    val minWaitMillis: Long = 2_000,
    val maxWaitMillis: Long = 5_000,
    val responseWindowMillis: Long = 3_000,
) {
    init {
        require(attempts > 0 && minWaitMillis > 0 && maxWaitMillis >= minWaitMillis)
        require(responseWindowMillis > 0)
    }
}

data class Attempt(val index: Int, val outcome: AttemptOutcome, val reactionNanos: Long? = null) {
    init {
        require(index > 0)
        require(if (outcome == AttemptOutcome.RESPONSE) reactionNanos != null && reactionNanos >= 0 else reactionNanos == null)
    }
}
data class Metrics(val meanReactionMillis: Double?, val responses: Int, val anticipations: Int, val omissions: Int)

data class Evaluation(
    val id: String,
    val worker: Worker,
    val shift: Shift,
    val startedAtMillis: Long,
    val configuration: PvtConfiguration,
    val status: EvaluationStatus = EvaluationStatus.IN_PROGRESS,
    val syncStatus: SyncStatus = SyncStatus.NOT_ENABLED,
    val attempts: List<Attempt> = emptyList(),
    val metrics: Metrics? = null,
    val interruptionReason: String? = null,
)
