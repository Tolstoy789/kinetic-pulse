package com.kineticpulse.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/** Siempre oscuro: la app está pensada para AMOLED y no ofrece tema claro. */
private val KineticColorScheme = darkColorScheme(
    primary = NeonVolt,
    onPrimary = TextOnNeon,
    primaryContainer = NeonVoltDim,
    onPrimaryContainer = TextOnNeon,
    secondary = NeonCyan,
    onSecondary = TextOnNeon,
    secondaryContainer = NeonCyanDim,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonMagenta,
    onTertiary = TextOnNeon,
    background = KineticBlack,
    onBackground = TextPrimary,
    surface = KineticBlack,
    onSurface = TextPrimary,
    surfaceVariant = KineticSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = KineticSurface,
    surfaceContainerHigh = KineticSurfaceHigh,
    surfaceContainerHighest = KineticSurfaceHigh,
    outline = KineticOutline,
    outlineVariant = KineticOutline,
    error = NeonMagenta,
    onError = TextOnNeon,
)

private val KineticShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
)

/** Specs de animación compartidas: rápidas y con poco rebote para que se sientan nativas a 120 Hz. */
object KineticMotion {
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> smooth() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
}

@Composable
fun KineticPulseTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKineticColors provides KineticColors()) {
        MaterialTheme(
            colorScheme = KineticColorScheme,
            typography = KineticTypography,
            shapes = KineticShapes,
            content = content,
        )
    }
}

/** Acceso a los tokens extra: `KineticTheme.colors.glowPrimary`. */
object KineticTheme {
    val colors: KineticColors
        @Composable get() = LocalKineticColors.current
}
