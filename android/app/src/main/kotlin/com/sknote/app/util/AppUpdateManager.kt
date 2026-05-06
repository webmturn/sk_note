package com.sknote.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.sknote.app.BuildConfig
import com.sknote.app.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File

object AppUpdateManager {

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var startupPrefetchJob: Deferred<UpdateResult>? = null

    @Volatile
    private var startupPrefetchResult: UpdateResult? = null

    data class UpdateInfo(
        val versionName: String,
        val changelog: String,
        val downloadUrl: String,
        val fileSize: Long,
        val htmlUrl: String
    )

    data class UpdateResult(
        val hasUpdate: Boolean,
        val info: UpdateInfo? = null,
        val error: String? = null
    )

    @Synchronized
    fun prefetchForStartup(context: Context) {
        if (!shouldAutoCheck(context)) return
        if (startupPrefetchResult != null) return
        val job = startupPrefetchJob
        if (job != null && job.isActive) return
        val appContext = context.applicationContext
        startupPrefetchJob = startupScope.async {
            val result = checkForUpdate()
            if (result.error == null) {
                markChecked(appContext)
            }
            startupPrefetchResult = result
            result
        }
    }

    suspend fun consumeStartupPrefetch(context: Context): UpdateResult? {
        if (startupPrefetchResult == null && startupPrefetchJob == null) {
            prefetchForStartup(context)
        }

        val result = startupPrefetchResult ?: startupPrefetchJob?.await() ?: return null

        synchronized(this) {
            startupPrefetchResult = null
            startupPrefetchJob = null
        }

        return result
    }

    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.getService().checkAppUpdate(BuildConfig.VERSION_NAME)
            if (!response.isSuccessful) {
                return@withContext UpdateResult(false, error = "服务器错误 (${response.code()})")
            }

            val body = response.body() ?: return@withContext UpdateResult(false, error = "空响应")

            val remoteVersion = body.latestVersion.orEmpty()
                .trim()
                .removePrefix("v")
                .removePrefix("V")
            if (remoteVersion.isBlank()) {
                // 后端尚无版本记录：视为无可更新版本，不报错
                return@withContext UpdateResult(false)
            }

            val info = UpdateInfo(
                versionName = remoteVersion,
                changelog = body.changelog.orEmpty().trim(),
                downloadUrl = body.downloadUrl.orEmpty(),
                fileSize = body.fileSize ?: 0L,
                htmlUrl = body.releaseUrl.orEmpty()
            )
            // 后端已经做过比较，但本地再校验一次以防版本号漂移
            val hasUpdate = body.hasUpdate && isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)
            UpdateResult(hasUpdate, info)
        } catch (e: Exception) {
            UpdateResult(false, error = ErrorUtil.friendlyMessage(e))
        }
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }

    fun startDownload(context: Context, url: String, versionName: String, onComplete: (File?) -> Unit): Long {
        val appContext = context.applicationContext
        val fileName = "SkNote_v${versionName}.apk"
        val downloadDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: run {
                onComplete(null)
                return -1L
            }
        val targetFile = File(downloadDir, fileName)
        if (targetFile.exists()) targetFile.delete()

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("SkNote 更新")
            .setDescription("正在下载 v$versionName ...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        appContext.unregisterReceiver(this)
                    } catch (_: IllegalArgumentException) {
                    }
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor != null) {
                        cursor.use {
                            if (it.moveToFirst()) {
                                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    onComplete(targetFile)
                                } else {
                                    onComplete(null)
                                }
                            } else {
                                onComplete(null)
                            }
                        }
                    } else {
                        onComplete(null)
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            appContext.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        return downloadId
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK = "last_check_time"
    private const val KEY_SKIP_VERSION = "skip_version"
    private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L

    fun shouldAutoCheck(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL
    }

    fun getLastCheckedTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0L)
    }

    fun markChecked(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    fun skipVersion(context: Context, versionName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIP_VERSION, versionName).apply()
    }

    fun isVersionSkipped(context: Context, versionName: String): Boolean {
        return getSkippedVersion(context) == versionName
    }

    fun getSkippedVersion(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val skipped = prefs.getString(KEY_SKIP_VERSION, "")?.ifBlank { null } ?: return null
        if (!isNewerVersion(skipped, BuildConfig.VERSION_NAME)) {
            prefs.edit().remove(KEY_SKIP_VERSION).apply()
            return null
        }
        return skipped
    }
}
