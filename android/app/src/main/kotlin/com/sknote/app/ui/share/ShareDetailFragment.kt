package com.sknote.app.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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

        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle
        navHandle?.getLiveData<Boolean>("refresh_share_detail")
            ?.observe(viewLifecycleOwner) { refresh ->
                if (refresh == true) {
                    loadShare(showProgress = false)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_shares", true)
                    navHandle.remove<Boolean>("refresh_share_detail")
                }
            }

        navHandle?.getLiveData<String>("share_result_message")
            ?.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>("share_result_message")
                }
            }

        binding.toolbar.inflateMenu(R.menu.menu_share_detail)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    val bundle = Bundle().apply { putLong("share_id", shareId) }
                    findNavController().navigate(R.id.createShareFragment, bundle)
                    true
                }
                R.id.action_copy_link -> {
                    currentShare?.let { copyShareLink(it) }
                    true
                }
                R.id.action_open_browser -> {
                    currentShare?.let {
                        val uri = buildBrowsableUri(normalizedDownloadUrl(it))
                        if (uri != null) {
                            openUri(uri)
                        } else {
                            Snackbar.make(binding.root, "下载链接无效", Snackbar.LENGTH_SHORT).show()
                        }
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
                val downloadUrl = normalizedDownloadUrl(share)
                if (downloadUrl.isEmpty()) {
                    Snackbar.make(binding.root, "暂无可复制的链接", Snackbar.LENGTH_SHORT).show()
                    return@let
                }
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("url", downloadUrl))
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
            binding.toolbar.menu.findItem(R.id.action_edit)?.isVisible = false
            binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = false
            binding.toolbar.menu.findItem(R.id.action_copy_link)?.isVisible = false
            binding.toolbar.menu.findItem(R.id.action_open_browser)?.isVisible = false
            loadShare()
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
                    if (!isFragmentUsable()) return@launch
                    if (response.isSuccessful) {
                        val body = response.body()
                        val liked = body?.liked ?: false
                        Snackbar.make(binding.root, if (liked) "已点赞" else "已取消点赞", Snackbar.LENGTH_SHORT).show()
                        loadShare(showProgress = false)
                    }
                } catch (e: Exception) {
                    if (!isFragmentUsable()) return@launch
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
                if (!isFragmentUsable()) return@launch
                if (response.isSuccessful) {
                    val share = response.body()?.share ?: return@launch
                    currentShare = share
                    val downloadUrl = normalizedDownloadUrl(share)
                    val browsableUri = buildBrowsableUri(downloadUrl)

                    binding.tvTitle.text = share.title
                    binding.tvDescription.text = share.description.orEmpty().ifEmpty { "暂无描述" }
                    binding.tvCategory.text = ShareCategories.getLabel(share.category.orEmpty())
                    binding.tvFileSize.text = share.fileSize.orEmpty().ifEmpty { "未知大小" }
                    binding.tvAuthor.text = share.authorName.orEmpty().ifEmpty { "匿名" }
                    binding.tvTime.text = TimeUtil.formatRelative(share.createdAt)
                    binding.tvViews.text = "${share.viewCount} 浏览"
                    binding.tvDownloadUrl.text = downloadUrl.ifEmpty { "暂无链接" }
                    binding.tvDownloadCount.text = "${share.downloadCount}"
                    binding.btnLike.text = "点赞 (${share.likeCount})"
                    binding.btnCopyUrl.isEnabled = downloadUrl.isNotEmpty()
                    binding.btnCopyUrl.alpha = if (downloadUrl.isNotEmpty()) 1f else 0.5f
                    binding.btnDownload.isEnabled = browsableUri != null
                    binding.btnDownload.alpha = if (browsableUri != null) 1f else 0.5f

                    if (!share.downloadPwd.isNullOrEmpty()) {
                        binding.layoutPassword.visibility = View.VISIBLE
                        binding.tvPassword.text = share.downloadPwd.orEmpty()
                    } else {
                        binding.layoutPassword.visibility = View.GONE
                    }

                    binding.toolbar.title = share.title

                    val canEdit = share.authorId == currentUserId || currentUserRole == "admin"
                    val canDelete = canEdit
                    binding.toolbar.menu.findItem(R.id.action_edit)?.isVisible = canEdit
                    binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = canDelete
                    binding.toolbar.menu.findItem(R.id.action_copy_link)?.isVisible = downloadUrl.isNotEmpty()
                    binding.toolbar.menu.findItem(R.id.action_open_browser)?.isVisible = browsableUri != null
                } else {
                    Snackbar.make(binding.root, "加载失败", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isFragmentUsable()) return@launch
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                if (isFragmentUsable()) binding.progressBar.visibility = View.GONE
            }
        }
    }


    private fun startDownload(share: Share) {
        val downloadUrl = normalizedDownloadUrl(share)
        val browsableUri = buildBrowsableUri(downloadUrl)
        if (browsableUri == null) {
            Snackbar.make(binding.root, "下载链接无效", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (LanzouParser.isLanzouUrl(downloadUrl)) {
            // 蓝奏云链接：尝试解析直链
            binding.btnDownload.isEnabled = false
            binding.btnDownload.text = "解析中..."

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // 记录下载次数
                    try { ApiClient.getService().recordShareDownload(shareId) } catch (_: Exception) {}

                    val result = LanzouParser.parse(downloadUrl, share.downloadPwd.orEmpty())
                    if (!isFragmentUsable()) return@launch
                    if (result.success) {
                        val resolvedUri = buildBrowsableUri(result.downloadUrl)
                        if (resolvedUri != null) {
                            openUri(resolvedUri)
                        } else {
                            showDownloadFallbackDialog(share, "解析后的链接无效")
                        }
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
            openUri(browsableUri)
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
                val uri = buildBrowsableUri(normalizedDownloadUrl(share))
                if (uri != null) {
                    openUri(uri)
                } else {
                    Snackbar.make(binding.root, "下载链接无效", Snackbar.LENGTH_SHORT).show()
                }
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

    private fun normalizedDownloadUrl(share: Share): String {
        return share.downloadUrl.orEmpty().trim()
    }

    private fun buildBrowsableUri(rawUrl: String?): Uri? {
        val normalizedUrl = rawUrl.orEmpty().trim()
        if (normalizedUrl.isEmpty()) return null
        val uri = Uri.parse(normalizedUrl)
        val scheme = uri.scheme?.lowercase()
        return if (scheme == "http" || scheme == "https") uri else null
    }

    private fun openUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val packageManager = context?.packageManager ?: return
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else if (isFragmentUsable()) {
            Snackbar.make(binding.root, "没有可用的应用打开该链接", Snackbar.LENGTH_SHORT).show()
        }
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
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("share_result_message", "已删除")
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
