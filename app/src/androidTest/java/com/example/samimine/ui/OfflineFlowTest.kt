package com.example.samimine.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.samimine.MainActivity
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun begin() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Usar Alex").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Usar Alex").performScrollTo().performClick()
        compose.onNodeWithText("Turno Día").performScrollTo().performClick()
        capture("home")
        compose.onNodeWithText("Iniciar prueba de reacción").performScrollTo().performClick()
        compose.onNodeWithText("Comenzar prueba").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("pvt-response").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "qa")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    @Test fun normalResponsesProduceMeanAndRemainReadable() {
        begin()
        for (index in 1..3) {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("$index / 3").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("TOCAR AHORA").fetchSemanticsNodes().isNotEmpty()
            }
            if (index == 1) capture("pvt")
            compose.onNodeWithTag("pvt-response").assertIsDisplayed().performClick()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sin datos").assertDoesNotExist()
        compose.onNodeWithText("Respuestas: 3\nToques antes de la señal: 0\nSeñales que no alcanzaste a tocar: 0").assertExists()
        capture("result")
    }

    @Test fun completeOfflineAndReadSavedResultInHistory() {
        begin()
        // Allow three real timeout windows to verify draw-bound onset and automatic omissions.
        compose.waitUntil(35_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sin datos").assertExists()
        compose.onNodeWithText("Respuestas: 0\nToques antes de la señal: 0\nSeñales que no alcanzaste a tocar: 3").assertExists()
        compose.onNodeWithText("Volver al inicio").assertIsDisplayed().performClick()
        compose.onNodeWithText("Ver mi historial").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Prueba de reacción · 3 de 3 señales").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Ver detalle").onFirst().performClick()
        compose.onNodeWithText("Prueba de reacción").assertExists()
        compose.onNodeWithText("Guardado en este teléfono").assertExists()
    }

    @Test fun activityRecreationInterruptsInsteadOfResuming() {
        begin()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración interrumpida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ACTIVIDAD INTERRUMPIDA").assertExists()
        compose.onNodeWithText("TIEMPO MEDIO DE RESPUESTA").assertDoesNotExist()
    }

    @Test fun backgroundInterruptsInsteadOfContinuing() {
        begin()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración interrumpida").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun abandonButtonPreservesInterruptedEvaluation() {
        begin()
        compose.onNodeWithText("Abandonar prueba").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración interrumpida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Repetir actividad").assertIsDisplayed()
    }
}
