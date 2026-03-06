package com.sknote.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.ResetPasswordRequest
import com.sknote.app.data.model.UpdateRoleRequest
import com.sknote.app.data.model.User
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class UserManageViewModel : ViewModel() {

    private val _users = MutableLiveData<List<User>>()
    val users: LiveData<List<User>> = _users

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun clearMessage() { _message.value = null }

    private var lastLoadTime = 0L
    private val cacheDuration = 60_000L
    private var currentSearch: String? = null

    fun loadUsers(force: Boolean = false, search: String? = null) {
        val now = System.currentTimeMillis()
        if (!force && search == currentSearch && now - lastLoadTime < cacheDuration && _users.value != null) {
            return
        }
        currentSearch = search
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getUsers(page = 1, limit = 100, search = search)
                if (response.isSuccessful) {
                    _users.value = response.body()?.users ?: emptyList()
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

    fun updateRole(userId: Long, newRole: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().updateUserRole(userId, UpdateRoleRequest(newRole))
                if (response.isSuccessful) {
                    _message.value = "角色已更新为 $newRole"
                    loadUsers(force = true, search = currentSearch)
                } else {
                    val error = response.errorBody()?.string() ?: "未知错误"
                    _message.value = "更新失败: $error"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().deleteUser(userId)
                if (response.isSuccessful) {
                    _message.value = "用户已删除"
                    loadUsers(force = true, search = currentSearch)
                } else {
                    val error = response.errorBody()?.string() ?: "未知错误"
                    _message.value = "删除失败: $error"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun resetPassword(userId: Long, newPassword: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().resetUserPassword(userId, ResetPasswordRequest(newPassword))
                if (response.isSuccessful) {
                    _message.value = "密码已重置"
                } else {
                    val error = response.errorBody()?.string() ?: "未知错误"
                    _message.value = "重置失败: $error"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }
}
