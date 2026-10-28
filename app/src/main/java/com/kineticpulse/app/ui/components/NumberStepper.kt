package com.kineticpulse.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kineticpulse.app.R
import java.util.Locale

/**
 * Input numérico grande, pensado para usar con una mano y sudando: botones ± de 48 dp y
 * teclado numérico. Muestra enteros sin decimales y acepta coma o punto.
 */
@Composable
fun NumberStepper(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    range: ClosedFloatingPointRange<Double> = 0.0..999.0,
    decimals: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    fun format(v: Double) = if (decimals && v % 1.0 != 0.0) String.format(Locale.ROOT, "%.1f", v) else v.toLong().toString()

    var text by remember { mutableStateOf(format(value)) }
    LaunchedEffect(value) {
        if (text.replace(',', '.').toDoubleOrNull() != value) text = format(value)
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton("−", stringResource(R.string.content_desc_decrease, label), accent) {
                onValueChange((value - step).coerceIn(range))
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val cleaned = raw.filter { it.isDigit() || (decimals && (it == '.' || it == ',')) }.take(6)
                    text = cleaned
                    cleaned.replace(',', '.').toDoubleOrNull()?.let { onValueChange(it.coerceIn(range)) }
                },
                textStyle = MaterialTheme.typography.displayMedium.copy(color = accent, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.widthIn(min = 72.dp, max = 120.dp),
            )
            StepButton("+", stringResource(R.string.content_desc_increase, label), accent) {
                onValueChange((value + step).coerceIn(range))
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, accent: Color, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = accent)
    }
}
