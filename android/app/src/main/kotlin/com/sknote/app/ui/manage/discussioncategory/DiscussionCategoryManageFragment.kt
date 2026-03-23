package com.sknote.app.ui.manage.discussioncategory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.model.DiscussionCategory
import com.sknote.app.databinding.FragmentCategoryManageBinding

class DiscussionCategoryManageFragment : Fragment() {

    private var _binding: FragmentCategoryManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiscussionCategoryManageViewModel by viewModels()
    private lateinit var adapter: DiscussionCategoryManageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.title = "讨论分类管理"
        binding.tvEmpty.text = "还没有讨论分类\n点击右下角按钮开始添加"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = DiscussionCategoryManageAdapter(
            onEdit = { category -> openEditor(category.id) },
            onDelete = { category -> showDeleteConfirm(category) },
        )
        binding.rvCategories.layoutManager = LinearLayoutManager(context)
        binding.rvCategories.adapter = adapter

        binding.fabAdd.setOnClickListener { openEditor() }

        observeData()
        viewModel.loadCategories()
    }

    private fun observeData() {
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>(DiscussionCategoryEditorFragment.RESULT_REFRESH_KEY)
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    viewModel.loadCategories()
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>(DiscussionCategoryEditorFragment.RESULT_REFRESH_KEY)
                }
            }

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>(DiscussionCategoryEditorFragment.RESULT_MESSAGE_KEY)
            ?.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>(DiscussionCategoryEditorFragment.RESULT_MESSAGE_KEY)
                }
            }

        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            adapter.submitList(categories)
            binding.toolbar.subtitle = "共 ${categories.size} 个讨论分类"
            binding.tvEmpty.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
            binding.rvCategories.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun openEditor(categoryId: Long = 0L) {
        findNavController().navigate(
            R.id.discussionCategoryEditorFragment,
            bundleOf(DiscussionCategoryEditorFragment.ARG_CATEGORY_ID to categoryId)
        )
    }

    private fun showDeleteConfirm(category: DiscussionCategory) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除讨论分类")
            .setMessage("确定删除讨论分类「${category.name}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteCategory(category.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
