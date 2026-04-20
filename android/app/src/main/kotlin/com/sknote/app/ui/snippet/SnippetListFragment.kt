package com.sknote.app.ui.snippet

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentSnippetListBinding
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SnippetListFragment : Fragment() {

    private var _binding: FragmentSnippetListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SnippetListViewModel by viewModels()
    private lateinit var adapter: SnippetAdapter
    private var currentCategory: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSnippetListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = parentFragment?.view?.let { Navigation.findNavController(it) }
            ?: findNavController()
        val navOptions = slideNavOptions()

        adapter = SnippetAdapter { snippet ->
            val bundle = Bundle().apply { putLong("snippet_id", snippet.id) }
            navController.navigate(R.id.snippetDetailFragment, bundle, navOptions)
        }

        binding.rvSnippets.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SnippetListFragment.adapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadSnippets(currentCategory, currentSearchQuery().ifEmpty { null }, force = true)
            viewModel.loadCategories()
        }

        binding.fabAddSnippet.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (isLoggedIn) {
                    navController.navigate(R.id.createSnippetFragment, null, navOptions)
                } else {
                    Snackbar.make(binding.root, "请先登录后再分享代码", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { navController.navigate(R.id.loginFragment, null, navOptions) }
                        .show()
                }
            }
        }

        binding.rvSnippets.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.fabAddSnippet.shrink()
                else if (dy < 0) binding.fabAddSnippet.extend()
            }
        })

        binding.btnSearch.setOnClickListener { performSearch() }

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
            viewModel.loadSnippets(currentCategory, force = true)
            updateSearchActionState("")
        }

        navController.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("refresh_snippets")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    viewModel.invalidateCache()
                    viewModel.loadSnippets(currentCategory, currentSearchQuery().ifEmpty { null }, force = true)
                    viewModel.loadCategories()
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("refresh_snippets")
                }
            }

        navController.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>("snippet_result_message")
            ?.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("snippet_result_message")
                }
            }

        updateSearchActionState(currentSearchQuery())
        observeData()
        viewModel.loadSnippets()
        viewModel.loadCategories()
    }

    private fun currentSearchQuery(): String {
        return binding.etSearch.text?.toString()?.trim().orEmpty()
    }

    private fun performSearch() {
        val query = currentSearchQuery()
        if (query.isEmpty()) return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        viewModel.loadSnippets(currentCategory, query, force = true)
    }

    private fun updateSearchActionState(query: String) {
        binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        binding.btnSearch.isEnabled = query.isNotEmpty()
        binding.btnSearch.alpha = if (query.isNotEmpty()) 1f else 0.4f
    }

    private fun observeData() {
        viewModel.snippets.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            val query = currentSearchQuery()
            binding.layoutEmpty.visibility = if (list.isEmpty() && binding.layoutError.visibility == View.GONE) View.VISIBLE else View.GONE
            binding.tvEmpty.text = if (query.isEmpty()) "暂无代码片段" else "未找到「$query」相关代码片段"
            binding.tvSnippetCount.text = if (list.isNotEmpty()) {
                if (query.isEmpty()) "${list.size} 个片段" else "找到 ${list.size} 个结果"
            } else {
                ""
            }
        }

        viewModel.categories.observe(viewLifecycleOwner) { cats ->
            val chipGroup = binding.chipGroup
            // Keep "全部" chip, remove dynamic ones
            while (chipGroup.childCount > 1) {
                chipGroup.removeViewAt(chipGroup.childCount - 1)
            }
            cats.forEach { cat ->
                val chip = Chip(requireContext()).apply {
                    text = SnippetCategories.getLabel(cat.category) + " (${cat.count})"
                    isCheckable = true
                    setOnClickListener {
                        binding.chipAll.isChecked = false
                        for (i in 0 until chipGroup.childCount) {
                            (chipGroup.getChildAt(i) as? Chip)?.isChecked = false
                        }
                        isChecked = true
                        currentCategory = cat.category
                        viewModel.loadSnippets(cat.category, currentSearchQuery().ifEmpty { null }, force = true)
                    }
                }
                chipGroup.addView(chip)
            }
            binding.chipAll.setOnClickListener {
                for (i in 0 until chipGroup.childCount) {
                    (chipGroup.getChildAt(i) as? Chip)?.isChecked = false
                }
                binding.chipAll.isChecked = true
                currentCategory = null
                viewModel.loadSnippets(null, currentSearchQuery().ifEmpty { null }, force = true)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }
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
            viewModel.loadSnippets(currentCategory, currentSearchQuery().ifEmpty { null }, force = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
