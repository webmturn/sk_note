package com.sknote.app.ui.search

import android.os.Bundle
import android.os.Parcelable
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
import com.sknote.app.util.slideNavOptions

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var articleAdapter: ArticleAdapter
    private var currentQuery: String = ""
    private var resultListState: Parcelable? = null

    companion object {
        private const val STATE_QUERY = "state_query"
        private const val STATE_LIST = "state_list"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedInstanceState?.let {
            resultListState = it.getParcelable(STATE_LIST)
            currentQuery = it.getString(STATE_QUERY).orEmpty()
        }

        val navOptions = slideNavOptions()

        articleAdapter = ArticleAdapter { article ->
            val bundle = Bundle().apply { putLong("article_id", article.id) }
            findNavController().navigate(R.id.articleDetailFragment, bundle, navOptions)
        }
        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = articleAdapter
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnSearch.setOnClickListener { performSearch() }

        if (currentQuery.isNotEmpty()) {
            binding.etSearch.setText(currentQuery)
            binding.etSearch.setSelection(currentQuery.length)
        } else if (savedInstanceState == null) {
            binding.etSearch.post {
                binding.etSearch.requestFocus()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSearchActionState(s?.toString()?.trim().orEmpty())
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
            updateSearchActionState("")
        }

        updateSearchActionState(currentQuery)
        observeData()
        if (currentQuery.isEmpty()) {
            viewModel.loadAll()
        } else {
            viewModel.search(currentQuery)
        }
    }

    private fun performSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isEmpty()) return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        viewModel.search(query)
    }

    private fun updateSearchActionState(query: String) {
        binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        binding.btnSearch.isEnabled = query.isNotEmpty()
        binding.btnSearch.alpha = if (query.isNotEmpty()) 1f else 0.4f
    }

    private fun observeData() {
        viewModel.results.observe(viewLifecycleOwner) { articles ->
            articleAdapter.submitList(articles) {
                val listState = resultListState
                if (listState != null) {
                    binding.rvResults.layoutManager?.onRestoreInstanceState(listState)
                    resultListState = null
                }
            }
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

    private fun captureUiState() {
        val currentBinding = _binding ?: return
        currentQuery = currentBinding.etSearch.text?.toString().orEmpty()
        resultListState = currentBinding.rvResults.layoutManager?.onSaveInstanceState()
    }

    override fun onPause() {
        captureUiState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureUiState()
        super.onSaveInstanceState(outState)
        outState.putString(STATE_QUERY, currentQuery)
        outState.putParcelable(STATE_LIST, resultListState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
