package eu.junak.baton.feature.update

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TestedUpdateTest {
    private val sha = "a".repeat(40)
    private val repo = "owner/baton"
    private fun release(code: Long = 100123, version: String = "0.3.7"): GitHubRelease {
        val tag = "v$version-commit.$sha"
        val asset = "baton-$code-$sha.apk"
        return GitHubRelease(tag, assets = listOf(GitHubAsset(asset,
            "https://github.com/$repo/releases/download/$tag/$asset", 3,
            digest = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")))
    }

    @Test fun unchangedVersionNameStillOffersNewerTestedBuild() {
        assertEquals(100123L, testedUpdate(release(), repo, 100122)?.versionCode)
        assertNull(testedUpdate(release(), repo, 100123))
        assertNull(testedUpdate(release(), repo, 100124))
        assertNotNull(testedUpdate(release(version = "0.0.1"), repo, 306))
    }

    @Test fun mismatchedCommitAmbiguousApksAndUnverifiedPackagesAreRejected() {
        val base = release()
        val bad = listOf(
            base.copy(tagName = "v0.3.7-commit.${"b".repeat(40)}"),
            base.copy(assets = base.assets + base.assets),
            base.copy(assets = base.assets.map { it.copy(digest = null) }),
            base.copy(assets = base.assets.map { it.copy(size = Long.MAX_VALUE) }),
            base.copy(assets = base.assets.map { it.copy(browserDownloadUrl = "https://example.com/app.apk") }),
            base.copy(tagName = "v0.3.6"),
            release(code = 2_100_000_001L),
        )
        bad.forEach { value -> assertThrows(Exception::class.java) { testedUpdate(value, repo, 1) } }
        assertThrows(Exception::class.java) { testedUpdate(base, "another/repo", 1) }
    }

    @Test fun corruptedOrTruncatedDownloadsNeverReachInstaller() {
        val file = File.createTempFile("baton-package-test", ".apk")
        try {
            file.writeText("abc")
            val update = testedUpdate(release(), repo, 1)!!
            verifyPackageBytes(file, update.sha256, update.size)
            file.writeText("abd")
            assertThrows(IllegalArgumentException::class.java) { verifyPackageBytes(file, update.sha256, 3) }
            file.writeText("ab")
            assertThrows(IllegalArgumentException::class.java) { verifyPackageBytes(file, update.sha256, 3) }
        } finally { file.delete() }
    }
}
