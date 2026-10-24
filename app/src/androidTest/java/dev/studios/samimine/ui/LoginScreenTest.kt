package dev.studios.samimine.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.studios.samimine.app.AccessState
import dev.studios.samimine.ui.screens.access.LoginScreen
import dev.studios.samimine.ui.theme.SamiMineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class LoginScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loginRequiresCredentialsAndSubmitsTrimmedEmailOnce() {
        var received: Pair<String, String>? = null
        compose.setContent { SamiMineTheme { LoginScreen(AccessState(loading = false)) { email, password -> received = email to password } } }
        compose.onNodeWithText("Ingresar").assertIsNotEnabled()
        compose.onNodeWithText("Correo electrónico").performTextInput(" worker@example.com ")
        compose.onNodeWithText("Contraseña").performTextInput("secret")
        compose.onNodeWithText("Ver", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Ocultar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Ingresar").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("worker@example.com" to "secret", received) }
        compose.onNodeWithText("Ingresar").assertIsNotEnabled()
    }

    @Test fun loadingAndServerErrorRemainVisible() {
        compose.setContent { SamiMineTheme { LoginScreen(AccessState(loading = true, error = "No se pudo ingresar")) { _, _ -> error("No debe enviar") } } }
        compose.onNodeWithText("Ingresando…").assertIsNotEnabled()
        compose.onNodeWithText("Correo electrónico").assertIsNotEnabled()
        compose.onNodeWithText("No se pudo ingresar").performScrollTo().assertIsDisplayed()
    }

    @Test fun debugUpdateExplainsWhyUpdaterIsUnavailable() {
        compose.setContent { SamiMineTheme { LoginScreen(AccessState(loading = false)) { _, _ -> } } }
        compose.onNodeWithText("Buscar actualizaciones", substring = true).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("esta compilación debug", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("esta compilación debug", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Buscar actualizaciones", substring = true).assertIsEnabled()
    }

    @Test fun accessActionsRemainReachableAcrossWidthsAndLargeFonts() {
        var width by mutableIntStateOf(360)
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SamiMineTheme { Box(Modifier.width(width.dp).fillMaxHeight()) { LoginScreen(AccessState(loading = false)) { _, _ -> } } }
            }
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "login-visual").apply { mkdirs() }
        for (w in listOf(360, 390, 412)) for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle { width = w; fontScale = scale }
            compose.onNodeWithText("Bienvenido").performScrollTo().assertIsDisplayed()
            File(directory, "login-${w}-${(scale * 100).toInt()}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("Contraseña").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Ingresar").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Buscar actualizaciones", substring = true).performScrollTo().assertIsDisplayed()
        }
    }
}
