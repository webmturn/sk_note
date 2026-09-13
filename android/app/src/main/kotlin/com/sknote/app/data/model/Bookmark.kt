package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 收藏 & 阅读历史 ============

data class BookmarkItem(
    val id: Long,
    @SerializedName("bookmarked_at") val bookmarkedAt: String? = null,
    @SerializedName("article_id") val articleId: Long,
    val title: String,
    val content: String? = "",
    val summary: String? = "",
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
    val content: String? = "",
    val summary: String? = "",
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class HistoryResponse(
    val history: List<HistoryItem>,
    val pagination: PaginationInfo
)

/**
 * 把收藏条目还原成详情页可用的 [Article]，用于「列表 → 详情」预填。
 *
 * 这条查询不返回 author_id / category_id / author_avatar，用 0 / null 占位：
 * 详情页的预填守卫只要求**正文非空**，其余字段会在详情接口回来后按同一条渲染路径
 * 补齐 —— 头像 36dp 固定、分类名与作者名同处一行，都不会引起位移。
 */
fun BookmarkItem.toPrefillArticle(): Article = Article(
    id = articleId,
    title = title,
    content = content,
    summary = summary,
    categoryId = 0L,
    authorId = 0L,
    authorName = authorName,
    categoryName = categoryName,
    viewCount = viewCount,
    likeCount = likeCount,
    createdAt = createdAt
)

/** 同 [BookmarkItem.toPrefillArticle]，用于阅读历史。 */
fun HistoryItem.toPrefillArticle(): Article = Article(
    id = articleId,
    title = title,
    content = content,
    summary = summary,
    categoryId = 0L,
    authorId = 0L,
    authorName = authorName,
    categoryName = categoryName,
    viewCount = viewCount,
    createdAt = createdAt
)
