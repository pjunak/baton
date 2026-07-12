package eu.junak.baton.feature.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `newer patch, minor, and major all win`() {
        assertTrue(isNewerVersion("0.2.1", "0.2.0"))
        assertTrue(isNewerVersion("0.3.0", "0.2.9"))
        assertTrue(isNewerVersion("1.0.0", "0.9.9"))
    }

    @Test
    fun `equal and older are not newer`() {
        assertFalse(isNewerVersion("0.2.0", "0.2.0"))
        assertFalse(isNewerVersion("0.1.9", "0.2.0"))
        assertFalse(isNewerVersion("0.2.0", "1.0.0"))
    }

    @Test
    fun `numeric compare, not lexicographic`() {
        assertTrue(isNewerVersion("0.10.0", "0.9.0"))
        assertFalse(isNewerVersion("0.9.0", "0.10.0"))
    }

    @Test
    fun `missing segments read as zero`() {
        assertFalse(isNewerVersion("1.2", "1.2.0"))
        assertFalse(isNewerVersion("1.2.0", "1.2"))
        assertTrue(isNewerVersion("1.2.1", "1.2"))
    }

    @Test
    fun `v prefix and whitespace are tolerated`() {
        assertTrue(isNewerVersion("v0.2.0", "0.1.0"))
        assertTrue(isNewerVersion(" 0.2.0 ", "v0.1.0"))
    }

    @Test
    fun `non-numeric suffixes are ignored rather than crashing`() {
        // "-rc1" drops (not an int) — 1.2.3-rc1 compares as 1.2.3. Acceptable
        // for CI-derived vX.Y.Z tags; pinned so a change here is a decision.
        assertTrue(isNewerVersion("1.2.3-rc1", "1.2.2"))
        assertFalse(isNewerVersion("1.2.3-rc1", "1.2.3"))
    }

    @Test
    fun `garbage never reads as an update`() {
        assertFalse(isNewerVersion("", "0.1.0"))
        assertFalse(isNewerVersion("not-a-version", "0.1.0"))
    }
}
