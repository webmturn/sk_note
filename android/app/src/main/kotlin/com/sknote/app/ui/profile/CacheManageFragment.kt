package com.sknote.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.databinding.FragmentCacheManageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CacheManageFragment : Fragment() {

    private var _binding: FragmentCacheManageBinding? = null
    private val binding get() = _binding!!

    private data class CacheUsage(
        val totalSize: Long,
        val imageSize: Long,
        val otherSize: Long
    )

    private fun isFragmentUsable(): Boolean {
        return _binding != null && isAdded && context != null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCacheManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        refreshCacheSizes()

        binding.rowClearImageCache.setOnClickListener {
            showClearDialog("图片缓存") {
                clearImageCache()
            }
        }

        binding.rowClearAllCache.setOnClickListener {
            showClearDialog("全部缓存") {
                clearAllCache()
            }
        }
    }

    private fun showClearDialog(name: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清除$name")
            .setMessage("确定要清除${name}吗？这不会影响账号数据。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清除") { _, _ -> onConfirm() }
            .show()
    }

    private val IMAGE_CACHE_DIRS = setOf("image_manager_disk_cache", "com.bumptech.glide")

    private fun setBusyState(isBusy: Boolean, statusText: String? = null) {
        binding.rowClearImageCache.isEnabled = !isBusy
        binding.rowClearAllCache.isEnabled = !isBusy
        binding.rowClearImageCache.alpha = if (isBusy) 0.6f else 1f
        binding.rowClearAllCache.alpha = if (isBusy) 0.6f else 1f
        if (statusText != null) {
            binding.tvCacheSummary.text = statusText
        }
    }

    private fun readCacheUsage(cacheDir: File): CacheUsage {
        var imageSize = 0L
        val totalSize = getDirSize(cacheDir)

        cacheDir.listFiles()?.forEach { sub ->
            val name = sub.name.lowercase()
            if (IMAGE_CACHE_DIRS.any { name.contains(it) }) imageSize += getDirSize(sub)
        }

        val otherSize = (totalSize - imageSize).coerceAtLeast(0)

        return CacheUsage(
            totalSize = totalSize,
            imageSize = imageSize,
            otherSize = otherSize
        )
    }

    private fun refreshCacheSizes(statusText: String? = null) {
        val usage = readCacheUsage(requireContext().cacheDir)

        binding.tvTotalCache.text = formatSize(usage.totalSize)
        binding.tvImageCache.text = formatSize(usage.imageSize)
        binding.tvOtherCache.text = formatSize(usage.otherSize)
        binding.tvCacheSummary.text = statusText ?: if (usage.totalSize > 0L) {
            "图片 ${formatSize(usage.imageSize)} · 其他 ${formatSize(usage.otherSize)}"
        } else {
            "当前缓存已清理"
        }
    }

    private fun clearImageCache() {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val beforeUsage = readCacheUsage(ctx.cacheDir)
            setBusyState(true, "正在清理图片缓存...")
            Glide.get(ctx).clearMemory()
            val afterUsage = withContext(Dispatchers.IO) {
                Glide.get(ctx).clearDiskCache()
                IMAGE_CACHE_DIRS.forEach { File(ctx.cacheDir, it).deleteRecursively() }
                readCacheUsage(ctx.cacheDir)
            }
            if (!isFragmentUsable()) return@launch
            setBusyState(false)
            val clearedBytes = (beforeUsage.imageSize - afterUsage.imageSize).coerceAtLeast(0)
            refreshCacheSizes(
                if (clearedBytes > 0L) {
                    "已清理图片缓存 ${formatSize(clearedBytes)}"
                } else {
                    "图片缓存已清理"
                }
            )
            Snackbar.make(binding.root, "图片缓存已清除", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun clearAllCache() {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val beforeUsage = readCacheUsage(ctx.cacheDir)
            setBusyState(true, "正在清理全部缓存...")
            Glide.get(ctx).clearMemory()
            val afterUsage = withContext(Dispatchers.IO) {
                Glide.get(ctx).clearDiskCache()
                ctx.cacheDir.deleteRecursively()
                ctx.cacheDir.mkdirs()
                readCacheUsage(ctx.cacheDir)
            }
            if (!isFragmentUsable()) return@launch
            setBusyState(false)
            val clearedBytes = (beforeUsage.totalSize - afterUsage.totalSize).coerceAtLeast(0)
            refreshCacheSizes(
                if (clearedBytes > 0L) {
                    "已清理全部缓存 ${formatSize(clearedBytes)}"
                } else {
                    "全部缓存已清理"
                }
            )
            Snackbar.make(binding.root, "全部缓存已清除", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { size += getDirSize(it) }
        } else if (dir.isFile) {
            size = dir.length()
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
