package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 代码片段 ============

data class Snippet(
    val id: Long,
    val title: String,
    val description: String? = "",
    val code: String? = null,
    val language: String? = "java",
    val category: String? = "general",
    val tags: String? = "",
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("author_name") val authorName: String? = "",
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class SnippetsResponse(
    val snippets: List<Snippet>,
    val pagination: PaginationInfo
)

data class SnippetDetailResponse(val snippet: Snippet)

data class SnippetCategoriesResponse(
    val categories: List<SnippetCategory>
)

data class SnippetCategory(
    val category: String,
    val count: Int
)

data class CreateSnippetRequest(
    val title: String,
    val description: String = "",
    val code: String,
    val language: String = "java",
    val category: String = "general",
    val tags: String = ""
)

data class UpdateSnippetRequest(
    val title: String,
    val description: String = "",
    val code: String,
    val language: String = "java",
    val category: String = "general",
    val tags: String = ""
)
