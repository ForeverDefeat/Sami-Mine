package dev.studios.samimine.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.studios.samimine.app.*
import dev.studios.samimine.application.usecase.EvaluationUseCases
import dev.studios.samimine.core.time.AndroidMonotonicClock
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.repository.RoomEvaluationRepository
import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Synthetic wording is deliberately restricted to this test APK. */
class GuidedBatteryFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: SamiMineViewModel
    private lateinit var db: SamiMineDatabase
    private var published = BatteryDefinition("default", "Batería de prueba", 1, "local-demo", listOf("PVT", "FLANKER"),
        mapOf("PVT" to "demo-pvt-v1", "FLANKER" to "demo-flanker-v2"))
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SamiMineDatabase::class.java).build()
        val repo = RoomEvaluationRepository(db)
        val definitions = InstrumentCatalog.definitions.map { definition ->
            val id = definition.id
            if (id == InstrumentId.KSS) InstrumentDefinition(id, "synthetic-fixture-v1", "es-PE", "Instrucción sintética de prueba", "Solo prueba",
                listOf(InstrumentQuestion("q", "Pregunta sintética", (1..9).map { AnswerOption("$it", "Opción sintética $it", it) })), true,
                "Permiso sintético de test", "Aprobación sintética de test")
            else definition
        }
        compose.runOnUiThread {
            model = SamiMineViewModel(repo, EvaluationUseCases(repo), AndroidMonotonicClock(), repo, instruments = definitions,
                batteryConfigLoader = { published to null })
        }
        compose.setContent { SamiMineApp(model, Worker("a", "Demo", "Área de prueba", "Demo")) }
        waitText("Turno Día")
        compose.onNodeWithText("Turno Día").performClick()
    }
    @After fun cleanup() {
        if (::model.isInitialized) compose.runOnUiThread { model.viewModelScope.cancel() }
        if (::db.isInitialized) db.close()
    }
    private fun waitText(text: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun briefOmissionAndPvtCompleteWithoutFabricatingQuestionnaireAnswer() {
        compose.onNodeWithText("Iniciar recorrido breve").performScrollTo().performClick()
        waitText("Comenzar primera actividad")
        compose.onNodeWithText("Comenzar primera actividad").performScrollTo().performClick()
        waitText("Omitir respuesta")
        compose.onNodeWithText("Omitir respuesta").performClick()
        waitText("Ver avance de la batería")
        compose.onNodeWithText("Ver avance de la batería").performClick()
        waitText("Continuar batería")
        compose.onNodeWithText("Continuar batería").performScrollTo().performClick()
        waitText("Comenzar prueba")
        compose.onNodeWithText("Comenzar prueba").performClick()
        repeat(3) { index ->
            val label = if (index == 2) "Ver resultado" else "Continuar prueba"
            waitText(label)
            compose.onNodeWithText(label).performClick()
        }
        compose.waitUntil(40_000) { model.state.value.destination == dev.studios.samimine.app.navigation.Destination.RESULT }
        compose.onNodeWithText("Ver avance de la batería").performClick()
        waitText("Completada")
        compose.runOnIdle {
            assertEquals(BatteryRunStatus.COMPLETED, model.state.value.guidedRun!!.status)
            assertEquals(BatteryStepStatus.OMITTED, model.state.value.guidedRun!!.steps[0].status)
        }
        compose.onNodeWithText("Respuesta omitida explícitamente").assertExists()
    }
    @Test fun interruptedPvtPausesAndCanContinueWithANewAttempt() {
        compose.onNodeWithText("Ver batería disponible").performScrollTo().performClick()
        waitText("Iniciar batería")
        compose.onNodeWithText("Iniciar batería").performScrollTo().performClick()
        waitText("Comenzar primera actividad")
        compose.onNodeWithText("Comenzar primera actividad").performScrollTo().performClick()
        waitText("Comenzar prueba")
        compose.onNodeWithText("Comenzar prueba").performClick()
        compose.waitUntil { model.state.value.evaluation != null }
        val original = model.state.value.evaluation!!.id
        compose.runOnUiThread { model.onBackground() }
        compose.waitUntil(10_000) { model.state.value.destination == dev.studios.samimine.app.navigation.Destination.HOME && !model.state.value.busy }
        compose.runOnUiThread { model.onForeground() }
        compose.onNodeWithText("Continuar batería").performScrollTo().performClick()
        waitText("Continuar batería")
        compose.onNodeWithText("Continuar batería").performScrollTo().performClick()
        waitText("Comenzar prueba")
        compose.onNodeWithText("Comenzar prueba").performClick()
        compose.waitUntil { model.state.value.evaluation?.let { it.id != original } == true && !model.state.value.busy }
        val run = model.state.value.guidedRun!!
        assertEquals(2, runBlocking { db.guided().get(run.id, "a")!!.toDomain().steps[0].attempts.size })
    }
    @Test fun customizedOrderLaunchesCognitivePracticeAndBackgroundPreservesItsAttempt() {
        compose.runOnUiThread {
            published = published.copy(version = 2, modules = listOf("FLANKER", "PVT"))
            model.refreshBatteryDefinition()
        }
        compose.waitUntil { model.state.value.batteryDefinition?.version == 2 }
        compose.onNodeWithText("Ver batería disponible").performScrollTo().performClick()
        compose.onNodeWithText("Iniciar batería").performScrollTo().performClick()
        waitText("Comenzar primera actividad")
        compose.onNodeWithText("Comenzar primera actividad").performScrollTo().performClick()
        waitText("Comenzar práctica")
        compose.onNodeWithText("Comenzar práctica").performClick()
        compose.waitUntil { model.state.value.cognitiveSession != null }
        compose.runOnUiThread { model.onBackground() }
        compose.waitUntil(10_000) { model.state.value.destination == dev.studios.samimine.app.navigation.Destination.HOME && !model.state.value.busy }
        val saved = runBlocking { db.guided().get(model.state.value.guidedRun!!.id, "a")!!.toDomain() }
        assertEquals("FLANKER", saved.steps[0].moduleId)
        assertEquals(BatteryStepStatus.INTERRUPTED, saved.steps[0].status)
        assertEquals(BatteryStepStatus.PENDING, saved.steps[1].status)
        assertEquals("COGNITIVE", saved.steps[0].attempts.single().kind)
    }
}
