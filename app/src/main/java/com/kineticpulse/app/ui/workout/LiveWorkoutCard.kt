package com.kineticpulse.app.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kineticpulse.app.R
import com.kineticpulse.app.ui.components.GlowProgressBar
import com.kineticpulse.app.ui.components.NumberStepper
import com.kineticpulse.app.ui.components.rememberIsometricTimer
import com.kineticpulse.app.ui.theme.KineticMotion
import com.kineticpulse.app.ui.theme.KineticPulseTheme
import com.kineticpulse.app.ui.theme.KineticTheme
import com.kineticpulse.core.model.ExerciseModality
import kotlin.math.roundToInt

/** Estado inmutable de la tarjeta. La lógica vive en el ViewModel (state hoisting). */
data class LiveSetDraft(
    val exercise: String = "press banca",
    val modality: ExerciseModality = ExerciseModality.WEIGHTED,
    val weightKg: Double = 60.0,
    val reps: Int = 8,
    val holdSeconds: Int = 20,
    val rpe: Double = 8.0,
    val setNumber: Int = 1,
)

/**
 * Registro activo del set en curso.
 *  - Gimnasio / peso corporal: steppers de kilos y reps.
 *  - Isometría: objetivo en segundos + temporizador con barra luminosa a 120 Hz.
 */
@Composable
fun LiveWorkoutCard(
    draft: LiveSetDraft,
    onDraftChange: (LiveSetDraft) -> Unit,
    onLogSet: (LiveSetDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KineticTheme.colors
    val isIsometric = draft.modality == ExerciseModality.ISOMETRIC
    val accent by animateColorAsState(
        targetValue = if (isIsometric) colors.glowSecondary else colors.glowPrimary,
        animationSpec = KineticMotion.smooth(),
        label = "accent",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header(draft, accent)

            AnimatedContent(
                targetState = isIsometric,
                transitionSpec = { fadeIn(KineticMotion.smooth()) togetherWith fadeOut(KineticMotion.smooth()) },
                label = "modality",
            ) { iso ->
                if (iso) {
                    IsometricSection(draft, onDraftChange, onLogSet)
                } else {
                    DynamicSection(draft, onDraftChange, accent)
                }
            }

            RpeSlider(draft.rpe, onRpeChange = { onDraftChange(draft.copy(rpe = it)) })

            if (!isIsometric) {
                PressableButton(
                    text = stringResource(R.string.action_log_set),
                    onClick = { onLogSet(draft) },
                )
            }
        }
    }
}

@Composable
private fun Header(draft: LiveSetDraft, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                draft.modality.name,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Text(draft.exercise.uppercase(), style = MaterialTheme.typography.headlineMedium)
        }
        Text("SET ${draft.setNumber}", style = MaterialTheme.typography.labelLarge, color = accent)
    }
}

@Composable
private fun DynamicSection(
    draft: LiveSetDraft,
    onDraftChange: (LiveSetDraft) -> Unit,
    accent: Color,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        NumberStepper(
            label = stringResource(R.string.label_weight),
            value = draft.weightKg,
            onValueChange = { onDraftChange(draft.copy(weightKg = it)) },
            step = 2.5,
            range = 0.0..500.0,
            decimals = true,
            accent = accent,
        )
        NumberStepper(
            label = stringResource(R.string.label_reps),
            value = draft.reps.toDouble(),
            onValueChange = { onDraftChange(draft.copy(reps = it.roundToInt())) },
            range = 1.0..100.0,
            accent = accent,
        )
    }
}

@Composable
private fun IsometricSection(
    draft: LiveSetDraft,
    onDraftChange: (LiveSetDraft) -> Unit,
    onLogSet: (LiveSetDraft) -> Unit,
) {
    val timer = rememberIsometricTimer(draft.holdSeconds) { held ->
        onLogSet(draft.copy(holdSeconds = held))
    }
    val colors = KineticTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (timer.isRunning) {
            Text(
                text = stringResource(R.string.hold_seconds, timer.elapsedSeconds.toInt()),
                style = MaterialTheme.typography.displayLarge,
                color = colors.glowSecondary,
            )
        } else {
            NumberStepper(
                label = stringResource(R.string.label_hold),
                value = draft.holdSeconds.toDouble(),
                onValueChange = { onDraftChange(draft.copy(holdSeconds = it.roundToInt())) },
                step = 5.0,
                range = 1.0..300.0,
                accent = colors.glowSecondary,
            )
        }

        GlowProgressBar(progress = { timer.progress })

        PressableButton(
            text = stringResource(if (timer.isRunning) R.string.action_stop_hold else R.string.action_start_hold),
            onClick = {
                if (timer.isRunning) {
                    val held = timer.elapsedSeconds.toInt()
                    timer.stop()
                    if (held > 0) onLogSet(draft.copy(holdSeconds = held))
                } else {
                    timer.start()
                }
            },
            container = if (timer.isRunning) MaterialTheme.colorScheme.tertiary else colors.glowSecondary,
        )
    }
}

@Composable
private fun RpeSlider(rpe: Double, onRpeChange: (Double) -> Unit) {
    val colors = KineticTheme.colors
    val tint = when {
        rpe >= 9.5 -> colors.fatigueOverreaching
        rpe >= 8.5 -> colors.fatigueHigh
        else -> colors.fatigueProductive
    }
    val animatedTint by animateColorAsState(tint, KineticMotion.smooth(), label = "rpeTint")

    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.label_rpe), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(rpe.toString(), style = MaterialTheme.typography.labelLarge, color = animatedTint)
        }
        Slider(
            value = rpe.toFloat(),
            onValueChange = { onRpeChange((it * 2).roundToInt() / 2.0) },
            valueRange = 5f..10f,
            steps = 9,
            colors = SliderDefaults.colors(thumbColor = animatedTint, activeTrackColor = animatedTint),
        )
    }
}

/** Botón con micro-escala de pulsación por spring: feedback táctil visual sin coste de layout. */
@Composable
private fun PressableButton(
    text: String,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.primary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, KineticMotion.snappy(), label = "press")

    Button(
        onClick = onClick,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D11)
@Composable
private fun LiveWorkoutCardWeightedPreview() {
    KineticPulseTheme { LiveWorkoutCard(LiveSetDraft(), {}, {}, Modifier.padding(16.dp)) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D11)
@Composable
private fun LiveWorkoutCardIsometricPreview() {
    KineticPulseTheme {
        LiveWorkoutCard(
            LiveSetDraft(exercise = "planche lean", modality = ExerciseModality.ISOMETRIC, holdSeconds = 15),
            {},
            {},
            Modifier.padding(16.dp),
        )
    }
}
