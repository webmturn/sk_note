package com.sknote.app.ui.reference

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.model.ReferenceItem
import com.sknote.app.databinding.FragmentReferenceDetailBinding
import io.noties.markwon.Markwon

class ReferenceDetailFragment : Fragment() {

    private var _binding: FragmentReferenceDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReferenceDetailViewModel by viewModels()
    private lateinit var markwon: Markwon
    private var referenceId: Long = 0
    private var currentRef: ReferenceItem? = null
    private val bookmarkPrefs by lazy {
        requireContext().getSharedPreferences("reference_bookmarks", Context.MODE_PRIVATE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReferenceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ReferenceData.init(requireContext())
        referenceId = arguments?.getLong("reference_id", 0L) ?: 0L
        if (referenceId <= 0L) return
        markwon = Markwon.create(requireContext())
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupToolbarMenu()
        setupCollapsibleSections()
        observeData()
        viewModel.loadReference(referenceId)
    }

    private fun setupToolbarMenu() {
        updateBookmarkIcon()
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_bookmark -> {
                    toggleBookmark()
                    true
                }
                R.id.action_share -> {
                    shareReference()
                    true
                }
                R.id.action_jump -> {
                    showQuickJumpDialog()
                    true
                }
                R.id.action_compare -> {
                    currentRef?.let { ref ->
                        if (ref.type == "block") {
                            BlockCompareDialog.newInstance(ref.id)
                                .show(childFragmentManager, "compare")
                        }
                    }
                    true
                }
                R.id.action_try_sw -> {
                    tryInSketchware()
                    true
                }
                else -> false
            }
        }
    }

    private fun tryInSketchware() {
        val ref = currentRef ?: return
        val codeText = buildString {
            appendLine("// ${ref.name} - ${ref.description}")
            if (ref.code.orEmpty().isNotEmpty()) {
                appendLine()
                appendLine(ref.code)
            }
            if (ref.example.isNotEmpty()) {
                appendLine()
                appendLine("// 示例:")
                appendLine(ref.example)
            }
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Sketchware代码", codeText))

        val packages = listOf("pro.sketchware", "com.besome.sketch.pro", "com.besome.sketch", "mod.agus.jcoderz.editor")
        var launched = false
        for (pkg in packages) {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
                launched = true
                break
            }
        }
        Snackbar.make(binding.root,
            if (launched) "代码已复制，已打开 Sketchware" else "代码已复制到剪贴板",
            Snackbar.LENGTH_LONG).show()
    }

    private fun toggleBookmark() {
        val ids = getBookmarkedIds().toMutableSet()
        if (ids.contains(referenceId)) {
            ids.remove(referenceId)
            Snackbar.make(binding.root, "已取消收藏", Snackbar.LENGTH_SHORT).show()
        } else {
            ids.add(referenceId)
            Snackbar.make(binding.root, "已收藏", Snackbar.LENGTH_SHORT).show()
        }
        bookmarkPrefs.edit().putStringSet("ids", ids.map { it.toString() }.toSet()).apply()
        updateBookmarkIcon()
    }

    private fun updateBookmarkIcon() {
        val isBookmarked = getBookmarkedIds().contains(referenceId)
        val icon = if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline
        binding.toolbar.menu.findItem(R.id.action_bookmark)?.setIcon(icon)
    }

    private fun getBookmarkedIds(): Set<Long> {
        return bookmarkPrefs.getStringSet("ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    private fun shareReference() {
        val ref = currentRef ?: return
        val text = buildString {
            append("[参考] ${ref.name}\n")
            append("类型: ${getTypeLabel(ref.type)} / ${ref.category}\n")
            if (ref.description.isNotEmpty()) append("\n${ref.description}\n")
            if (ref.example.isNotEmpty()) append("\n示例:\n${ref.example}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "分享参考"))
    }

    private fun setupCollapsibleSections() {
        binding.labelParams.setOnClickListener {
            val visible = binding.cardParams.visibility == View.VISIBLE
            binding.cardParams.visibility = if (visible) View.GONE else View.VISIBLE
            binding.ivParamsArrow.setImageResource(
                if (visible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
        }
        binding.labelUsage.setOnClickListener {
            val visible = binding.tvUsage.visibility == View.VISIBLE
            binding.tvUsage.visibility = if (visible) View.GONE else View.VISIBLE
            binding.ivUsageArrow.setImageResource(
                if (visible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
        }
        binding.labelExample.setOnClickListener {
            val visible = binding.cardExample.visibility == View.VISIBLE
            binding.cardExample.visibility = if (visible) View.GONE else View.VISIBLE
            binding.ivExampleArrow.setImageResource(
                if (visible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
        }
        binding.labelCode.setOnClickListener {
            val visible = binding.cardCode.visibility == View.VISIBLE
            binding.cardCode.visibility = if (visible) View.GONE else View.VISIBLE
            binding.ivCodeArrow.setImageResource(
                if (visible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
        }
        binding.labelManual.setOnClickListener {
            val visible = binding.layoutManual.visibility == View.VISIBLE
            binding.layoutManual.visibility = if (visible) View.GONE else View.VISIBLE
            binding.ivManualArrow.setImageResource(
                if (visible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
        }
        binding.labelWidgetProps.setOnClickListener {
            val visible = binding.layoutWidgetProps.visibility == View.VISIBLE
            binding.layoutWidgetProps.visibility = if (visible) View.GONE else View.VISIBLE
            binding.ivPropsArrow.setImageResource(
                if (visible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
        }
    }

    private fun showQuickJumpDialog() {
        val ref = currentRef ?: return
        val items = ReferenceData.getByTypeAndCategory(ref.type, ref.category)
        val names = items.map { it.name }.toTypedArray()
        val currentIdx = items.indexOfFirst { it.id == ref.id }
        AlertDialog.Builder(requireContext())
            .setTitle("${getTypeLabel(ref.type)} / ${ref.category}")
            .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                dialog.dismiss()
                val selected = items[which]
                if (selected.id != referenceId) {
                    referenceId = selected.id
                    updateBookmarkIcon()
                    viewModel.loadReference(selected.id)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getTypeLabel(type: String): String = ReferenceIcons.getTypeLabel(type)

    private fun fixMarkdownBreaks(text: String): String {
        return text.replace("\n", "  \n")
    }

    private fun getIconRes(item: ReferenceItem): Int = ReferenceIcons.getIconRes(item)

    private fun observeData() {
        viewModel.reference.observe(viewLifecycleOwner) { ref ->
            currentRef = ref

            // 重置可选区域（prev/next 切换时需要）
            binding.labelParams.visibility = View.GONE
            binding.cardParams.visibility = View.GONE
            binding.ivParamsArrow.setImageResource(R.drawable.ic_expand_less)
            binding.labelUsage.visibility = View.GONE
            binding.tvUsage.visibility = View.GONE
            binding.ivUsageArrow.setImageResource(R.drawable.ic_expand_less)
            binding.labelCode.visibility = View.GONE
            binding.cardCode.visibility = View.GONE
            binding.ivCodeArrow.setImageResource(R.drawable.ic_expand_less)
            binding.labelExample.visibility = View.GONE
            binding.cardExample.visibility = View.GONE
            binding.ivExampleArrow.setImageResource(R.drawable.ic_expand_less)
            binding.labelManual.visibility = View.GONE
            binding.layoutManual.visibility = View.GONE
            binding.layoutManual.removeAllViews()
            binding.ivManualArrow.setImageResource(R.drawable.ic_expand_less)
            binding.labelWidgetProps.visibility = View.GONE
            binding.layoutWidgetProps.visibility = View.GONE
            binding.layoutWidgetProps.removeAllViews()
            binding.ivPropsArrow.setImageResource(R.drawable.ic_expand_less)
            binding.labelEventMapping.visibility = View.GONE
            binding.layoutEventMapping.visibility = View.GONE
            binding.layoutEventMapping.removeAllViews()
            binding.labelAssociatedBlocks.visibility = View.GONE
            binding.layoutAssociatedBlocks.visibility = View.GONE
            binding.layoutAssociatedBlocks.removeAllViews()
            binding.labelRelated.visibility = View.GONE
            binding.layoutRelatedBlocks.visibility = View.GONE

            // 滚动到顶部
            (binding.root as? android.view.ViewGroup)?.let { root ->
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    if (child is androidx.core.widget.NestedScrollView) {
                        child.scrollTo(0, 0)
                    }
                }
            }

            binding.layoutContent.visibility = View.VISIBLE
            binding.tvName.text = ref.name
            binding.tvType.text = getTypeLabel(ref.type)
            binding.tvCategory.text = ref.category
            binding.toolbar.title = ref.name
            binding.toolbar.menu.findItem(R.id.action_compare)?.isVisible = (ref.type == "block")
            if (ref.type == "block") {
                binding.blockShapeDetail.visibility = View.VISIBLE
                binding.iconCardDetail.visibility = View.GONE
                try {
                    val color = if (ref.color.isNotEmpty()) Color.parseColor(ref.color) else 0xFFE1A92A.toInt()
                    binding.blockShapeDetail.blockColor = color
                } catch (_: Exception) {
                    binding.blockShapeDetail.blockColor = 0xFFE1A92A.toInt()
                }
                binding.blockShapeDetail.blockShape = ref.shape?.ifEmpty { "s" } ?: "s"
                val spec = ref.spec ?: ""
                if (spec.isNotEmpty()) {
                    binding.blockShapeDetail.blockSpec = spec
                    binding.blockShapeDetail.blockLabel = ""
                } else {
                    binding.blockShapeDetail.blockSpec = ""
                    binding.blockShapeDetail.blockLabel = ref.name ?: ""
                }
            } else {
                binding.blockShapeDetail.visibility = View.GONE
                binding.iconCardDetail.visibility = View.VISIBLE
                binding.ivIconDetail.setImageResource(getIconRes(ref))
                // Tint icon with block color
                val iconColor = try {
                    if (ref.color.isNotEmpty()) Color.parseColor(ref.color) else Color.parseColor("#FF1976D2")
                } catch (_: Exception) { Color.parseColor("#FF1976D2") }
                val tintedBg = Color.argb(25, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor))
                binding.iconCardDetail.setCardBackgroundColor(tintedBg)
                binding.ivIconDetail.setColorFilter(iconColor)
            }

            markwon.setMarkdown(binding.tvDescription, fixMarkdownBreaks(ref.description.ifEmpty { "暂无描述" }))

            // Set header accent color
            val blockColor = try {
                if (ref.color.isNotEmpty()) Color.parseColor(ref.color) else Color.parseColor("#FF1976D2")
            } catch (_: Exception) { Color.parseColor("#FF1976D2") }
            binding.headerAccent.setBackgroundColor(blockColor)

            if (ref.code.orEmpty().isNotEmpty()) {
                binding.labelCode.visibility = View.VISIBLE
                binding.cardCode.visibility = View.VISIBLE
                binding.tvCode.text = ref.code
            }

            if (ref.parameters.isNotEmpty()) {
                binding.labelParams.visibility = View.VISIBLE
                binding.cardParams.visibility = View.VISIBLE
                markwon.setMarkdown(binding.tvParameters, fixMarkdownBreaks(ref.parameters))
            }

            if (ref.usage.isNotEmpty()) {
                binding.labelUsage.visibility = View.VISIBLE
                binding.tvUsage.visibility = View.VISIBLE
                markwon.setMarkdown(binding.tvUsage, fixMarkdownBreaks(ref.usage))
            }

            if (ref.example.isNotEmpty()) {
                binding.labelExample.visibility = View.VISIBLE
                binding.cardExample.visibility = View.VISIBLE
                binding.tvExample.text = ref.example
                binding.btnCopyCode.setOnClickListener {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("示例代码", ref.example))
                    Snackbar.make(binding.root, "代码已复制到剪贴板", Snackbar.LENGTH_SHORT).show()
                }
            }

            loadManual(ref)
            loadWidgetProps(ref)
            loadEventMapping(ref)
            loadAssociatedBlocks(ref)
            loadRelatedItems(ref)
            setupNavBar(ref)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) binding.layoutContent.visibility = View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.layoutError.visibility = View.VISIBLE
                binding.tvError.text = error
                binding.layoutContent.visibility = View.GONE
            } else {
                binding.layoutError.visibility = View.GONE
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            viewModel.loadReference(referenceId)
        }
    }

    private fun setupNavBar(ref: ReferenceItem) {
        val allItems = ReferenceData.getByType(ref.type)
        val currentIndex = allItems.indexOfFirst { it.id == ref.id }
        if (currentIndex < 0 || allItems.size <= 1) {
            binding.layoutNavBar.visibility = View.GONE
            return
        }
        binding.layoutNavBar.visibility = View.VISIBLE

        val prevItem = if (currentIndex > 0) allItems[currentIndex - 1] else null
        val nextItem = if (currentIndex < allItems.size - 1) allItems[currentIndex + 1] else null

        binding.btnPrev.isEnabled = prevItem != null
        binding.btnPrev.text = prevItem?.name ?: "无"
        binding.btnPrev.alpha = if (prevItem != null) 1f else 0.4f
        binding.btnPrev.setOnClickListener {
            prevItem?.let {
                referenceId = it.id
                updateBookmarkIcon()
                viewModel.loadReference(it.id)
            }
        }

        binding.btnNext.isEnabled = nextItem != null
        binding.btnNext.text = nextItem?.name ?: "无"
        binding.btnNext.alpha = if (nextItem != null) 1f else 0.4f
        binding.btnNext.setOnClickListener {
            nextItem?.let {
                referenceId = it.id
                updateBookmarkIcon()
                viewModel.loadReference(it.id)
            }
        }
    }

    private val associatedBlockIds = mapOf(
        // Variable types
        1L to listOf(5, 6, 7),                                      // Number → set, increase, decrease
        2L to listOf(5),                                             // String → set
        3L to listOf(5),                                             // Boolean → set
        4L to listOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 17),        // Map → map ops
        20L to listOf(23, 24, 25, 26, 27, 28, 29, 30, 34, 35),     // List String → list ops
        21L to listOf(23, 24, 25, 26, 27, 28, 29, 30, 34, 35),     // List Number → list ops
        22L to listOf(23, 25, 26, 27, 28, 31, 32, 33),             // List Map → list + listMap ops
        // Components
        1000L to listOf(335, 336, 337),                              // SharedPreferences
        1001L to listOf(515, 516, 517, 518, 519, 520, 521, 522, 523, 524, 525, 526, 527, 528, 529, 530, 531, 532), // SQLite
        1002L to listOf(320, 321, 322, 323, 324, 325, 326),         // Intent
        1003L to listOf(261),                                        // Camera
        1004L to listOf(262),                                        // FilePicker
        1005L to listOf(260),                                        // Vibrator
        1006L to listOf(410, 411, 412, 413, 414, 415, 416, 417, 418, 419, 420), // BluetoothConnect
        1007L to listOf(445, 446),                                   // LocationManager
        1008L to listOf(280, 281, 282, 283, 284, 285, 286, 287, 505, 506), // Notification
        1010L to listOf(200, 201, 202),                              // Timer
        1011L to listOf(210, 211, 212, 213, 214, 215, 216),         // Calendar
        1013L to listOf(220, 221, 222, 223, 224, 225, 226),         // Dialog
        1014L to listOf(220, 221, 225, 226),                         // ProgressDialog
        1015L to listOf(225),                                        // TimePickerDialog
        1020L to listOf(240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250), // MediaPlayer
        1021L to listOf(255, 256, 257, 258),                         // SoundPool
        1022L to listOf(480, 481, 482, 483, 484, 485),              // TextToSpeech
        1023L to listOf(486, 487, 488),                              // SpeechToText
        1030L to listOf(425, 426, 427, 428),                         // RequestNetwork
        1040L to listOf(230, 231, 232, 233, 234, 235, 236),         // FirebaseDB
        1041L to listOf(430, 431, 432, 433, 434, 435, 436, 437),    // FirebaseAuth
        1042L to listOf(440, 441, 442),                              // FirebaseStorage
        1043L to listOf(295, 296, 297),                              // InterstitialAd
        1044L to listOf(295, 296, 297),                              // RewardedVideoAd
        1050L to listOf(265, 266, 267, 268, 269, 270, 271, 272, 273, 274, 510), // ObjectAnimator
        1060L to listOf(290, 291)                                    // Gyroscope
    )

    private fun loadEventMapping(ref: ReferenceItem) {
        binding.layoutEventMapping.removeAllViews()

        when (ref.type) {
            "widget", "component" -> {
                val events = ReferenceData.getEventsForItem(ref.id)
                if (events.isEmpty()) {
                    binding.labelEventMapping.visibility = View.GONE
                    binding.layoutEventMapping.visibility = View.GONE
                    return
                }
                binding.labelEventMapping.text = "可用事件"
                binding.labelEventMapping.visibility = View.VISIBLE
                binding.layoutEventMapping.visibility = View.VISIBLE

                val chipGroup = com.google.android.material.chip.ChipGroup(requireContext())
                for (event in events) {
                    val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                        text = event.name
                        textSize = 12f
                        isClickable = true
                        chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                            resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
                        )
                        setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
                        setOnClickListener {
                            val bundle = Bundle().apply { putLong("reference_id", event.id) }
                            findNavController().navigate(R.id.referenceDetailFragment, bundle)
                        }
                    }
                    chipGroup.addView(chip)
                }
                binding.layoutEventMapping.addView(chipGroup)
            }
            "event" -> {
                val targets = ReferenceData.getTargetsForEvent(ref.id)
                val isActivity = ReferenceData.isActivityEvent(ref.id)

                if (targets.isEmpty() && !isActivity) {
                    binding.labelEventMapping.visibility = View.GONE
                    binding.layoutEventMapping.visibility = View.GONE
                    return
                }

                binding.labelEventMapping.text = "适用于"
                binding.labelEventMapping.visibility = View.VISIBLE
                binding.layoutEventMapping.visibility = View.VISIBLE

                if (isActivity) {
                    val tv = android.widget.TextView(requireContext()).apply {
                        text = "Activity 级别事件 — 适用于所有 Activity"
                        textSize = 13f
                        setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                        setPadding(0, 4, 0, 4)
                    }
                    binding.layoutEventMapping.addView(tv)
                } else {
                    val flowLayout = com.google.android.material.chip.ChipGroup(requireContext())
                    for (target in targets) {
                        val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                            text = target.name
                            textSize = 12f
                            isClickable = true
                            chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                                resolveThemeColor(com.google.android.material.R.attr.colorTertiaryContainer)
                            )
                            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnTertiaryContainer))
                            setOnClickListener {
                                val bundle = Bundle().apply { putLong("reference_id", target.id) }
                                findNavController().navigate(R.id.referenceDetailFragment, bundle)
                            }
                        }
                        flowLayout.addView(chip)
                    }
                    binding.layoutEventMapping.addView(flowLayout)
                }
            }
            else -> {
                binding.labelEventMapping.visibility = View.GONE
                binding.layoutEventMapping.visibility = View.GONE
            }
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun loadWidgetProps(ref: ReferenceItem) {
        binding.layoutWidgetProps.removeAllViews()
        val props = ReferenceData.getWidgetProps(ref.id)
        if (props.isNullOrEmpty()) {
            binding.labelWidgetProps.visibility = View.GONE
            binding.layoutWidgetProps.visibility = View.GONE
            return
        }
        binding.labelWidgetProps.visibility = View.VISIBLE
        binding.layoutWidgetProps.visibility = View.VISIBLE

        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        // Table header
        val header = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceVariant))
        }
        header.addView(android.widget.TextView(ctx).apply {
            text = "属性"; textSize = 12f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2.5f)
        })
        header.addView(android.widget.TextView(ctx).apply {
            text = "类型"; textSize = 12f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        })
        header.addView(android.widget.TextView(ctx).apply {
            text = "说明"; textSize = 12f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
        })

        val card = com.google.android.material.card.MaterialCardView(ctx).apply {
            radius = dp(10).toFloat()
            strokeWidth = dp(1)
            strokeColor = resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurface))
            cardElevation = 0f
        }
        val tableLayout = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL }
        tableLayout.addView(header)

        props.forEachIndexed { idx, prop ->
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
                if (idx % 2 == 1) setBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceVariant) and 0x40FFFFFF or 0x10000000)
            }
            row.addView(android.widget.TextView(ctx).apply {
                text = prop.name; textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorPrimary))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2.5f)
            })
            row.addView(android.widget.TextView(ctx).apply {
                text = prop.type; textSize = 11f
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            })
            row.addView(android.widget.TextView(ctx).apply {
                text = prop.desc; textSize = 11f
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
            })
            tableLayout.addView(row)

            if (idx < props.size - 1) {
                tableLayout.addView(View(ctx).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant))
                    alpha = 0.3f
                })
            }
        }

        card.addView(tableLayout)
        binding.layoutWidgetProps.addView(card)
    }

    private fun loadManual(ref: ReferenceItem) {
        binding.layoutManual.removeAllViews()
        if (ref.type != "widget" && ref.type != "component") {
            binding.labelManual.visibility = View.GONE
            binding.layoutManual.visibility = View.GONE
            return
        }

        binding.labelManual.visibility = View.VISIBLE
        binding.layoutManual.visibility = View.VISIBLE

        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        var step = 0
        fun nextStep(): String = String(Character.toChars(0x2460 + step++))

        fun sectionTitle(number: String, title: String): android.widget.TextView {
            return android.widget.TextView(ctx).apply {
                text = "$number $title"
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
                setPadding(0, dp(12), 0, dp(6))
            }
        }

        fun bodyText(content: String): android.widget.TextView {
            return android.widget.TextView(ctx).apply {
                text = content
                textSize = 13f
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(8), dp(2), 0, dp(2))
                lineHeight = (textSize * 1.6).toInt()
            }
        }

        fun divider(): View {
            return View(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { topMargin = dp(8); bottomMargin = dp(4) }
                setBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant))
                alpha = 0.3f
            }
        }

        // ① Declaration
        val declaration = ReferenceData.getDeclaration(ref.id)
        if (declaration != null) {
            binding.layoutManual.addView(sectionTitle(nextStep(), "声明 / 添加"))
            binding.layoutManual.addView(bodyText(declaration))
            binding.layoutManual.addView(divider())
        }

        // ② Properties (from parameters)
        if (ref.parameters.isNotEmpty()) {
            binding.layoutManual.addView(sectionTitle(nextStep(), "属性与方法"))
            val tvParams = android.widget.TextView(ctx).apply {
                textSize = 13f
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(8), dp(2), 0, dp(2))
                lineHeight = (textSize * 1.6).toInt()
            }
            markwon.setMarkdown(tvParams, fixMarkdownBreaks(ref.parameters))
            binding.layoutManual.addView(tvParams)
            binding.layoutManual.addView(divider())
        }

        // ③ Usage
        if (ref.usage.isNotEmpty()) {
            binding.layoutManual.addView(sectionTitle(nextStep(), "基本用法"))
            val tvUsage = android.widget.TextView(ctx).apply {
                textSize = 13f
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(8), dp(2), 0, dp(2))
                lineHeight = (textSize * 1.6).toInt()
            }
            markwon.setMarkdown(tvUsage, fixMarkdownBreaks(ref.usage))
            binding.layoutManual.addView(tvUsage)
            binding.layoutManual.addView(divider())
        }

        // ④ Available Events
        val events = ReferenceData.getEventsForItem(ref.id)
        if (events.isNotEmpty()) {
            binding.layoutManual.addView(sectionTitle(nextStep(), "可用事件"))
            val chipGroup = com.google.android.material.chip.ChipGroup(ctx)
            for (event in events) {
                val chip = com.google.android.material.chip.Chip(ctx).apply {
                    text = event.name
                    textSize = 12f
                    isClickable = true
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
                    )
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
                    setOnClickListener {
                        val bundle = Bundle().apply { putLong("reference_id", event.id) }
                        findNavController().navigate(R.id.referenceDetailFragment, bundle)
                    }
                }
                chipGroup.addView(chip)
            }
            binding.layoutManual.addView(chipGroup)
            binding.layoutManual.addView(divider())
        }

        // ⑤ Operation Blocks
        val blockIds = associatedBlockIds[ref.id]
        if (!blockIds.isNullOrEmpty()) {
            val blocks = ReferenceData.getByIds(blockIds)
            if (blocks.isNotEmpty()) {
                binding.layoutManual.addView(sectionTitle(nextStep(), "操作积木块 (${blocks.size})"))
                val inflater = LayoutInflater.from(ctx)
                for (item in blocks) {
                    val itemView = inflater.inflate(R.layout.item_related_block, binding.layoutManual, false)
                    val blockShape = itemView.findViewById<BlockShapeView>(R.id.blockShapeRelated)
                    val tvName = itemView.findViewById<android.widget.TextView>(R.id.tvRelatedName)
                    val tvDesc = itemView.findViewById<android.widget.TextView>(R.id.tvRelatedDesc)
                    try {
                        val color = if (item.color.isNotEmpty()) Color.parseColor(item.color) else 0xFFE1A92A.toInt()
                        blockShape.blockColor = color
                    } catch (_: Exception) { blockShape.blockColor = 0xFFE1A92A.toInt() }
                    blockShape.blockShape = item.shape?.ifEmpty { "s" } ?: "s"
                    val spec = item.spec ?: ""
                    if (spec.isNotEmpty()) { blockShape.blockSpec = spec; blockShape.blockLabel = "" }
                    else { blockShape.blockSpec = ""; blockShape.blockLabel = item.name ?: "" }
                    tvName.text = item.name ?: ""
                    tvDesc.text = item.description.ifEmpty { "" }
                    itemView.setOnClickListener {
                        val bundle = Bundle().apply { putLong("reference_id", item.id) }
                        findNavController().navigate(R.id.referenceDetailFragment, bundle)
                    }
                    binding.layoutManual.addView(itemView)
                }
                binding.layoutManual.addView(divider())
            }
        }

        // ⑥ Complete Example
        if (ref.example.isNotEmpty()) {
            binding.layoutManual.addView(sectionTitle(nextStep(), "完整示例"))
            val codeCard = com.google.android.material.card.MaterialCardView(ctx).apply {
                radius = dp(12).toFloat()
                strokeWidth = 0
                setCardBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            val hsv = android.widget.HorizontalScrollView(ctx)
            val tvCode = android.widget.TextView(ctx).apply {
                text = ref.example
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }
            hsv.addView(tvCode)
            codeCard.addView(hsv)
            binding.layoutManual.addView(codeCard)
        }

        // If nothing was added, hide the section
        if (binding.layoutManual.childCount == 0) {
            binding.labelManual.visibility = View.GONE
            binding.layoutManual.visibility = View.GONE
        }
    }

    private fun loadAssociatedBlocks(ref: ReferenceItem) {
        val blockIds = associatedBlockIds[ref.id]
        binding.layoutAssociatedBlocks.removeAllViews()
        if (blockIds.isNullOrEmpty()) {
            binding.labelAssociatedBlocks.visibility = View.GONE
            binding.layoutAssociatedBlocks.visibility = View.GONE
            return
        }
        val blocks = ReferenceData.getByIds(blockIds)
        if (blocks.isEmpty()) {
            binding.labelAssociatedBlocks.visibility = View.GONE
            binding.layoutAssociatedBlocks.visibility = View.GONE
            return
        }
        binding.labelAssociatedBlocks.visibility = View.VISIBLE
        binding.layoutAssociatedBlocks.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(requireContext())
        for (item in blocks) {
            val itemView = inflater.inflate(R.layout.item_related_block, binding.layoutAssociatedBlocks, false)
            val blockShape = itemView.findViewById<BlockShapeView>(R.id.blockShapeRelated)
            val tvName = itemView.findViewById<android.widget.TextView>(R.id.tvRelatedName)
            val tvDesc = itemView.findViewById<android.widget.TextView>(R.id.tvRelatedDesc)

            try {
                val color = if (item.color.isNotEmpty()) Color.parseColor(item.color) else 0xFFE1A92A.toInt()
                blockShape.blockColor = color
            } catch (_: Exception) {
                blockShape.blockColor = 0xFFE1A92A.toInt()
            }
            blockShape.blockShape = item.shape?.ifEmpty { "s" } ?: "s"
            val spec = item.spec ?: ""
            if (spec.isNotEmpty()) {
                blockShape.blockSpec = spec
                blockShape.blockLabel = ""
            } else {
                blockShape.blockSpec = ""
                blockShape.blockLabel = item.name ?: ""
            }

            tvName.text = item.name ?: ""
            tvDesc.text = item.description.ifEmpty { "" }

            itemView.setOnClickListener {
                val bundle = Bundle().apply { putLong("reference_id", item.id) }
                findNavController().navigate(R.id.referenceDetailFragment, bundle)
            }
            binding.layoutAssociatedBlocks.addView(itemView)
        }
    }

    private fun loadRelatedItems(ref: ReferenceItem) {
        val ids = ref.relatedIds
        val related = if (!ids.isNullOrEmpty()) {
            ReferenceData.getByIds(ids)
        } else {
            ReferenceData.getByTypeAndCategory(ref.type, ref.category)
                .filter { it.id != ref.id }
                .take(5)
        }

        binding.layoutRelatedBlocks.removeAllViews()
        if (related.isNotEmpty()) {
            binding.labelRelated.visibility = View.VISIBLE
            binding.layoutRelatedBlocks.visibility = View.VISIBLE
            val inflater = LayoutInflater.from(requireContext())
            for (item in related) {
                val itemView = inflater.inflate(R.layout.item_related_block, binding.layoutRelatedBlocks, false)
                val blockShape = itemView.findViewById<BlockShapeView>(R.id.blockShapeRelated)
                val tvName = itemView.findViewById<android.widget.TextView>(R.id.tvRelatedName)
                val tvDesc = itemView.findViewById<android.widget.TextView>(R.id.tvRelatedDesc)

                try {
                    val color = if (item.color.isNotEmpty()) Color.parseColor(item.color) else 0xFFE1A92A.toInt()
                    blockShape.blockColor = color
                } catch (_: Exception) {
                    blockShape.blockColor = 0xFFE1A92A.toInt()
                }
                blockShape.blockShape = item.shape?.ifEmpty { "s" } ?: "s"
                val spec = item.spec ?: ""
                if (spec.isNotEmpty()) {
                    blockShape.blockSpec = spec
                    blockShape.blockLabel = ""
                } else {
                    blockShape.blockSpec = ""
                    blockShape.blockLabel = item.name ?: ""
                }

                tvName.text = item.name ?: ""
                tvDesc.text = item.description.ifEmpty { "" }

                itemView.setOnClickListener {
                    val bundle = Bundle().apply { putLong("reference_id", item.id) }
                    findNavController().navigate(R.id.referenceDetailFragment, bundle)
                }
                binding.layoutRelatedBlocks.addView(itemView)
            }
        } else {
            binding.labelRelated.visibility = View.GONE
            binding.layoutRelatedBlocks.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
