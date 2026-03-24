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
            .setMessage("确定要清除${name}吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("清除") { _, _ -> onConfirm() }
            .show()
    }

    private val IMAGE_CACHE_DIRS = setOf("image_manager_disk_cache", "com.bumptech.glide")

    private fun refreshCacheSizes() {
        val cacheDir = requireContext().cacheDir

        var imageSize = 0L
        val totalSize = getDirSize(cacheDir)

        cacheDir.listFiles()?.forEach { sub ->
            val name = sub.name.lowercase()
            if (IMAGE_CACHE_DIRS.any { name.contains(it) }) imageSize += getDirSize(sub)
        }

        val otherSize = (totalSize - imageSize).coerceAtLeast(0)

        binding.tvTotalCache.text = formatSize(totalSize)
        binding.tvImageCache.text = formatSize(imageSize)
        binding.tvOtherCache.text = formatSize(otherSize)
    }

    private fun clearImageCache() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                Glide.get(ctx).clearDiskCache()
                IMAGE_CACHE_DIRS.forEach { File(ctx.cacheDir, it).deleteRecursively() }
            }
            if (!isFragmentUsable()) return@launch
            refreshCacheSizes()
            Snackbar.make(binding.root, "图片缓存已清除", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun clearAllCache() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ctx.cacheDir.deleteRecursively()
            }
            if (!isFragmentUsable()) return@launch
            refreshCacheSizes()
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
