package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 关注 ============

data class FollowUser(
    val id: Long,
    val username: String,
    val nickname: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    val role: String = "user",
    @SerializedName("followed_at") val followedAt: String? = null
) {
    val displayName: String get() = nickname.ifEmpty { username }
}

data class FollowListResponse(
    val users: List<FollowUser>,
    val pagination: PaginationInfo
)

data class FollowToggleResponse(
    val message: String,
    val following: Boolean
)

data class FollowCheckResponse(
    val following: Boolean
)

// ============ 公开资料 ============

data class PublicProfileResponse(
    val user: User,
    val stats: PublicProfileStats
)

data class PublicProfileStats(
    val following: Int = 0,
    val followers: Int = 0,
    val discussions: Int = 0,
    val snippets: Int = 0,
    val shares: Int = 0
)
