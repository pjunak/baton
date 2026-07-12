package eu.junak.baton.feature.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Where the self-update flow currently is, for the Settings UI to render. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val notes: String?, val downloadUrl: String) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class ReadyToInstall(val apk: File, val version: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * In-app self-update: checks the configured GitHub repo's latest release
 * ([BuildConfig.UPDATE_REPO]) and, if its version is newer than the installed one,
 * downloads the APK asset and hands it to the system installer. The user's server is
 * never involved — this only talks to api.github.com. Downloads run on the updater's
 * own scope, so navigating away from Settings doesn't cancel them.
 */
@Singleton
class Updater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gitHubApi: GitHubApi,
    @param:Named("github") private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var launchCheckDone = false

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Query the latest release and compare versions. Result lands in [state]. */
    fun check() {
        _state.value = UpdateState.Checking
        scope.launch { _state.value = resolveLatest() }
    }

    /**
     * The silent launch-time variant: only runs from [UpdateState.Idle] (never
     * stomps an in-flight check/download), and only ever *publishes*
     * [UpdateState.Available] — an unreachable GitHub or an up-to-date install
     * stays quietly Idle instead of greeting the user with a status they
     * didn't ask about. What Available enables: the Settings-tab badge and the
     * pre-populated update card when they get there.
     */
    fun checkOnLaunch() {
        // Once per process: onCreate re-runs on every rotation/theme change,
        // and each launch check is a real api.github.com call.
        if (launchCheckDone) return
        launchCheckDone = true
        if (_state.value != UpdateState.Idle) return
        scope.launch {
            val result = resolveLatest()
            if (result is UpdateState.Available && _state.value == UpdateState.Idle) {
                _state.value = result
            }
        }
    }

    private suspend fun resolveLatest(): UpdateState {
        val repo = repoParts() ?: return UpdateState.Error("Update source is misconfigured.")
        val release = runCatching { gitHubApi.latestRelease(repo.first, repo.second) }.getOrElse { e ->
            // A 404 from /releases/latest just means no full release is published yet — not an error.
            return if (e is HttpException && e.code() == 404) {
                UpdateState.UpToDate
            } else {
                UpdateState.Error("Couldn't check GitHub — ${e.message ?: "network error"}")
            }
        }
        val latest = release.tagName.removePrefix("v").trim()
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        return when {
            apk == null -> UpdateState.Error("The latest release has no APK attached.")
            isNewerVersion(latest, currentVersion()) ->
                UpdateState.Available(latest, release.body?.takeIf { it.isNotBlank() }, apk.browserDownloadUrl)
            else -> UpdateState.UpToDate
        }
    }

    /** Download the APK for an [available] update, then launch the installer. */
    fun download(available: UpdateState.Available) {
        _state.value = UpdateState.Downloading(0f)
        scope.launch {
            val dest = File(context.cacheDir, "updates/baton-${available.version}.apk")
            // Evict APKs from previous updates — they'd otherwise pile up in
            // the cache dir forever (one per release ever installed).
            dest.parentFile?.listFiles()?.forEach { if (it != dest) it.delete() }
            runCatching { downloadTo(available.downloadUrl, dest) { p -> _state.value = UpdateState.Downloading(p) } }
                .onSuccess {
                    _state.value = UpdateState.ReadyToInstall(dest, available.version)
                    install(dest)
                }
                .onFailure { _state.value = UpdateState.Error("Download failed — ${it.message}") }
        }
    }

    /**
     * Launch the system installer for [apk]. If this app can't yet install packages,
     * send the user to grant that one-time permission first — the UI keeps the Install
     * button so they can tap it again on return.
     */
    fun install(apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { _state.value = UpdateState.Error("Couldn't open the installer — ${it.message}") }
    }

    /** Reset back to the idle "Check for updates" affordance. */
    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private suspend fun downloadTo(url: String, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }

    private fun repoParts(): Pair<String, String>? =
        BuildConfig.UPDATE_REPO.split("/").takeIf { it.size == 2 }?.let { it[0] to it[1] }

    private fun currentVersion(): String {
        val info: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName ?: "0"
    }

    private companion object {
        const val DOWNLOAD_BUFFER = 64 * 1024
    }
}
