package com.kineticpulse.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import com.kineticpulse.app.R
import java.util.Locale

/**
 * Número grande con botones − y + de 64 dp a los lados, pensado para usar con una mano y
 * sudando. El número también se puede tocar y escribir. Acepta coma o punto.
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
    unit: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    fun format(v: Double) = if (decimals && v % 1.0 != 0.0) String.format(Locale.ROOT, "%.1f", v) else v.toLong().toString()

    var text by remember { mutableStateOf(format(value)) }
    LaunchedEffect(value) {
        if (text.replace(',', '.').toDoubleOrNull() != value) text = format(value)
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepButton("−", stringResource(R.string.content_desc_decrease, label), accent) {
                onValueChange((value - step).coerceIn(range))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                BasicTextField(
                    value = text,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isDigit() || (decimals && (it == '.' || it == ',')) }.take(6)
                        text = cleaned
                        cleaned.replace(',', '.').toDoubleOrNull()?.let { onValueChange(it.coerceIn(range)) }
                    },
                    textStyle = MaterialTheme.typography.displayLarge.copy(color = accent, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (unit != null) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StepButton("+", stringResource(R.string.content_desc_increase, label), accent) {
                onValueChange((value + step).coerceIn(range))
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, accent: Color, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = accent,
        ),
        modifier = Modifier.size(64.dp).semantics { contentDescription = description },
    ) {
        Text(symbol, fontSize = 32.sp, color = accent)
    }
}
