package com.sknote.app.ui.reference

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sknote.app.data.model.ReferenceItem

enum class SortMode { DEFAULT, NAME_ASC, NAME_DESC, CATEGORY, ID_ASC, ID_DESC, COLOR }

class ReferenceViewModel : ViewModel() {

    private val _references = MutableLiveData<List<ReferenceItem>>()
    val references: LiveData<List<ReferenceItem>> = _references

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    var sortMode: SortMode = SortMode.DEFAULT
    var shapeFilter: String? = null

    private fun applySorting(items: List<ReferenceItem>): List<ReferenceItem> = when (sortMode) {
        SortMode.NAME_ASC -> items.sortedBy { it.name.lowercase() }
        SortMode.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
        SortMode.CATEGORY -> items.sortedWith(compareBy({ it.category }, { it.name.lowercase() }))
        SortMode.ID_ASC -> items.sortedBy { it.id }
        SortMode.ID_DESC -> items.sortedByDescending { it.id }
        SortMode.COLOR -> items.sortedWith(compareBy({ it.color }, { it.name.lowercase() }))
        SortMode.DEFAULT -> items
    }

    private fun applyShapeFilter(items: List<ReferenceItem>): List<ReferenceItem> {
        val shape = shapeFilter ?: return items
        return ReferenceData.getByShape(items, shape)
    }

    fun loadReferences(type: String? = null, category: String? = null) {
        _isLoading.value = true
        _error.value = null
        var items = when {
            type != null && category != null -> ReferenceData.getByTypeAndCategory(type, category)
            type != null -> ReferenceData.getByType(type)
            else -> ReferenceData.getAllItems()
        }
        items = applyShapeFilter(items)
        _references.value = applySorting(items)
        _isLoading.value = false
    }

    fun searchReferences(query: String) {
        _isLoading.value = true
        _error.value = null
        _references.value = applySorting(ReferenceData.search(query))
        _isLoading.value = false
    }

    fun getSortLabel(): String = when (sortMode) {
        SortMode.DEFAULT -> "默认"
        SortMode.NAME_ASC -> "A→Z"
        SortMode.NAME_DESC -> "Z→A"
        SortMode.CATEGORY -> "分类"
        SortMode.ID_ASC -> "ID↑"
        SortMode.ID_DESC -> "ID↓"
        SortMode.COLOR -> "颜色"
    }
}
