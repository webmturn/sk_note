package com.sknote.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.util.NavPrefill
import com.sknote.app.util.putPrefill
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentHomeBinding
import com.sknote.app.util.SkeletonAnimator
import com.sknote.app.util.hideSkeletonAndShow
import com.sknote.app.util.showSkeleton
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private fun isFragmentUsable(): Boolean {
        return _binding != null && isAdded && context != null
    }

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var latestShareAdapter: LatestShareAdapter

    private var skeletonAnimator: SkeletonAnimator? = null

    /**
     * 首屏是否已经出过内容。用于区分「首次加载」（走骨架屏）与「下拉刷新」
     * （保留已有内容，只用转圈提示）。其余 8 个列表/详情页也是这个约定。
     */
    private var contentShown = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        observeData()

        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle
        navHandle?.getLiveData<Boolean>("refresh_shares")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    viewModel.loadData(force = true)
                    navHandle.remove<Boolean>("refresh_shares")
                }
            }

        navHandle?.getLiveData<String>("share_result_message")
            ?.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>("share_result_message")
                }
            }

        val navOptions = slideNavOptions()

        viewModel.loadData()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData(force = true)
            updateGreeting()
        }

        updateGreeting()

        binding.layoutSearch.setOnClickListener {
            findNavController().navigate(R.id.searchFragment, null, navOptions)
        }

        binding.tvViewAll.setOnClickListener {
            val bundle = Bundle().apply {
                putLong("category_id", 0L)
                putString("category_name", "全部文章")
            }
            findNavController().navigate(R.id.action_home_to_articleList, bundle)
        }

        binding.cardAnalyzer.setOnClickListener {
            findNavController().navigate(R.id.swToolsFragment, null, navOptions)
        }

        binding.cardShares.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_shareList)
        }

        binding.tvViewAllShares.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_shareList)
        }

        binding.cardRandomLearn.setOnClickListener {
            com.sknote.app.ui.reference.ReferenceData.init(requireContext())
            val allItems = com.sknote.app.ui.reference.ReferenceData.getAllItems()
            if (allItems.isNotEmpty()) {
                val random = allItems.random()
                val bundle = Bundle().apply { putLong("reference_id", random.id) }
                findNavController().navigate(R.id.referenceDetailFragment, bundle, navOptions)
            }
        }
    }

    private fun setupRecyclerViews() {
        categoryAdapter = CategoryAdapter { category ->
            val bundle = Bundle().apply {
                putLong("category_id", category.id)
                putString("category_name", category.name)
            }
            findNavController().navigate(R.id.action_home_to_articleList, bundle)
        }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        articleAdapter = ArticleAdapter { article ->
            val bundle = Bundle().apply {
                putLong("article_id", article.id)
                // 首页接口（`aggregated.ts`）已带上 `a.content`，可整条预填。
                putPrefill(NavPrefill.KEY_ARTICLE, article)
            }
            findNavController().navigate(R.id.action_home_to_articleDetail, bundle)
        }
        binding.rvArticles.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = articleAdapter
        }

        latestShareAdapter = LatestShareAdapter { share ->
            val bundle = Bundle().apply {
                putLong("share_id", share.id)
                // 首页接口的分享用 `SELECT *`（`aggregated.ts`），字段完整，可整页预填。
                putPrefill(NavPrefill.KEY_SHARE, share)
            }
            findNavController().navigate(R.id.action_home_to_shareDetail, bundle)
        }
        binding.rvLatestShares.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = latestShareAdapter
        }
    }

    private fun observeData() {
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            categoryAdapter.submitList(categories)
            binding.layoutCategorySection.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
            if (categories.isNotEmpty()) contentShown = true
        }

        viewModel.articles.observe(viewLifecycleOwner) { articles ->
            articleAdapter.submitList(articles)
            binding.tvEmpty.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
            if (articles.isNotEmpty()) contentShown = true
        }

        viewModel.latestShares.observe(viewLifecycleOwner) { shares ->
            latestShareAdapter.submitList(shares)
            binding.layoutShareSection.visibility = if (shares.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        // 原实现只有 swipeRefresh.isRefreshing 一个加载提示：首页没有骨架屏，
        // 首屏加载时用户看到的是「分类」「最新文章」两个标题下面一片空白。
        // 现首次加载走骨架屏（与其余 8 个页面同一套 SkeletonAnimator + 同一套骨架卡片），
        // 下拉刷新仍保留已有内容、只用转圈提示。
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading && contentShown
            if (isLoading) {
                if (!contentShown) {
                    showSkeleton(binding.skeletonHome.root, binding.layoutSections)
                    skeletonAnimator?.stop()
                    skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonHome.root)
                }
            } else {
                skeletonAnimator?.stop()
                skeletonAnimator = null
                hideSkeletonAndShow(binding.skeletonHome.root, binding.layoutSections)
            }
        }

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
            viewModel.loadData()
        }
    }

    private fun updateGreeting() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (isLoggedIn) {
                val nickname = ApiClient.getTokenManager().getNickname().first() ?: ""
                val username = ApiClient.getTokenManager().getUsername().first() ?: ""
                if (!isFragmentUsable()) return@launch
                val displayName = nickname.ifEmpty { username }
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val greeting = when {
                    hour < 6 -> "夜深了"
                    hour < 12 -> "早上好"
                    hour < 18 -> "下午好"
                    else -> "晚上好"
                }
                binding.tvGreeting.text = "$greeting, $displayName"
                binding.tvSubtitle.text = "继续探索 Sketchware Pro"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
    }

    override fun onDestroyView() {
        skeletonAnimator?.stop()
        skeletonAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
