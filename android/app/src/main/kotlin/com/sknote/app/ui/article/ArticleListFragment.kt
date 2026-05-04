package com.sknote.app.ui.article

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentArticleListBinding
import com.sknote.app.util.SkeletonAnimator
import com.sknote.app.util.fadeIn
import com.sknote.app.util.fadeOut
import com.sknote.app.util.hideSkeletonAndShow
import com.sknote.app.util.showSkeleton
import com.sknote.app.util.slideNavOptions
import com.sknote.app.ui.home.ArticleAdapter

class ArticleListFragment : Fragment() {

    private var _binding: FragmentArticleListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ArticleListViewModel by viewModels()
    private lateinit var adapter: ArticleAdapter
    private var currentCategoryId: Long? = null
    private var skeletonAnimator: SkeletonAnimator? = null
    private var contentShown: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArticleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoryIdArg = arguments?.getLong("category_id", 0L) ?: 0L
        currentCategoryId = if (categoryIdArg > 0L) categoryIdArg else null
        val categoryName = arguments?.getString("category_name") ?: "文章列表"

        binding.toolbar.title = categoryName
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ArticleAdapter { article ->
            val bundle = Bundle().apply { putLong("article_id", article.id) }
            findNavController().navigate(R.id.articleDetailFragment, bundle, slideNavOptions())
        }
        binding.rvArticles.layoutManager = LinearLayoutManager(context)
        binding.rvArticles.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadArticles(currentCategoryId)
        }

        observeData()

        val cached = viewModel.articles.value
        if (cached != null) {
            contentShown = true
            binding.skeletonContainer.root.visibility = View.GONE
            adapter.submitList(cached)
            if (cached.isEmpty()) binding.layoutEmpty.fadeIn() else binding.layoutEmpty.fadeOut()
        } else {
            showSkeleton(binding.skeletonContainer.root, binding.rvArticles)
            skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
        }

        viewModel.loadArticles(currentCategoryId)
    }

    private fun observeData() {
        viewModel.articles.observe(viewLifecycleOwner) { articles ->
            if (!contentShown) {
                contentShown = true
                skeletonAnimator?.stop()
                hideSkeletonAndShow(binding.skeletonContainer.root, binding.rvArticles)
            }
            adapter.submitList(articles)
            if (articles.isEmpty()) binding.layoutEmpty.fadeIn() else binding.layoutEmpty.fadeOut()
        }
        viewModel.isLoading.observe(viewLifecycleOwner) {
            binding.swipeRefresh.isRefreshing = it && contentShown
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                skeletonAnimator?.stop()
                binding.skeletonContainer.root.visibility = View.GONE
                binding.tvError.text = error
                binding.layoutError.fadeIn()
                binding.layoutEmpty.fadeOut()
            } else {
                binding.layoutError.fadeOut()
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            if (!contentShown) {
                showSkeleton(binding.skeletonContainer.root, binding.rvArticles)
                skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
            }
            viewModel.loadArticles(currentCategoryId)
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
