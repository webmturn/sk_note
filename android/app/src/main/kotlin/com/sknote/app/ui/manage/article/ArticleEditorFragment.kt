package com.sknote.app.ui.manage.article

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
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

    private data class ArticleDraftState(
        val title: String,
        val summary: String,
        val content: String,
        val categoryId: Long?
    )

    private var _binding: FragmentArticleEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ArticleEditorViewModel by viewModels()
    private lateinit var markwon: Markwon

    private var articleId: Long? = null
    private var isPreviewMode = false
    private var categories: List<Category> = emptyList()
    private var selectedCategoryId: Long? = null
    private var currentDraftState = ArticleDraftState("", "", "", null)
    private var initialDraftState = ArticleDraftState("", "", "", null)
    private var hasRestoredDraftState = false
    private var hasLoadedInitialData = false

    companion object {
        const val RESULT_REFRESH_KEY = "refresh_articles"
        const val RESULT_MESSAGE_KEY = "article_manage_message"
        private const val STATE_CURRENT_TITLE = "state_current_title"
        private const val STATE_CURRENT_SUMMARY = "state_current_summary"
        private const val STATE_CURRENT_CONTENT = "state_current_content"
        private const val STATE_CURRENT_CATEGORY_ID = "state_current_category_id"
        private const val STATE_INITIAL_TITLE = "state_initial_title"
        private const val STATE_INITIAL_SUMMARY = "state_initial_summary"
        private const val STATE_INITIAL_CONTENT = "state_initial_content"
        private const val STATE_INITIAL_CATEGORY_ID = "state_initial_category_id"
        private const val STATE_PREVIEW_MODE = "state_preview_mode"
        private const val STATE_HAS_LOADED_INITIAL_DATA = "state_has_loaded_initial_data"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArticleEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        markwon = Markwon.create(requireContext())

        savedInstanceState?.let {
            currentDraftState = ArticleDraftState(
                title = it.getString(STATE_CURRENT_TITLE).orEmpty(),
                summary = it.getString(STATE_CURRENT_SUMMARY).orEmpty(),
                content = it.getString(STATE_CURRENT_CONTENT).orEmpty(),
                categoryId = it.getLong(STATE_CURRENT_CATEGORY_ID).takeIf { value -> value > 0L }
            )
            initialDraftState = ArticleDraftState(
                title = it.getString(STATE_INITIAL_TITLE).orEmpty(),
                summary = it.getString(STATE_INITIAL_SUMMARY).orEmpty(),
                content = it.getString(STATE_INITIAL_CONTENT).orEmpty(),
                categoryId = it.getLong(STATE_INITIAL_CATEGORY_ID).takeIf { value -> value > 0L }
            )
            selectedCategoryId = currentDraftState.categoryId
            isPreviewMode = it.getBoolean(STATE_PREVIEW_MODE, false)
            hasRestoredDraftState = true
            hasLoadedInitialData = it.getBoolean(STATE_HAS_LOADED_INITIAL_DATA, false)
        }

        articleId = arguments?.getLong("article_id")?.takeIf { it > 0 }

        binding.toolbar.title = if (articleId == null) "创建文章" else "编辑文章"
        binding.tvScreenTitle.text = if (articleId == null) "创建文章" else "编辑文章"
        binding.tvScreenSubtitle.text = if (articleId == null) {
            "整理好标题、摘要和 Markdown 正文后再保存，阅读体验会更清晰。"
        } else {
            "修改后的内容保存后会立即同步到文章详情页。"
        }
        binding.toolbar.setNavigationOnClickListener { confirmExit() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { confirmExit() }
            }
        )

        binding.btnPreview.setOnClickListener { togglePreview() }

        binding.btnSave.setOnClickListener { saveArticle() }

        setupInputs()
        observeData()
        applyDraftState(currentDraftState)
        applyPreviewMode()
        updateFormState()
        viewModel.loadCategories()

        if (!hasLoadedInitialData) {
            articleId?.let { viewModel.loadArticle(it) }
        }
    }

    private fun setupInputs() {
        binding.etTitle.doAfterTextChanged {
            binding.layoutTitle.error = null
            updateFormState()
        }
        binding.etSummary.doAfterTextChanged {
            updateFormState()
        }
        binding.etContent.doAfterTextChanged {
            binding.layoutContent.error = null
            if (isPreviewMode) renderPreview()
            updateFormState()
        }
        binding.spinnerCategory.doAfterTextChanged {
            binding.layoutCategory.error = null
            updateFormState()
        }
        binding.spinnerCategory.setOnClickListener { binding.spinnerCategory.showDropDown() }
        binding.spinnerCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.spinnerCategory.showDropDown()
        }
    }

    private fun observeData() {
        viewModel.categories.observe(viewLifecycleOwner) { cats ->
            categories = cats
            val names = cats.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
            binding.spinnerCategory.setAdapter(adapter)
            binding.spinnerCategory.setOnItemClickListener { _, _, position, _ ->
                selectedCategoryId = cats[position].id
                binding.layoutCategory.error = null
                updateFormState()
            }

            selectedCategoryId?.let { categoryId ->
                val index = cats.indexOfFirst { it.id == categoryId }
                if (index >= 0) {
                    binding.spinnerCategory.setText(cats[index].name, false)
                    selectedCategoryId = cats[index].id
                }
            }
            updateFormState()
        }

        viewModel.article.observe(viewLifecycleOwner) { article ->
            article ?: return@observe
            if (hasLoadedInitialData && hasRestoredDraftState) return@observe
            binding.etTitle.setText(article.title)
            binding.etSummary.setText(article.summary.orEmpty())
            binding.etContent.setText(article.content.orEmpty())
            selectedCategoryId = article.categoryId
            binding.switchPublished.isChecked = article.isPublished == 1

            val index = categories.indexOfFirst { it.id == article.categoryId }
            if (index >= 0) {
                binding.spinnerCategory.setText(categories[index].name, false)
            }
            if (isPreviewMode) renderPreview()
            hasLoadedInitialData = true
            captureInitialDraftState()
            updateFormState()
        }

        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                viewModel.onSaveHandled()
                findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_REFRESH_KEY, true)
                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                    RESULT_MESSAGE_KEY,
                    if (articleId == null) "文章创建成功" else "文章更新成功"
                )
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
            binding.btnSave.text = if (isLoading) "保存中..." else "保存文章"
            updateFormState()
        }
    }

    private fun togglePreview() {
        isPreviewMode = !isPreviewMode
        applyPreviewMode()
        updateFormState()
    }

    private fun applyPreviewMode() {
        if (isPreviewMode) {
            renderPreview()
            binding.etContent.visibility = View.GONE
            binding.tvPreview.visibility = View.VISIBLE
            binding.btnPreview.text = "继续编辑"
        } else {
            binding.etContent.visibility = View.VISIBLE
            binding.tvPreview.visibility = View.GONE
            binding.btnPreview.text = "预览"
        }
    }

    private fun applyDraftState(state: ArticleDraftState) {
        if (state.title.isEmpty() && state.summary.isEmpty() && state.content.isEmpty() && state.categoryId == null) {
            return
        }
        binding.etTitle.setText(state.title)
        binding.etSummary.setText(state.summary)
        binding.etContent.setText(state.content)
        selectedCategoryId = state.categoryId
    }

    private fun renderPreview() {
        val content = binding.etContent.text?.toString().orEmpty()
        if (content.isBlank()) {
            binding.tvPreview.text = "暂无可预览内容"
            binding.tvPreview.alpha = 0.6f
        } else {
            binding.tvPreview.alpha = 1f
            markwon.setMarkdown(binding.tvPreview, content)
        }
    }

    private fun confirmExit() {
        if (hasUnsavedChanges()) {
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

        if (!validateBeforeSave(title, content)) return

        val publishedFlag = if (binding.switchPublished.isChecked) 1 else 0

        if (articleId != null) {
            viewModel.updateArticle(articleId!!, UpdateArticleRequest(
                title = title,
                content = content,
                summary = summary,
                categoryId = selectedCategoryId,
                isPublished = publishedFlag,
            ))
        } else {
            viewModel.createArticle(CreateArticleRequest(
                title = title,
                content = content,
                summary = summary,
                categoryId = selectedCategoryId!!,
                isPublished = publishedFlag,
            ))
        }
    }

    private fun validateBeforeSave(title: String, content: String): Boolean {
        var isValid = true
        if (title.isEmpty()) {
            binding.layoutTitle.error = "请输入文章标题"
            isValid = false
        } else {
            binding.layoutTitle.error = null
        }
        if (selectedCategoryId == null) {
            binding.layoutCategory.error = "请选择分类"
            isValid = false
        } else {
            binding.layoutCategory.error = null
        }
        if (content.isEmpty()) {
            binding.layoutContent.error = "请输入文章正文"
            isValid = false
        } else {
            binding.layoutContent.error = null
        }
        if (!isValid) updateFormState()
        return isValid
    }

    private fun captureInitialDraftState() {
        initialDraftState = currentDraftState()
    }

    private fun currentDraftState(): ArticleDraftState {
        return ArticleDraftState(
            title = binding.etTitle.text?.toString()?.trim().orEmpty(),
            summary = binding.etSummary.text?.toString()?.trim().orEmpty(),
            content = binding.etContent.text?.toString()?.trim().orEmpty(),
            categoryId = selectedCategoryId
        )
    }

    private fun hasUnsavedChanges(): Boolean {
        return currentDraftState() != initialDraftState
    }

    private fun updateFormState() {
        val isLoading = viewModel.isLoading.value == true
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val summary = binding.etSummary.text?.toString()?.trim().orEmpty()
        val content = binding.etContent.text?.toString()?.trim().orEmpty()
        val canSave = !isLoading && title.isNotEmpty() && selectedCategoryId != null && content.isNotEmpty()

        binding.btnSave.isEnabled = canSave
        binding.btnSave.alpha = if (canSave) 1f else 0.6f
        binding.btnPreview.alpha = if (content.isNotEmpty() || isPreviewMode) 1f else 0.6f
        binding.tvEditorStats.text = buildString {
            if (isPreviewMode) {
                append("预览模式 · ")
            }
            append("正文 ")
            append(content.length)
            append(" 字 · 摘要 ")
            append(summary.length)
            append(" 字")
        }
        binding.tvDraftMeta.text = when {
            isLoading && articleId != null -> "正在保存文章修改，请稍候…"
            isLoading -> "正在创建文章，请稍候…"
            title.isEmpty() -> "先填写标题，让文章主题更明确"
            selectedCategoryId == null -> "请选择文章分类，方便内容归档"
            content.isEmpty() -> "正文不能为空，至少补充主要内容"
            articleId != null -> "保存后会同步更新文章详情页内容"
            else -> "内容已准备好，可以保存文章"
        }
    }

    override fun onPause() {
        if (_binding != null) {
            currentDraftState = currentDraftState()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (_binding != null) {
            currentDraftState = currentDraftState()
        }
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CURRENT_TITLE, currentDraftState.title)
        outState.putString(STATE_CURRENT_SUMMARY, currentDraftState.summary)
        outState.putString(STATE_CURRENT_CONTENT, currentDraftState.content)
        currentDraftState.categoryId?.let { outState.putLong(STATE_CURRENT_CATEGORY_ID, it) }
        outState.putString(STATE_INITIAL_TITLE, initialDraftState.title)
        outState.putString(STATE_INITIAL_SUMMARY, initialDraftState.summary)
        outState.putString(STATE_INITIAL_CONTENT, initialDraftState.content)
        initialDraftState.categoryId?.let { outState.putLong(STATE_INITIAL_CATEGORY_ID, it) }
        outState.putBoolean(STATE_PREVIEW_MODE, isPreviewMode)
        outState.putBoolean(STATE_HAS_LOADED_INITIAL_DATA, hasLoadedInitialData)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
