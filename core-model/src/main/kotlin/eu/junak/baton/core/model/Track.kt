package eu.junak.baton.core.model

import kotlinx.serialization.Serializable

/**
 * Track metadata, mirrored from the backend's `TrackOut` (library API). Returned
 * by `GET /api/library/tracks/{id}`, `/search`, `/tree`, and the batch endpoint
 * `GET /api/library/tracks?ids=…` used to resolve queue/history id lists.
 *
 * `addedAt` is kept as the raw ISO-8601 string the server emits rather than a
 * parsed instant — the UI doesn't need it parsed in v1, and this avoids coupling
 * to the server's exact datetime serialization. Parse lazily if a feature needs it.
 */
@Serializable
data class Track(
    val id: Int,
    val path: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val year: Int? = null,
    val genre: String = "",
    val lengthS: Double = 0.0,
    val bpm: Int? = null,
    val sizeBytes: Long = 0,
    val addedAt: String,
    val displayTitle: String = "",
    val origin: String = "",
) {
    /** Best display name: the user-set `displayTitle` if present, else `title`. */
    val effectiveTitle: String get() = displayTitle.ifBlank { title }
}
