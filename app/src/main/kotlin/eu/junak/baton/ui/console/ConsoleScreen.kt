package eu.junak.baton.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.R
import eu.junak.baton.core.model.LoopMode
import eu.junak.baton.core.model.ShuffleMode
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.ui.components.TrackArtwork
import eu.junak.baton.ui.components.TrackListItem
import eu.junak.baton.ui.console.ConsoleViewModel.QueueEntry
import eu.junak.baton.ui.devices.DevicePicker
import eu.junak.baton.ui.devices.DeviceVolumeControl
import eu.junak.baton.ui.devices.DevicesViewModel
import eu.junak.baton.ui.theme.ActiveAccent
import eu.junak.baton.ui.theme.BatonSpacing
import java.util.Locale
import kotlinx.coroutines.isActive
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    openDevices: Boolean = false,
    onOpenDevicesHandled: () -> Unit = {},
    viewModel: ConsoleViewModel = hiltViewModel(),
    devicesViewModel: DevicesViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val devicesUi by devicesViewModel.uiState.collectAsStateWithLifecycle()
    var showDevices by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openDevices) {
        if (openDevices) {
            showDevices = true
            onOpenDevicesHandled()
        }
    }

    // Screen-on is deliberately opt-in: the display is normally Baton's largest battery cost.
    KeepScreenOn(ui.keepConsoleAwake)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useWideLayout = useWideConsoleLayout(maxWidth.value, maxHeight.value)
        if (useWideLayout) {
            ConsoleWide(
                ui,
                viewModel,
                devicesUi,
                onDeviceVolume = devicesViewModel::setDeviceVolume,
                onShowDevices = { showDevices = true },
            )
        } else {
            ConsolePortrait(
                ui,
                viewModel,
                devicesUi,
                onDeviceVolume = devicesViewModel::setDeviceVolume,
                onShowDevices = { showDevices = true },
            )
        }
    }

    DeviceTopSheet(
        visible = showDevices,
        viewModel = devicesViewModel,
        onDismiss = { showDevices = false },
    )
}

@Composable
private fun ConsolePortrait(
    ui: ConsoleViewModel.UiState,
    viewModel: ConsoleViewModel,
    devicesUi: DevicesViewModel.UiState,
    onDeviceVolume: (String, Float) -> Unit,
    onShowDevices: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutputPicker(devicesUi, onDeviceVolume, onShowDevices)
        QueueList(ui, viewModel, Modifier.weight(1f).fillMaxWidth(), PaddingValues(BatonSpacing.Medium)) {
            if (!ui.connected) item { ConnectionBanner(ui.status, ui.failureDetail) }
            item { ArtworkAndNowPlaying(ui, artworkSize = 240.dp) }
        }
        ConsoleControlBar(ui, viewModel)
    }
}

@Composable
private fun ConsoleWide(
    ui: ConsoleViewModel.UiState,
    viewModel: ConsoleViewModel,
    devicesUi: DevicesViewModel.UiState,
    onDeviceVolume: (String, Float) -> Unit,
    onShowDevices: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(0.44f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(BatonSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!ui.connected) item { ConnectionBanner(ui.status, ui.failureDetail) }
            item { ArtworkAndNowPlaying(ui, artworkSize = 200.dp) }
        }
        Column(
            modifier = Modifier
                .weight(0.56f)
                .fillMaxHeight(),
        ) {
            OutputPicker(devicesUi, onDeviceVolume, onShowDevices)
            QueueList(ui, viewModel, Modifier.weight(1f).fillMaxWidth(), PaddingValues(horizontal = BatonSpacing.Medium))
            ConsoleControlBar(ui, viewModel)
        }
    }
}

