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
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Comment
import com.sknote.app.data.model.Discussion
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
import com.sknote.app.util.AppSnackbar

class DiscussionDetailFragment : Fragment() {

    private var _binding: FragmentDiscussionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiscussionDetailViewModel by viewModels()
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var commentPlaceholderAdapter: CommentPlaceholderAdapter
    private lateinit var headerAdapter: DiscussionHeaderAdapter
    private lateinit var markwon: Markwon
    private var discussionId: Long = 0
    /** 本次已上报过已读的讨论 id，避免每次 liveData 回调都打一次接口。 */
    private val markedReadIds = mutableSetOf<Long>()
    private var cachedUserId: Long = -1
    private var cachedRole: String = "user"
    private var isLoggedIn: Boolean = false
    private var currentDiscussionAuthorId: Long = -1
    private var replyParentCommentId: Long? = null
    private var replyTargetName: String = ""
    private var skeletonAnimator: SkeletonAnimator? = null
    private var contentShown: Boolean = false

    /** 导航参数带来的预填数据，等页头 ViewHolder 就绪后套用，套用后置空。 */
    private var pendingPrefill: Discussion? = null

    /** 真实评论是否已经渲染过。用于避免预填的占位行与真实评论同时存在。 */
    private var commentsLoaded: Boolean = false

    /** 当前展示的评论（threadedSort 之后的顺序）。点引用块跳转时按它定位。 */
    private var displayedComments: List<Comment> = emptyList()

    /** 已展开的楼（根评论 id）。默认全部收起，只显示根评论 + 前几条回复。 */
    private val expandedRoots = mutableSetOf<Long>()

    /** 跳转高亮的延时清理任务：连续跳转时先撤掉上一次，否则旧任务会提前清掉新高亮。 */
    private val clearHighlight = Runnable {
        commentAdapter.highlight(null)
        // 由根适配器（ConcatAdapter）广播，理由见 CommentAdapter.highlight 的说明
        _binding?.rvMain?.adapter?.notifyDataSetChanged()
    }

    private companion object {
        /**
         * 占位评论行上限。占位行只需铺满页头以下的首屏，多出来的本来就在屏幕之外，
         * 因此不必按真实评论数铺满——真实评论数可能是几百条。
         */
        const val MAX_COMMENT_PLACEHOLDERS = 6

        /** 等页头 ViewHolder 就绪的帧数上限，防止极端情况下无限重试。 */
        const val MAX_PREFILL_WAIT_FRAMES = 10

        /** 引用跳转后高亮停留时长：够看清落点，又不至于一直亮着。 */
        const val HIGHLIGHT_MS = 1200L

        /** 收起态下一楼先显示几条回复。 */
        const val COLLAPSED_REPLY_PREVIEW = 2

        const val STATE_EXPANDED_ROOTS = "expanded_roots"
    }

    private val hb: LayoutDiscussionDetailHeaderBinding?
        get() = headerAdapter.headerBinding

