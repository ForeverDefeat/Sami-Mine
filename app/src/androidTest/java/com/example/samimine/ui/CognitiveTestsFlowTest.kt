package com.example.samimine.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.example.samimine.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CognitiveTestsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun home(testName: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Usar Alex").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Usar Alex").performScrollTo().performClick()
        compose.onNodeWithText("Turno Día").performScrollTo().performClick()
        compose.onNodeWithText(testName).performScrollTo().performClick()
        compose.onNodeWithText(testName).assertIsDisplayed()
        compose.onNodeWithText("Comenzar práctica").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Práctica 1/", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun completePractice(type: String) {
        when (type) {
            "Flecha central" -> {
                compose.onNodeWithText("Izquierda").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Práctica 2/", substring = true).fetchSemanticsNodes().isNotEmpty() }
                compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Derecha").assertIsEnabled() }.isSuccess }
                compose.onNodeWithText("Derecha").performClick()
            }
            "Responder o esperar" -> {
                compose.onNodeWithText("Responder").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Práctica 2/", substring = true).fetchSemanticsNodes().isNotEmpty() }
                compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("No responder").assertIsEnabled() }.isSuccess }
                compose.onNodeWithText("No responder").performClick()
            }
            else -> {
                compose.onNodeWithTag("visual-target").performClick()
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Listo para comenzar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Comenzar prueba").assertIsDisplayed().performClick()
    }

    @Test fun flankerBackgroundInterruptsInsteadOfAdvancing() {
        home("Flecha central"); completePractice("Flecha central")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertInterrupted()
    }

    @Test fun goNoGoRecreationInterruptsInsteadOfResuming() {
        home("Responder o esperar"); completePractice("Responder o esperar")
        compose.activityRule.scenario.recreate()
        assertInterrupted()
    }

    @Test fun visualSearchAbandonKeepsIncompleteResult() {
        home("Busca la T"); completePractice("Busca la T")
        compose.onNodeWithText("Abandonar prueba").performClick()
        assertInterrupted()
    }

    private fun assertInterrupted() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración interrumpida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ACTIVIDAD INTERRUMPIDA").assertExists()
        compose.onNodeWithText("Repetir actividad").assertIsDisplayed()
    }

    private fun waitForTrial(label: String, number: Int, total: Int) {
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("$label · $number/$total").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("cognitive-stimulus").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Compose's synthetic event clock advances independently of real uptime during waitUntil.
     * Use Android input timestamps for end-to-end reaction-time assertions. */
    private fun SemanticsNodeInteraction.deviceClick() {
        val center = fetchSemanticsNode().boundsInWindow.center
        val now = SystemClock.uptimeMillis()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, center.x, center.y, 0).let {
            try { instrumentation.sendPointerSync(it) } finally { it.recycle() }
        }
        MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, center.x, center.y, 0).let {
            try { instrumentation.sendPointerSync(it) } finally { it.recycle() }
        }
    }

    private fun metric(key: String, value: String) {
        compose.onNodeWithTag("metric-$key").assert(hasAnyDescendant(hasText(value)))
    }

    @Test fun flankerPracticeRunSummaryAndHistoryAreReachable() {
        home("Flecha central")
        completePractice("Flecha central")
        repeat(12) { index ->
            waitForTrial("Flecha central", index + 1, 12)
            compose.onNodeWithText("Izquierda").deviceClick()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Flecha central").assertExists()
        metric("correct", "6")
        metric("errors", "6")
        metric("omissions", "0")
        compose.onNodeWithText("No determina si estás apto para trabajar", substring = true).assertExists()
        compose.onNodeWithText("Volver al inicio").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ver mi historial").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ver mi historial").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Mi historial").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("12 series"))
        compose.onAllNodesWithText("12 series").onFirst().assertIsDisplayed()
    }

    @Test fun goNoGoCanBeCompletedAndSavedLocally() {
        home("Responder o esperar")
        completePractice("Responder o esperar")
        repeat(20) { index ->
            waitForTrial("Responder o esperar", index + 1, 20)
            compose.onNodeWithText("Responder").deviceClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("cognitive-stimulus").fetchSemanticsNodes().isEmpty() }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Toques ante el cuadrado").assertExists()
        metric("goHits", "16")
        metric("noGoCommissions", "4")
        metric("goOmissions", "0")
        metric("anticipations", "0")
        compose.onNodeWithText("Toques antes de la señal").assertExists()
        compose.onNodeWithText("Guardado en este teléfono").assertExists()
    }

    @Test fun visualSearchUsesGridAndShowsDescriptiveMetrics() {
        home("Busca la T")
        completePractice("Busca la T")
        repeat(8) { index ->
            waitForTrial("Busca la T", index + 1, 8)
            compose.onNodeWithTag("visual-target").deviceClick()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Letras T encontradas").assertExists()
        metric("found", "8")
        metric("omissions", "0")
        compose.onNodeWithText("Respuestas correctas").assertExists()
        compose.onNodeWithText("Busca la T").assertExists()
    }
}
