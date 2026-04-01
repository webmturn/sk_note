package com.sknote.app.ui.share

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.CreateShareRequest
import com.sknote.app.data.model.UpdateShareRequest
import com.sknote.app.databinding.FragmentCreateShareBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CreateShareFragment : Fragment() {

    private var _binding: FragmentCreateShareBinding? = null
    private val binding get() = _binding!!
    private var shareId: Long? = null
    private var isLoggedIn: Boolean = false
    private var hasLoadedInitialData = false

    private fun isFragmentUsable(): Boolean {
        return _binding != null && isAdded && context != null
    }

    private val categoryKeys get() = ShareCategories.keys
    private val categoryLabels get() = ShareCategories.labels
    private var selectedCategory = "general"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shareId = arguments?.getLong("share_id")?.takeIf { it > 0L }
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.title = if (shareId == null) "发布分享" else "编辑分享"
        binding.btnGoLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }

        setupForm()
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
        binding.spinnerCategory.setText(categoryLabels[0], false)
        binding.spinnerCategory.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = categoryKeys[position]
        }

        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun refreshAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (!isFragmentUsable()) return@launch
            renderAuthState()
            if (isLoggedIn && !hasLoadedInitialData) {
                shareId?.let { loadShare(it) }
                hasLoadedInitialData = true
            }
        }
    }

    private fun renderAuthState() {
        binding.layoutAuthRequired.visibility = if (isLoggedIn) View.GONE else View.VISIBLE
        binding.scrollContent.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        binding.layoutSubmitBar.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
    }

    private fun loadShare(id: Long) {
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
                } else {
                    Snackbar.make(binding.root, "加载失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
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

    private fun submit() {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val downloadUrl = binding.etDownloadUrl.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val fileSize = binding.etFileSize.text.toString().trim()

        if (title.isEmpty()) {
            Snackbar.make(binding.root, "请输入标题", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (downloadUrl.isEmpty()) {
            Snackbar.make(binding.root, "请输入下载链接", Snackbar.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
