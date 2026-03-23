package com.sknote.app.data.model

import com.google.gson.annotations.SerializedName

// ============ 分类 ============

data class Category(
    val id: Long,
    val name: String,
    val description: String? = "",
    val icon: String? = "",
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("parent_id") val parentId: Long? = null
)

data class CategoriesResponse(val categories: List<Category>)
data class CategoryResponse(val category: Category)

data class CreateCategoryRequest(
    val name: String,
    val description: String = "",
    val icon: String = "",
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("parent_id") val parentId: Long? = null
)
