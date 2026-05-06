package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 应用版本发布 ============

data class AppRelease(
    val id: Long,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("version_code") val versionCode: Int,
    val changelog: String? = "",
    @SerializedName("download_url") val downloadUrl: String? = "",
    @SerializedName("file_size") val fileSize: Long = 0,
    @SerializedName("release_url") val releaseUrl: String? = "",
    @SerializedName("is_active") val isActive: Int = 1,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AppReleasesResponse(
    val releases: List<AppRelease>
)

data class CreateAppReleaseRequest(
    @SerializedName("version_name") val versionName: String,
    @SerializedName("version_code") val versionCode: Int,
    val changelog: String = "",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("file_size") val fileSize: Long = 0,
    @SerializedName("release_url") val releaseUrl: String = ""
)

data class UpdateReleaseActiveRequest(
    @SerializedName("is_active") val isActive: Int
)

data class CheckUpdateResponse(
    @SerializedName("has_update") val hasUpdate: Boolean = false,
    @SerializedName("latest_version") val latestVersion: String? = null,
    @SerializedName("version_code") val versionCode: Int? = null,
    val changelog: String? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("file_size") val fileSize: Long? = null,
    @SerializedName("release_url") val releaseUrl: String? = null,
    @SerializedName("released_at") val releasedAt: String? = null,
    val message: String? = null
)
