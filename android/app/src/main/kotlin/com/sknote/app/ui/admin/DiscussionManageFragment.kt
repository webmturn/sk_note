package com.sknote.app.ui.admin

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
import com.sknote.app.R
import com.sknote.app.data.model.Discussion
import com.sknote.app.databinding.FragmentDiscussionManageBinding

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

        adapter = DiscussionManageAdapter(
            onView = { discussion ->
                val bundle = Bundle().apply { putLong("discussion_id", discussion.id) }
                findNavController().navigate(R.id.discussionDetailFragment, bundle)
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
