package com.sknote.app.ui.manage.share

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.ApproveShareRequest
import com.sknote.app.data.model.Share
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class ShareManageViewModel : ViewModel() {

    enum class FilterStatus(val backendValue: String?) {
        ALL(null),
        PENDING("pending"),
        APPROVED("approved"),
    }

    private val _shares = MutableLiveData<List<Share>>()
    val shares: LiveData<List<Share>> = _shares

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _filter = MutableLiveData(FilterStatus.ALL)
    val filter: LiveData<FilterStatus> = _filter

    fun clearMessage() { _message.value = null }

    fun setFilter(status: FilterStatus) {
        if (_filter.value == status) return
        _filter.value = status
        loadShares()
    }

    fun loadShares() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.getService().getSharesManage(
                    page = 1,
                    limit = 100,
                    status = _filter.value?.backendValue,
                )
                if (response.isSuccessful) {
                    _shares.value = response.body()?.shares ?: emptyList()
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

    fun approveShare(id: Long, approve: Boolean) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService()
                    .approveShare(id, ApproveShareRequest(approve))
                if (response.isSuccessful) {
                    _message.value = if (approve) "已批准" else "已取消批准"
                    loadShares()
                } else {
                    _message.value = "审核失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }

    fun deleteShare(id: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().deleteShare(id)
                if (response.isSuccessful) {
                    _message.value = "分享已删除"
                    loadShares()
                } else {
                    _message.value = "删除失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _message.value = ErrorUtil.friendlyMessage(e)
            }
        }
    }
}
