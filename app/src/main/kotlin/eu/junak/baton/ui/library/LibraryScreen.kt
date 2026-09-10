package eu.junak.baton.ui.library

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.R
import eu.junak.baton.core.model.Track
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.ui.components.SectionHeader
import eu.junak.baton.ui.components.TrackListItem
import eu.junak.baton.ui.theme.BatonSpacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun LibraryScreen(
    onSelectOutput: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val connected = connection == ConnectionStatus.CONNECTED
    val sendFailedText by rememberUpdatedState(stringResource(R.string.library_send_failed))
    val queueRequestedText by rememberUpdatedState(stringResource(R.string.library_queue_requested))
    val selectOutput by rememberUpdatedState(onSelectOutput)
    val message by rememberUpdatedState(onMessage)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LibraryEvent.SELECT_OUTPUT -> selectOutput()
                LibraryEvent.SEND_FAILED -> message(sendFailedText)
                // Socket acceptance is not a server acknowledgement; the copy reflects that boundary.
                LibraryEvent.QUEUE_REQUESTED -> message(queueRequestedText)
            }
        }
    }
    LibraryScreenContent(ui, connected, remember(viewModel) {
        LibraryCallbacks(
            onBack = viewModel::back,
            onUp = viewModel::goUp,
            onAncestor = viewModel::openAncestor,
            onFolder = viewModel::openFolder,
            onQuery = viewModel::onQueryChange,
            onRefresh = viewModel::refresh,
            onPlayFolder = { viewModel.playCurrentFolder() },
            onPlay = { viewModel.playTrack(it) },
            onEnqueue = viewModel::enqueue,
            onInterrupt = { viewModel.playInterrupt(it) },
            onContainingFolder = viewModel::openContainingFolder,
            onScroll = viewModel::rememberScroll,
            coverUrl = viewModel::coverUrl,
        )
    })
}

