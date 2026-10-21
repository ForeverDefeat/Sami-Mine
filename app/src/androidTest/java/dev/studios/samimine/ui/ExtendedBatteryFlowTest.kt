package dev.studios.samimine.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.studios.samimine.MainActivity
import org.junit.Rule
import org.junit.Test

class ExtendedBatteryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun home() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Usar Alex").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Usar Alex").performScrollTo().performClick()
        compose.onNodeWithText("Turno Día").performScrollTo().performClick()
    }
    private fun practice(name: String, responseTag: String? = null) {
        home()
        compose.onNodeWithTag("tab-TESTS").performClick()
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.onNodeWithText("Comenzar práctica").performClick()
        repeat(6) { index ->
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Práctica ${index + 1}/6").fetchSemanticsNodes().isNotEmpty() }
            val response = if (responseTag != null) compose.onNodeWithTag(responseTag) else compose.onNodeWithText("Igual")
            compose.waitUntil(5_000) { runCatching { response.assertIsEnabled() }.isSuccess }
            response.performClick()
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Listo para comenzar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Comenzar prueba").performClick()
    }

    @Test fun associationStopsAfterMinuteWithoutInventingResponses() {
        practice("Símbolos y dígitos", "digit-1")
        compose.waitUntil(70_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("metric-details").performScrollTo().performClick()
        compose.onNodeWithTag("metric-attempted").performScrollTo().assert(hasAnyDescendant(hasText("0")))
        compose.onNodeWithTag("metric-correctPerMinute").performScrollTo().assert(hasAnyDescendant(hasText("0 / min")))
        compose.onNodeWithText("Guardado en este teléfono").assertExists()
    }

    @Test fun memoryCompletesTwentyFourComparisonsWithoutInventingResponses() {
        practice("Recuerda el anterior")
        compose.waitUntil(95_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("metric-details").performScrollTo().performClick()
        compose.onNodeWithTag("metric-omissions").performScrollTo().assert(hasAnyDescendant(hasText("24")))
        compose.onNodeWithText("experimental-1back-v1", substring = true).assertExists()
    }

    @Test fun pendingScalesExplainAvailabilityAndBriefCannotStart() {
        home()
        compose.onNodeWithText("Iniciar recorrido breve").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("tab-QUESTIONNAIRES").performClick()
        compose.onNodeWithText("Somnolencia actual · KSS").performScrollTo().performClick()
        compose.onNodeWithText("PENDIENTE DE HABILITACIÓN").assertExists()
        compose.onNodeWithText("Guardar respuestas").assertDoesNotExist()
        compose.onNodeWithText("Volver al catálogo").performClick()
        compose.onNodeWithTag("tab-QUESTIONNAIRES").assertIsSelected()
        compose.onNodeWithTag("tab-HOME").performClick()
        compose.onNodeWithText("Iniciar prueba de reacción").performScrollTo().assertIsEnabled()
    }
}
