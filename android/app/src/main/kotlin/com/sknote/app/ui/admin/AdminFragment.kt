package com.sknote.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.BuildConfig
import com.sknote.app.SkNoteApp
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.local.TokenManager
import com.sknote.app.databinding.FragmentAdminBinding
import com.sknote.app.util.AppUpdateManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navOptions = androidx.navigation.NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()

        binding.btnGoLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment, null, navOptions)
        }

        binding.cardNotifications.setOnClickListener {
            findNavController().navigate(R.id.notificationFragment, null, navOptions)
        }

        binding.cardBookmarks.setOnClickListener {
            findNavController().navigate(R.id.bookmarkListFragment, null, navOptions)
        }

        binding.cardHistory.setOnClickListener {
            findNavController().navigate(R.id.readingHistoryFragment, null, navOptions)
        }

        binding.cardProfileEdit.setOnClickListener {
            findNavController().navigate(R.id.profileEditFragment, null, navOptions)
        }

        binding.cardUsers.setOnClickListener {
            findNavController().navigate(R.id.userManageFragment, null, navOptions)
        }

        binding.cardCategories.setOnClickListener {
            findNavController().navigate(R.id.categoryManageFragment, null, navOptions)
        }

        binding.cardArticles.setOnClickListener {
            findNavController().navigate(R.id.articleManageFragment, null, navOptions)
        }

        binding.cardDiscussions.setOnClickListener {
            findNavController().navigate(R.id.discussionManageFragment, null, navOptions)
        }

        binding.cardSnippets.setOnClickListener {
            findNavController().navigate(R.id.snippetManageFragment, null, navOptions)
        }

        setupSettings()

        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        ApiClient.getTokenManager().clearAuth()
                        Snackbar.make(binding.root, "已退出登录", Snackbar.LENGTH_SHORT).show()
                        refreshLoginState()
                    }
                }
                .show()
        }

        refreshLoginState()
    }

    private fun refreshLoginState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (isLoggedIn) {
                val username = ApiClient.getTokenManager().getUsername().first() ?: ""
                val role = ApiClient.getTokenManager().getUserRole().first() ?: "user"

                binding.cardUserProfile.visibility = View.VISIBLE
                binding.cardGuestLogin.visibility = View.GONE
                binding.tvFunctionHeader.visibility = View.VISIBLE
                binding.cardFunctionGroup.visibility = View.VISIBLE
                binding.cardLogout.visibility = View.VISIBLE

                binding.tvUsername.text = username
                binding.tvRole.text = if (role == "admin") "管理员" else "普通用户"

                val isAdmin = role == "admin"
                binding.tvAdminHeader.visibility = if (isAdmin) View.VISIBLE else View.GONE
                binding.cardAdminGroup.visibility = if (isAdmin) View.VISIBLE else View.GONE

                loadStats()
            } else {
                binding.cardUserProfile.visibility = View.GONE
                binding.cardGuestLogin.visibility = View.VISIBLE
                binding.tvFunctionHeader.visibility = View.GONE
                binding.cardFunctionGroup.visibility = View.GONE
                binding.tvAdminHeader.visibility = View.GONE
                binding.cardAdminGroup.visibility = View.GONE
                binding.cardLogout.visibility = View.GONE
            }
            binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"
        }
    }

    private fun loadStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getStats()
                if (response.isSuccessful) {
                    val stats = response.body() ?: return@launch
                    binding.tvStatNotifications.text = "${stats.unreadNotifications}"
                    binding.tvNotificationDesc.text = if (stats.unreadNotifications > 0) "${stats.unreadNotifications} 条未读" else ""
                    binding.tvStatArticles.text = "${stats.totalArticles}"
                    binding.tvStatDiscussions.text = "${stats.totalDiscussions}"
                }
            } catch (_: Exception) { }
        }
    }

    private fun setupSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            val mode = ApiClient.getTokenManager().getThemeMode().first()
            binding.tvThemeMode.text = SkNoteApp.themeModeLabel(mode)
        }

        binding.rowThemeMode.setOnClickListener { showThemeModeDialog() }

        val appPrefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        binding.switchShapeFilter.isChecked = appPrefs.getBoolean("shape_filter_enabled", false)
        binding.switchShapeFilter.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.edit().putBoolean("shape_filter_enabled", isChecked).apply()
        }

        binding.tvCacheSize.text = getCacheSize()

        binding.rowClearCache.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("清除缓存")
                .setMessage("确定要清除所有缓存数据吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除") { _, _ ->
                    requireContext().cacheDir.deleteRecursively()
                    binding.tvCacheSize.text = getCacheSize()
                    Snackbar.make(binding.root, "缓存已清除", Snackbar.LENGTH_SHORT).show()
                }
                .show()
        }

        binding.rowCheckUpdate.setOnClickListener { checkUpdate(manual = true) }

        binding.rowAbout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("关于 SkNote")
                .setMessage(
                    "版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n" +
                    "Sketchware Pro 手册\n" +
                    "学习、探索、交流\n\n" +
                    "基于 Material Design 3 构建\n" +
                    "后端：Cloudflare Workers + D1\n" +
                    "前端：Android Kotlin + Jetpack"
                )
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun checkUpdate(manual: Boolean) {
        binding.progressUpdate.visibility = View.VISIBLE
        binding.tvUpdateStatus.text = "正在检查..."

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AppUpdateManager.checkForUpdate()
            if (_binding == null) return@launch

            binding.progressUpdate.visibility = View.GONE

            if (result.error != null && manual) {
                binding.tvUpdateStatus.text = "检查失败"
                Snackbar.make(binding.root, "检查更新失败: ${result.error}", Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            if (result.hasUpdate && result.info != null) {
                binding.tvUpdateStatus.text = "发现新版本 v${result.info.versionName}"
                if (manual || !AppUpdateManager.isVersionSkipped(requireContext(), result.info.versionName)) {
                    showUpdateDialog(result.info)
                }
            } else {
                binding.tvUpdateStatus.text = "已是最新版本"
                if (manual) {
                    Snackbar.make(binding.root, "当前已是最新版本", Snackbar.LENGTH_SHORT).show()
                }
            }
            AppUpdateManager.markChecked(requireContext())
        }
    }

    private fun showUpdateDialog(info: AppUpdateManager.UpdateInfo) {
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
            AppUpdateManager.skipVersion(requireContext(), info.versionName)
            binding.tvUpdateStatus.text = "已跳过 v${info.versionName}"
        }
        builder.show()
    }

    private fun startDownloadAndInstall(info: AppUpdateManager.UpdateInfo) {
        binding.tvUpdateStatus.text = "正在下载..."
        binding.progressUpdate.visibility = View.VISIBLE

        AppUpdateManager.startDownload(requireContext(), info.downloadUrl, info.versionName) { file ->
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                binding.progressUpdate.visibility = View.GONE
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

    private fun showThemeModeDialog() {
        val modes = arrayOf(TokenManager.THEME_SYSTEM, TokenManager.THEME_LIGHT, TokenManager.THEME_DARK)
        val labels = arrayOf("跟随系统", "浅色模式", "深色模式")

        viewLifecycleOwner.lifecycleScope.launch {
            val current = ApiClient.getTokenManager().getThemeMode().first()
            val checkedIndex = modes.indexOf(current).coerceAtLeast(0)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("主题模式")
                .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                    val selected = modes[which]
                    AppCompatDelegate.setDefaultNightMode(SkNoteApp.themeModeToNightMode(selected))
                    binding.tvThemeMode.text = labels[which]
                    viewLifecycleOwner.lifecycleScope.launch {
                        ApiClient.getTokenManager().setThemeMode(selected)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
