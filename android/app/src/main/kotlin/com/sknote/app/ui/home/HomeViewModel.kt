package com.sknote.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Article
import com.sknote.app.data.model.Category
import com.sknote.app.data.model.Share
import com.sknote.app.util.ErrorUtil
import com.sknote.app.util.StartupWarmupManager
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _articles = MutableLiveData<List<Article>>()
    val articles: LiveData<List<Article>> = _articles

    private val _latestShares = MutableLiveData<List<Share>>()
    val latestShares: LiveData<List<Share>> = _latestShares

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

        if (!force) {
            val warmData = StartupWarmupManager.getWarmHomeData(cacheDuration)
            if (warmData != null) {
                _categories.value = warmData.categories
                _articles.value = warmData.articles
                _latestShares.value = warmData.latestShares
                _error.value = null
                lastLoadTime = warmData.fetchedAt
                fetchHomeData(showLoading = false)
                return
            }
        }

        fetchHomeData(showLoading = true)
    }

    private fun fetchHomeData(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) {
                _isLoading.value = true
                _error.value = null
            }

            try {
                val response = ApiClient.getService().getHomeData(limit = 10)
                if (response.isSuccessful) {
                    val data = response.body()
                    val categories = data?.categories ?: emptyList()
                    val articles = data?.articles ?: emptyList()
                    val latestShares = data?.latestShares ?: emptyList()
                    _categories.value = categories
                    _articles.value = articles
                    _latestShares.value = latestShares
                    StartupWarmupManager.updateWarmHomeData(categories, articles, latestShares)
                    lastLoadTime = System.currentTimeMillis()
                    _error.value = null
                } else if (showLoading) {
                    _error.value = "加载失败 (${response.code()})"
                }
            } catch (e: Exception) {
                if (showLoading) {
                    _error.value = ErrorUtil.friendlyMessage(e)
                }
            } finally {
                if (showLoading) {
                    _isLoading.value = false
                }
            }
        }
    }
}
