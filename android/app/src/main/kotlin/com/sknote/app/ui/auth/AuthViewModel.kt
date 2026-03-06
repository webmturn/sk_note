package com.sknote.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.AuthResponse
import com.sknote.app.data.model.LoginRequest
import com.sknote.app.data.model.RegisterRequest
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _authResult = MutableLiveData<AuthResponse>()
    val authResult: LiveData<AuthResponse> = _authResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loggedOut = MutableLiveData<Boolean>()
    val loggedOut: LiveData<Boolean> = _loggedOut

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().login(LoginRequest(username, password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.token != null && body.user != null) {
                        ApiClient.getTokenManager().saveAuth(body.token, body.user.username, body.user.role)
                        _authResult.value = body
                    } else {
                        _authResult.value = AuthResponse(null, null, "登录失败：服务端响应异常")
                    }
                } else {
                    _authResult.value = AuthResponse(null, null, "登录失败")
                }
            } catch (e: Exception) {
                _authResult.value = AuthResponse(null, null, ErrorUtil.friendlyMessage(e))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().register(RegisterRequest(username, email, password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.token != null && body.user != null) {
                        ApiClient.getTokenManager().saveAuth(body.token, body.user.username, body.user.role)
                        _authResult.value = body
                    } else {
                        _authResult.value = AuthResponse(null, null, "注册失败：服务端响应异常")
                    }
                } else {
                    _authResult.value = AuthResponse(null, null, "注册失败")
                }
            } catch (e: Exception) {
                _authResult.value = AuthResponse(null, null, ErrorUtil.friendlyMessage(e))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            ApiClient.getTokenManager().clearAuth()
            _loggedOut.value = true
        }
    }
}
