package com.kineticpulse.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Temporizador isométrico sincronizado con el vsync (`withFrameNanos`): el progreso se
 * actualiza en cada frame del panel (120 Hz) sin depender de `delay`, que deriva.
 */
@Stable
class IsometricTimerState(targetSeconds: Int) {
    var targetSeconds by mutableStateOf(targetSeconds)
    var isRunning by mutableStateOf(false)
        private set
    var elapsedSeconds by mutableFloatStateOf(0f)
        internal set

    val progress: Float get() = if (targetSeconds <= 0) 0f else elapsedSeconds / targetSeconds

    fun start() {
        elapsedSeconds = 0f
        isRunning = true
    }

    fun stop() {
        isRunning = false
    }
}

@Composable
fun rememberIsometricTimer(targetSeconds: Int, onComplete: (heldSeconds: Int) -> Unit): IsometricTimerState {
    val state = remember { IsometricTimerState(targetSeconds) }
    state.targetSeconds = targetSeconds
    val latestOnComplete by rememberUpdatedState(onComplete)

    LaunchedEffect(state.isRunning) {
        if (!state.isRunning) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (state.isRunning) {
            val now = withFrameNanos { it }
            state.elapsedSeconds = (now - start) / 1_000_000_000f
            if (state.elapsedSeconds >= state.targetSeconds) {
                state.stop()
                latestOnComplete(state.targetSeconds)
            }
        }
    }
    return state
}
