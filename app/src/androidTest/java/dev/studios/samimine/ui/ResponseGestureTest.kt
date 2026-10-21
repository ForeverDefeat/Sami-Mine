package dev.studios.samimine.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import dev.studios.samimine.app.PvtPhase
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.screens.cognitive.CognitiveRunScreen
import dev.studios.samimine.ui.screens.pvt.PvtScreen
import dev.studios.samimine.ui.theme.SamiMineTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Physical down/up events, including gestures split across recomposition and trial boundaries. */
@RunWith(Parameterized::class)
class ResponseGestureTest(private val kind: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = listOf("PVT", "FLANKER", "GO_NO_GO", "VISUAL_SEARCH")
    }
    @get:Rule val compose = createComposeRule()
    private var enabled by mutableStateOf(true)
    private var index by mutableIntStateOf(1)
    private val answers = mutableListOf<Pair<Int, Long>>()

    private fun show() {
        compose.setContent {
            SamiMineTheme {
                val trialIndex = index
                if (kind == "PVT") PvtScreen(PvtPhase.SIGNAL, trialIndex, 3, enabled, {}, {
                    answers += trialIndex to 0L; enabled = false
                }) else {
                    val type = CognitiveTestType.valueOf(kind)
                    CognitiveRunScreen(type, CognitiveTrial(trialIndex, false,
                        if (type == CognitiveTestType.GO_NO_GO) "GO" else "CONGRUENT_LEFT", "LEFT", 0),
                        trialIndex, 20, CognitivePhase.STIMULUS, enabled, {}, { _, time ->
                            answers += trialIndex to time; enabled = false
                        })
                }
            }
        }
    }
    private fun target() = when (kind) {
        "PVT" -> compose.onNodeWithTag("pvt-response")
        "FLANKER" -> compose.onNodeWithText("Izquierda")
        "GO_NO_GO" -> compose.onNodeWithText("Responder")
        else -> compose.onNodeWithTag("visual-target")
    }
    private fun next() = compose.runOnIdle { index++; enabled = true }

    @Test fun recordsAtDownAndReleaseCannotAnswerNextTrial() {
        show()
        target().performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(listOf(1), answers.map { it.first }) }
        next()
        target().performTouchInput { advanceEventTime(1_000); up() }
        compose.runOnIdle { assertEquals(1, answers.size) }
        target().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(1, 2), answers.map { it.first }) }
    }
    @Test fun secondFingerCannotAnswerUntilAllFingersLift() {
        show()
        target().performTouchInput { down(0, center); down(1, center) }
        compose.runOnIdle { assertEquals(1, answers.size) }
        next()
        target().performTouchInput { up(0); down(2, center); up(1); up(2) }
        compose.runOnIdle { assertEquals(1, answers.size) }
        target().performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, answers.size) }
    }
    @Test fun disabledContactCannotBecomeAnAnswerWhenEnabled() {
        enabled = false
        show()
        target().performTouchInput { down(center) }
        next()
        target().performTouchInput { up() }
        compose.runOnIdle { assertTrue(answers.isEmpty()) }
        target().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, answers.size) }
    }
    @Test fun doubleTapDuringTransitionOnlyAnswersOnce() {
        show()
        target().performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(1, answers.size) }
    }
    @Test fun semanticActionRemainsAvailableAndRespectsDisabledState() {
        show()
        target().performSemanticsAction(SemanticsActions.OnClick) { it() }
        target().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, answers.size) }
    }
}
