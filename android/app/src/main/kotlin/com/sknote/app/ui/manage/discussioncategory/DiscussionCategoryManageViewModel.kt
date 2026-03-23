package com.sknote.app.ui.manage.discussioncategory

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.CreateDiscussionCategoryRequest
import com.sknote.app.data.model.DiscussionCategory
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class DiscussionCategoryManageViewModel : ViewModel() {

    private val _categories = MutableLiveData<List<DiscussionCategory>>()
    val categories: LiveData<List<DiscussionCategory>> = _categories

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().getDiscussionCategories()
                if (response.isSuccessful) {
                    _categories.value = response.body()?.categories ?: emptyList()
                } else {
                    _message.value = "加载失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun createCategory(request: CreateDiscussionCategoryRequest) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().createDiscussionCategory(request)
                if (response.isSuccessful) {
                    _message.value = "讨论分类创建成功"
                    loadCategories()
                } else {
                    _message.value = "创建失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun updateCategory(id: Long, request: CreateDiscussionCategoryRequest) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().updateDiscussionCategory(id, request)
                if (response.isSuccessful) {
                    _message.value = "讨论分类更新成功"
                    loadCategories()
                } else {
                    _message.value = "更新失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().deleteDiscussionCategory(id)
                if (response.isSuccessful) {
                    _message.value = "讨论分类已删除"
                    loadCategories()
                } else {
                    _message.value = "删除失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }
}
