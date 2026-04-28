package com.sknote.app.ui.snippet

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Snippet
import com.sknote.app.data.model.SnippetCategory
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class SnippetListViewModel : ViewModel() {

    private val _snippets = MutableLiveData<List<Snippet>>()
    val snippets: LiveData<List<Snippet>> = _snippets

    private val _categories = MutableLiveData<List<SnippetCategory>>()
    val categories: LiveData<List<SnippetCategory>> = _categories

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun invalidateCache() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun loadSnippets(category: String? = null, search: String? = null, force: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = ApiClient.getService().getSnippets(
                    category = category, search = search, limit = 50
                )
                if (response.isSuccessful) {
                    _snippets.value = response.body()?.snippets ?: emptyList()
                } else {
                    _error.value = "加载失败"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().getSnippetCategories()
                if (response.isSuccessful) {
                    _categories.value = response.body()?.categories ?: emptyList()
                }
            } catch (_: Exception) { }
        }
    }
}
