package com.sknote.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.data.model.Category
import com.sknote.app.data.model.CreateArticleRequest
import com.sknote.app.data.model.UpdateArticleRequest
import com.sknote.app.databinding.FragmentArticleEditorBinding
import io.noties.markwon.Markwon

class ArticleEditorFragment : Fragment() {

    private var _binding: FragmentArticleEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ArticleEditorViewModel by viewModels()
    private lateinit var markwon: Markwon

    private var articleId: Long? = null
    private var isPreviewMode = false
    private var categories: List<Category> = emptyList()
    private var selectedCategoryId: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArticleEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        markwon = Markwon.create(requireContext())

        articleId = arguments?.getLong("article_id")?.takeIf { it > 0 }

        binding.toolbar.title = if (articleId == null) "创建文章" else "编辑文章"
        binding.toolbar.setNavigationOnClickListener { confirmExit() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { confirmExit() }
            }
        )

        binding.btnPreview.setOnClickListener { togglePreview() }

        binding.btnSave.setOnClickListener { saveArticle() }

        observeData()
        viewModel.loadCategories()

        articleId?.let { viewModel.loadArticle(it) }
    }

    private fun observeData() {
        viewModel.categories.observe(viewLifecycleOwner) { cats ->
            categories = cats
            val names = cats.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
            binding.spinnerCategory.setAdapter(adapter)
            binding.spinnerCategory.setOnItemClickListener { _, _, position, _ ->
                selectedCategoryId = cats[position].id
            }

            // If editing, set the category after categories are loaded
            viewModel.article.value?.let { article ->
                val index = cats.indexOfFirst { it.id == article.categoryId }
                if (index >= 0) {
                    binding.spinnerCategory.setText(cats[index].name, false)
                    selectedCategoryId = cats[index].id
                }
            }
        }

        viewModel.article.observe(viewLifecycleOwner) { article ->
            binding.etTitle.setText(article.title)
            binding.etSummary.setText(article.summary)
            binding.etContent.setText(article.content)
            selectedCategoryId = article.categoryId

            val index = categories.indexOfFirst { it.id == article.categoryId }
            if (index >= 0) {
                binding.spinnerCategory.setText(categories[index].name, false)
            }
        }

        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                viewModel.onSaveHandled()
                Snackbar.make(binding.root, "保存成功", Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                viewModel.onErrorHandled()
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnSave.isEnabled = !isLoading
            binding.btnSave.text = if (isLoading) "保存中..." else "保存文章"
        }
    }

    private fun togglePreview() {
        isPreviewMode = !isPreviewMode
        if (isPreviewMode) {
            val content = binding.etContent.text.toString()
            markwon.setMarkdown(binding.tvPreview, content)
            binding.etContent.visibility = View.GONE
            binding.tvPreview.visibility = View.VISIBLE
            binding.btnPreview.text = "编辑"
        } else {
            binding.etContent.visibility = View.VISIBLE
            binding.tvPreview.visibility = View.GONE
            binding.btnPreview.text = "预览"
        }
    }

    private fun confirmExit() {
        val hasContent = binding.etTitle.text.toString().isNotEmpty() ||
                binding.etContent.text.toString().isNotEmpty()
        if (hasContent) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认退出")
                .setMessage("当前编辑内容尚未保存，确定退出吗？")
                .setPositiveButton("退出") { _, _ -> findNavController().navigateUp() }
                .setNegativeButton("取消", null)
                .show()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun saveArticle() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        val summary = binding.etSummary.text.toString().trim()

        if (title.isEmpty()) {
            Snackbar.make(binding.root, "请输入文章标题", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (selectedCategoryId == null) {
            Snackbar.make(binding.root, "请选择分类", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (content.isEmpty()) {
            Snackbar.make(binding.root, "请输入文章内容", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (articleId != null) {
            viewModel.updateArticle(articleId!!, UpdateArticleRequest(
                title = title,
                content = content,
                summary = summary,
                categoryId = selectedCategoryId
            ))
        } else {
            viewModel.createArticle(CreateArticleRequest(
                title = title,
                content = content,
                summary = summary,
                categoryId = selectedCategoryId!!
            ))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
