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

    private val _discussion = MutableLiveData<Discussion?>()
    val discussion: LiveData<Discussion?> = _discussion

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

    private val _isSending = MutableLiveData<Boolean>(false)
    val isSending: LiveData<Boolean> = _isSending
    private val likingCommentIds = mutableSetOf<Long>()

    fun onCommentSentHandled() { _commentSent.value = null }
    fun onDeletedHandled() { _deleted.value = null }

    fun loadDiscussion(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getDiscussion(id)
                if (response.isSuccessful) {
                    val body = response.body() ?: run {
                        _error.value = "讨论数据为空"
                        return@launch
                    }
                    _discussion.value = body.discussion
                    _comments.value = threadedSort(body.comments)
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

    fun sendComment(discussionId: Long, content: String, parentId: Long? = null) {
        if (_isSending.value == true) return
        viewModelScope.launch {
            _isSending.value = true
            try {
                val response = ApiClient.getService().createComment(
                    discussionId, CreateCommentRequest(content, parentId)
                )
                if (response.isSuccessful) {
                    _commentSent.value = true
                    loadDiscussion(discussionId)
                } else {
                    _error.value = "评论失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isSending.value = false
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
        if (!likingCommentIds.add(commentId)) return
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
            } finally {
                likingCommentIds.remove(commentId)
            }
        }
    }

    /**
     * 将评论按讨论串分组排序：顶级评论按时间排序，其下所有子回复紧随其后（按时间排序）。
     * 回复的回复会追溯到根评论归组，不再被无关评论隔断。
     */
    private fun threadedSort(comments: List<Comment>): List<Comment> {
        if (comments.isEmpty()) return comments
        val byId = comments.associateBy { it.id }

        // 找到评论的根祖先ID（parentId == null 的那个）
        fun rootId(c: Comment): Long {
            var current = c
            val visited = mutableSetOf(current.id)
            while (current.parentId != null) {
                val parent = byId[current.parentId] ?: break
                if (!visited.add(parent.id)) break // 防止循环
                current = parent
            }
            return current.id
        }

        // 顶级评论（按时间排序）
        val roots = comments.filter { it.parentId == null }.sortedBy { it.createdAt }
        // 按根评论分组
        val childrenByRoot = mutableMapOf<Long, MutableList<Comment>>()
        for (c in comments) {
            if (c.parentId != null) {
                val rid = rootId(c)
                childrenByRoot.getOrPut(rid) { mutableListOf() }.add(c)
            }
        }

        val result = mutableListOf<Comment>()
        for (root in roots) {
            result.add(root)
            childrenByRoot[root.id]?.sortedBy { it.createdAt }?.let { result.addAll(it) }
        }

        // 处理孤儿评论（父评论已被删除）
        val addedIds = result.map { it.id }.toSet()
        for (c in comments) {
            if (c.id !in addedIds) result.add(c)
        }

        return result
    }
}
