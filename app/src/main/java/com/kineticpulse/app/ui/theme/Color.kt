package com.kineticpulse.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Paleta Kinetic Dark ─────────────────────────────────────────────
// Fondo casi negro: en Dynamic AMOLED 2X los píxeles se apagan casi del todo,
// con un matiz azulado que evita el "black smearing" al hacer scroll a 120 Hz.
val KineticBlack = Color(0xFF0D0D11)
val KineticSurface = Color(0xFF15151C)
val KineticSurfaceHigh = Color(0xFF1E1E28)
val KineticOutline = Color(0xFF2E2E3A)

val NeonVolt = Color(0xFFCCFF00)
val NeonVoltDim = Color(0xFF7A9900)
val NeonCyan = Color(0xFF00F0FF)
val NeonCyanDim = Color(0xFF00808A)
val NeonMagenta = Color(0xFFFF2E88)
val WarningAmber = Color(0xFFFFB800)

val TextPrimary = Color(0xFFF2F2F7)
val TextSecondary = Color(0xFF9A9AAE)
val TextOnNeon = Color(0xFF0D0D11)

/** Tokens semánticos extra que Material 3 no cubre (glows, estados de fatiga). */
@Immutable
data class KineticColors(
    val glowPrimary: Color = NeonVolt,
    val glowSecondary: Color = NeonCyan,
    val track: Color = KineticOutline,
    val fatigueFresh: Color = NeonCyan,
    val fatigueProductive: Color = NeonVolt,
    val fatigueHigh: Color = WarningAmber,
    val fatigueOverreaching: Color = NeonMagenta,
)

val LocalKineticColors = staticCompositionLocalOf { KineticColors() }
