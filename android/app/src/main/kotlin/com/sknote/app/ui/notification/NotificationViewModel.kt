package com.sknote.app.ui.notification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Notification
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch
import retrofit2.Response

data class NotificationActionEvent(
    val message: String,
    val isError: Boolean = false
)

class NotificationViewModel : ViewModel() {

    private val _notifications = MutableLiveData<List<Notification>>()
    val notifications: LiveData<List<Notification>> = _notifications

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isActionLoading = MutableLiveData(false)
    val isActionLoading: LiveData<Boolean> = _isActionLoading

    private val _actionEvent = MutableLiveData<NotificationActionEvent?>()
    val actionEvent: LiveData<NotificationActionEvent?> = _actionEvent

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
        runAction(successFallback = "已全部标为已读") {
            ApiClient.getService().markAllNotificationsRead()
        }
    }

    fun deleteAllNotifications() {
        runAction(successFallback = "已清空全部通知") {
            ApiClient.getService().deleteAllNotifications()
        }
    }

    fun deleteNotification(notificationId: Long) {
        runAction(successFallback = "删除成功") {
            ApiClient.getService().deleteNotification(notificationId)
        }
    }

    fun onActionEventHandled() {
        _actionEvent.value = null
    }

    private fun runAction(
        successFallback: String,
        request: suspend () -> Response<com.sknote.app.data.model.MessageResponse>
    ) {
        if (_isActionLoading.value == true) return
        viewModelScope.launch {
            _isActionLoading.value = true
            try {
                val response = request()
                if (response.isSuccessful) {
                    val message = response.body()?.message.orEmpty().ifBlank { successFallback }
                    _actionEvent.value = NotificationActionEvent(message)
                    loadNotifications()
                } else {
                    _actionEvent.value = NotificationActionEvent(
                        response.body()?.error ?: "操作失败: ${response.code()}",
                        isError = true
                    )
                }
            } catch (e: Exception) {
                _actionEvent.value = NotificationActionEvent(ErrorUtil.friendlyMessage(e), isError = true)
            } finally {
                _isActionLoading.value = false
            }
        }
    }
}
