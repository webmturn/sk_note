package com.sknote.app.ui.share

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
import com.sknote.app.databinding.FragmentShareListBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShareListFragment : Fragment() {

    private var _binding: FragmentShareListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShareListViewModel by viewModels()
    private lateinit var adapter: ShareAdapter
    private var currentCategory: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShareListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = parentFragment?.view?.let { Navigation.findNavController(it) }
            ?: findNavController()

        binding.toolbar.setNavigationOnClickListener { navController.navigateUp() }

        adapter = ShareAdapter { share ->
            val bundle = Bundle().apply { putLong("share_id", share.id) }
            navController.navigate(R.id.action_shares_to_detail, bundle)
        }

        binding.rvShares.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ShareListFragment.adapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadShares(currentCategory, force = true)
            viewModel.loadCategories()
        }

        binding.fabAddShare.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (isLoggedIn) {
                    navController.navigate(R.id.action_shares_to_create)
                } else {
                    Snackbar.make(binding.root, "请先登录后再分享文件", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { navController.navigate(R.id.loginFragment) }
                        .show()
                }
            }
        }

        binding.rvShares.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.fabAddShare.shrink()
                else if (dy < 0) binding.fabAddShare.extend()
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString().trim()
                viewModel.loadShares(currentCategory, if (query.isNotEmpty()) query else null, force = true)
                true
            } else false
        }

        navController.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("refresh_shares")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    viewModel.invalidateCache()
                    viewModel.loadShares(currentCategory, force = true)
                    viewModel.loadCategories()
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("refresh_shares")
                }
            }

        observeData()
        viewModel.loadShares()
        viewModel.loadCategories()
    }

    private fun observeData() {
        viewModel.shares.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.layoutEmpty.visibility = if (list.isEmpty() && binding.layoutError.visibility == View.GONE) View.VISIBLE else View.GONE
            binding.tvShareCount.text = if (list.isNotEmpty()) "${list.size} 个分享" else ""
        }

        viewModel.categories.observe(viewLifecycleOwner) { cats ->
            val chipGroup = binding.chipGroup
            while (chipGroup.childCount > 1) {
                chipGroup.removeViewAt(chipGroup.childCount - 1)
            }
            cats.forEach { cat ->
                val chip = Chip(requireContext()).apply {
                    text = ShareCategories.getLabel(cat.category) + " (${cat.count})"
                    isCheckable = true
                    setOnClickListener {
                        currentCategory = cat.category
                        viewModel.loadShares(cat.category, force = true)
                    }
                }
                chipGroup.addView(chip)
            }
            binding.chipAll.setOnClickListener {
                currentCategory = null
                viewModel.loadShares(null, force = true)
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
            viewModel.loadShares(currentCategory, force = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
