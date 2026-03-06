package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 通用响应 ============

data class MessageResponse(
    val message: String? = null,
    val error: String? = null,
    val id: Long? = null
)

data class PaginationInfo(
    val page: Int,
    val limit: Int,
    val total: Int,
    @SerializedName("total_pages") val totalPages: Int
)

// ============ 用户 ============

data class User(
    val id: Long,
    val username: String,
    val email: String? = null,
    val role: String = "user",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("created_at") val createdAt: String? = null
)

data class AuthResponse(
    val token: String?,
    val user: User?,
    val error: String? = null
)

data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val email: String, val password: String)

data class HomeResponse(
    val categories: List<Category>,
    val articles: List<Article>
)

data class StatsResponse(
    @SerializedName("unread_notifications") val unreadNotifications: Int = 0,
    @SerializedName("total_articles") val totalArticles: Int = 0,
    @SerializedName("total_discussions") val totalDiscussions: Int = 0,
    @SerializedName("total_snippets") val totalSnippets: Int = 0,
    @SerializedName("total_users") val totalUsers: Int = 0
)

data class UsersResponse(
    val users: List<User>,
    val pagination: PaginationInfo
)

data class UpdateRoleRequest(val role: String)
data class ResetPasswordRequest(@SerializedName("new_password") val newPassword: String)

// ============ 分类 ============

data class Category(
    val id: Long,
    val name: String,
    val description: String = "",
    val icon: String = "",
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("parent_id") val parentId: Long? = null
)

data class CategoriesResponse(val categories: List<Category>)
data class CategoryResponse(val category: Category)

// ============ 文章 ============

data class Article(
    val id: Long,
    val title: String,
    val content: String,
    val summary: String = "",
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

// ============ 参考手册 ============

data class ReferenceItem(
    val id: Long,
    val name: String,
    val category: String,
    val type: String,
    val description: String = "",
    val usage: String = "",
    val parameters: String = "",
    val example: String = "",
    val icon: String = "",
    val color: String = "",
    val shape: String = "s",
    val spec: String = "",
    val code: String = "",
    @SerializedName("related_ids") val relatedIds: List<Int>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class ReferenceListResponse(
    val references: List<ReferenceItem>,
    val pagination: PaginationInfo? = null
)

data class ReferenceDetailResponse(
    val reference: ReferenceItem
)

// ============ 管理员操作 ============

data class CreateCategoryRequest(
    val name: String,
    val description: String = "",
    val icon: String = "",
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("parent_id") val parentId: Long? = null
)

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
    @SerializedName("category_id") val categoryId: Long? = null
)

// ============ 通知 ============

data class Notification(
    val id: Long,
    @SerializedName("user_id") val userId: Long,
    val type: String,
    val title: String,
    val content: String = "",
    @SerializedName("related_type") val relatedType: String? = null,
    @SerializedName("related_id") val relatedId: Long? = null,
    @SerializedName("is_read") val isRead: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class NotificationsResponse(
    val notifications: List<Notification>,
    @SerializedName("unread_count") val unreadCount: Int = 0,
    val pagination: PaginationInfo? = null
)

data class UnreadCountResponse(val count: Int)

// ============ 用户操作 ============

data class ChangePasswordRequest(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class LikeResponse(
    val message: String,
    val liked: Boolean = false
)

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

// ============ 代码片段 ============

data class Snippet(
    val id: Long,
    val title: String,
    val description: String = "",
    val code: String,
    val language: String = "java",
    val category: String = "general",
    val tags: String = "",
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("author_name") val authorName: String = "",
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
