package com.sknote.app.ui.discussion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Comment
import com.sknote.app.data.model.CreateCommentRequest
import com.sknote.app.data.model.Discussion
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class DiscussionDetailViewModel : ViewModel() {

    private val _discussion = MutableLiveData<Discussion>()
    val discussion: LiveData<Discussion> = _discussion

    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> = _comments

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _commentSent = MutableLiveData<Boolean?>()
    val commentSent: LiveData<Boolean?> = _commentSent

    private val _deleted = MutableLiveData<Boolean?>()
    val deleted: LiveData<Boolean?> = _deleted

    fun onCommentSentHandled() { _commentSent.value = null }
    fun onDeletedHandled() { _deleted.value = null }

    fun loadDiscussion(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getDiscussion(id)
                if (response.isSuccessful) {
                    val body = response.body()
                    _discussion.value = body?.discussion
                    _comments.value = body?.comments ?: emptyList()
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

    fun sendComment(discussionId: Long, content: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().createComment(
                    discussionId, CreateCommentRequest(content)
                )
                if (response.isSuccessful) {
                    _commentSent.value = true
                    loadDiscussion(discussionId)
                } else {
                    _error.value = "评论失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun deleteDiscussion(id: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().deleteDiscussion(id)
                if (response.isSuccessful) {
                    _deleted.value = true
                } else {
                    _error.value = "删除失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun deleteComment(discussionId: Long, commentId: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().deleteComment(discussionId, commentId)
                if (response.isSuccessful) {
                    loadDiscussion(discussionId)
                } else {
                    _error.value = "删除评论失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun likeComment(discussionId: Long, commentId: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().likeComment(discussionId, commentId)
                if (response.isSuccessful) {
                    loadDiscussion(discussionId)
                } else {
                    _error.value = "操作失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }
}
