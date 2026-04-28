package com.sknote.app.ui.share

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Share
import com.sknote.app.data.model.ShareCategory
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class ShareListViewModel : ViewModel() {

    private val _shares = MutableLiveData<List<Share>>()
    val shares: LiveData<List<Share>> = _shares

    private val _categories = MutableLiveData<List<ShareCategory>>()
    val categories: LiveData<List<ShareCategory>> = _categories

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun invalidateCache() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun loadShares(category: String? = null, search: String? = null, force: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = ApiClient.getService().getShares(
                    category = category, search = search, limit = 50
                )
                if (response.isSuccessful) {
                    _shares.value = response.body()?.shares ?: emptyList()
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
                val response = ApiClient.getService().getShareCategories()
                if (response.isSuccessful) {
                    _categories.value = response.body()?.categories ?: emptyList()
                }
            } catch (_: Exception) { }
        }
    }
}
