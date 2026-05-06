package com.sknote.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.databinding.ActivityMainBinding
import com.sknote.app.util.AppUpdateManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingStartupUpdateInfo: AppUpdateManager.UpdateInfo? = null
    private var hasShownStartupUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ensure every fragment root view has an opaque background for slide transitions
        val bgValue = TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, bgValue, true)
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?
                ) {
                    if (v.background == null) {
                        v.setBackgroundResource(bgValue.resourceId)
                    }
                }
            }, true
        )

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // Replace the default (animation-less, instant) bottom-nav navigation with a
        // Material fade-through transition. setupWithNavController above already wires
        // the reverse direction (destination changed -> update checked item), so we
        // only need to override the click handler here.
        val tabNavOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(navController.graph.startDestinationId, /*inclusive*/ false, /*saveState*/ true)
            .setEnterAnim(R.anim.nav_fade_in)
            .setExitAnim(R.anim.nav_fade_out)
            .setPopEnterAnim(R.anim.nav_fade_in)
            .setPopExitAnim(R.anim.nav_fade_out)
            .build()
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (navController.currentDestination?.id == item.itemId) return@setOnItemSelectedListener true
            try {
                navController.navigate(item.itemId, null, tabNavOptions)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }

        val topLevelDestinations = setOf(
            R.id.homeFragment,
            R.id.referenceFragment,
            R.id.discussionListFragment,
            R.id.adminFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val wrapper = binding.bottomNavWrapper
            if (destination.id in topLevelDestinations) {
                wrapper.animate().cancel()
                wrapper.visibility = View.VISIBLE
                wrapper.isEnabled = true
                binding.bottomNav.isEnabled = true
                wrapper.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            } else {
                wrapper.animate().cancel()
                wrapper.isEnabled = false
                binding.bottomNav.isEnabled = false
                wrapper.animate()
                    .translationY(wrapper.height.toFloat())
                    .alpha(0f)
                    .setDuration(300)
                    .start()
            }
        }

        if (savedInstanceState == null) {
            observeStartupUpdate()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        maybeShowPendingStartupUpdate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            maybeShowPendingStartupUpdate()
        }
    }

    private fun observeStartupUpdate() {
        lifecycleScope.launch {
            val result = AppUpdateManager.consumeStartupPrefetch(this@MainActivity) ?: return@launch
            val info = result.info ?: return@launch
            if (!result.hasUpdate) return@launch
            if (AppUpdateManager.isVersionSkipped(this@MainActivity, info.versionName)) return@launch
            pendingStartupUpdateInfo = info
            maybeShowPendingStartupUpdate()
        }
    }

    private fun maybeShowPendingStartupUpdate() {
        val info = pendingStartupUpdateInfo ?: return
        if (hasShownStartupUpdate) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (!hasWindowFocus() || isFinishing || isDestroyed) return
        hasShownStartupUpdate = true
        pendingStartupUpdateInfo = null
        showAutoUpdateDialog(info)
    }

    private fun showAutoUpdateDialog(info: AppUpdateManager.UpdateInfo) {
        val sizeText = if (info.fileSize > 0) "\n安装包大小：${AppUpdateManager.formatFileSize(info.fileSize)}" else ""
        val changelogText = if (info.changelog.isNotBlank()) "\n\n更新内容：\n${info.changelog}" else ""
        val noLinkText = if (info.downloadUrl.isBlank() && info.htmlUrl.isBlank()) {
            "\n\n注意：管理员暂未提供下载链接，请稍后手动检查更新。"
        } else ""

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("发现新版本 v${info.versionName}")
            .setMessage("当前版本：v${BuildConfig.VERSION_NAME}$sizeText$changelogText$noLinkText")

        when {
            info.downloadUrl.isNotBlank() -> {
                builder.setPositiveButton("立即更新") { _, _ -> startDownload(info) }
            }
            info.htmlUrl.isNotBlank() -> {
                builder.setPositiveButton("前往下载") { _, _ ->
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl)))
                    }.onFailure {
                        Snackbar.make(binding.root, "无法打开下载页面", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                builder.setPositiveButton("知道了", null)
            }
        }

        builder.setNegativeButton("以后再说", null)
        builder.setNeutralButton("跳过此版本") { _, _ ->
            AppUpdateManager.skipVersion(this, info.versionName)
        }
        builder.show()
    }

    private fun startDownload(info: AppUpdateManager.UpdateInfo) {
        Snackbar.make(binding.root, "正在后台下载更新...", Snackbar.LENGTH_LONG).show()

        AppUpdateManager.startDownload(this, info.downloadUrl, info.versionName) { file ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (file != null && file.exists()) {
                    AppUpdateManager.installApk(this@MainActivity, file)
                } else {
                    Snackbar.make(binding.root, "下载失败，请稍后重试", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
}
