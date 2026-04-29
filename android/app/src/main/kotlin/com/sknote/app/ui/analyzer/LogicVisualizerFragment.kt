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
import android.os.Parcelable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
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
import com.google.android.material.tabs.TabLayout
import com.sknote.app.R
import com.sknote.app.databinding.FragmentLogicVisualizerBinding
import com.sknote.app.ui.reference.BlockShapeView
import com.sknote.app.ui.reference.ReferenceData
import com.sknote.app.util.slideNavOptions
import org.json.JSONObject
import java.io.File

private typealias LogicBlock = SkProjectParser.LogicBlock

class LogicVisualizerFragment : Fragment() {

    private var _binding: FragmentLogicVisualizerBinding? = null
    private val binding get() = _binding!!

    data class SkProject(val id: String, val name: String, val packageName: String, val path: File)

    data class LogicEvent(
        val activity: String,
        val eventName: String,
        val displayName: String,
        val sectionKey: String,
        val blocks: Map<Int, LogicBlock>,
        val rootId: Int,
        val rawLines: List<String> = emptyList(),
        val isMoreBlock: Boolean = false,
        val moreBlockSpec: String = ""
    )

    private var allProjects = listOf<SkProject>()
    private var allEvents = listOf<LogicEvent>()
    private var activities = listOf<String>()
    private var currentActivityIndex = 0
    private var currentEvents = listOf<LogicEvent>()
    private var selectedEventIndex = 0
    private var isInVisualization = false
    private var currentProject: SkProject? = null
    private var currentProjectId: String? = null
    private var projectListState: Parcelable? = null
    private var eventListState: Parcelable? = null
    private var blockScrollY: Int = 0
    private var currentTab: Int = TAB_EVENTS

    /** Per-Activity logic data parsed from `logic` file. */
    private var activityLogics: Map<String, SkProjectParser.ActivityLogic> = emptyMap()
    /** Per-XML view trees parsed from `view` file. */
    private var layoutTrees: Map<String, SkProjectParser.LayoutTree> = emptyMap()
    /** Resource files for the current project. */
    private var projectResources: SkProjectParser.ResourceFiles? = null
    /** Lazily built renderer for the new container-based block visualisation. */
    private var blockRenderer: BlockChainRenderer? = null

    companion object {
        private const val STATE_IS_IN_VISUALIZATION = "state_is_in_visualization"
        private const val STATE_PROJECT_ID = "state_project_id"
        private const val STATE_ACTIVITY_INDEX = "state_activity_index"
        private const val STATE_EVENT_INDEX = "state_event_index"
        private const val STATE_BLOCK_SCROLL_Y = "state_block_scroll_y"
        private const val STATE_PROJECT_LIST = "state_project_list"
        private const val STATE_EVENT_LIST = "state_event_list"
        private const val STATE_TAB_INDEX = "state_tab_index"

        const val TAB_OVERVIEW = 0
        const val TAB_EVENTS = 1
        const val TAB_VIEW = 2
        const val TAB_RESOURCES = 3
    }

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
        savedInstanceState?.let {
            isInVisualization = it.getBoolean(STATE_IS_IN_VISUALIZATION, false)
            currentProjectId = it.getString(STATE_PROJECT_ID)
            currentActivityIndex = it.getInt(STATE_ACTIVITY_INDEX, 0)
            selectedEventIndex = it.getInt(STATE_EVENT_INDEX, 0)
            blockScrollY = it.getInt(STATE_BLOCK_SCROLL_Y, 0)
            projectListState = it.getParcelable(STATE_PROJECT_LIST)
            eventListState = it.getParcelable(STATE_EVENT_LIST)
            currentTab = it.getInt(STATE_TAB_INDEX, TAB_EVENTS)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.toolbar.setNavigationOnClickListener {
            if (isInVisualization) goBackFromVisualization() else findNavController().navigateUp()
        }

        binding.btnGrantPermission.setOnClickListener { requestStoragePermission() }

        setupTabs()

        if (hasStoragePermission()) {
            loadProjects()
            restoreUiStateAfterLoad()
        } else {
            showPermissionUI()
        }
    }

