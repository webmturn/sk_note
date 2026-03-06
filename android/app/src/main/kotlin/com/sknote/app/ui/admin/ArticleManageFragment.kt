package com.sknote.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.model.Article
import com.sknote.app.databinding.FragmentArticleManageBinding

class ArticleManageFragment : Fragment() {

    private var _binding: FragmentArticleManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ArticleManageViewModel by viewModels()
    private lateinit var adapter: ArticleManageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArticleManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ArticleManageAdapter(
            onEdit = { article ->
                val bundle = Bundle().apply { putLong("article_id", article.id) }
                findNavController().navigate(R.id.articleEditorFragment, bundle)
            },
            onDelete = { article -> showDeleteConfirm(article) }
        )
        binding.rvArticles.layoutManager = LinearLayoutManager(context)
        binding.rvArticles.adapter = adapter

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.articleEditorFragment)
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadArticles(force = true)
        }

        observeData()
        viewModel.loadArticles()
    }

    private fun observeData() {
        viewModel.articles.observe(viewLifecycleOwner) { articles ->
            adapter.submitList(articles)
            binding.tvEmpty.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun showDeleteConfirm(article: Article) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除文章")
            .setMessage("确定删除文章「${article.title}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteArticle(article.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
