package com.sknote.app.ui.discussion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.SmmsUploader
import com.sknote.app.databinding.FragmentCreateDiscussionBinding
import com.sknote.app.ui.reference.BlockShapeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class CreateDiscussionFragment : Fragment() {

    private var _binding: FragmentCreateDiscussionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreateDiscussionViewModel by viewModels()

    private val categoryKeys = listOf("general", "question", "feedback", "bug", "feature")
    private val categoryLabels = listOf("综合", "提问", "反馈", "Bug", "功能建议")
    private var selectedCategory = "general"
    private var blockJson: JSONObject? = null
    private var paletteJson: JSONObject? = null

    private lateinit var imageAdapter: ImagePreviewAdapter
    private val maxImages = 9

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

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryLabels)
        binding.spinnerCategory.setAdapter(adapter)
        binding.spinnerCategory.setText(categoryLabels[0], false)
        binding.spinnerCategory.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = categoryKeys[position]
        }

        setupImagePicker()
        setupBlockShare()
        setupPaletteShare()

        binding.btnSubmit.setOnClickListener { submitDiscussion() }

        observeData()
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
        binding.etTitle.setText("🧩 ${bj.optString("name", "自定义积木块")}")

        // Use 'general' category for block shares
        selectedCategory = "general"
        binding.spinnerCategory.setText(categoryLabels[0], false)

        // Show block preview card
        val previewView = BlockShareHelper.createPreviewView(requireContext(), binding.blockPreviewContainer, bj, showActions = false)
        binding.blockPreviewContainer.addView(previewView)
        binding.blockPreviewContainer.visibility = View.VISIBLE
    }

    private fun setupPaletteShare() {
        val jsonStr = arguments?.getString("palette_json", "") ?: ""
        if (jsonStr.isEmpty()) return

        try {
            paletteJson = JSONObject(jsonStr)
        } catch (_: Exception) { return }

        val pj = paletteJson ?: return

        binding.toolbar.title = "分享调色板"
        binding.etTitle.setText("🎨 ${pj.optString("palette_name", "自定义调色板")}")

        selectedCategory = "general"
        binding.spinnerCategory.setText(categoryLabels[0], false)

        val previewView = BlockShareHelper.createPalettePreviewView(requireContext(), binding.blockPreviewContainer, pj, showActions = false)
        binding.blockPreviewContainer.addView(previewView)
        binding.blockPreviewContainer.visibility = View.VISIBLE
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
    }

    private fun uploadImage(uri: Uri) {
        val pos = imageAdapter.indexOfUri(uri)
        if (pos < 0) return
        imageAdapter.setUploading(pos, true)
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
                val result = SmmsUploader.upload(bytes, fileName)

                val p = imageAdapter.indexOfUri(uri)
                if (p < 0) return@launch
                if (result.success && result.url != null) {
                    imageAdapter.setUploadedUrl(p, result.url)
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
            val inputStream = resolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val maxWidth = 1200
            val scaled = if (original.width > maxWidth) {
                val ratio = maxWidth.toFloat() / original.width
                Bitmap.createScaledBitmap(original, maxWidth, (original.height * ratio).toInt(), true)
            } else {
                original
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            if (scaled !== original) scaled.recycle()
            original.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun submitDiscussion() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        if (title.isEmpty()) {
            Snackbar.make(binding.root, "请输入标题", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (content.isEmpty() && imageAdapter.size() == 0 && blockJson == null && paletteJson == null) {
            Snackbar.make(binding.root, "请输入内容", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (imageAdapter.size() > 0 && !imageAdapter.allUploaded()) {
            Snackbar.make(binding.root, "图片正在上传中，请稍候...", Snackbar.LENGTH_SHORT).show()
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

        viewModel.createDiscussion(title, finalContent, selectedCategory)
    }

    private fun observeData() {
        viewModel.success.observe(viewLifecycleOwner) { success ->
            if (success) {
                Snackbar.make(binding.root, "发布成功", Snackbar.LENGTH_SHORT).show()
                findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_discussions", true)
                findNavController().navigateUp()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnSubmit.isEnabled = !isLoading
            binding.btnSubmit.text = if (isLoading) "发布中..." else "发布讨论"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