@Composable
private fun OutputPicker(
    ui: DevicesViewModel.UiState,
    onVolume: (String, Float) -> Unit,
    onClick: () -> Unit,
) {
    val activeOutput = ui.devices.singleOrNull { it.isActiveOutput }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (activeOutput != null) {
            DeviceVolumeControl(
                deviceLabel = activeOutput.name,
                volume = activeOutput.volume,
                enabled = ui.connected,
                onVolume = { onVolume(activeOutput.deviceId, it) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = BatonSpacing.Large),
                showIcons = false,
                showPercent = true,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Speaker,
                contentDescription = stringResource(R.string.devices_title),
                tint = if (ui.devices.any { it.isActiveOutput }) {
                    ActiveAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun ArtworkAndNowPlaying(ui: ConsoleViewModel.UiState, artworkSize: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TrackArtwork(
            url = ui.coverUrl,
            modifier = Modifier.size(artworkSize),
            corner = BatonSpacing.Medium,
            description = ui.nowPlaying?.effectiveTitle?.let {
                stringResource(R.string.console_artwork_description, it)
            },
        )
        Spacer(Modifier.height(20.dp))
        NowPlaying(ui.nowPlaying?.effectiveTitle, ui.nowPlaying?.artist)
    }
}

@Composable
private fun QueueList(
    ui: ConsoleViewModel.UiState,
    viewModel: ConsoleViewModel,
    modifier: Modifier,
    contentPadding: PaddingValues,
    beforeQueue: LazyListScope.() -> Unit = {},
) {
    QueueListContent(ui, remember(viewModel) {
        QueueCallbacks(
            onMove = viewModel::moveQueueItem,
            onJump = viewModel::jumpToQueue,
            onRemove = { viewModel.removeFromQueue(it) },
            onClear = { viewModel.clearQueue() },
            coverUrl = viewModel::coverUrl,
        )
    }, modifier, contentPadding, beforeQueue)
}

internal data class QueueCallbacks(
    val onMove: (Int, Int, List<Int>?) -> Unit,
    val onJump: (Int) -> Unit,
    val onRemove: (Int) -> Unit,
    val onClear: () -> Unit,
    val coverUrl: (Int) -> String?,
)

@Composable
internal fun QueueListContent(
    ui: ConsoleViewModel.UiState,
    actions: QueueCallbacks,
    modifier: Modifier,
    contentPadding: PaddingValues,
    beforeQueue: LazyListScope.() -> Unit = {},
) {
    val list = rememberLazyListState()
    val dragState = remember(list) { QueueDragState(list) }
    val queue = ui.queue.map { it.trackId }
    val latestQueue by rememberUpdatedState(queue)
    val connected by rememberUpdatedState(ui.connected)
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val drag = dragState.drag
    LaunchedEffect(queue, ui.connected) {
        if (!ui.connected || dragState.drag?.queue?.let { it != queue } == true) dragState.cancel()
    }
    LaunchedEffect(drag?.from) {
        var previousFrame = withFrameNanos { it }
        while (isActive && dragState.drag != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            previousFrame = frame
            val current = dragState.drag ?: break
            val layout = list.layoutInfo
            val speed = queueAutoScrollSpeed(
                current.top, current.top + current.height,
                layout.viewportStartOffset.toFloat(), layout.viewportEndOffset.toFloat(), edge,
            )
            if (speed != 0f) list.scrollBy(speed * seconds)
            dragState.updateTarget()
        }
    }
    Box(modifier.clip(RoundedCornerShape(0.dp))) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("queue_list").onGloballyPositioned { dragState.coordinates = it }
                .pointerInput(dragState, queue, ui.connected) {
                    if (!ui.connected) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val index = dragState.handleAt(down.position) ?: return@awaitEachGesture
                        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        if (!connected || !dragState.start(index, latestQueue)) return@awaitEachGesture
                        longPress.consume()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            var released = false
                            // Claim moves before LazyColumn's scroll detector once the handle is held.
                            // Normal list swipes never enter this loop and keep their native scrolling.
                            while (dragState.drag != null) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } > 1) break
                                val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                                if (change.isConsumed) break
                                if (!change.pressed) {
                                    change.consume()
                                    released = true
                                    break
                                }
                                dragState.move(change.positionChange().y)
                                change.consume()
                            }
                            val completed = dragState.drag
                            if (released) currentEvent.changes.forEach { it.consume() }
                            if (released && connected && completed != null && completed.queue == latestQueue) {
                                actions.onMove(completed.from, completed.target, completed.queue)
                            }
                        } finally {
                            dragState.cancel()
                        }
                    }
                },
            state = list,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            beforeQueue()
            queueContent(ui, actions, dragState)
        }
        drag?.let { active ->
            val entry = ui.queue.getOrNull(active.from)
            if (entry != null && queue == active.queue) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = BatonSpacing.Medium)
                        .graphicsLayer { translationY = active.top + list.layoutInfo.beforeContentPadding }
                        .testTag("queue_drag_preview")
                        .clearAndSetSemantics { },
                    shadowElevation = BatonSpacing.Small,
                    tonalElevation = 3.dp,
                ) {
                    TrackListItem(
                        entry.track?.effectiveTitle ?: stringResource(R.string.track_fallback, entry.trackId),
                        entry.track?.artist,
                        actions.coverUrl(entry.trackId),
                        trailingContent = { Icon(Icons.Filled.DragHandle, null, Modifier.padding(BatonSpacing.Medium)) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.queueContent(ui: ConsoleViewModel.UiState, actions: QueueCallbacks, dragState: QueueDragState) {
    if (ui.queue.isEmpty()) return
    item { QueueHeader(ui.queue.size, enabled = ui.connected, onClear = actions.onClear) }
    itemsIndexed(ui.queue, key = { index, entry -> "q:$index:${entry.trackId}" }) { index, entry ->
        QueueRow(
            entry = entry,
            index = index,
            queueSize = ui.queue.size,
            dragState = dragState,
            coverUrl = actions.coverUrl(entry.trackId),
            enabled = ui.connected,
            onPlay = { actions.onJump(index) },
            onMove = { from, to -> actions.onMove(from, to, null) },
            onRemove = { actions.onRemove(index) },
        )
    }
}

@Composable
private fun ConsoleControlBar(ui: ConsoleViewModel.UiState, viewModel: ConsoleViewModel) {
    Surface(tonalElevation = 3.dp, shadowElevation = BatonSpacing.Small) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BatonSpacing.Large, end = BatonSpacing.Large, top = 0.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // The seek block drops into the transport buttons' unused top padding.
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

/**
 * The output-device picker, pulled DOWN from the top (its trigger is the speaker icon
 * up there). A dimmed scrim — tap to dismiss — plus a full-width panel that slides in
 * from the top edge. Material3 has no top-sheet, so it's hand-rolled.
 */
@Composable
private fun DeviceTopSheet(
    visible: Boolean,
    viewModel: DevicesViewModel,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = visible) { onDismiss() }
    var dragOffset by remember(visible) { mutableFloatStateOf(0f) }
    val dismiss by rememberUpdatedState(onDismiss)
    val dismissThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val closeLabel = stringResource(R.string.devices_close)
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
                    .graphicsLayer { translationY = dragOffset }
                    .pointerInput(Unit) { detectTapGestures {} },
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp)
                            .pointerInput(dismissThreshold) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragOffset = 0f },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        dragOffset = (dragOffset + amount).coerceIn(-size.height * 3f, 0f)
                                    },
                                    onDragEnd = {
                                        if (dragOffset < -dismissThreshold) dismiss()
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { dragOffset = 0f },
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(48.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.DragHandle, contentDescription = null)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, closeLabel) }
                    }
                    Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        DevicePicker(viewModel)
                    }
                }
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
                    Spacer(Modifier.width(BatonSpacing.Small))
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
    dragState: QueueDragState,
    coverUrl: String?,
    enabled: Boolean,
    onPlay: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
) {
    val drag = dragState.drag
    val markerColor = MaterialTheme.colorScheme.primary
    DisposableEffect(index, dragState) {
        onDispose { dragState.handles.remove(index) }
    }
    val title = entry.track?.effectiveTitle ?: stringResource(R.string.track_fallback, entry.trackId)
    val playLabel = stringResource(R.string.console_play_queue, title)
    val reorderLabel = stringResource(R.string.console_reorder_queue, title)
    val moveUpLabel = stringResource(R.string.console_move_queue_up)
    val moveDownLabel = stringResource(R.string.console_move_queue_down)

    TrackListItem(
        title = title,
        artist = entry.track?.artist,
        artworkUrl = coverUrl,
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
                        .onGloballyPositioned { dragState.handles[index] = it },
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
            .graphicsLayer { alpha = if (drag?.from == index) 0.3f else 1f }
            .drawWithContent {
                drawContent()
                if (drag != null && drag.target == index && drag.target != drag.from) {
                    val y = if (drag.target > drag.from) size.height else 0f
                    drawLine(markerColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
                }
            }
            .clickable(enabled = enabled, onClickLabel = playLabel, onClick = onPlay),
    )
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** Landscape phones and large windows get independent now-playing and queue panes. */
internal fun useWideConsoleLayout(widthDp: Float, heightDp: Float): Boolean =
    widthDp >= 840f || (widthDp >= 600f && widthDp > heightDp)
