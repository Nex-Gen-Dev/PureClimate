package com.midea.pureclimate.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A published build, read from the GitHub "latest release" API. */
data class ReleaseInfo(
    val buildNumber: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
    val publishedAt: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: ReleaseInfo?) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Checks GitHub Releases for a newer PureClimate build, downloads the APK and
 * hands it to Android's installer. Builds are numbered by CI (tag "build-N",
 * versionCode N), so "newer" is simply a higher number.
 */
class UpdateManager(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("pure_climate_updates", Context.MODE_PRIVATE)
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val currentBuild: Int = runCatching {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)

    val currentVersionName: String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull() ?: "?"

    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(v) { prefs.edit().putBoolean(KEY_AUTO, v).apply() }

    val lastCheckMillis: Long get() = prefs.getLong(KEY_LAST_CHECK, 0L)

    /** Build the user chose "Later" for; we don't nag again for that same build this session. */
    var snoozedBuild: Int = 0
        private set

    fun snooze(build: Int) {
        snoozedBuild = build
    }

    /** Called on app start. */
    fun autoCheckIfDue() {
        if (!autoCheck) return
        check()
    }

    fun check() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        job?.cancel()
        _state.value = UpdateState.Checking
        job = scope.launch(Dispatchers.IO) {
            _state.value = try {
                val latest = fetchLatest()
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                when {
                    latest == null -> UpdateState.UpToDate(null)
                    latest.buildNumber > currentBuild -> {
                        val cached = apkFile(latest)
                        if (cached.exists() && cached.length() == latest.apkSize) UpdateState.ReadyToInstall(latest, cached)
                        else UpdateState.Available(latest)
                    }
                    else -> UpdateState.UpToDate(latest)
                }
            } catch (e: Exception) {
                Log.w(TAG, "update check failed", e)
                UpdateState.Failed(e.message ?: "Could not check for updates")
            }
        }
    }

    fun download(release: ReleaseInfo) {
        if (_state.value is UpdateState.Downloading) return
        job?.cancel()
        _state.value = UpdateState.Downloading(release, 0f)
        job = scope.launch(Dispatchers.IO) {
            _state.value = try {
                val target = apkFile(release)
                downloadTo(release, target)
                UpdateState.ReadyToInstall(release, target)
            } catch (e: Exception) {
                Log.w(TAG, "download failed", e)
                UpdateState.Failed("Download failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** True if Android will let us open the installer; otherwise the user must allow it once. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < 26 || app.packageManager.canRequestPackageInstalls()

    /** Opens "Install unknown apps" for PureClimate. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
        context.startActivity(intent)
    }

    /** Hands the downloaded APK to the system installer (shows Android's install prompt). */
    fun install(context: Context, file: File): Boolean {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Failed("No installer found on this phone")
            false
        }
    }

    // ---- network --------------------------------------------------------------------

    private fun fetchLatest(): ReleaseInfo? {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PureClimate-Updater")
        }
        try {
            when (val code = conn.responseCode) {
                200 -> Unit
                404 -> throw IllegalStateException(
                    "No public releases found at github.com/$REPO. If the repo is private, the phone can't see its releases.",
                )
                403, 429 -> throw IllegalStateException("GitHub is rate-limiting checks. Try again in a few minutes.")
                else -> throw IllegalStateException("GitHub answered HTTP $code")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return parseRelease(json)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo? {
        val tag = json.optString("tag_name")
        val build = TAG_NUMBER.find(tag)?.value?.toIntOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return ReleaseInfo(
                    buildNumber = build,
                    versionName = json.optString("name").ifBlank { tag },
                    notes = json.optString("body").trim(),
                    apkUrl = a.getString("browser_download_url"),
                    apkSize = a.optLong("size", -1L),
                    pageUrl = json.optString("html_url"),
                    publishedAt = json.optString("published_at"),
                )
            }
        }
        return null
    }

    private fun downloadTo(release: ReleaseInfo, target: File) {
        target.parentFile?.mkdirs()
        // Remove older downloads so the cache doesn't grow.
        target.parentFile?.listFiles()?.filter { it != target }?.forEach { it.delete() }
        val tmp = File(target.parentFile, target.name + ".part")
        var url = URL(release.apkUrl)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "PureClimate-Updater")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                url = URL(url, loc)
                redirects++
                continue
            }
            if (code != 200) {
                conn.disconnect()
                throw IllegalStateException("HTTP $code")
            }
            break
        }
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastEmit > 128 * 1024) {
                            lastEmit = done
                            _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (release.apkSize > 0 && tmp.length() != release.apkSize) {
            tmp.delete()
            throw IllegalStateException("incomplete download")
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) throw IllegalStateException("could not save the update")
    }

    private fun apkFile(release: ReleaseInfo) = File(File(app.cacheDir, "updates"), "PureClimate-${release.buildNumber}.apk")

    companion object {
        private const val TAG = "UpdateManager"
        /** Where releases are published (see .github/workflows/build.yml). */
        const val REPO = "Nex-Gen-Dev/PureClimate"
        private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
        private val TAG_NUMBER = Regex("\\d+")
        private const val KEY_AUTO = "auto_check"
        private const val KEY_LAST_CHECK = "last_check"
    }
}
