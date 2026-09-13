package com.sknote.app.ui.article

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
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
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
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
        // 现状：**文章没有任何一条列表接口返回 `a.content`** —— `articles.ts:22` 的列表
        // 查询（文章列表与搜索都用它）是明确列列名的，`aggregated.ts:16` 的首页查询、
        // `bookmarks.ts:16`/`:82` 的收藏与历史查询同样没有取这一列；带 `SELECT a.*` 的
        // `articles.ts:75` 是**详情**查询，正是这里想避开的那次请求。因此这条预填路径
        // 目前对所有入口都会被守卫拒绝，页面走骨架 —— 与改造前一致，不会更糟。
        // 后端只要在列表查询里补上 `a.content`，这里无需改动即可自动生效。
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
            binding.tvTime.text = TimeUtil.formatRelative(article.createdAt)
            binding.tvMeta.text = "${article.viewCount} 阅读"
            binding.toolbar.title = "文章详情"

            if (!article.categoryName.isNullOrEmpty()) {
                binding.tvCategory.text = article.categoryName.orEmpty()
                binding.tvCategory.visibility = View.VISIBLE
            }

            binding.tvLikeCount.text = if (article.likeCount > 0) "${article.likeCount}" else "点赞"
            binding.readingProgress.visibility = View.VISIBLE

            markwon.setMarkdown(binding.tvContent, article.content.orEmpty())
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
                        Snackbar.make(binding.root, if (liked) "已点赞" else "已取消点赞", Snackbar.LENGTH_SHORT).show()
                        viewModel.loadArticle(articleId)
                    }
                } catch (e: Exception) {
                    Snackbar.make(binding.root, "操作失败", Snackbar.LENGTH_SHORT).show()
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
                        Snackbar.make(binding.root, if (isBookmarked) "已收藏" else "已取消收藏", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Snackbar.make(binding.root, "操作失败", Snackbar.LENGTH_SHORT).show()
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
                val progress = (scrollY * 100) / maxScroll
                binding.readingProgress.progress = progress.coerceIn(0, 100)
            }
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
