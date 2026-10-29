package dev.studios.samimine.domain.model

enum class ExerciseSessionStatus { PREPARED, ACTIVE, PAUSED, FINISHED }

/** Ephemeral controller: timestamps are monotonic, and only active time consumes a step. */
class ExerciseSession(val exercise: WellbeingExercise, private val nowMillis: () -> Long) {
    var status = ExerciseSessionStatus.PREPARED
        private set
    var stepIndex = 0
        private set
    private var stepElapsed = 0L
    private var activeSince = 0L
    val step get() = exercise.steps[stepIndex]
    val remainingMillis get() = (step.seconds * 1000L - stepElapsed).coerceAtLeast(0)
    val visualElapsedMillis: Long get() = (stepElapsed +
        if (status == ExerciseSessionStatus.ACTIVE) (nowMillis() - activeSince).coerceAtLeast(0) else 0L)
        .coerceAtMost(step.seconds * 1000L)
    val remainingSeconds get() = ((remainingMillis + 999) / 1000).toInt()
    val totalRemainingSeconds get() = remainingSeconds + exercise.steps.drop(stepIndex + 1).sumOf { it.seconds }
    val progress get() = if (status == ExerciseSessionStatus.FINISHED) 1f else
        (exercise.steps.take(stepIndex).sumOf { it.seconds } + stepElapsed / 1000f) / exercise.durationSeconds

    fun startOrResume() {
        if (status != ExerciseSessionStatus.PREPARED && status != ExerciseSessionStatus.PAUSED) return
        activeSince = nowMillis()
        status = ExerciseSessionStatus.ACTIVE
    }

    fun tick() {
        if (status != ExerciseSessionStatus.ACTIVE) return
        val now = nowMillis()
        stepElapsed += (now - activeSince).coerceAtLeast(0)
        activeSince = now
        while (stepElapsed >= step.seconds * 1000L) {
            if (stepIndex == exercise.steps.lastIndex) {
                stepElapsed = step.seconds * 1000L
                status = ExerciseSessionStatus.FINISHED
                return
            }
            stepElapsed -= step.seconds * 1000L
            stepIndex++
        }
    }

    fun pause() {
        if (status != ExerciseSessionStatus.ACTIVE) return
        tick()
        if (status == ExerciseSessionStatus.ACTIVE) status = ExerciseSessionStatus.PAUSED
    }

    fun skipStep() {
        if (status != ExerciseSessionStatus.ACTIVE && status != ExerciseSessionStatus.PAUSED) return
        val requestedStep = stepIndex
        tick()
        if (status == ExerciseSessionStatus.FINISHED || requestedStep != stepIndex || !step.canSkip) return
        if (stepIndex == exercise.steps.lastIndex) finish() else {
            stepIndex++
            stepElapsed = 0
            activeSince = nowMillis()
        }
    }

    fun finish() { status = ExerciseSessionStatus.FINISHED }
}
