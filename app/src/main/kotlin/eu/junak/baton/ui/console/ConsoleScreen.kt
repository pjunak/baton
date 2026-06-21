package eu.junak.baton.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.core.model.LoopMode
import eu.junak.baton.core.model.ShuffleMode
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.ui.console.ConsoleViewModel.QueueEntry
import java.util.Locale

@Composable
fun ConsoleScreen(
    onSignedOut: () -> Unit,
    viewModel: ConsoleViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { StatusLine(ui.status) }

        item { NowPlaying(ui.nowPlaying?.effectiveTitle, ui.nowPlaying?.artist) }

        if (ui.durationMs > 0) {
            item { SeekBar(ui.positionMs, ui.durationMs, viewModel::seekTo) }
        }

        item {
            TransportRow(
                isPlaying = ui.isPlaying,
                shuffle = ui.shuffle,
                loop = ui.loop,
                onShuffle = viewModel::cycleShuffle,
                onPrevious = viewModel::skipPrevious,
                onPlayPause = viewModel::playPause,
                onNext = viewModel::skipNext,
                onLoop = viewModel::cycleLoop,
            )
        }

        item { ModeCaption(ui.shuffle, ui.loop) }

        item { VolumeRow(ui.volume, viewModel::setVolume) }

        if (ui.queue.isNotEmpty()) {
            item { QueueHeader(ui.queue.size, viewModel::clearQueue) }
            itemsIndexed(ui.queue, key = { index, entry -> "q:$index:${entry.trackId}" }) { index, entry ->
                QueueRow(entry) { viewModel.removeFromQueue(index) }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.signOut(onSignedOut) }) { Text("Sign out") }
        }
    }
}

@Composable
private fun StatusLine(status: ConnectionStatus) {
    Text(
        text = statusLabel(status),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NowPlaying(title: String?, artist: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                text = artist?.ifBlank { "Unknown artist" } ?: "Unknown artist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "Nothing playing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeekBar(positionMs: Int, durationMs: Int, onSeek: (Int) -> Unit) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val value = if (scrubbing) scrubValue else positionMs.toFloat()

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = value.coerceIn(0f, durationMs.toFloat()),
            onValueChange = {
                scrubbing = true
                scrubValue = it
            },
            onValueChangeFinished = {
                onSeek(scrubValue.toInt())
                scrubbing = false
            },
            valueRange = 0f..durationMs.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(value.toInt()), style = MaterialTheme.typography.labelMedium)
            Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    shuffle: ShuffleMode,
    loop: LoopMode,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onLoop: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIcon(Icons.Filled.Shuffle, "Shuffle", active = shuffle != ShuffleMode.OFF, onClick = onShuffle)
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp))
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(52.dp),
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp))
        }
        ToggleIcon(
            icon = if (loop == LoopMode.TRACK) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            description = "Repeat",
            active = loop != LoopMode.OFF,
            onClick = onLoop,
        )
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeCaption(shuffle: ShuffleMode, loop: LoopMode) {
    Text(
        text = "Shuffle: ${shuffleLabel(shuffle)}  ·  Repeat: ${loopLabel(loop)}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun VolumeRow(volume: Double, onVolume: (Double) -> Unit) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val value = if (scrubbing) scrubValue else volume.toFloat()

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.VolumeDown, contentDescription = null)
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = {
                scrubbing = true
                scrubValue = it
            },
            onValueChangeFinished = {
                onVolume(scrubValue.toDouble())
                scrubbing = false
            },
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Icon(Icons.Filled.VolumeUp, contentDescription = null)
    }
}

@Composable
private fun QueueHeader(count: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Up next ($count)", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun QueueRow(entry: QueueEntry, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.track?.effectiveTitle ?: "Track #${entry.trackId}") },
        supportingContent = { Text(entry.track?.artist?.ifBlank { "Unknown artist" } ?: "Unknown artist") },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove from queue")
            }
        },
    )
}

private fun statusLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.CONNECTED -> "● Connected"
    ConnectionStatus.CONNECTING -> "○ Connecting…"
    ConnectionStatus.DISCONNECTED -> "○ Disconnected"
}

private fun shuffleLabel(mode: ShuffleMode): String = when (mode) {
    ShuffleMode.OFF -> "Off"
    ShuffleMode.RANDOM -> "Random"
    ShuffleMode.WEIGHTED -> "Weighted"
}

private fun loopLabel(mode: LoopMode): String = when (mode) {
    LoopMode.OFF -> "Off"
    LoopMode.FOLLOW -> "Follow"
    LoopMode.QUEUE -> "Queue"
    LoopMode.TRACK -> "Track"
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
