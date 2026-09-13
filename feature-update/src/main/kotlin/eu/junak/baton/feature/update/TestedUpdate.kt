package eu.junak.baton.feature.update

import java.io.File
import java.net.URI
import java.security.MessageDigest

internal data class TestedUpdate(
    val label: String, val versionCode: Long, val url: String, val sha256: String, val size: Long,
)

/** Version names are display labels; CI's increasing Android code decides availability. */
internal fun testedUpdate(release: GitHubRelease, repo: String, currentCode: Long): TestedUpdate? {
    val tag = Regex("v([0-9]+\\.[0-9]+\\.[0-9]+)-commit\\.([0-9a-f]{40})")
        .matchEntire(release.tagName) ?: error("Missing tested commit")
    val asset = release.assets.filter { it.name.endsWith(".apk") }.single()
    val name = Regex("baton-([0-9]+)-([0-9a-f]{40})\\.apk").matchEntire(asset.name)
        ?: error("Invalid tested package name")
    val code = name.groupValues[1].toLong()
    require(code in 1..2_100_000_000L && name.groupValues[2] == tag.groupValues[2])
    require(asset.size in 1..MAX_APK_BYTES)
    val digest = asset.digest ?: error("Missing package digest")
    require(Regex("sha256:[0-9a-f]{64}").matches(digest))
    val url = URI(asset.browserDownloadUrl)
    require(url.scheme == "https" && url.host == "github.com" && url.port == -1 && url.userInfo == null)
    require(url.query == null && url.fragment == null)
    require(url.path == "/$repo/releases/download/${release.tagName}/${asset.name}")
    if (code <= currentCode) return null
    return TestedUpdate("${tag.groupValues[1]} · ${tag.groupValues[2].take(7)}", code,
        asset.browserDownloadUrl, digest.removePrefix("sha256:"), asset.size)
}

internal fun verifyPackageBytes(file: File, expectedHash: String, expectedSize: Long) {
    require(file.length() == expectedSize && expectedSize in 1..MAX_APK_BYTES)
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    require(actual == expectedHash) { "Package checksum mismatch" }
}

private const val MAX_APK_BYTES = 250L * 1024 * 1024
