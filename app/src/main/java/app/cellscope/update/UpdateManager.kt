package app.cellscope.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cellscope.BuildConfig
import app.cellscope.MainActivity
import app.cellscope.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Current : UpdateState
    data class Downloading(val version: String) : UpdateState
    data class Ready(val version: String, val apk: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateManager(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val checkMutex = Mutex()
    private val _state = MutableStateFlow(restoredState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun start() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build(),
        )
        enqueueCheck(force = false)
    }

    fun checkNow() = enqueueCheck(force = true)

    fun installIntent(apk: File): Intent {
        require(isManagedApk(apk)) { "Update APK is outside CellScope's update directory" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    internal suspend fun performCheck(force: Boolean): ListenableWorker.Result = checkMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!force && System.currentTimeMillis() - preferences.getLong(LAST_CHECKED_AT, 0L) < CHECK_INTERVAL_MS) {
                return@withContext ListenableWorker.Result.success()
            }

            _state.value = UpdateState.Checking
            try {
                val release = fetchLatestRelease()
                preferences.edit().putLong(LAST_CHECKED_AT, System.currentTimeMillis()).apply()
                if (!VersionNumbers.isNewer(BuildConfig.VERSION_NAME, release.version)) {
                    clearDownloadedUpdate()
                    _state.value = UpdateState.Current
                    return@withContext ListenableWorker.Result.success()
                }

                _state.value = UpdateState.Downloading(release.version)
                val apk = downloadAndVerify(release)
                verifyApkIdentity(apk)
                preferences.edit()
                    .putString(READY_VERSION, release.version)
                    .putString(READY_APK, apk.absolutePath)
                    .apply()
                _state.value = UpdateState.Ready(release.version, apk)
                showReadyNotification(release.version)
                ListenableWorker.Result.success()
            } catch (error: Exception) {
                _state.value = UpdateState.Error(error.message ?: "Update check failed")
                if (force) {
                    ListenableWorker.Result.failure(Data.Builder().putString("error", error.message).build())
                } else {
                    ListenableWorker.Result.retry()
                }
            }
        }
    }

    private fun enqueueCheck(force: Boolean) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putBoolean(UpdateWorker.FORCE, force).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            if (force) MANUAL_WORK else STARTUP_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun restoredState(): UpdateState {
        val version = preferences.getString(READY_VERSION, null) ?: return UpdateState.Idle
        val path = preferences.getString(READY_APK, null) ?: return UpdateState.Idle
        val apk = File(path)
        if (VersionNumbers.isNewer(BuildConfig.VERSION_NAME, version) && apk.isFile && isManagedApk(apk)) {
            return UpdateState.Ready(version, apk)
        }
        preferences.edit().remove(READY_VERSION).remove(READY_APK).apply()
        updateDirectory().listFiles()?.forEach(File::delete)
        return UpdateState.Idle
    }

    private fun fetchLatestRelease(): Release {
        val json = JSONObject(readUrl(LATEST_RELEASE, MAX_METADATA_BYTES).decodeToString())
        val version = json.getString("tag_name").removePrefix("v")
        val assets = json.getJSONArray("assets")
        var apkName: String? = null
        var apkUrl: String? = null
        val urls = mutableMapOf<String, String>()
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.getString("name")
            val url = asset.getString("browser_download_url")
            urls[name] = url
            if (name == "CellScope-v$version.apk") {
                apkName = name
                apkUrl = url
            }
        }
        val selectedName = apkName ?: error("Latest release has no APK")
        return Release(
            version = version,
            apkName = selectedName,
            apkUrl = apkUrl ?: error("Latest release has no APK URL"),
            checksumUrl = urls["$selectedName.sha256"] ?: error("Latest release has no SHA-256 sidecar"),
        )
    }

    private fun downloadAndVerify(release: Release): File {
        val expected = readUrl(release.checksumUrl, MAX_CHECKSUM_BYTES).decodeToString()
            .trim().substringBefore(' ').lowercase()
        require(expected.matches(Regex("[0-9a-f]{64}"))) { "Release checksum is invalid" }

        val directory = updateDirectory().apply { mkdirs() }
        val temporary = File(directory, "${release.apkName}.part")
        val destination = File(directory, release.apkName)
        temporary.delete()
        destination.delete()
        try {
            downloadUrl(release.apkUrl, temporary, MAX_APK_BYTES)
            val actual = sha256(temporary)
            require(actual == expected) { "Downloaded APK failed SHA-256 verification" }
            require(temporary.renameTo(destination)) { "Could not finalize downloaded update" }
            directory.listFiles()?.filter { it != destination }?.forEach(File::delete)
            return destination
        } finally {
            temporary.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApkIdentity(apk: File) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Downloaded file is not a valid APK")
        require(archive.packageName == context.packageName) { "Downloaded APK has the wrong application ID" }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val installedVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        require(archiveVersion > installedVersion) { "Downloaded APK does not have a newer version code" }

        val archiveSigners = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        val installedSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        require(!archiveSigners.isNullOrEmpty() && !installedSigners.isNullOrEmpty()) { "Could not verify APK signing identity" }
        require(archiveSigners.any { candidate -> installedSigners.any { it == candidate } }) {
            "Downloaded APK is not signed by the installed CellScope key"
        }
    }

    private fun showReadyNotification(version: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.update_channel_description) },
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_INSTALL_UPDATE
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("CellScope $version is ready")
            .setContentText("Tap to review and install the verified update")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The ready state remains visible in Settings when notification permission is denied.
        }
    }

    private fun clearDownloadedUpdate() {
        preferences.edit().remove(READY_VERSION).remove(READY_APK).apply()
        updateDirectory().listFiles()?.forEach(File::delete)
    }

    private fun updateDirectory() = File(context.filesDir, "updates")

    private fun isManagedApk(apk: File): Boolean =
        apk.isFile && apk.extension == "apk" && apk.canonicalFile.parentFile == updateDirectory().canonicalFile

    private fun readUrl(url: String, maximumBytes: Long): ByteArray {
        val connection = openConnection(url)
        return connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maximumBytes) { "Update response is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }.also { connection.disconnect() }
    }

    private fun downloadUrl(url: String, destination: File, maximumBytes: Long) {
        val connection = openConnection(url)
        val declaredLength = connection.contentLengthLong
        require(declaredLength in -1..maximumBytes) { "Update APK is too large" }
        connection.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maximumBytes) { "Update APK is too large" }
                    output.write(buffer, 0, count)
                }
            }
        }
        connection.disconnect()
    }

    private fun openConnection(initialUrl: String): HttpURLConnection {
        var url = URI(initialUrl).toURL()
        repeat(MAX_REDIRECTS + 1) {
            require(url.protocol == "https" && url.host in ALLOWED_HOSTS) { "Untrusted update URL" }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CellScope/${BuildConfig.VERSION_NAME}")
            }
            val status = connection.responseCode
            if (status in 200..299) return connection
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location") ?: error("Update redirect has no location")
                connection.disconnect()
                url = URL(url, location)
            } else {
                connection.disconnect()
                error("Update server returned HTTP $status")
            }
        }
        error("Too many update redirects")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Release(
        val version: String,
        val apkName: String,
        val apkUrl: String,
        val checksumUrl: String,
    )

    companion object {
        private const val PREFERENCES = "updates"
        private const val LAST_CHECKED_AT = "last_checked_at"
        private const val READY_VERSION = "ready_version"
        private const val READY_APK = "ready_apk"
        private const val STARTUP_WORK = "update-startup"
        private const val MANUAL_WORK = "update-manual"
        private const val PERIODIC_WORK = "update-periodic"
        private const val UPDATE_CHANNEL = "app_updates"
        private const val UPDATE_NOTIFICATION_ID = 2001
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        private const val MAX_METADATA_BYTES = 1024L * 1024L
        private const val MAX_CHECKSUM_BYTES = 4L * 1024L
        private const val MAX_APK_BYTES = 100L * 1024L * 1024L
        private const val MAX_REDIRECTS = 5
        private const val LATEST_RELEASE = "https://api.github.com/repos/codyps/CellScope/releases/latest"
        private val ALLOWED_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )
    }
}

class UpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as app.cellscope.CellScopeApplication
        return application.updates.performCheck(inputData.getBoolean(FORCE, false))
    }

    companion object {
        const val FORCE = "force"
    }
}

internal object VersionNumbers {
    fun isNewer(current: String, candidate: String): Boolean {
        val currentParts = parse(current) ?: return false
        val candidateParts = parse(candidate) ?: return false
        val size = maxOf(currentParts.size, candidateParts.size)
        for (index in 0 until size) {
            val currentPart = currentParts.getOrElse(index) { 0 }
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            if (candidatePart != currentPart) return candidatePart > currentPart
        }
        return false
    }

    private fun parse(version: String): List<Int>? {
        val normalized = version.removePrefix("v")
        if (!normalized.matches(Regex("[0-9]+(\\.[0-9]+)*"))) return null
        return normalized.split('.').map { it.toIntOrNull() ?: return null }
    }
}
