package com.sknote.app.ui.discussion

import android.app.Dialog
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.BackendImageUploader
import com.sknote.app.data.model.DiscussionCategory
import com.sknote.app.databinding.FragmentCreateDiscussionBinding
import com.sknote.app.util.DiscussionIconResolver
import io.noties.markwon.Markwon
import io.noties.markwon.image.glide.GlideImagesPlugin
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
    private var isPreviewMode = false
    private var draftRestored = false
    private var markwon: Markwon? = null
    private val draftSaveHandler = Handler(Looper.getMainLooper())
    private val draftSaveRunnable = Runnable { persistDraftNow() }

    private val pickMultipleImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxImages)
    ) { uris: List<Uri> ->
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

        markwon = Markwon.builder(requireContext())
            .usePlugin(GlideImagesPlugin.create(requireContext()))
            .build()

        binding.toolbar.setNavigationOnClickListener { confirmExit() }
        binding.toolbar.title = if (discussionId == null) "发起讨论" else "编辑讨论"
        binding.tvScreenTitle.text = if (discussionId == null) "发起讨论" else "编辑讨论"

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { confirmExit() }
            }
        )

        binding.chipGroupCategory.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedId) ?: return@setOnCheckedStateChangeListener
            val slug = chip.tag as? String ?: return@setOnCheckedStateChangeListener
            if (slug != selectedCategory) {
                selectedCategory = slug
                updateCategoryDescription()
                updateFormState()
            }
        }

        setupInputs()
        setupFormatToolbar()
        setupImagePicker()
        binding.btnClearAttachment.setOnClickListener { clearAttachment() }
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
        if (discussionId == null) {
            promptRestoreDraftIfNeeded()
        }
    }

    private fun setupInputs() {
        binding.etTitle.doAfterTextChanged {
            binding.layoutTitle.error = null
            updateFormState()
            scheduleDraftSave()
        }
        binding.etContent.doAfterTextChanged {
            binding.layoutContent.error = null
            if (isPreviewMode) refreshPreview()
            updateFormState()
            scheduleDraftSave()
        }
    }

    private fun setupFormatToolbar() {
        binding.btnFormatBold.setOnClickListener { wrapSelection("**", "**", "加粗文字") }
        binding.btnFormatItalic.setOnClickListener { wrapSelection("*", "*", "斜体文字") }
        binding.btnFormatCode.setOnClickListener { wrapSelection("`", "`", "code") }
        binding.btnFormatCodeBlock.setOnClickListener {
            wrapSelection("\n```\n", "\n```\n", "// 代码块")
        }
        binding.btnFormatLink.setOnClickListener { insertLinkTemplate() }
        binding.btnFormatQuote.setOnClickListener { prefixLines("> ", "引用内容") }
        binding.btnFormatList.setOnClickListener { prefixLines("- ", "列表项") }
        binding.btnTogglePreview.setOnClickListener { togglePreview() }
        updatePreviewToggleButton()
    }

    private fun togglePreview() {
        isPreviewMode = !isPreviewMode
        if (isPreviewMode) {
            binding.layoutContent.visibility = View.GONE
            binding.cardPreview.visibility = View.VISIBLE
            refreshPreview()
            // Disable formatting buttons that don't make sense in preview mode
            setFormatButtonsEnabled(false)
        } else {
            binding.layoutContent.visibility = View.VISIBLE
            binding.cardPreview.visibility = View.GONE
            setFormatButtonsEnabled(true)
        }
        updatePreviewToggleButton()
    }

    private fun setFormatButtonsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.4f
        listOf(
            binding.btnFormatBold,
            binding.btnFormatItalic,
            binding.btnFormatCode,
            binding.btnFormatCodeBlock,
            binding.btnFormatLink,
            binding.btnFormatQuote,
            binding.btnFormatList
        ).forEach {
            it.isEnabled = enabled
            it.alpha = alpha
        }
    }

    private fun updatePreviewToggleButton() {
        binding.btnTogglePreview.text = if (isPreviewMode) "编辑" else "预览"
        binding.btnTogglePreview.setIconResource(
            if (isPreviewMode) R.drawable.ic_edit else R.drawable.ic_visibility
        )
    }

    private fun refreshPreview() {
        val md = markwon ?: return
        val raw = binding.etContent.text?.toString().orEmpty()
        val parts = mutableListOf<String>()
        if (raw.isNotBlank()) parts.add(raw)
        val uploaded = imageAdapter.getUploadedUrls()
        if (uploaded.isNotEmpty()) {
            parts.add(uploaded.joinToString("\n\n") { "![图片]($it)" })
        }
        val joined = parts.joinToString("\n\n")
        if (joined.isBlank()) {
            binding.tvPreview.alpha = 0.6f
            binding.tvPreview.text = "暂无可预览内容，请在正文中输入文字或添加图片。"
        } else {
            binding.tvPreview.alpha = 1f
            md.setMarkdown(binding.tvPreview, joined)
        }
    }

    private fun clearAttachment() {
        if (blockJson == null && paletteJson == null) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("移除附件？")
            .setMessage("将不再随讨论发布该附件。")
            .setPositiveButton("移除") { _, _ ->
                blockJson = null
                paletteJson = null
                renderAttachmentPreview()
                updateGuideCard()
                updateFormState()
                scheduleDraftSave()
            }
            .setNegativeButton("保留", null)
            .show()
    }

    private fun wrapSelection(prefix: String, suffix: String, placeholder: String) {
        val edit = binding.etContent
        val text = edit.text ?: Editable.Factory.getInstance().newEditable("")
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(start)
        val selected = text.substring(start, end)
        val inner = if (selected.isEmpty()) placeholder else selected
        val replacement = prefix + inner + suffix
        text.replace(start, end, replacement)
        edit.requestFocus()
        val cursorStart = start + prefix.length
        val cursorEnd = cursorStart + inner.length
        edit.setSelection(cursorStart, cursorEnd)
    }

    private fun prefixLines(prefix: String, placeholder: String) {
        val edit = binding.etContent
        val text = edit.text ?: Editable.Factory.getInstance().newEditable("")
        val rawStart = edit.selectionStart.coerceAtLeast(0)
        val rawEnd = edit.selectionEnd.coerceAtLeast(rawStart)
        val lineStart = text.toString().lastIndexOf('\n', (rawStart - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val selectedSection = text.substring(lineStart, rawEnd)
        val replacement = if (selectedSection.isBlank()) {
            prefix + placeholder
        } else {
            selectedSection.split('\n').joinToString("\n") { line ->
                if (line.isBlank()) line else prefix + line
            }
        }
        text.replace(lineStart, rawEnd, replacement)
        edit.requestFocus()
        edit.setSelection(lineStart + replacement.length)
    }

    private fun insertLinkTemplate() {
        val edit = binding.etContent
        val text = edit.text ?: Editable.Factory.getInstance().newEditable("")
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(start)
        val selected = text.substring(start, end).trim()
        val labelText = if (selected.isEmpty()) "链接文字" else selected
        val template = "[$labelText](https://)"
        text.replace(start, end, template)
        edit.requestFocus()
        // Place cursor inside the URL portion so the user can paste/type immediately.
        val urlStart = start + labelText.length + 3 // "[label](".length
        edit.setSelection(urlStart + "https://".length)
    }

    private fun bindCategories(categories: List<DiscussionCategory>) {
        discussionCategories = categories
        val group = binding.chipGroupCategory
        group.removeAllViews()
        if (categories.isEmpty()) {
            updateCategoryDescription()
            updateFormState()
            return
        }

        val selected = categories.firstOrNull { it.slug == selectedCategory } ?: categories.first()
        selectedCategory = selected.slug

        val inflater = LayoutInflater.from(group.context)
        categories.forEach { category ->
            val chip = inflater.inflate(R.layout.chip_discussion_category, group, false) as Chip
            chip.id = View.generateViewId()
            chip.tag = category.slug
            chip.text = category.name
            chip.setChipIconResource(DiscussionIconResolver.category(category.slug))
            chip.isChecked = category.slug == selectedCategory
            group.addView(chip)
        }
        updateCategoryDescription()
        updateFormState()
    }

    private fun updateCategoryDescription() {
        val current = discussionCategories.firstOrNull { it.slug == selectedCategory }
        binding.tvCategoryDescription.text = current?.description.orEmpty().ifEmpty {
            "为讨论选择一个分类，方便其他人发现"
        }
    }

    private fun bindExistingDiscussion(discussion: com.sknote.app.data.model.Discussion) {
        if (hasBoundExistingDiscussion) return
        hasBoundExistingDiscussion = true
        linkedArticleId = discussion.articleId
        selectedCategory = discussion.category.orEmpty().ifEmpty { selectedCategory }
        binding.etTitle.setText(discussion.title)

        val rawContent = discussion.content.orEmpty()
        // Extract embedded block/palette JSON so the editor shows clean text and a preview card,
        // matching how the detail screen renders the same content.
        blockJson = BlockShareHelper.extractBlockJson(rawContent)
        paletteJson = BlockShareHelper.extractPaletteJson(rawContent)
        val cleanContent = if (blockJson != null || paletteJson != null) {
            BlockShareHelper.getCleanContent(rawContent)
        } else {
            rawContent
        }
        binding.etContent.setText(cleanContent)

        renderAttachmentPreview()

        syncSelectedChip()
        updateCategoryDescription()
        updateGuideCard()
        updateFormState()
        captureInitialDraftState()
    }

    private fun renderAttachmentPreview() {
        val container = binding.blockPreviewContainer
        container.removeAllViews()
        val bj = blockJson
        val pj = paletteJson
        if (bj == null && pj == null) {
            binding.blockPreviewWrapper.visibility = View.GONE
            return
        }
        if (bj != null) {
            val preview = BlockShareHelper.createPreviewView(requireContext(), container, bj, showActions = false)
            container.addView(preview)
        }
        if (pj != null) {
            val preview = BlockShareHelper.createPalettePreviewView(requireContext(), container, pj, showActions = false)
            container.addView(preview)
        }
        binding.blockPreviewWrapper.visibility = View.VISIBLE
    }

    private fun syncSelectedChip() {
        val group = binding.chipGroupCategory
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.isChecked = chip.tag == selectedCategory
        }
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
        syncSelectedChip()
        updateCategoryDescription()

        renderAttachmentPreview()
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
        syncSelectedChip()
        updateCategoryDescription()

        renderAttachmentPreview()
        updateGuideCard()
        updateFormState()
    }

    private fun setupImagePicker() {
        imageAdapter = ImagePreviewAdapter(
            onRemove = { position ->
                imageAdapter.removeAt(position)
                updateImageUI()
            },
            onPreview = { item -> showImagePreviewDialog(item) }
        )

        binding.rvImages.apply {
            layoutManager = GridLayoutManager(context, 4)
            this.adapter = imageAdapter
        }

        binding.btnAddImage.setOnClickListener {
            if (imageAdapter.size() >= maxImages) {
                Snackbar.make(binding.root, "最多只能添加 $maxImages 张图片", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickMultipleImages.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
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
        val total = imageAdapter.size()
        binding.tvImageHint.text = when {
            total == 0 -> "最多可添加 $maxImages 张图片，自动压缩到 ≤ 1MB"
            !imageAdapter.allUploaded() -> "已选择 $total/$maxImages 张，正在上传…"
            else -> "已选择 $total/$maxImages 张图片"
        }
        if (isPreviewMode) refreshPreview()
        updateFormState()
    }

    private fun showImagePreviewDialog(item: ImageItem) {
        val ctx = context ?: return
        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val image = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { dialog.dismiss() }
        }
        Glide.with(ctx)
            .load(item.uploadedUrl ?: item.uri)
            .into(image)
        val container = FrameLayout(ctx).apply {
            setBackgroundColor(Color.BLACK)
            addView(image)
        }
        dialog.setContentView(container)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.show()
    }

    private fun uploadImage(uri: Uri) {
        val pos = imageAdapter.indexOfUri(uri)
        if (pos < 0) return
        imageAdapter.setUploading(pos, true)
        updateFormState()
        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { compressImage(uri, resolver) }
                val bytes = result.bytes
                if (bytes == null) {
                    val reason = result.error ?: "图片处理失败"
                    Snackbar.make(binding.root, reason, Snackbar.LENGTH_LONG).show()
                    val p = imageAdapter.indexOfUri(uri)
                    if (p >= 0) { imageAdapter.removeAt(p); updateImageUI() }
                    return@launch
                }

                val fileName = "img_${System.currentTimeMillis()}.jpg"
                val uploadResult = BackendImageUploader.upload(bytes, fileName)

                val p = imageAdapter.indexOfUri(uri)
                if (p < 0) return@launch
                if (uploadResult.success && uploadResult.url != null) {
                    imageAdapter.setUploadedUrl(p, uploadResult.url)
                    updateFormState()
                } else {
                    Snackbar.make(binding.root, uploadResult.error ?: "上传失败", Snackbar.LENGTH_SHORT).show()
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

    private data class CompressResult(val bytes: ByteArray?, val error: String? = null)

    private fun openImageStream(uri: Uri, resolver: ContentResolver): java.io.InputStream? {
        // 1) Standard openInputStream path.
        try {
            val direct = resolver.openInputStream(uri)
            if (direct != null) return direct
            Log.w(TAG, "openImageStream: openInputStream returned null for uri=$uri scheme=${uri.scheme} authority=${uri.authority}")
        } catch (e: Exception) {
            Log.w(TAG, "openImageStream: openInputStream threw for uri=$uri", e)
        }
        // 2) Fallback: ParcelFileDescriptor (legitimate openable providers).
        try {
            val fd = resolver.openFileDescriptor(uri, "r")
            if (fd != null) {
                return java.io.FileInputStream(fd.fileDescriptor)
            }
            Log.w(TAG, "openImageStream: openFileDescriptor returned null for uri=$uri")
        } catch (e: Exception) {
            Log.w(TAG, "openImageStream: openFileDescriptor threw for uri=$uri", e)
        }
        // 3) Fallback: typed asset file descriptor (some providers only implement this).
        try {
            val afd = resolver.openTypedAssetFileDescriptor(uri, "image/*", null)
                ?: resolver.openTypedAssetFileDescriptor(uri, "*/*", null)
            if (afd != null) {
                return afd.createInputStream()
            }
            Log.w(TAG, "openImageStream: openTypedAssetFileDescriptor returned null for uri=$uri")
        } catch (e: Exception) {
            Log.w(TAG, "openImageStream: openTypedAssetFileDescriptor threw for uri=$uri", e)
        }
        return null
    }

    private fun copyUriToCacheFile(uri: Uri, resolver: ContentResolver): java.io.File? {
        val stream = openImageStream(uri, resolver) ?: return null
        return try {
            val cacheDir = requireContext().cacheDir
            val file = java.io.File.createTempFile("pick_", ".bin", cacheDir)
            stream.use { input ->
                java.io.FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            Log.w(TAG, "copyUriToCacheFile: copy failed for $uri", e)
            null
        }
    }

    private fun compressImage(uri: Uri, resolver: ContentResolver): CompressResult {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            try {
                openImageStream(uri, resolver)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, boundsOptions)
                } ?: return CompressResult(null, "无法读取图片 [${uri.authority ?: uri.scheme}]，请改从系统相册选择")
            } catch (e: SecurityException) {
                Log.w(TAG, "compressImage: no permission to open $uri", e)
                return CompressResult(null, "没有访问该图片的权限 [${uri.authority}]，请从相册重新选择")
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                val mime = boundsOptions.outMimeType.orEmpty()
                Log.w(TAG, "compressImage: bounds decode failed for $uri mime=$mime")
                return CompressResult(null, if (mime.isNotEmpty()) "不支持的图片格式：$mime" else "不支持的图片格式")
            }

            var sampleSize = calculateInSampleSize(
                width = boundsOptions.outWidth,
                height = boundsOptions.outHeight,
                reqWidth = 1280,
                reqHeight = 1280
            )
            var preferredConfig = Bitmap.Config.ARGB_8888
            var original: Bitmap? = null
            var attempt = 0
            while (original == null && attempt < 4) {
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = preferredConfig
                }
                try {
                    original = openImageStream(uri, resolver)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                    }
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "compressImage: OOM at sampleSize=$sampleSize, retrying smaller", oom)
                }
                if (original == null) {
                    if (preferredConfig == Bitmap.Config.ARGB_8888) {
                        preferredConfig = Bitmap.Config.RGB_565
                    } else {
                        sampleSize = (sampleSize * 2).coerceAtMost(16)
                    }
                }
                attempt++
            }
            if (original == null) {
                return CompressResult(null, "图片太大或格式不受支持，请选择其他图片")
            }

            val rotated = try {
                applyExifOrientation(original, uri, resolver)
            } catch (e: Exception) {
                Log.w(TAG, "compressImage: applyExifOrientation failed", e)
                original
            }

            val maxWidth = 960
            val scaled = if (rotated.width > maxWidth) {
                val ratio = maxWidth.toFloat() / rotated.width
                Bitmap.createScaledBitmap(rotated, maxWidth, (rotated.height * ratio).toInt().coerceAtLeast(1), true)
            } else {
                rotated
            }

            val baos = ByteArrayOutputStream()
            var quality = 82
            do {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 8
            } while (baos.size() > 850 * 1024 && quality >= 42)

            if (scaled !== rotated) scaled.recycle()
            if (rotated !== original) rotated.recycle()
            original.recycle()
            CompressResult(baos.toByteArray())
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "compressImage: OOM", e)
            CompressResult(null, "手机内存不足，请选择较小的图片")
        } catch (e: Exception) {
            Log.w(TAG, "compressImage: unexpected error", e)
            CompressResult(null, "图片处理失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, uri: Uri, resolver: ContentResolver): Bitmap {
        val orientation = openImageStream(uri, resolver)?.use { inputStream ->
            ExifInterface(inputStream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }

        return if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
        if (isPreviewMode) togglePreview()
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

        if (selectedCategory.isBlank()) {
            Snackbar.make(binding.root, "请选择讨论分类", Snackbar.LENGTH_SHORT).show()
            isValid = false
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
                clearDraft()
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

    private fun draftPrefs(): android.content.SharedPreferences {
        return requireContext().getSharedPreferences("discussion_draft", Context.MODE_PRIVATE)
    }

    private fun draftKey(): String? {
        if (discussionId != null) return null
        return when {
            blockJson != null || paletteJson != null -> null
            linkedArticleId != null -> "draft_article_$linkedArticleId"
            else -> "draft_general"
        }
    }

    private fun scheduleDraftSave() {
        if (!draftRestored) return
        if (draftKey() == null) return
        // Debounce frequent text changes: persist 400ms after the last edit.
        draftSaveHandler.removeCallbacks(draftSaveRunnable)
        draftSaveHandler.postDelayed(draftSaveRunnable, 400)
    }

    private fun persistDraftNow() {
        if (_binding == null) return
        if (!draftRestored) return
        val key = draftKey() ?: return
        val title = binding.etTitle.text?.toString().orEmpty()
        val content = binding.etContent.text?.toString().orEmpty()
        val prefs = draftPrefs()
        if (title.isBlank() && content.isBlank()) {
            prefs.edit().remove("${key}_title").remove("${key}_content").remove("${key}_category").apply()
            return
        }
        prefs.edit()
            .putString("${key}_title", title)
            .putString("${key}_content", content)
            .putString("${key}_category", selectedCategory)
            .apply()
    }

    private fun clearDraft() {
        val key = draftKey() ?: return
        draftPrefs().edit()
            .remove("${key}_title")
            .remove("${key}_content")
            .remove("${key}_category")
            .apply()
    }

    private fun promptRestoreDraftIfNeeded() {
        val key = draftKey()
        if (key == null) {
            draftRestored = true
            return
        }
        val prefs = draftPrefs()
        val savedTitle = prefs.getString("${key}_title", "").orEmpty()
        val savedContent = prefs.getString("${key}_content", "").orEmpty()
        val savedCategory = prefs.getString("${key}_category", null)
        val hasCurrentInput = !binding.etTitle.text.isNullOrBlank() || !binding.etContent.text.isNullOrBlank()
        if (savedTitle.isBlank() && savedContent.isBlank()) {
            draftRestored = true
            return
        }
        if (hasCurrentInput) {
            // Don't overwrite incoming arguments-driven prefill (e.g. block share).
            draftRestored = true
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("恢复未发布草稿？")
            .setMessage("检测到上次未发布的草稿，是否载入？")
            .setCancelable(false)
            .setPositiveButton("载入草稿") { _, _ ->
                binding.etTitle.setText(savedTitle)
                binding.etContent.setText(savedContent)
                if (!savedCategory.isNullOrBlank()) {
                    selectedCategory = savedCategory
                    syncSelectedChip()
                    updateCategoryDescription()
                }
                draftRestored = true
                captureInitialDraftState()
                updateFormState()
                Snackbar.make(binding.root, "已载入草稿", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("丢弃草稿") { _, _ ->
                clearDraft()
                draftRestored = true
            }
            .show()
    }

    override fun onDestroyView() {
        // Flush any pending draft write before tearing down the view.
        draftSaveHandler.removeCallbacks(draftSaveRunnable)
        persistDraftNow()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "CreateDiscussion"
    }
}
