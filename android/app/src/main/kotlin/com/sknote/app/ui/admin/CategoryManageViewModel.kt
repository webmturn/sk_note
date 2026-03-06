package com.sknote.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Category
import com.sknote.app.data.model.CreateCategoryRequest
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class CategoryManageViewModel : ViewModel() {

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun clearMessage() { _message.value = null }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().getCategories()
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

    fun createCategory(request: CreateCategoryRequest) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().createCategory(request)
                if (response.isSuccessful) {
                    _message.value = "分类创建成功"
                    loadCategories()
                } else {
                    _message.value = "创建失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun updateCategory(id: Long, request: CreateCategoryRequest) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().updateCategory(id, request)
                if (response.isSuccessful) {
                    _message.value = "分类更新成功"
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
                val response = ApiClient.getService().deleteCategory(id)
                if (response.isSuccessful) {
                    _message.value = "分类已删除"
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