    /**
     * 打开详情即上报「已读」（幂等）。只在登录后调用 —— 后端那个接口需要鉴权。
     * 成功后让列表页知道要刷新未读状态；失败静默：已读只是增益信息，
     * 不该因为网络问题弹提示打扰阅读。
     */
    private fun markDiscussionRead(discussionId: Long) {
        if (markedReadIds.contains(discussionId)) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!ApiClient.getTokenManager().isLoggedIn().first()) return@launch
                val response = ApiClient.getService().markDiscussionRead(discussionId)
                if (response.isSuccessful) {
                    markedReadIds.add(discussionId)
                    findNavController().previousBackStackEntry?.savedStateHandle
                        ?.set("refresh_discussions", true)
                }
            } catch (_: Exception) {
                // 忽略：下次打开再试
            }
        }
    }

    /**
     * 标签渲染成 #chips（与片段详情同一形态；解析规则也一致：逗号/顿号/空白切分）。
     * 无标签时整块隐藏，不留空白行。
     */
    private fun bindTags(rawTags: String) {
        val h = hb ?: return
        val tags = rawTags
            .split(Regex("[,，、\\s]+"))
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }

        if (tags.isEmpty()) {
            h.layoutTags.visibility = View.GONE
            h.chipGroupTags.removeAllViews()
            return
        }

        h.layoutTags.visibility = View.VISIBLE
        h.chipGroupTags.removeAllViews()
        tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = "#$tag"
                isCheckable = false
                isClickable = false
                isCloseIconVisible = false
                setEnsureMinTouchTargetSize(false)
            }
            h.chipGroupTags.addView(chip)
        }
    }

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

    /**
     * 用导航参数带来的讨论数据渲染页头。
     *
     * 数据来源见 [DiscussionNavArgs]：列表接口本就返回完整正文与各项计数，
     * 所以页头第一帧就能是真实内容，不需要任何占位块。
     * 必须等 rvMain 完成首次布局后调用——headerBinding 在 header ViewHolder 建好前为 null。
     */
    private fun applyPrefill(prefill: Discussion) {
        // 真实评论已经在手上时绝不能再插占位行。配置变更后 ViewModel 存活，
        // 观察者会带着已加载的 comments 先触发；这里若无条件插入，占位行就会
        // 被追加到真实评论下方。
        if (!commentsLoaded) {
            commentPlaceholderAdapter.show(prefill.replyCount.coerceIn(0, MAX_COMMENT_PLACEHOLDERS))
            hb?.tvCommentsHeader?.text = "评论 (${prefill.replyCount})"
        }
        // 交给观察者按常规路径渲染，避免页头出现「预填一套 / 接口一套」两段逻辑。
        viewModel.setPrefilledDiscussion(prefill)
    }

    /**
     * 等页头 ViewHolder 建好后再套用预填数据。
     *
     * 不能只 post 一次：View.post 在视图尚未 attach 时会排进 HandlerActionQueue，
     * 由 dispatchAttachedToWindow 执行，那一刻**早于首次 layout**，此时
     * headerBinding 还是 null，预填会被静默丢掉（旧实现就是 `hb ?: return`，
     * 预填是否生效取决于 attach 与 layout 的先后，并不可靠）。
     * 这里逐帧重试到就绪为止，并在极端情况下放弃——放弃只是退回「等接口」，
     * 不会卡住页面。
     */
    private fun renderPendingPrefill(attempt: Int = 0) {
        val prefill = pendingPrefill ?: return
        if (hb == null || !binding.rvMain.isLaidOut) {
            if (attempt >= MAX_PREFILL_WAIT_FRAMES) {
                pendingPrefill = null
                return
            }
            binding.rvMain.post {
                if (_binding != null) {
                    renderPendingPrefill(attempt + 1)
                }
            }
            return
        }
        pendingPrefill = null
        applyPrefill(prefill)
    }

    /**
     * 首次显示真实内容。两条路径的进入方式不同，因此收敛到这里：
     * 有预填数据时首帧就是正确的，直接显示；没有预填数据时整页骨架正在呼吸，交叉淡出。
     */
    private fun revealContent() {
        if (contentShown) return
        contentShown = true
        val animator = skeletonAnimator
        if (animator == null) {
            binding.rvMain.visibility = View.VISIBLE
        } else {
            animator.stop()
            skeletonAnimator = null
            hideSkeletonAndShow(binding.skeletonContainer.root, binding.rvMain)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        discussionId = arguments?.getLong(KEY_DISCUSSION_ID, 0L) ?: 0L
        if (discussionId <= 0L) {
            Toast.makeText(requireContext(), "无效的讨论ID", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }
        markwon = Markwon.builder(requireContext())
            .usePlugin(GlideImagesPlugin.create(requireContext()))
            .build()
        // 折叠状态活过旋转：fragment 实例会随配置变更重建，字段本身留不住。
        savedInstanceState?.getLongArray(STATE_EXPANDED_ROOTS)?.forEach { expandedRoots.add(it) }
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
                    AppSnackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>("discussion_result_message")
                }
            }

        headerAdapter = DiscussionHeaderAdapter()
        commentPlaceholderAdapter = CommentPlaceholderAdapter()
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
                    AppSnackbar.make(binding.root, "你没有权限删除这条评论", Snackbar.LENGTH_SHORT).show()
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
            },
            onEditClick = { comment ->
                if (comment.authorId == cachedUserId || cachedRole == "admin") {
                    showEditCommentDialog(comment)
                } else {
                    AppSnackbar.make(binding.root, "你没有权限编辑这条评论", Snackbar.LENGTH_SHORT).show()
                }
            },
            onQuoteClick = { comment -> jumpToQuotedComment(comment) },
            onExpandClick = { rootId, expanded ->
                if (expanded) expandedRoots.remove(rootId) else expandedRoots.add(rootId)
                renderComments(displayedComments)
            }
        )
        binding.rvMain.apply {
            layoutManager = LinearLayoutManager(context)
            // 顺序即版面顺序：页头 → 占位评论行 → 真实评论行。
            // 占位行与真实行共用同一套行高（见 layout_skeleton_comment_row.xml），
            // 数据回来后占位整体清空，首条评论的顶边位置不变。
            adapter = ConcatAdapter(headerAdapter, commentPlaceholderAdapter, commentAdapter)
        }

        val prefill = arguments?.prefillDiscussion()
        if (prefill != null) {
            // 预填路径：页头所需数据在点击时就已具备，首帧直接是真实内容，不使用整页骨架。
            binding.skeletonContainer.root.visibility = View.GONE
            pendingPrefill = prefill
        } else {
            // 冷启动路径（通知入口只带 discussion_id）：没有可预填的数据，
            // 正文高度在数据到达前本质上不可知，只能沿用整页骨架。
            showSkeleton(binding.skeletonContainer.root, binding.layoutError)
            skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
        }

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
                    AppSnackbar.make(binding.root, "请输入评论内容", Snackbar.LENGTH_SHORT).show()
                    return@launch
                }
                binding.btnSend.isEnabled = false
                viewModel.sendComment(discussionId, content, replyParentCommentId)
            }
        }

        binding.btnCancelReply.setOnClickListener {
            clearReplyTarget()
        }

        // 点提示条 = 跳回被引用的那条评论（与点评论里的引用块同一行为）
        binding.layoutReplyIndicator.setOnClickListener {
            replyParentCommentId?.let { jumpToComment(it) }
        }

        observeData()
        // 观察者注册之后再套用预填，保证 setPrefilledDiscussion 一定会被渲染。
        renderPendingPrefill()
        refreshUserContext(loadDiscussion = true)
    }

    private fun observeData() {
        viewModel.discussion.observe(viewLifecycleOwner) { discussion ->
            discussion ?: return@observe
            val h = hb ?: return@observe
            revealContent()
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
            bindTags(discussion.tags.orEmpty())
            markDiscussionRead(discussion.id)

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
            val isAdmin = cachedRole == "admin"
            binding.toolbar.menu.findItem(R.id.action_edit)?.isVisible = canEdit
            binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = canDelete
            binding.toolbar.menu.findItem(R.id.action_pin)?.apply {
                isVisible = isAdmin
                title = if (discussion.isPinned == 1) "取消置顶" else "置顶"
            }
            binding.toolbar.menu.findItem(R.id.action_close)?.apply {
                isVisible = isAdmin
                title = if (discussion.isClosed == 1) "解锁讨论" else "锁定讨论"
            }

            // 锁定的讨论：非 admin/editor 隐藏评论框
            renderReplyComposer()

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
                    R.id.action_pin -> {
                        val newPinned = discussion.isPinned != 1
                        viewModel.togglePin(discussionId, newPinned) { ok, msg ->
                            if (_binding == null) return@togglePin
                            AppSnackbar.make(
                                binding.root,
                                if (ok) (if (newPinned) "已置顶" else "已取消置顶") else (msg ?: "操作失败"),
                                Snackbar.LENGTH_SHORT
                            ).show()
                            if (ok) {
                                findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_discussions", true)
                            }
                        }
                        true
                    }
                    R.id.action_close -> {
                        val newClosed = discussion.isClosed != 1
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(if (newClosed) "锁定讨论" else "解锁讨论")
                            .setMessage(
                                if (newClosed) "锁定后将禁止其他用户继续回复，仍可解锁恢复。"
                                else "解锁后允许所有用户继续回复。"
                            )
                            .setPositiveButton("确定") { _, _ ->
                                viewModel.toggleClose(discussionId, newClosed) { ok, msg ->
                                    if (_binding == null) return@toggleClose
                                    AppSnackbar.make(
                                        binding.root,
                                        if (ok) (if (newClosed) "已锁定" else "已解锁") else (msg ?: "操作失败"),
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                    if (ok) {
                                        findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_discussions", true)
                                    }
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
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
                AppSnackbar.make(binding.root, "已删除", Snackbar.LENGTH_SHORT).show()
                findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_discussions", true)
                findNavController().navigateUp()
            }
        }

        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            commentsLoaded = true
            displayedComments = comments
            // 真实评论到达，撤掉占位行。两者共用同一套行高，且首条评论的顶边
            // 与首条占位行的顶边都紧贴页头底部，因此这一步不产生位移。
            commentPlaceholderAdapter.clear()
            renderComments(comments)
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
                AppSnackbar.make(binding.root, "评论成功", Snackbar.LENGTH_SHORT).show()
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
        val isClosed = viewModel.discussion.value?.isClosed == 1
        val canBypassLock = cachedRole == "admin" || cachedRole == "editor"
        val locked = isClosed && !canBypassLock

        binding.layoutReplyClosed.visibility = if (locked) View.VISIBLE else View.GONE
        binding.layoutReplyGuest.visibility = if (!locked && !isLoggedIn) View.VISIBLE else View.GONE
        binding.layoutReply.visibility = if (!locked && isLoggedIn) View.VISIBLE else View.GONE

        if (locked || !isLoggedIn) {
            binding.etReply.text?.clear()
            clearReplyTarget()
        }
    }

    private fun beginReplyTo(comment: com.sknote.app.data.model.Comment) {
        replyParentCommentId = comment.id
        replyTargetName = comment.authorName ?: "匿名"
        binding.etReply.hint = "回复 @$replyTargetName"
        binding.tvReplyIndicator.text = "正在回复 @$replyTargetName"
        // 引用原文预览：写作时能看见自己要回的是哪句话。父评论本来就在本地列表里
        // （评论整篇一次返回，见 comments 观察者），不用再发请求。
        val quoted = comment.content?.trim().orEmpty()
        binding.tvReplyQuote.text = if (quoted.isEmpty()) "" else "「$quoted」"
        binding.tvReplyQuote.visibility = if (quoted.isEmpty()) View.GONE else View.VISIBLE
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
        binding.tvReplyQuote.text = ""
        binding.tvReplyQuote.visibility = View.GONE
        binding.layoutReplyIndicator.visibility = View.GONE
    }

    /** 点评论里的引用块：跳到它引用的那条评论。 */
    private fun jumpToQuotedComment(comment: Comment) {
        comment.parentId?.let { jumpToComment(it) }
    }

    /**
     * 滚到指定评论并高亮一下。**目标可能正被折叠在某一楼里，先把它所在的楼展开。**
     *
     * 顺序很关键：`submitList` 是异步 diff，必须等它提交完再算下标，否则滚动位置算的是
     * 旧的折叠列表。找不到目标（父评论已被删除、或不在本次返回里）时给一句提示，
     * 而不是静默无反应 —— 点了没动静比提示"不在了"更难排查。
     */
    private fun jumpToComment(commentId: Long) {
        if (displayedComments.none { it.id == commentId }) {
            AppSnackbar.make(binding.root, "原评论已不在了", Snackbar.LENGTH_SHORT).show()
            return
        }
        val rootId = rootIdMap(displayedComments)[commentId]
        if (rootId != null && rootId != commentId && expandedRoots.add(rootId)) {
            renderComments(displayedComments) { scrollToComment(commentId) }
        } else {
            scrollToComment(commentId)
        }
    }

    private fun scrollToComment(commentId: Long) {
        val index = commentAdapter.currentList.indexOfFirst {
            it is CommentRow.Item && it.comment.id == commentId
        }
        if (index < 0) {
            AppSnackbar.make(binding.root, "原评论已不在了", Snackbar.LENGTH_SHORT).show()
            return
        }
        val lm = binding.rvMain.layoutManager as? LinearLayoutManager ?: return
        binding.rvMain.removeCallbacks(clearHighlight)
        val offset = headerAdapter.itemCount + commentPlaceholderAdapter.itemCount
        commentAdapter.highlight(commentId)
        // 先广播重绑、再排滚动：两个动作都在下一次布局生效，但滚动位置必须在
        // 重绑之后排队，否则会被整体刷新吃掉。
        binding.rvMain.adapter?.notifyDataSetChanged()
        lm.scrollToPositionWithOffset(offset + index, 0)
        binding.rvMain.postDelayed(clearHighlight, HIGHLIGHT_MS)
    }

    /** 按当前折叠状态把整篇评论整形成列表行并提交（[onCommitted] 在 diff 应用后回调）。 */
    private fun renderComments(comments: List<Comment>, onCommitted: (() -> Unit)? = null) {
        val rows = buildRows(comments, expandedRoots)
        if (onCommitted != null) commentAdapter.submitList(rows, onCommitted) else commentAdapter.submitList(rows)
    }

    /**
     * 每一楼显示根评论 + 前 [COLLAPSED_REPLY_PREVIEW] 条回复，其余折叠成一行
     * 「展开其余 N 条回复」；已展开的楼全部显示，末尾补一行「收起回复」。
     *
     * 折叠掉的回复**不生成行**（不是隐藏），长楼因此不参与布局与滚动 —— 讨论 11 的
     * 15 条回复全挂在同一个根下，展开态约 2200dp（3.4 屏），收起后约一屏。
     *
     * 判据与 ViewModel 的 threadedSort 一致：追到「没有父评论、或父评论不在本篇里」的
     * 那一层作为楼根，所以孤儿评论自己就是一楼的根，不会掉出行列表。
     */
    private fun buildRows(comments: List<Comment>, expanded: Set<Long>): List<CommentRow> {
        if (comments.isEmpty()) return emptyList()
        val byId = comments.associateBy { it.id }
        val rootOf = rootIdMap(comments)
        val replyTotal = HashMap<Long, Int>()
        for (c in comments) {
            val r = rootOf.getValue(c.id)
            if (c.id != r) replyTotal[r] = (replyTotal[r] ?: 0) + 1
        }
        val seenReplies = HashMap<Long, Int>()
        val rows = ArrayList<CommentRow>(comments.size + replyTotal.size)
        for ((i, c) in comments.withIndex()) {
            val r = rootOf.getValue(c.id)
            val total = replyTotal[r] ?: 0
            val isExpanded = r in expanded
            val visible: Boolean
            if (c.id == r || isExpanded || total <= COLLAPSED_REPLY_PREVIEW) {
                visible = true
            } else {
                val n = (seenReplies[r] ?: 0) + 1
                seenReplies[r] = n
                visible = n <= COLLAPSED_REPLY_PREVIEW
            }
            if (visible) rows += CommentRow.Item(c, c.parentId?.let { byId[it] })
            // 楼结束（下一条属于别的楼，或已是最后一条）→ 补折叠 / 收起行
            val nextRoot = comments.getOrNull(i + 1)?.let { rootOf.getValue(it.id) }
            if (total > COLLAPSED_REPLY_PREVIEW && (nextRoot == null || nextRoot != r)) {
                rows += CommentRow.Expand(r, total - COLLAPSED_REPLY_PREVIEW, isExpanded)
            }
        }
        return rows
    }

    /** 解析每条评论所属楼的根评论 id（追到根祖先，带记忆化与环保护）。 */
    private fun rootIdMap(comments: List<Comment>): Map<Long, Long> {
        val byId = comments.associateBy { it.id }
        val memo = HashMap<Long, Long>(comments.size)
        for (c in comments) {
            if (memo.containsKey(c.id)) continue
            val path = ArrayList<Comment>(4)
            val seen = HashSet<Long>(4)
            var cur = c
            while (seen.add(cur.id)) {
                path += cur
                cur = cur.parentId?.let { byId[it] } ?: break
            }
            for (v in path) memo[v.id] = cur.id
        }
        return memo
    }

    private fun copyCommentContent(content: String) {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("comment", content)
        clipboard.setPrimaryClip(clip)
        AppSnackbar.make(binding.root, "评论内容已复制", Snackbar.LENGTH_SHORT).show()
    }

    private fun showEditCommentDialog(comment: Comment) {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val inputLayout = TextInputLayout(context).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            isCounterEnabled = true
            counterMaxLength = 5000
            hint = "评论内容"
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            setText(comment.content.orEmpty())
            setSelection(text?.length ?: 0)
            minLines = 3
            maxLines = 8
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        inputLayout.addView(editText)

        val container = android.widget.FrameLayout(context).apply {
            setPadding(padding, (8 * density).toInt(), padding, 0)
            addView(inputLayout)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("编辑评论")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val newContent = editText.text?.toString()?.trim().orEmpty()
                if (newContent.isEmpty()) {
                    inputLayout.error = "评论内容不能为空"
                    return@setOnClickListener
                }
                if (newContent.length > 5000) {
                    inputLayout.error = "评论内容最长5000个字符"
                    return@setOnClickListener
                }
                if (newContent == comment.content.orEmpty().trim()) {
                    dialog.dismiss()
                    return@setOnClickListener
                }
                inputLayout.error = null
                saveButton.isEnabled = false
                viewModel.updateComment(discussionId, comment.id, newContent) { success, message ->
                    if (_binding == null) return@updateComment
                    saveButton.isEnabled = true
                    if (success) {
                        dialog.dismiss()
                        AppSnackbar.make(binding.root, "评论已更新", Snackbar.LENGTH_SHORT).show()
                    } else {
                        AppSnackbar.make(binding.root, message ?: "更新失败", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLongArray(STATE_EXPANDED_ROOTS, expandedRoots.toLongArray())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMain.removeCallbacks(clearHighlight)
        skeletonAnimator?.stop()
        skeletonAnimator = null
        contentShown = false
        pendingPrefill = null
        commentsLoaded = false
        displayedComments = emptyList()
        _binding = null
    }
}
