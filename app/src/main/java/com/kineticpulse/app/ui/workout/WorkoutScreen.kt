package com.kineticpulse.app.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kineticpulse.app.R
import com.kineticpulse.app.data.db.SetWithExercise
import com.kineticpulse.app.ui.theme.KineticTheme
import com.kineticpulse.core.fatigue.FatigueLevel
import com.kineticpulse.core.model.ExerciseModality
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutEvent.SetSaved -> {
                    val result = snackbar.showSnackbar(
                        message = context.resources.getQuantityString(R.plurals.sets_saved, event.count, event.count),
                        actionLabel = context.getString(R.string.action_undo).takeIf { event.count == 1 },
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoLastSet()
                }
                WorkoutEvent.SetUndone -> snackbar.showSnackbar(context.getString(R.string.set_undone))
            }
        }
    }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }

    WorkoutContent(state = state, actions = viewModel, now = now, snackbar = snackbar)
}

/** Pantalla sin ViewModel: la usan la app, las vistas previas y las capturas. */
@Composable
fun WorkoutContent(
    state: WorkoutUiState,
    actions: WorkoutActions,
    now: Long,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                // safeDrawing ya incluye el teclado (IME) y los recortes de cámara.
                .safeDrawingPadding(),
        ) {
            when {
                state.isLoading -> Unit
                state.active == null -> HomeContent(state.home, actions)
                else -> ActiveContent(state.active, state.exercises, actions, now)
            }
        }
    }
}

// ── Inicio ──────────────────────────────────────────────────────────

@Composable
fun HomeContent(home: HomeUiState, actions: WorkoutActions) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PulseMark(Modifier.width(40.dp).height(20.dp))
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = KineticTheme.colors.glowPrimary,
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = 0.sp))
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            home.lastSummary?.let { s ->
                InfoCard(
                    title = stringResource(R.string.home_done_title),
                    body = pluralStringResource(R.plurals.home_done_body, s.sets, s.sets, s.minutes),
                    tint = KineticTheme.colors.glowPrimary,
                )
            }
            home.fatigue?.let { level ->
                val (title, body, tint) = fatigueCopy(level)
                InfoCard(title = title, body = body, tint = tint)
            }
            Text(
                if (home.sessionsThisWeek == 0) {
                    stringResource(R.string.home_week_none)
                } else {
                    pluralStringResource(R.plurals.home_week, home.sessionsThisWeek, home.sessionsThisWeek)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))
        BigButton(text = stringResource(R.string.action_start_workout), onClick = actions::onStartWorkout)
    }
}

@Composable
private fun fatigueCopy(level: FatigueLevel): Triple<String, String, Color> {
    val c = KineticTheme.colors
    return when (level) {
        FatigueLevel.FRESH -> Triple(stringResource(R.string.fatigue_fresh_title), stringResource(R.string.fatigue_fresh_body), c.fatigueFresh)
        FatigueLevel.PRODUCTIVE -> Triple(stringResource(R.string.fatigue_ok_title), stringResource(R.string.fatigue_ok_body), c.fatigueProductive)
        FatigueLevel.HIGH -> Triple(stringResource(R.string.fatigue_high_title), stringResource(R.string.fatigue_high_body), c.fatigueHigh)
        FatigueLevel.OVERREACHING -> Triple(stringResource(R.string.fatigue_over_title), stringResource(R.string.fatigue_over_body), c.fatigueOverreaching)
    }
}

@Composable
private fun InfoCard(title: String, body: String, tint: Color) {
    OutlinedCard(border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = tint)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** El trazo de pulso del icono, en pequeño, como marca de la app. */
@Composable
private fun PulseMark(modifier: Modifier = Modifier) {
    val volt = KineticTheme.colors.glowPrimary
    val cyan = KineticTheme.colors.glowSecondary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.6f)
            lineTo(w * 0.3f, h * 0.6f)
            lineTo(w * 0.42f, h * 0.1f)
            lineTo(w * 0.55f, h * 0.95f)
            lineTo(w * 0.66f, h * 0.6f)
            lineTo(w * 0.85f, h * 0.6f)
        }
        drawPath(path, volt, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(cyan, radius = 3.dp.toPx(), center = Offset(w * 0.95f, h * 0.6f))
    }
}

