package com.sknote.app.ui.manage.release

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.AppRelease
import com.sknote.app.data.model.CreateAppReleaseRequest
import com.sknote.app.data.model.UpdateReleaseActiveRequest
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class ReleaseManageViewModel : ViewModel() {

    private val _releases = MutableLiveData<List<AppRelease>>()
    val releases: LiveData<List<AppRelease>> = _releases

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun clearMessage() { _message.value = null }

    fun loadReleases() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getAppReleases()
                if (response.isSuccessful) {
                    _releases.value = response.body()?.releases ?: emptyList()
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

    fun createRelease(request: CreateAppReleaseRequest, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().createAppRelease(request)
                if (response.isSuccessful) {
                    onResult(true, null)
                    _message.value = "版本发布成功"
                    loadReleases()
                } else {
                    onResult(false, "发布失败: ${response.code()}")
                }
            } catch (e: Exception) {
                onResult(false, ErrorUtil.friendlyMessage(e))
            }
        }
    }

    fun deleteRelease(id: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().deleteAppRelease(id)
                if (response.isSuccessful) {
                    _message.value = "版本已删除"
                    loadReleases()
                } else {
                    _message.value = "删除失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun setReleaseActive(id: Long, active: Boolean) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().setAppReleaseActive(
                    id, UpdateReleaseActiveRequest(if (active) 1 else 0)
                )
                if (response.isSuccessful) {
                    _message.value = if (active) "已上架" else "已下架"
                    loadReleases()
                } else {
                    _message.value = "操作失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }
}
