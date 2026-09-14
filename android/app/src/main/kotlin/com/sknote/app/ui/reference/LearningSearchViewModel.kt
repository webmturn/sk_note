package com.sknote.app.ui.reference

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * 「学习资源」页两个 tab 之间共享的搜索词。
 *
 * 为什么需要它：搜索入口现在只有一个，放在容器的标题行（[ReferenceContainerFragment]），
 * 而真正要过滤的列表在另一个 fragment（ViewPager2 里的 [com.sknote.app.ui.snippet.SnippetListFragment]）。
 * 两者没有直接引用关系，所以用一个作用域挂在**容器**上的 ViewModel 传值：
 * 容器用 `by viewModels()` 取，子 fragment 用 `by viewModels({ requireParentFragment() })` 取，
 * 拿到的是同一个实例，且能扛住旋转重建（配置变更时 ViewModel 不销毁，输入框里的词不会丢）。
 *
 * 只放一个字段是刻意的：参考手册 tab 的搜索走独立搜索页（`referenceSearchFragment`），
 * 不经过这里。
 */
class LearningSearchViewModel : ViewModel() {

    private val _query = MutableLiveData("")
    val query: LiveData<String> = _query

    /** 与当前值相同时不重复派发，避免同一个词触发两次网络请求。 */
    fun setQuery(value: String) {
        if (_query.value != value) _query.value = value
    }
}
