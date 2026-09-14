package com.sknote.app.ui.discussion

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.DiscussionCategory
import com.sknote.app.databinding.FragmentDiscussionListBinding
import com.sknote.app.ui.common.SortOptionsSheet
import com.sknote.app.util.CategoryIconResolver
import com.sknote.app.util.SkeletonAnimator
import com.sknote.app.util.hideSkeletonAndShow
import com.sknote.app.util.showSkeleton
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.sknote.app.util.AppSnackbar

class DiscussionListFragment : Fragment() {

    private var _binding: FragmentDiscussionListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiscussionListViewModel by viewModels()
    private lateinit var adapter: DiscussionAdapter
    private var currentCategory: String? = null
    private var currentArticleId: Long? = null
    /** 当前排序，取值见 DiscussionListViewModel.SORT_*；随 savedInstanceState 保留。 */
    private var currentSort: String = DiscussionListViewModel.SORT_LATEST
    private var listState: Parcelable? = null
    private var skeletonAnimator: SkeletonAnimator? = null
    private var contentShown: Boolean = false

    companion object {
        private const val STATE_CATEGORY = "state_category"
        private const val STATE_SORT = "state_sort"
        private const val STATE_LIST = "state_list"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscussionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentCategory = savedInstanceState?.getString(STATE_CATEGORY) ?: currentCategory
        // 只在有存档时才覆盖：视图被重建（例如从详情页返回、宿主重建 View）时
        // savedInstanceState 可能为 null，无条件赋值会把用户刚选的排序打回「最新」
        // —— 这是真机验证时抓到的：选「最热」→ 进详情 → 返回，按钮又变回「最新」。
        savedInstanceState?.getString(STATE_SORT)?.let { currentSort = it }
        listState = savedInstanceState?.getParcelable(STATE_LIST)
        currentArticleId = arguments?.getLong("article_id")?.takeIf { it > 0L }
        val navOptions = slideNavOptions()

        adapter = DiscussionAdapter(
            onClick = { discussion ->
                val bundle = Bundle().apply {
                    putLong(KEY_DISCUSSION_ID, discussion.id)
                    // 列表接口返回的是完整 Discussion（含正文、浏览数、评论数），
                    // 整条带过去，详情页第一帧就能渲染真实页头，不必等第二次请求。
                    putPrefillDiscussion(discussion)
                }
                findNavController().navigate(R.id.action_discussions_to_detail, bundle)
            },
            onAuthorClick = { discussion ->
                if (discussion.authorId > 0) {
                    val bundle = Bundle().apply { putLong("user_id", discussion.authorId) }
                    findNavController().navigate(R.id.publicProfileFragment, bundle, navOptions)
                }
            }
        )

        binding.rvDiscussions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DiscussionListFragment.adapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadDiscussions(currentCategory, currentArticleId, currentSort, force = true)
        }

        binding.btnSort.text = DiscussionListViewModel.sortLabel(currentSort)

        // 排序选项弹层：与参考手册页共用同一个 SortOptionsSheet（第三十四批从 PopupMenu 换过来）。
        // 原来那个 3 项菜单连「当前是哪一项」都没有标记。
        childFragmentManager.setFragmentResultListener(
            SortOptionsSheet.REQUEST_KEY, viewLifecycleOwner
        ) { _, bundle ->
            val picked = DiscussionListViewModel.SORT_OPTIONS
                .getOrNull(bundle.getInt(SortOptionsSheet.RESULT_INDEX, -1))?.first
            if (picked != null && picked != currentSort) {
                currentSort = picked
                binding.btnSort.text = DiscussionListViewModel.sortLabel(picked)
                // 换排序等于换一份列表：滚回顶部再加载，否则会停在上一次的滚动位置
                binding.rvDiscussions.scrollToPosition(0)
                viewModel.loadDiscussions(currentCategory, currentArticleId, currentSort, force = true)
            }
        }

        binding.btnSort.setOnClickListener {
            SortOptionsSheet.newInstance(
                title = "排序方式",
                labels = DiscussionListViewModel.SORT_OPTIONS.map { it.second },
                descs = listOf("按最后一条回复的时间", "按浏览量", "按回复数"),
                selectedIndex = DiscussionListViewModel.SORT_OPTIONS.indexOfFirst { it.first == currentSort }
            ).show(childFragmentManager, "sortSheet")
        }

