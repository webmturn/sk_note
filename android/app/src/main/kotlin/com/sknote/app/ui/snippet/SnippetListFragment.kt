package com.sknote.app.ui.snippet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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

        adapter = SnippetAdapter { snippet ->
            val bundle = Bundle().apply { putLong("snippet_id", snippet.id) }
            navController.navigate(R.id.snippetDetailFragment, bundle)
        }

        binding.rvSnippets.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SnippetListFragment.adapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadSnippets(currentCategory, force = true)
            viewModel.loadCategories()
        }

        binding.fabAddSnippet.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (isLoggedIn) {
                    navController.navigate(R.id.createSnippetFragment)
                } else {
                    Snackbar.make(binding.root, "请先登录后再分享代码", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { navController.navigate(R.id.loginFragment) }
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

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString().trim()
                viewModel.loadSnippets(currentCategory, if (query.isNotEmpty()) query else null, force = true)
                true
            } else false
        }

        navController.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("refresh_snippets")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    viewModel.invalidateCache()
                    viewModel.loadSnippets(currentCategory, force = true)
                    viewModel.loadCategories()
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("refresh_snippets")
                }
            }

        observeData()
        viewModel.loadSnippets()
        viewModel.loadCategories()
    }

    private fun observeData() {
        viewModel.snippets.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.layoutEmpty.visibility = if (list.isEmpty() && binding.layoutError.visibility == View.GONE) View.VISIBLE else View.GONE
            binding.tvSnippetCount.text = if (list.isNotEmpty()) "${list.size} 个片段" else ""
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
                        currentCategory = cat.category
                        viewModel.loadSnippets(cat.category, force = true)
                    }
                }
                chipGroup.addView(chip)
            }
            binding.chipAll.setOnClickListener {
                currentCategory = null
                viewModel.loadSnippets(null, force = true)
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
            viewModel.loadSnippets(currentCategory, force = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
