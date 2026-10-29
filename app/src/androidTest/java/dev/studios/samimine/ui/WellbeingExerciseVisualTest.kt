package dev.studios.samimine.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.studios.samimine.ui.screens.home.RecommendationsScreen
import dev.studios.samimine.ui.theme.SamiMineTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class WellbeingExerciseVisualTest(private val width: Int, private val scale: Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp-font{1}")
        fun cases() = listOf(360, 390, 412).flatMap { w -> listOf(arrayOf<Any>(w, 1f), arrayOf<Any>(w, 1.5f)) }
    }
    @get:Rule val compose = createComposeRule()

    @Test fun catalogDetailAndSessionHaveReachableControls() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                SamiMineTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.requiredWidth(width.dp).fillMaxHeight()) { RecommendationsScreen() }
                    }
                }
            }
        }
        fun capture(name: String) {
            val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "wellbeing-visual").apply { mkdirs() }
            File(folder, "$name-$width-${(scale * 100).toInt()}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        capture("catalog")
        compose.onNodeWithTag("exercise-muscle-relaxation").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-start").performScrollTo().assertIsDisplayed()
        capture("detail")
        compose.onNodeWithTag("exercise-start").performClick()
        capture("session")
        compose.onNodeWithText("Pausar").performScrollTo().performClick()
        compose.onNodeWithText("Continuar").assertIsDisplayed()
        compose.onNodeWithText("Terminar").performScrollTo().assertIsDisplayed()
        capture("paused-controls")
        compose.onNodeWithText("Terminar").performClick()
        listOf("gentle-breathing", "mindful-pause").forEach { id ->
            compose.onNodeWithTag("exercise-$id").performScrollTo().performClick()
            compose.onNodeWithTag("exercise-start").performScrollTo().performClick()
            compose.onNodeWithTag("exercise-character").performScrollTo().assertIsDisplayed()
            capture(id)
            compose.onNodeWithText("Terminar").performScrollTo().performClick()
        }
    }
}
