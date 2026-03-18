package com.sknote.app.data.model

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
    @com.google.gson.annotations.SerializedName("total_pages") val totalPages: Int
)

data class LikeResponse(
    val message: String,
    val liked: Boolean = false
)
