package com.sknote.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.ChangePasswordRequest
import com.sknote.app.data.model.User
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class ProfileEditViewModel : ViewModel() {

    private val _user = MutableLiveData<User>()
    val user: LiveData<User> = _user

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getMe()
                if (response.isSuccessful) {
                    response.body()?.get("user")?.let { _user.value = it }
                } else {
                    _error.value = "加载用户信息失败"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(nickname: String, username: String, bio: String, avatarUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val params = mutableMapOf<String, String>()
                params["nickname"] = nickname
                params["username"] = username
                params["bio"] = bio
                params["avatar_url"] = avatarUrl
                val response = ApiClient.getService().updateProfile(params)
                if (response.isSuccessful) {
                    _message.value = "资料更新成功"
                    ApiClient.getTokenManager().updateUsername(username)
                    ApiClient.getTokenManager().updateNickname(nickname)
                    loadProfile()
                } else {
                    val serverMsg = try {
                        org.json.JSONObject(response.errorBody()?.string() ?: "").optString("error", "")
                    } catch (_: Exception) { "" }
                    _error.value = serverMsg.ifEmpty {
                        when (response.code()) {
                            409 -> "账号已被占用"
                            400 -> "输入不符合要求"
                            else -> "更新失败: ${response.code()}"
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().changePassword(
                    ChangePasswordRequest(oldPassword, newPassword)
                )
                if (response.isSuccessful) {
                    _message.value = "密码修改成功"
                } else {
                    val serverMsg = try {
                        org.json.JSONObject(response.errorBody()?.string() ?: "").optString("error", "")
                    } catch (_: Exception) { "" }
                    _error.value = serverMsg.ifEmpty {
                        when (response.code()) {
                            401 -> "旧密码错误"
                            400 -> "新密码至少6位"
                            else -> "修改失败: ${response.code()}"
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessage() { _message.value = null }
    fun clearError() { _error.value = null }
}
