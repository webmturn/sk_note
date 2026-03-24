package com.sknote.app.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Share
import com.sknote.app.databinding.FragmentShareDetailBinding
import com.sknote.app.util.requireLoggedIn
import com.sknote.app.util.LanzouParser
import com.sknote.app.util.TimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShareDetailFragment : Fragment() {

    private var _binding: FragmentShareDetailBinding? = null
    private val binding get() = _binding!!
    private var shareId: Long = 0
    private var currentShare: Share? = null
    private var currentUserId: Long = -1
    private var currentUserRole: String = "user"
    private var isLiking = false

    private fun isFragmentUsable(): Boolean {
        return _binding != null && isAdded && context != null && activity != null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShareDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shareId = arguments?.getLong("share_id", 0L) ?: 0L
        if (shareId <= 0L) {
            Snackbar.make(binding.root, "无效的分享ID", Snackbar.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.toolbar.inflateMenu(R.menu.menu_share_detail)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_copy_link -> {
                    currentShare?.let { copyShareLink(it) }
                    true
                }
                R.id.action_open_browser -> {
                    currentShare?.let {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.downloadUrl.orEmpty())))
                    }
                    true
                }
                R.id.action_delete -> {
                    confirmDelete()
                    true
                }
                else -> false
            }
        }

        binding.btnCopyUrl.setOnClickListener {
            currentShare?.let { share ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("url", share.downloadUrl.orEmpty()))
                Snackbar.make(binding.root, "链接已复制", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnCopyPassword.setOnClickListener {
            currentShare?.let { share ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("password", share.downloadPwd.orEmpty()))
                Snackbar.make(binding.root, "密码已复制", Snackbar.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (isLoggedIn) {
                currentUserId = ApiClient.getTokenManager().getUserId().first() ?: -1
                currentUserRole = ApiClient.getTokenManager().getUserRole().first() ?: "user"
            }
            if (!isFragmentUsable()) return@launch
            loadShare()
            refreshCurrentUserState()
        }

        binding.btnLike.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                if (isLiking) return@launch
                if (!isFragmentUsable()) return@launch
                if (!requireLoggedIn(binding.root)) {
                    return@launch
                }
                try {
                    isLiking = true
                    binding.btnLike.isEnabled = false
                    val response = ApiClient.getService().likeShare(shareId)
                    if (_binding == null) return@launch
                    if (response.isSuccessful) {
                        val body = response.body()
                        val liked = body?.liked ?: false
                        Snackbar.make(binding.root, if (liked) "已点赞" else "已取消点赞", Snackbar.LENGTH_SHORT).show()
                        loadShare(showProgress = false)
                    }
                } catch (e: Exception) {
                    if (_binding == null) return@launch
                    Snackbar.make(binding.root, "操作失败", Snackbar.LENGTH_SHORT).show()
                } finally {
                    isLiking = false
                    _binding?.btnLike?.isEnabled = true
                }
            }
        }

        binding.btnDownload.setOnClickListener {
            currentShare?.let { share -> startDownload(share) }
        }
    }

    private fun loadShare(showProgress: Boolean = true) {
        if (showProgress) binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getShare(shareId)
                if (_binding == null) return@launch
                if (response.isSuccessful) {
                    val share = response.body()?.share ?: return@launch
                    currentShare = share

                    binding.tvTitle.text = share.title
                    binding.tvDescription.text = share.description.orEmpty().ifEmpty { "暂无描述" }
                    binding.tvCategory.text = ShareCategories.getLabel(share.category.orEmpty())
                    binding.tvFileSize.text = share.fileSize.orEmpty().ifEmpty { "未知大小" }
                    binding.tvAuthor.text = share.authorName.orEmpty().ifEmpty { "匿名" }
                    binding.tvTime.text = TimeUtil.formatRelative(share.createdAt)
                    binding.tvViews.text = "${share.viewCount} 浏览"
                    binding.tvDownloadUrl.text = share.downloadUrl.orEmpty()
                    binding.tvDownloadCount.text = "${share.downloadCount}"
                    binding.btnLike.text = "点赞 (${share.likeCount})"

                    if (!share.downloadPwd.isNullOrEmpty()) {
                        binding.layoutPassword.visibility = View.VISIBLE
                        binding.tvPassword.text = share.downloadPwd.orEmpty()
                    } else {
                        binding.layoutPassword.visibility = View.GONE
                    }

                    binding.toolbar.title = share.title

                    val canDelete = share.authorId == currentUserId || currentUserRole == "admin"
                    binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = canDelete
                } else {
                    Snackbar.make(binding.root, "加载失败", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private suspend fun refreshCurrentUserState() {
        val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
        if (!isLoggedIn) {
            currentUserId = -1
            currentUserRole = "user"
            if (_binding != null) {
                binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = false
            }
            return
        }

        try {
            val response = ApiClient.getService().getMe()
            if (!response.isSuccessful) return
            val user = response.body()?.get("user") ?: return
            currentUserId = user.id
            currentUserRole = user.role
            ApiClient.getTokenManager().updateNickname(user.displayName)
            ApiClient.getTokenManager().updateUserRole(user.role)
            if (_binding != null) {
                val canDelete = currentShare?.authorId == currentUserId || currentUserRole == "admin"
                binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = canDelete
            }
        } catch (_: Exception) {
        }
    }

    private fun startDownload(share: Share) {
        if (LanzouParser.isLanzouUrl(share.downloadUrl.orEmpty())) {
            // 蓝奏云链接：尝试解析直链
            binding.btnDownload.isEnabled = false
            binding.btnDownload.text = "解析中..."

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // 记录下载次数
                    try { ApiClient.getService().recordShareDownload(shareId) } catch (_: Exception) {}

                    val result = LanzouParser.parse(share.downloadUrl.orEmpty(), share.downloadPwd.orEmpty())
                    if (!isFragmentUsable()) return@launch
                    if (result.success) {
                        // 用浏览器打开真实下载链接
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.downloadUrl))
                        startActivity(intent)
                    } else {
                        // 解析失败，提供备选方案
                        showDownloadFallbackDialog(share, result.error)
                    }
                } catch (e: Exception) {
                    if (!isFragmentUsable()) return@launch
                    showDownloadFallbackDialog(share, e.message ?: "未知错误")
                } finally {
                    _binding?.btnDownload?.isEnabled = true
                    _binding?.btnDownload?.text = "下载"
                }
            }
        } else {
            // 非蓝奏云链接：直接用浏览器打开
            viewLifecycleOwner.lifecycleScope.launch {
                try { ApiClient.getService().recordShareDownload(shareId) } catch (_: Exception) {}
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(share.downloadUrl.orEmpty()))
            startActivity(intent)
        }
    }

    private fun showDownloadFallbackDialog(share: Share, error: String) {
        if (!isFragmentUsable()) return
        val pwd = if (!share.downloadPwd.isNullOrEmpty()) "\n密码：${share.downloadPwd}" else ""
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("自动解析失败")
            .setMessage("原因：$error\n\n你可以手动在浏览器中打开链接下载。$pwd")
            .setPositiveButton("在浏览器中打开") { _, _ ->
                if (!isFragmentUsable()) return@setPositiveButton
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(share.downloadUrl.orEmpty()))
                startActivity(intent)
            }
            .setNeutralButton("复制链接") { _, _ ->
                if (!isFragmentUsable()) return@setNeutralButton
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = if (!share.downloadPwd.isNullOrEmpty()) {
                    "${share.downloadUrl.orEmpty()} 密码：${share.downloadPwd}"
                } else {
                    share.downloadUrl.orEmpty()
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("download_url", text))
                Snackbar.make(binding.root, "链接已复制", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyShareLink(share: Share) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = if (!share.downloadPwd.isNullOrEmpty()) {
            "${share.downloadUrl.orEmpty()} 密码：${share.downloadPwd}"
        } else {
            share.downloadUrl.orEmpty()
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("share_url", text))
        Snackbar.make(binding.root, "链接已复制到剪贴板", Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除分享")
            .setMessage("确定要删除这个分享吗？此操作不可撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> deleteShare() }
            .show()
    }

    private fun deleteShare() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().deleteShare(shareId)
                if (!isFragmentUsable()) return@launch
                if (response.isSuccessful) {
                    Snackbar.make(binding.root, "已删除", Snackbar.LENGTH_SHORT).show()
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_shares", true)
                    findNavController().navigateUp()
                } else {
                    Snackbar.make(binding.root, "删除失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isFragmentUsable()) return@launch
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
