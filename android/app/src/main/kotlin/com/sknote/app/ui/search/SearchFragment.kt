package com.sknote.app.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentSearchBinding
import com.sknote.app.ui.home.ArticleAdapter

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var articleAdapter: ArticleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        articleAdapter = ArticleAdapter { article ->
            val bundle = Bundle().apply { putLong("article_id", article.id) }
            findNavController().navigate(R.id.articleDetailFragment, bundle)
        }
        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = articleAdapter
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.etSearch.post {
            binding.etSearch.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }

        viewModel.loadAll()

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.loadAll()
        }

        observeData()
    }

    private fun performSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isEmpty()) return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        viewModel.search(query)
    }

    private fun observeData() {
        viewModel.results.observe(viewLifecycleOwner) { articles ->
            articleAdapter.submitList(articles)
            val query = binding.etSearch.text.toString().trim()
            binding.tvEmpty.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
            binding.tvEmpty.text = if (query.isEmpty()) "暂无文章" else "未找到「$query」相关文章"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

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
            val query = binding.etSearch.text.toString().trim()
            if (query.isEmpty()) viewModel.loadAll() else viewModel.search(query)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
