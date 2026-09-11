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

    // 发布结果用状态而非回调回传：回调会把 Fragment 的 binding 装进 ViewModel，
    // 请求在途时旋转/切后台会因 _binding == null 直接丢弃结果，导致列表不刷新。
    private val _saveSuccess = MutableLiveData<Boolean?>()
    val saveSuccess: LiveData<Boolean?> = _saveSuccess

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun onSaveHandled() { _saveSuccess.value = null }
    fun onErrorHandled() { _error.value = null }

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

    fun createRelease(request: CreateAppReleaseRequest) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().createAppRelease(request)
                if (response.isSuccessful) {
                    _message.value = "版本发布成功"
                    loadReleases()
                    _saveSuccess.value = true
                } else {
                    _error.value = "发布失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = ErrorUtil.friendlyMessage(e)
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
