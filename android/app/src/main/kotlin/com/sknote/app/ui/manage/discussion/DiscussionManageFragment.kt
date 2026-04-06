package com.sknote.app.ui.manage.discussion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import androidx.core.os.bundleOf
import com.sknote.app.R
import com.sknote.app.data.model.Discussion
import com.sknote.app.databinding.FragmentDiscussionManageBinding
import com.sknote.app.ui.manage.category.CategoryManageFragment

class DiscussionManageFragment : Fragment() {

    private var _binding: FragmentDiscussionManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiscussionManageViewModel by viewModels()
    private lateinit var adapter: DiscussionManageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscussionManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnManageDiscussionCategories.setOnClickListener {
            findNavController().navigate(
                R.id.categoryManageFragment,
                bundleOf(CategoryManageFragment.ARG_INITIAL_TAB to 1)
            )
        }

        adapter = DiscussionManageAdapter(
            onView = { discussion ->
                val bundle = Bundle().apply { putLong("discussion_id", discussion.id) }
                findNavController().navigate(R.id.discussionDetailFragment, bundle)
            },
            onEdit = { discussion ->
                val bundle = Bundle().apply { putLong("discussion_id", discussion.id) }
                findNavController().navigate(R.id.createDiscussionFragment, bundle)
            },
            onDelete = { discussion -> showDeleteConfirm(discussion) }
        )
        binding.rvDiscussions.layoutManager = LinearLayoutManager(context)
        binding.rvDiscussions.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadDiscussions(force = true)
        }

        observeData()
        viewModel.loadDiscussions()
    }

    private fun observeData() {
        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle

        navHandle?.getLiveData<Boolean>("refresh_discussions")
            ?.observe(viewLifecycleOwner) { refresh ->
                if (refresh == true) {
                    viewModel.loadDiscussions(force = true)
                    navHandle.remove<Boolean>("refresh_discussions")
                }
            }

        navHandle?.getLiveData<String>("discussion_result_message")
            ?.observe(viewLifecycleOwner) { msg ->
                if (!msg.isNullOrEmpty()) {
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>("discussion_result_message")
                }
            }

        viewModel.discussions.observe(viewLifecycleOwner) { discussions ->
            adapter.submitList(discussions)
            binding.tvEmpty.visibility = if (discussions.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun showDeleteConfirm(discussion: Discussion) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除讨论")
            .setMessage("确定删除讨论「${discussion.title}」吗？\n包含 ${discussion.replyCount} 条回复，此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteDiscussion(discussion.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
