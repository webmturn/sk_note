package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 收藏 & 阅读历史 ============

data class BookmarkItem(
    val id: Long,
    @SerializedName("bookmarked_at") val bookmarkedAt: String? = null,
    @SerializedName("article_id") val articleId: Long,
    val title: String,
    val summary: String = "",
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class BookmarksResponse(
    val bookmarks: List<BookmarkItem>,
    val pagination: PaginationInfo
)

data class BookmarkCheckResponse(val bookmarked: Boolean)
data class BookmarkToggleResponse(val message: String, val bookmarked: Boolean)

data class HistoryItem(
    val id: Long,
    @SerializedName("read_at") val readAt: String? = null,
    @SerializedName("article_id") val articleId: Long,
    val title: String,
    val summary: String = "",
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class HistoryResponse(
    val history: List<HistoryItem>,
    val pagination: PaginationInfo
)
