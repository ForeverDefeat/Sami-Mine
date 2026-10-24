package dev.studios.samimine.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.screens.cognitive.GuidedBatteryScreen
import dev.studios.samimine.ui.theme.SamiMineTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class GuidedBatteryVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fiveNavigationDestinationsRemainVisibleWithLargeText() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                SamiMineTheme {
                    Box(Modifier.width(360.dp)) {
                        dev.studios.samimine.app.WorkerNavigation(dev.studios.samimine.app.navigation.WorkerTab.RECOMMENDATIONS, true, {})
                    }
                }
            }
        }
        for (tab in dev.studios.samimine.app.navigation.WorkerTab.entries) {
            compose.onNodeWithTag("tab-${tab.name}").assertIsDisplayed().assertIsEnabled()
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "guided-visual").apply { mkdirs() }
        File(directory, "navigation-360-150.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun malformedRemoteSummaryDoesNotCrashTheWellbeingScreen() {
        compose.setContent {
            SamiMineTheme {
                dev.studios.samimine.ui.screens.access.RemoteBatterySummary(
                    "{\"definition\":{\"version\":\"invalid\"}}",
                    listOf("[{\"moduleId\":null,\"status\":null,\"attempts\":null}]"), true, {})
            }
        }
        compose.onNodeWithText("No se pudieron mostrar los pasos de este recorrido.", substring = true).assertExists()
    }
    @Test fun overviewActionsStayReachableAcrossWidthsAndFontScales() {
        var width by mutableIntStateOf(360)
        var fontScale by mutableFloatStateOf(1f)
        val modules = listOf("PVT", "FLANKER", "GO_NO_GO", "VISUAL_SEARCH", "DSST", "N_BACK")
        val protocols = BatteryCatalog.modules().filter { it.id in modules }.associate { it.id to it.protocolVersion!! }
        val definition = BatteryDefinition("default", "Batería cognitiva del piloto", 1, "demo", modules, protocols)
        val run = GuidedBatteryRun("visual", "a", Shift.DAY, 100, definition,
            steps = modules.mapIndexed { index, id -> if (index == 0) BatteryStep(id, BatteryStepStatus.COMPLETED,
                listOf(BatteryAttempt("PVT", "visual-fixture", "COMPLETED"))) else BatteryStep(id) }, status = BatteryRunStatus.PAUSED)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SamiMineTheme {
                    Box(Modifier.width(width.dp).fillMaxHeight()) {
                        GuidedBatteryScreen(run, definition, null, true, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "guided-visual").apply { mkdirs() }
        for (w in listOf(360, 390, 412)) for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle { width = w; fontScale = scale }
            compose.onNodeWithText("Continuar batería").performScrollTo().assertIsDisplayed().assertIsEnabled()
            File(directory, "battery-${w}-${(scale * 100).toInt()}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("Abandonar batería").performScrollTo().assertIsDisplayed()
        }
    }
}
