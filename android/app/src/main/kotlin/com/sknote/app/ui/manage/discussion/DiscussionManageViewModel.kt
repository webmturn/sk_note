package com.sknote.app.ui.manage.discussion

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

    @Suppress("UNUSED_PARAMETER")
    fun loadDiscussions(force: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getDiscussions(page = 1, limit = 100)
                if (response.isSuccessful) {
                    _discussions.value = response.body()?.discussions ?: emptyList()
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
