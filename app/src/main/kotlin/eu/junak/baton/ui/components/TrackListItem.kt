package eu.junak.baton.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.junak.baton.R

/** Shared artwork/title/artist row used anywhere a track appears in a list. */
@Composable
fun TrackListItem(
    title: String,
    artist: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        leadingContent = { TrackArtwork(artworkUrl, Modifier.size(44.dp), corner = 6.dp) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                artist?.ifBlank { stringResource(R.string.unknown_artist) }
                    ?: stringResource(R.string.unknown_artist),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
    )
}
