package com.sknote.app.ui.analyzer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentLogicVisualizerBinding
import com.sknote.app.ui.reference.BlockShapeView
import com.sknote.app.ui.reference.ReferenceData
import org.json.JSONObject
import java.io.File

class LogicVisualizerFragment : Fragment() {

    private var _binding: FragmentLogicVisualizerBinding? = null
    private val binding get() = _binding!!

    data class SkProject(val id: String, val name: String, val packageName: String, val path: File)

    data class LogicBlock(
        val blockId: String,
        val opCode: String,
        val spec: String,
        val color: Int,
        val type: String,
        val nextBlock: Int,
        val subStack1: Int,
        val subStack2: Int,
        val rawJson: String = ""
    )

    data class LogicEvent(
        val activity: String,
        val eventName: String,
        val displayName: String,
        val sectionKey: String,
        val blocks: Map<Int, LogicBlock>,
        val rootId: Int,
        val rawLines: List<String> = emptyList()
    )

    private var allProjects = listOf<SkProject>()
    private var allEvents = listOf<LogicEvent>()
    private var activities = listOf<String>()
    private var currentActivityIndex = 0
    private var currentEvents = listOf<LogicEvent>()
    private var selectedEventIndex = 0
    private var isInVisualization = false
    private var currentProject: SkProject? = null

