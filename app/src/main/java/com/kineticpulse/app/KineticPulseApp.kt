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
        // Desde Android 14 solo llegan UI_HIDDEN y BACKGROUND: ambos significan "ya no se ve",
        // no presión de memoria. El gestor decide cuándo soltar el modelo (1–5 GB).
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) gemma.onAppBackgrounded()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        gemma.onMemoryPressure()
    }
}
