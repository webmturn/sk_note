package com.sknote.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.databinding.ActivityMainBinding
import com.sknote.app.util.AppUpdateManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        val topLevelDestinations = setOf(
            R.id.homeFragment,
            R.id.referenceFragment,
            R.id.discussionListFragment,
            R.id.adminFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility = if (destination.id in topLevelDestinations) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        if (savedInstanceState == null) {
            autoCheckUpdate()
        }
    }

    private fun autoCheckUpdate() {
        if (!AppUpdateManager.shouldAutoCheck(this)) return

        lifecycleScope.launch {
            val result = AppUpdateManager.checkForUpdate()
            AppUpdateManager.markChecked(this@MainActivity)

            if (result.hasUpdate && result.info != null) {
                if (!AppUpdateManager.isVersionSkipped(this@MainActivity, result.info.versionName)) {
                    showAutoUpdateDialog(result.info)
                }
            }
        }
    }

    private fun showAutoUpdateDialog(info: AppUpdateManager.UpdateInfo) {
        val sizeText = if (info.fileSize > 0) "\n安装包大小：${AppUpdateManager.formatFileSize(info.fileSize)}" else ""
        val changelogText = if (info.changelog.isNotBlank()) "\n\n更新内容：\n${info.changelog}" else ""

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("发现新版本 v${info.versionName}")
            .setMessage("当前版本：v${BuildConfig.VERSION_NAME}$sizeText$changelogText")

        if (info.downloadUrl.isNotBlank()) {
            builder.setPositiveButton("立即更新") { _, _ -> startDownload(info) }
        } else {
            builder.setPositiveButton("前往下载") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl)))
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
                if (file != null && file.exists()) {
                    AppUpdateManager.installApk(this@MainActivity, file)
                } else {
                    Snackbar.make(binding.root, "下载失败，请稍后重试", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
}