internal data class LibraryCallbacks(
    val onBack: () -> Unit,
    val onUp: () -> Unit,
    val onAncestor: (String) -> Unit,
    val onFolder: (String) -> Unit,
    val onQuery: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onPlayFolder: () -> Unit,
    val onPlay: (Track) -> Unit,
    val onEnqueue: (Track) -> Unit,
    val onInterrupt: (Track) -> Unit,
    val onContainingFolder: (Track) -> Unit,
    val onScroll: (String, Int, Int) -> Unit,
    val coverUrl: (Int) -> String?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LibraryScreenContent(ui: LibraryBrowserState, connected: Boolean, actions: LibraryCallbacks) {
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backDirection by remember { mutableFloatStateOf(1f) }
    val currentLocation by rememberUpdatedState(ui.location.key)
    PredictiveBackHandler(enabled = ui.backTarget != null && !imeVisible && menuTrack == null) { events ->
        val origin = currentLocation
        try {
            events.collect { event ->
                backProgress = event.progress
                backDirection = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            }
            if (currentLocation == origin) {
                focus.clearFocus()
                actions.onBack()
            }
        } finally {
            // A cancelled gesture never changes the folder, search, or scroll position.
            backProgress = 0f
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (backProgress > 0f) {
            val target = ui.backTarget
            val label = when {
                target?.searching == true -> stringResource(R.string.library_search_return, target.query)
                target?.path.isNullOrEmpty() -> stringResource(R.string.tab_library)
                else -> target.path.substringAfterLast('/')
            }
            Text(
                stringResource(R.string.library_back_to, label),
                Modifier.align(Alignment.TopCenter).padding(BatonSpacing.Medium),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Column(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = size.width * 0.08f * backProgress * backDirection
                translationY = 56.dp.toPx() * backProgress
                scaleX = 1f - 0.06f * backProgress
                scaleY = scaleX
            }.background(MaterialTheme.colorScheme.background),
        ) {
            OutlinedTextField(
                value = ui.location.query,
                onValueChange = actions.onQuery,
                label = { Text(stringResource(R.string.library_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (ui.location.searching) IconButton(onClick = {
                        focus.clearFocus()
                        actions.onQuery("")
                    }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.library_clear_search))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = BatonSpacing.Medium, vertical = BatonSpacing.Small),
            )
            LibraryNavigationBar(ui, onAncestor = {
                focus.clearFocus()
                actions.onAncestor(it)
            }, onUp = { focus.clearFocus(); actions.onUp() }, onRefresh = actions.onRefresh)
            if (!connected) {
                Text(
                    stringResource(R.string.connection_reconnecting),
                    Modifier.padding(horizontal = BatonSpacing.Medium, vertical = BatonSpacing.Small),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!ui.location.searching && ui.content != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = BatonSpacing.Medium),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(
                        onClick = { actions.onPlayFolder() },
                        enabled = connected && (ui.content!!.folders.isNotEmpty() || ui.content!!.tracks.isNotEmpty()),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Text(stringResource(R.string.library_play_folder), Modifier.padding(start = BatonSpacing.Small))
                    }
                }
            }
            if (ui.failed) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = BatonSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (ui.location.searching) R.string.library_search_error else R.string.library_load_error),
                        Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = actions.onRefresh) { Text(stringResource(R.string.action_retry)) }
                }
            }
            PullToRefreshBox(
                isRefreshing = ui.loading && ui.content != null,
                onRefresh = { if (!ui.loading) actions.onRefresh() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    ui.content != null -> key(ui.location.key) {
                        LibraryList(ui.location, ui.content!!, connected, actions) { focus.clearFocus(); menuTrack = it }
                    }
                    ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> LazyColumn(Modifier.fillMaxSize()) { item { } }
                }
            }
        }
    }
    menuTrack?.let { track ->
        ModalBottomSheet(
            onDismissRequest = { menuTrack = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(track.effectiveTitle, Modifier.padding(horizontal = BatonSpacing.Large), style = MaterialTheme.typography.titleMedium)
            Text(
                track.path, Modifier.padding(horizontal = BatonSpacing.Large, vertical = BatonSpacing.Small),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_add_queue)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                modifier = Modifier.clickable(enabled = connected) { menuTrack = null; actions.onEnqueue(track) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_play_interrupt)) },
                leadingContent = { Icon(Icons.Filled.Bolt, null) },
                modifier = Modifier.clickable(enabled = connected) { menuTrack = null; actions.onInterrupt(track) },
            )
            if (ui.location.searching) ListItem(
                headlineContent = { Text(stringResource(R.string.library_open_folder)) },
                leadingContent = { Icon(Icons.Filled.FolderOpen, null) },
                modifier = Modifier.clickable {
                    menuTrack = null
                    focus.clearFocus()
                    actions.onContainingFolder(track)
                },
            )
            TextButton(onClick = { menuTrack = null }, modifier = Modifier.align(Alignment.End).padding(BatonSpacing.Small)) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

@Composable
private fun LibraryNavigationBar(ui: LibraryBrowserState, onAncestor: (String) -> Unit, onUp: () -> Unit, onRefresh: () -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(ui.location.path, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Surface(tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (ui.location.path.isNotEmpty()) IconButton(onClick = onUp) {
                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.library_up))
            }
            Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAncestor("") }, enabled = ui.location.path.isNotEmpty() || ui.location.searching) {
                    Text(stringResource(R.string.tab_library))
                }
                val parts = ui.location.path.split('/').filter { it.isNotEmpty() }
                parts.forEachIndexed { index, part ->
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        onClick = { onAncestor(parts.take(index + 1).joinToString("/")) },
                        enabled = index < parts.lastIndex || ui.location.searching,
                    ) { Text(part, maxLines = 1) }
                }
            }
            IconButton(onClick = onRefresh, enabled = !ui.loading) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
            }
        }
    }
}

