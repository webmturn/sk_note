package com.sknote.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Discussion
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class DiscussionManageViewModel : ViewModel() {

    private val _discussions = MutableLiveData<List<Discussion>>()
    val discussions: LiveData<List<Discussion>> = _discussions

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun clearMessage() { _message.value = null }

    private var lastLoadTime = 0L
    private val cacheDuration = 60_000L

    fun loadDiscussions(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastLoadTime < cacheDuration && _discussions.value != null) {
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getDiscussions(page = 1, limit = 100)
                if (response.isSuccessful) {
                    _discussions.value = response.body()?.discussions ?: emptyList()
                    lastLoadTime = System.currentTimeMillis()
                } else {
                    _message.value = "加载失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteDiscussion(id: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().deleteDiscussion(id)
                if (response.isSuccessful) {
                    _message.value = "讨论已删除"
                    loadDiscussions(force = true)
                } else {
                    _message.value = "删除失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }
}
