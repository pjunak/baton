package eu.junak.baton.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.baton.core.model.Track

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search library") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            ui.loading -> Centered { CircularProgressIndicator() }

            ui.error != null -> Centered {
                Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            ui.searchResults != null -> {
                val results = ui.searchResults.orEmpty()
                if (results.isEmpty()) {
                    Centered { Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { "s:${it.id}" }) { track ->
                            TrackRow(track, { viewModel.playTrack(track) }, { viewModel.enqueue(track) })
                        }
                    }
                }
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (ui.path.isNotEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text("Up") },
                            supportingContent = { Text("/${ui.path}") },
                            leadingContent = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                            modifier = Modifier.clickable { viewModel.goUp() },
                        )
                    }
                }
                if (ui.folders.isNotEmpty() || ui.tracks.isNotEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text("Play this folder") },
                            leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                            modifier = Modifier.clickable { viewModel.playCurrentFolder() },
                        )
                    }
                }
                items(ui.folders, key = { "f:${it.path}" }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        supportingContent = { Text("${folder.trackCount} tracks") },
                        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.openFolder(folder) },
                    )
                }
                items(ui.tracks, key = { "t:${it.id}" }) { track ->
                    TrackRow(track, { viewModel.playTrack(track) }, { viewModel.enqueue(track) })
                }
                if (ui.folders.isEmpty() && ui.tracks.isEmpty()) {
                    item {
                        Text(
                            text = "This folder is empty",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onPlay: () -> Unit, onEnqueue: () -> Unit) {
    ListItem(
        headlineContent = { Text(track.effectiveTitle) },
        supportingContent = { Text(track.artist.ifBlank { "Unknown artist" }) },
        leadingContent = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onEnqueue) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to queue")
            }
        },
        modifier = Modifier.clickable(onClick = onPlay),
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
