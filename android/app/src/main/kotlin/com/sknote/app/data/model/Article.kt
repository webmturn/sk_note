package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 文章 ============

data class Article(
    val id: Long,
    val title: String,
    val content: String? = "",
    val summary: String? = "",
    @SerializedName("category_id") val categoryId: Long,
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar") val authorAvatar: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class ArticlesResponse(
    val articles: List<Article>,
    val pagination: PaginationInfo
)

data class ArticleResponse(val article: Article)

data class CreateArticleRequest(
    val title: String,
    val content: String,
    val summary: String = "",
    @SerializedName("category_id") val categoryId: Long
)

data class UpdateArticleRequest(
    val title: String? = null,
    val content: String? = null,
    val summary: String? = null,
    @SerializedName("category_id") val categoryId: Long? = null,
    @SerializedName("sort_order") val sortOrder: Int? = null,
    @SerializedName("is_published") val isPublished: Int? = null
)
