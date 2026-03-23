package com.sknote.app.ui.manage.category

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
import com.google.android.material.tabs.TabLayout
import com.sknote.app.R
import com.sknote.app.data.model.Category
import com.sknote.app.data.model.DiscussionCategory
import com.sknote.app.databinding.FragmentCategoryManageBinding
import com.sknote.app.ui.manage.discussioncategory.DiscussionCategoryEditorFragment
import com.sknote.app.ui.manage.discussioncategory.DiscussionCategoryManageAdapter
import com.sknote.app.ui.manage.discussioncategory.DiscussionCategoryManageViewModel

class CategoryManageFragment : Fragment() {

    companion object {
        const val ARG_INITIAL_TAB = "initial_tab"
        private const val STATE_SELECTED_TAB = "selected_tab"
    }

    private var _binding: FragmentCategoryManageBinding? = null
    private val binding get() = _binding!!

    private val articleVm: CategoryManageViewModel by viewModels()
    private val discussionVm: DiscussionCategoryManageViewModel by viewModels()

    private lateinit var articleAdapter: CategoryManageAdapter
    private lateinit var discussionAdapter: DiscussionCategoryManageAdapter

    private var currentTab = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.title = "分类管理"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // --- adapters ---
        articleAdapter = CategoryManageAdapter(
            onEdit = { openArticleCategoryEditor(it.id) },
            onDelete = { showArticleDeleteConfirm(it) }
        )
        discussionAdapter = DiscussionCategoryManageAdapter(
            onEdit = { openDiscussionCategoryEditor(it.id) },
            onDelete = { showDiscussionDeleteConfirm(it) }
        )

        binding.rvCategories.layoutManager = LinearLayoutManager(context)

        // --- tabs ---
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("文章分类"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("讨论分类"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { switchTab(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.fabAdd.setOnClickListener {
            if (currentTab == 0) openArticleCategoryEditor() else openDiscussionCategoryEditor()
        }

        observeData()
        articleVm.loadCategories()
        discussionVm.loadCategories()

        val initialTab = (
            savedInstanceState?.getInt(STATE_SELECTED_TAB)
                ?: arguments?.getInt(ARG_INITIAL_TAB, 0)
                ?: 0
            ).coerceIn(0, binding.tabLayout.tabCount - 1)
        binding.tabLayout.getTabAt(initialTab)?.select()
        switchTab(initialTab)
    }

    private fun switchTab(position: Int) {
        currentTab = position
        if (position == 0) {
            binding.rvCategories.adapter = articleAdapter
            updateArticleEmpty(articleVm.categories.value ?: emptyList())
        } else {
            binding.rvCategories.adapter = discussionAdapter
            updateDiscussionEmpty(discussionVm.categories.value ?: emptyList())
        }
    }

    // ---------- observe ----------

    private fun observeData() {
        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle

        // article editor results
        navHandle?.getLiveData<Boolean>(CategoryEditorFragment.RESULT_REFRESH_KEY)
            ?.observe(viewLifecycleOwner) { refresh ->
                if (refresh == true) {
                    articleVm.loadCategories()
                    navHandle.remove<Boolean>(CategoryEditorFragment.RESULT_REFRESH_KEY)
                }
            }
        navHandle?.getLiveData<String>(CategoryEditorFragment.RESULT_MESSAGE_KEY)
            ?.observe(viewLifecycleOwner) { msg ->
                if (!msg.isNullOrEmpty()) {
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>(CategoryEditorFragment.RESULT_MESSAGE_KEY)
                }
            }

        // discussion editor results
        navHandle?.getLiveData<Boolean>(DiscussionCategoryEditorFragment.RESULT_REFRESH_KEY)
            ?.observe(viewLifecycleOwner) { refresh ->
                if (refresh == true) {
                    discussionVm.loadCategories()
                    navHandle.remove<Boolean>(DiscussionCategoryEditorFragment.RESULT_REFRESH_KEY)
                }
            }
        navHandle?.getLiveData<String>(DiscussionCategoryEditorFragment.RESULT_MESSAGE_KEY)
            ?.observe(viewLifecycleOwner) { msg ->
                if (!msg.isNullOrEmpty()) {
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>(DiscussionCategoryEditorFragment.RESULT_MESSAGE_KEY)
                }
            }

        // article list
        articleVm.categories.observe(viewLifecycleOwner) { list ->
            articleAdapter.submitList(list)
            if (currentTab == 0) updateArticleEmpty(list)
        }
        articleVm.message.observe(viewLifecycleOwner) { msg ->
            msg?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show(); articleVm.clearMessage() }
        }

        // discussion list
        discussionVm.categories.observe(viewLifecycleOwner) { list ->
            discussionAdapter.submitList(list)
            if (currentTab == 1) updateDiscussionEmpty(list)
        }
        discussionVm.message.observe(viewLifecycleOwner) { msg ->
            msg?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show(); discussionVm.clearMessage() }
        }
    }

    // ---------- empty state ----------

    private fun updateArticleEmpty(list: List<Category>) {
        binding.tvEmpty.text = "还没有文章分类\n点击右下角按钮开始添加"
        binding.toolbar.subtitle = "共 ${list.size} 个文章分类"
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCategories.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateDiscussionEmpty(list: List<DiscussionCategory>) {
        binding.tvEmpty.text = "还没有讨论分类\n点击右下角按钮开始添加"
        binding.toolbar.subtitle = "共 ${list.size} 个讨论分类"
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCategories.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---------- navigation ----------

    private fun openArticleCategoryEditor(categoryId: Long = 0L) {
        findNavController().navigate(
            R.id.categoryEditorFragment,
            bundleOf(CategoryEditorFragment.ARG_CATEGORY_ID to categoryId)
        )
    }

    private fun openDiscussionCategoryEditor(categoryId: Long = 0L) {
        findNavController().navigate(
            R.id.discussionCategoryEditorFragment,
            bundleOf(DiscussionCategoryEditorFragment.ARG_CATEGORY_ID to categoryId)
        )
    }

    // ---------- delete confirms ----------

    private fun showArticleDeleteConfirm(category: Category) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除分类")
            .setMessage("确定删除分类「${category.name}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> articleVm.deleteCategory(category.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDiscussionDeleteConfirm(category: DiscussionCategory) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除讨论分类")
            .setMessage("确定删除讨论分类「${category.name}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> discussionVm.deleteCategory(category.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_TAB, currentTab)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
