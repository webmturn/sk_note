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
import com.sknote.app.util.AppSnackbar

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

        // 底部导航栏当前是否处于隐藏状态。
        // 用途一（主要）：只有「隐藏 → 显示」这一次跨状态才做滑入动画——顶层 Tab 之间
        //         互切时导航栏本就可见，若无条件滑入会让它每次先跳下去再弹回来。
        // 用途二（防御性）：结束回调里再确认一次状态。
        //
        // 订正：此前这里注释称「先置 false 再 cancel() 是必需的，否则结束回调会把正在
        // 显示的导航栏又置为 GONE」。该说法是错的——ViewPropertyAnimator.withEndAction
        // 的文档明确写着动画被取消时该 Runnable 不会执行，AOSP 实现里 onAnimationCancel
        // 会直接把回调从 mAnimatorOnEndMap 移除（ViewPropertyAnimator.java:1092-1093）。
        // 所以 cancel() 根本不触发结束回调，不存在这个竞态；顺序仅为可读性。
        var navHidden = false

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val wrapper = binding.bottomNavWrapper
            if (destination.id in topLevelDestinations) {
                wrapper.isEnabled = true
                binding.bottomNav.isEnabled = true
                if (!navHidden) {
                    // 导航栏已经在显示：只保证状态归位，不做任何动画
                    wrapper.animate().cancel()
                    wrapper.visibility = View.VISIBLE
                    wrapper.alpha = 1f
                    wrapper.translationY = 0f
                    return@addOnDestinationChangedListener
                }
                navHidden = false
                wrapper.animate().cancel()
                wrapper.visibility = View.VISIBLE
                // 自下方滑入。尚未测量时 height 为 0，退化为纯淡入。
                wrapper.translationY = wrapper.height.toFloat()
                wrapper.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            } else {
                wrapper.isEnabled = false
                binding.bottomNav.isEnabled = false
                // 已经是隐藏状态（例如详情页跳详情页），不重复播放收起动画
                if (navHidden) return@addOnDestinationChangedListener
                navHidden = true
                wrapper.animate().cancel()
                wrapper.animate()
                    .translationY(wrapper.height.toFloat())
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        // 必须置为 GONE：alpha=0 的 View 仍参与命中测试，
                        // 否则底部这条区域会一直吞掉点击。
                        if (navHidden) {
                            wrapper.visibility = View.GONE
                            wrapper.translationY = 0f
                        }
                    }
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
                        AppSnackbar.make(binding.root, "无法打开下载页面", Snackbar.LENGTH_SHORT).show()
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
        AppSnackbar.make(binding.root, "正在后台下载更新...", Snackbar.LENGTH_LONG).show()

        AppUpdateManager.startDownload(this, info.downloadUrl, info.versionName) { file ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (file != null && file.exists()) {
                    AppUpdateManager.installApk(this@MainActivity, file)
                } else {
                    AppSnackbar.make(binding.root, "下载失败，请稍后重试", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
}
