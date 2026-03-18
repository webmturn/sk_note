package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 讨论 ============

data class Discussion(
    val id: Long,
    val title: String,
    val content: String,
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar") val authorAvatar: String? = null,
    @SerializedName("article_id") val articleId: Long? = null,
    val category: String = "general",
    @SerializedName("is_pinned") val isPinned: Int = 0,
    @SerializedName("is_closed") val isClosed: Int = 0,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("reply_count") val replyCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class Comment(
    val id: Long,
    val content: String,
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar") val authorAvatar: String? = null,
    @SerializedName("discussion_id") val discussionId: Long,
    @SerializedName("parent_id") val parentId: Long? = null,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class DiscussionsResponse(
    val discussions: List<Discussion>,
    val pagination: PaginationInfo
)

data class DiscussionDetailResponse(
    val discussion: Discussion,
    val comments: List<Comment>
)

data class CreateDiscussionRequest(
    val title: String,
    val content: String,
    val category: String = "general",
    @SerializedName("article_id") val articleId: Long? = null
)

data class CreateCommentRequest(
    val content: String,
    @SerializedName("parent_id") val parentId: Long? = null
)
