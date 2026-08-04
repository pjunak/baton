package eu.junak.baton.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.R
import eu.junak.baton.core.model.LoopMode
import eu.junak.baton.core.model.ShuffleMode
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.ui.components.TrackArtwork
import eu.junak.baton.ui.console.ConsoleViewModel.QueueEntry
import eu.junak.baton.ui.devices.DevicePicker
import eu.junak.baton.ui.theme.ActiveAccent
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var showDevices by remember { mutableStateOf(false) }

    // Screen-on is deliberately opt-in: the display is normally Baton's largest battery cost.
    KeepScreenOn(ui.keepConsoleAwake)

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
                    contentDescription = stringResource(R.string.devices_title),
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
                    TrackArtwork(
                        url = ui.coverUrl,
                        modifier = Modifier.size(240.dp),
                        corner = 16.dp,
                        description = ui.nowPlaying?.effectiveTitle?.let {
                            stringResource(R.string.console_artwork_description, it)
                        },
                    )
                    Spacer(Modifier.height(20.dp))
                    NowPlaying(ui.nowPlaying?.effectiveTitle, ui.nowPlaying?.artist)
                }
            }

            if (ui.queue.isNotEmpty()) {
                item { QueueHeader(ui.queue.size, enabled = ui.connected, onClear = viewModel::clearQueue) }
                itemsIndexed(ui.queue, key = { index, entry -> "q:$index:${entry.trackId}" }) { index, entry ->
                    QueueRow(
                        entry = entry,
                        index = index,
                        queueSize = ui.queue.size,
                        coverUrl = viewModel.coverUrl(entry.trackId),
                        enabled = ui.connected,
                        onPlay = { viewModel.jumpToQueue(index) },
                        onMove = viewModel::moveQueueItem,
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
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
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
                    text = stringResource(
                        if (connecting) R.string.connection_connecting else R.string.connection_reconnecting,
                    ),
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
                text = artist?.ifBlank { stringResource(R.string.unknown_artist) }
                    ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = stringResource(R.string.console_nothing_playing),
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
    val displayedPositionMs = (frac * durationMs).roundToInt()
    val seekDescription = stringResource(R.string.console_seek_description)
    val seekState = stringResource(
        R.string.console_seek_state,
        formatTime(displayedPositionMs),
        formatTime(durationMs),
    )
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val dotOffset = with(LocalDensity.current) { (widthPx * frac).toDp() } - 5.dp

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics {
                    contentDescription = seekDescription
                    stateDescription = seekState
                    progressBarRangeInfo = ProgressBarRangeInfo(frac, 0f..1f)
                    if (!enabled || durationMs <= 0) disabled()
                    setProgress { target ->
                        if (!enabled || durationMs <= 0) {
                            false
                        } else {
                            onSeek((target.coerceIn(0f, 1f) * durationMs).roundToInt())
                            true
                        }
                    }
                }
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
        Row(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatTime(displayedPositionMs),
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
            Icon(
                Icons.Filled.SkipPrevious,
                stringResource(R.string.console_previous),
                Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.width(72.dp)) // the docked play button rests in this gap (in the nav bar below)
        IconButton(onClick = onNext, enabled = enabled) {
            Icon(
                Icons.Filled.SkipNext,
                stringResource(R.string.console_next),
                Modifier.size(34.dp),
            )
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
    ToggleIcon(
        Icons.Filled.Shuffle,
        stringResource(
            if (mode == ShuffleMode.OFF) R.string.console_shuffle_off else R.string.console_shuffle_random,
        ),
        active = mode != ShuffleMode.OFF,
        enabled = enabled,
        onClick = onClick,
    )
}

/** Repeat / continue control. A distinct glyph per loop mode so the active
 *  end-of-queue behaviour is legible at a glance: off = repeat (dimmed),
 *  continue = ∞, repeat-all = repeat, repeat-one = repeat·1. */
@Composable
private fun LoopToggle(mode: LoopMode, enabled: Boolean, onClick: () -> Unit) {
    val (icon, labelResource) = when (mode) {
        LoopMode.OFF -> Icons.Filled.Repeat to R.string.console_repeat_off
        LoopMode.FOLLOW -> Icons.Filled.AllInclusive to R.string.console_repeat_follow
        LoopMode.QUEUE -> Icons.Filled.Repeat to R.string.console_repeat_all
        LoopMode.TRACK -> Icons.Filled.RepeatOne to R.string.console_repeat_one
    }
    ToggleIcon(icon, stringResource(labelResource), active = mode != LoopMode.OFF, enabled = enabled, onClick = onClick)
}

@Composable
private fun QueueHeader(count: Int, enabled: Boolean, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.console_up_next, count), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onClear, enabled = enabled) { Text(stringResource(R.string.action_clear)) }
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    index: Int,
    queueSize: Int,
    coverUrl: String?,
    enabled: Boolean,
    onPlay: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
) {
    var dragOffset by remember(index) { mutableFloatStateOf(0f) }
    var rowHeightPx by remember(index) { mutableIntStateOf(0) }
    val title = entry.track?.effectiveTitle ?: stringResource(R.string.track_fallback, entry.trackId)
    val playLabel = stringResource(R.string.console_play_queue, title)
    val reorderLabel = stringResource(R.string.console_reorder_queue, title)
    val moveUpLabel = stringResource(R.string.console_move_queue_up)
    val moveDownLabel = stringResource(R.string.console_move_queue_down)

    ListItem(
        leadingContent = { TrackArtwork(coverUrl, Modifier.size(44.dp), corner = 6.dp) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(entry.track?.artist?.ifBlank { stringResource(R.string.unknown_artist) } ?: stringResource(R.string.unknown_artist), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = reorderLabel
                            if (!enabled) disabled()
                            customActions = buildList {
                                if (enabled && index > 0) {
                                    add(CustomAccessibilityAction(moveUpLabel) {
                                        onMove(index, index - 1)
                                        true
                                    })
                                }
                                if (enabled && index < queueSize - 1) {
                                    add(CustomAccessibilityAction(moveDownLabel) {
                                        onMove(index, index + 1)
                                        true
                                    })
                                }
                            }
                        }
                        .pointerInput(index, queueSize, enabled, rowHeightPx) {
                            if (!enabled || rowHeightPx <= 0) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                },
                                onDragEnd = {
                                    val target = (index + (dragOffset / rowHeightPx).roundToInt())
                                        .coerceIn(0, queueSize - 1)
                                    dragOffset = 0f
                                    if (target != index) onMove(index, target)
                                },
                                onDragCancel = { dragOffset = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DragHandle, contentDescription = null)
                }
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.console_remove_queue))
                }
            }
        },
        modifier = Modifier
            .zIndex(if (dragOffset == 0f) 0f else 1f)
            .graphicsLayer { translationY = dragOffset }
            .onSizeChanged { rowHeightPx = it.height }
            .clickable(enabled = enabled, onClickLabel = playLabel, onClick = onPlay),
    )
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
