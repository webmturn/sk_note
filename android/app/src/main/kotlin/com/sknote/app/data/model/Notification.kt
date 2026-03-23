package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 通知 ============

data class Notification(
    val id: Long,
    @SerializedName("user_id") val userId: Long,
    val type: String,
    val title: String,
    val content: String? = "",
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
