package com.sknote.app.ui.discussion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.BackendImageUploader
import com.sknote.app.data.model.DiscussionCategory
import com.sknote.app.databinding.FragmentCreateDiscussionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class CreateDiscussionFragment : Fragment() {

    private data class DiscussionDraftState(
        val title: String,
        val content: String,
        val category: String,
        val imageCount: Int
    )

    private var _binding: FragmentCreateDiscussionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreateDiscussionViewModel by viewModels()

    private var discussionId: Long? = null
    private var discussionCategories: List<DiscussionCategory> = emptyList()
    private var selectedCategory = "general"
    private var linkedArticleId: Long? = null
    private var hasBoundExistingDiscussion = false
    private var blockJson: JSONObject? = null
    private var paletteJson: JSONObject? = null

    private lateinit var imageAdapter: ImagePreviewAdapter
    private val maxImages = 9
    private var initialDraftState = DiscussionDraftState("", "", "general", 0)

    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        val remaining = maxImages - imageAdapter.size()
        uris.take(remaining).forEach { addImage(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateDiscussionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        discussionId = arguments?.getLong("discussion_id")?.takeIf { it > 0L }
        linkedArticleId = arguments?.getLong("article_id")?.takeIf { it > 0L }

        binding.toolbar.setNavigationOnClickListener { confirmExit() }
        binding.toolbar.title = if (discussionId == null) "发起讨论" else "编辑讨论"
        binding.tvScreenTitle.text = if (discussionId == null) "发起讨论" else "编辑讨论"

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { confirmExit() }
            }
        )

        binding.spinnerCategory.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = discussionCategories.getOrNull(position)?.slug ?: selectedCategory
            binding.layoutCategory.error = null
            updateFormState()
        }

        setupInputs()
        setupImagePicker()
        if (discussionId == null) {
            setupBlockShare()
            setupPaletteShare()
        }

        binding.btnSubmit.setOnClickListener { submitDiscussion() }

        observeData()
        updateGuideCard()
        updateFormState()
        captureInitialDraftState()
        viewModel.loadCategories()
        discussionId?.let { viewModel.loadDiscussion(it) }
    }

    private fun setupInputs() {
        binding.etTitle.doAfterTextChanged {
            binding.layoutTitle.error = null
            updateFormState()
        }
        binding.etContent.doAfterTextChanged {
            binding.layoutContent.error = null
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

    private fun bindCategories(categories: List<DiscussionCategory>) {
        discussionCategories = categories
        val labels = categories.map { it.name }
        binding.spinnerCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels))

        val selected = categories.firstOrNull { it.slug == selectedCategory } ?: categories.firstOrNull()
        selected?.let {
            selectedCategory = it.slug
            binding.spinnerCategory.setText(it.name, false)
        }
        updateFormState()
    }

    private fun bindExistingDiscussion(discussion: com.sknote.app.data.model.Discussion) {
        if (hasBoundExistingDiscussion) return
        hasBoundExistingDiscussion = true
        linkedArticleId = discussion.articleId
        selectedCategory = discussion.category.orEmpty().ifEmpty { selectedCategory }
        binding.etTitle.setText(discussion.title)
        binding.etContent.setText(discussion.content.orEmpty())

        val selected = discussionCategories.firstOrNull { it.slug == selectedCategory }
        if (selected != null) {
            binding.spinnerCategory.setText(selected.name, false)
        }
        updateGuideCard()
        updateFormState()
        captureInitialDraftState()
    }

    private fun setupBlockShare() {
        val jsonStr = arguments?.getString("block_json", "") ?: ""
        if (jsonStr.isEmpty()) return

        try {
            blockJson = JSONObject(jsonStr)
        } catch (_: Exception) { return }

        val bj = blockJson ?: return

        // Pre-fill UI
        binding.toolbar.title = "分享积木块"
        binding.etTitle.setText(bj.optString("name", "自定义积木块"))

        // Use 'general' category for block shares
        selectedCategory = "general"
        discussionCategories.firstOrNull { it.slug == selectedCategory }?.let {
            binding.spinnerCategory.setText(it.name, false)
        }

        // Show block preview card
        val previewView = BlockShareHelper.createPreviewView(requireContext(), binding.blockPreviewContainer, bj, showActions = false)
        binding.blockPreviewContainer.addView(previewView)
        binding.blockPreviewContainer.visibility = View.VISIBLE
        updateGuideCard()
        updateFormState()
    }

    private fun setupPaletteShare() {
        val jsonStr = arguments?.getString("palette_json", "") ?: ""
        if (jsonStr.isEmpty()) return

        try {
            paletteJson = JSONObject(jsonStr)
        } catch (_: Exception) { return }

        val pj = paletteJson ?: return

        binding.toolbar.title = "分享调色板"
        binding.etTitle.setText(pj.optString("palette_name", "自定义调色板"))

        selectedCategory = "general"
        discussionCategories.firstOrNull { it.slug == selectedCategory }?.let {
            binding.spinnerCategory.setText(it.name, false)
        }

        val previewView = BlockShareHelper.createPalettePreviewView(requireContext(), binding.blockPreviewContainer, pj, showActions = false)
        binding.blockPreviewContainer.addView(previewView)
        binding.blockPreviewContainer.visibility = View.VISIBLE
        updateGuideCard()
        updateFormState()
    }

    private fun setupImagePicker() {
        imageAdapter = ImagePreviewAdapter { position ->
            imageAdapter.removeAt(position)
            updateImageUI()
        }

        binding.rvImages.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            this.adapter = imageAdapter
        }

        binding.btnAddImage.setOnClickListener {
            if (imageAdapter.size() >= maxImages) {
                Snackbar.make(binding.root, "最多只能添加 $maxImages 张图片", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickMultipleImages.launch("image/*")
        }
    }

    private fun addImage(uri: Uri) {
        if (imageAdapter.size() >= maxImages) return
        imageAdapter.addImage(uri)
        updateImageUI()
        uploadImage(uri)
    }

    private fun updateImageUI() {
        binding.rvImages.visibility = if (imageAdapter.size() > 0) View.VISIBLE else View.GONE
        binding.tvImageHint.text = if (imageAdapter.size() > 0) {
            "已选择 ${imageAdapter.size()}/$maxImages 张图片"
        } else {
            "最多可添加 $maxImages 张图片，每张不超过 5MB"
        }
        updateFormState()
    }

    private fun uploadImage(uri: Uri) {
        val pos = imageAdapter.indexOfUri(uri)
        if (pos < 0) return
        imageAdapter.setUploading(pos, true)
        updateFormState()
        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { compressImage(uri, resolver) }
                if (bytes == null) {
                    Snackbar.make(binding.root, "图片处理失败", Snackbar.LENGTH_SHORT).show()
                    val p = imageAdapter.indexOfUri(uri)
                    if (p >= 0) { imageAdapter.removeAt(p); updateImageUI() }
                    return@launch
                }

                val fileName = "img_${System.currentTimeMillis()}.jpg"
                val result = BackendImageUploader.upload(bytes, fileName)

                val p = imageAdapter.indexOfUri(uri)
                if (p < 0) return@launch
                if (result.success && result.url != null) {
                    imageAdapter.setUploadedUrl(p, result.url)
                    updateFormState()
                } else {
                    Snackbar.make(binding.root, result.error ?: "上传失败", Snackbar.LENGTH_SHORT).show()
                    imageAdapter.removeAt(p)
                    updateImageUI()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "上传失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
                val p = imageAdapter.indexOfUri(uri)
                if (p >= 0) { imageAdapter.removeAt(p); updateImageUI() }
            }
        }
    }

    private fun compressImage(uri: Uri, resolver: android.content.ContentResolver): ByteArray? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            } ?: return null
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = boundsOptions.outWidth,
                    height = boundsOptions.outHeight,
                    reqWidth = 1280,
                    reqHeight = 1280
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val original = resolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return null

            val maxWidth = 960
            val scaled = if (original.width > maxWidth) {
                val ratio = maxWidth.toFloat() / original.width
                Bitmap.createScaledBitmap(original, maxWidth, (original.height * ratio).toInt(), true)
            } else {
                original
            }

            val baos = ByteArrayOutputStream()
            var quality = 82
            do {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 8
            } while (baos.size() > 850 * 1024 && quality >= 42)

            if (scaled !== original) scaled.recycle()
            original.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= reqWidth && currentHeight / 2 >= reqHeight) {
            currentWidth /= 2
            currentHeight /= 2
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun submitDiscussion() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        if (!validateBeforeSubmit(title, content)) return

        if (imageAdapter.size() > 0 && !imageAdapter.allUploaded()) {
            Snackbar.make(binding.root, "图片正在上传中，请稍候...", Snackbar.LENGTH_SHORT).show()
            updateFormState()
            return
        }

        val imageMarkdown = imageAdapter.getUploadedUrls().joinToString("\n\n") { url ->
            "![图片]($url)"
        }

        // Build final content with block/palette data embedded
        val parts = mutableListOf<String>()
        if (content.isNotEmpty()) parts.add(content)
        blockJson?.let { parts.add(BlockShareHelper.encodeBlockMarkdown(it)) }
        paletteJson?.let { parts.add(BlockShareHelper.encodePaletteMarkdown(it)) }
        if (imageMarkdown.isNotEmpty()) parts.add(imageMarkdown)

        val finalContent = parts.joinToString("\n\n")

        val editingId = discussionId
        if (editingId != null) {
            viewModel.updateDiscussion(editingId, title, finalContent, selectedCategory, linkedArticleId)
        } else {
            viewModel.createDiscussion(title, finalContent, selectedCategory, linkedArticleId)
        }
    }

    private fun validateBeforeSubmit(title: String, content: String): Boolean {
        var isValid = true
        if (title.isEmpty()) {
            binding.layoutTitle.error = "请输入标题"
            isValid = false
        } else {
            binding.layoutTitle.error = null
        }

        val hasCategory = binding.spinnerCategory.text?.toString()?.trim().orEmpty().isNotEmpty() || selectedCategory.isNotBlank()
        if (!hasCategory) {
            binding.layoutCategory.error = "请选择分类"
            isValid = false
        } else {
            binding.layoutCategory.error = null
        }

        val hasBodyContent = content.isNotEmpty() || imageAdapter.size() > 0 || blockJson != null || paletteJson != null
        if (!hasBodyContent) {
            binding.layoutContent.error = "请输入正文，或添加图片/分享内容"
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

    private fun currentDraftState(): DiscussionDraftState {
        return DiscussionDraftState(
            title = binding.etTitle.text?.toString()?.trim().orEmpty(),
            content = binding.etContent.text?.toString()?.trim().orEmpty(),
            category = selectedCategory,
            imageCount = imageAdapter.size()
        )
    }

    private fun hasUnsavedChanges(): Boolean {
        return currentDraftState() != initialDraftState
    }

    private fun confirmExit() {
        if (!hasUnsavedChanges()) {
            findNavController().navigateUp()
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("放弃本次编辑？")
            .setMessage("当前填写的讨论内容尚未保存，确定返回吗？")
            .setPositiveButton("返回") { _, _ -> findNavController().navigateUp() }
            .setNegativeButton("继续编辑", null)
            .show()
    }

    private fun updateGuideCard() {
        binding.tvScreenTitle.text = when {
            discussionId != null -> "编辑讨论"
            blockJson != null -> "分享积木块"
            paletteJson != null -> "分享调色板"
            linkedArticleId != null -> "发起文章讨论"
            else -> "发起讨论"
        }
        binding.tvScreenSubtitle.text = when {
            discussionId != null -> "修改标题、正文或分类后，会同步更新当前讨论内容。"
            blockJson != null -> "可以补充适用场景、参数说明或遇到的问题，方便别人复用和讨论。"
            paletteJson != null -> "建议写清配色用途、适配场景和使用建议，让别人更容易参考。"
            linkedArticleId != null -> "这条讨论会关联到文章，适合继续追问、反馈补充或延伸交流。"
            else -> "尽量写清背景、现象和期望结果，别人更容易理解并回复。"
        }
    }

    private fun updateFormState() {
        val isLoading = viewModel.isLoading.value == true
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val content = binding.etContent.text?.toString()?.trim().orEmpty()
        val hasBodyContent = content.isNotEmpty() || imageAdapter.size() > 0 || blockJson != null || paletteJson != null
        val isUploadingImages = imageAdapter.size() > 0 && !imageAdapter.allUploaded()
        val canSubmit = !isLoading && title.isNotEmpty() && hasBodyContent && !isUploadingImages

        binding.btnSubmit.isEnabled = canSubmit
        binding.btnSubmit.alpha = if (canSubmit) 1f else 0.6f
        binding.btnAddImage.isEnabled = !isLoading && imageAdapter.size() < maxImages
        binding.btnAddImage.alpha = if (binding.btnAddImage.isEnabled) 1f else 0.6f
        binding.tvSubmitHint.text = when {
            isLoading && discussionId != null -> "正在保存讨论修改，请稍候…"
            isLoading -> "正在发布讨论，请稍候…"
            isUploadingImages -> "图片仍在上传中，上传完成后即可发布"
            title.isEmpty() -> "先写一个清晰的标题，方便别人快速理解主题"
            !hasBodyContent -> "正文至少填写一点内容，或添加图片/分享内容"
            discussionId != null -> "修改完成后会同步更新讨论详情和列表"
            linkedArticleId != null -> "发布后会成为该文章下的关联讨论"
            else -> "可以直接发布，也可以继续补充细节和图片"
        }
    }

    private fun observeData() {
        viewModel.categories.observe(viewLifecycleOwner) { bindCategories(it) }

        viewModel.discussion.observe(viewLifecycleOwner) { discussion ->
            discussion?.let { bindExistingDiscussion(it) }
        }

        viewModel.success.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                viewModel.onSuccessHandled()
                findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_discussions", true)
                findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_discussion_detail", true)
                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                    "discussion_result_message",
                    if (discussionId == null) "讨论发布成功" else "讨论更新成功"
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
            binding.btnSubmit.text = when {
                isLoading && discussionId != null -> "保存中..."
                isLoading -> "发布中..."
                discussionId != null -> "保存修改"
                else -> "发布讨论"
            }
            updateFormState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
