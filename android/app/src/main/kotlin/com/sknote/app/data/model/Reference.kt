package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 参考手册 ============

data class ReferenceItem(
    val id: Long,
    val name: String,
    val category: String,
    val type: String,
    val description: String? = "",
    val usage: String? = "",
    val parameters: String? = "",
    val example: String? = "",
    val icon: String? = "",
    val color: String? = "",
    val shape: String? = "s",
    val spec: String? = "",
    val code: String? = "",
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
