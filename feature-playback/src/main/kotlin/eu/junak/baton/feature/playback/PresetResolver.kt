package eu.junak.baton.feature.playback

import eu.junak.baton.core.network.api.PresetEffect
import eu.junak.baton.core.network.api.PresetManifest

/** The canonical preset selection projected from one PlayerState snapshot. */
internal data class PresetSelection(
    val modeId: String?,
    val presetIds: List<String>,
    val presetRevision: Int,
)

/**
 * Resolves ordered active preset ids into the flattened effect rack consumed
 * by the audio processor. Manifests are mode-scoped and cached until the
 * server's dedicated preset revision changes.
 */
internal class PresetResolver(
    private val loadPresets: suspend (String) -> List<PresetManifest>,
) {
    private var cachedModeId: String? = null
    private var cachedRevision: Int = -1
    private var cachedById: Map<String, PresetManifest> = emptyMap()

    suspend fun resolve(selection: PresetSelection): List<PresetEffect> {
        val modeId = selection.modeId
        if (modeId == null || selection.presetIds.isEmpty()) return emptyList()

        val needsRefresh =
            cachedModeId != modeId ||
                cachedRevision != selection.presetRevision ||
                selection.presetIds.any { it !in cachedById }
        if (needsRefresh) {
            cachedById = loadPresets(modeId).associateBy(PresetManifest::id)
            cachedModeId = modeId
            cachedRevision = selection.presetRevision
        }

        return selection.presetIds.flatMap { cachedById[it]?.effects.orEmpty() }
    }

    fun clear() {
        cachedModeId = null
        cachedRevision = -1
        cachedById = emptyMap()
    }
}
