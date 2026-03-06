package com.sknote.app.ui.article

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentArticleDetailBinding
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
    private var isBookmarked = false

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

        val articleId = arguments?.getLong("article_id", 0L) ?: 0L
        if (articleId <= 0L) return

        setupScrollProgress()
        viewModel.loadArticle(articleId)

        viewModel.article.observe(viewLifecycleOwner) { article ->
            currentArticleTitle = article.title
            currentArticleSummary = article.summary
            currentArticleContent = article.content

            binding.tvTitle.text = article.title
            binding.tvAuthor.text = article.authorName ?: "Unknown"
            binding.tvTime.text = TimeUtil.formatRelative(article.createdAt)
            binding.tvMeta.text = "${article.viewCount} 阅读"
            binding.toolbar.title = "文章详情"

            if (!article.categoryName.isNullOrEmpty()) {
                binding.tvCategory.text = article.categoryName
                binding.tvCategory.visibility = View.VISIBLE
            }

            binding.tvLikeCount.text = if (article.likeCount > 0) "${article.likeCount}" else "点赞"
            binding.readingProgress.visibility = View.VISIBLE

            markwon.setMarkdown(binding.tvContent, article.content)
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
            viewModel.loadArticle(articleId)
        }

        binding.btnLike.setOnClickListener {
            viewModel.likeArticle(articleId)
            Snackbar.make(binding.root, "已点赞", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            if (currentArticleTitle.isNotEmpty()) {
                val shareText = "$currentArticleTitle\n\n${currentArticleSummary.ifEmpty { currentArticleContent.take(200) }}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, currentArticleTitle)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                startActivity(Intent.createChooser(intent, "分享文章"))
            }
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
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (!isLoggedIn) {
                    Snackbar.make(binding.root, "请先登录", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { findNavController().navigate(R.id.loginFragment) }
                        .show()
                    return@launch
                }
                try {
                    val response = ApiClient.getService().toggleBookmark(articleId)
                    if (response.isSuccessful) {
                        isBookmarked = response.body()?.bookmarked ?: false
                        updateBookmarkUI()
                        Snackbar.make(binding.root, if (isBookmarked) "已收藏" else "已取消收藏", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Snackbar.make(binding.root, "操作失败", Snackbar.LENGTH_SHORT).show()
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
        _binding = null
    }
}
