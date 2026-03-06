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
import com.sknote.app.data.model.Category
import com.sknote.app.data.model.CreateCategoryRequest
import com.sknote.app.databinding.FragmentCategoryManageBinding
import com.sknote.app.databinding.DialogCategoryBinding

class CategoryManageFragment : Fragment() {

    private var _binding: FragmentCategoryManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CategoryManageViewModel by viewModels()
    private lateinit var adapter: CategoryManageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = CategoryManageAdapter(
            onEdit = { category -> showCategoryDialog(category) },
            onDelete = { category -> showDeleteConfirm(category) }
        )
        binding.rvCategories.layoutManager = LinearLayoutManager(context)
        binding.rvCategories.adapter = adapter

        binding.fabAdd.setOnClickListener { showCategoryDialog(null) }

        observeData()
        viewModel.loadCategories()
    }

    private fun observeData() {
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            adapter.submitList(categories)
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

    private fun showCategoryDialog(category: Category?) {
        val dialogBinding = DialogCategoryBinding.inflate(layoutInflater)
        category?.let {
            dialogBinding.etName.setText(it.name)
            dialogBinding.etDescription.setText(it.description)
            dialogBinding.etIcon.setText(it.icon)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (category == null) "添加分类" else "编辑分类")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                if (name.isEmpty()) {
                    Snackbar.make(binding.root, "分类名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val request = CreateCategoryRequest(
                    name = name,
                    description = dialogBinding.etDescription.text.toString().trim(),
                    icon = dialogBinding.etIcon.text.toString().trim()
                )
                if (category == null) {
                    viewModel.createCategory(request)
                } else {
                    viewModel.updateCategory(category.id, request)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteConfirm(category: Category) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除分类")
            .setMessage("确定删除分类「${category.name}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteCategory(category.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
