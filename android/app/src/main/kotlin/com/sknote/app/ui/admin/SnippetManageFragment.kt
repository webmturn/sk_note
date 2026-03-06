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
import com.sknote.app.data.model.Snippet
import com.sknote.app.databinding.FragmentSnippetManageBinding

class SnippetManageFragment : Fragment() {

    private var _binding: FragmentSnippetManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SnippetManageViewModel by viewModels()
    private lateinit var adapter: SnippetManageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSnippetManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = SnippetManageAdapter(
            onView = { snippet ->
                val bundle = Bundle().apply { putLong("snippet_id", snippet.id) }
                findNavController().navigate(R.id.snippetDetailFragment, bundle)
            },
            onDelete = { snippet -> showDeleteConfirm(snippet) }
        )
        binding.rvSnippets.layoutManager = LinearLayoutManager(context)
        binding.rvSnippets.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadSnippets(force = true)
        }

        observeData()
        viewModel.loadSnippets()
    }

    private fun observeData() {
        viewModel.snippets.observe(viewLifecycleOwner) { snippets ->
            adapter.submitList(snippets)
            binding.tvEmpty.visibility = if (snippets.isEmpty()) View.VISIBLE else View.GONE
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

    private fun showDeleteConfirm(snippet: Snippet) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除代码片段")
            .setMessage("确定删除「${snippet.title}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteSnippet(snippet.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
