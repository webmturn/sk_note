package com.sknote.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.databinding.FragmentCacheManageBinding
import java.io.File

class CacheManageFragment : Fragment() {

    private var _binding: FragmentCacheManageBinding? = null
    private val binding get() = _binding!!

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
                refreshCacheSizes()
                Snackbar.make(binding.root, "图片缓存已清除", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.rowClearNetworkCache.setOnClickListener {
            showClearDialog("网络缓存") {
                clearNetworkCache()
                refreshCacheSizes()
                Snackbar.make(binding.root, "网络缓存已清除", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.rowClearAllCache.setOnClickListener {
            showClearDialog("全部缓存") {
                requireContext().cacheDir.deleteRecursively()
                refreshCacheSizes()
                Snackbar.make(binding.root, "全部缓存已清除", Snackbar.LENGTH_SHORT).show()
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

    private fun refreshCacheSizes() {
        val cacheDir = requireContext().cacheDir

        val imageDir = File(cacheDir, "image_cache")
        val networkDir = File(cacheDir, "http-cache")

        val imageSize = getDirSize(imageDir)
        val networkSize = getDirSize(networkDir)
        val totalSize = getDirSize(cacheDir)
        val otherSize = totalSize - imageSize - networkSize

        binding.tvTotalCache.text = formatSize(totalSize)
        binding.tvImageCache.text = formatSize(imageSize)
        binding.tvNetworkCache.text = formatSize(networkSize)
        binding.tvOtherCache.text = formatSize(otherSize.coerceAtLeast(0))
    }

    private fun clearImageCache() {
        File(requireContext().cacheDir, "image_cache").deleteRecursively()
    }

    private fun clearNetworkCache() {
        File(requireContext().cacheDir, "http-cache").deleteRecursively()
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
