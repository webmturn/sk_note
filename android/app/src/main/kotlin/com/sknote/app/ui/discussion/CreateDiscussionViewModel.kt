package com.sknote.app.ui.discussion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.CreateDiscussionRequest
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class CreateDiscussionViewModel : ViewModel() {

    private val _success = MutableLiveData<Boolean>()
    val success: LiveData<Boolean> = _success

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun createDiscussion(title: String, content: String, category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().createDiscussion(
                    CreateDiscussionRequest(title, content, category)
                )
                if (response.isSuccessful) {
                    _success.value = true
                } else {
                    _error.value = "发布失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
