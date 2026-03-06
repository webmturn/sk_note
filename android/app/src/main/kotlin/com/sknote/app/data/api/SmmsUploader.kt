package com.sknote.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.sknote.app.BuildConfig
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SmmsUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class UploadResult(
        val success: Boolean,
        val url: String? = null,
        val error: String? = null
    )

    private fun guessMimeType(fileName: String): String = when {
        fileName.endsWith(".png", true) -> "image/png"
        fileName.endsWith(".gif", true) -> "image/gif"
        fileName.endsWith(".webp", true) -> "image/webp"
        else -> "image/jpeg"
    }

    suspend fun upload(imageBytes: ByteArray, fileName: String): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val apiToken = BuildConfig.SMMS_API_TOKEN
                if (apiToken.isBlank()) {
                    return@withContext UploadResult(false, error = "未配置 SM.MS API Token")
                }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "smfile",
                        fileName,
                        imageBytes.toRequestBody(guessMimeType(fileName).toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url("https://sm.ms/api/v2/upload")
                    .post(requestBody)
                    .addHeader("User-Agent", "SkNote-Android/1.0")
                    .addHeader("Authorization", apiToken)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext UploadResult(false, error = "空响应")

                val json = JSONObject(body)
                val success = json.optBoolean("success", false)

                if (success) {
                    val data = json.getJSONObject("data")
                    val url = data.getString("url")
                    UploadResult(true, url = url)
                } else {
                    val code = json.optString("code", "")
                    if (code == "image_repeated") {
                        val existUrl = json.optString("images", "")
                        if (existUrl.isNotEmpty()) {
                            UploadResult(true, url = existUrl)
                        } else {
                            UploadResult(false, error = "图片已存在")
                        }
                    } else {
                        val msg = json.optString("message", "上传失败")
                        UploadResult(false, error = msg)
                    }
                }
            } catch (e: Exception) {
                UploadResult(false, error = "上传异常: ${e.message}")
            }
        }
    }
}
