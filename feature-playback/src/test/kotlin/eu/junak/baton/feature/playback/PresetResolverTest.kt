package eu.junak.baton.feature.playback

import eu.junak.baton.core.network.api.PresetEffect
import eu.junak.baton.core.network.api.PresetManifest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetResolverTest {

    @Test
    fun `flattens manifests in active id and declaration order`() = runTest {
        val resolver = PresetResolver {
            listOf(
                manifest("first", "lowpass", "delay"),
                manifest("second", "tremolo", "reverb"),
            )
        }

        val effects = resolver.resolve(PresetSelection("dnd", listOf("second", "first"), 4))

        assertEquals(listOf("tremolo", "reverb", "lowpass", "delay"), effects.map { it.type })
    }

    @Test
    fun `caches one mode until preset revision changes`() = runTest {
        var loads = 0
        val resolver = PresetResolver {
            loads += 1
            listOf(manifest("cave", "reverb"))
        }
        val selection = PresetSelection("dnd", listOf("cave"), 7)

        resolver.resolve(selection)
        resolver.resolve(selection)
        resolver.resolve(selection.copy(presetRevision = 8))

        assertEquals(2, loads)
    }

    @Test
    fun `mode change and explicit clear invalidate the cache`() = runTest {
        var loads = 0
        val resolver = PresetResolver { mode ->
            loads += 1
            listOf(manifest("shared", if (mode == "dnd") "lowpass" else "highpass"))
        }

        val first = resolver.resolve(PresetSelection("dnd", listOf("shared"), 1))
        val second = resolver.resolve(PresetSelection("scifi", listOf("shared"), 1))
        resolver.clear()
        resolver.resolve(PresetSelection("scifi", listOf("shared"), 1))

        assertEquals("lowpass", first.single().type)
        assertEquals("highpass", second.single().type)
        assertEquals(3, loads)
    }

    @Test
    fun `empty selection stays dry without fetching`() = runTest {
        var loads = 0
        val resolver = PresetResolver {
            loads += 1
            emptyList()
        }

        assertEquals(emptyList<PresetEffect>(), resolver.resolve(PresetSelection("dnd", emptyList(), 1)))
        assertEquals(emptyList<PresetEffect>(), resolver.resolve(PresetSelection(null, listOf("cave"), 1)))
        assertEquals(0, loads)
    }

    private fun manifest(id: String, vararg types: String): PresetManifest = PresetManifest(
        id = id,
        name = id,
        effects = types.map { PresetEffect(it) },
    )
}
