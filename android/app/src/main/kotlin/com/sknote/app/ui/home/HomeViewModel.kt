package com.sknote.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Article
import com.sknote.app.data.model.Category
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _articles = MutableLiveData<List<Article>>()
    val articles: LiveData<List<Article>> = _articles

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var lastLoadTime = 0L
    private val cacheDuration = 60_000L

    fun loadData(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastLoadTime < cacheDuration && _categories.value != null) {
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = ApiClient.getService().getHomeData(limit = 10)
                if (response.isSuccessful) {
                    val data = response.body()
                    _categories.value = data?.categories ?: emptyList()
                    _articles.value = data?.articles ?: emptyList()
                    lastLoadTime = System.currentTimeMillis()
                } else {
                    _error.value = "加载失败 (${response.code()})"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
