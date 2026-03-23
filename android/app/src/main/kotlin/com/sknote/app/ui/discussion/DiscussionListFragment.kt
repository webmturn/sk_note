package com.sknote.app.ui.discussion

import android.os.Bundle
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
import com.sknote.app.util.CategoryIconResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DiscussionListFragment : Fragment() {

    private var _binding: FragmentDiscussionListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiscussionListViewModel by viewModels()
    private lateinit var adapter: DiscussionAdapter
    private var currentCategory: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscussionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DiscussionAdapter(
            onClick = { discussion ->
                val bundle = Bundle().apply { putLong("discussion_id", discussion.id) }
                findNavController().navigate(R.id.action_discussions_to_detail, bundle)
            },
            onAuthorClick = { discussion ->
                if (discussion.authorId > 0) {
                    val bundle = Bundle().apply { putLong("user_id", discussion.authorId) }
                    findNavController().navigate(R.id.publicProfileFragment, bundle)
                }
            }
        )

        binding.rvDiscussions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DiscussionListFragment.adapter
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadDiscussions(currentCategory, force = true) }

        binding.fabNewDiscussion.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (isLoggedIn) {
                    findNavController().navigate(R.id.action_discussions_to_create)
                } else {
                    Snackbar.make(binding.root, "请先登录后再发帖", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { findNavController().navigate(R.id.loginFragment) }
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
                    viewModel.loadDiscussions(currentCategory, force = true)
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>("refresh_discussions")
                }
            }

        observeData()
        viewModel.loadCategories()
        viewModel.loadDiscussions()
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
                    currentCategory = category
                    viewModel.loadDiscussions(category)
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
            adapter.submitList(list)
            binding.layoutEmpty.visibility = if (list.isEmpty() && binding.layoutError.visibility == View.GONE) View.VISIBLE else View.GONE
            binding.tvDiscussionCount.text = if (list.isNotEmpty()) "${list.size} 条讨论" else ""
        }
        viewModel.categories.observe(viewLifecycleOwner) { renderCategoryChips(it) }
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
            viewModel.loadDiscussions(currentCategory)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
