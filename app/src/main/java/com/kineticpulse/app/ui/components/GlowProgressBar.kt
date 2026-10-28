package com.kineticpulse.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kineticpulse.app.ui.theme.KineticTheme

/**
 * Barra de progreso con halo neón. El glow se dibuja con capas de alpha decreciente en
 * lugar de `Modifier.blur`, que fuerza una capa offscreen por frame y cuesta a 120 Hz.
 * Leer [progress] como lambda difiere la lectura a la fase de dibujo: sin recomposición.
 */
@Composable
fun GlowProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    startColor: Color = KineticTheme.colors.glowSecondary,
    endColor: Color = KineticTheme.colors.glowPrimary,
    trackColor: Color = KineticTheme.colors.track,
) {
    Canvas(modifier.fillMaxWidth().height(height * 3)) {
        val barHeight = height.toPx()
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barHeight / 2f)

        drawRoundRect(trackColor, Offset(0f, top), Size(size.width, barHeight), radius)

        val fraction = progress().coerceIn(0f, 1f)
        if (fraction <= 0f) return@Canvas
        val width = size.width * fraction
        val brush = Brush.horizontalGradient(listOf(startColor, endColor), endX = size.width)

        for (layer in 3 downTo 1) {
            val spread = barHeight * 0.45f * layer
            drawRoundRect(
                brush = brush,
                topLeft = Offset(-spread / 2f, top - spread / 2f),
                size = Size(width + spread, barHeight + spread),
                cornerRadius = CornerRadius((barHeight + spread) / 2f),
                alpha = 0.08f * (4 - layer),
            )
        }
        drawRoundRect(brush, Offset(0f, top), Size(width, barHeight), radius)
    }
}
