package com.sknote.app.ui.discussion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Discussion
import com.sknote.app.data.model.DiscussionCategory
import com.sknote.app.util.DiscussionCategoryDefaults
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DiscussionListViewModel : ViewModel() {

    private val _discussions = MutableLiveData<List<Discussion>>()
    val discussions: LiveData<List<Discussion>> = _discussions

    /** 未读：discussionId -> 新增回复数。只在登录后取，匿名时为空。 */
    private val _unread = MutableLiveData<Map<Long, Int>>(emptyMap())
    val unread: LiveData<Map<Long, Int>> = _unread

    private val _categories = MutableLiveData<List<DiscussionCategory>>()
    val categories: LiveData<List<DiscussionCategory>> = _categories

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun invalidateCache() = Unit

    /**
     * 取「我的未读」。与列表分开请求：列表接口带 120s 共享缓存，未读是按用户不同的，
     * 混在一起会把别人的未读状态缓存给你（后端注释里写了同样的理由）。
     * 未登录时保持空表，失败也保持空表 —— 未读只是增益信息，不该影响列表可用性。
     */
    fun loadUnread() {
        viewModelScope.launch {
            try {
                if (!ApiClient.getTokenManager().isLoggedIn().first()) {
                    _unread.value = emptyMap()
                    return@launch
                }
                val response = ApiClient.getService().getUnreadDiscussions()
                if (response.isSuccessful) {
                    val items = response.body()?.items.orEmpty()
                    _unread.value = items.associate { it.discussionId to it.newReplyCount }
                }
            } catch (_: Exception) {
                // 未读取不到就不标红点，不打扰用户
            }
        }
    }

    fun loadCategories() {
        // 先用本地兜底分类把 chip 行填上，再去请求网络。
        // 这不是性能优化，而是修一个真实的布局跳变：chip 行位于列表上方、高度由内容决定，
        // 而本页的 ChipGroup 在 XML 里是空的（「全部」也是网络回来后才动态添加的），
        // 因此在分类到达前这一行只剩 HorizontalScrollView 的 12dp padding，分类到达后
        // 长到 60dp，把下面整个列表（含骨架）向下推 48dp —— 真机实测值。
        // 分享与片段两页的 ChipGroup 在 XML 里已静态放了一个「全部」chip，高度恒定，没有这个问题。
        // 本地兜底表与本页原有的失败分支用的是同一份数据，因此先填后填内容一致。
        if (_categories.value == null) {
            _categories.value = DiscussionCategoryDefaults.categories
        }
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().getDiscussionCategories()
                val remoteCategories = if (response.isSuccessful) response.body()?.categories.orEmpty() else emptyList()
                _categories.value = if (remoteCategories.isNotEmpty()) remoteCategories else DiscussionCategoryDefaults.categories
            } catch (_: Exception) {
                _categories.value = DiscussionCategoryDefaults.categories
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun loadDiscussions(
        category: String? = null,
        articleId: Long? = null,
        sort: String = SORT_LATEST,
        force: Boolean = false
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = ApiClient.getService().getDiscussions(
                    category = category,
                    articleId = articleId,
                    sort = sort
                )
                if (response.isSuccessful) {
                    _discussions.value = response.body()?.discussions ?: emptyList()
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

    companion object {
        const val SORT_LATEST = "latest"
        const val SORT_HOT = "hot"
        const val SORT_REPLIES = "replies"

        /** 排序项：(接口值, 按钮上显示的短标签)。顺序即菜单顺序。 */
        val SORT_OPTIONS = listOf(
            SORT_LATEST to "最新",
            SORT_HOT to "最热",
            SORT_REPLIES to "最多回复"
        )

        fun sortLabel(sort: String): String =
            SORT_OPTIONS.firstOrNull { it.first == sort }?.second ?: "最新"
    }
}
