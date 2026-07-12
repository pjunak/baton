package eu.junak.baton.ui.session

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Session controls — the live-firing surface for the active *mode* (scene):
 * mode switching, one-tap cues, the soundboard (tap fires once; long-press
 * starts a repeating loop), EQ preset toggles, pre-configured interrupts, and
 * a live "Now" section for stopping the interrupt / running loops. Everything
 * reflects the server's PlayerState, so it stays in sync with the web client
 * and other remotes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(viewModel: SessionViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var loopTarget by remember { mutableStateOf<SessionViewModel.SoundItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // --- Modes ----------------------------------------------------------
        item { SectionHeader("Modes") }
        item {
            if (ui.modes.isEmpty()) {
                Hint("No modes configured on the server.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.modes.forEach { mode ->
                        FilterChip(
                            selected = mode.active,
                            onClick = { viewModel.toggleMode(mode.id) },
                            enabled = ui.connected,
                            label = { Text(mode.name) },
                        )
                    }
                }
            }
        }

        // --- Cues -----------------------------------------------------------
        if (ui.cues.isNotEmpty()) {
            item { SectionHeader("Cues") }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.cues.forEach { cue ->
                        CueButton(cue.name, cue.description, enabled = ui.connected) {
                            viewModel.fireCue(cue.id)
                        }
                    }
                }
            }
        }

        // --- Soundboard -----------------------------------------------------
        item { SectionHeader(ui.soundboardName?.let { "Soundboard · $it" } ?: "Soundboard") }
        item {
            when {
                ui.activeModeId == null -> Hint("Activate a mode to load its soundboard.")
                ui.soundCategories.isEmpty() -> Hint("This mode has no soundboard sounds.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Hint("Tap to fire once · hold to start a repeating loop.")
                    ui.soundCategories.forEach { category ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(category.name, style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                category.items.forEach { item ->
                                    SoundButton(
                                        icon = item.icon,
                                        name = item.name,
                                        enabled = ui.connected,
                                        onClick = { viewModel.fireSfx(item.file) },
                                        onLongClick = { loopTarget = item },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- EQ presets -------------------------------------------------------
        if (ui.presets.isNotEmpty()) {
            item { SectionHeader("EQ presets") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.presets.forEach { preset ->
                            FilterChip(
                                selected = preset.active,
                                onClick = { viewModel.togglePreset(preset.id) },
                                enabled = ui.connected,
                                label = { Text(preset.name) },
                            )
                        }
                    }
                    if (ui.presets.any { it.active }) {
                        TextButton(onClick = viewModel::clearPresets, enabled = ui.connected) {
                            Text("Clear all")
                        }
                    }
                }
            }
        }

        // --- Interrupts -------------------------------------------------------
        if (ui.interrupts.isNotEmpty()) {
            item { SectionHeader("Interrupts") }
            items(ui.interrupts, key = { "${it.name}|${it.detail}" }) { interrupt ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(interrupt.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            interrupt.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (interrupt.canFire) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.fireInterrupt(interrupt) },
                        enabled = ui.connected && interrupt.canFire,
                    ) { Text("Fire") }
                }
            }
        }

        // --- Live: interrupt + loops ---------------------------------------
        if (ui.interruptActive || ui.loops.isNotEmpty()) {
            item { SectionHeader("Now") }
            if (ui.interruptActive) {
                item { LiveRow("Interrupt playing", enabled = ui.connected, onStop = viewModel::cancelInterrupt) }
            }
            items(ui.loops, key = { it.id }) { loop ->
                LiveRow("Loop · ${loop.name}", enabled = ui.connected) { viewModel.stopLoop(loop.id) }
            }
        }
    }

    loopTarget?.let { target ->
        LoopIntervalDialog(
            soundName = target.name,
            onConfirm = { intervalS ->
                viewModel.startLoop(target.name, target.file, intervalS)
                loopTarget = null
            },
            onDismiss = { loopTarget = null },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun LiveRow(label: String, enabled: Boolean, onStop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onStop, enabled = enabled) { Text("Stop") }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A cue tile: the mode's saved one-click setup. Reads as the headline action
 *  (container color) next to the neutral soundboard pads. */
@Composable
private fun CueButton(name: String, description: String?, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.widthIn(min = 104.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A soundboard pad: optional emoji glyph over the sound's name. Tap fires it
 *  once; long-press opens the repeating-loop dialog. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundButton(
    icon: String?,
    name: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .size(width = 104.dp, height = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!icon.isNullOrBlank()) {
                Text(icon, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Interval picker for a long-pressed sound. The loop is server-driven, so it
 *  keeps firing across app restarts until stopped (here or on any client). */
@Composable
private fun LoopIntervalDialog(
    soundName: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var intervalText by remember { mutableStateOf("30") }
    val interval = intervalText.toIntOrNull()?.takeIf { it in 1..3600 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loop “$soundName”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Fire every N seconds until stopped (runs on the server — every client sees it under Now).")
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { value -> intervalText = value.filter(Char::isDigit).take(4) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("s") },
                    singleLine = true,
                    isError = interval == null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = interval != null,
                onClick = { interval?.let { onConfirm(it.toDouble()) } },
            ) { Text("Start loop") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