// ── Entreno en curso ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveContent(
    active: ActiveUiState,
    exercises: List<ExerciseOption>,
    actions: WorkoutActions,
    now: Long,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showQuickLog by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "top") {
            ActiveTopBar(minutes = ((now - active.startedAt) / 60_000).toInt(), onFinish = { confirmFinish = true })
        }
        item(key = "card") {
            LiveWorkoutCard(
                draft = active.draft,
                onDraftChange = actions::onDraftChange,
                onLogSet = actions::onLogSet,
                onChangeExercise = { showPicker = true },
            )
        }
        item(key = "rest") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                active.lastSetAt?.let { RestTimer(seconds = ((now - it) / 1_000).toInt(), modifier = Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
                TextButton(onClick = { showQuickLog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_type_it), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (active.sets.isNotEmpty()) {
            item(key = "sets_title") {
                Text(
                    pluralStringResource(R.plurals.today_sets, active.sets.size, active.sets.size),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(active.sets, key = { it.id }) { set -> SetRow(set, Modifier.animateItem()) }
        }
    }

    if (showPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showPicker = false }, sheetState = sheetState) {
            ExercisePickerContent(
                options = exercises,
                current = active.draft.exercise,
                onPick = {
                    actions.onPickExercise(it)
                    showPicker = false
                },
            )
        }
    }

    if (showQuickLog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Al guardar, el ViewModel vacía el texto: cerramos la hoja en cuanto ocurre.
        var submitted by remember { mutableStateOf(false) }
        LaunchedEffect(active.quickLog) {
            if (submitted && !active.quickLog.isParsing && active.quickLog.text.isEmpty()) showQuickLog = false
        }
        ModalBottomSheet(onDismissRequest = { showQuickLog = false }, sheetState = sheetState) {
            QuickLogContent(
                state = active.quickLog,
                onTextChange = actions::onQuickLogTextChange,
                onSubmit = {
                    submitted = true
                    actions.onQuickLogSubmit()
                },
            )
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = { Text(pluralStringResource(R.plurals.finish_body, active.sets.size, active.sets.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    actions.onFinishWorkout()
                }) { Text(stringResource(R.string.action_finish)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.action_keep_going)) }
            },
        )
    }
}

@Composable
private fun ActiveTopBar(minutes: Int, onFinish: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.workout_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.workout_elapsed, minutes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_finish), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun RestTimer(seconds: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.rest_timer, seconds / 60, seconds % 60),
        style = MaterialTheme.typography.titleMedium,
        color = KineticTheme.colors.glowSecondary,
        modifier = modifier,
    )
}

@Composable
private fun SetRow(set: SetWithExercise, modifier: Modifier = Modifier) {
    val detail = when (set.modality) {
        ExerciseModality.ISOMETRIC -> "${set.holdSeconds ?: 0} s"
        ExerciseModality.WEIGHTED -> "${formatKg(set.weightKg ?: 0.0)} kg × ${set.reps ?: 0}"
        ExerciseModality.BODYWEIGHT -> "${set.reps ?: 0} reps"
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(set.exerciseName.displayName(), style = MaterialTheme.typography.bodyLarge)
                set.rpe?.let {
                    Text(
                        stringResource(Effort.nearest(it).label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(detail, style = MaterialTheme.typography.titleLarge, color = KineticTheme.colors.glowPrimary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

// ── Hojas inferiores ────────────────────────────────────────────────

@Composable
fun ExercisePickerContent(options: List<ExerciseOption>, current: String, onPick: (ExerciseOption) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim().lowercase()
    val filtered = options.filter { trimmed.isEmpty() || trimmed in it.name }
    val isNew = trimmed.isNotEmpty() && options.none { it.name == trimmed }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(stringResource(R.string.picker_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.picker_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (isNew) {
            Text(
                stringResource(R.string.picker_new, query.trim()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseModality.entries.forEach { modality ->
                    OutlinedButton(
                        onClick = { onPick(ExerciseOption(trimmed, modality)) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(stringResource(modality.label()), textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(filtered, key = { it.name }) { option ->
                val selected = option.name == current
                TextButton(
                    onClick = { onPick(option) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        option.name.displayName(),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (selected) KineticTheme.colors.glowPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(option.modality.label()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun QuickLogContent(state: QuickLogUiState, onTextChange: (String) -> Unit, onSubmit: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.quick_log_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.quick_log_help),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.OutlinedTextField(
            value = state.text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.quick_log_hint)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        state.preview.forEach { dto ->
            val detail = when (dto.modality) {
                ExerciseModality.ISOMETRIC -> "${dto.sets} × ${dto.holdSeconds ?: 0} s"
                ExerciseModality.WEIGHTED -> "${dto.sets} × ${dto.reps ?: 0} · ${formatKg(dto.weightKg ?: 0.0)} kg"
                ExerciseModality.BODYWEIGHT -> "${dto.sets} × ${dto.reps ?: 0}"
            }
            Text("✓ ${dto.exercise.displayName()}  $detail", style = MaterialTheme.typography.titleMedium, color = KineticTheme.colors.glowPrimary)
        }
        BigButton(
            text = stringResource(if (state.isParsing) R.string.quick_log_reading else R.string.action_save),
            onClick = { if (state.text.isNotBlank() && !state.isParsing) onSubmit() },
        )
    }
}
