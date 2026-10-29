package dev.studios.samimine.domain.evaluation

import dev.studios.samimine.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ExerciseSessionTest {
    private var time = 0L
    private fun session(index: Int = 0) = ExerciseSession(WellbeingExercises.all[index]) { time }

    @Test fun visualClockFreezesResumesAndResetsAtStepBoundaries() {
        val session = session(1)
        session.startOrResume()
        time = 1200
        assertEquals(1200L, session.visualElapsedMillis)
        session.pause()
        time = 9000
        assertEquals(1200L, session.visualElapsedMillis)
        session.startOrResume()
        time += 800
        assertEquals(2000L, session.visualElapsedMillis)
        time += 28000
        session.tick()
        assertEquals(0L, session.visualElapsedMillis)
        time += 1000
        session.skipStep()
        assertEquals(0L, session.visualElapsedMillis)
        assertEquals(ExerciseVisual.HANDS_REST, session.step.visual)
    }

    @Test fun everyGuideCompletesExactlyOnceAfterFiveActiveMinutes() {
        WellbeingExercises.all.forEach { exercise ->
            assertEquals(300, exercise.durationSeconds)
            time = 0
            val session = ExerciseSession(exercise) { time }
            assertEquals(ExerciseSessionStatus.PREPARED, session.status)
            session.startOrResume()
            time = 299_999
            session.tick()
            assertEquals(ExerciseSessionStatus.ACTIVE, session.status)
            assertEquals(1, session.remainingSeconds)
            time++
            session.tick()
            assertEquals(ExerciseSessionStatus.FINISHED, session.status)
            assertEquals(0, session.totalRemainingSeconds)
            session.tick()
            session.startOrResume()
            session.skipStep()
            assertEquals(ExerciseSessionStatus.FINISHED, session.status)
            assertEquals(1f, session.progress)
        }
    }

    @Test fun pausingAccountsForPartialStepAndNeverConsumesBackgroundTime() {
        val session = session()
        session.startOrResume()
        time = 12_500
        session.pause()
        assertEquals(18, session.remainingSeconds)
        time = 500_000
        session.tick()
        assertEquals(18, session.remainingSeconds)
        session.startOrResume()
        time += 17_500
        session.tick()
        assertEquals(1, session.stepIndex)
        assertEquals(60, session.remainingSeconds)
    }

    @Test fun repeatedStartDoesNotResetActiveClockAndLateTickCrossesSteps() {
        val session = session()
        session.startOrResume()
        time = 10_000
        session.startOrResume()
        time = 100_000
        session.tick()
        assertEquals(2, session.stepIndex)
        assertEquals(80, session.remainingSeconds)
        assertEquals(200, session.totalRemainingSeconds)
    }

    @Test fun skipOnlyAppliesToMuscleStepsAndKeepsPauseState() {
        val session = session(1)
        session.skipStep()
        assertEquals(0, session.stepIndex)
        session.startOrResume()
        session.skipStep()
        assertEquals(0, session.stepIndex)
        time = 30_000
        session.tick()
        assertTrue(session.step.canSkip)
        session.pause()
        session.skipStep()
        assertEquals(2, session.stepIndex)
        assertEquals(45, session.remainingSeconds)
        assertEquals(ExerciseSessionStatus.PAUSED, session.status)
        time += 100_000
        session.tick()
        assertEquals(45, session.remainingSeconds)
    }

    @Test fun stoppedControllerCannotRestartAndNewControllerHasNoHistory() {
        val old = session()
        old.startOrResume()
        old.finish()
        old.finish()
        old.startOrResume()
        assertEquals(ExerciseSessionStatus.FINISHED, old.status)
        assertEquals(ExerciseSessionStatus.PREPARED, session().status)
        assertEquals(300, session().totalRemainingSeconds)
    }
}
