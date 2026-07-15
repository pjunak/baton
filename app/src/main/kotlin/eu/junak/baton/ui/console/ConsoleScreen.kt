package eu.junak.baton.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.core.model.LoopMode
import eu.junak.baton.core.model.ShuffleMode
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.ui.components.TrackArtwork
import eu.junak.baton.ui.console.ConsoleViewModel.QueueEntry
import eu.junak.baton.ui.devices.DevicePicker
import eu.junak.baton.ui.theme.ActiveAccent
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var showDevices by remember { mutableStateOf(false) }

    // The remote shouldn't sleep mid-session — keep the screen on while the Console is shown.
    KeepScreenOn()

    Column(Modifier.fillMaxSize()) {
        // Output picker tucked in the top-right corner, keeping the bottom controls minimal.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { showDevices = true }) {
                Icon(
                    imageVector = Icons.Filled.Speaker,
                    contentDescription = "Output devices",
                    tint = if (ui.playingHere) ActiveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Scrollable: artwork, now-playing, and the queue.
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!ui.connected) {
                item { ConnectionBanner(ui.status, ui.failureDetail) }
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TrackArtwork(ui.coverUrl, Modifier.size(240.dp), corner = 16.dp)
                    Spacer(Modifier.height(20.dp))
                    NowPlaying(ui.nowPlaying?.effectiveTitle, ui.nowPlaying?.artist)
                }
            }

            if (ui.queue.isNotEmpty()) {
                item { QueueHeader(ui.queue.size, enabled = ui.connected, onClear = viewModel::clearQueue) }
                itemsIndexed(ui.queue, key = { index, entry -> "q:$index:${entry.trackId}" }) { index, entry ->
                    QueueRow(
                        entry = entry,
                        coverUrl = viewModel.coverUrl(entry.trackId),
                        enabled = ui.connected,
                        onRemove = { viewModel.removeFromQueue(index) },
                    )
                }
            }
        }

        // Pinned, centered control bar (Spotify-style): thin seek line, compact transport, device picker.
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Negative gap: the seek block drops into the control buttons' empty top padding.
                // Safe because the times sit at the far edges while the controls are centered.
                verticalArrangement = Arrangement.spacedBy((-12).dp),
            ) {
                SeekLine(ui.positionMs, ui.durationMs, enabled = ui.connected, onSeek = viewModel::seekTo)
                TransportRow(
                    shuffle = ui.shuffle,
                    loop = ui.loop,
                    enabled = ui.connected,
                    onShuffle = viewModel::cycleShuffle,
                    onPrevious = viewModel::skipPrevious,
                    onNext = viewModel::skipNext,
                    onLoop = viewModel::cycleLoop,
                )
            }
        }
    }

    DeviceTopSheet(visible = showDevices, onDismiss = { showDevices = false })
}

/**
 * The output-device picker, pulled DOWN from the top (its trigger is the speaker icon
 * up there). A dimmed scrim — tap to dismiss — plus a full-width panel that slides in
 * from the top edge. Material3 has no top-sheet, so it's hand-rolled.
 */
