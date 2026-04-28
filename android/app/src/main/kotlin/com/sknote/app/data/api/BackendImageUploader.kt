package com.sknote.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object BackendImageUploader {

    data class UploadResult(
        val success: Boolean,
        val url: String? = null,
        val error: String? = null
    )

    suspend fun upload(
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String = "image/jpeg"
    ): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", fileName, requestBody)
                val response = ApiClient.getService().uploadImage(imagePart)
                if (response.isSuccessful) {
                    val url = response.body()?.url.orEmpty()
                    if (url.isNotEmpty()) {
                        UploadResult(true, url = url)
                    } else {
                        UploadResult(false, error = "服务器未返回图片链接")
                    }
                } else {
                    val serverMsg = try {
                        JSONObject(response.errorBody()?.string() ?: "").optString("error", "")
                    } catch (_: Exception) {
                        ""
                    }
                    UploadResult(
                        success = false,
                        error = serverMsg.ifEmpty {
                            when (response.code()) {
                                400 -> "图片格式或大小不符合要求"
                                401 -> "请先登录后再上传图片"
                                else -> "上传失败: ${response.code()}"
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                UploadResult(false, error = "上传异常: ${e.message}")
            }
        }
    }
}
