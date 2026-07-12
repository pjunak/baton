package eu.junak.baton.feature.update

/**
 * Numeric dotted-version compare ("1.2.3" > "1.2" > "1.1.9"). Non-numeric
 * segments are ignored, so "1.2.3-rc1" compares as 1.2.3 — good enough for
 * tags CI derives from `vX.Y.Z`. Top-level (not an [Updater] method) so it's
 * unit-testable without standing up the Hilt graph.
 */
internal fun isNewerVersion(latest: String, current: String): Boolean {
    val l = versionParts(latest)
    val c = versionParts(current)
    for (i in 0 until maxOf(l.size, c.size)) {
        val lv = l.getOrElse(i) { 0 }
        val cv = c.getOrElse(i) { 0 }
        if (lv != cv) return lv > cv
    }
    return false
}

private fun versionParts(v: String): List<Int> =
    v.trim().removePrefix("v").split(".", "-", "+").mapNotNull { it.toIntOrNull() }
