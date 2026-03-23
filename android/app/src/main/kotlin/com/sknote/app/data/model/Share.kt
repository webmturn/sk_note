package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 文件分享 ============

data class Share(
    val id: Long,
    val title: String,
    val description: String? = "",
    val category: String? = "general",
    @SerializedName("download_url") val downloadUrl: String? = "",
    @SerializedName("download_pwd") val downloadPwd: String? = "",
    @SerializedName("file_size") val fileSize: String? = "",
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("author_name") val authorName: String? = "",
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("download_count") val downloadCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class SharesResponse(
    val shares: List<Share>,
    val pagination: PaginationInfo
)

data class ShareDetailResponse(val share: Share)

data class ShareCategoriesResponse(
    val categories: List<ShareCategory>
)

data class ShareCategory(
    val category: String,
    val count: Int
)

data class CreateShareRequest(
    val title: String,
    val description: String = "",
    val category: String = "general",
    @SerializedName("download_url") val downloadUrl: String,
    @SerializedName("download_pwd") val downloadPwd: String = "",
    @SerializedName("file_size") val fileSize: String = ""
)
