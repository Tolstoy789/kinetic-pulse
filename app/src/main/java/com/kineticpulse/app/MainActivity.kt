package com.kineticpulse.app

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.kineticpulse.app.ai.GemmaLocalManager
import com.kineticpulse.app.ui.theme.KineticBlack
import com.kineticpulse.app.ui.theme.KineticPulseTheme
import com.kineticpulse.app.ui.workout.WorkoutScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var gemma: GemmaLocalManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(KineticBlack.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(KineticBlack.toArgb()),
        )
        super.onCreate(savedInstanceState)
        requestMaxRefreshRate()
        holdSplashUntilPulseDrawn()
        splashScreen.setOnExitAnimationListener { splash ->
            splash.animate()
                .alpha(0f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(SPLASH_EXIT_MS)
                .withEndAction { splash.remove() }
                .start()
        }
        setContent {
            KineticPulseTheme { WorkoutScreen() }
        }
    }

    override fun onStart() {
        super.onStart()
        gemma.onAppForegrounded()
    }

    /**
     * Mantiene el splash hasta que la línea de pulso termina de dibujarse. Sin esto, en un
     * arranque en caliente el primer frame llega antes y la animación se corta a medias.
     */
    private fun holdSplashUntilPulseDrawn() {
        val start = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (SystemClock.uptimeMillis() - start < SPLASH_MIN_MS) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
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

    private companion object {
        const val SPLASH_MIN_MS = 900L
        const val SPLASH_EXIT_MS = 250L
    }
}
