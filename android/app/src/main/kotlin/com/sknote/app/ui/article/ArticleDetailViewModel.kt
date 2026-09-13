package com.sknote.app.ui.article

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Article
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class ArticleDetailViewModel : ViewModel() {

    private val _article = MutableLiveData<Article?>()
    val article: LiveData<Article?> = _article

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * 用导航参数带来的文章先把页面渲染出来。
     *
     * 不产生网络请求，也不改变加载流程：只是把它作为 `_article` 的当前值，让页面
     * 按常规观察者路径渲染一次真实内容，详情接口回来后用同样的路径再渲染一次
     * （浏览数、点赞数可能更新）。已有真实数据时不覆盖。
     */
    fun setPrefilledArticle(article: Article) {
        if (_article.value != null) return
        _article.value = article
    }

    fun loadArticle(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getArticle(id)
                if (response.isSuccessful) {
                    val article = response.body()?.article
                    if (article != null) {
                        _article.value = article
                    } else {
                        _error.value = "文章内容为空"
                    }
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

    fun likeArticle(id: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().likeArticle(id)
                if (response.isSuccessful) {
                    loadArticle(id)
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }
}
