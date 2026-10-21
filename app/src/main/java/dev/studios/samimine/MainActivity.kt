package dev.studios.samimine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.studios.samimine.app.*
import dev.studios.samimine.data.remote.PilotEnvironment
import dev.studios.samimine.ui.screens.access.AccessRoot

/**
 * Entrada Android de la aplicación y puente entre el ciclo de vida y las pruebas.
 * El ViewModel se conserva al recrear la Activity, pero una prueba activa se interrumpe
 * para no continuar una medición tras rotación, cambio de pantalla o segundo plano.
 */
class MainActivity : ComponentActivity() {
    private var model: SamiMineViewModel? = null
    private var access: AccessViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SamiMineApplication).container
        if (!PilotEnvironment.demoTests) {
            access = ViewModelProvider(this, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AccessViewModel(applicationContext, container) as T
            })[AccessViewModel::class.java]
            setContent { AccessRoot(access!!) { model = it } }
            return
        }
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SamiMineViewModel(container.repository, container.useCases, container.clock, container.repository) as T
        })[SamiMineViewModel::class.java]
        if (savedInstanceState != null) {
            model?.technicalFailure("La pantalla se recreó durante la prueba")
        }
        setContent { SamiMineApp(model!!) }
    }
    override fun onStart() {
        super.onStart()
        model?.onForeground()
        access?.refreshAccess()
    }
    override fun onStop() {
        model?.onBackground()
        super.onStop()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) model?.technicalFailure("La ventana perdió el foco durante la prueba")
    }
}