    // Sketchware event name → Chinese translation
    private val eventNameMap = mapOf(
        "onCreate" to "创建时",
        "onBackPressed" to "返回键按下时",
        "onResume" to "恢复时",
        "onPause" to "暂停时",
        "onDestroy" to "销毁时",
        "onStart" to "启动时",
        "onStop" to "停止时",
        "onClick" to "点击时",
        "onLongClick" to "长按时",
        "onItemSelected" to "选中时",
        "onItemClick" to "列表项点击",
        "onItemLongClick" to "列表项长按",
        "onTextChanged" to "文本变化时",
        "onCheckedChange" to "选中状态变化",
        "onProgressChanged" to "进度变化时",
        "onPageSelected" to "页面选中时",
        "onPageScrolled" to "页面滑动时",
        "onScrollChanged" to "滚动变化时",
        "onScrolled" to "滚动时",
        "onSwipeRefresh" to "下拉刷新时",
        "onTabSelected" to "标签选中时",
        "onResponse" to "请求成功时",
        "onErrorResponse" to "请求失败时",
        "onDataChange" to "数据变化时",
        "onCancelled" to "取消时",
        "onChildAdded" to "子节点添加时",
        "onChildChanged" to "子节点变化时",
        "onChildRemoved" to "子节点删除时",
        "onTimerFinish" to "计时结束时",
        "onAnimationEnd" to "动画结束时",
        "onAnimationStart" to "动画开始时",
        "onSensorChanged" to "传感器变化时",
        "onConnected" to "已连接",
        "onDisconnected" to "已断开",
        "onDataReceived" to "数据接收时",
        "onLocationChanged" to "位置变化时",
        "onUploadProgress" to "上传进度",
        "onDownloadProgress" to "下载进度",
        "onUploadSuccess" to "上传成功",
        "onDownloadSuccess" to "下载成功",
        "onSignInSuccess" to "登录成功",
        "onSignInFailed" to "登录失败",
        "onCreateUserSuccess" to "注册成功",
        "onCreateUserFailed" to "注册失败",
        "onAdLoaded" to "广告加载完成",
        "onAdFailedToLoad" to "广告加载失败",
        "onAdClosed" to "广告关闭",
        "onMenuItemClick" to "菜单项点击",
        "onCreateOptionsMenu" to "创建选项菜单",
        "onActivityResult" to "Activity返回结果",
        "onPermissionGranted" to "权限已授予",
        "onPermissionDenied" to "权限被拒绝",
        "onRecyclerScrolled" to "RecyclerView滚动",
        "onRecyclerScrollStateChanged" to "RecyclerView滚动状态变化",
        "onBindCustomView" to "绑定自定义View",
        "onCreateCustomView" to "创建自定义View",
        "initializeLogic" to "初始化逻辑",
        "onPickResult" to "选择结果",
        "onCompleted" to "完成时",
        "onFailure" to "失败时",
        "onSuccess" to "成功时"
    )

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadProjects() else showPermissionUI()
    }

    private val manageStorageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasStoragePermission()) loadProjects() else showPermissionUI()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogicVisualizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            goBackFromVisualization()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ReferenceData.init(requireContext())
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.toolbar.setNavigationOnClickListener {
            if (isInVisualization) goBackFromVisualization() else findNavController().navigateUp()
        }

        binding.btnGrantPermission.setOnClickListener { requestStoragePermission() }

        if (hasStoragePermission()) {
            loadProjects()
        } else {
            showPermissionUI()
        }
    }

    private fun goBackFromVisualization() {
        binding.layoutVisualization.visibility = View.GONE
        binding.rvProjects.visibility = View.VISIBLE
        binding.toolbar.subtitle = "找到 ${allProjects.size} 个项目"
        binding.toolbar.menu.clear()
        isInVisualization = false
        backCallback.isEnabled = false
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
        binding.layoutVisualization.visibility = View.GONE
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
            analyzeLogic(project)
        }

        binding.toolbar.subtitle = "找到 ${projects.size} 个项目"
    }

    // ---- Logic file parsing ----

    private fun parseLogicFileForVisualization(file: File): List<LogicEvent> {
        val events = mutableListOf<LogicEvent>()
        try {
            val lines = file.readLines()
            var currentSection = ""
            var currentBlocks = mutableMapOf<Int, LogicBlock>()
            var minId = Int.MAX_VALUE

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("@")) {
                    if (currentBlocks.isNotEmpty() && !currentSection.endsWith("_components")) {
                        val event = buildLogicEvent(currentSection, currentBlocks, minId)
                        if (event != null) events.add(event)
                    }
                    currentSection = trimmed
                    currentBlocks = mutableMapOf()
                    minId = Int.MAX_VALUE
                    continue
                }
                if (trimmed.isEmpty()) continue
                if (currentSection.endsWith("_components")) continue

                try {
                    val json = JSONObject(trimmed)
                    val blockId = json.optString("id", "")
                    val blockIdInt = blockId.toIntOrNull() ?: continue
                    val opCode = json.optString("opCode", "")
                    if (opCode.isEmpty()) continue

                    val spec = json.optString("spec", opCode)
                    val color = json.optInt("color", -0x1E8D5E2)
                    val type = json.optString("type", " ")
                    val nextBlock = json.optInt("nextBlock", -1)
                    val subStack1 = json.optInt("subStack1", -1)
                    val subStack2 = json.optInt("subStack2", -1)

                    val block = LogicBlock(blockId, opCode, spec, color, type, nextBlock, subStack1, subStack2, trimmed)
                    currentBlocks[blockIdInt] = block
                    if (blockIdInt < minId) minId = blockIdInt
                } catch (_: Exception) {}
            }

            if (currentBlocks.isNotEmpty() && !currentSection.endsWith("_components")) {
                val event = buildLogicEvent(currentSection, currentBlocks, minId)
                if (event != null) events.add(event)
            }
        } catch (_: Exception) {}
        return events
    }

    private fun buildLogicEvent(section: String, blocks: Map<Int, LogicBlock>, minId: Int): LogicEvent? {
        if (blocks.isEmpty()) return null
        val clean = section.removePrefix("@")
        val activity = clean.substringBefore(".java")
        val eventPart = clean.substringAfter(".java_", "")

        // Parse event parts: e.g. "button1_onClick" → target="button1", event="onClick"
        val parts = eventPart.split("_")
        val eventName: String
        val displayName: String

        if (parts.size >= 2) {
            val target = parts.dropLast(1).joinToString("_")
            val rawEvent = parts.last()
            val translated = eventNameMap[rawEvent] ?: rawEvent
            eventName = eventPart
            displayName = if (target.isNotEmpty()) "$target.$translated" else translated
        } else if (eventPart.isNotEmpty()) {
            val translated = eventNameMap[eventPart] ?: eventPart
            eventName = eventPart
            displayName = translated
        } else {
            eventName = "initializeLogic"
            displayName = "初始化逻辑"
        }

        val referenced = mutableSetOf<Int>()
        blocks.values.forEach { b ->
            if (b.nextBlock >= 0) referenced.add(b.nextBlock)
            if (b.subStack1 >= 0) referenced.add(b.subStack1)
            if (b.subStack2 >= 0) referenced.add(b.subStack2)
        }
        val rootId = blocks.keys.firstOrNull { it !in referenced } ?: minId

        val rawLines = blocks.values.map { it.rawJson }.filter { it.isNotEmpty() }
        return LogicEvent(activity, eventName, displayName, section, blocks, rootId, rawLines)
    }

    // ---- Visualization ----

    private fun analyzeLogic(project: SkProject) {
        binding.rvProjects.visibility = View.GONE
        binding.layoutVisualization.visibility = View.VISIBLE
        binding.toolbar.subtitle = project.name
        isInVisualization = true
        backCallback.isEnabled = true
        currentProject = project

        // Add toolbar menu
        binding.toolbar.inflateMenu(R.menu.menu_logic_visualizer)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search_blocks -> { showSearchDialog(); true }
                else -> false
            }
        }

        val logicFile = File(project.path, "logic")
        if (!logicFile.exists()) {
            Snackbar.make(binding.root, "未找到逻辑文件", Snackbar.LENGTH_SHORT).show()
            return
        }

        allEvents = parseLogicFileForVisualization(logicFile)
        if (allEvents.isEmpty()) {
            Snackbar.make(binding.root, "没有找到逻辑事件", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Group by activity
        activities = allEvents.map { it.activity }.distinct()
        currentActivityIndex = 0
        val totalBlocks = allEvents.sumOf { it.blocks.size }
        binding.tvStatsLabel.text = "${activities.size} Activity · ${allEvents.size} 事件 · $totalBlocks 积木"

        // Setup activity selector (tap to switch)
        binding.tvActivityLabel.text = "▼ ${activities[0]}"
        binding.tvActivityLabel.setOnClickListener { showActivityPicker() }

        switchToActivity(0)
    }

    private fun showSearchDialog() {
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val input = EditText(ctx).apply {
            hint = "输入 opCode 或 spec 关键字"
            textSize = 14f
            setPadding(dp(20), dp(16), dp(20), dp(8))
            setSingleLine()
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("搜索积木块")
            .setView(input)
            .setPositiveButton("搜索") { _, _ ->
                val query = input.text.toString().trim().lowercase()
                if (query.isNotEmpty()) performSearch(query)
            }
            .setNegativeButton("取消", null)
            .show()
        input.requestFocus()
    }

    private fun performSearch(query: String) {
        data class SearchResult(val event: LogicEvent, val block: LogicBlock, val activityIndex: Int, val eventIndex: Int)
        val results = mutableListOf<SearchResult>()
        for (event in allEvents) {
            for ((_, block) in event.blocks) {
                if (block.opCode.lowercase().contains(query) || block.spec.lowercase().contains(query)) {
                    val ai = activities.indexOf(event.activity)
                    val ei = allEvents.filter { it.activity == event.activity }.indexOf(event)
                    results.add(SearchResult(event, block, ai, ei))
                }
            }
        }
        if (results.isEmpty()) {
            Snackbar.make(binding.root, "未找到匹配的积木块", Snackbar.LENGTH_SHORT).show()
            return
        }
        // Show results as a dialog list
        val items = results.take(30).map { r ->
            "${r.event.activity} → ${r.event.displayName}\n${r.block.opCode}: ${r.block.spec.take(40)}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("找到 ${results.size} 个结果")
            .setItems(items) { _, which ->
                val r = results[which]
                // Switch activity without rendering default event
                if (r.activityIndex >= 0 && r.activityIndex != currentActivityIndex) {
                    currentActivityIndex = r.activityIndex
                    val actName = activities[r.activityIndex]
                    binding.tvActivityLabel.text = "▼ $actName"
                    currentEvents = allEvents.filter { it.activity == actName }
                    setupEventList()
                }
                // Navigate to the event containing this block
                if (r.eventIndex >= 0 && r.eventIndex < currentEvents.size) {
                    selectedEventIndex = r.eventIndex
                    binding.rvEvents.adapter?.notifyDataSetChanged()
                    binding.rvEvents.scrollToPosition(r.eventIndex)
                    renderEvent(currentEvents[r.eventIndex])
                }
            }
            .show()
    }

    private fun showActivityPicker() {
        val items = activities.mapIndexed { i, name ->
            val count = allEvents.count { it.activity == name }
            "$name ($count 事件)"
        }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择 Activity")
            .setItems(items) { _, which -> switchToActivity(which) }
            .show()
    }

    private fun switchToActivity(index: Int) {
        currentActivityIndex = index
        val actName = activities[index]
        binding.tvActivityLabel.text = "▼ $actName"
        currentEvents = allEvents.filter { it.activity == actName }
        selectedEventIndex = 0
        setupEventList()
        if (currentEvents.isNotEmpty()) {
            renderEvent(currentEvents[0])
        }
    }

    private fun setupEventList() {
        val ctx = requireContext()
        binding.rvEvents.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        binding.rvEvents.adapter = EventChipAdapter(currentEvents) { index ->
            selectedEventIndex = index
            renderEvent(currentEvents[index])
            // Auto-scroll chips to selected
            binding.rvEvents.smoothScrollToPosition(index)
        }
    }

    private fun renderEvent(event: LogicEvent) {
        val container = binding.layoutBlocks
        container.removeAllViews()
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        // Event header card
        val headerCard = MaterialCardView(ctx).apply {
            radius = dp(12).toFloat()
            strokeWidth = 0
            cardElevation = 0f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorPrimaryContainer))
        }
        val headerLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val headerTextLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerTextLayout.addView(TextView(ctx).apply {
            text = event.displayName
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
        })
        headerTextLayout.addView(TextView(ctx).apply {
            text = event.eventName
            textSize = 10f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
            alpha = 0.6f
            typeface = Typeface.MONOSPACE
        })
        headerLayout.addView(headerTextLayout)
        // Copy button
        val copyBtn = TextView(ctx).apply {
            text = "⎘ 复制"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { showCopyEventDialog(event) }
        }
        headerLayout.addView(copyBtn)

        // Block count badge
        headerLayout.addView(TextView(ctx).apply {
            text = "${event.blocks.size}"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
            alpha = 0.5f
            setPadding(dp(8), 0, 0, 0)
        })
        headerCard.addView(headerLayout)
        container.addView(headerCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // Render block chain
        renderBlockChain(container, event.blocks, event.rootId, 0, dp)

        // Scroll to top
        binding.scrollBlocks.scrollTo(0, 0)

        if (container.childCount <= 1) {
            container.addView(TextView(ctx).apply {
                text = "此事件没有积木块"
                textSize = 14f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(8), dp(16), 0, 0)
            })
        }
    }

    private val nestColors = intArrayOf(
        0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt(),
        0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF00BCD4.toInt()
    )

    private fun renderBlockChain(container: LinearLayout, blocks: Map<Int, LogicBlock>, startId: Int, depth: Int, dp: (Int) -> Int) {
        var currentId = startId
        val visited = mutableSetOf<Int>()
        val ctx = requireContext()
        val nestColor = if (depth > 0) nestColors[(depth - 1) % nestColors.size] else 0

        while (currentId >= 0 && currentId !in visited) {
            visited.add(currentId)
            val block = blocks[currentId] ?: break

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Nesting depth bars
            for (d in 0 until depth) {
                val barColor = nestColors[d % nestColors.size]
                row.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                        leftMargin = dp(if (d == 0) 4 else 12)
                    }
                    setBackgroundColor(barColor)
                    alpha = 0.5f
                })
            }

            // Spacer after bars
            if (depth > 0) {
                row.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), 0)
                })
            }

            // Block shape view
            val shape = mapBlockType(block.type, block.opCode)
            val blockView = BlockShapeView(ctx).apply {
                this.blockColor = block.color
                this.blockShape = shape
                this.blockSpec = block.spec
                this.blockScale = 0.8f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Click → navigate to reference; Long-press → show info popup
            val refItem = ReferenceData.search(block.opCode).firstOrNull()
            if (refItem != null) {
                blockView.setOnClickListener {
                    val bundle = Bundle().apply { putLong("reference_id", refItem.id) }
                    findNavController().navigate(R.id.referenceDetailFragment, bundle)
                }
                blockView.setOnLongClickListener {
                    showBlockInfo(block, refItem)
                    true
                }
            } else {
                blockView.setOnLongClickListener {
                    showBlockInfo(block, null)
                    true
                }
            }

            row.addView(blockView)

            // opCode label
            row.addView(TextView(ctx).apply {
                text = block.opCode
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                alpha = 0.5f
                setPadding(dp(6), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            container.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(1); bottomMargin = dp(1) })

            // Render subStack1
            if (block.subStack1 >= 0) {
                renderBlockChain(container, blocks, block.subStack1, depth + 1, dp)
            }

            // E-shape: else divider + subStack2
            if (block.type.trim() == "e" && block.subStack2 >= 0) {
                val elseRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                for (d in 0 until depth) {
                    val barColor = nestColors[d % nestColors.size]
                    elseRow.addView(View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                            leftMargin = dp(if (d == 0) 4 else 12)
                        }
                        setBackgroundColor(barColor)
                        alpha = 0.5f
                    })
                }
                elseRow.addView(TextView(ctx).apply {
                    text = "  else"
                    textSize = 10f
                    setTypeface(typeface, Typeface.BOLD_ITALIC)
                    setTextColor(nestColor.takeIf { it != 0 } ?: resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    setPadding(dp(8), dp(2), 0, dp(2))
                })
                container.addView(elseRow)

                renderBlockChain(container, blocks, block.subStack2, depth + 1, dp)
            }

            currentId = block.nextBlock
        }
    }

    private fun showBlockInfo(block: LogicBlock, refItem: com.sknote.app.data.model.ReferenceItem?) {
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        fun addRow(label: String, value: String) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row.addView(TextView(ctx).apply {
                text = label
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(ctx).apply {
                text = value
                textSize = 12f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            layout.addView(row)
        }

        addRow("opCode", block.opCode)
        addRow("Spec", block.spec)
        addRow("形状", ReferenceData.shapeLabels[mapBlockType(block.type, block.opCode)] ?: block.type)
        addRow("颜色", String.format("#%06X", 0xFFFFFF and block.color))
        if (refItem != null) {
            addRow("类别", refItem.category)
            if (refItem.description.isNotEmpty()) addRow("说明", refItem.description)
        }

        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(block.opCode)
            .setView(layout)
            .setNegativeButton("关闭", null)
        if (refItem != null) {
            builder.setPositiveButton("查看详情") { _, _ ->
                val bundle = Bundle().apply { putLong("reference_id", refItem.id) }
                findNavController().navigate(R.id.referenceDetailFragment, bundle)
            }
        }
        builder.show()
    }

    // ---- Cross-project block copy ----

    private fun showCopyEventDialog(event: LogicEvent) {
        val ctx = requireContext()
        val targetProjects = allProjects.filter { it.id != currentProject?.id }
        if (targetProjects.isEmpty()) {
            Snackbar.make(binding.root, "没有其他项目可复制到", Snackbar.LENGTH_SHORT).show()
            return
        }
        val items = targetProjects.map { "${it.name} (ID: ${it.id})" }.toTypedArray()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("复制 ${event.blocks.size} 个积木块到...")
            .setItems(items) { _, which ->
                showCopyTargetSectionDialog(event, targetProjects[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCopyTargetSectionDialog(event: LogicEvent, targetProject: SkProject) {
        val ctx = requireContext()
        val targetLogicFile = File(targetProject.path, "logic")

        // Parse target project to find existing activities
        val targetActivities = mutableListOf<String>()
        if (targetLogicFile.exists()) {
            try {
                targetLogicFile.readLines().forEach { line ->
                    val t = line.trim()
                    if (t.startsWith("@") && t.contains(".java")) {
                        val act = t.removePrefix("@").substringBefore(".java")
                        if (act !in targetActivities) targetActivities.add(act)
                    }
                }
            } catch (_: Exception) {}
        }

        // Options: use original section name, or pick a target activity
        val options = mutableListOf<String>()
        val sectionKeys = mutableListOf<String>()

        // Option 1: Keep original section name
        options.add("保持原始段落名: ${event.sectionKey.removePrefix("@")}")
        sectionKeys.add(event.sectionKey)

        // Option 2: For each target activity, offer to copy under it with same event suffix
        val eventSuffix = event.sectionKey.removePrefix("@").substringAfter(".java_", "")
        for (act in targetActivities) {
            val newSection = "@${act}.java_${eventSuffix}"
            if (newSection != event.sectionKey) {
                options.add("复制到 ${act} 的 ${event.displayName}")
                sectionKeys.add(newSection)
            }
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("选择目标段落")
            .setItems(options.toTypedArray()) { _, which ->
                performCopyEvent(event, targetProject, sectionKeys[which])
            }
            .setNeutralButton("自定义段落名") { _, _ ->
                showCustomSectionDialog(event, targetProject)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCustomSectionDialog(event: LogicEvent, targetProject: SkProject) {
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val input = EditText(ctx).apply {
            hint = "例如: @MainActivity.java_button1_onClick"
            setText(event.sectionKey)
            textSize = 13f
            setPadding(dp(20), dp(16), dp(20), dp(8))
            setSingleLine()
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("输入目标段落名")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val section = input.text.toString().trim()
                if (section.isNotEmpty()) {
                    val finalSection = if (!section.startsWith("@")) "@$section" else section
                    performCopyEvent(event, targetProject, finalSection)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performCopyEvent(event: LogicEvent, targetProject: SkProject, targetSection: String) {
        // Show confirmation before writing
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认复制")
            .setMessage("将 ${event.blocks.size} 个积木块复制到\n${targetProject.name}\n\n目标段落: ${targetSection.removePrefix("@")}\n\n复制前将自动备份目标文件。")
            .setPositiveButton("确认复制") { _, _ -> executeCopy(event, targetProject, targetSection) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeCopy(event: LogicEvent, targetProject: SkProject, targetSection: String) {
        try {
            val targetLogicFile = File(targetProject.path, "logic")

            // 0. Backup target file before modification
            if (targetLogicFile.exists()) {
                val backupFile = File(targetProject.path, "logic.bak")
                targetLogicFile.copyTo(backupFile, overwrite = true)
            }

            // 1. Find max block ID in target file
            var maxId = 0
            if (targetLogicFile.exists()) {
                targetLogicFile.readLines().forEach { line ->
                    val t = line.trim()
                    if (t.startsWith("{")) {
                        try {
                            val json = JSONObject(t)
                            val bid = json.optString("id", "0").toIntOrNull() ?: 0
                            if (bid > maxId) maxId = bid
                        } catch (_: Exception) {}
                    }
                }
            }

            // 2. Build ID remap: old ID -> new ID (starting from maxId + 1)
            val sortedIds = event.blocks.keys.sorted()
            val idRemap = mutableMapOf<Int, Int>()
            var nextId = maxId + 1
            for (oldId in sortedIds) {
                idRemap[oldId] = nextId
                nextId++
            }

            // 3. Remap each block's JSON
            val remappedLines = mutableListOf<String>()
            for (oldId in sortedIds) {
                val block = event.blocks[oldId] ?: continue
                val json = JSONObject(block.rawJson)

                // Remap id
                json.put("id", idRemap[oldId].toString())

                // Remap nextBlock
                val nb = json.optInt("nextBlock", -1)
                json.put("nextBlock", if (nb >= 0 && idRemap.containsKey(nb)) idRemap[nb]!! else nb)

                // Remap subStack1
                val ss1 = json.optInt("subStack1", -1)
                json.put("subStack1", if (ss1 >= 0 && idRemap.containsKey(ss1)) idRemap[ss1]!! else ss1)

                // Remap subStack2
                val ss2 = json.optInt("subStack2", -1)
                json.put("subStack2", if (ss2 >= 0 && idRemap.containsKey(ss2)) idRemap[ss2]!! else ss2)

                // Remap parameter references (@blockId)
                val params = json.optJSONArray("parameters")
                if (params != null) {
                    for (i in 0 until params.length()) {
                        val p = params.optString(i, "")
                        if (p.startsWith("@")) {
                            val refId = p.removePrefix("@").toIntOrNull()
                            if (refId != null && idRemap.containsKey(refId)) {
                                params.put(i, "@${idRemap[refId]}")
                            }
                        }
                    }
                }

                remappedLines.add(json.toString())
            }

            // 4. Check if target section already exists (line-by-line exact match)
            val existingContent = if (targetLogicFile.exists()) targetLogicFile.readText() else ""
            val existingLines = existingContent.split("\n")
            val sectionExists = existingLines.any { it.trim() == targetSection }

            if (sectionExists) {
                // Insert blocks at the end of the existing section
                val lines = existingLines.toMutableList()
                var insertIndex = -1
                var foundSection = false
                for (i in lines.indices) {
                    if (lines[i].trim() == targetSection) {
                        foundSection = true
                    } else if (foundSection && (lines[i].trim().startsWith("@") || (lines[i].trim().isEmpty() && i + 1 < lines.size && lines[i + 1].trim().startsWith("@")))) {
                        insertIndex = i
                        break
                    }
                }
                if (insertIndex < 0) insertIndex = lines.size

                // Insert remapped blocks
                for ((idx, blockLine) in remappedLines.withIndex()) {
                    lines.add(insertIndex + idx, blockLine)
                }
                targetLogicFile.writeText(lines.joinToString("\n"))
            } else {
                // Append new section
                val sb = StringBuilder()
                if (existingContent.isNotEmpty()) {
                    sb.append(existingContent)
                    if (!existingContent.endsWith("\n")) sb.append("\n")
                    sb.append("\n")
                }
                sb.append(targetSection).append("\n")
                for (line in remappedLines) {
                    sb.append(line).append("\n")
                }
                targetLogicFile.writeText(sb.toString())
            }

            Snackbar.make(
                binding.root,
                "已复制 ${remappedLines.size} 个积木块到 ${targetProject.name}（已备份原文件）",
                Snackbar.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Snackbar.make(binding.root, "复制失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun mapBlockType(type: String, opCode: String): String {
        // Try to find shape from ReferenceData first
        val refItem = ReferenceData.search(opCode).firstOrNull()
        if (refItem != null && refItem.shape.isNotEmpty()) {
            return refItem.shape
        }
        // Map Sketchware type characters to BlockShapeView shapes
        return when (type.trim()) {
            "c" -> "c"
            "e" -> "e"
            "f" -> "f"
            "b" -> "b"
            "d" -> "d"
            "r" -> "r"
            "h" -> "h"
            else -> "s"  // default statement block
        }
    }

    // ---- Adapters ----

    inner class ProjectAdapter(
        private val projects: List<SkProject>,
        private val onClick: (SkProject) -> Unit
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
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val textLayout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textLayout.addView(TextView(parent.context).apply {
                id = android.R.id.text1; textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            })
            textLayout.addView(TextView(parent.context).apply {
                id = android.R.id.text2; textSize = 12f
            })
            layout.addView(textLayout)
            layout.addView(TextView(parent.context).apply {
                text = "查看 →"
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
            holder.card.setOnClickListener { onClick(project) }
        }

        override fun getItemCount() = projects.size
    }

    inner class EventChipAdapter(
        private val events: List<LogicEvent>,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<EventChipAdapter.VH>() {

        inner class VH(val chip: Chip) : RecyclerView.ViewHolder(chip)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
            val chip = Chip(parent.context).apply {
                textSize = 11f
                isCheckable = true
                chipStartPadding = dp(4).toFloat()
                chipEndPadding = dp(4).toFloat()
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(4) }
            }
            return VH(chip)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val event = events[position]
            holder.chip.text = "${event.displayName} (${event.blocks.size})"
            holder.chip.isChecked = position == selectedEventIndex
            holder.chip.setOnClickListener {
                val prev = selectedEventIndex
                selectedEventIndex = position
                notifyItemChanged(prev)
                notifyItemChanged(position)
                onSelect(position)
            }
        }

        override fun getItemCount() = events.size
    }

    // ---- Helpers ----

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