@Composable
private fun LibraryList(
    location: LibraryLocation,
    content: LibraryContent,
    connected: Boolean,
    actions: LibraryCallbacks,
    onOptions: (Track) -> Unit,
) {
    val listState = rememberLazyListState(location.firstVisibleItem, location.scrollOffset)
    LaunchedEffect(listState, location.key) {
        snapshotFlow {
            if (!listState.isScrollInProgress && listState.layoutInfo.totalItemsCount > 0) {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            } else null
        }.filterNotNull().distinctUntilChanged().collect { (index, offset) ->
            actions.onScroll(location.key, index, offset)
        }
    }
    DisposableEffect(listState, location.key) {
        onDispose {
            if (listState.layoutInfo.totalItemsCount > 0) {
                actions.onScroll(location.key, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("library_list"), state = listState, contentPadding = PaddingValues(bottom = BatonSpacing.Small)) {
        if (content.folders.isNotEmpty()) {
            item(key = "folders") { SectionHeader(stringResource(R.string.library_folders), Modifier.padding(BatonSpacing.Medium)) }
            items(content.folders, key = { "f:${it.path}" }) { folder ->
                ListItem(
                    headlineContent = { Text(folder.name) },
                    supportingContent = { Text(pluralStringResource(R.plurals.library_track_count, folder.trackCount, folder.trackCount)) },
                    leadingContent = { Icon(Icons.Filled.Folder, null) },
                    modifier = Modifier.clickable { actions.onFolder(folder.path) },
                )
            }
        }
        if (content.tracks.isNotEmpty() && !location.searching) {
            item(key = "tracks") { SectionHeader(stringResource(R.string.library_tracks), Modifier.padding(BatonSpacing.Medium)) }
        }
        items(content.tracks, key = { "t:${it.id}" }) { track ->
            TrackRow(
                track, actions.coverUrl(track.id), connected,
                onPlay = { actions.onPlay(track) },
                onEnqueue = { actions.onEnqueue(track) },
                onOptions = { onOptions(track) },
                showPath = location.searching,
            )
        }
        if (content.folders.isEmpty() && content.tracks.isEmpty()) item(key = "empty") {
            Text(
                stringResource(if (location.searching) R.string.library_no_matches else R.string.library_folder_empty),
                Modifier.padding(BatonSpacing.Large), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRow(
    track: Track,
    coverUrl: String?,
    enabled: Boolean,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onOptions: () -> Unit,
    showPath: Boolean,
) {
    val playLabel = stringResource(R.string.library_play_track, track.effectiveTitle)
    val optionsLabel = stringResource(R.string.library_track_options, track.effectiveTitle)
    val enqueueLabel = stringResource(R.string.library_add_queue)
    val haptics = LocalHapticFeedback.current
    val enqueue by rememberUpdatedState(onEnqueue)
    val actionsEnabled by rememberUpdatedState(enabled)
    // Gesture state is intentionally not saved: restoring a settled swipe must never replay an action.
    val swipe = remember {
        SwipeToDismissBoxState(initialValue = SwipeToDismissBoxValue.Settled, positionalThreshold = { width -> width * 0.35f })
    }
    LaunchedEffect(swipe) {
        snapshotFlow { swipe.settledValue }.collect { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) {
                if (actionsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    enqueue()
                }
                swipe.reset()
            }
        }
    }
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        gesturesEnabled = enabled,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clearAndSetSemantics { }
                    .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = BatonSpacing.Large),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End,
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                Text(enqueueLabel, Modifier.padding(start = BatonSpacing.Small))
            }
        },
    ) {
        TrackListItem(
            title = track.effectiveTitle,
            artist = if (showPath) parentLibraryPath(track.path).ifEmpty { stringResource(R.string.tab_library) } else track.artist,
            artworkUrl = coverUrl,
            trailingContent = {
                Row {
                    IconButton(onClick = onEnqueue, enabled = enabled) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, enqueueLabel) }
                    IconButton(onClick = onOptions) { Icon(Icons.Filled.MoreVert, optionsLabel) }
                }
            },
            modifier = Modifier.combinedClickable(
                enabled = enabled,
                onClickLabel = playLabel,
                onClick = { if (enabled) onPlay() },
                onLongClickLabel = optionsLabel,
                onLongClick = onOptions,
            ).semantics {
                customActions = if (enabled) listOf(CustomAccessibilityAction(enqueueLabel) { onEnqueue(); true }) else emptyList()
            },
        )
    }
}
