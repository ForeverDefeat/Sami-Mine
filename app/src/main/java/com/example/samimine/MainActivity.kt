package com.example.samimine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.samimine.app.*

class MainActivity : ComponentActivity() {
    private lateinit var model: SamiMineViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SamiMineApplication).container
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SamiMineViewModel(container.repository, container.useCases, container.clock, container.repository) as T
        })[SamiMineViewModel::class.java]
        if (savedInstanceState != null) {
            model.interrupt("La pantalla se recreó durante la prueba")
            model.interruptCognitive("La pantalla se recreó durante la prueba")
        }
        setContent { SamiMineApp(model) }
    }
    override fun onStart() {
        super.onStart()
        model.onForeground()
    }
    override fun onStop() {
        model.onBackground()
        super.onStop()
    }
}
