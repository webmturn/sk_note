package com.sknote.app.ui.reference

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sknote.app.data.model.ReferenceItem

class ReferenceDetailViewModel : ViewModel() {

    private val _reference = MutableLiveData<ReferenceItem>()
    val reference: LiveData<ReferenceItem> = _reference

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadReference(id: Long) {
        _isLoading.value = true
        _error.value = null
        val item = ReferenceData.getAllItems().find { it.id == id }
        if (item != null) {
            _reference.value = item
        } else {
            _error.value = "未找到该参考项"
        }
        _isLoading.value = false
    }
}
