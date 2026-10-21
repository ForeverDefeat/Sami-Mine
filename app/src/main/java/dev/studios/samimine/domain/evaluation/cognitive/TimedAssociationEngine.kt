package dev.studios.samimine.domain.evaluation.cognitive

/** Global DSST deadline: never extends after a response or recomposition. */
class TimedAssociationEngine(private val durationMillis: Long = 60_000) {
    var startedAt: Long? = null
        private set
    var finishedAt: Long? = null
        private set
    fun presented(now: Long): Boolean {
        if (startedAt != null || finishedAt != null) return false
        startedAt = now
        return true
    }
    fun accepts(now: Long): Boolean = startedAt?.let { finishedAt == null && now >= it && now - it < durationMillis } == true
    fun expire(now: Long): Boolean {
        val start = startedAt ?: return false
        if (finishedAt != null || now - start < durationMillis) return false
        finishedAt = start + durationMillis
        return true
    }
    fun stop() { if (finishedAt == null) finishedAt = startedAt }
}
