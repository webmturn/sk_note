package com.sknote.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
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
import com.sknote.app.SkNoteApp
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentSettingsBinding
import com.sknote.app.util.AppUpdateManager
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var currentScrollY = 0

    companion object {
        private const val STATE_SCROLL_Y = "state_scroll_y"
    }

    private fun isFragmentUsable(): Boolean {
        return _binding != null && isAdded && context != null && activity != null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: currentScrollY

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"
        refreshUpdateSummary()
        restoreUiState()

        // Theme mode -> navigate to theme selection page
        viewLifecycleOwner.lifecycleScope.launch {
            val mode = ApiClient.getTokenManager().getThemeMode().first()
            if (!isFragmentUsable()) return@launch
            binding.tvThemeMode.text = SkNoteApp.themeModeLabel(mode)
        }
        binding.rowThemeMode.setOnClickListener {
            findNavController().navigate(R.id.themeSelectionFragment, null, slideNavOptions())
        }

        // Cache -> navigate to cache manage page
        binding.tvCacheSize.text = getCacheSize()
        binding.rowCacheManage.setOnClickListener {
            findNavController().navigate(R.id.cacheManageFragment, null, slideNavOptions())
        }

        // Feedback -> navigate to feedback page
        binding.rowFeedback.setOnClickListener {
            findNavController().navigate(R.id.feedbackFragment, null, slideNavOptions())
        }

        // Update
        binding.rowCheckUpdate.setOnClickListener { checkUpdate(manual = true) }

        // About -> navigate to about page
        binding.rowAbout.setOnClickListener {
            findNavController().navigate(R.id.aboutFragment, null, slideNavOptions())
        }
    }

    override fun onPause() {
        captureUiState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureUiState()
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SCROLL_Y, currentScrollY)
    }

    private fun setUpdateBusyState(isBusy: Boolean) {
        binding.progressUpdate.visibility = if (isBusy) View.VISIBLE else View.GONE
        binding.rowCheckUpdate.isEnabled = !isBusy
        binding.rowCheckUpdate.alpha = if (isBusy) 0.7f else 1f
    }

    private fun refreshUpdateSummary() {
        if (!isFragmentUsable()) return
        val skippedVersion = AppUpdateManager.getSkippedVersion(requireContext())
        val lastChecked = AppUpdateManager.getLastCheckedTime(requireContext())
        binding.tvUpdateStatus.text = when {
            skippedVersion != null && lastChecked > 0L -> "已跳过 v$skippedVersion · 上次检查 ${formatCheckedTime(lastChecked)}"
            skippedVersion != null -> "已跳过 v$skippedVersion · 点击重新检查"
            lastChecked > 0L -> "上次检查 ${formatCheckedTime(lastChecked)}"
            else -> "点击检查新版本"
        }
    }

    private fun formatCheckedTime(timeMillis: Long): String {
        return DateUtils.getRelativeTimeSpanString(
            timeMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    private fun checkUpdate(manual: Boolean) {
        setUpdateBusyState(true)
        binding.tvUpdateStatus.text = "正在检查..."

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AppUpdateManager.checkForUpdate()
            if (!isFragmentUsable()) return@launch

            setUpdateBusyState(false)

            if (result.error != null) {
                binding.tvUpdateStatus.text = "检查失败，请稍后重试"
                if (manual) {
                    Snackbar.make(binding.root, "检查更新失败: ${result.error}", Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }

            AppUpdateManager.markChecked(requireContext())

            if (result.hasUpdate && result.info != null) {
                binding.tvUpdateStatus.text = "发现新版本 v${result.info.versionName}"
                if (manual || !AppUpdateManager.isVersionSkipped(requireContext(), result.info.versionName)) {
                    showUpdateDialog(result.info)
                }
            } else {
                binding.tvUpdateStatus.text = "已是最新版本 · 刚刚检查"
            }
        }
    }

    private fun showUpdateDialog(info: AppUpdateManager.UpdateInfo) {
        if (!isFragmentUsable()) return
        val sizeText = if (info.fileSize > 0) "\n安装包大小：${AppUpdateManager.formatFileSize(info.fileSize)}" else ""
        val changelogText = if (info.changelog.isNotBlank()) "\n\n更新内容：\n${info.changelog}" else ""

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("发现新版本 v${info.versionName}")
            .setMessage("当前版本：v${BuildConfig.VERSION_NAME}$sizeText$changelogText")

        if (info.downloadUrl.isNotBlank()) {
            builder.setPositiveButton("立即更新") { _, _ ->
                startDownloadAndInstall(info)
            }
        } else {
            builder.setPositiveButton("前往下载") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(info.htmlUrl))
                startActivity(intent)
            }
        }

        builder.setNegativeButton("以后再说", null)
        builder.setNeutralButton("跳过此版本") { _, _ ->
            if (!isFragmentUsable()) return@setNeutralButton
            AppUpdateManager.skipVersion(requireContext(), info.versionName)
            refreshUpdateSummary()
        }
        builder.show()
    }

    private fun startDownloadAndInstall(info: AppUpdateManager.UpdateInfo) {
        binding.tvUpdateStatus.text = "正在下载 v${info.versionName}..."
        setUpdateBusyState(true)

        AppUpdateManager.startDownload(requireContext(), info.downloadUrl, info.versionName) { file ->
            activity?.runOnUiThread {
                if (!isFragmentUsable()) return@runOnUiThread
                setUpdateBusyState(false)
                if (file != null && file.exists()) {
                    binding.tvUpdateStatus.text = "下载完成，正在安装..."
                    AppUpdateManager.installApk(requireContext(), file)
                } else {
                    binding.tvUpdateStatus.text = "下载失败"
                    Snackbar.make(binding.root, "下载失败，请重试", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        Snackbar.make(binding.root, "正在后台下载更新...", Snackbar.LENGTH_LONG).show()
    }

    private fun getCacheSize(): String {
        val size = getDirSize(requireContext().cacheDir)
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            else -> String.format("%.1fMB", size / (1024.0 * 1024.0))
        }
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { size += getDirSize(it) }
        } else {
            size = dir.length()
        }
        return size
    }

    private fun captureUiState() {
        val currentBinding = _binding ?: return
        currentScrollY = currentBinding.root.scrollY
    }

    private fun restoreUiState() {
        if (currentScrollY == 0) return
        val currentBinding = _binding ?: return
        val targetScrollY = currentScrollY
        currentBinding.root.post {
            currentBinding.root.scrollTo(0, targetScrollY)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh theme label and cache size when returning from sub-pages
        viewLifecycleOwner.lifecycleScope.launch {
            val mode = ApiClient.getTokenManager().getThemeMode().first()
            if (!isFragmentUsable()) return@launch
            binding.tvThemeMode.text = SkNoteApp.themeModeLabel(mode)
        }
        if (isFragmentUsable()) {
            binding.tvCacheSize.text = getCacheSize()
            refreshUpdateSummary()
            restoreUiState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
