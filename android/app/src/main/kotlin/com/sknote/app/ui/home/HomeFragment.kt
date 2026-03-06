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
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var articleAdapter: ArticleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        observeData()
        viewModel.loadData()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData(force = true)
            updateGreeting()
        }

        updateGreeting()

        binding.layoutSearch.setOnClickListener {
            findNavController().navigate(R.id.searchFragment)
        }

        binding.tvViewAll.setOnClickListener {
            findNavController().navigate(R.id.searchFragment)
        }

        binding.cardAnalyzer.setOnClickListener {
            findNavController().navigate(R.id.swToolsFragment)
        }

        binding.cardRandomLearn.setOnClickListener {
            com.sknote.app.ui.reference.ReferenceData.init(requireContext())
            val allItems = com.sknote.app.ui.reference.ReferenceData.getAllItems()
            if (allItems.isNotEmpty()) {
                val random = allItems.random()
                val bundle = Bundle().apply { putLong("reference_id", random.id) }
                findNavController().navigate(R.id.referenceDetailFragment, bundle)
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
            val bundle = Bundle().apply { putLong("article_id", article.id) }
            findNavController().navigate(R.id.action_home_to_articleDetail, bundle)
        }
        binding.rvArticles.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = articleAdapter
        }
    }

    private fun observeData() {
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            categoryAdapter.submitList(categories)
            binding.layoutCategorySection.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.articles.observe(viewLifecycleOwner) { articles ->
            articleAdapter.submitList(articles)
            binding.tvEmpty.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
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
                val username = ApiClient.getTokenManager().getUsername().first() ?: ""
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val greeting = when {
                    hour < 6 -> "夜深了"
                    hour < 12 -> "早上好"
                    hour < 18 -> "下午好"
                    else -> "晚上好"
                }
                binding.tvGreeting.text = "$greeting, $username"
                binding.tvSubtitle.text = "继续探索 Sketchware Pro"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