@Composable
private fun DeviceTopSheet(visible: Boolean, onDismiss: () -> Unit) {
    BackHandler(enabled = visible) { onDismiss() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures {} },
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                DevicePicker()
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun ConnectionBanner(status: ConnectionStatus, failureDetail: String?) {
    val connecting = status == ConnectionStatus.CONNECTING
    val container = if (connecting) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = if (connecting) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = onContainer)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (connecting) "Connecting…" else "Disconnected — reconnecting",
                    color = onContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            // The why, when an outage persists (wrong URL, server down). Transient
            // failures self-heal and take the whole banner with them.
            if (!connecting && failureDetail != null) {
                Text(
                    text = failureDetail,
                    color = onContainer,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NowPlaying(title: String?, artist: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = artist?.ifBlank { "Unknown artist" } ?: "Unknown artist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/** A thin, minimalist seek line with a small playhead dot — drag anywhere on it to scrub. */
@Composable
private fun SeekLine(positionMs: Int, durationMs: Int, enabled: Boolean, onSeek: (Int) -> Unit) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableIntStateOf(0) }
    val frac = dragFrac ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.error // DBG: was onSurfaceVariant @0.3f — bright to confirm render
    val dotOffset = with(LocalDensity.current) { (widthPx * frac).toDp() } - 5.dp

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(enabled, durationMs, widthPx) {
                    if (!enabled || durationMs <= 0 || widthPx <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFrac = (offset.x / widthPx).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ -> dragFrac = (change.position.x / widthPx).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFrac?.let { onSeek((it * durationMs).toInt()) }
                            dragFrac = null
                        },
                        onDragCancel = { dragFrac = null },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(inactive),
            )
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(active),
            )
            Box(
                Modifier
                    .offset(x = dotOffset)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(active),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime((frac * durationMs).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportRow(
    shuffle: ShuffleMode,
    loop: LoopMode,
    enabled: Boolean,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLoop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShuffleToggle(shuffle, enabled = enabled, onClick = onShuffle)
        IconButton(onClick = onPrevious, enabled = enabled) {
            Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(34.dp))
        }
        Spacer(Modifier.width(72.dp)) // the docked play button rests in this gap (in the nav bar below)
        IconButton(onClick = onNext, enabled = enabled) {
            Icon(Icons.Filled.SkipNext, "Next", Modifier.size(34.dp))
        }
        LoopToggle(loop, enabled = enabled, onClick = onLoop)
    }
}

@Composable
private fun ToggleIcon(
    icon: ImageVector,
    description: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (active) ActiveAccent else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}

/** Shuffle control: off (dimmed) ↔ random (accent). */
@Composable
private fun ShuffleToggle(mode: ShuffleMode, enabled: Boolean, onClick: () -> Unit) {
    val (icon, label) = when (mode) {
        ShuffleMode.OFF -> Icons.Filled.Shuffle to "Shuffle off"
        ShuffleMode.RANDOM -> Icons.Filled.Shuffle to "Shuffle: random order"
    }
    ToggleIcon(icon, label, active = mode != ShuffleMode.OFF, enabled = enabled, onClick = onClick)
}

/** Repeat / continue control. A distinct glyph per loop mode so the active
 *  end-of-queue behaviour is legible at a glance: off = repeat (dimmed),
 *  continue = ∞, repeat-all = repeat, repeat-one = repeat·1. */
@Composable
private fun LoopToggle(mode: LoopMode, enabled: Boolean, onClick: () -> Unit) {
    val (icon, label) = when (mode) {
        LoopMode.OFF -> Icons.Filled.Repeat to "Repeat off"
        LoopMode.FOLLOW -> Icons.Filled.AllInclusive to "Continue into the library"
        LoopMode.QUEUE -> Icons.Filled.Repeat to "Repeat all"
        LoopMode.TRACK -> Icons.Filled.RepeatOne to "Repeat one"
    }
    ToggleIcon(icon, label, active = mode != LoopMode.OFF, enabled = enabled, onClick = onClick)
}

@Composable
private fun QueueHeader(count: Int, enabled: Boolean, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Up next ($count)", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onClear, enabled = enabled) { Text("Clear") }
    }
}

@Composable
private fun QueueRow(entry: QueueEntry, coverUrl: String?, enabled: Boolean, onRemove: () -> Unit) {
    ListItem(
        leadingContent = { TrackArtwork(coverUrl, Modifier.size(44.dp), corner = 6.dp) },
        headlineContent = { Text(entry.track?.effectiveTitle ?: "Track #${entry.trackId}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(entry.track?.artist?.ifBlank { "Unknown artist" } ?: "Unknown artist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Filled.Close, contentDescription = "Remove from queue")
            }
        },
    )
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
