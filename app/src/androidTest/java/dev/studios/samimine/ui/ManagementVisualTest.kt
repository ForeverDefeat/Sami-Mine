package dev.studios.samimine.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.studios.samimine.app.*
import dev.studios.samimine.data.remote.BatteryDraft
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.screens.access.*
import dev.studios.samimine.ui.theme.SamiMineTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class ManagementVisualTest(private val width: Int, private val scale: Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp-font{1}")
        fun cases() = listOf(360, 390, 412).flatMap { width -> listOf(arrayOf<Any>(width, 1f), arrayOf<Any>(width, 1.5f)) }
    }
    @get:Rule val compose = createComposeRule()
    private val worker = UserProfile("worker", "María Quispe", "demo", UserRole.WORKER, true, "Operaciones · Mina sur", "Personal propio", "well")
    private val run = RemoteRun("PVT_demo", "PVT", "COMPLETED", 1792792800000,
        """{"shift":"DAY","metrics":{"medianReactionMillis":284,"responses":3,"omissions":0}}""", "Prueba de reacción")
    @Test fun bothRolesHaveReachableActionsAtEveryWidthAndFontScale() {
        var state by mutableStateOf(AccessState(loading = false))
        var fixture by mutableIntStateOf(0)
        val draft = BatteryDraft("Batería del turno", listOf("PVT", "FLANKER", "DSST"), 1)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                SamiMineTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.requiredWidth(width.dp).fillMaxHeight()) {
                        key(fixture) { ManagementExperience(state, ManagementActions(), BatteryEditorState(draft, draft), update = { Text("SamiMine · Versión de prueba") }) }
                    }
                } }
            }
        }
        fun capture(name: String) {
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "management-visual").apply { mkdirs() }
            File(directory, "$name-${width}-${(scale * 100).toInt()}.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (role in listOf(UserRole.WELLBEING, UserRole.ADMIN)) {
            val session = UserSession(UserProfile("${role.name}-fixture", if (role == UserRole.ADMIN) "Carlos Mendoza" else "Ana Torres", "demo", role, true))
            fun show(selected: Boolean = false, detail: Boolean = false) {
                compose.runOnIdle { state = AccessState(session, loading = false, workers = listOf(worker, worker.copy(uid = "b", name = "José Flores", area = "Mantenimiento")),
                    selectedWorker = if (selected) worker else null, runs = listOf(run), detail = if (detail) run else null); fixture++ }
                compose.waitForIdle()
            }
            show()
            compose.onNodeWithText("María Quispe · Activo").assertIsDisplayed()
            compose.onNodeWithTag("management-tab-account").assertIsDisplayed()
            capture("${role.name.lowercase()}-workers")
            show(selected = true)
            compose.onNodeWithText(worker.name).assertIsDisplayed()
            capture("${role.name.lowercase()}-history")
            if (role == UserRole.ADMIN) {
                compose.onNodeWithText("Editar perfil y asignación").performScrollTo().performClick()
                compose.onNodeWithText("Guardar cambios").assertIsDisplayed()
                capture("admin-profile")
                show()
                compose.onNodeWithTag("management-tab-batteries").performClick()
                capture("admin-batteries")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Publicar nueva versión"))
                compose.onNodeWithText("Publicar nueva versión").assertIsDisplayed().assertIsEnabled()
                capture("admin-publish")
            } else {
                show(selected = true, detail = true)
                capture("wellbeing-result")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Respuestas registradas"))
                compose.onNodeWithText("Respuestas registradas").assertIsDisplayed()
            }
            compose.onNodeWithTag("management-tab-account").performClick()
            compose.onNodeWithText("Cerrar sesión").performScrollTo().assertIsDisplayed().assertIsEnabled()
            capture("${role.name.lowercase()}-account")
        }
    }
}