        binding.fabNewDiscussion.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (isLoggedIn) {
                    val bundle = Bundle().apply {
                        currentArticleId?.let { putLong("article_id", it) }
                    }
                    findNavController().navigate(R.id.action_discussions_to_create, bundle)
                } else {
                    AppSnackbar.make(binding.root, "请先登录后再发帖", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { findNavController().navigate(R.id.loginFragment, null, navOptions) }
                        .show()
                }
            }
        }

        binding.rvDiscussions.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.fabNewDiscussion.shrink()
                else if (dy < 0) binding.fabNewDiscussion.extend()
            }
        })

        // 从发帖/详情页返回时刷新列表
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("refresh_discussions")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    viewModel.invalidateCache()
                    viewModel.loadDiscussions(currentCategory, currentArticleId, currentSort, force = true)
                    // 从详情页回来时未读状态可能变了（刚把那条标成已读）
                    viewModel.loadUnread()
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>("refresh_discussions")
                }
            }

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>("discussion_result_message")
            ?.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    AppSnackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>("discussion_result_message")
                }
            }

        observeData()

        val cached = viewModel.discussions.value
        if (cached != null) {
            contentShown = true
            binding.skeletonContainer.root.visibility = View.GONE
            adapter.submitList(cached)
            binding.tvDiscussionCount.text = if (cached.isNotEmpty()) "${cached.size} 条讨论" else ""
            if (cached.isEmpty()) binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            showSkeleton(binding.skeletonContainer.root, binding.rvDiscussions)
            skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
        }

        viewModel.loadCategories()
        viewModel.loadDiscussions(currentCategory, currentArticleId, currentSort)
        viewModel.loadUnread()
    }

    private fun renderCategoryChips(categories: List<DiscussionCategory>) {
        if (currentCategory != null && categories.none { it.slug == currentCategory }) {
            currentCategory = null
        }

        binding.chipGroup.removeAllViews()

        fun addChip(label: String, category: String?, iconKey: String? = null) {
            val chip = Chip(ContextThemeWrapper(requireContext(), com.google.android.material.R.style.Widget_Material3_Chip_Filter)).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                if (category == currentCategory || (category == null && currentCategory == null)) {
                    isChecked = true
                }
                val iconSpec = CategoryIconResolver.resolve(iconKey, label)
                iconSpec.drawableRes?.let {
                    setChipIconResource(it)
                    isChipIconVisible = true
                    chipIconTint = textColors
                }
                setOnClickListener {
                    for (i in 0 until binding.chipGroup.childCount) {
                        (binding.chipGroup.getChildAt(i) as? Chip)?.isChecked = false
                    }
                    isChecked = true
                    currentCategory = category
                    viewModel.loadDiscussions(category, currentArticleId, currentSort)
                }
            }
            binding.chipGroup.addView(chip)
        }

        addChip("全部", null)
        categories.forEach { category ->
            addChip(category.name, category.slug, category.icon)
        }
    }

    private fun observeData() {
        viewModel.discussions.observe(viewLifecycleOwner) { list ->
            if (!contentShown) {
                contentShown = true
                skeletonAnimator?.stop()
                hideSkeletonAndShow(binding.skeletonContainer.root, binding.rvDiscussions)
            }
            adapter.submitList(list) {
                val pendingListState = listState
                if (pendingListState != null) {
                    binding.rvDiscussions.layoutManager?.onRestoreInstanceState(pendingListState)
                    listState = null
                }
            }
            binding.layoutEmpty.visibility = if (list.isEmpty() && binding.layoutError.visibility == View.GONE) View.VISIBLE else View.GONE
            // 空结果也报「0 条讨论」：此前这里留空字符串，筛选到没有内容的分类时
            // 页头那行计数会整条消失，读起来像「没加载出来」。
            binding.tvDiscussionCount.text = "${list.size} 条讨论"
            // 列表先到、未读后到也没关系：适配器按 id 精确刷新受影响的那几行
            adapter.setUnread(viewModel.unread.value.orEmpty())
        }
        viewModel.unread.observe(viewLifecycleOwner) { adapter.setUnread(it.orEmpty()) }
        viewModel.categories.observe(viewLifecycleOwner) { renderCategoryChips(it) }
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
                showSkeleton(binding.skeletonContainer.root, binding.rvDiscussions)
                skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
            }
            viewModel.loadDiscussions(currentCategory, currentArticleId, currentSort)
        }
    }

    private fun captureUiState() {
        val currentBinding = _binding ?: return
        listState = currentBinding.rvDiscussions.layoutManager?.onSaveInstanceState()
    }

    override fun onPause() {
        captureUiState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureUiState()
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CATEGORY, currentCategory)
        outState.putString(STATE_SORT, currentSort)
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
