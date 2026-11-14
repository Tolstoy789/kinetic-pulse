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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

/**
 * La tarjeta de la serie actual. De arriba abajo, en el orden en que se usa:
 * ejercicio (toca para cambiar) → números → cómo se sintió → botón grande de guardar.
 */
@Composable
fun LiveWorkoutCard(
    draft: LiveSetDraft,
    onDraftChange: (LiveSetDraft) -> Unit,
    onLogSet: (LiveSetDraft) -> Unit,
    onChangeExercise: () -> Unit,
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ExerciseHeader(draft, accent, onChangeExercise)

            AnimatedContent(
                targetState = draft.modality,
                transitionSpec = { fadeIn(KineticMotion.smooth()) togetherWith fadeOut(KineticMotion.smooth()) },
                label = "modality",
            ) { modality ->
                when (modality) {
                    ExerciseModality.ISOMETRIC -> IsometricSection(draft, onDraftChange, onLogSet)
                    else -> DynamicSection(draft, onDraftChange, accent)
                }
            }

            EffortPicker(Effort.nearest(draft.rpe)) { onDraftChange(draft.copy(rpe = it.rpe)) }

            if (!isIsometric) {
                BigButton(
                    text = stringResource(R.string.action_save_set, draft.setNumber),
                    onClick = { onLogSet(draft) },
                )
            }
        }
    }
}

@Composable
private fun ExerciseHeader(draft: LiveSetDraft, accent: Color, onChangeExercise: () -> Unit) {
    OutlinedButton(
        onClick = onChangeExercise,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                draft.exercise.displayName(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(draft.modality.label()),
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
        }
        Text(
            stringResource(R.string.action_change),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DynamicSection(
    draft: LiveSetDraft,
    onDraftChange: (LiveSetDraft) -> Unit,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (draft.modality == ExerciseModality.WEIGHTED) {
            NumberStepper(
                label = stringResource(R.string.label_weight),
                value = draft.weightKg,
                onValueChange = { onDraftChange(draft.copy(weightKg = it)) },
                step = 2.5,
                range = 0.0..500.0,
                decimals = true,
                unit = "kg",
                accent = accent,
            )
        }
        NumberStepper(
            label = stringResource(R.string.label_reps),
            value = draft.reps.toDouble(),
            onValueChange = { onDraftChange(draft.copy(reps = it.roundToInt())) },
            range = 1.0..100.0,
            accent = accent,
        )
        PreviousHint(draft)
    }
}

@Composable
private fun PreviousHint(draft: LiveSetDraft) {
    val prev = draft.previous ?: return
    val text = when (draft.modality) {
        ExerciseModality.WEIGHTED -> stringResource(R.string.previous_weighted, formatKg(prev.weightKg ?: 0.0), prev.reps ?: 0)
        ExerciseModality.BODYWEIGHT -> stringResource(R.string.previous_reps, prev.reps ?: 0)
        ExerciseModality.ISOMETRIC -> stringResource(R.string.previous_hold, prev.holdSeconds ?: 0)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (timer.isRunning) {
            Text(
                text = stringResource(R.string.hold_seconds, timer.elapsedSeconds.toInt()),
                style = MaterialTheme.typography.displayLarge,
                color = colors.glowSecondary,
            )
            Text(
                stringResource(R.string.hold_goal, draft.holdSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlowProgressBar(progress = { timer.progress })
        } else {
            NumberStepper(
                label = stringResource(R.string.label_hold),
                value = draft.holdSeconds.toDouble(),
                onValueChange = { onDraftChange(draft.copy(holdSeconds = it.roundToInt())) },
                step = 5.0,
                range = 1.0..300.0,
                unit = "s",
                accent = colors.glowSecondary,
            )
            PreviousHint(draft)
        }

        BigButton(
            text = if (timer.isRunning) {
                stringResource(R.string.action_stop_hold)
            } else {
                stringResource(R.string.action_start_hold, draft.holdSeconds)
            },
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

/** Tres palabras en lugar de un deslizador de RPE. */
@Composable
private fun EffortPicker(selected: Effort, onSelect: (Effort) -> Unit) {
    val colors = KineticTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.label_effort),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Effort.entries.forEach { effort ->
                val tint = when (effort) {
                    Effort.EASY -> colors.fatigueFresh
                    Effort.HARD -> colors.fatigueProductive
                    Effort.MAX -> colors.fatigueOverreaching
                }
                val isSelected = effort == selected
                OutlinedButton(
                    onClick = { onSelect(effort) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) tint else MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) tint.copy(alpha = 0.14f) else Color.Transparent,
                        contentColor = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(
                        stringResource(effort.label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Botón principal: 72 dp de alto y una micro-escala al pulsar como respuesta táctil. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, KineticMotion.snappy(), label = "press")

    Button(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onPrimary),
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D11)
@Composable
private fun LiveWorkoutCardWeightedPreview() {
    KineticPulseTheme {
        LiveWorkoutCard(
            LiveSetDraft(previous = PreviousSet(57.5, 8, null)),
            {},
            {},
            {},
            Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D11)
@Composable
private fun LiveWorkoutCardIsometricPreview() {
    KineticPulseTheme {
        LiveWorkoutCard(
            LiveSetDraft(exercise = "planche lean", modality = ExerciseModality.ISOMETRIC, holdSeconds = 15),
            {},
            {},
            {},
            Modifier.padding(16.dp),
        )
    }
}
