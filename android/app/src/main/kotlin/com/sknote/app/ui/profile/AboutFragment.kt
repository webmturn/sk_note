package com.sknote.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.BuildConfig
import com.sknote.app.R
import com.sknote.app.databinding.FragmentAboutBinding
import com.sknote.app.util.slideNavOptions

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.tvVersionInfo.text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        binding.rowGithub.setOnClickListener {
            openUrl("https://github.com/webmturn/sk_note")
        }

        binding.rowFeedback.setOnClickListener {
            findNavController().navigate(R.id.feedbackFragment, null, slideNavOptions())
        }

        binding.rowLicense.setOnClickListener {
            showLicenseDialog()
        }
    }

    private fun showLicenseDialog() {
        val licenses = buildString {
            appendLine("本项目使用了以下开源库：")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("▸ Retrofit 2")
            appendLine("  Square, Inc.")
            appendLine("  Apache License 2.0")
            appendLine()
            appendLine("▸ OkHttp 3")
            appendLine("  Square, Inc.")
            appendLine("  Apache License 2.0")
            appendLine()
            appendLine("▸ Kotlin Coroutines")
            appendLine("  JetBrains")
            appendLine("  Apache License 2.0")
            appendLine()
            appendLine("▸ AndroidX Libraries")
            appendLine("  Google / AOSP")
            appendLine("  Apache License 2.0")
            appendLine()
            appendLine("▸ Material Components for Android")
            appendLine("  Google")
            appendLine("  Apache License 2.0")
            appendLine()
            appendLine("▸ DataStore Preferences")
            appendLine("  Google / AndroidX")
            appendLine("  Apache License 2.0")
            appendLine()
            appendLine("▸ Navigation Component")
            appendLine("  Google / AndroidX")
            appendLine("  Apache License 2.0")
            appendLine()
            appendLine("▸ Gson")
            appendLine("  Google")
            appendLine("  Apache License 2.0")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("开源许可证")
            .setMessage(licenses)
            .setPositiveButton("确定", null)
            .show()
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
