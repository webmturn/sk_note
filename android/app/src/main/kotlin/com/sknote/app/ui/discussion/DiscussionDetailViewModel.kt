package com.sknote.app.ui.discussion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Comment
import com.sknote.app.data.model.CreateCommentRequest
import com.sknote.app.data.model.Discussion
import com.sknote.app.data.model.UpdateCloseRequest
import com.sknote.app.data.model.UpdateCommentRequest
import com.sknote.app.data.model.UpdatePinRequest
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

    /**
     * 用导航参数里带过来的讨论数据先把页头渲染出来。
     *
     * 这一步不产生任何网络请求，也不改变加载流程：它只是把 [discussion] 作为
     * `_discussion` 的当前值，让页面按常规观察者路径渲染一次真实内容，
     * 详情接口回来后用同样的路径再渲染一次（数值可能更新，例如浏览数、评论数）。
     *
     * 已有真实数据时不覆盖，避免迟到的预填把接口结果顶掉。
     */
    fun setPrefilledDiscussion(discussion: Discussion) {
        if (_discussion.value != null) return
        _discussion.value = discussion
    }

    /**
     * 重新派发缓存数据。旋转后 LiveData 会在页头 ViewHolder 就绪前先回调一次，
     * Fragment 因 `hb == null` 提前退出且之后不会再有新值；页头就绪后重放一次即可。
     */
    fun replayCachedDiscussion() {
        val cached = _discussion.value ?: return
        _discussion.value = cached
    }

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

    fun togglePin(discussionId: Long, pinned: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().pinDiscussion(
                    discussionId, UpdatePinRequest(if (pinned) 1 else 0)
                )
                if (response.isSuccessful) {
                    onResult(true, null)
                    loadDiscussion(discussionId)
                } else {
                    onResult(false, "操作失败: ${response.code()}")
                }
            } catch (e: Exception) {
                onResult(false, ErrorUtil.friendlyMessage(e))
            }
        }
    }

    fun toggleClose(discussionId: Long, closed: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().closeDiscussion(
                    discussionId, UpdateCloseRequest(if (closed) 1 else 0)
                )
                if (response.isSuccessful) {
                    onResult(true, null)
                    loadDiscussion(discussionId)
                } else {
                    onResult(false, "操作失败: ${response.code()}")
                }
            } catch (e: Exception) {
                onResult(false, ErrorUtil.friendlyMessage(e))
            }
        }
    }

    fun updateComment(discussionId: Long, commentId: Long, content: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().updateComment(
                    discussionId, commentId, UpdateCommentRequest(content)
                )
                if (response.isSuccessful) {
                    onResult(true, null)
                    loadDiscussion(discussionId)
                } else {
                    onResult(false, "更新评论失败: ${response.code()}")
                }
            } catch (e: Exception) {
                onResult(false, ErrorUtil.friendlyMessage(e))
            }
        }
    }

    fun likeComment(discussionId: Long, commentId: Long) {
        if (!likingCommentIds.add(commentId)) return
        val original = _comments.value?.firstOrNull { it.id == commentId }
        if (original == null) {
            likingCommentIds.remove(commentId)
            return
        }

        // 先本地翻转，点击后立刻看到填充/取消；失败再回滚。
        val optimisticLiked = !original.liked
        val optimisticCount = (original.likeCount + if (optimisticLiked) 1 else -1).coerceAtLeast(0)
        updateCommentLike(commentId, optimisticLiked, optimisticCount)

        viewModelScope.launch {
            try {
                val response = ApiClient.getService().likeComment(discussionId, commentId)
                val result = response.body()
                if (response.isSuccessful) {
                    if (result != null && result.liked != optimisticLiked) {
                        val correctedCount = (original.likeCount + if (result.liked) 1 else -1)
                            .coerceAtLeast(0)
                        updateCommentLike(commentId, result.liked, correctedCount)
                    }
                } else {
                    updateCommentLike(commentId, original.liked, original.likeCount)
                    _error.value = "操作失败: ${response.code()}"
                }
            } catch (e: Exception) {
                updateCommentLike(commentId, original.liked, original.likeCount)
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                likingCommentIds.remove(commentId)
            }
        }
    }

    private fun updateCommentLike(commentId: Long, liked: Boolean, likeCount: Int) {
        val current = _comments.value ?: return
        _comments.value = current.map { comment ->
            if (comment.id == commentId) {
                comment.copy(liked = liked, likeCount = likeCount)
            } else {
                comment
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
