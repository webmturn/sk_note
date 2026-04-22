package com.sknote.app.ui.share

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.CreateShareRequest
import com.sknote.app.data.model.UpdateShareRequest
import com.sknote.app.databinding.FragmentCreateShareBinding
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CreateShareFragment : Fragment() {

    private data class ShareDraftState(
        val title: String,
        val description: String,
        val category: String,
        val downloadUrl: String,
        val password: String,
        val fileSize: String
    )

    private var _binding: FragmentCreateShareBinding? = null
    private val binding get() = _binding!!
    private var shareId: Long? = null
    private var isLoggedIn: Boolean = false
    private var hasLoadedInitialData = false
    private var isLoadingInitialData = false
    private var currentDraftState = ShareDraftState("", "", "general", "", "", "")
    private var initialDraftState = ShareDraftState("", "", "general", "", "", "")

    private fun isFragmentUsable(): Boolean {
        return _binding != null && isAdded && context != null
    }

    private val categoryKeys get() = ShareCategories.keys
    private val categoryLabels get() = ShareCategories.labels
    private var selectedCategory = "general"

    companion object {
        private const val STATE_CURRENT_TITLE = "state_current_title"
        private const val STATE_CURRENT_DESCRIPTION = "state_current_description"
        private const val STATE_CURRENT_CATEGORY = "state_current_category"
        private const val STATE_CURRENT_DOWNLOAD_URL = "state_current_download_url"
        private const val STATE_CURRENT_PASSWORD = "state_current_password"
        private const val STATE_CURRENT_FILE_SIZE = "state_current_file_size"
        private const val STATE_INITIAL_TITLE = "state_initial_title"
        private const val STATE_INITIAL_DESCRIPTION = "state_initial_description"
        private const val STATE_INITIAL_CATEGORY = "state_initial_category"
        private const val STATE_INITIAL_DOWNLOAD_URL = "state_initial_download_url"
        private const val STATE_INITIAL_PASSWORD = "state_initial_password"
        private const val STATE_INITIAL_FILE_SIZE = "state_initial_file_size"
        private const val STATE_HAS_LOADED_INITIAL_DATA = "state_has_loaded_initial_data"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedInstanceState?.let {
            currentDraftState = ShareDraftState(
                title = it.getString(STATE_CURRENT_TITLE).orEmpty(),
                description = it.getString(STATE_CURRENT_DESCRIPTION).orEmpty(),
                category = it.getString(STATE_CURRENT_CATEGORY).orEmpty().ifEmpty { "general" },
                downloadUrl = it.getString(STATE_CURRENT_DOWNLOAD_URL).orEmpty(),
                password = it.getString(STATE_CURRENT_PASSWORD).orEmpty(),
                fileSize = it.getString(STATE_CURRENT_FILE_SIZE).orEmpty()
            )
            initialDraftState = ShareDraftState(
                title = it.getString(STATE_INITIAL_TITLE).orEmpty(),
                description = it.getString(STATE_INITIAL_DESCRIPTION).orEmpty(),
                category = it.getString(STATE_INITIAL_CATEGORY).orEmpty().ifEmpty { currentDraftState.category },
                downloadUrl = it.getString(STATE_INITIAL_DOWNLOAD_URL).orEmpty(),
                password = it.getString(STATE_INITIAL_PASSWORD).orEmpty(),
                fileSize = it.getString(STATE_INITIAL_FILE_SIZE).orEmpty()
            )
            selectedCategory = currentDraftState.category
            hasLoadedInitialData = it.getBoolean(STATE_HAS_LOADED_INITIAL_DATA, false)
        }

        shareId = arguments?.getLong("share_id")?.takeIf { it > 0L }
        val navOptions = slideNavOptions()
        binding.toolbar.setNavigationOnClickListener { confirmExit() }
        binding.toolbar.title = if (shareId == null) "发布分享" else "编辑分享"
        binding.btnGoLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment, null, navOptions)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { confirmExit() }
            }
        )

        setupForm()
        applyDraftState(currentDraftState)
        refreshAuthState()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            refreshAuthState()
        }
    }

    private fun setupForm() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryLabels)
        binding.spinnerCategory.setAdapter(adapter)
        val initialIndex = categoryKeys.indexOf(selectedCategory).takeIf { it >= 0 } ?: 0
        binding.spinnerCategory.setText(categoryLabels[initialIndex], false)
        binding.spinnerCategory.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = categoryKeys[position]
            binding.layoutCategory.error = null
        }

        setupFieldValidation()
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun applyDraftState(state: ShareDraftState) {
        if (!isFragmentUsable()) return
        binding.etTitle.setText(state.title)
        binding.etDescription.setText(state.description)
        binding.etDownloadUrl.setText(state.downloadUrl)
        binding.etPassword.setText(state.password)
        binding.etFileSize.setText(state.fileSize)
        selectedCategory = state.category
        val categoryIndex = categoryKeys.indexOf(state.category).takeIf { it >= 0 } ?: 0
        binding.spinnerCategory.setText(categoryLabels[categoryIndex], false)
    }

    private fun refreshAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (!isFragmentUsable()) return@launch
            renderAuthState()
            if (isLoggedIn && !hasLoadedInitialData && !isLoadingInitialData) {
                val editingId = shareId
                if (editingId != null) {
                    loadShare(editingId)
                } else {
                    hasLoadedInitialData = true
                }
            }
        }
    }

    private fun renderAuthState() {
        binding.layoutAuthRequired.visibility = if (isLoggedIn) View.GONE else View.VISIBLE
        binding.scrollContent.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        binding.layoutSubmitBar.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
    }

    private fun loadShare(id: Long) {
        isLoadingInitialData = true
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "加载中..."
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getShare(id)
                if (!isFragmentUsable()) return@launch
                if (response.isSuccessful) {
                    val share = response.body()?.share ?: return@launch
                    binding.etTitle.setText(share.title)
                    binding.etDescription.setText(share.description.orEmpty())
                    binding.etDownloadUrl.setText(share.downloadUrl.orEmpty())
                    binding.etPassword.setText(share.downloadPwd.orEmpty())
                    binding.etFileSize.setText(share.fileSize.orEmpty())
                    val categoryIndex = categoryKeys.indexOf(share.category.orEmpty()).takeIf { it >= 0 } ?: 0
                    selectedCategory = categoryKeys[categoryIndex]
                    binding.spinnerCategory.setText(categoryLabels[categoryIndex], false)
                    hasLoadedInitialData = true
                    captureInitialDraftState()
                } else {
                    Snackbar.make(binding.root, "加载失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isFragmentUsable()) return@launch
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                isLoadingInitialData = false
                _binding?.btnSubmit?.isEnabled = true
                _binding?.btnSubmit?.text = if (shareId == null) "发布分享" else "保存修改"
            }
        }
    }

    private fun submit() {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val downloadUrl = binding.etDownloadUrl.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val fileSize = binding.etFileSize.text.toString().trim()

        if (!validateForm(title, downloadUrl)) {
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = if (shareId == null) "发布中..." else "保存中..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val editingId = shareId
                val response = if (editingId == null) {
                    ApiClient.getService().createShare(
                        CreateShareRequest(
                            title = title,
                            description = description,
                            category = selectedCategory,
                            downloadUrl = downloadUrl,
                            downloadPwd = password,
                            fileSize = fileSize
                        )
                    )
                } else {
                    ApiClient.getService().updateShare(
                        editingId,
                        UpdateShareRequest(
                            title = title,
                            description = description,
                            category = selectedCategory,
                            downloadUrl = downloadUrl,
                            downloadPwd = password,
                            fileSize = fileSize
                        )
                    )
                }
                if (!isFragmentUsable()) return@launch
                if (response.isSuccessful) {
                    val message = if (editingId == null) "分享成功" else "更新成功"
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_shares", true)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_share_detail", true)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("share_result_message", message)
                    findNavController().navigateUp()
                } else {
                    Snackbar.make(binding.root, if (editingId == null) "发布失败: ${response.code()}" else "保存失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isFragmentUsable()) return@launch
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                _binding?.btnSubmit?.isEnabled = true
                _binding?.btnSubmit?.text = if (shareId == null) "发布分享" else "保存修改"
            }
        }
    }

    private fun setupFieldValidation() {
        binding.etTitle.addTextChangedListener(simpleWatcher { binding.layoutTitle.error = null })
        binding.etDescription.addTextChangedListener(simpleWatcher { binding.layoutDescription.error = null })
        binding.etDownloadUrl.addTextChangedListener(simpleWatcher { binding.layoutDownloadUrl.error = null })
        binding.etPassword.addTextChangedListener(simpleWatcher { binding.layoutPassword.error = null })
        binding.etFileSize.addTextChangedListener(simpleWatcher { binding.layoutFileSize.error = null })
    }

    private fun simpleWatcher(onChanged: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onChanged()
            }
        }
    }

    private fun clearFieldErrors() {
        binding.layoutTitle.error = null
        binding.layoutDescription.error = null
        binding.layoutCategory.error = null
        binding.layoutDownloadUrl.error = null
        binding.layoutPassword.error = null
        binding.layoutFileSize.error = null
    }

    private fun validateForm(title: String, downloadUrl: String): Boolean {
        clearFieldErrors()
        var valid = true
        if (title.isEmpty()) {
            binding.layoutTitle.error = "请输入标题"
            valid = false
        }
        if (downloadUrl.isEmpty()) {
            binding.layoutDownloadUrl.error = "请输入下载链接"
            valid = false
        }
        return valid
    }

    private fun captureInitialDraftState() {
        initialDraftState = currentDraftState()
    }

    private fun currentDraftState(): ShareDraftState {
        return ShareDraftState(
            title = binding.etTitle.text?.toString()?.trim().orEmpty(),
            description = binding.etDescription.text?.toString()?.trim().orEmpty(),
            category = selectedCategory,
            downloadUrl = binding.etDownloadUrl.text?.toString()?.trim().orEmpty(),
            password = binding.etPassword.text?.toString()?.trim().orEmpty(),
            fileSize = binding.etFileSize.text?.toString()?.trim().orEmpty()
        )
    }

    private fun hasUnsavedChanges(): Boolean {
        return currentDraftState() != initialDraftState
    }

    private fun confirmExit() {
        if (!hasUnsavedChanges()) {
            findNavController().navigateUp()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("放弃本次编辑？")
            .setMessage("当前填写的分享内容尚未保存，确定返回吗？")
            .setPositiveButton("返回") { _, _ -> findNavController().navigateUp() }
            .setNegativeButton("继续编辑", null)
            .show()
    }

    override fun onPause() {
        if (_binding != null) {
            currentDraftState = currentDraftState()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (_binding != null) {
            currentDraftState = currentDraftState()
        }
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CURRENT_TITLE, currentDraftState.title)
        outState.putString(STATE_CURRENT_DESCRIPTION, currentDraftState.description)
        outState.putString(STATE_CURRENT_CATEGORY, currentDraftState.category)
        outState.putString(STATE_CURRENT_DOWNLOAD_URL, currentDraftState.downloadUrl)
        outState.putString(STATE_CURRENT_PASSWORD, currentDraftState.password)
        outState.putString(STATE_CURRENT_FILE_SIZE, currentDraftState.fileSize)
        outState.putString(STATE_INITIAL_TITLE, initialDraftState.title)
        outState.putString(STATE_INITIAL_DESCRIPTION, initialDraftState.description)
        outState.putString(STATE_INITIAL_CATEGORY, initialDraftState.category)
        outState.putString(STATE_INITIAL_DOWNLOAD_URL, initialDraftState.downloadUrl)
        outState.putString(STATE_INITIAL_PASSWORD, initialDraftState.password)
        outState.putString(STATE_INITIAL_FILE_SIZE, initialDraftState.fileSize)
        outState.putBoolean(STATE_HAS_LOADED_INITIAL_DATA, hasLoadedInitialData)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
