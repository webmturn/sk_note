package com.sknote.app.ui.notification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Notification
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class NotificationViewModel : ViewModel() {

    private val _notifications = MutableLiveData<List<Notification>>()
    val notifications: LiveData<List<Notification>> = _notifications

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = ApiClient.getService().getNotifications()
                if (response.isSuccessful) {
                    val body = response.body()
                    _notifications.value = body?.notifications ?: emptyList()
                    _unreadCount.value = body?.unreadCount ?: 0
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

    fun markRead(notificationId: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().markNotificationRead(notificationId)
                if (response.isSuccessful) {
                    loadNotifications()
                }
            } catch (_: Exception) { }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().markAllNotificationsRead()
                if (response.isSuccessful) {
                    loadNotifications()
                }
            } catch (_: Exception) { }
        }
    }

    fun deleteNotification(notificationId: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().deleteNotification(notificationId)
                if (response.isSuccessful) {
                    loadNotifications()
                }
            } catch (_: Exception) { }
        }
    }
}
