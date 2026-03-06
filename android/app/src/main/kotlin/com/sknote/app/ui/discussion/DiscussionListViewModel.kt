package com.sknote.app.ui.discussion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Discussion
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class DiscussionListViewModel : ViewModel() {

    private val _discussions = MutableLiveData<List<Discussion>>()
    val discussions: LiveData<List<Discussion>> = _discussions

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var lastLoadTime = 0L
    private var lastCategory: String? = null
    private val cacheDuration = 60_000L

    fun invalidateCache() {
        lastLoadTime = 0L
    }

    fun loadDiscussions(category: String? = null, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && category == lastCategory && now - lastLoadTime < cacheDuration && _discussions.value != null) {
            return
        }
        lastCategory = category
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = ApiClient.getService().getDiscussions(category = category)
                if (response.isSuccessful) {
                    _discussions.value = response.body()?.discussions ?: emptyList()
                    lastLoadTime = System.currentTimeMillis()
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
}
