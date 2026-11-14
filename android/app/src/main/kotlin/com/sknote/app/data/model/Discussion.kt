package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 讨论 ============

data class Discussion(
    val id: Long,
    val title: String,
    val content: String? = "",
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar") val authorAvatar: String? = null,
    @SerializedName("article_id") val articleId: Long? = null,
    val category: String? = "general",
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("category_icon") val categoryIcon: String? = null,
    @SerializedName("is_pinned") val isPinned: Int = 0,
    @SerializedName("is_closed") val isClosed: Int = 0,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("reply_count") val replyCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    /** 标签：逗号分隔的纯文本，与 Snippet.tags 同一做法（详情页渲染成 #chips）。 */
    val tags: String? = ""
)

data class Comment(
    val id: Long,
    val content: String? = "",
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar") val authorAvatar: String? = null,
    @SerializedName("discussion_id") val discussionId: Long,
    @SerializedName("parent_id") val parentId: Long? = null,
    @SerializedName("parent_author_name") val parentAuthorName: String? = null,
    @SerializedName("like_count") val likeCount: Int = 0,
    /** 当前登录用户是否已赞；未登录或旧接口缺字段时按未赞处理。 */
    val liked: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class DiscussionsResponse(
    val discussions: List<Discussion>,
    val pagination: PaginationInfo
)

data class DiscussionDetailResponse(
    val discussion: Discussion,
    val comments: List<Comment>
)

data class DiscussionCategory(
    val id: Long,
    val slug: String,
    val name: String,
    val description: String? = "",
    val icon: String? = "",
    @SerializedName("sort_order") val sortOrder: Int = 0,
)

data class DiscussionCategoriesResponse(val categories: List<DiscussionCategory>)

data class DiscussionCategoryResponse(val category: DiscussionCategory)

/** 未读项：某条讨论在我上次读过之后新增的回复数。 */
data class UnreadDiscussionItem(
    @SerializedName("discussion_id") val discussionId: Long,
    @SerializedName("new_reply_count") val newReplyCount: Int = 0
)

data class UnreadDiscussionsResponse(
    val items: List<UnreadDiscussionItem> = emptyList(),
    @SerializedName("total_unread") val totalUnread: Int = 0,
    @SerializedName("total_new_replies") val totalNewReplies: Int = 0
)

data class CreateDiscussionCategoryRequest(
    val slug: String,
    val name: String,
    val description: String = "",
    val icon: String = "",
    @SerializedName("sort_order") val sortOrder: Int = 0,
)

data class CreateDiscussionRequest(
    val title: String,
    val content: String,
    val category: String = "general",
    @SerializedName("article_id") val articleId: Long? = null,
    /** 标签原文（逗号分隔），后端 normalizeTags 会去空/去重/限量。 */
    val tags: String = ""
)

data class UpdateDiscussionRequest(
    val title: String,
    val content: String,
    val category: String = "general",
    @SerializedName("article_id") val articleId: Long? = null,
    val tags: String = ""
)

data class CreateCommentRequest(
    val content: String,
    @SerializedName("parent_id") val parentId: Long? = null
)

data class UpdateCommentRequest(
    val content: String,
)

data class UpdatePinRequest(
    @SerializedName("is_pinned") val isPinned: Int
)

data class UpdateCloseRequest(
    @SerializedName("is_closed") val isClosed: Int
)