    private fun setupTabs() {
        val tabs = binding.tabsView
        if (tabs.tabCount == 0) {
            tabs.addTab(tabs.newTab().setText("概览"))
            tabs.addTab(tabs.newTab().setText("事件"))
            tabs.addTab(tabs.newTab().setText("视图"))
            tabs.addTab(tabs.newTab().setText("资源"))
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                applyTabVisibility()
                refreshCurrentTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                refreshCurrentTab()
            }
        })
    }

    private fun applyTabVisibility() {
        val b = _binding ?: return
        b.scrollOverview.visibility = if (currentTab == TAB_OVERVIEW) View.VISIBLE else View.GONE
        b.panelEvents.visibility = if (currentTab == TAB_EVENTS) View.VISIBLE else View.GONE
        b.scrollViewTree.visibility = if (currentTab == TAB_VIEW) View.VISIBLE else View.GONE
        b.scrollResources.visibility = if (currentTab == TAB_RESOURCES) View.VISIBLE else View.GONE
    }

    private fun refreshCurrentTab() {
        when (currentTab) {
            TAB_OVERVIEW -> renderOverview()
            TAB_VIEW -> renderViewTree()
            TAB_RESOURCES -> renderResources()
            else -> {} // events tab is rendered by switchToActivity / renderEvent
        }
    }

    private fun goBackFromVisualization() {
        binding.layoutVisualization.visibility = View.GONE
        binding.rvProjects.visibility = View.VISIBLE
        binding.toolbar.subtitle = "找到 ${allProjects.size} 个项目"
        binding.toolbar.menu.clear()
        currentProject = null
        currentProjectId = null
        allEvents = emptyList()
        activities = emptyList()
        currentEvents = emptyList()
        currentActivityIndex = 0
        selectedEventIndex = 0
        eventListState = null
        blockScrollY = 0
        isInVisualization = false
        activityLogics = emptyMap()
        layoutTrees = emptyMap()
        projectResources = null
        blockRenderer = null
        binding.layoutOverview.removeAllViews()
        binding.layoutViewTree.removeAllViews()
        binding.layoutResources.removeAllViews()
        binding.layoutBlocks.removeAllViews()
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

    private fun restoreUiStateAfterLoad() {
        val restoredProjectId = currentProjectId
        if (isInVisualization && !restoredProjectId.isNullOrEmpty()) {
            val project = allProjects.firstOrNull { it.id == restoredProjectId }
            if (project != null) {
                binding.root.post { analyzeLogic(project, restoreState = true) }
                return
            }
            currentProject = null
            currentProjectId = null
            isInVisualization = false
        }
        val listState = projectListState ?: return
        binding.rvProjects.post {
            binding.rvProjects.layoutManager?.onRestoreInstanceState(listState)
        }
    }

    // ---- Logic file parsing ----

    /**
     * Builds the flat [LogicEvent] list (events + MoreBlocks) consumed by the
     * existing event chip / block-renderer pipeline, from the parsed activity logics.
     */
    private fun buildLogicEventsFromActivities(
        activities: Map<String, SkProjectParser.ActivityLogic>
    ): List<LogicEvent> {
        val out = mutableListOf<LogicEvent>()
        for ((_, act) in activities) {
            for (chain in act.blockChains.values) {
                val (display, eventName) = describeEntryKey(chain.entryKey, chain.isMoreBlock)
                out.add(
                    LogicEvent(
                        activity = act.activityName,
                        eventName = eventName,
                        displayName = display,
                        sectionKey = chain.sectionKey,
                        blocks = chain.blocks,
                        rootId = chain.rootId,
                        rawLines = chain.rawLines,
                        isMoreBlock = chain.isMoreBlock,
                        moreBlockSpec = if (chain.isMoreBlock) {
                            val funcName = chain.entryKey.removeSuffix("_moreBlock")
                            act.moreBlocks.firstOrNull { it.name == funcName }?.spec.orEmpty()
                        } else ""
                    )
                )
            }
        }
        return out
    }

    /**
     * Turns a Sketchware block-section entry key (e.g. "btn1_onClick", "foo_moreBlock",
     * "onCreate") into a display label + canonical event name.
     */
    private fun describeEntryKey(
        entryKey: String,
        isMoreBlock: Boolean
    ): Pair<String, String> {
        if (isMoreBlock) {
            val funcName = entryKey.removeSuffix("_moreBlock")
            return "¶ $funcName" to entryKey
        }
        if (entryKey.isEmpty()) return "初始化逻辑" to "initializeLogic"
        val parts = entryKey.split("_")
        return if (parts.size >= 2) {
            val target = parts.dropLast(1).joinToString("_")
            val rawEvent = parts.last()
            val translated = eventNameMap[rawEvent] ?: rawEvent
            val label = if (target.isNotEmpty()) "$target.$translated" else translated
            label to entryKey
        } else {
            val translated = eventNameMap[entryKey] ?: entryKey
            translated to entryKey
        }
    }

    // ---- Visualization ----

    private fun analyzeLogic(project: SkProject, restoreState: Boolean = false) {
        binding.rvProjects.visibility = View.GONE
        binding.layoutVisualization.visibility = View.VISIBLE
        binding.toolbar.subtitle = project.name
        isInVisualization = true
        backCallback.isEnabled = true
        currentProject = project
        currentProjectId = project.id

        // Add toolbar menu
        binding.toolbar.inflateMenu(R.menu.menu_logic_visualizer)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search_blocks -> { showSearchDialog(); true }
                else -> false
            }
        }

        val logicFile = File(project.path, "logic")
        val viewFile = File(project.path, "view")
        if (!logicFile.exists()) {
            Snackbar.make(binding.root, "未找到逻辑文件", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Parse all project data via the shared parser.
        activityLogics = SkProjectParser.parseLogic(logicFile)
        layoutTrees = SkProjectParser.parseView(viewFile)
        projectResources = SkProjectParser.scanResources(
            Environment.getExternalStorageDirectory(), project.id
        )
        blockRenderer = createBlockRenderer()

        allEvents = buildLogicEventsFromActivities(activityLogics)
        if (allEvents.isEmpty() && activityLogics.isEmpty()) {
            Snackbar.make(binding.root, "没有找到逻辑事件", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Activity ordering: union of logic + view (so XML-only activities show up too).
        val orderedActivities = LinkedHashSet<String>()
        activityLogics.values.forEach { orderedActivities.add(it.activityName) }
        layoutTrees.values.forEach { if (it.activityName.isNotEmpty()) orderedActivities.add(it.activityName) }
        if (orderedActivities.isEmpty()) {
            Snackbar.make(binding.root, "未找到任何 Activity", Snackbar.LENGTH_SHORT).show()
            return
        }
        activities = orderedActivities.toList()
        currentActivityIndex = if (restoreState) {
            currentActivityIndex.coerceIn(0, activities.lastIndex)
        } else {
            0
        }

        val totalBlocks = allEvents.sumOf { it.blocks.size }
        val moreBlockCount = activityLogics.values.sumOf { it.moreBlocks.size }
        val varCount = activityLogics.values.sumOf { it.variables.size }
        val statsParts = mutableListOf(
            "${activities.size} Activity",
            "${allEvents.size - moreBlockCount} 事件",
            "$totalBlocks 积木"
        )
        if (moreBlockCount > 0) statsParts.add("$moreBlockCount 自定义块")
        if (varCount > 0) statsParts.add("$varCount 变量")
        binding.tvStatsLabel.text = statsParts.joinToString(" · ")

        binding.tvActivityLabel.text = "▼ ${activities[currentActivityIndex]}"
        binding.tvActivityLabel.setOnClickListener { showActivityPicker() }

        // Show the previously selected tab and ensure events tab still works.
        val tabs = binding.tabsView
        if (tabs.tabCount > 0) {
            val tab = tabs.getTabAt(currentTab.coerceIn(0, tabs.tabCount - 1))
            tab?.select()
        }
        applyTabVisibility()

        switchToActivity(currentActivityIndex, restoreState)
    }

    private fun createBlockRenderer(): BlockChainRenderer = BlockChainRenderer(
        ctx = requireContext(),
        onBlockClick = { block, _ ->
            val refItem = ReferenceData.search(block.opCode).firstOrNull()
            if (refItem != null) {
                val bundle = Bundle().apply { putLong("reference_id", refItem.id) }
                findNavController().navigate(R.id.referenceDetailFragment, bundle, slideNavOptions())
            } else {
                showBlockInfo(block, null, currentEvents.getOrNull(selectedEventIndex)?.let {
                    blockRenderer?.fillSpec(block.spec, block.parameters, it.blocks).orEmpty()
                }.orEmpty())
            }
        },
        onBlockLongClick = { block, filled ->
            val refItem = ReferenceData.search(block.opCode).firstOrNull()
            showBlockInfo(block, refItem, filled)
        },
        resolveColorAttr = { attr -> resolveColor(attr) }
    )

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
                val filledSpec = fillSpec(block.spec, block.parameters, event.blocks).lowercase()
                if (block.opCode.lowercase().contains(query) ||
                    block.spec.lowercase().contains(query) ||
                    filledSpec.contains(query) ||
                    block.parameters.any { it.lowercase().contains(query) }) {
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
            val filled = fillSpec(r.block.spec, r.block.parameters, r.event.blocks)
            "${r.event.activity} → ${r.event.displayName}\n${r.block.opCode}: ${filled.take(50)}"
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

    private fun switchToActivity(index: Int, restoreState: Boolean = false) {
        currentActivityIndex = index
        val actName = activities[index]
        binding.tvActivityLabel.text = "▼ $actName"
        currentEvents = allEvents.filter { it.activity == actName }
        selectedEventIndex = if (restoreState && currentEvents.isNotEmpty()) {
            selectedEventIndex.coerceIn(0, currentEvents.lastIndex)
        } else {
            0
        }
        setupEventList()
        if (currentEvents.isNotEmpty()) {
            val eventIndex = selectedEventIndex.coerceIn(0, currentEvents.lastIndex)
            renderEvent(currentEvents[eventIndex], restoreState)
            binding.rvEvents.post {
                val state = eventListState
                if (restoreState && state != null) {
                    binding.rvEvents.layoutManager?.onRestoreInstanceState(state)
                } else {
                    binding.rvEvents.scrollToPosition(eventIndex)
                }
            }
        } else {
            binding.layoutBlocks.removeAllViews()
            val ctx = requireContext()
            val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
            binding.layoutBlocks.addView(TextView(ctx).apply {
                text = "该 Activity 没有事件或自定义块"
                textSize = 13f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(8), dp(16), 0, 0)
            })
        }
        // Refresh non-event tabs whenever the active Activity changes.
        refreshCurrentTab()
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

    // ---- Tab: Overview ----

    private fun renderOverview() {
        val container = binding.layoutOverview
        container.removeAllViews()
        val ctx = context ?: return
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val activityName = activities.getOrNull(currentActivityIndex) ?: return
        val javaName = "$activityName.java"
        val logic = activityLogics[javaName]
        if (logic == null) {
            container.addView(emptyHint(ctx, dp, "该 Activity 没有逻辑数据"))
            return
        }

        // Section: variables
        if (logic.variables.isNotEmpty()) {
            container.addView(sectionHeader(ctx, dp, "变量 (${logic.variables.size})"))
            for (v in logic.variables) {
                container.addView(buildKeyValueRow(ctx, dp, v.typeLabel, v.name))
            }
        }
        // Section: lists
        if (logic.lists.isNotEmpty()) {
            container.addView(sectionHeader(ctx, dp, "集合 (${logic.lists.size})"))
            for (l in logic.lists) {
                container.addView(buildKeyValueRow(ctx, dp, l.typeLabel, l.name))
            }
        }
        // Section: components
        if (logic.components.isNotEmpty()) {
            container.addView(sectionHeader(ctx, dp, "组件 (${logic.components.size})"))
            for (c in logic.components) {
                val sub = listOf(c.param1, c.param2, c.param3)
                    .filter { it.isNotEmpty() }
                    .joinToString("  ·  ")
                container.addView(buildKeyValueRow(ctx, dp, c.typeName, c.componentId, sub))
            }
        }
        // Section: events list
        if (logic.events.isNotEmpty()) {
            container.addView(sectionHeader(ctx, dp, "事件 (${logic.events.size})"))
            for (e in logic.events) {
                val type = SkProjectParser.eventTypeLabels[e.eventType] ?: "?"
                val translated = eventNameMap[e.eventName] ?: e.eventName
                container.addView(buildKeyValueRow(ctx, dp, type, "${e.targetId}.${translated}", e.eventName))
            }
        }
        // Section: more blocks
        if (logic.moreBlocks.isNotEmpty()) {
            container.addView(sectionHeader(ctx, dp, "自定义块 (${logic.moreBlocks.size})"))
            for (mb in logic.moreBlocks) {
                container.addView(buildKeyValueRow(ctx, dp, "MoreBlock", mb.name, mb.spec))
            }
        }

        if (container.childCount == 0) {
            container.addView(emptyHint(ctx, dp, "该 Activity 没有变量、列表、组件或自定义块"))
        }
    }

    private fun sectionHeader(ctx: android.content.Context, dp: (Int) -> Int, title: String): View {
        val tv = TextView(ctx)
        tv.text = title
        tv.textSize = 12f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
        tv.setPadding(dp(2), dp(14), 0, dp(6))
        return tv
    }

    private fun buildKeyValueRow(
        ctx: android.content.Context,
        dp: (Int) -> Int,
        tag: String,
        value: String,
        sub: String = ""
    ): View {
        val card = MaterialCardView(ctx).apply {
            radius = dp(8).toFloat()
            strokeWidth = 0
            cardElevation = 0f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceVariant))
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        // tag chip
        row.addView(TextView(ctx).apply {
            text = tag
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
            setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorPrimaryContainer))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(10) }
        })
        // value column
        val text = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        text.addView(TextView(ctx).apply {
            this.text = value
            textSize = 13f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        })
        if (sub.isNotEmpty()) {
            text.addView(TextView(ctx).apply {
                this.text = sub
                textSize = 11f
                typeface = Typeface.MONOSPACE
                alpha = 0.7f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
        row.addView(text)
        card.addView(row)
        return card
    }

    private fun emptyHint(ctx: android.content.Context, dp: (Int) -> Int, msg: String): View {
        val tv = TextView(ctx)
        tv.text = msg
        tv.textSize = 13f
        tv.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        tv.setPadding(dp(8), dp(20), 0, 0)
        return tv
    }

    // ---- Tab: View tree ----

    private fun renderViewTree() {
        val container = binding.layoutViewTree
        container.removeAllViews()
        val ctx = context ?: return
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val activityName = activities.getOrNull(currentActivityIndex) ?: return

        // Find layout files belonging to this activity (exact match + xml-name fallback)
        val matched = layoutTrees.values.filter { it.activityName == activityName }
        if (matched.isEmpty()) {
            container.addView(emptyHint(ctx, dp, "未找到 $activityName 的布局文件"))
            return
        }

        for (tree in matched) {
            container.addView(sectionHeader(ctx, dp, "${tree.xmlName} (${tree.roots.sumOf { countNodes(it) }})"))
            for (root in tree.roots) {
                renderViewNode(container, ctx, dp, root, depth = 0)
            }
            tree.fab?.let {
                container.addView(sectionHeader(ctx, dp, "FloatingActionButton"))
                renderViewNode(container, ctx, dp, it, depth = 0)
            }
        }
    }

    private fun countNodes(node: SkProjectParser.ViewNode): Int {
        var n = 1
        for (c in node.children) n += countNodes(c)
        return n
    }

    private fun renderViewNode(
        container: LinearLayout,
        ctx: android.content.Context,
        dp: (Int) -> Int,
        node: SkProjectParser.ViewNode,
        depth: Int
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8 + depth * 14), dp(4), dp(8), dp(4))
            isClickable = true
            isFocusable = true
            val tv = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            setBackgroundResource(tv.resourceId)
            setOnClickListener { showViewReferences(node) }
        }
        // type tag
        row.addView(TextView(ctx).apply {
            text = node.typeName
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
            setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorPrimaryContainer))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8) }
        })
        // id
        row.addView(TextView(ctx).apply {
            text = node.id
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        })
        // text/hint preview
        val previewText = when {
            node.text.isNotEmpty() -> "  \"${node.text}\""
            node.hint.isNotEmpty() -> "  hint: ${node.hint}"
            node.imageResource.isNotEmpty() && node.imageResource != "default_image" -> "  img: ${node.imageResource}"
            else -> ""
        }
        if (previewText.isNotEmpty()) {
            row.addView(TextView(ctx).apply {
                text = previewText
                textSize = 11f
                alpha = 0.65f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
        container.addView(row)
        for (child in node.children) {
            renderViewNode(container, ctx, dp, child, depth + 1)
        }
    }

    /**
     * Find every block referencing the given view id (typically as block.spec or as a
     * targetId-prefixed event entry key) in the current Activity, and offer to jump
     * to the first matching event.
     */
    private fun showViewReferences(node: SkProjectParser.ViewNode) {
        val viewId = node.id
        if (viewId.isEmpty()) return
        val activityName = activities.getOrNull(currentActivityIndex) ?: return
        val activityEvents = allEvents.filter { it.activity == activityName }

        data class Hit(val event: LogicEvent, val kind: String, val opCode: String, val blockId: String)
        val hits = mutableListOf<Hit>()
        for (event in activityEvents) {
            // Event handler that targets this view (entry key starts with "viewId_")
            if (event.eventName.startsWith("${viewId}_") || event.eventName == viewId) {
                hits.add(Hit(event, "事件", "", ""))
            }
            for (block in event.blocks.values) {
                if (block.spec == viewId || block.parameters.any { it == viewId }) {
                    hits.add(Hit(event, "引用", block.opCode, block.blockId))
                }
            }
        }
        if (hits.isEmpty()) {
            Snackbar.make(binding.root, "${node.typeName} $viewId 未在逻辑里被引用", Snackbar.LENGTH_SHORT).show()
            return
        }
        val items = hits.take(30).map {
            if (it.kind == "事件") "[事件] ${it.event.displayName}"
            else "${it.event.displayName} → ${it.opCode} #${it.blockId}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$viewId — ${hits.size} 处引用")
            .setItems(items) { _, which ->
                val pick = hits[which]
                val idx = currentEvents.indexOf(pick.event)
                if (idx >= 0) {
                    selectedEventIndex = idx
                    val tabs = binding.tabsView
                    tabs.getTabAt(TAB_EVENTS)?.select()
                    binding.rvEvents.adapter?.notifyDataSetChanged()
                    binding.rvEvents.scrollToPosition(idx)
                    renderEvent(pick.event)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    // ---- Tab: Resources ----

    private fun renderResources() {
        val container = binding.layoutResources
        container.removeAllViews()
        val ctx = context ?: return
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val res = projectResources
        if (res == null || res.total == 0) {
            container.addView(emptyHint(ctx, dp, "项目未使用图片、字体或音频资源"))
            return
        }

        if (res.images.isNotEmpty()) {
            container.addView(sectionHeader(ctx, dp, "图片 (${res.images.size})"))
            renderImageGrid(container, ctx, dp, res.images)
        }
        if (res.fonts.isNotEmpty()) {
            container.addView(sectionHeader(ctx, dp, "字体 (${res.fonts.size})"))
            for (f in res.fonts) {
                container.addView(buildKeyValueRow(ctx, dp, "font", f.nameWithoutExtension, "${f.length() / 1024} KB · ${f.extension}"))
            }
        }
        if (res.sounds.isNotEmpty()) {
            container.addView(sectionHeader(ctx, dp, "音频 (${res.sounds.size})"))
            for (s in res.sounds) {
                container.addView(buildKeyValueRow(ctx, dp, "sound", s.nameWithoutExtension, "${s.length() / 1024} KB · ${s.extension}"))
            }
        }
    }

    private fun renderImageGrid(
        container: LinearLayout,
        ctx: android.content.Context,
        dp: (Int) -> Int,
        images: List<File>
    ) {
        // Simple flow layout: wrap rows of fixed-size thumbnails.
        val cellSize = dp(96)
        val cellSpacing = dp(8)
        val columns = (resources.displayMetrics.widthPixels / (cellSize + cellSpacing)).coerceAtLeast(2)

        var row: LinearLayout? = null
        images.forEachIndexed { index, file ->
            if (index % columns == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = cellSpacing }
                }
                container.addView(row)
            }
            row?.addView(buildImageCell(ctx, dp, file, cellSize))
        }
    }

    private fun buildImageCell(
        ctx: android.content.Context,
        dp: (Int) -> Int,
        file: File,
        cellSize: Int
    ): View {
        val card = MaterialCardView(ctx).apply {
            radius = dp(8).toFloat()
            strokeWidth = 0
            cardElevation = 0f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(cellSize, cellSize + dp(20)).apply {
                rightMargin = dp(8)
            }
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val img = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(cellSize - dp(12), cellSize - dp(20))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            try {
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) setImageBitmap(bmp) else setImageResource(android.R.drawable.ic_menu_gallery)
            } catch (_: Exception) {
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }
        val name = TextView(ctx).apply {
            text = file.nameWithoutExtension
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        }
        box.addView(img)
        box.addView(name)
        card.addView(box)
        return card
    }

    private fun renderEvent(event: LogicEvent, restoreState: Boolean = false) {
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
        // Action buttons container
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // Code preview button
        btnRow.addView(TextView(ctx).apply {
            text = "{ } 代码"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { showCodePreview(event) }
        })
        // Copy button
        btnRow.addView(TextView(ctx).apply {
            text = "⎘ 复制"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { showCopyEventDialog(event) }
        })
        headerLayout.addView(btnRow)

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

        // For MoreBlock events, show the function signature spec under the header.
        if (event.isMoreBlock && event.moreBlockSpec.isNotEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "spec: ${event.moreBlockSpec}"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(4), 0, dp(4), dp(8))
            })
        }

        // Render block chain via the container-based renderer.
        val renderer = blockRenderer ?: createBlockRenderer().also { blockRenderer = it }
        renderer.render(container, event.blocks, event.rootId)

        if (restoreState) {
            binding.scrollBlocks.post { binding.scrollBlocks.scrollTo(0, blockScrollY) }
        } else {
            binding.scrollBlocks.scrollTo(0, 0)
        }

        if (container.childCount <= 1) {
            container.addView(TextView(ctx).apply {
                text = "此事件没有积木块"
                textSize = 14f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(8), dp(16), 0, 0)
            })
        }
    }

    // ---- Parameter resolution ----

    /**
     * Resolves a block (by ID) into a human-readable inline expression string.
     * For example: block with spec "getText of %m.textview" and parameters ["edittext1"]
     * becomes "getText(edittext1)"
     */
    private fun resolveBlockExpression(blocks: Map<Int, LogicBlock>, blockId: Int, depth: Int = 0): String {
        if (depth > 8) return "..."  // prevent infinite recursion
        val block = blocks[blockId] ?: return "?"

        // Fill spec with resolved parameters
        return fillSpec(block.spec, block.parameters, blocks, depth)
    }

    /**
     * Substitutes spec placeholders (%s, %b, %d, %m.xxx, %s.inputOnly) with actual parameter values.
     * Parameters referencing other blocks (@id) are recursively resolved.
     */
    private fun fillSpec(spec: String, parameters: List<String>, blocks: Map<Int, LogicBlock>, depth: Int = 0): String {
        if (parameters.isEmpty()) return spec

        val result = StringBuilder()
        var paramIndex = 0
        var i = 0
        while (i < spec.length) {
            if (spec[i] == '%' && i + 1 < spec.length) {
                // Find the end of this placeholder
                val placeholderStart = i
                i++ // skip '%'
                // Handle %s.inputOnly, %m.xxx, %s, %b, %d
                while (i < spec.length && spec[i] != ' ' && spec[i] != '%') {
                    i++
                }
                // We found a placeholder, resolve the parameter
                if (paramIndex < parameters.size) {
                    val paramValue = parameters[paramIndex]
                    val resolved = resolveParamValue(paramValue, blocks, depth)
                    result.append(resolved)
                    paramIndex++
                } else {
                    // No more parameters, keep placeholder text
                    result.append(spec.substring(placeholderStart, i))
                }
            } else {
                result.append(spec[i])
                i++
            }
        }
        return result.toString().trim()
    }

    /**
     * Resolves a single parameter value:
     * - "@123" → recursively resolve block 123 into expression
     * - direct value → return as-is (e.g., "textview1", "Hello", "100")
     */
    private fun resolveParamValue(value: String, blocks: Map<Int, LogicBlock>, depth: Int): String {
        if (value.isEmpty()) return "⬚"  // empty slot indicator
        if (value.startsWith("@")) {
            val refId = value.removePrefix("@").toIntOrNull()
            if (refId != null) {
                val resolved = resolveBlockExpression(blocks, refId, depth + 1)
                return "[$resolved]"
            }
        }
        return value
    }

    // ---- Pseudo-code generation ----

    private fun generatePseudoCode(blocks: Map<Int, LogicBlock>, startId: Int, indent: Int = 0): String {
        val sb = StringBuilder()
        var currentId = startId
        val visited = mutableSetOf<Int>()
        val prefix = "  ".repeat(indent)

        while (currentId >= 0 && currentId !in visited) {
            visited.add(currentId)
            val block = blocks[currentId] ?: break

            val filledSpec = fillSpec(block.spec, block.parameters, blocks)
            val disabledMark = if (block.disabled) "// [禁用] " else ""

            when (block.type.trim()) {
                "c" -> {
                    // If block
                    sb.appendLine("${prefix}${disabledMark}$filledSpec {")
                    if (block.subStack1 >= 0) {
                        sb.append(generatePseudoCode(blocks, block.subStack1, indent + 1))
                    }
                    sb.appendLine("${prefix}}")
                }
                "e" -> {
                    // If-else block
                    sb.appendLine("${prefix}${disabledMark}$filledSpec {")
                    if (block.subStack1 >= 0) {
                        sb.append(generatePseudoCode(blocks, block.subStack1, indent + 1))
                    }
                    sb.appendLine("${prefix}} else {")
                    if (block.subStack2 >= 0) {
                        sb.append(generatePseudoCode(blocks, block.subStack2, indent + 1))
                    }
                    sb.appendLine("${prefix}}")
                }
                "f" -> {
                    // Terminal block (break, return, etc.)
                    sb.appendLine("${prefix}${disabledMark}⛔ $filledSpec")
                }
                else -> {
                    // Regular statement
                    sb.appendLine("${prefix}${disabledMark}$filledSpec")
                }
            }

            currentId = block.nextBlock
        }
        return sb.toString()
    }

    private fun showCodePreview(event: LogicEvent) {
        val code = generatePseudoCode(event.blocks, event.rootId)
        if (code.isBlank()) {
            Snackbar.make(binding.root, "此事件没有积木块", Snackbar.LENGTH_SHORT).show()
            return
        }

        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        val scrollView = android.widget.HorizontalScrollView(ctx).apply {
            isFillViewport = true
        }
        val verticalScroll = android.widget.ScrollView(ctx).apply {
            isFillViewport = true
        }
        val tv = TextView(ctx).apply {
            text = code
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTextIsSelectable(true)
        }
        verticalScroll.addView(tv)
        scrollView.addView(verticalScroll)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("${event.displayName} — 伪代码预览")
            .setView(scrollView)
            .setPositiveButton("复制") { _, _ ->
                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("pseudo_code", code))
                Snackbar.make(binding.root, "已复制伪代码", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showBlockInfo(block: LogicBlock, refItem: com.sknote.app.data.model.ReferenceItem?, filledSpec: String = "") {
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
        if (filledSpec.isNotEmpty() && filledSpec != block.spec) {
            addRow("实际内容", filledSpec)
        }
        if (block.parameters.isNotEmpty()) {
            addRow("参数", block.parameters.joinToString(", "))
        }
        addRow("形状", ReferenceData.shapeLabels[mapBlockType(block.type, block.opCode)] ?: block.type)
        addRow("颜色", String.format("#%06X", 0xFFFFFF and block.color))
        if (block.disabled) addRow("状态", "已禁用")
        if (refItem != null) {
            addRow("类别", refItem.category)
            if (refItem.description.orEmpty().isNotEmpty()) addRow("说明", refItem.description.orEmpty())
        }

        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(block.opCode)
            .setView(layout)
            .setNegativeButton("关闭", null)
        if (refItem != null) {
            builder.setPositiveButton("查看详情") { _, _ ->
                val bundle = Bundle().apply { putLong("reference_id", refItem.id) }
                findNavController().navigate(R.id.referenceDetailFragment, bundle, slideNavOptions())
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
        if (refItem != null && refItem.shape.orEmpty().isNotEmpty()) {
            return refItem.shape.orEmpty()
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
            // Visually distinguish MoreBlocks (custom functions) from regular events.
            if (event.isMoreBlock) {
                holder.chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    resolveColor(com.google.android.material.R.attr.colorTertiaryContainer)
                )
                holder.chip.setTextColor(
                    resolveColor(com.google.android.material.R.attr.colorOnTertiaryContainer)
                )
            } else {
                holder.chip.chipBackgroundColor = null
                holder.chip.setTextColor(
                    resolveColor(com.google.android.material.R.attr.colorOnSurface)
                )
            }
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

    private fun captureUiState() {
        val currentBinding = _binding ?: return
        projectListState = currentBinding.rvProjects.layoutManager?.onSaveInstanceState()
        if (isInVisualization) {
            eventListState = currentBinding.rvEvents.layoutManager?.onSaveInstanceState()
            blockScrollY = currentBinding.scrollBlocks.scrollY
        }
    }

    override fun onPause() {
        captureUiState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureUiState()
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_IS_IN_VISUALIZATION, isInVisualization)
        outState.putString(STATE_PROJECT_ID, currentProjectId)
        outState.putInt(STATE_ACTIVITY_INDEX, currentActivityIndex)
        outState.putInt(STATE_EVENT_INDEX, selectedEventIndex)
        outState.putInt(STATE_BLOCK_SCROLL_Y, blockScrollY)
        outState.putParcelable(STATE_PROJECT_LIST, projectListState)
        outState.putParcelable(STATE_EVENT_LIST, eventListState)
        outState.putInt(STATE_TAB_INDEX, currentTab)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
