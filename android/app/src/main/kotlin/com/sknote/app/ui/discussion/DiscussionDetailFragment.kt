package com.sknote.app.ui.discussion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.ClipData
import android.content.ClipboardManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentDiscussionDetailBinding
import com.sknote.app.util.TimeUtil
import io.noties.markwon.Markwon
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DiscussionDetailFragment : Fragment() {

    private var _binding: FragmentDiscussionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiscussionDetailViewModel by viewModels()
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var markwon: Markwon
    private var discussionId: Long = 0
    private var cachedUserId: Long = -1
    private var cachedRole: String = "user"
    private var replyParentCommentId: Long? = null
    private var replyTargetName: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscussionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        discussionId = arguments?.getLong("discussion_id", 0L) ?: 0L
        if (discussionId <= 0L) return
        markwon = Markwon.builder(requireContext())
            .usePlugin(GlideImagesPlugin.create(requireContext()))
            .build()
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.menu_discussion_detail)

        commentAdapter = CommentAdapter(
            onReplyClick = { comment ->
                beginReplyTo(comment)
            },
            onCopyClick = { comment ->
                copyCommentContent(comment.content)
            },
            onDeleteClick = { comment ->
                if (comment.authorId == cachedUserId || cachedRole == "admin") {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除评论")
                        .setMessage("确定删除这条评论吗？")
                        .setPositiveButton("删除") { _, _ -> viewModel.deleteComment(discussionId, comment.id) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            },
            onLikeClick = { comment ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                    if (isLoggedIn) {
                        viewModel.likeComment(discussionId, comment.id)
                    } else {
                        Snackbar.make(binding.root, "请先登录", Snackbar.LENGTH_SHORT)
                            .setAction("去登录") { findNavController().navigate(R.id.loginFragment) }
                            .show()
                    }
                }
            },
            onAvatarClick = { comment ->
                if (comment.authorId > 0) {
                    val bundle = Bundle().apply { putLong("user_id", comment.authorId) }
                    findNavController().navigate(R.id.publicProfileFragment, bundle)
                }
            }
        )
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (!isLoggedIn) {
                binding.layoutReply.visibility = View.GONE
            }
        }

        binding.btnSend.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (!isLoggedIn) {
                    Snackbar.make(binding.root, "请先登录", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { findNavController().navigate(R.id.loginFragment) }
                        .show()
                    return@launch
                }
                val content = binding.etReply.text.toString().trim()
                if (content.isEmpty()) {
                    Snackbar.make(binding.root, "请输入评论内容", Snackbar.LENGTH_SHORT).show()
                    return@launch
                }
                binding.btnSend.isEnabled = false
                viewModel.sendComment(discussionId, content, replyParentCommentId)
            }
        }

        binding.btnCancelReply.setOnClickListener {
            clearReplyTarget()
        }

        // 预加载用户信息，只请求一次
        viewLifecycleOwner.lifecycleScope.launch {
            cachedUserId = getUserId()
            cachedRole = ApiClient.getTokenManager().getUserRole().first() ?: "user"
        }

        observeData()
        viewModel.loadDiscussion(discussionId)
    }

    private fun getCategoryLabel(category: String): String = when (category) {
        "general" -> "综合"
        "question" -> "提问"
        "feedback" -> "反馈"
        "bug" -> "Bug"
        "feature" -> "功能建议"
        else -> category
    }

    private fun observeData() {
        viewModel.discussion.observe(viewLifecycleOwner) { discussion ->
            binding.tvTitle.text = discussion.title
            binding.tvAuthor.text = discussion.authorName ?: "匿名"
            val navigateToAuthor = View.OnClickListener {
                if (discussion.authorId > 0) {
                    val bundle = Bundle().apply { putLong("user_id", discussion.authorId) }
                    findNavController().navigate(R.id.publicProfileFragment, bundle)
                }
            }
            binding.tvAuthor.setOnClickListener(navigateToAuthor)
            binding.ivAuthorAvatar.setOnClickListener(navigateToAuthor)
            binding.tvTime.text = TimeUtil.formatRelative(discussion.createdAt)
            binding.tvViewCount.text = "${discussion.viewCount} 浏览"
            binding.tvContent.visibility = View.VISIBLE
            binding.chipCategory.text = getCategoryLabel(discussion.category)

            // Handle block/palette share preview
            binding.blockPreviewContainer.removeAllViews()
            if (BlockShareHelper.containsAnyShare(discussion.content)) {
                val cleanContent = BlockShareHelper.getCleanContent(discussion.content)
                if (cleanContent.isNotEmpty()) {
                    markwon.setMarkdown(binding.tvContent, cleanContent)
                } else {
                    binding.tvContent.visibility = android.view.View.GONE
                }

                val blockJson = BlockShareHelper.extractBlockJson(discussion.content)
                if (blockJson != null) {
                    val preview = BlockShareHelper.createPreviewView(
                        requireContext(), binding.blockPreviewContainer, blockJson, showActions = true
                    )
                    binding.blockPreviewContainer.addView(preview)
                }

                val paletteJson = BlockShareHelper.extractPaletteJson(discussion.content)
                if (paletteJson != null) {
                    val preview = BlockShareHelper.createPalettePreviewView(
                        requireContext(), binding.blockPreviewContainer, paletteJson, showActions = true
                    ) {
                        val bundle = Bundle().apply {
                            putString("palette_json", paletteJson.toString())
                        }
                        findNavController().navigate(R.id.paletteDetailFragment, bundle)
                    }
                    binding.blockPreviewContainer.addView(preview)
                }

                if (blockJson != null || paletteJson != null) {
                    binding.blockPreviewContainer.visibility = android.view.View.VISIBLE
                }
            } else {
                markwon.setMarkdown(binding.tvContent, discussion.content)
                binding.blockPreviewContainer.visibility = android.view.View.GONE
            }

            val canDelete = discussion.authorId == cachedUserId || cachedRole == "admin"
            binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = canDelete

            binding.toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_share -> {
                        val shareText = "${discussion.title}\n\n${discussion.content.take(200)}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, discussion.title)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        startActivity(Intent.createChooser(intent, "分享讨论"))
                        true
                    }
                    R.id.action_delete -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("删除讨论")
                            .setMessage("确定删除这个讨论吗？此操作不可撤销。")
                            .setPositiveButton("删除") { _, _ -> viewModel.deleteDiscussion(discussionId) }
                            .setNegativeButton("取消", null)
                            .show()
                        true
                    }
                    else -> false
                }
            }
        }

        viewModel.deleted.observe(viewLifecycleOwner) { deleted ->
            if (deleted == true) {
                viewModel.onDeletedHandled()
                Snackbar.make(binding.root, "已删除", Snackbar.LENGTH_SHORT).show()
                findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_discussions", true)
                findNavController().navigateUp()
            }
        }

        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            commentAdapter.submitList(comments)
            binding.tvCommentsHeader.text = "评论 (${comments.size})"
            binding.layoutNoComments.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.layoutError.visibility = View.VISIBLE
                binding.tvError.text = error
            } else {
                binding.layoutError.visibility = View.GONE
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            viewModel.loadDiscussion(discussionId)
        }

        viewModel.isSending.observe(viewLifecycleOwner) { sending ->
            binding.btnSend.isEnabled = !sending
        }

        viewModel.commentSent.observe(viewLifecycleOwner) { sent ->
            if (sent == true) {
                viewModel.onCommentSentHandled()
                binding.etReply.text?.clear()
                clearReplyTarget()
                // 隐藏键盘
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.etReply.windowToken, 0)
                Snackbar.make(binding.root, "评论成功", Snackbar.LENGTH_SHORT).show()
                // 滚动到底部查看新评论
                binding.rvComments.post {
                    var v: android.view.View? = binding.rvComments
                    while (v != null && v !is androidx.core.widget.NestedScrollView) v = v.parent as? android.view.View
                    (v as? androidx.core.widget.NestedScrollView)?.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    private fun beginReplyTo(comment: com.sknote.app.data.model.Comment) {
        replyParentCommentId = comment.id
        replyTargetName = comment.authorName ?: "匿名"
        binding.etReply.hint = "回复 @$replyTargetName"
        binding.tvReplyIndicator.text = "正在回复 @$replyTargetName"
        binding.layoutReplyIndicator.visibility = View.VISIBLE
        binding.etReply.requestFocus()
        // 弹出键盘
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.etReply, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun clearReplyTarget() {
        replyParentCommentId = null
        replyTargetName = ""
        binding.etReply.hint = "写评论..."
        binding.layoutReplyIndicator.visibility = View.GONE
    }

    private fun copyCommentContent(content: String) {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("comment", content)
        clipboard.setPrimaryClip(clip)
        Snackbar.make(binding.root, "评论内容已复制", Snackbar.LENGTH_SHORT).show()
    }

    private suspend fun getUserId(): Long {
        return try {
            val response = ApiClient.getService().getMe()
            if (response.isSuccessful) {
                response.body()?.get("user")?.id ?: -1
            } else -1
        } catch (e: Exception) { -1 }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
