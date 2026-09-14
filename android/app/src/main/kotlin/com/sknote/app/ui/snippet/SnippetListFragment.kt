package com.sknote.app.ui.snippet

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.sknote.app.ui.reference.LearningSearchViewModel
import com.sknote.app.util.NavPrefill
import com.sknote.app.util.SkeletonAnimator
import com.sknote.app.util.hideSkeletonAndShow
import com.sknote.app.util.putPrefill
import com.sknote.app.util.showSkeleton
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.sknote.app.util.AppSnackbar
import com.sknote.app.util.hideOnScroll

class SnippetListFragment : Fragment() {

    private var _binding: FragmentSnippetListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SnippetListViewModel by viewModels()

    /**
     * 搜索词来自容器（作用域挂在 [com.sknote.app.ui.reference.ReferenceContainerFragment] 上），
     * 因为输入框现在在容器的标题行里，而列表在这里。第三十三批把行内搜索框收进了标题行。
     */
    private val searchViewModel: LearningSearchViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: SnippetAdapter
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
        _binding = FragmentSnippetListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedInstanceState?.let {
            currentCategory = it.getString(STATE_CATEGORY)
            listState = it.getParcelable(STATE_LIST)
            currentQuery = it.getString(STATE_QUERY).orEmpty()
        }
        // 进程被杀后重建时把关键词回灌给容器，标题行的输入框才不会是空的（配置变更时那边本来就有）
        if (currentQuery.isNotEmpty()) searchViewModel.setQuery(currentQuery)

        val navController = parentFragment?.view?.let { Navigation.findNavController(it) }
            ?: findNavController()
        val navOptions = slideNavOptions()

        adapter = SnippetAdapter { snippet ->
            val bundle = Bundle().apply {
                putLong("snippet_id", snippet.id)
                // 片段列表接口已带上 code，整条带过去，详情页第一帧就能渲染真实代码。
                putPrefill(NavPrefill.KEY_SNIPPET, snippet)
            }
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

        // 向下滚动时收起分类 chip 行，向上滚回时展开（与参考手册 tab 同一套行为）
        binding.rvSnippets.hideOnScroll(binding.layoutFilterRowSpace, binding.layoutFilterRow)

        binding.fabAddSnippet.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (isLoggedIn) {
                    navController.navigate(R.id.createSnippetFragment, null, navOptions)
                } else {
                    AppSnackbar.make(binding.root, "请先登录后再分享代码", Snackbar.LENGTH_SHORT)
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

        // 搜索框已经搬到容器的标题行，这里只消费共享的查询词。
        // 值与 currentQuery 相同时不动：首次组合、旋转重建、回灌都走这条静默分支，不会多打请求。
        searchViewModel.query.observe(viewLifecycleOwner) { query ->
            val normalized = query.orEmpty()
            if (normalized != currentQuery) {
                currentQuery = normalized
                viewModel.loadSnippets(currentCategory, normalized.ifEmpty { null }, force = true)
            }
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
                    AppSnackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("snippet_result_message")
                }
            }

        observeData()

        val cached = viewModel.snippets.value
        if (cached != null) {
            contentShown = true
            binding.skeletonContainer.root.visibility = View.GONE
            adapter.submitList(cached)
            binding.tvSnippetCount.text = if (cached.isNotEmpty()) "${cached.size} 个片段" else ""
            if (cached.isEmpty()) binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            showSkeleton(binding.skeletonContainer.root, binding.rvSnippets)
            skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
        }

        viewModel.loadSnippets(currentCategory, currentQuery.ifEmpty { null })
        viewModel.loadCategories()
    }

    private fun currentSearchQuery(): String = currentQuery

    private fun observeData() {
        viewModel.snippets.observe(viewLifecycleOwner) { list ->
            if (!contentShown) {
                contentShown = true
                skeletonAnimator?.stop()
                hideSkeletonAndShow(binding.skeletonContainer.root, binding.rvSnippets)
            }
            adapter.submitList(list) {
                val pendingListState = listState
                if (pendingListState != null) {
                    binding.rvSnippets.layoutManager?.onRestoreInstanceState(pendingListState)
                    listState = null
                }
            }
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
                    isChecked = currentCategory == cat.category
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
                showSkeleton(binding.skeletonContainer.root, binding.rvSnippets)
                skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
            }
            viewModel.loadSnippets(currentCategory, currentSearchQuery().ifEmpty { null }, force = true)
        }
    }

    private fun captureUiState() {
        val currentBinding = _binding ?: return
        // currentQuery 现在由容器的输入框驱动（经 LearningSearchViewModel 下发），这里不再读 EditText
        listState = currentBinding.rvSnippets.layoutManager?.onSaveInstanceState()
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
