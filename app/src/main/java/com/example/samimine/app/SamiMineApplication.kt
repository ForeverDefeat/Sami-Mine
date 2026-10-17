package com.example.samimine.app

import android.app.Application
import com.example.samimine.app.di.AppContainer

class SamiMineApplication : Application() {
    val container by lazy { AppContainer(this) }
}
