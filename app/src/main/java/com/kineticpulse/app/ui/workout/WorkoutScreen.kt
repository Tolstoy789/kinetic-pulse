package com.kineticpulse.app.ui.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kineticpulse.app.R
import com.kineticpulse.app.ai.EngineState
import com.kineticpulse.app.data.db.SetWithExercise
import com.kineticpulse.app.ui.theme.KineticTheme
import com.kineticpulse.core.fatigue.FatigueLevel
import com.kineticpulse.core.fatigue.FatigueReport
import com.kineticpulse.core.model.ExerciseModality
import com.kineticpulse.core.model.RoutineSetDto

@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val quickLog by viewModel.quickLog.collectAsStateWithLifecycle()
    val engine by viewModel.engineState.collectAsStateWithLifecycle()
    val sets by viewModel.sets.collectAsStateWithLifecycle()
    val fatigue by viewModel.fatigue.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            // safeDrawing ya incluye el teclado (IME) y los recortes de cámara.
            .safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "header") {
            Column {
                Text(stringResource(R.string.live_workout_title).uppercase(), style = MaterialTheme.typography.headlineMedium)
                EngineStatusLine(engine)
            }
        }
        item(key = "quicklog") {
            QuickLogPanel(
                state = quickLog,
                onTextChange = viewModel::onQuickLogTextChange,
                onSubmit = viewModel::onQuickLogSubmit,
            )
        }
        item(key = "live") {
            LiveWorkoutCard(draft = draft, onDraftChange = viewModel::onDraftChange, onLogSet = viewModel::onLogSet)
        }
        fatigue?.let { report -> item(key = "fatigue") { FatigueChip(report) } }
        if (sets.isNotEmpty()) {
            item(key = "sets_title") {
                Text(stringResource(R.string.sets_logged).uppercase(), style = MaterialTheme.typography.labelLarge)
            }
        }
        items(sets, key = { it.id }) { set -> SetRow(set, Modifier.animateItem()) }
    }
}

@Composable
private fun EngineStatusLine(state: EngineState) {
    val text = when (state) {
        EngineState.Unloaded -> stringResource(R.string.engine_status_unloaded)
        is EngineState.Loading -> stringResource(R.string.engine_status_loading)
        is EngineState.Ready -> stringResource(R.string.engine_status_ready, state.model.displayName, state.backend.name)
        is EngineState.ModelMissing -> stringResource(R.string.engine_status_missing)
        is EngineState.Error -> stringResource(R.string.engine_status_error, state.message)
    }
    val color = when (state) {
        is EngineState.Ready -> KineticTheme.colors.glowPrimary
        is EngineState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun QuickLogPanel(state: QuickLogUiState, onTextChange: (String) -> Unit, onSubmit: () -> Unit) {
    OutlinedCard(
        border = BorderStroke(1.dp, KineticTheme.colors.glowSecondary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.quick_log_title).uppercase(), style = MaterialTheme.typography.labelLarge, color = KineticTheme.colors.glowSecondary)
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.quick_log_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSubmit, enabled = !state.isParsing && state.text.isNotBlank()) {
                    Text(stringResource(R.string.action_parse).uppercase(), style = MaterialTheme.typography.labelLarge)
                }
                if (state.isParsing) CircularProgressIndicator(strokeWidth = 2.dp, color = KineticTheme.colors.glowSecondary)
            }
            state.preview.forEach { PreviewLine(it) }
        }
    }
}

@Composable
private fun PreviewLine(dto: RoutineSetDto) {
    val detail = when (dto.modality) {
        ExerciseModality.ISOMETRIC -> "${dto.sets}×${dto.reps ?: 1} · ${dto.holdSeconds ?: 0}s"
        ExerciseModality.WEIGHTED -> "${dto.sets}×${dto.reps ?: 0} · ${dto.weightKg ?: 0.0} kg"
        ExerciseModality.BODYWEIGHT -> "${dto.sets}×${dto.reps ?: 0}"
    }
    Text("› ${dto.exercise}  $detail", style = MaterialTheme.typography.labelMedium, color = KineticTheme.colors.glowPrimary)
}

@Composable
private fun FatigueChip(report: FatigueReport) {
    val colors = KineticTheme.colors
    val tint: Color = when (report.level) {
        FatigueLevel.FRESH -> colors.fatigueFresh
        FatigueLevel.PRODUCTIVE -> colors.fatigueProductive
        FatigueLevel.HIGH -> colors.fatigueHigh
        FatigueLevel.OVERREACHING -> colors.fatigueOverreaching
    }
    OutlinedCard(border = BorderStroke(1.dp, tint.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${stringResource(R.string.fatigue_label).uppercase()} · ${report.level.name} · ACWR ${report.acuteChronicRatio}",
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
            Text(report.recommendation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SetRow(set: SetWithExercise, modifier: Modifier = Modifier) {
    val detail = when (set.modality) {
        ExerciseModality.ISOMETRIC -> "${set.holdSeconds ?: 0}s" + (set.reps?.let { " × $it" } ?: "")
        ExerciseModality.WEIGHTED -> "${set.weightKg ?: 0.0} kg × ${set.reps ?: 0}"
        ExerciseModality.BODYWEIGHT -> "${set.reps ?: 0} reps"
    }
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#${set.setIndex}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "  ${set.exerciseName}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(detail, style = MaterialTheme.typography.labelLarge, color = KineticTheme.colors.glowPrimary)
        set.rpe?.let { Text("  @$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
