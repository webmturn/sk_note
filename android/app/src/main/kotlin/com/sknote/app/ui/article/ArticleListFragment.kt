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
import com.sknote.app.ui.home.ArticleAdapter

class ArticleListFragment : Fragment() {

    private var _binding: FragmentArticleListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ArticleListViewModel by viewModels()
    private lateinit var adapter: ArticleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArticleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoryId = arguments?.getLong("category_id", 0L) ?: 0L
        if (categoryId <= 0L) return
        val categoryName = arguments?.getString("category_name") ?: "文章列表"

        binding.toolbar.title = categoryName
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ArticleAdapter { article ->
            val bundle = Bundle().apply { putLong("article_id", article.id) }
            findNavController().navigate(R.id.articleDetailFragment, bundle)
        }
        binding.rvArticles.layoutManager = LinearLayoutManager(context)
        binding.rvArticles.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadArticles(categoryId)
        }

        observeData()
        viewModel.loadArticles(categoryId)
    }

    private fun observeData() {
        viewModel.articles.observe(viewLifecycleOwner) { articles ->
            adapter.submitList(articles)
            binding.tvEmpty.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.layoutError.visibility = View.VISIBLE
                binding.tvError.text = error
                binding.tvEmpty.visibility = View.GONE
            } else {
                binding.layoutError.visibility = View.GONE
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            val categoryId = arguments?.getLong("category_id", 0L) ?: 0L
            if (categoryId <= 0L) return@setOnClickListener
            viewModel.loadArticles(categoryId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
