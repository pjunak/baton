package eu.junak.baton.ui.session

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Session controls: switch the ambient *mode* (scene), fire SFX from the active
 * mode's soundboard, and stop the live interrupt / looping SFX. Everything reflects
 * the server's PlayerState, so it stays in sync with the web client and other remotes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(viewModel: SessionViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

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

        // --- Soundboard -----------------------------------------------------
        item { SectionHeader(ui.soundboardName?.let { "Soundboard · $it" } ?: "Soundboard") }
        item {
            when {
                ui.activeModeId == null -> Hint("Activate a mode to load its soundboard.")
                ui.soundCategories.isEmpty() -> Hint("This mode has no soundboard sounds.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ui.soundCategories.forEach { category ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(category.name, style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                category.items.forEach { item ->
                                    SoundButton(item.icon, item.name, enabled = ui.connected) {
                                        viewModel.fireSfx(item.file)
                                    }
                                }
                            }
                        }
                    }
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
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A soundboard pad: optional emoji glyph over the sound's name. Tap fires it once. */
@Composable
private fun SoundButton(icon: String?, name: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.size(width = 104.dp, height = 76.dp),
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
