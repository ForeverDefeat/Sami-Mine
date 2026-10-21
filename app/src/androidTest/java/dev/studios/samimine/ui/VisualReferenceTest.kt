package dev.studios.samimine.ui

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
import dev.studios.samimine.app.WorkerProfileMenu
import dev.studios.samimine.app.WorkerNavigation
import dev.studios.samimine.app.navigation.WorkerTab
import dev.studios.samimine.ui.screens.home.WorkerCatalogScreen
import dev.studios.samimine.app.PvtPhase
import dev.studios.samimine.data.demo.DemoWorkers
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.screens.identification.IdentificationScreen
import dev.studios.samimine.ui.screens.shift.ShiftScreen
import dev.studios.samimine.ui.screens.home.HomeScreen
import dev.studios.samimine.ui.screens.instructions.InstructionsScreen
import dev.studios.samimine.ui.screens.pvt.PvtScreen
import dev.studios.samimine.ui.screens.result.ResultScreen
import dev.studios.samimine.ui.screens.history.HistoryScreen
import dev.studios.samimine.ui.screens.cognitive.*
import dev.studios.samimine.ui.theme.*
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
        metrics = Metrics(250.0, 2, 0, 1, 250.0, 4.1666666667))
    private val cognitiveSessions = CognitiveTestType.values().associateWith { type ->
        val config = CognitiveTestConfig.forType(type)
        val trials = CognitiveTrials.generate(config, 42).map { trial ->
            val outcome = when (type) {
                CognitiveTestType.FLANKER -> if (trial.isPractice) "PRACTICE_CORRECT" else "HIT"
                CognitiveTestType.GO_NO_GO -> if (trial.isPractice) "PRACTICE_CORRECT" else if (trial.stimulusCode == "GO") "GO_HIT" else "NO_GO_CORRECT_REJECTION"
                CognitiveTestType.VISUAL_SEARCH -> if (trial.isPractice) "PRACTICE_CORRECT" else "HIT"
                CognitiveTestType.DSST -> if (trial.isPractice) "PRACTICE_CORRECT" else "HIT"
                CognitiveTestType.N_BACK -> if (trial.isPractice) "PRACTICE_CORRECT" else if (trial.expectedResponse == "SAME") "NBACK_HIT" else "CORRECT_REJECTION"
            }
            CognitiveTrialRecord(trial.index, trial.isPractice, trial.stimulusCode, trial.expectedResponse,
                trial.expectedResponse, outcome, if (trial.isPractice) null else 450, null, null, trial.interTrialDelayMillis)
        }
        CognitiveTestSession("visual-${type.name}", worker.id, worker.name, worker.area, worker.employment, Shift.DAY,
            type, type.version, "{}", 42, 1_792_164_000_000, 1_792_164_060_000,
            CognitiveSessionStatus.COMPLETED, trials = trials, calculationVersion = "descriptive-v2")
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
                                if (screen in listOf("home", "tests", "questionnaires", "history")) WorkerProfileMenu(true, {}, {})
                                if (screen !in listOf("identification", "home", "tests", "questionnaires", "history")) BackAction(if (screen == "pvt") "Abandonar prueba" else "Volver", dark = screen == "pvt") {}
                                Box(Modifier.weight(1f)) {
                                    when (screen) {
                                        "identification" -> IdentificationScreen(DemoWorkers.all, true) {}
                                        "shift" -> ShiftScreen(worker) {}
                                        "tests", "questionnaires" -> key(screen) { WorkerCatalogScreen(screen == "questionnaires", InstrumentCatalog.definitions, true, {}, {}, {}) }
                                        "home" -> HomeScreen(worker, Shift.DAY, {})
                                        "instructions" -> InstructionsScreen(true) {}
                                        "pvt" -> PvtScreen(PvtPhase.SIGNAL, 2, 3, true, {}, {})
                                        "result" -> ResultScreen(evaluation, false, {}, {})
                                        "history" -> HistoryScreen(worker, listOf(evaluation), true) {}
                                        "questionnaire" -> QuestionnaireScreen(InstrumentCatalog.definitions.first(), false, true, { _, _, _, _ -> }, {})
                                        "assessment" -> AssessmentResultScreen(AssessmentSession("fixture", worker.id, Shift.DAY, evaluation.startedAtMillis, "COMPLETED", evaluation.id),
                                            listOf(SelfReport("fixture-report", worker.id, Shift.DAY, "fixture", InstrumentId.KSS, "synthetic-fixture", "es-PE", evaluation.startedAtMillis, emptyMap(), true, null, null)), evaluation, false, {})
                                        else -> {
                                            val type = when {
                                                screen.endsWith("flanker") -> CognitiveTestType.FLANKER
                                                screen.endsWith("go-no-go") -> CognitiveTestType.GO_NO_GO
                                                screen.endsWith("dsst") -> CognitiveTestType.DSST
                                                screen.endsWith("n-back") -> CognitiveTestType.N_BACK
                                                else -> CognitiveTestType.VISUAL_SEARCH
                                            }
                                            when {
                                                screen.startsWith("cognitive-instructions-") -> CognitiveInstructionsScreen(type, {}, {})
                                                screen.startsWith("cognitive-ready-") -> CognitiveReadyScreen(type, {})
                                                screen.startsWith("cognitive-run-") -> CognitiveRunScreen(type,
                                                    CognitiveTrials.generate(CognitiveTestConfig.forType(type), 42).first { !it.isPractice },
                                                    1, CognitiveTestConfig.forType(type).scoredTrials, CognitivePhase.STIMULUS, true, {}, { _, _ -> }, seed = 42)
                                                screen.startsWith("practice-") -> CognitivePracticeScreen(
                                                    type, CognitiveTrials.generate(CognitiveTestConfig.forType(type), 42).first(),
                                                    0, CognitiveTestConfig.forType(type).practiceTrials,
                                                    if (screen.startsWith("practice-feedback")) "La respuesta era ${if (type == CognitiveTestType.DSST) "el dígito 6" else "Diferente"}" else null, true, {}, seed = 42)
                                                screen.startsWith("cognitive-result-") -> CognitiveResultScreen(cognitiveSessions.getValue(type), false, {}, {})
                                            }
                                        }
                                    }
                                }
                                if (screen in listOf("home", "tests", "questionnaires", "history", "instructions", "result", "assessment") || screen.startsWith("cognitive-instructions-") || screen.startsWith("cognitive-result-")) {
                                    WorkerNavigation(when (screen) {
                                        "tests" -> WorkerTab.TESTS
                                        "questionnaires" -> WorkerTab.QUESTIONNAIRES
                                        "history" -> WorkerTab.HISTORY
                                        else -> if (screen.startsWith("cognitive-")) WorkerTab.TESTS else WorkerTab.HOME
                                    }, true, {})
                                }
                            }
                        }
                    }
                }
            }
        }
        val cognitiveScreens = listOf("practice", "cognitive-instructions", "cognitive-ready", "cognitive-run", "cognitive-result").flatMap { prefix ->
            listOf("flanker", "go-no-go", "visual-search", "dsst", "n-back").map { "$prefix-$it" } } + listOf("practice-feedback-dsst", "practice-feedback-n-back")
        val feedbackOnly = InstrumentationRegistry.getArguments().getString("batteryFeedbackOnly") == "true"
        val destinations = if (feedbackOnly) listOf("practice-feedback-dsst", "practice-feedback-n-back") else
            listOf("identification", "shift", "home", "tests", "questionnaires", "instructions", "pvt", "result", "history", "questionnaire", "assessment") + cognitiveScreens
        for (destination in destinations) {
            compose.runOnIdle { screen = destination }
            compose.waitForIdle()
            capture(destination)
            when (destination) {
                "identification" -> compose.onNodeWithText("Usar Alex").performScrollTo().assertIsDisplayed()
                "shift" -> {
                    val day = compose.onNodeWithText("Turno Día").fetchSemanticsNode().boundsInRoot
                    val night = compose.onNodeWithText("Turno Noche").fetchSemanticsNode().boundsInRoot
                    if (fontScale == 1f) {
                        assertEquals(day.top, night.top, 1f)
                        assertTrue("Shift cards should be side by side", day.right < night.left)
                    }
                    compose.onNodeWithText("Turno Noche").performScrollTo().assertIsDisplayed()
                }
                "home" -> {
                    compose.onNodeWithText("GUARDADO EN EL TELÉFONO").assertExists()
                    compose.onNodeWithText("Iniciar prueba de reacción").performScrollTo().assertIsDisplayed()
                }
                "tests", "questionnaires" -> {
                    compose.onNodeWithTag(if (destination == "tests") "tab-TESTS" else "tab-QUESTIONNAIRES").assertIsSelected()
                    compose.onNodeWithText(if (destination == "tests") "Recuerda el anterior" else "Fatiga retrospectiva · Chalder").performScrollTo().assertIsDisplayed()
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
                "questionnaire" -> { compose.onNodeWithText("PENDIENTE DE HABILITACIÓN").assertExists(); assertFooterStaysVisible("Volver al catálogo") }
                "assessment" -> assertFooterStaysVisible("Volver al inicio")
                "practice-dsst", "practice-feedback-dsst" -> {
                    compose.onNodeWithTag("digit-1").assertIsDisplayed(); compose.onNodeWithTag("digit-6").assertIsDisplayed()
                    if (destination.contains("feedback")) compose.onNodeWithText("Respuesta anterior: La respuesta era el dígito 6").assertIsDisplayed()
                }
                "practice-n-back", "practice-feedback-n-back" -> {
                    compose.onNodeWithText("Igual").assertIsDisplayed(); compose.onNodeWithText("Diferente").assertIsDisplayed()
                    if (destination.contains("feedback")) compose.onNodeWithText("Respuesta anterior: La respuesta era Diferente").assertIsDisplayed()
                }
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
                        destination.endsWith("dsst") -> compose.onNodeWithTag("digit-6")
                        destination.endsWith("n-back") -> compose.onNodeWithText("Diferente")
                        else -> compose.onNodeWithTag("visual-target").assertHeightIsAtLeast(48.dp)
                    }
                    val bounds = action.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val viewport = compose.onNodeWithTag("visual-root").fetchSemanticsNode().boundsInRoot
                    assertTrue("Response fits viewport", bounds.bottom <= viewport.bottom + 1f)
                }
                in cognitiveScreens.filter { it.startsWith("cognitive-result-") } ->
                    assertFooterStaysVisible("Volver a pruebas")
            }
            if (destination in listOf("identification", "shift", "home", "tests", "questionnaires", "instructions", "result")) capture("$destination-action")
        }
    }

    private fun assertFooterStaysVisible(label: String) {
        val action = compose.onNodeWithText(label).assertIsDisplayed()
        val viewport = compose.onNodeWithTag("visual-root").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onAllNodesWithTag("tab-HOME").fetchSemanticsNodes()
        val bottom = navigation.firstOrNull()?.boundsInRoot?.top ?: viewport.bottom
        val actionBottom = action.fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Primary action must fit above navigation", actionBottom <= bottom + 1f)
        assertTrue("Primary action should stay near the bottom", actionBottom >= bottom - 140 * compose.density.density)
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
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/samimine-navigation-20261020").use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).readBytes()
        }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/samimine-navigation-20261020/$filename").use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).readBytes()
        }
    }
}
