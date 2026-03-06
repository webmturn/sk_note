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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object AppUpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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

    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val repo = BuildConfig.GITHUB_REPO
            if (repo.isBlank()) return@withContext UpdateResult(false, error = "未配置 GitHub 仓库")

            val url = "https://api.github.com/repos/$repo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateResult(false, error = "GitHub API 错误 (${response.code})")
            }

            val body = response.body?.string() ?: return@withContext UpdateResult(false, error = "空响应")
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val remoteVersion = tagName.removePrefix("v").removePrefix("V")
            val changelog = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", "")

            var downloadUrl = ""
            var fileSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        fileSize = asset.optLong("size", 0)
                        break
                    }
                }
            }

            if (remoteVersion.isBlank()) {
                return@withContext UpdateResult(false, error = "无法解析版本号")
            }

            val hasUpdate = isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)
            val info = UpdateInfo(
                versionName = remoteVersion,
                changelog = changelog,
                downloadUrl = downloadUrl,
                fileSize = fileSize,
                htmlUrl = htmlUrl
            )
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
        val fileName = "SkNote_v${versionName}.apk"
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadDir, fileName)
        if (targetFile.exists()) targetFile.delete()

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("SkNote 更新")
            .setDescription("正在下载 v$versionName ...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            onComplete(targetFile)
                        } else {
                            onComplete(null)
                        }
                        cursor.close()
                    } else {
                        onComplete(null)
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
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

    fun markChecked(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    fun skipVersion(context: Context, versionName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIP_VERSION, versionName).apply()
    }

    fun isVersionSkipped(context: Context, versionName: String): Boolean {
        val skipped = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SKIP_VERSION, "")
        return skipped == versionName
    }
}
