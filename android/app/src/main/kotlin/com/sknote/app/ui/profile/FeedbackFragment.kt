package com.sknote.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.BuildConfig
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentFeedbackBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FeedbackFragment : Fragment() {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // 默认选中 Bug 报告
        binding.chipBug.isChecked = true

        // 设备信息
        binding.tvDeviceInfo.text = buildDeviceInfo()

        // 提交反馈
        binding.btnSubmit.setOnClickListener { submitFeedback() }

        // GitHub Issues
        binding.rowGithub.setOnClickListener {
            openUrl("https://github.com/nichuanfang/sk_note/issues")
        }

        // 讨论区
        binding.rowDiscussion.setOnClickListener {
            findNavController().navigate(R.id.discussionListFragment)
        }
    }

    private fun buildDeviceInfo(): String {
        return buildString {
            appendLine("应用版本: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("设备型号: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("系统版本: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("屏幕密度: ${resources.displayMetrics.densityDpi}dpi")
            append("语言: ${resources.configuration.locales[0]}")
        }
    }

    private fun getTypeLabel(): String {
        return when {
            binding.chipBug.isChecked -> "Bug 报告"
            binding.chipFeature.isChecked -> "功能建议"
            else -> "其他反馈"
        }
    }

    private fun submitFeedback() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        val contact = binding.etContact.text.toString().trim()

        if (title.isEmpty()) {
            binding.tilTitle.error = "请输入标题"
            return
        }
        binding.tilTitle.error = null

        if (content.isEmpty()) {
            binding.tilContent.error = "请输入详细描述"
            return
        }
        binding.tilContent.error = null

        if (content.length > 1000) {
            binding.tilContent.error = "内容不能超过 1000 字"
            return
        }

        // 由于没有后端 API，使用讨论区发帖方式提交反馈
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("提交反馈")
            .setMessage("将通过讨论区发帖的方式提交反馈，是否继续？")
            .setNegativeButton("取消", null)
            .setPositiveButton("继续") { _, _ ->
                postFeedbackAsDiscussion(title, content, contact)
            }
            .show()
    }

    private fun postFeedbackAsDiscussion(title: String, content: String, contact: String) {
        binding.progressSubmit.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (!isLoggedIn) {
                    Snackbar.make(binding.root, "请先登录后再提交反馈", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { findNavController().navigate(R.id.loginFragment) }
                        .show()
                    return@launch
                }

                val feedbackContent = buildString {
                    appendLine("**[${getTypeLabel()}]**")
                    appendLine()
                    appendLine(content)
                    if (contact.isNotEmpty()) {
                        appendLine()
                        appendLine("---")
                        appendLine("联系方式: $contact")
                    }
                    appendLine()
                    appendLine("---")
                    appendLine("```")
                    append(binding.tvDeviceInfo.text)
                    appendLine()
                    appendLine("```")
                }

                val request = com.sknote.app.data.model.CreateDiscussionRequest(
                    title = "[反馈] $title",
                    content = feedbackContent,
                    category = "feedback"
                )
                val response = ApiClient.getService().createDiscussion(request)
                if (response.isSuccessful) {
                    Snackbar.make(binding.root, "反馈已提交，感谢你的反馈！", Snackbar.LENGTH_SHORT).show()
                    binding.etTitle.text?.clear()
                    binding.etContent.text?.clear()
                    binding.etContact.text?.clear()
                } else {
                    Snackbar.make(binding.root, "提交失败: ${response.message()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "提交失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                if (_binding != null) {
                    binding.progressSubmit.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                }
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Snackbar.make(binding.root, "无法打开链接", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
