package com.sknote.app.ui.article

import android.content.Intent
import android.os.Bundle
import android.text.util.Linkify
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.util.slideNavOptions
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Article
import com.sknote.app.databinding.FragmentArticleDetailBinding
import com.sknote.app.util.NavPrefill
import com.sknote.app.util.SkeletonAnimator
import com.sknote.app.util.hideSkeletonAndShow
import com.sknote.app.util.prefill
import com.sknote.app.util.requireLoggedIn
import com.sknote.app.util.showSkeleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.sknote.app.util.TimeUtil
import io.noties.markwon.Markwon
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import com.sknote.app.util.AppSnackbar

class ArticleDetailFragment : Fragment() {

    private var _binding: FragmentArticleDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ArticleDetailViewModel by viewModels()
    private lateinit var markwon: Markwon
    private var currentArticleTitle: String = ""
    private var currentArticleSummary: String = ""
    private var currentArticleContent: String = ""
    private var currentArticleAuthorId: Long = -1L
    private var isBookmarked = false
    private var isLiking = false
    private var isBookmarking = false
    private var cachedUserId: Long = -1L
    private var cachedRole: String = "user"
    private var skeletonAnimator: SkeletonAnimator? = null
    private var contentShown: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArticleDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        markwon = Markwon.builder(requireContext())
            .usePlugin(GlideImagesPlugin.create(requireContext()))
            // 正文里的链接默认**点不动**：ClickableSpan 必须有 MovementMethod 才接得到点击，
            // 而 core 只在 movementMethod 为 null 时才补 LinkMovementMethod
            // （CorePlugin.afterSetText）—— 布局里的 textIsSelectable="true" 已把它设成
            // ArrowKeyMovementMethod，于是"能选中但点不开"。MovementMethodPlugin 的
            // beforeSetText 是无条件覆盖，并通知 CorePlugin 让路，所以与 textIsSelectable
            // 共存：链接点得开，长按仍能选中复制（第四十三批真机截图已证）。
            .usePlugin(MovementMethodPlugin.link())
            // 裸 URL（用户直接粘贴的网址）默认只是普通文字 —— core 不识别裸 URL，要靠
            // LinkifyPlugin 在**渲染期**补 span（它往 CorePlugin 注册 OnTextAddedListener，
            // 与 movementMethod 是两回事，所以两个插件的注册顺序无所谓）。口径收窄到
            // WEB_URLS：默认的 create() 连邮箱和电话也会变成 mailto:/tel: 链接。
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
            .build()
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.menu_article_detail)

        val articleId = arguments?.getLong("article_id", 0L) ?: 0L
        if (articleId <= 0L) {
            Toast.makeText(requireContext(), "无效的文章ID", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle
        navHandle?.getLiveData<Boolean>("refresh_articles")
            ?.observe(viewLifecycleOwner) { refresh ->
                if (refresh == true) {
                    viewModel.loadArticle(articleId)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_articles", true)
                    navHandle.remove<Boolean>("refresh_articles")
                }
            }

        navHandle?.getLiveData<String>("article_manage_message")
            ?.observe(viewLifecycleOwner) { msg ->
                if (!msg.isNullOrEmpty()) {
                    AppSnackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>("article_manage_message")
                }
            }

        setupScrollProgress()

        // 只有拿到**含正文**的完整 Article 才走预填，否则退回骨架路径。
        //
        // 正文缺失时必须退回骨架：这一页的「相关讨论」卡片与点赞/收藏操作栏都在正文
        // 下方、且同在滚动容器内，正文留空会让它们先出现在屏幕上、再被正文顶下去，
        // 比骨架更糟。
        //
        // 现状：**四条列表查询都刻意带上了 `a.content`** —— `articles.ts:21-26`（列表与搜索）、
        // `aggregated.ts:18`（首页聚合）、`bookmarks.ts:15-17`（收藏）、`bookmarks.ts:82-84`
        // （阅读历史），每一处的注释都写明「别把这一列删掉」（第二十批）。所以从这些入口
        // 进来预填都会命中，第一帧就是真实正文（真机转场中间帧可见，不需要等网络）。
        // 只有**正文本身为空**的文章才会退回骨架。
        // 若哪天有人把某一条查询的 `a.content` 去掉，这条路径会**静默**退回骨架 ——
        // 不会崩，但会丢掉这个优化。
        val prefill = arguments?.prefill<Article>(NavPrefill.KEY_ARTICLE)
            ?.takeIf { !it.content.isNullOrEmpty() }
        if (prefill != null) {
            binding.skeletonContainer.root.visibility = View.GONE
        } else {
            showSkeleton(binding.skeletonContainer.root, binding.scrollView, binding.layoutError)
            skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
        }

        viewModel.loadArticle(articleId)

        viewLifecycleOwner.lifecycleScope.launch {
            cachedUserId = ApiClient.getTokenManager().getUserId().first() ?: -1L
            cachedRole = ApiClient.getTokenManager().getUserRole().first() ?: "user"
            updateEditVisibility()
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> {
                    if (currentArticleTitle.isNotEmpty()) {
                        val shareText = "$currentArticleTitle\n\n${currentArticleSummary.ifEmpty { currentArticleContent.take(200) }}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, currentArticleTitle)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        startActivity(Intent.createChooser(intent, "分享文章"))
                    }
                    true
                }
                R.id.action_edit -> {
                    val bundle = Bundle().apply { putLong("article_id", articleId) }
                    findNavController().navigate(R.id.articleEditorFragment, bundle, slideNavOptions())
                    true
                }
                else -> false
            }
        }

        viewModel.article.observe(viewLifecycleOwner) { article ->
            article ?: return@observe
            revealContent()
            currentArticleTitle = article.title
            currentArticleSummary = article.summary.orEmpty()
            currentArticleContent = article.content.orEmpty()
            currentArticleAuthorId = article.authorId
            updateEditVisibility()

            binding.tvTitle.text = article.title
            binding.tvAuthor.text = article.authorName ?: "Unknown"
            bindAuthorAvatar(article.authorAvatar)
            binding.tvTime.text = TimeUtil.formatRelative(article.createdAt)
            binding.tvMeta.text = "${article.viewCount} 阅读"
            // 标题是静态串，放在布局里（`app:title`）—— 原先只在这里赋值，导致骨架态与
            // 加载失败态下顶栏是"只有返回箭头的空标题栏"。

            if (!article.categoryName.isNullOrEmpty()) {
                binding.tvCategory.text = article.categoryName.orEmpty()
                binding.tvCategory.visibility = View.VISIBLE
            }

            binding.tvLikeCount.text = if (article.likeCount > 0) "${article.likeCount}" else "点赞"
            binding.readingProgress.visibility = View.VISIBLE

            markwon.setMarkdown(binding.tvContent, article.content.orEmpty())
            // 正文短到不可滚动时滚动回调永远不会触发，那条 2dp 轨道会一直停在 0%，
            // 看起来像一条坏掉的加载条 —— 布局完成后量一次，滚不动就把它收掉。
            binding.scrollView.post { syncProgressVisibility() }
        }

        // 观察者注册之后再套用预填，保证 setPrefilledArticle 一定会被渲染。
        // 这一页没有需要等待 ViewHolder 的列表，渲染是同步的，因此内容与可见性
        // 都在首次布局之前就绪，不会出现空白帧。
        prefill?.let { viewModel.setPrefilledArticle(it) }

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
                showSkeleton(binding.skeletonContainer.root, binding.scrollView, binding.layoutError)
                skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
            }
            viewModel.loadArticle(articleId)
        }

        binding.btnLike.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                if (isLiking) return@launch
                if (!requireLoggedIn(binding.root)) {
                    return@launch
                }
                try {
                    isLiking = true
                    binding.btnLike.isEnabled = false
                    val response = ApiClient.getService().likeArticle(articleId)
                    if (response.isSuccessful) {
                        val liked = response.body()?.liked ?: false
                        AppSnackbar.make(binding.root, if (liked) "已点赞" else "已取消点赞", Snackbar.LENGTH_SHORT).show()
                        viewModel.loadArticle(articleId)
                    }
                } catch (e: Exception) {
                    AppSnackbar.make(binding.root, "操作失败", Snackbar.LENGTH_SHORT).show()
                } finally {
                    isLiking = false
                    _binding?.btnLike?.isEnabled = true
                }
            }
        }

        binding.btnShare.setOnClickListener {
            binding.toolbar.menu.performIdentifierAction(R.id.action_share, 0)
        }

        val navOptions = androidx.navigation.NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()

        binding.btnDiscuss.setOnClickListener {
            val bundle = Bundle().apply { putLong("article_id", articleId) }
            findNavController().navigate(R.id.discussionListFragment, bundle, navOptions)
        }

        binding.cardDiscussions.setOnClickListener {
            val bundle = Bundle().apply { putLong("article_id", articleId) }
            findNavController().navigate(R.id.discussionListFragment, bundle, navOptions)
        }

        binding.btnBookmark.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                if (isBookmarking) return@launch
                if (!requireLoggedIn(binding.root)) {
                    return@launch
                }
                try {
                    isBookmarking = true
                    binding.btnBookmark.isEnabled = false
                    val response = ApiClient.getService().toggleBookmark(articleId)
                    if (response.isSuccessful) {
                        isBookmarked = response.body()?.bookmarked ?: false
                        updateBookmarkUI()
                        AppSnackbar.make(binding.root, if (isBookmarked) "已收藏" else "已取消收藏", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    AppSnackbar.make(binding.root, "操作失败", Snackbar.LENGTH_SHORT).show()
                } finally {
                    isBookmarking = false
                    _binding?.btnBookmark?.isEnabled = true
                }
            }
        }

        // 检查收藏状态 + 记录阅读历史
        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (isLoggedIn) {
                try {
                    val checkResp = ApiClient.getService().checkBookmark(articleId)
                    if (checkResp.isSuccessful) {
                        isBookmarked = checkResp.body()?.bookmarked ?: false
                        updateBookmarkUI()
                    }
                } catch (_: Exception) { }
                try {
                    ApiClient.getService().recordHistory(articleId)
                } catch (_: Exception) { }
            }
        }
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
            binding.scrollView.visibility = View.VISIBLE
        } else {
            animator.stop()
            skeletonAnimator = null
            hideSkeletonAndShow(binding.skeletonContainer.root, binding.scrollView)
        }
    }

    private fun updateBookmarkUI() {
        if (isBookmarked) {
            binding.ivBookmark.setImageResource(R.drawable.ic_bookmark_filled)
            binding.tvBookmarkLabel.text = "已收藏"
            val tv = TypedValue()
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
            binding.tvBookmarkLabel.setTextColor(resources.getColor(tv.resourceId, null))
        } else {
            binding.ivBookmark.setImageResource(R.drawable.ic_bookmark_outline)
            binding.tvBookmarkLabel.text = "收藏"
            val tv = TypedValue()
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true)
            binding.tvBookmarkLabel.setTextColor(resources.getColor(tv.resourceId, null))
        }
    }

    private fun updateEditVisibility() {
        val canEdit = cachedRole == "admin" || currentArticleAuthorId == cachedUserId
        _binding?.toolbar?.menu?.findItem(R.id.action_edit)?.isVisible = canEdit
    }

    private fun setupScrollProgress() {
        binding.scrollView.setOnScrollChangeListener { v: View, _, scrollY, _, _ ->
            val child = (v as androidx.core.widget.NestedScrollView).getChildAt(0) ?: return@setOnScrollChangeListener
            val maxScroll = child.height - v.height
            if (maxScroll > 0) {
                // 兜底：万一 syncProgressVisibility() 量早了把它收掉，这里第一条滚动就恢复。
                binding.readingProgress.visibility = View.VISIBLE
                val progress = (scrollY * 100) / maxScroll
                binding.readingProgress.progress = progress.coerceIn(0, 100)
            }
        }
    }

    /**
     * 内容不足以滚动时把进度条收掉。
     * 这种情况滚动回调**根本不会触发**（没有可滚动距离），轨道会一直停在 0%，
     * 看上去像一条坏掉的加载条，所以要在布局完成后主动量一次。
     */
    private fun syncProgressVisibility() {
        val sv = _binding?.scrollView ?: return
        val child = sv.getChildAt(0) ?: return
        binding.readingProgress.visibility = if (child.height - sv.height > 0) View.VISIBLE else View.GONE
    }

    /**
     * 作者头像。与讨论区同一套写法（`CommentAdapter:144-157`）：有头像就 circleCrop 载入并去掉
     * 占位内边距，没有就回到 `ic_person` 占位、留 4dp 内边距。
     * 接口一直在返回 `author_avatar`（`articles.ts:79`），但此前这个 ImageView 连 id 都没有，
     * 结构上不可能被赋值 —— 所以永远显示占位人像。
     */
    private fun bindAuthorAvatar(url: String?) {
        val iv = _binding?.ivAvatar ?: return
        val placeholderPadding = (4 * resources.displayMetrics.density).toInt()
        if (!url.isNullOrEmpty()) {
            iv.imageTintList = null
            iv.setPadding(0, 0, 0, 0)
            Glide.with(this)
                .load(url)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(iv)
        } else {
            Glide.with(this).clear(iv)
            iv.setImageResource(R.drawable.ic_person)
            iv.setPadding(placeholderPadding, placeholderPadding, placeholderPadding, placeholderPadding)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        skeletonAnimator?.stop()
        skeletonAnimator = null
        contentShown = false
        _binding = null
    }
}
