package dev.studios.samimine.app

import android.app.Application
import dev.studios.samimine.app.di.AppContainer

/** Mantiene una composición de dependencias por proceso, creada al solicitar [container]. */
class SamiMineApplication : Application() {
    val container by lazy { AppContainer(this) }
}
