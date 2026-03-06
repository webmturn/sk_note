package com.sknote.app.ui.analyzer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentProjectAnalyzerBinding
import com.sknote.app.ui.reference.ReferenceData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectAnalyzerFragment : Fragment() {

    private var _binding: FragmentProjectAnalyzerBinding? = null
    private val binding get() = _binding!!
    private var allProjects = listOf<SkProject>()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            goBackToProjectList()
        }
    }

    data class SkProject(val id: String, val name: String, val packageName: String, val path: File)
    data class ViewNode(
        val type: String,
        val id: String,
        val children: MutableList<ViewNode> = mutableListOf(),
        val width: Int = -1,
        val height: Int = -2,
        val orientation: Int = 1,
        val gravity: Int = 0,
        val bgColor: Int = 0xFFFFFFFF.toInt(),
        val paddingLeft: Int = 0,
        val paddingTop: Int = 0,
        val paddingRight: Int = 0,
        val paddingBottom: Int = 0,
        val marginLeft: Int = 0,
        val marginTop: Int = 0,
        val marginRight: Int = 0,
        val marginBottom: Int = 0,
        val weight: Float = 0f,
        val text: String = "",
        val textSize: Int = 12,
        val textColor: Int = -0x1000000,
        val hint: String = "",
        val rawType: Int = -1
    )

    data class AnalysisData(
        val emptySections: MutableList<String> = mutableListOf(),
        val sectionBlockCounts: MutableMap<String, Int> = mutableMapOf(),
        val declaredComponentNames: MutableSet<String> = mutableSetOf(),
        val addSourceSections: MutableList<String> = mutableListOf(),
        val duplicatePatterns: MutableMap<String, Int> = mutableMapOf(),
        val viewWidgetIds: MutableSet<String> = mutableSetOf(),
        val viewWidgetTypes: MutableMap<String, String> = mutableMapOf(),
        val activityCount: Int = 0,
        val totalBlockCount: Int = 0,
        val deepNesting: MutableList<String> = mutableListOf(),
        val unusedListeners: MutableSet<String> = mutableSetOf()
    )

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadProjects() else showPermissionUI()
    }

    private val manageStorageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasStoragePermission()) loadProjects() else showPermissionUI()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProjectAnalyzerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ReferenceData.init(requireContext())

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.menu_analyzer)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_global_stats -> { showCrossProjectStats(); true }
                R.id.action_custom_blocks -> { findNavController().navigate(R.id.customBlocksFragment); true }
                R.id.action_backup -> { showBackupDialog(); true }
                R.id.action_launch_sw -> { launchSketchware(); true }
                else -> false
            }
        }

        binding.btnGrantPermission.setOnClickListener { requestStoragePermission() }

        if (hasStoragePermission()) {
            loadProjects()
            // Handle direct navigation from SwToolsFragment
            val showGlobal = arguments?.getBoolean("show_global_stats", false) == true
            val showBackup = arguments?.getBoolean("show_backup", false) == true
            if (showGlobal) {
                binding.root.post { showCrossProjectStats() }
            } else if (showBackup) {
                binding.root.post { showBackupDialog() }
            }
        } else {
            showPermissionUI()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            manageStorageLauncher.launch(intent)
        } else {
            permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showPermissionUI() {
        binding.layoutPermission.visibility = View.VISIBLE
        binding.rvProjects.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutAnalysis.visibility = View.GONE
    }

    private fun loadProjects() {
        binding.layoutPermission.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE

        val skDir = File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list")
        val dataDir = File(Environment.getExternalStorageDirectory(), ".sketchware/data")

        if (!skDir.exists() || !skDir.isDirectory) {
            binding.progressBar.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            return
        }

        val projects = mutableListOf<SkProject>()
        skDir.listFiles()?.forEach { projDir ->
            val projectFile = File(projDir, "project")
            if (projectFile.exists()) {
                try {
                    val json = JSONObject(projectFile.readText())
                    val id = projDir.name
                    val name = json.optString("my_ws_name", "未命名")
                    val pkg = json.optString("my_sc_pkg_name", "")
                    val projDataDir = File(dataDir, id)
                    projects.add(SkProject(id, name, pkg, projDataDir))
                } catch (_: Exception) {}
            }
        }

        projects.sortByDescending { it.id.toIntOrNull() ?: 0 }

        binding.progressBar.visibility = View.GONE
        if (projects.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            return
        }

        allProjects = projects
        binding.rvProjects.visibility = View.VISIBLE
        binding.rvProjects.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProjects.adapter = ProjectAdapter(projects) { project ->
            analyzeProject(project)
        }

        binding.toolbar.subtitle = "找到 ${projects.size} 个项目"
    }

    private fun goBackToProjectList() {
        binding.layoutAnalysis.visibility = View.GONE
        binding.rvProjects.visibility = View.VISIBLE
        binding.toolbar.subtitle = "扫描 Sketchware 项目"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        backCallback.isEnabled = false
    }

    private fun analyzeProject(project: SkProject) {
        binding.rvProjects.visibility = View.GONE
        binding.layoutAnalysis.visibility = View.VISIBLE
        binding.toolbar.subtitle = project.name
        backCallback.isEnabled = true
        binding.toolbar.setNavigationOnClickListener { goBackToProjectList() }

        val container = binding.layoutAnalysisContent
        container.removeAllViews()
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        // Project info card
        val infoCard = createCard()
        val infoLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        infoLayout.addView(createText("${project.name}", 17f, true, resolveColor(com.google.android.material.R.attr.colorOnSurface)))
        infoLayout.addView(createText("ID: ${project.id}  |  ${project.packageName}", 12f, false, resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)))
        infoCard.addView(infoLayout)
        container.addView(infoCard, marginParams(dp(0), dp(0), dp(0), dp(12)))

        // Parse logic files
        val logicDir = project.path
        val usedBlocks = mutableMapOf<String, Int>() // blockOpCode -> count
        val usedComponents = mutableSetOf<String>()
        val usedEvents = mutableSetOf<String>()
        val usedListeners = mutableSetOf<String>()
        val analysisData = AnalysisData()
        var totalBlockCount = 0

        if (logicDir.exists()) {
            logicDir.listFiles()?.forEach { file ->
                if (file.name == "logic" && file.isFile) {
                    parseLogicFile(file, usedBlocks, usedEvents, usedListeners, usedComponents, analysisData)
                    totalBlockCount = usedBlocks.values.sum()
                }
            }
        }

        // Stats summary
        val statsCard = createCard()
        val statsLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(16), dp(8), dp(16))
            gravity = android.view.Gravity.CENTER
        }
        statsLayout.addView(createStatItem("🧱", "积木块", "$totalBlockCount", resolveColor(com.google.android.material.R.attr.colorPrimary)))
        statsLayout.addView(createStatItem("📋", "种类", "${usedBlocks.size}", resolveColor(com.google.android.material.R.attr.colorTertiary)))
        statsLayout.addView(createStatItem("⚙\ufe0f", "组件", "${usedComponents.size}", resolveColor(com.google.android.material.R.attr.colorSecondary)))
        statsLayout.addView(createStatItem("⚡", "事件", "${usedEvents.size}", resolveColor(com.google.android.material.R.attr.colorError)))
        statsCard.addView(statsLayout)
        container.addView(statsCard, marginParams(dp(0), dp(0), dp(0), dp(12)))

        // Top used blocks
        if (usedBlocks.isNotEmpty()) {
            container.addView(createSectionTitle("📊 使用最多的积木块"))
            val topBlocks = usedBlocks.entries.sortedByDescending { it.value }.take(15)
            val maxCount = topBlocks.first().value.toFloat()
            val topCard = createCard()
            val topLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            topBlocks.forEachIndexed { idx, (opCode, count) ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(2), dp(4), dp(2), dp(4))
                }
                val labelRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                labelRow.addView(TextView(ctx).apply {
                    text = "${idx + 1}"
                    textSize = 11f
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    layoutParams = LinearLayout.LayoutParams(dp(20), LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                labelRow.addView(TextView(ctx).apply {
                    text = opCode
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        val items = ReferenceData.search(opCode)
                        if (items.isNotEmpty()) {
                            val bundle = Bundle().apply { putLong("reference_id", items[0].id) }
                            findNavController().navigate(R.id.referenceDetailFragment, bundle)
                        }
                    }
                })
                labelRow.addView(TextView(ctx).apply {
                    text = "${count}"
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                })
                row.addView(labelRow)
                // Progress bar
                val barContainer = FrameLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(4)
                    ).apply { topMargin = dp(3); bottomMargin = dp(2) }
                }
                barContainer.addView(View(ctx).apply {
                    setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceVariant))
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                    )
                })
                val ratio = count / maxCount
                barContainer.addView(View(ctx).apply {
                    val gd = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(2).toFloat()
                        setColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                    }
                    background = gd
                    alpha = 0.7f
                    layoutParams = FrameLayout.LayoutParams(
                        0, FrameLayout.LayoutParams.MATCH_PARENT
                    )
                })
                barContainer.post {
                    val bar = barContainer.getChildAt(1)
                    bar.layoutParams = FrameLayout.LayoutParams(
                        (barContainer.width * ratio).toInt().coerceAtLeast(dp(2)),
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                row.addView(barContainer)
                topLayout.addView(row)
            }
            topCard.addView(topLayout)
            container.addView(topCard, marginParams(dp(0), dp(0), dp(0), dp(12)))
        }

        // Used components
        if (usedComponents.isNotEmpty()) {
            container.addView(createSectionTitle("⚙\ufe0f 使用的组件"))
            val compCard = createCard()
            val chipGroup = ChipGroup(ctx).apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
            usedComponents.sorted().forEach { comp ->
                val chip = Chip(ctx).apply {
                    text = comp
                    textSize = 12f
                    isClickable = true
                    setOnClickListener {
                        val items = ReferenceData.search(comp)
                        if (items.isNotEmpty()) {
                            val bundle = Bundle().apply { putLong("reference_id", items[0].id) }
                            findNavController().navigate(R.id.referenceDetailFragment, bundle)
                        }
                    }
                }
                chipGroup.addView(chip)
            }
            compCard.addView(chipGroup)
            container.addView(compCard, marginParams(dp(0), dp(0), dp(0), dp(12)))
        }

        // Used events
        if (usedEvents.isNotEmpty()) {
            container.addView(createSectionTitle("⚡ 事件 (${usedEvents.size})"))
            val eventCard = createCard()
            val chipGroup = ChipGroup(ctx).apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
            usedEvents.sorted().forEach { event ->
                val chip = Chip(ctx).apply {
                    text = event
                    textSize = 12f
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        resolveColor(com.google.android.material.R.attr.colorErrorContainer)
                    )
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnErrorContainer))
                }
                chipGroup.addView(chip)
            }
            eventCard.addView(chipGroup)
            container.addView(eventCard, marginParams(dp(0), dp(0), dp(0), dp(12)))
        }

        // View structure
        val viewDir = project.path
        val viewNodes = mutableListOf<ViewNode>()
        val viewSections = mutableListOf<ViewSection>()
        if (viewDir.exists()) {
            viewDir.listFiles()?.forEach { file ->
                if (file.name == "view" && file.isFile) {
                    parseViewFile(file, viewNodes, viewSections)
                }
            }
        }
        if (viewSections.isNotEmpty()) {
            // Section title with toggle
            val titleRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(12), dp(4), dp(6))
            }
            titleRow.addView(TextView(ctx).apply {
                text = "View 结构 (${viewSections.size} 个页面)"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val toggleBtn = TextView(ctx).apply {
                text = "可视化预览"
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                setPadding(dp(10), dp(4), dp(10), dp(4))
                setBackgroundResource(android.R.drawable.list_selector_background)
            }
            titleRow.addView(toggleBtn)
            container.addView(titleRow)

            // Page selector chips
            val pageChipGroup = ChipGroup(ctx).apply {
                isSingleSelection = true
                setPadding(dp(4), 0, dp(4), dp(4))
            }
            container.addView(pageChipGroup)

            // Content cards - text tree & visual preview share a content area
            val viewCard = createCard()
            val viewLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            viewCard.addView(viewLayout)
            container.addView(viewCard, marginParams(dp(0), dp(0), dp(0), dp(12)))

            val previewCard = createCard()
            previewCard.visibility = View.GONE
            val previewScroll = android.widget.HorizontalScrollView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                isFillViewport = true
            }
            val previewInnerScroll = android.widget.ScrollView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(500)
                )
                isFillViewport = true
            }
            val previewContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundColor(0xFFF5F5F5.toInt())
            }
            previewInnerScroll.addView(previewContainer)
            previewScroll.addView(previewInnerScroll)
            previewCard.addView(previewScroll)
            container.addView(previewCard, marginParams(dp(0), dp(0), dp(0), dp(12)))

            var showingPreview = false

            // Render a specific page
            fun renderPage(section: ViewSection) {
                viewLayout.removeAllViews()
                section.roots.forEach { root -> buildViewTree(viewLayout, root, 0, dp) }
                previewContainer.removeAllViews()
                section.roots.forEach { root -> previewContainer.addView(buildVisualPreview(root, dp)) }
            }

            // Build page chips
            viewSections.forEachIndexed { index, section ->
                val chip = Chip(ctx).apply {
                    text = section.name
                    textSize = 12f
                    isCheckable = true
                    isChecked = (index == 0)
                    setOnClickListener { renderPage(section) }
                }
                pageChipGroup.addView(chip)
            }

            // Render first page by default
            if (viewSections.isNotEmpty()) renderPage(viewSections[0])

            // Toggle logic
            toggleBtn.setOnClickListener {
                showingPreview = !showingPreview
                if (showingPreview) {
                    viewCard.visibility = View.GONE
                    previewCard.visibility = View.VISIBLE
                    toggleBtn.text = "文字树"
                } else {
                    viewCard.visibility = View.VISIBLE
                    previewCard.visibility = View.GONE
                    toggleBtn.text = "可视化预览"
                }
            }
        }

        // Suggestions
        container.addView(createSectionTitle("💡 建议"))
        val sugCard = createCard()
        val sugLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        // Collect view widget IDs for cross-reference
        fun collectWidgetIds(node: ViewNode) {
            if (node.id.isNotEmpty()) {
                analysisData.viewWidgetIds.add(node.id)
                analysisData.viewWidgetTypes[node.id] = node.type
            }
            node.children.forEach { collectWidgetIds(it) }
        }
        viewNodes.forEach { collectWidgetIds(it) }

        val suggestions = generateSuggestions(usedBlocks, usedComponents, usedEvents, usedListeners, analysisData, totalBlockCount)
        if (suggestions.isEmpty()) {
            sugLayout.addView(TextView(ctx).apply {
                text = "✅ 项目结构良好，暂无建议"
                textSize = 14f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, dp(4), 0, dp(4))
            })
        } else {
            suggestions.forEach { sug ->
                val isWarning = sug.startsWith("⚠") || sug.startsWith("🚫")
                val isIndent = sug.startsWith("   ")
                val bgColor = when {
                    isIndent -> 0x00000000
                    isWarning -> 0x0CFF9800.toInt()
                    else -> 0x00000000
                }
                sugLayout.addView(TextView(ctx).apply {
                    text = if (isIndent) sug else "• $sug"
                    textSize = 13f
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                    setPadding(if (isIndent) dp(16) else dp(4), dp(6), dp(4), dp(6))
                    if (bgColor != 0) {
                        val gd = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = dp(6).toFloat()
                            setColor(bgColor)
                        }
                        background = gd
                    }
                })
            }
        }
        sugCard.addView(sugLayout)
        container.addView(sugCard, marginParams(dp(0), dp(0), dp(0), dp(16)))
    }

    private val componentTypeMap = mapOf(
        1 to "Intent", 2 to "SharedPreferences", 3 to "Calendar",
        4 to "Vibrator", 5 to "Timer", 6 to "Dialog",
        7 to "MediaPlayer", 8 to "SoundPool", 9 to "ObjectAnimator",
        10 to "Firebase", 11 to "FirebaseAuth", 12 to "FirebaseStorage",
        13 to "Camera", 14 to "FilePicker", 15 to "RequestNetwork",
        16 to "TextToSpeech", 17 to "SpeechToText", 18 to "BluetoothConnect",
        19 to "LocationManager", 20 to "ProgressDialog", 21 to "InterstitialAd",
        22 to "RewardedVideoAd", 23 to "Notification", 24 to "Gyroscope",
        25 to "FirebaseCloudMessage", 26 to "SQLite", 27 to "Fragment",
        28 to "TimePickerDialog", 29 to "DatePickerDialog"
    )

    private fun parseLogicFile(file: File, blocks: MutableMap<String, Int>, events: MutableSet<String>, listeners: MutableSet<String>, components: MutableSet<String>, extra: AnalysisData) {
        try {
            val lines = file.readLines()
            var section = ""
            var sectionBlockCount = 0
            var prevOpCode = ""
            var consecutiveCount = 1

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("@")) {
                    // Finalize previous section
                    if (section.isNotEmpty() && !section.endsWith("_components") && !section.endsWith("_events") && !section.endsWith("_var") && !section.endsWith("_list") && !section.endsWith("_func")) {
                        extra.sectionBlockCounts[section] = sectionBlockCount
                        if (sectionBlockCount == 0) {
                            extra.emptySections.add(section)
                        }
                    }
                    section = trimmed
                    sectionBlockCount = 0
                    prevOpCode = ""
                    consecutiveCount = 1

                    if (section.contains(".java_")) {
                        val eventSuffix = section.substringAfter(".java_")
                        val parts = eventSuffix.split("_")
                        if (parts.size >= 2) {
                            val target = parts.dropLast(1).joinToString("_")
                            val actualEvent = parts.last()
                            if (target.isNotEmpty()) events.add(target)
                            if (actualEvent.isNotEmpty()) events.add(actualEvent)
                        } else if (eventSuffix.isNotEmpty()) {
                            events.add(eventSuffix)
                        }
                    }
                    continue
                }
                if (trimmed.isEmpty()) continue

                try {
                    val json = JSONObject(trimmed)
                    if (section.endsWith("_components")) {
                        val compType = json.optInt("type", -1)
                        val compName = componentTypeMap[compType]
                        if (compName != null) {
                            components.add(compName)
                            val compId = json.optString("componentId", "")
                            if (compId.isNotEmpty()) extra.declaredComponentNames.add(compId)
                        }
                    } else {
                        val opCode = json.optString("opCode", "")
                        if (opCode.isNotEmpty()) {
                            blocks[opCode] = (blocks[opCode] ?: 0) + 1
                            sectionBlockCount++

                            // Track addSourceDirectly locations
                            if (opCode == "addSourceDirectly") {
                                val sectionName = section.removePrefix("@").substringAfter(".java_", "")
                                if (sectionName.isNotEmpty() && sectionName !in extra.addSourceSections) {
                                    extra.addSourceSections.add(sectionName)
                                }
                            }

                            // Track consecutive duplicate patterns
                            if (opCode == prevOpCode) {
                                consecutiveCount++
                                if (consecutiveCount >= 5) {
                                    extra.duplicatePatterns[opCode] = maxOf(extra.duplicatePatterns[opCode] ?: 0, consecutiveCount)
                                }
                            } else {
                                consecutiveCount = 1
                            }
                            prevOpCode = opCode
                        }
                        val typeName = json.optString("typeName", "")
                        if (typeName.isNotEmpty() && section.contains("_listener")) {
                            listeners.add(typeName)
                        }
                    }
                } catch (_: Exception) {}
            }

            // Finalize last section
            if (section.isNotEmpty() && !section.endsWith("_components") && !section.endsWith("_events") && !section.endsWith("_var") && !section.endsWith("_list") && !section.endsWith("_func")) {
                extra.sectionBlockCounts[section] = sectionBlockCount
                if (sectionBlockCount == 0) {
                    extra.emptySections.add(section)
                }
            }

        } catch (_: Exception) {}
    }

    private fun generateSuggestions(
        blocks: Map<String, Int>,
        components: Set<String>,
        events: Set<String>,
        listeners: Set<String>,
        extra: AnalysisData,
        totalBlockCount: Int
    ): List<String> {
        val suggestions = mutableListOf<String>()

        // ── 1. 缺失事件处理 ──
        val componentEventMap = mapOf(
            "Timer" to listOf("onTimerFinish" to "计时结束"),
            "RequestNetwork" to listOf("onResponse" to "响应成功", "onErrorResponse" to "响应失败"),
            "FirebaseDB" to listOf("onDataChange" to "数据变更", "onCancelled" to "操作取消"),
            "FirebaseAuth" to listOf("onSignInSuccess" to "登录成功", "onSignInFailed" to "登录失败"),
            "FirebaseStorage" to listOf("onUploadSuccess" to "上传成功", "onUploadFailed" to "上传失败", "onDownloadSuccess" to "下载成功"),
            "Camera" to listOf("onPictureTaken" to "拍照完成"),
            "FilePicker" to listOf("onFilesPicked" to "选择文件"),
            "SpeechToText" to listOf("onSpeechResult" to "识别结果"),
            "BluetoothConnect" to listOf("onBluetoothConnected" to "蓝牙连接"),
            "LocationManager" to listOf("onLocationChanged" to "位置变更"),
            "Gyroscope" to listOf("onSensorChanged" to "传感器变化")
        )
        for ((comp, requiredEvents) in componentEventMap) {
            if (components.contains(comp)) {
                for ((eventName, label) in requiredEvents) {
                    if (!events.contains(eventName)) {
                        suggestions.add("⚠ 使用了 $comp 但未处理 $eventName ($label) 事件")
                    }
                }
            }
        }

        // ── 2. 未使用的组件 ──
        // Component IDs like "timer1" appear in block parameters, not opCode names.
        // Check if any event section references this component (target_eventName pattern).
        val sectionTargets = extra.sectionBlockCounts.keys.mapNotNull { sec ->
            val suffix = sec.substringAfter(".java_", "")
            val parts = suffix.split("_")
            if (parts.size >= 2) parts.dropLast(1).joinToString("_") else null
        }.toSet()
        for (compName in extra.declaredComponentNames) {
            if (compName !in sectionTargets) {
                suggestions.add("🚫 声明了组件 '$compName' 但未在任何事件中引用，考虑移除")
            }
        }

        // ── 3. 空事件处理器 ──
        val emptyHandlers = extra.emptySections.filter { it.contains(".java_") }
        if (emptyHandlers.isNotEmpty()) {
            val count = emptyHandlers.size
            val examples = emptyHandlers.take(3).map { it.removePrefix("@").substringAfter(".java_") }
            suggestions.add("📭 $count 个空事件处理器: ${examples.joinToString(", ")}${if (count > 3) " ..." else ""}")
        }

        // ── 4. addSourceDirectly 详细分析 ──
        val addSrcCount = blocks["addSourceDirectly"] ?: 0
        if (addSrcCount > 0) {
            if (addSrcCount > 20) {
                suggestions.add("🔧 大量使用 addSourceDirectly (${addSrcCount}次)，项目可维护性降低")
            } else if (addSrcCount > 5) {
                suggestions.add("🔧 使用 addSourceDirectly ${addSrcCount}次，建议部分替换为内置积木块")
            }
            if (extra.addSourceSections.isNotEmpty()) {
                val locs = extra.addSourceSections.take(5).joinToString(", ")
                suggestions.add("   ↳ 出现在: $locs${if (extra.addSourceSections.size > 5) " ..." else ""}")
            }
        }

        // ── 5. 重复积木块模式 ──
        for ((opCode, maxRun) in extra.duplicatePatterns) {
            if (maxRun >= 5) {
                suggestions.add("🔁 '$opCode' 连续重复 ${maxRun}+ 次，考虑使用循环代替")
            }
        }

        // ── 6. 项目复杂度 ──
        if (blocks.size > 80) {
            suggestions.add("📊 项目使用了 ${blocks.size} 种积木块，复杂度很高，建议拆分 Activity")
        } else if (blocks.size > 50) {
            suggestions.add("📊 项目使用了 ${blocks.size} 种积木块，复杂度较高")
        }

        if (totalBlockCount > 500) {
            suggestions.add("📊 项目共 $totalBlockCount 个积木块，规模较大")
        }

        // ── 7. 无事件 ──
        if (events.isEmpty()) {
            suggestions.add("💡 项目未使用任何事件，考虑添加用户交互")
        }

        // ── 8. 错误处理检查 ──
        if (components.contains("RequestNetwork")) {
            if (events.contains("onResponse") && !events.contains("onErrorResponse")) {
                suggestions.add("⚠ RequestNetwork 只处理了成功回调，未处理 onErrorResponse 错误回调")
            }
        }
        if (blocks.containsKey("filePutFile") || blocks.containsKey("writeFile")) {
            val hasIsExist = blocks.containsKey("isExist") || blocks.containsKey("isDirectory")
            if (!hasIsExist) {
                suggestions.add("💡 使用了文件写入但未检查路径是否存在 (isExist/isDirectory)")
            }
        }

        // ── 9. UI 相关检查 ──
        val hasListView = extra.viewWidgetTypes.values.any { it == "ListView" || it == "RecyclerView" || it == "GridView" }
        if (hasListView && !blocks.containsKey("setListCustomViewData") && !blocks.containsKey("recyclerBindCustomView")) {
            if (!events.contains("onBindCustomView")) {
                suggestions.add("💡 使用了列表控件但未设置自定义列表项")
            }
        }

        val hasWebView = extra.viewWidgetTypes.values.any { it == "WebView" }
        if (hasWebView && !blocks.containsKey("webViewLoadUrl")) {
            suggestions.add("💡 添加了 WebView 但未使用 webViewLoadUrl 加载内容")
        }

        val hasEditText = extra.viewWidgetTypes.values.any { it == "EditText" }
        if (hasEditText && !events.contains("onTextChanged")) {
            // Only suggest if there's substantial logic
            if (totalBlockCount > 20) {
                suggestions.add("💡 使用了 EditText 但未监听 onTextChanged 事件")
            }
        }

        // ── 10. Activity 生命周期检查 ──
        if (totalBlockCount > 50 && !events.contains("onBackPressed")) {
            suggestions.add("💡 较大项目建议处理 onBackPressed 事件以控制退出行为")
        }
        if (components.contains("MediaPlayer") || components.contains("SoundPool")) {
            if (!events.contains("onPause") && !events.contains("onDestroy") && !events.contains("onStop")) {
                suggestions.add("⚠ 使用了媒体播放器但未在 onPause/onStop 中停止，可能导致后台继续播放")
            }
        }
        if (components.contains("LocationManager")) {
            if (!events.contains("onPause") && !events.contains("onStop")) {
                suggestions.add("⚠ 使用了 LocationManager 但未在 onPause/onStop 中停止定位，浪费电量")
            }
        }

        // ── 11. 安全建议 ──
        if (blocks.containsKey("intentSetAction") && !blocks.containsKey("intentSetPackage")) {
            suggestions.add("🔒 使用了隐式 Intent 但未设置 Package，可能有安全风险")
        }

        // ── 12. 性能建议 ──
        val foreverCount = blocks["forever"] ?: 0
        if (foreverCount > 0 && !blocks.containsKey("wait")) {
            suggestions.add("⚠ 使用了 forever 循环但未配合 wait 延时，可能导致 ANR")
        }

        val timerCount = blocks.entries.filter { it.key.contains("Timer", ignoreCase = true) }.sumOf { it.value }
        if (components.count { it == "Timer" } == 0 && timerCount > 10) {
            suggestions.add("💡 大量使用 Timer 相关积木块，确保合理管理计时器生命周期")
        }

        // ── 13. 代码质量 ──
        val setVarCount = (blocks["setVarInt"] ?: 0) + (blocks["setVarString"] ?: 0) + (blocks["setVarBoolean"] ?: 0)
        if (setVarCount > 100) {
            suggestions.add("📊 变量赋值操作 $setVarCount 次，项目逻辑可能过于冗长")
        }

        val toastCount = blocks["toast"] ?: 0
        if (toastCount > 15) {
            suggestions.add("💡 Toast 使用 $toastCount 次过多，考虑使用 Snackbar 或 Dialog 替代部分提示")
        }

        return suggestions
    }

    // Helper UI methods
    private fun createCard(): MaterialCardView {
        return MaterialCardView(requireContext()).apply {
            radius = (12 * resources.displayMetrics.density)
            strokeWidth = 0
            cardElevation = 0f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceVariant))
        }
    }

    private fun createSectionTitle(title: String): TextView {
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        return TextView(requireContext()).apply {
            text = title
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            setPadding(dp(4), dp(12), 0, dp(6))
        }
    }

    private fun createText(content: String, size: Float, bold: Boolean, color: Int): TextView {
        return TextView(requireContext()).apply {
            text = content
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(color)
        }
    }

    private fun createStatItem(icon: String, label: String, value: String, color: Int): LinearLayout {
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = icon
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, dp(4))
            })
            addView(TextView(context).apply {
                text = value; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setTextColor(color)
                gravity = android.view.Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = label; textSize = 11f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun marginParams(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(l, t, r, b)
        }
    }

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    // Simple project list adapter
    inner class ProjectAdapter(
        private val projects: List<SkProject>,
        private val onAnalyze: (SkProject) -> Unit
    ) : RecyclerView.Adapter<ProjectAdapter.VH>() {

        inner class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
            val card = MaterialCardView(parent.context).apply {
                radius = dp(12).toFloat()
                strokeWidth = 0
                cardElevation = 0f
                setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceVariant))
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                isClickable = true
                isFocusable = true
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                foreground = ContextCompat.getDrawable(context, outValue.resourceId)
            }
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val textLayout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textLayout.addView(TextView(parent.context).apply { id = android.R.id.text1; textSize = 15f; setTypeface(typeface, Typeface.BOLD) })
            textLayout.addView(TextView(parent.context).apply { id = android.R.id.text2; textSize = 12f })
            layout.addView(textLayout)
            layout.addView(TextView(parent.context).apply {
                text = "分析 →"
                textSize = 13f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            })
            card.addView(layout)
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val project = projects[position]
            holder.card.findViewById<TextView>(android.R.id.text1).apply {
                text = project.name
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            }
            holder.card.findViewById<TextView>(android.R.id.text2).apply {
                text = "ID: ${project.id}  |  ${project.packageName}"
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
            holder.card.setOnClickListener { onAnalyze(project) }
        }

        override fun getItemCount() = projects.size
    }

    data class ViewSection(val name: String, val roots: MutableList<ViewNode> = mutableListOf())

    private fun parseViewFile(file: File, allRoots: MutableList<ViewNode>, sections: MutableList<ViewSection>) {
        try {
            val lines = file.readLines()
            val nodeMap = mutableMapOf<String, ViewNode>() // id -> node
            var currentSection: ViewSection? = null

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("@")) {
                    // New layout section
                    val sectionName = trimmed.removePrefix("@").removeSuffix(".xml")
                    currentSection = ViewSection(sectionName)
                    sections.add(currentSection)
                    nodeMap.clear()
                    continue
                }
                try {
                    val json = JSONObject(trimmed)
                    val id = json.optString("id", "")
                    val rawType = json.optInt("type", -1)
                    val type = when (rawType) {
                        0 -> "LinearLayout(V)"
                        1 -> "LinearLayout(H)"
                        2 -> "ScrollView(V)"
                        3 -> "ScrollView(H)"
                        4 -> "Button"
                        5 -> "TextView"
                        6 -> "EditText"
                        7 -> "ImageView"
                        8 -> "WebView"
                        9 -> "ProgressBar"
                        10 -> "ListView"
                        11 -> "Spinner"
                        12 -> "CheckBox"
                        13 -> "Switch"
                        14 -> "SeekBar"
                        15 -> "CalendarView"
                        16 -> "Fab"
                        17 -> "AdView"
                        18 -> "MapView"
                        19 -> "RadioButton"
                        20 -> "RatingBar"
                        21 -> "VideoView"
                        22 -> "SearchView"
                        23 -> "ImageButton"
                        24 -> "GridView"
                        25 -> "RecyclerView"
                        26 -> "ViewPager"
                        27 -> "CardView"
                        28 -> "TabLayout"
                        29 -> "BottomNav"
                        30 -> "CollapsingToolbar"
                        31 -> "SwipeRefresh"
                        32 -> "DatePicker"
                        33 -> "TimePicker"
                        34 -> "CircleImageView"
                        35 -> "TextInputLayout"
                        else -> "View($rawType)"
                    }
                    val parentId = json.optString("parent", "")
                    val layout = json.optJSONObject("layout")
                    val textObj = json.optJSONObject("text")
                    val node = ViewNode(
                        type = type, id = id,
                        width = layout?.optInt("width", -1) ?: -1,
                        height = layout?.optInt("height", -2) ?: -2,
                        orientation = layout?.optInt("orientation", 1) ?: 1,
                        gravity = layout?.optInt("gravity", 0) ?: 0,
                        bgColor = layout?.optInt("backgroundColor", 0xFFFFFFFF.toInt()) ?: 0xFFFFFFFF.toInt(),
                        paddingLeft = layout?.optInt("paddingLeft", 0) ?: 0,
                        paddingTop = layout?.optInt("paddingTop", 0) ?: 0,
                        paddingRight = layout?.optInt("paddingRight", 0) ?: 0,
                        paddingBottom = layout?.optInt("paddingBottom", 0) ?: 0,
                        marginLeft = layout?.optInt("marginLeft", 0) ?: 0,
                        marginTop = layout?.optInt("marginTop", 0) ?: 0,
                        marginRight = layout?.optInt("marginRight", 0) ?: 0,
                        marginBottom = layout?.optInt("marginBottom", 0) ?: 0,
                        weight = layout?.optDouble("weight", 0.0)?.toFloat() ?: 0f,
                        text = textObj?.optString("text", "") ?: "",
                        textSize = textObj?.optInt("textSize", 12) ?: 12,
                        textColor = textObj?.optInt("textColor", -0x1000000) ?: -0x1000000,
                        hint = textObj?.optString("hint", "") ?: "",
                        rawType = rawType
                    )
                    nodeMap[id] = node

                    if (parentId.isEmpty() || parentId == "root" || !nodeMap.containsKey(parentId)) {
                        allRoots.add(node)
                        currentSection?.roots?.add(node)
                    } else {
                        nodeMap[parentId]?.children?.add(node)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // Color palette for containers — distinct colors by depth with better contrast
    private val containerColors = intArrayOf(
        0x302196F3.toInt(), 0x304CAF50.toInt(), 0x30FF9800.toInt(),
        0x309C27B0.toInt(), 0x30E91E63.toInt(), 0x3000BCD4.toInt()
    )

    private fun buildVisualPreview(node: ViewNode, dp: (Int) -> Int, depth: Int = 0): View {
        val ctx = requireContext()
        val isContainer = node.type.contains("Layout") || node.type.contains("Scroll") ||
            node.type.contains("Card") || node.type.contains("Swipe") ||
            node.type.contains("ViewPager") || node.type.contains("Tab")

        // Max depth guard
        if (depth > 10) {
            return TextView(ctx).apply {
                text = "... (\u5d4c\u5957\u8fc7\u6df1)"
                textSize = 11f
                setTextColor(0xFF999999.toInt())
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
        }

        // Determine preview dimensions
        val previewW = when (node.width) {
            -1 -> LinearLayout.LayoutParams.MATCH_PARENT
            -2 -> LinearLayout.LayoutParams.WRAP_CONTENT
            else -> dp(node.width.coerceAtLeast(24))
        }
        val previewH = when (node.height) {
            -1 -> LinearLayout.LayoutParams.WRAP_CONTENT
            -2 -> LinearLayout.LayoutParams.WRAP_CONTENT
            else -> dp(node.height.coerceAtLeast(16))
        }

        val lp = LinearLayout.LayoutParams(previewW, previewH).apply {
            setMargins(
                dp(node.marginLeft.coerceAtLeast(1)),
                dp(node.marginTop.coerceAtLeast(1)),
                dp(node.marginRight.coerceAtLeast(1)),
                dp(node.marginBottom.coerceAtLeast(1))
            )
            if (node.weight > 0f) weight = node.weight
        }

        val view: View = if (isContainer) {
            // Determine orientation: node.orientation 0=horizontal, 1=vertical, -1=default vertical
            val orient = if (node.orientation == 0) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

            val container = LinearLayout(ctx).apply {
                orientation = orient
                if (node.gravity != 0) gravity = node.gravity
            }

            // Apply container styling
            val bgColor = containerColors[depth % containerColors.size]
            container.setBackgroundColor(bgColor)
            container.setPadding(
                dp(node.paddingLeft.coerceAtLeast(2)),
                dp(node.paddingTop.coerceAtLeast(2)),
                dp(node.paddingRight.coerceAtLeast(2)),
                dp(node.paddingBottom.coerceAtLeast(2))
            )

            // Add ID label at top of container
            val label = TextView(ctx).apply {
                text = "${node.type.substringBefore("(")} #${node.id}"
                textSize = 10f
                setTextColor(0xAA000000.toInt())
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setBackgroundColor(0x15000000.toInt())
            }
            container.addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            // Recursively add children
            node.children.forEach { child ->
                container.addView(buildVisualPreview(child, dp, depth + 1))
            }

            // Ensure container has min size for visibility
            container.minimumWidth = dp(60)
            container.minimumHeight = dp(28)
            container
        } else {
            // Leaf widget — render approximation
            buildWidgetPreview(node, dp)
        }

        // Apply border for all views
        val wrapper = FrameLayout(ctx).apply {
            val gd = android.graphics.drawable.GradientDrawable().apply {
                setStroke(1, 0x40000000.toInt())
                cornerRadius = dp(2).toFloat()
                if (!isContainer) {
                    // Use white-ish background for non-container views
                    val alpha = (node.bgColor ushr 24) and 0xFF
                    if (alpha > 0 && node.bgColor != 0xFFFFFFFF.toInt()) {
                        setColor(node.bgColor)
                    } else {
                        setColor(0xFFFAFAFA.toInt())
                    }
                }
            }
            background = gd
        }
        wrapper.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        wrapper.layoutParams = lp

        // Tooltip on click — show full info
        wrapper.setOnClickListener {
            val sizeStr = when (node.width) {
                -1 -> "match_parent"
                -2 -> "wrap_content"
                else -> "${node.width}dp"
            } + " × " + when (node.height) {
                -1 -> "match_parent"
                -2 -> "wrap_content"
                else -> "${node.height}dp"
            }
            val info = buildString {
                append("${node.type}\n")
                append("ID: ${node.id}\n")
                append("尺寸: $sizeStr\n")
                if (node.text.isNotEmpty()) append("文字: ${node.text}\n")
                if (node.hint.isNotEmpty()) append("提示: ${node.hint}\n")
                if (isContainer) {
                    append("方向: ${if (node.orientation == 0) "水平" else "垂直"}\n")
                    append("子控件: ${node.children.size}\n")
                }
                if (node.paddingLeft + node.paddingTop + node.paddingRight + node.paddingBottom > 0) {
                    append("内边距: ${node.paddingLeft},${node.paddingTop},${node.paddingRight},${node.paddingBottom}\n")
                }
                if (node.marginLeft + node.marginTop + node.marginRight + node.marginBottom > 0) {
                    append("外边距: ${node.marginLeft},${node.marginTop},${node.marginRight},${node.marginBottom}\n")
                }
            }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle(node.id.ifEmpty { node.type })
                .setMessage(info)
                .setPositiveButton("确定", null)
                .show()
        }

        return wrapper
    }

    private fun buildWidgetPreview(node: ViewNode, dp: (Int) -> Int): View {
        val ctx = requireContext()
        return when {
            node.type == "Button" || node.type == "Fab" || node.type == "ImageButton" -> {
                TextView(ctx).apply {
                    text = if (node.text.isNotEmpty()) node.text else node.id
                    textSize = 13f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xFF1565C0.toInt())
                    setBackgroundColor(0xFFE3F2FD.toInt())
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    minimumHeight = dp(36)
                    minimumWidth = dp(60)
                }
            }
            node.type == "TextView" -> {
                TextView(ctx).apply {
                    text = if (node.text.isNotEmpty()) node.text else "TextView"
                    textSize = node.textSize.toFloat().coerceIn(10f, 18f)
                    setTextColor(node.textColor)
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    minimumHeight = dp(24)
                }
            }
            node.type == "EditText" -> {
                TextView(ctx).apply {
                    text = if (node.hint.isNotEmpty()) node.hint else if (node.text.isNotEmpty()) node.text else "EditText"
                    textSize = 13f
                    setTextColor(0xFF999999.toInt())
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    val gd = android.graphics.drawable.GradientDrawable().apply {
                        setStroke(dp(1), 0xFF999999.toInt())
                        cornerRadius = dp(4).toFloat()
                        setColor(0xFFFFFFFF.toInt())
                    }
                    background = gd
                    minimumHeight = dp(36)
                }
            }
            node.type == "ImageView" || node.type == "CircleImageView" -> {
                TextView(ctx).apply {
                    text = "🖼"
                    textSize = 20f
                    gravity = android.view.Gravity.CENTER
                    setBackgroundColor(0xFFE0E0E0.toInt())
                    minimumWidth = dp(48)
                    minimumHeight = dp(48)
                }
            }
            node.type == "CheckBox" || node.type == "RadioButton" -> {
                TextView(ctx).apply {
                    text = "${if (node.type == "CheckBox") "☐" else "○"} ${if (node.text.isNotEmpty()) node.text else node.id}"
                    textSize = 13f
                    setTextColor(0xFF333333.toInt())
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    minimumHeight = dp(32)
                }
            }
            node.type == "Switch" -> {
                TextView(ctx).apply {
                    text = "⊙ ${if (node.text.isNotEmpty()) node.text else "Switch"}"
                    textSize = 13f
                    setTextColor(0xFF333333.toInt())
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    minimumHeight = dp(32)
                }
            }
            node.type == "SeekBar" || node.type == "ProgressBar" || node.type == "RatingBar" -> {
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    addView(TextView(ctx).apply {
                        text = node.type
                        textSize = 10f
                        setTextColor(0xFF666666.toInt())
                    })
                    addView(View(ctx).apply {
                        setBackgroundColor(0xFFBBDEFB.toInt())
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
                        ).apply { topMargin = dp(2) }
                    })
                    minimumHeight = dp(28)
                }
            }
            node.type == "Spinner" -> {
                TextView(ctx).apply {
                    text = "▼ ${node.id}"
                    textSize = 13f
                    setTextColor(0xFF333333.toInt())
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    setBackgroundColor(0xFFF5F5F5.toInt())
                    minimumHeight = dp(36)
                }
            }
            node.type == "WebView" -> {
                TextView(ctx).apply {
                    text = "🌐 WebView\n${node.id}"
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xFF666666.toInt())
                    setBackgroundColor(0xFFF0F0F0.toInt())
                    setPadding(dp(12), dp(20), dp(12), dp(20))
                    minimumHeight = dp(60)
                }
            }
            node.type == "ListView" || node.type == "RecyclerView" || node.type == "GridView" -> {
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(0xFFF8F8F8.toInt())
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    addView(TextView(ctx).apply {
                        text = "${node.type} #${node.id}"
                        textSize = 10f
                        setTextColor(0xFF666666.toInt())
                        setPadding(0, 0, 0, dp(4))
                    })
                    for (i in 1..3) {
                        addView(TextView(ctx).apply {
                            text = "  Item $i"
                            textSize = 12f
                            setTextColor(0xFF888888.toInt())
                            setPadding(dp(8), dp(4), 0, dp(4))
                        })
                        if (i < 3) addView(View(ctx).apply {
                            setBackgroundColor(0xFFE0E0E0.toInt())
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                            )
                        })
                    }
                    minimumHeight = dp(80)
                }
            }
            node.type == "MapView" -> {
                TextView(ctx).apply {
                    text = "🗺 MapView\n${node.id}"
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xFF666666.toInt())
                    setBackgroundColor(0xFFE8F5E9.toInt())
                    setPadding(dp(12), dp(20), dp(12), dp(20))
                    minimumHeight = dp(60)
                }
            }
            node.type == "SearchView" -> {
                TextView(ctx).apply {
                    text = "🔍 搜索..."
                    textSize = 13f
                    setTextColor(0xFF999999.toInt())
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setBackgroundColor(0xFFF5F5F5.toInt())
                    minimumHeight = dp(36)
                }
            }
            else -> {
                // Generic fallback
                TextView(ctx).apply {
                    text = "${node.type}\n#${node.id}"
                    textSize = 11f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xFF888888.toInt())
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    minimumHeight = dp(28)
                }
            }
        }
    }

    private fun buildViewTree(container: LinearLayout, node: ViewNode, depth: Int, dp: (Int) -> Int) {
        val ctx = requireContext()
        val prefix = "│  ".repeat(maxOf(0, depth - 1)) + if (depth > 0) "├─ " else ""
        val isLayout = node.type.contains("Layout") || node.type.contains("Scroll") ||
            node.type.contains("Card") || node.type.contains("Swipe") || node.type.contains("Tab")

        val row = TextView(ctx).apply {
            text = "$prefix${node.type}  ${if (node.id.isNotEmpty()) "#${node.id}" else ""}"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(if (isLayout) resolveColor(com.google.android.material.R.attr.colorPrimary)
                else resolveColor(com.google.android.material.R.attr.colorOnSurface))
            setPadding(dp(4), dp(2), 0, dp(2))
        }
        container.addView(row)
        node.children.forEach { child -> buildViewTree(container, child, depth + 1, dp) }
    }

    private fun showCrossProjectStats() {
        if (allProjects.isEmpty()) {
            Snackbar.make(binding.root, "没有找到项目", Snackbar.LENGTH_SHORT).show()
            return
        }

        binding.rvProjects.visibility = View.GONE
        binding.layoutAnalysis.visibility = View.VISIBLE
        binding.toolbar.subtitle = "全局统计 (${allProjects.size} 个项目)"
        binding.toolbar.setNavigationOnClickListener {
            binding.layoutAnalysis.visibility = View.GONE
            binding.rvProjects.visibility = View.VISIBLE
            binding.toolbar.subtitle = "找到 ${allProjects.size} 个项目"
            binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        }

        val container = binding.layoutAnalysisContent
        container.removeAllViews()
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        val globalBlocks = mutableMapOf<String, Int>()
        val globalComponents = mutableMapOf<String, Int>()
        var totalBlocks = 0

        allProjects.forEach { project ->
            if (project.path.exists()) {
                project.path.listFiles()?.forEach { file ->
                    if (file.name == "logic" && file.isFile) {
                        val blocks = mutableMapOf<String, Int>()
                        val comps = mutableSetOf<String>()
                        parseLogicFile(file, blocks, mutableSetOf(), mutableSetOf(), comps, AnalysisData())
                        blocks.forEach { (k, v) ->
                            globalBlocks[k] = (globalBlocks[k] ?: 0) + v
                            totalBlocks += v
                        }
                        comps.forEach { globalComponents[it] = (globalComponents[it] ?: 0) + 1 }
                    }
                }
            }
        }

        // Summary
        container.addView(createSectionTitle("全局概览"))
        val statsCard = createCard()
        val statsLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        statsLayout.addView(createStatItem("🧱", "总积木块", "$totalBlocks", resolveColor(com.google.android.material.R.attr.colorPrimary)))
        statsLayout.addView(createStatItem("📋", "种类", "${globalBlocks.size}", resolveColor(com.google.android.material.R.attr.colorTertiary)))
        statsLayout.addView(createStatItem("📁", "项目", "${allProjects.size}", resolveColor(com.google.android.material.R.attr.colorSecondary)))
        statsLayout.addView(createStatItem("⚙\ufe0f", "组件", "${globalComponents.size}", resolveColor(com.google.android.material.R.attr.colorError)))
        statsCard.addView(statsLayout)
        container.addView(statsCard, marginParams(dp(0), dp(0), dp(0), dp(12)))

        // Most used blocks across all projects
        if (globalBlocks.isNotEmpty()) {
            container.addView(createSectionTitle("最常用积木块 TOP 20"))
            val topCard = createCard()
            val topLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            globalBlocks.entries.sortedByDescending { it.value }.take(20).forEachIndexed { idx, (op, count) ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(5), dp(4), dp(5))
                }
                row.addView(TextView(ctx).apply {
                    text = "${idx + 1}."; textSize = 12f
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    layoutParams = LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                row.addView(TextView(ctx).apply {
                    text = op; textSize = 13f; typeface = Typeface.MONOSPACE
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(ctx).apply {
                    text = "${count}次"; textSize = 12f
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                })
                topLayout.addView(row)
            }
            topCard.addView(topLayout)
            container.addView(topCard, marginParams(dp(0), dp(0), dp(0), dp(12)))
        }

        // Never used blocks (from reference data)
        val allRefBlocks = ReferenceData.getByType("block").map { it.name }.toSet()
        val neverUsed = allRefBlocks.filter { !globalBlocks.containsKey(it) }.sorted()
        if (neverUsed.isNotEmpty()) {
            container.addView(createSectionTitle("从未使用的积木块 (${neverUsed.size})"))
            val neverCard = createCard()
            val chipGroup = ChipGroup(ctx).apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
            neverUsed.take(30).forEach { name ->
                chipGroup.addView(Chip(ctx).apply {
                    text = name; textSize = 11f
                    setOnClickListener {
                        val items = ReferenceData.search(name)
                        if (items.isNotEmpty()) {
                            val bundle = Bundle().apply { putLong("reference_id", items[0].id) }
                            findNavController().navigate(R.id.referenceDetailFragment, bundle)
                        }
                    }
                })
            }
            if (neverUsed.size > 30) {
                chipGroup.addView(Chip(ctx).apply {
                    text = "...还有 ${neverUsed.size - 30} 个"; textSize = 11f; isClickable = false
                })
            }
            neverCard.addView(chipGroup)
            container.addView(neverCard, marginParams(dp(0), dp(0), dp(0), dp(12)))
        }

        // Most used components
        if (globalComponents.isNotEmpty()) {
            container.addView(createSectionTitle("组件使用频率 (跨项目)"))
            val compCard = createCard()
            val compLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            globalComponents.entries.sortedByDescending { it.value }.forEach { (comp, count) ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(5), dp(4), dp(5))
                }
                row.addView(TextView(ctx).apply {
                    text = comp; textSize = 13f
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(ctx).apply {
                    text = "${count} 个项目"; textSize = 12f
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorSecondary))
                })
                compLayout.addView(row)
            }
            compCard.addView(compLayout)
            container.addView(compCard, marginParams(dp(0), dp(0), dp(0), dp(16)))
        }
    }

    private fun launchSketchware() {
        val packages = listOf(
            "pro.sketchware",
            "com.besome.sketch.pro",
            "com.besome.sketch",
            "mod.agus.jcoderz.editor"
        )
        for (pkg in packages) {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
                return
            }
        }
        Snackbar.make(binding.root, "未找到 Sketchware Pro", Snackbar.LENGTH_SHORT).show()
    }

    private fun showBackupDialog() {
        if (allProjects.isEmpty()) {
            Snackbar.make(binding.root, "没有找到项目", Snackbar.LENGTH_SHORT).show()
            return
        }
        val names = allProjects.map { "${it.name} (${it.id})" }.toTypedArray()
        val checked = BooleanArray(names.size) { false }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择要备份的项目")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton("取消", null)
            .setPositiveButton("备份") { _, _ ->
                val selected = allProjects.filterIndexed { idx, _ -> checked[idx] }
                if (selected.isEmpty()) {
                    Snackbar.make(binding.root, "未选择任何项目", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                backupProjects(selected)
            }
            .show()
    }

    private fun backupProjects(projects: List<SkProject>) {
        Snackbar.make(binding.root, "正在备份...", Snackbar.LENGTH_INDEFINITE).show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val count = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val backupDir = File(Environment.getExternalStorageDirectory(), "SkNote_Backup")
                    if (!backupDir.exists()) backupDir.mkdirs()

                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                    var c = 0

                    for (project in projects) {
                        val myscDir = File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list/${project.id}")
                        val dataDir = project.path

                        val zipFile = File(backupDir, "${project.name}_${project.id}_$timestamp.zip")
                        java.io.FileOutputStream(zipFile).use { fos ->
                            java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(fos)).use { zos ->
                                fun addDirToZip(dir: File, basePath: String) {
                                    dir.listFiles()?.forEach { file ->
                                        val entryName = "$basePath/${file.name}"
                                        if (file.isDirectory) {
                                            addDirToZip(file, entryName)
                                        } else {
                                            zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                                            file.inputStream().use { it.copyTo(zos) }
                                            zos.closeEntry()
                                        }
                                    }
                                }

                                if (myscDir.exists()) addDirToZip(myscDir, "mysc/${project.id}")
                                if (dataDir.exists()) addDirToZip(dataDir, "data/${project.id}")
                            }
                        }
                        c++
                    }
                    c
                }
                Snackbar.make(binding.root, "已备份 $count 个项目到 SkNote_Backup/", Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "备份失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
