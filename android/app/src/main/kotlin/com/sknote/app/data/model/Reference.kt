package com.sknote.app.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

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
    @JsonAdapter(RelatedIdsDeserializer::class)
    @SerializedName("related_ids") val relatedIds: String? = null,
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

/**
 * related_ids 兼容反序列化：
 * - 本地 assets JSON 格式为数组 [1, 2, 3]
 * - 后端 API 返回格式为逗号字符串 "1,2,3"
 * 统一转为 String?
 */
class RelatedIdsDeserializer : JsonDeserializer<String?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): String? {
        if (json == null || json.isJsonNull) return null
        if (json.isJsonArray) {
            val arr = json.asJsonArray
            if (arr.size() == 0) return null
            return arr.joinToString(",") { it.asInt.toString() }
        }
        val s = json.asString
        return s.ifEmpty { null }
    }
}
