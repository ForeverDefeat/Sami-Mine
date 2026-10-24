package dev.studios.samimine.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import dev.studios.samimine.MainActivity
import org.junit.Rule
import org.junit.Test

class WorkerNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun begin(shift: String = "Día") {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Usar Alex").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Usar Alex").performScrollTo().performClick()
        compose.onNodeWithTag("tab-HOME").assertDoesNotExist()
        compose.onNodeWithText("Turno $shift").performScrollTo().performClick()
        compose.onNodeWithTag("tab-HOME").assertIsSelected()
        compose.onNodeWithText("Volver").assertDoesNotExist()
    }

    @Test fun fiveTabsInstructionsQuestionnairesRecommendationsAndAndroidBack() {
        begin()
        compose.onNodeWithTag("tab-TESTS").performClick().assertIsSelected()
        compose.onNodeWithText("Flecha central").performScrollTo().performClick()
        compose.onNodeWithTag("tab-TESTS").assertIsSelected()
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithText("Flecha central").assertExists()
        compose.onNodeWithTag("tab-QUESTIONNAIRES").performClick()
        compose.onNodeWithText("Esfuerzo físico · Borg 6–20").assertDoesNotExist()
        compose.onNodeWithText("Somnolencia actual · KSS").performScrollTo().performClick()
        compose.onNodeWithTag("tab-QUESTIONNAIRES").assertDoesNotExist()
        compose.onNodeWithText("PENDIENTE DE HABILITACIÓN").assertExists()
        compose.onNodeWithText("Volver al catálogo").performClick()
        compose.onNodeWithTag("tab-QUESTIONNAIRES").assertIsSelected()
        compose.onNodeWithTag("tab-RECOMMENDATIONS").performClick().assertIsSelected()
        compose.onNodeWithText("Antes de las pruebas").assertExists()
        compose.onNodeWithText("Bienestar durante el turno").performScrollTo().assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("tab-HOME").assertIsSelected()
        compose.onNodeWithTag("tab-HISTORY").performClick().assertIsSelected()
        compose.onNodeWithText("Mi historial").assertExists()
        pressBack()
        compose.onNodeWithTag("tab-HOME").assertIsSelected()
        pressBack()
        compose.onNodeWithText("Selecciona tu turno").assertExists()
    }

    @Test fun profileMenuChangesShiftAndClearsWorkerContext() {
        begin("Noche")
        compose.onNodeWithText("Perfil y turno").performClick()
        compose.onNodeWithText("Cambiar turno").performClick()
        compose.onNodeWithText("Turno Día").performScrollTo().performClick()
        compose.onNodeWithTag("tab-HOME").assertIsSelected()
        compose.onNodeWithText("Perfil y turno").performClick()
        compose.onNodeWithText("Cambiar perfil").performClick()
        compose.onNodeWithText("Usar Alex").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tab-HOME").assertDoesNotExist()
    }

    @Test fun tabSwitchPreservesScrollAndNewShiftResetsIt() {
        begin()
        compose.onNodeWithText("Es una demostración, no una evaluación laboral.").performScrollTo()
        fun offset() = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val previous = offset()
        assertTrue("Home should scroll on the phone viewport", previous > 0f)
        compose.onNodeWithTag("tab-TESTS").performClick()
        compose.onNodeWithTag("tab-HOME").performClick()
        assertEquals(previous, offset(), 1f)
        compose.onNodeWithText("Perfil y turno").performClick()
        compose.onNodeWithText("Cambiar turno").performClick()
        compose.onNodeWithText("Turno Noche").performScrollTo().performClick()
        assertEquals(0f, offset(), 1f)
    }

    @Test fun pvtRetryKeepsTestsOriginAndHistoricalDetailReturnsToHistory() {
        begin()
        compose.onNodeWithTag("tab-TESTS").performClick()
        compose.onNodeWithText("Prueba de reacción").performScrollTo().performClick()
        compose.onNodeWithText("Comenzar prueba").performClick()
        compose.onNodeWithText("Abandonar prueba").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración interrumpida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Repetir actividad").performClick()
        compose.onNodeWithTag("tab-TESTS").assertIsSelected()
        compose.onNodeWithText("Comenzar prueba").performClick()
        compose.onNodeWithText("Abandonar prueba").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración interrumpida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Volver a pruebas").performClick()
        compose.onNodeWithTag("tab-HISTORY").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Ver detalle").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Ver detalle").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Repetir actividad").assertDoesNotExist()
        compose.onNodeWithText("Volver al historial").performClick()
        compose.onNodeWithText("Mi historial").assertExists()
    }

    @Test fun practiceHidesNavigationAndAbandonedResultRestoresOrigin() {
        begin()
        compose.onNodeWithTag("tab-TESTS").performClick()
        compose.onNodeWithText("Flecha central").performScrollTo().performClick()
        compose.onNodeWithText("Comenzar práctica").performClick()
        compose.onNodeWithTag("tab-TESTS").assertDoesNotExist()
        compose.onNodeWithText("Abandonar prueba").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Demostración interrumpida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-TESTS").assertIsSelected()
        compose.onNodeWithText("Volver a pruebas").performClick()
        compose.onNodeWithText("Flecha central").assertExists()
    }
}
