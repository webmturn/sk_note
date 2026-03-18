package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

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
    val articles: List<Article>,
    @SerializedName("latest_shares") val latestShares: List<Share>? = null
)

data class StatsResponse(
    @SerializedName("unread_notifications") val unreadNotifications: Int = 0,
    @SerializedName("total_articles") val totalArticles: Int = 0,
    @SerializedName("total_discussions") val totalDiscussions: Int = 0,
    @SerializedName("total_snippets") val totalSnippets: Int = 0,
    @SerializedName("total_users") val totalUsers: Int = 0,
    @SerializedName("total_shares") val totalShares: Int = 0
)

data class UsersResponse(
    val users: List<User>,
    val pagination: PaginationInfo
)

data class UpdateRoleRequest(val role: String)
data class ResetPasswordRequest(@SerializedName("new_password") val newPassword: String)

data class ChangePasswordRequest(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String
)
