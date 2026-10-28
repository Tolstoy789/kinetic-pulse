package com.kineticpulse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.kineticpulse.app.ui.theme.KineticBlack
import com.kineticpulse.app.ui.theme.KineticPulseTheme
import com.kineticpulse.app.ui.workout.WorkoutScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(KineticBlack.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(KineticBlack.toArgb()),
        )
        super.onCreate(savedInstanceState)
        requestMaxRefreshRate()
        setContent {
            KineticPulseTheme { WorkoutScreen() }
        }
    }

    /**
     * Pide el modo de mayor tasa de refresco (120 Hz en el S25 Ultra). One UI usa refresco
     * adaptativo y puede bajar a 60 Hz con contenido estático; esto lo evita en esta ventana.
     */
    private fun requestMaxRefreshRate() {
        val current = display?.mode ?: return
        val best = display?.supportedModes
            ?.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            ?.maxByOrNull { it.refreshRate }
            ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }
}
