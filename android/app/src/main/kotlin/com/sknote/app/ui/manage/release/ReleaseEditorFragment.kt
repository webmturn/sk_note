package com.sknote.app.ui.manage.release

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.BuildConfig
import com.sknote.app.data.model.CreateAppReleaseRequest
import com.sknote.app.databinding.FragmentReleaseEditorBinding
import com.sknote.app.util.requireRolesOrExit
import kotlinx.coroutines.launch

class ReleaseEditorFragment : Fragment() {

    private var _binding: FragmentReleaseEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReleaseManageViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReleaseEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            if (!requireRolesOrExit(setOf("admin"), "仅管理员可发布版本")) {
                return@launch
            }
            setupForm()
        }
    }

    private fun setupForm() {
        binding.tvCurrent.text = "当前 App 版本：v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})"
        // Pre-fill version code with current+1 to reduce typing
        binding.etVersionCode.setText((BuildConfig.VERSION_CODE + 1).toString())

        binding.btnSave.setOnClickListener { submit() }
    }

    private fun submit() {
        val versionName = binding.etVersionName.text?.toString()?.trim().orEmpty()
        val versionCodeText = binding.etVersionCode.text?.toString()?.trim().orEmpty()
        val changelog = binding.etChangelog.text?.toString()?.trim().orEmpty()
        val downloadUrl = binding.etDownloadUrl.text?.toString()?.trim().orEmpty()
        val fileSizeText = binding.etFileSize.text?.toString()?.trim().orEmpty()
        val releaseUrl = binding.etReleaseUrl.text?.toString()?.trim().orEmpty()

        binding.layoutVersionName.error = null
        binding.layoutVersionCode.error = null
        binding.layoutFileSize.error = null
        binding.layoutDownloadUrl.error = null
        binding.layoutReleaseUrl.error = null

        if (versionName.isEmpty()) {
            binding.layoutVersionName.error = "版本号不能为空"
            return
        }
        if (!versionName.matches(Regex("^[vV]?\\d+(\\.\\d+)*$"))) {
            binding.layoutVersionName.error = "版本号格式无效，请使用如 1.2.3 的形式"
            return
        }
        val versionCode = versionCodeText.toIntOrNull()
        if (versionCode == null || versionCode <= 0) {
            binding.layoutVersionCode.error = "版本代码必须是正整数"
            return
        }
        val fileSize = if (fileSizeText.isEmpty()) 0L else fileSizeText.toLongOrNull() ?: run {
            binding.layoutFileSize.error = "文件大小必须是数字（字节）"
            return
        }
        if (downloadUrl.isNotEmpty() && !downloadUrl.startsWith("http")) {
            binding.layoutDownloadUrl.error = "下载链接需以 http(s) 开头"
            return
        }
        if (releaseUrl.isNotEmpty() && !releaseUrl.startsWith("http")) {
            binding.layoutReleaseUrl.error = "发布页链接需以 http(s) 开头"
            return
        }

        binding.btnSave.isEnabled = false
        val request = CreateAppReleaseRequest(
            versionName = versionName,
            versionCode = versionCode,
            changelog = changelog,
            downloadUrl = downloadUrl,
            fileSize = fileSize,
            releaseUrl = releaseUrl,
        )
        viewModel.createRelease(request) { success, message ->
            if (_binding == null) return@createRelease
            binding.btnSave.isEnabled = true
            if (success) {
                findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_releases", true)
                findNavController().navigateUp()
            } else {
                Snackbar.make(binding.root, message ?: "发布失败", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
