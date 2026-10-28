package com.kineticpulse.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.kineticpulse.app.ai.GemmaLocalManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KineticPulseApp : Application() {

    @Inject lateinit var gemma: GemmaLocalManager

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // En segundo plano, el modelo (1–5 GB) es lo primero que debe salir de memoria.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) gemma.onMemoryPressure()
    }
}
