package com.example.samimine.ui

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.samimine.app.PvtPhase
import com.example.samimine.data.demo.DemoWorkers
import com.example.samimine.domain.model.*
import com.example.samimine.ui.components.*
import com.example.samimine.ui.screens.identification.IdentificationScreen
import com.example.samimine.ui.screens.shift.ShiftScreen
import com.example.samimine.ui.screens.home.HomeScreen
import com.example.samimine.ui.screens.instructions.InstructionsScreen
import com.example.samimine.ui.screens.pvt.PvtScreen
import com.example.samimine.ui.screens.result.ResultScreen
import com.example.samimine.ui.screens.history.HistoryScreen
import com.example.samimine.ui.screens.cognitive.*
import com.example.samimine.ui.theme.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Real Compose rendering at six viewport/font settings; fixtures never enter the production DB. */
@RunWith(Parameterized::class)
class VisualReferenceTest(private val width: Int, private val fontScale: Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp-font{1}")
        fun cases() = listOf(360, 390, 412).flatMap { w -> listOf(arrayOf<Any>(w, 1f), arrayOf<Any>(w, 1.5f)) }
    }
    @get:Rule val compose = createComposeRule()
    private val worker = DemoWorkers.all.first()
    private val evaluation = Evaluation("visual-fixture", worker, Shift.DAY, 1_792_164_000_000,
        PvtConfiguration(), EvaluationStatus.COMPLETED,
        attempts = listOf(Attempt(1, AttemptOutcome.RESPONSE, 200_000_000), Attempt(2, AttemptOutcome.RESPONSE, 300_000_000), Attempt(3, AttemptOutcome.OMISSION)),
        metrics = Metrics(250.0, 2, 0, 1))
    private val cognitiveSessions = CognitiveTestType.values().associateWith { type ->
        val config = CognitiveTestConfig.forType(type)
        val trials = CognitiveTrials.generate(config, 42).map { trial ->
            val outcome = when (type) {
                CognitiveTestType.FLANKER -> if (trial.isPractice) "PRACTICE_CORRECT" else "HIT"
                CognitiveTestType.GO_NO_GO -> if (trial.isPractice) "PRACTICE_CORRECT" else if (trial.stimulusCode == "GO") "GO_HIT" else "NO_GO_CORRECT_REJECTION"
                CognitiveTestType.VISUAL_SEARCH -> if (trial.isPractice) "PRACTICE_CORRECT" else "HIT"
            }
            CognitiveTrialRecord(trial.index, trial.isPractice, trial.stimulusCode, trial.expectedResponse,
                trial.expectedResponse, outcome, if (trial.isPractice) null else 450, null, null, trial.interTrialDelayMillis)
        }
        CognitiveTestSession("visual-${type.name}", worker.id, worker.name, worker.area, worker.employment, Shift.DAY,
            type, type.version, "{}", 42, 1_792_164_000_000, 1_792_164_060_000,
            CognitiveSessionStatus.COMPLETED, trials = trials)
    }

    @Test fun referenceScreensFitAndActionsRemainReachable() {
        var screen by mutableStateOf("identification")
        compose.setContent {
            val density = LocalDensity.current.density
            val config = Configuration(LocalConfiguration.current).apply { screenWidthDp = width; this.fontScale = this@VisualReferenceTest.fontScale }
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale), LocalConfiguration provides config) {
                SamiMineTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Surface(Modifier.requiredWidth(width.dp).fillMaxHeight().testTag("visual-root"), color = if (screen == "pvt") TestBackground else PageBackground) {
                            Column {
                                if (screen != "identification") BackAction(if (screen == "pvt") "Abandonar prueba" else "Volver", dark = screen == "pvt") {}
                                Box(Modifier.weight(1f)) {
                                    when (screen) {
                                        "identification" -> IdentificationScreen(DemoWorkers.all, true) {}
                                        "shift" -> ShiftScreen(worker) {}
                                        "home" -> HomeScreen(worker, Shift.DAY, {}, {}, {})
                                        "instructions" -> InstructionsScreen(true) {}
                                        "pvt" -> PvtScreen(PvtPhase.SIGNAL, 2, 3, true, {}, {})
                                        "result" -> ResultScreen(evaluation, false, {}, {})
                                        "history" -> HistoryScreen(worker, listOf(evaluation), true) {}
                                        else -> {
                                            val type = when {
                                                screen.endsWith("flanker") -> CognitiveTestType.FLANKER
                                                screen.endsWith("go-no-go") -> CognitiveTestType.GO_NO_GO
                                                else -> CognitiveTestType.VISUAL_SEARCH
                                            }
                                            when {
                                                screen.startsWith("cognitive-instructions-") -> CognitiveInstructionsScreen(type, {}, {})
                                                screen.startsWith("cognitive-ready-") -> CognitiveReadyScreen(type, {})
                                                screen.startsWith("cognitive-run-") -> CognitiveRunScreen(type,
                                                    CognitiveTrials.generate(CognitiveTestConfig.forType(type), 42).first { !it.isPractice },
                                                    1, CognitiveTestConfig.forType(type).scoredTrials, CognitivePhase.STIMULUS, true, {}, { _, _ -> })
                                                screen.startsWith("practice-") -> CognitivePracticeScreen(
                                                    type, CognitiveTrials.generate(CognitiveTestConfig.forType(type), 42).first(),
                                                    0, CognitiveTestConfig.forType(type).practiceTrials, null, true, {})
                                                screen.startsWith("cognitive-result-") -> CognitiveResultScreen(cognitiveSessions.getValue(type), false, {}, {})
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val cognitiveScreens = listOf("practice", "cognitive-instructions", "cognitive-ready", "cognitive-run", "cognitive-result").flatMap { prefix ->
            listOf("flanker", "go-no-go", "visual-search").map { "$prefix-$it" } }
        for (destination in listOf("identification", "shift", "home", "instructions", "pvt", "result", "history") + cognitiveScreens) {
            compose.runOnIdle { screen = destination }
            compose.waitForIdle()
            capture(destination)
            when (destination) {
                "identification" -> compose.onNodeWithText("Usar Alex").performScrollTo().assertIsDisplayed()
                "shift" -> compose.onNodeWithText("Turno Noche").performScrollTo().assertIsDisplayed()
                "home" -> {
                    compose.onNodeWithText("GUARDADO EN EL TELÉFONO").assertExists()
                    compose.onNodeWithText("Iniciar prueba de reacción").performScrollTo().assertIsDisplayed()
                }
                "instructions" -> assertFooterStaysVisible("Comenzar prueba")
                "pvt" -> {
                    val signal = compose.onNodeWithTag("pvt-stimulus").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val response = compose.onNodeWithTag("pvt-response").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val viewport = compose.onNodeWithTag("visual-root").fetchSemanticsNode().boundsInRoot
                    assertTrue("Stimulus must retain drawable space", signal.height > 24 * compose.density.density)
                    assertTrue("Response must follow stimulus", signal.bottom <= response.top)
                    assertTrue("Response must fit viewport", response.bottom <= viewport.bottom + 1f)
                    compose.onNodeWithTag("pvt-response").assertHeightIsAtLeast(116.dp)
                }
                "result" -> {
                    compose.onNodeWithText("ALERTA").assertDoesNotExist()
                    assertFooterStaysVisible("Volver al inicio")
                }
                "history" -> compose.onNodeWithText("Ver detalle").assertIsDisplayed()
                "practice-flanker" -> {
                    compose.onNodeWithText("Práctica 1/2").assertIsDisplayed()
                    compose.onNodeWithText("Izquierda").assertIsDisplayed()
                    compose.onNodeWithText("Derecha").assertIsDisplayed()
                }
                "practice-go-no-go" -> {
                    compose.onNodeWithText("Práctica 1/2").assertIsDisplayed()
                    compose.onNodeWithText("Responder").assertIsDisplayed()
                }
                "practice-visual-search" -> {
                    compose.onNodeWithText("Práctica 1/1").assertIsDisplayed()
                    compose.onNodeWithTag("visual-target").assertHeightIsAtLeast(48.dp)
                }
                in cognitiveScreens.filter { it.startsWith("cognitive-instructions-") } ->
                    assertFooterStaysVisible("Comenzar práctica")
                in cognitiveScreens.filter { it.startsWith("cognitive-ready-") } ->
                    assertFooterStaysVisible("Comenzar prueba")
                in cognitiveScreens.filter { it.startsWith("cognitive-run-") } -> {
                    compose.onNodeWithTag("cognitive-stimulus").assertIsDisplayed()
                    val action = when {
                        destination.endsWith("go-no-go") -> compose.onNodeWithText("Responder")
                        destination.endsWith("flanker") -> compose.onNodeWithText("Derecha")
                        else -> compose.onNodeWithTag("visual-target").assertHeightIsAtLeast(48.dp)
                    }
                    val bounds = action.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val viewport = compose.onNodeWithTag("visual-root").fetchSemanticsNode().boundsInRoot
                    assertTrue("Response fits viewport", bounds.bottom <= viewport.bottom + 1f)
                }
                in cognitiveScreens.filter { it.startsWith("cognitive-result-") } ->
                    assertFooterStaysVisible("Volver al inicio")
            }
            if (destination in listOf("identification", "shift", "home", "instructions", "result")) capture("$destination-action")
        }
    }

    private fun assertFooterStaysVisible(label: String) {
        val action = compose.onNodeWithText(label).assertIsDisplayed()
        val viewport = compose.onNodeWithTag("visual-root").fetchSemanticsNode().boundsInRoot
        assertTrue("Primary action should sit at the bottom of the viewport", action.fetchSemanticsNode().boundsInRoot.bottom >= viewport.bottom - 140 * compose.density.density)
        compose.onNodeWithTag("action-page-content").performTouchInput { swipeUp() }
        action.assertIsDisplayed()
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val filename = "${width}dp-font${if (fontScale == 1f) "100" else "150"}-$name.png"
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), filename)
        file.outputStream().use { output ->
            assertTrue(compose.onNodeWithTag("visual-root").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        // Shell copies test artifacts to a dedicated public directory before Gradle uninstalls the test APK.
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/samimine-visual-20261016").use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).readBytes()
        }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/samimine-visual-20261016/$filename").use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).readBytes()
        }
    }
}
