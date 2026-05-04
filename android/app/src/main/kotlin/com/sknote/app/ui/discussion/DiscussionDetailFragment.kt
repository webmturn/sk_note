package com.sknote.app.ui.discussion

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.Intent
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Comment
import com.sknote.app.databinding.FragmentDiscussionDetailBinding
import com.sknote.app.databinding.LayoutDiscussionDetailHeaderBinding
import com.sknote.app.util.DiscussionCategoryDefaults
import com.sknote.app.util.SkeletonAnimator
import com.sknote.app.util.TimeUtil
import com.sknote.app.util.hideSkeletonAndShow
import com.sknote.app.util.requireLoggedIn
import com.sknote.app.util.showSkeleton
import com.sknote.app.util.slideNavOptions
import io.noties.markwon.Markwon
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DiscussionDetailFragment : Fragment() {

    private var _binding: FragmentDiscussionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiscussionDetailViewModel by viewModels()
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var headerAdapter: DiscussionHeaderAdapter
    private lateinit var markwon: Markwon
    private var discussionId: Long = 0
    private var cachedUserId: Long = -1
    private var cachedRole: String = "user"
    private var isLoggedIn: Boolean = false
    private var currentDiscussionAuthorId: Long = -1
    private var replyParentCommentId: Long? = null
    private var replyTargetName: String = ""
    private var skeletonAnimator: SkeletonAnimator? = null
    private var contentShown: Boolean = false

    private val hb: LayoutDiscussionDetailHeaderBinding?
        get() = headerAdapter.headerBinding

    private fun renderDiscussionAuthorAvatar(avatarUrl: String?) {
        val h = hb ?: return
        val placeholderPadding = (6 * resources.displayMetrics.density).toInt()
        if (!avatarUrl.isNullOrEmpty()) {
            h.ivAuthorAvatar.imageTintList = null
            h.ivAuthorAvatar.setPadding(0, 0, 0, 0)
            Glide.with(this)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(h.ivAuthorAvatar)
        } else {
            Glide.with(this).clear(h.ivAuthorAvatar)
            h.ivAuthorAvatar.setImageResource(R.drawable.ic_person)
            h.ivAuthorAvatar.imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(h.ivAuthorAvatar, com.google.android.material.R.attr.colorOnSecondaryContainer)
            )
            h.ivAuthorAvatar.setPadding(
                placeholderPadding,
                placeholderPadding,
                placeholderPadding,
                placeholderPadding
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscussionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun prefillHeader() {
        val args = arguments ?: return
        val h = hb ?: return
        val title = args.getString("prefill_title")
        val author = args.getString("prefill_author_name")
        val category = args.getString("prefill_category_name")
        val time = args.getString("prefill_created_at")
        if (!title.isNullOrEmpty()) h.tvTitle.text = title
        if (!author.isNullOrEmpty()) h.tvAuthor.text = author
        if (!category.isNullOrEmpty()) h.chipCategory.text = category
        if (!time.isNullOrEmpty()) h.tvTime.text = TimeUtil.formatRelative(time)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        discussionId = arguments?.getLong("discussion_id", 0L) ?: 0L
        if (discussionId <= 0L) {
            Toast.makeText(requireContext(), "无效的讨论ID", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }
        markwon = Markwon.builder(requireContext())
            .usePlugin(GlideImagesPlugin.create(requireContext()))
            .build()
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.menu_discussion_detail)

        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle
        navHandle?.getLiveData<Boolean>("refresh_discussion_detail")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    viewModel.loadDiscussion(discussionId)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_discussions", true)
                    navHandle.remove<Boolean>("refresh_discussion_detail")
                }
            }

        navHandle?.getLiveData<String>("discussion_result_message")
            ?.observe(viewLifecycleOwner) { msg ->
                if (!msg.isNullOrEmpty()) {
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>("discussion_result_message")
                }
            }

        headerAdapter = DiscussionHeaderAdapter()
        commentAdapter = CommentAdapter(
            onReplyClick = { comment ->
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!requireLoggedIn(binding.root)) return@launch
                    beginReplyTo(comment)
                }
            },
            onCopyClick = { comment ->
                copyCommentContent(comment.content.orEmpty())
            },
            onDeleteClick = { comment ->
                if (comment.authorId == cachedUserId || cachedRole == "admin" || cachedRole == "editor") {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除评论")
                        .setMessage("确定删除这条评论吗？")
                        .setPositiveButton("删除") { _, _ -> viewModel.deleteComment(discussionId, comment.id) }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    Snackbar.make(binding.root, "你没有权限删除这条评论", Snackbar.LENGTH_SHORT).show()
                }
            },
            onLikeClick = { comment ->
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!requireLoggedIn(binding.root)) return@launch
                    viewModel.likeComment(discussionId, comment.id)
                }
            },
            onAvatarClick = { comment ->
                if (comment.authorId > 0) {
                    val bundle = Bundle().apply { putLong("user_id", comment.authorId) }
                    findNavController().navigate(R.id.publicProfileFragment, bundle, slideNavOptions())
                }
            }
        )
        binding.rvMain.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = ConcatAdapter(headerAdapter, commentAdapter)
        }
        binding.rvMain.post { prefillHeader() }

        showSkeleton(binding.skeletonContainer.root, binding.layoutError)
        skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)

        binding.btnLoginReply.setOnClickListener {
            findNavController().navigate(R.id.loginFragment, null, slideNavOptions())
        }

        binding.btnSend.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                if (!requireLoggedIn(binding.root)) {
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

        observeData()
        refreshUserContext(loadDiscussion = true)
    }

    private fun observeData() {
        viewModel.discussion.observe(viewLifecycleOwner) { discussion ->
            discussion ?: return@observe
            val h = hb ?: return@observe
            if (!contentShown) {
                contentShown = true
                skeletonAnimator?.stop()
                hideSkeletonAndShow(binding.skeletonContainer.root, binding.rvMain)
            }
            currentDiscussionAuthorId = discussion.authorId
            h.tvTitle.text = discussion.title
            h.tvAuthor.text = discussion.authorName ?: "匿名"
            val navigateToAuthor = View.OnClickListener {
                if (discussion.authorId > 0) {
                    val bundle = Bundle().apply { putLong("user_id", discussion.authorId) }
                    findNavController().navigate(R.id.publicProfileFragment, bundle, slideNavOptions())
                }
            }
            h.tvAuthor.setOnClickListener(navigateToAuthor)
            h.ivAuthorAvatar.setOnClickListener(navigateToAuthor)
            renderDiscussionAuthorAvatar(discussion.authorAvatar)
            h.tvTime.text = TimeUtil.formatRelative(discussion.createdAt)
            h.tvViewCount.text = "${discussion.viewCount} 浏览"
            h.tvContent.visibility = View.VISIBLE
            h.chipCategory.text = discussion.categoryName.orEmpty().ifEmpty {
                DiscussionCategoryDefaults.label(discussion.category)
            }

            // Handle block/palette share preview
            h.blockPreviewContainer.removeAllViews()
            if (BlockShareHelper.containsAnyShare(discussion.content.orEmpty())) {
                val cleanContent = BlockShareHelper.getCleanContent(discussion.content.orEmpty())
                if (cleanContent.isNotEmpty()) {
                    markwon.setMarkdown(h.tvContent, preserveDiscussionLineBreaks(cleanContent))
                } else {
                    h.tvContent.visibility = View.GONE
                }

                val blockJson = BlockShareHelper.extractBlockJson(discussion.content.orEmpty())
                if (blockJson != null) {
                    val preview = BlockShareHelper.createPreviewView(
                        requireContext(), h.blockPreviewContainer, blockJson, showActions = true
                    )
                    h.blockPreviewContainer.addView(preview)
                }

                val paletteJson = BlockShareHelper.extractPaletteJson(discussion.content.orEmpty())
                if (paletteJson != null) {
                    val preview = BlockShareHelper.createPalettePreviewView(
                        requireContext(), h.blockPreviewContainer, paletteJson, showActions = true
                    ) {
                        val bundle = Bundle().apply {
                            putString("palette_json", paletteJson.toString())
                        }
                        findNavController().navigate(R.id.paletteDetailFragment, bundle, slideNavOptions())
                    }
                    h.blockPreviewContainer.addView(preview)
                }

                if (blockJson != null || paletteJson != null) {
                    h.blockPreviewContainer.visibility = View.VISIBLE
                }
            } else {
                markwon.setMarkdown(h.tvContent, preserveDiscussionLineBreaks(discussion.content.orEmpty()))
                h.blockPreviewContainer.visibility = View.GONE
            }

            // Edit limited to author or admin (matches backend); delete includes editor for moderation.
            val canEdit = discussion.authorId == cachedUserId || cachedRole == "admin"
            val canDelete = canEdit || cachedRole == "editor"
            binding.toolbar.menu.findItem(R.id.action_edit)?.isVisible = canEdit
            binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = canDelete

            binding.toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_share -> {
                        val shareText = "${discussion.title}\n\n${discussion.content.orEmpty().take(200)}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, discussion.title)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        startActivity(Intent.createChooser(intent, "分享讨论"))
                        true
                    }
                    R.id.action_edit -> {
                        val bundle = Bundle().apply { putLong("discussion_id", discussionId) }
                        findNavController().navigate(R.id.createDiscussionFragment, bundle, slideNavOptions())
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
            hb?.tvCommentsHeader?.text = "评论 (${comments.size})"
            hb?.layoutNoComments?.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading && contentShown) binding.progressBar.show() else binding.progressBar.hide()
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                skeletonAnimator?.stop()
                binding.skeletonContainer.root.visibility = View.GONE
                binding.layoutError.visibility = View.VISIBLE
                binding.tvError.text = error
            } else {
                binding.layoutError.visibility = View.GONE
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            if (!contentShown) {
                showSkeleton(binding.skeletonContainer.root, binding.layoutError)
                skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
            }
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
            }
        }
    }

    private fun preserveDiscussionLineBreaks(text: String): String {
        val normalized = text.replace("\r\n", "\n")
        return normalized.replace(Regex("(?<!\\n)\\n(?!\\n)"), "  \n")
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            refreshUserContext()
        }
    }

    private fun refreshUserContext(loadDiscussion: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            cachedUserId = if (isLoggedIn) ApiClient.getTokenManager().getUserId().first() ?: -1 else -1
            cachedRole = if (isLoggedIn) ApiClient.getTokenManager().getUserRole().first() ?: "user" else "user"
            if (_binding == null) return@launch
            commentAdapter.updateUserContext(cachedUserId, cachedRole)
            renderReplyComposer()
            if (loadDiscussion) {
                viewModel.loadDiscussion(discussionId)
            }
        }
    }

    private fun renderReplyComposer() {
        binding.layoutReplyGuest.visibility = if (isLoggedIn) View.GONE else View.VISIBLE
        binding.layoutReply.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        if (!isLoggedIn) {
            binding.etReply.text?.clear()
            clearReplyTarget()
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

    override fun onDestroyView() {
        super.onDestroyView()
        skeletonAnimator?.stop()
        skeletonAnimator = null
        contentShown = false
        _binding = null
    }
}
