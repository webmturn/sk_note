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
import com.sknote.app.databinding.FragmentCreateShareBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CreateShareFragment : Fragment() {

    private var _binding: FragmentCreateShareBinding? = null
    private val binding get() = _binding!!

    private val categoryKeys get() = ShareCategories.keys
    private val categoryLabels get() = ShareCategories.labels
    private var selectedCategory = "general"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (!isLoggedIn) {
                Snackbar.make(binding.root, "请先登录", Snackbar.LENGTH_SHORT)
                    .setAction("去登录") { findNavController().navigate(R.id.loginFragment) }
                    .show()
                findNavController().navigateUp()
                return@launch
            }

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryLabels)
            binding.spinnerCategory.setAdapter(adapter)
            binding.spinnerCategory.setText(categoryLabels[0], false)
            binding.spinnerCategory.setOnItemClickListener { _, _, position, _ ->
                selectedCategory = categoryKeys[position]
            }

            binding.btnSubmit.setOnClickListener { submit() }
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
        binding.btnSubmit.text = "发布中..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().createShare(
                    CreateShareRequest(
                        title = title,
                        description = description,
                        category = selectedCategory,
                        downloadUrl = downloadUrl,
                        downloadPwd = password,
                        fileSize = fileSize
                    )
                )
                if (response.isSuccessful) {
                    Snackbar.make(binding.root, "分享成功", Snackbar.LENGTH_SHORT).show()
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_shares", true)
                    findNavController().navigateUp()
                } else {
                    Snackbar.make(binding.root, "发布失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "发布分享"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
