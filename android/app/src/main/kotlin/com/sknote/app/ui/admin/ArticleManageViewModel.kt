package com.sknote.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Article
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class ArticleManageViewModel : ViewModel() {

    private val _articles = MutableLiveData<List<Article>>()
    val articles: LiveData<List<Article>> = _articles

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun clearMessage() { _message.value = null }

    private var lastLoadTime = 0L
    private val cacheDuration = 60_000L

    fun loadArticles(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastLoadTime < cacheDuration && _articles.value != null) {
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getArticles(page = 1, limit = 100)
                if (response.isSuccessful) {
                    _articles.value = response.body()?.articles ?: emptyList()
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

    fun deleteArticle(id: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().deleteArticle(id)
                if (response.isSuccessful) {
                    _message.value = "文章已删除"
                    loadArticles(force = true)
                } else {
                    _message.value = "删除失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }
}
