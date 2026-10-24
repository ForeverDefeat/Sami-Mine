package dev.studios.samimine.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import dev.studios.samimine.app.*
import dev.studios.samimine.data.remote.BatteryDraft
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.screens.access.*
import dev.studios.samimine.ui.theme.SamiMineTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManagementScreensTest {
    @get:Rule val compose = createComposeRule()
    private val worker = UserProfile("worker", "Trabajador de prueba", "demo", UserRole.WORKER, true, "Operaciones", "Contratista", "well")
    private fun session(role: UserRole) = UserSession(UserProfile("manager", "Responsable de prueba", "demo", role, true))
    private val result = RemoteRun("PVT_r", "PVT", "COMPLETED", 1792792800000, """{"shift":"DAY","metrics":{"responses":3,"medianReactionMillis":222},"configuration":{"version":"demo-pvt-v1"}}""", "Prueba de reacción")
    private fun scrollTo(text: String) { compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text)) }

    @Test fun administratorNeverRendersMetricsOrResponseActions() {
        compose.setContent { SamiMineTheme { ManagementExperience(AccessState(session(UserRole.ADMIN), loading = false, selectedWorker = worker, runs = listOf(result)), ManagementActions(), update = {}) } }
        scrollTo("Prueba de reacción")
        compose.onNodeWithText("222").assertDoesNotExist()
        compose.onNodeWithText("Ver resultado y respuestas").assertDoesNotExist()
        compose.onNodeWithText("Editar perfil y asignación").assertExists()
        compose.onNodeWithTag("management-tab-batteries").assertExists()
    }
    @Test fun wellbeingHasNoAdministrationAndResponsesAreCollapsedUntilRequested() {
        compose.setContent { SamiMineTheme { ManagementExperience(AccessState(session(UserRole.WELLBEING), loading = false, selectedWorker = worker, detail = result,
            detailChunks = listOf("[{\"index\":1,\"actualResponse\":\"LEFT\",\"outcomeCode\":\"HIT\"}]")), ManagementActions(), update = {}) } }
        compose.onNodeWithTag("management-tab-batteries").assertDoesNotExist()
        compose.onNodeWithText("Editar perfil y asignación").assertDoesNotExist()
        scrollTo("Respuestas registradas")
        compose.onNodeWithText("Registro 1").assertDoesNotExist()
        compose.onNodeWithText("Respuestas registradas").performClick()
        scrollTo("Registro 1")
        compose.onNodeWithText("Respuesta registrada: Izquierda").assertExists()
        scrollTo("Datos técnicos")
        compose.onNodeWithText("Datos técnicos").performClick()
        scrollTo("Versión: demo-pvt-v1")
        compose.onNodeWithText("Versión: demo-pvt-v1").assertExists()
    }
    @Test fun dirtyProfileWarnsBeforeChangingSectionAndAndroidBack() {
        var feedbackCleared = false
        compose.setContent { SamiMineTheme { ManagementExperience(AccessState(session(UserRole.ADMIN), loading = false, selectedWorker = worker), ManagementActions(clearFeedback = { feedbackCleared = true }), update = {}) } }
        compose.onNodeWithText("Editar perfil y asignación").performScrollTo().performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("Nombre cambiado")
        compose.onNodeWithTag("management-tab-account").performClick()
        compose.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
        compose.onNodeWithText("Seguir editando").performClick()
        compose.onNodeWithText("Nombre").assertTextContains("Nombre cambiado")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
        compose.onNodeWithText("Descartar y salir").performClick()
        compose.onNodeWithText("Participación recibida").assertExists()
        assertTrue(feedbackCleared)
    }
    @Test fun failedSavePreservesFormAndSuccessfulRetryReturnsToTheWorker() {
        var state by mutableStateOf(AccessState(session(UserRole.ADMIN), loading = false, selectedWorker = worker))
        var attempts = 0
        val actions = ManagementActions(saveWorker = { source, draft ->
            attempts++
            state = if (attempts == 1) state.copy(error = "Sin conexión para guardar")
            else state.copy(error = null, notice = "Perfil y asignación guardados", profileSaveVersion = 1,
                selectedWorker = source.copy(name = draft.name, wellbeingUid = draft.assigned))
        })
        compose.setContent { SamiMineTheme { ManagementExperience(state, actions, update = {}) } }
        compose.onNodeWithText("Editar perfil y asignación").performScrollTo().performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("Nuevo nombre")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Guardar cambios").performClick()
        compose.waitUntil(5_000) { attempts == 1 }
        compose.onNodeWithText("Sin conexión para guardar").assertExists()
        compose.onNodeWithText("Nombre").assertTextContains("Nuevo nombre")
        compose.onNodeWithText("Nombre").performTextReplacement("Nombre corregido antes de reintentar")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Reintentar").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Participación recibida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nombre corregido antes de reintentar").assertExists()
        assertEquals(2, attempts)
    }
    @Test fun workerListKeepsItsPositionWhenReturningFromHistory() {
        val workers = (0..49).map { worker.copy(uid = "worker-$it", name = "Persona $it") }
        var state by mutableStateOf(AccessState(session(UserRole.WELLBEING), loading = false, workers = workers, moreWorkers = true))
        compose.setContent { SamiMineTheme { ManagementExperience(state, ManagementActions(selectWorker = { state = state.copy(selectedWorker = it) }, backToWorkers = { state = state.copy(selectedWorker = null) }), update = {}) } }
        compose.onNodeWithTag("management-workers").performScrollToNode(hasText("Persona 20 · Activo"))
        compose.onNodeWithText("Persona 20 · Activo").performClick()
        compose.onNodeWithText("Volver a trabajadores").performClick()
        compose.onNodeWithText("Persona 20 · Activo").assertIsDisplayed()
    }
    @Test fun losingAssignmentRemovesOpenDetailsAndItsNavigation() {
        var state by mutableStateOf(AccessState(session(UserRole.WELLBEING), loading = false, selectedWorker = worker, detail = result))
        compose.setContent { SamiMineTheme { ManagementExperience(state, ManagementActions(), update = {}) } }
        compose.runOnIdle { state = state.copy(selectedWorker = null, detail = null, detailChunks = emptyList(), workers = emptyList(), error = "La asignación cambió") }
        compose.onNodeWithText(worker.name).assertDoesNotExist()
        compose.onNodeWithText("Resultado de actividad").assertDoesNotExist()
        compose.onNodeWithText("Trabajadores asignados").assertExists()
    }
    @Test fun publishingNeedsConfirmationAndUnavailableModulesRemainBlocked() {
        val saved = BatteryDraft("Batería de prueba", listOf("PVT"), 1)
        var batteryState by mutableStateOf(BatteryEditorState(saved, saved))
        var publications = 0
        val actions = object : BatteryEditorActions by BatteryEditorActions.None { override fun publish() { publications++ } }
        compose.setContent { SamiMineTheme { ManagementExperience(AccessState(session(UserRole.ADMIN), loading = false), ManagementActions(), batteryState, actions, update = {}) } }
        compose.onNodeWithTag("management-tab-batteries").performClick()
        scrollTo("Publicar nueva versión")
        compose.onNodeWithText("Publicar nueva versión").performClick()
        assertEquals(0, publications)
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(0, publications)
        compose.onNodeWithText("Publicar nueva versión").performClick()
        compose.onNodeWithText("Publicar", substring = false).performClick()
        assertEquals(1, publications)
        compose.runOnIdle { val blocked = saved.copy(modules = listOf("KSS")); batteryState = BatteryEditorState(blocked, blocked) }
        scrollTo("Publicar nueva versión")
        compose.onNodeWithText("Publicar nueva versión").assertIsNotEnabled()
    }
    @Test fun batteryAttemptReturnsToItsParentInsteadOfLosingTheHistory() {
        val parent = RemoteRun("BATTERY_b", "BATTERY", "COMPLETED", 100, """{"definition":{"version":1}}""", "Batería de prueba")
        val chunks = listOf("""[{"moduleId":"PVT","status":"COMPLETED","attempts":[{"kind":"PVT","runId":"r","status":"COMPLETED"}]}]""")
        var state by mutableStateOf(AccessState(session(UserRole.WELLBEING), loading = false, selectedWorker = worker, detail = parent, detailChunks = chunks))
        compose.setContent { SamiMineTheme { ManagementExperience(state, ManagementActions(openAttempt = { state = state.copy(detail = result, detailChunks = emptyList(), detailParents = listOf(parent)) },
            closeResult = { state = state.copy(detail = parent, detailChunks = chunks, detailParents = emptyList()) }), update = {}) } }
        scrollTo("Ver resultado del intento 1")
        compose.onNodeWithText("Ver resultado del intento 1").performClick()
        compose.onNodeWithText("Volver al recorrido").performClick()
        compose.onNodeWithText("Batería de prueba").assertExists()
        assertEquals(parent.id, state.detail!!.id)
    }
    @Test fun temporaryDisconnectionKeepsTheAdministrativeDraftUntilReconnection() {
        var state by mutableStateOf(AccessState(session(UserRole.ADMIN), loading = false, selectedWorker = worker))
        compose.setContent { SamiMineTheme { ManagementExperience(state, ManagementActions(), update = {}) } }
        compose.onNodeWithText("Editar perfil y asignación").performScrollTo().performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("Nombre pendiente")
        Espresso.closeSoftKeyboard()
        compose.runOnIdle { state = state.copy(session = state.session!!.copy(offline = true), runs = emptyList(), detail = null, detailChunks = emptyList()) }
        compose.onNodeWithText("Sin conexión").assertExists()
        compose.onNodeWithText("Tus cambios sin guardar se conservan en esta pantalla.").assertExists()
        compose.runOnIdle { state = state.copy(session = state.session!!.copy(offline = false)) }
        compose.onNodeWithText("Nombre").assertTextContains("Nombre pendiente")
    }
    @Test fun batteryAcknowledgementRemainsVisibleWhenSavingAtTheBottom() {
        val original = BatteryDraft("Batería", listOf("PVT"), 1)
        var state by mutableStateOf(BatteryEditorState(original.copy(name = "Editada"), original))
        val actions = object : BatteryEditorActions by BatteryEditorActions.None {
            override fun save() { state = state.copy(baseline = state.draft, message = "Borrador guardado") }
        }
        compose.setContent { SamiMineTheme { BatteryManagementContent(state, actions, true) } }
        scrollTo("Guardar borrador")
        compose.onNodeWithText("Guardar borrador").performClick()
        compose.onAllNodesWithText("Borrador guardado").onFirst().assertIsDisplayed()
    }
}
