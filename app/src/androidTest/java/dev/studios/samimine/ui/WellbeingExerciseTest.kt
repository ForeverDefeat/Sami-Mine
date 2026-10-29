package dev.studios.samimine.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.screens.home.*
import dev.studios.samimine.ui.theme.SamiMineTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WellbeingExerciseTest {
    @get:Rule val compose = createComposeRule()

    @Test fun characterFollowsSessionTimeAndFreezesWhilePaused() {
        var time = 0L
        val session = ExerciseSession(WellbeingExercises.all.first()) { time }.also { it.startOrResume() }
        compose.mainClock.autoAdvance = false
        var playing by mutableStateOf(true)
        compose.setContent { SamiMineTheme { ExerciseCharacter(ExerciseVisual.LOOK_AROUND, session, playing = playing) } }
        compose.mainClock.advanceTimeBy(100)
        fun capture() = compose.onNodeWithTag("exercise-character").captureToImage().asAndroidBitmap()
        val initial = capture()
        compose.runOnIdle { time = 1500L }
        compose.mainClock.advanceTimeBy(100)
        assertFalse(initial.sameAs(capture()))
        compose.runOnIdle { session.pause(); playing = false }
        compose.mainClock.advanceTimeBy(100)
        val paused = capture()
        compose.runOnIdle { time = 9000L }
        compose.mainClock.advanceTimeBy(500)
        assertTrue(paused.sameAs(capture()))
        compose.runOnIdle { session.startOrResume(); time += 1500L; playing = true }
        compose.mainClock.advanceTimeBy(100)
        assertFalse(paused.sameAs(capture()))
    }

    @Test fun catalogCleanDetailAndEarlyExitForAllThreeGuides() {
        compose.setContent { SamiMineTheme { RecommendationsScreen() } }
        WellbeingExercises.all.forEach { exercise ->
            compose.onNodeWithTag("exercise-${exercise.id}").performScrollTo().performClick()
            compose.onNodeWithText("Antes de comenzar").assertExists()
            compose.onNodeWithText("Sustento y fuentes").assertDoesNotExist()
            compose.onNodeWithText(exercise.evidence).assertDoesNotExist()
            compose.onNodeWithTag("exercise-start").performScrollTo().performClick()
            compose.onNodeWithText("Pausar").performScrollTo().performClick()
            compose.onNodeWithText("Continuar").assertIsDisplayed()
            compose.onNodeWithText("Terminar").performScrollTo().performClick()
            compose.onNodeWithText("Una pausa para ti").assertExists()
        }
        compose.onNodeWithText("Consejos para tu evaluación").performScrollTo().performClick()
        compose.onNodeWithText("Antes de las pruebas").performScrollTo().assertIsDisplayed()
    }

    @Test fun lifecyclePauseRequiresExplicitResumeAndMuscleStepCanBeSkipped() {
        var time = 0L
        lateinit var owner: LifecycleOwner
        lateinit var registry: LifecycleRegistry
        lateinit var session: ExerciseSession
        compose.runOnUiThread {
            owner = object : LifecycleOwner { override val lifecycle: Lifecycle get() = registry }
            registry = LifecycleRegistry(owner).apply { currentState = Lifecycle.State.RESUMED }
            session = ExerciseSession(WellbeingExercises.all[1]) { time }.also { it.startOrResume() }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                SamiMineTheme { ExerciseSessionView(session, onExit = {}, onRepeat = {}) }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { time = 30_000; registry.currentState = Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {
            assertEquals(ExerciseSessionStatus.PAUSED, session.status)
            assertEquals(1, session.stepIndex)
            time = 900_000
            registry.currentState = Lifecycle.State.RESUMED
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(5, session.remainingSeconds) }
        compose.onNodeWithText("Omitir este paso").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(2, session.stepIndex); assertEquals(ExerciseSessionStatus.PAUSED, session.status) }
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { time += 300_000 }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Has terminado la guía").assertExists()
        compose.onNodeWithText("Volver a las guías").assertExists()
    }

    @Test fun everyGuideFinishesAndCanBeRepeatedWithoutRestoringPreviousTime() {
        var time = 0L
        var session by mutableStateOf(ExerciseSession(WellbeingExercises.all.first()) { time }.also { it.startOrResume() })
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SamiMineTheme { ExerciseSessionView(session, onExit = {}, onRepeat = {
                session = ExerciseSession(session.exercise) { time }.also { it.startOrResume() }
            }) }
        }
        WellbeingExercises.all.forEach { exercise ->
            compose.runOnIdle { session = ExerciseSession(exercise) { time }.also { it.startOrResume() } }
            compose.mainClock.advanceTimeBy(300)
            compose.runOnIdle { time += 300_000 }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("Has terminado la guía").assertExists()
            compose.onNodeWithText("Repetir guía").performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(300)
            compose.runOnIdle { assertEquals(300, session.totalRemainingSeconds) }
        }
    }
}
