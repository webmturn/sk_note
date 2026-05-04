package com.sknote.app.ui.share

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
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
import com.sknote.app.databinding.FragmentShareListBinding
import com.sknote.app.util.SkeletonAnimator
import com.sknote.app.util.hideSkeletonAndShow
import com.sknote.app.util.showSkeleton
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShareListFragment : Fragment() {

    private var _binding: FragmentShareListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShareListViewModel by viewModels()
    private lateinit var adapter: ShareAdapter
    private var currentCategory: String? = null
    private var currentQuery: String = ""
    private var listState: Parcelable? = null
    private var skeletonAnimator: SkeletonAnimator? = null
    private var contentShown: Boolean = false

    companion object {
        private const val STATE_CATEGORY = "state_category"
        private const val STATE_QUERY = "state_query"
        private const val STATE_LIST = "state_list"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShareListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedInstanceState?.let {
            currentCategory = it.getString(STATE_CATEGORY)
            listState = it.getParcelable(STATE_LIST)
            currentQuery = it.getString(STATE_QUERY).orEmpty()
        }

        val navController = parentFragment?.view?.let { Navigation.findNavController(it) }
            ?: findNavController()
        val navOptions = slideNavOptions()

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
            viewModel.loadShares(currentCategory, currentSearchQuery().ifEmpty { null }, force = true)
            viewModel.loadCategories()
        }

        binding.fabAddShare.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (isLoggedIn) {
                    navController.navigate(R.id.action_shares_to_create)
                } else {
                    Snackbar.make(binding.root, "请先登录后再分享文件", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { navController.navigate(R.id.loginFragment, null, navOptions) }
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

        if (currentQuery.isNotEmpty()) {
            binding.etSearch.setText(currentQuery)
            binding.etSearch.setSelection(currentQuery.length)
        }

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
            viewModel.loadShares(currentCategory, force = true)
            updateSearchActionState("")
        }

        navController.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("refresh_shares")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    viewModel.invalidateCache()
                    viewModel.loadShares(currentCategory, currentSearchQuery().ifEmpty { null }, force = true)
                    viewModel.loadCategories()
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("refresh_shares")
                }
            }

        navController.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>("share_result_message")
            ?.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("share_result_message")
                }
            }

        updateSearchActionState(currentQuery)
        observeData()

        val cached = viewModel.shares.value
        if (cached != null) {
            contentShown = true
            binding.skeletonContainer.root.visibility = View.GONE
            adapter.submitList(cached)
            binding.tvShareCount.text = if (cached.isNotEmpty()) "${cached.size} 个分享" else ""
            if (cached.isEmpty()) binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            showSkeleton(binding.skeletonContainer.root, binding.rvShares)
            skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
        }

        viewModel.loadShares(currentCategory, currentQuery.ifEmpty { null })
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
        viewModel.loadShares(currentCategory, query, force = true)
    }

    private fun updateSearchActionState(query: String) {
        binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        binding.btnSearch.isEnabled = query.isNotEmpty()
        binding.btnSearch.alpha = if (query.isNotEmpty()) 1f else 0.4f
    }

    private fun observeData() {
        viewModel.shares.observe(viewLifecycleOwner) { list ->
            if (!contentShown) {
                contentShown = true
                skeletonAnimator?.stop()
                hideSkeletonAndShow(binding.skeletonContainer.root, binding.rvShares)
            }
            adapter.submitList(list) {
                val pendingListState = listState
                if (pendingListState != null) {
                    binding.rvShares.layoutManager?.onRestoreInstanceState(pendingListState)
                    listState = null
                }
            }
            val query = currentSearchQuery()
            binding.layoutEmpty.visibility = if (list.isEmpty() && binding.layoutError.visibility == View.GONE) View.VISIBLE else View.GONE
            binding.tvEmpty.text = if (query.isEmpty()) "暂无文件分享" else "未找到「$query」相关分享"
            binding.tvShareCount.text = if (list.isNotEmpty()) {
                if (query.isEmpty()) "${list.size} 个分享" else "找到 ${list.size} 个结果"
            } else {
                ""
            }
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
                    isChecked = currentCategory == cat.category
                    setOnClickListener {
                        binding.chipAll.isChecked = false
                        for (i in 0 until chipGroup.childCount) {
                            (chipGroup.getChildAt(i) as? Chip)?.isChecked = false
                        }
                        isChecked = true
                        currentCategory = cat.category
                        viewModel.loadShares(cat.category, currentSearchQuery().ifEmpty { null }, force = true)
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
                viewModel.loadShares(null, currentSearchQuery().ifEmpty { null }, force = true)
            }
            binding.chipAll.isChecked = currentCategory == null
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {
            binding.swipeRefresh.isRefreshing = it && contentShown
        }
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
                showSkeleton(binding.skeletonContainer.root, binding.rvShares)
                skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
            }
            viewModel.loadShares(currentCategory, currentSearchQuery().ifEmpty { null }, force = true)
        }
    }

    private fun captureUiState() {
        val currentBinding = _binding ?: return
        currentQuery = currentBinding.etSearch.text?.toString().orEmpty()
        listState = currentBinding.rvShares.layoutManager?.onSaveInstanceState()
    }

    override fun onPause() {
        captureUiState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureUiState()
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CATEGORY, currentCategory)
        outState.putString(STATE_QUERY, currentQuery)
        outState.putParcelable(STATE_LIST, listState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        skeletonAnimator?.stop()
        skeletonAnimator = null
        contentShown = false
        _binding = null
    }
}
