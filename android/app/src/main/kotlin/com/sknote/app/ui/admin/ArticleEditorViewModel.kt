package com.sknote.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Article
import com.sknote.app.data.model.Category
import com.sknote.app.data.model.CreateArticleRequest
import com.sknote.app.data.model.UpdateArticleRequest
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class ArticleEditorViewModel : ViewModel() {

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _article = MutableLiveData<Article>()
    val article: LiveData<Article> = _article

    private val _saveSuccess = MutableLiveData<Boolean?>()
    val saveSuccess: LiveData<Boolean?> = _saveSuccess

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun onSaveHandled() { _saveSuccess.value = null }
    fun onErrorHandled() { _error.value = null }

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadCategories() {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().getCategories()
                if (response.isSuccessful) {
                    _categories.value = response.body()?.categories ?: emptyList()
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun loadArticle(id: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().getArticle(id)
                if (response.isSuccessful) {
                    _article.value = response.body()?.article
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun createArticle(request: CreateArticleRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().createArticle(request)
                if (response.isSuccessful) {
                    _saveSuccess.value = true
                } else {
                    _error.value = "创建失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateArticle(id: Long, request: UpdateArticleRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().updateArticle(id, request)
                if (response.isSuccessful) {
                    _saveSuccess.value = true
                } else {
                    _error.value = "更新失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
