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
import kotlinx.coroutines.launch

class DiscussionListViewModel : ViewModel() {

    private val _discussions = MutableLiveData<List<Discussion>>()
    val discussions: LiveData<List<Discussion>> = _discussions

    private val _categories = MutableLiveData<List<DiscussionCategory>>()
    val categories: LiveData<List<DiscussionCategory>> = _categories

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun invalidateCache() = Unit

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
