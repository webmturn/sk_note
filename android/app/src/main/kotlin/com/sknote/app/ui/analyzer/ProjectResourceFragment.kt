package com.sknote.app.ui.analyzer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.media.MediaPlayer
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.sknote.app.R
import com.sknote.app.databinding.FragmentProjectResourceBinding
import org.json.JSONObject
import java.io.File

class ProjectResourceFragment : Fragment() {

    private var _binding: FragmentProjectResourceBinding? = null
    private val binding get() = _binding!!

    data class SkProject(val id: String, val name: String, val path: String)
    data class ResourceItem(val file: File, val name: String, val size: Long, val isImage: Boolean)

    private var allProjects = listOf<SkProject>()
    private var currentProject: SkProject? = null
    private var currentTab = 0
    private var isInResources = false
    private var mediaPlayer: MediaPlayer? = null

    private val resourceTypes = listOf("images", "sounds", "icons", "fonts", "widgets")
    private val tabLabels = listOf("图片", "音频", "图标", "字体", "控件")

    private fun resolveResDir(resBase: File, type: String, projectId: String): File {
        val withData = File(resBase, "$type/data/$projectId")
        if (withData.exists()) return withData
        return File(resBase, "$type/$projectId")
    }

    private val imageExts = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "xml")
    private val soundExts = setOf("mp3", "wav", "ogg", "aac", "m4a")

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadProjects() else showPermissionUI()
    }

    private val manageStorageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasStoragePermission()) loadProjects() else showPermissionUI()
    }

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFile(it) }
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            goBackFromResources()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProjectResourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.toolbar.setNavigationOnClickListener {
            if (isInResources) goBackFromResources() else findNavController().navigateUp()
        }
        binding.btnGrantPermission.setOnClickListener { requestStoragePermission() }

        binding.fabAdd.setOnClickListener { addResource() }

        if (hasStoragePermission()) loadProjects() else showPermissionUI()
    }

    private fun goBackFromResources() {
        stopPlayback()
        binding.layoutResources.visibility = View.GONE
        binding.rvProjects.visibility = View.VISIBLE
        binding.toolbar.subtitle = "找到 ${allProjects.size} 个项目"
        isInResources = false
        backCallback.isEnabled = false
        currentProject = null
    }

    // ---- Permission ----

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
        binding.layoutResources.visibility = View.GONE
    }

    // ---- Load projects ----

    private fun loadProjects() {
        binding.layoutPermission.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE

        val skDir = File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list")
        val resBase = File(Environment.getExternalStorageDirectory(), ".sketchware/resources")

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
                    // Check if this project has any resources
                    val hasRes = resourceTypes.any { type ->
                        val dir = resolveResDir(resBase, type, id)
                        dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
                    }
                    projects.add(SkProject(id, name, id))
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
        binding.rvProjects.adapter = ProjectListAdapter(projects) { project ->
            openProjectResources(project)
        }
        binding.toolbar.subtitle = "找到 ${projects.size} 个项目"
    }

    // ---- Open project resources ----

    private fun openProjectResources(project: SkProject) {
        currentProject = project
        isInResources = true
        backCallback.isEnabled = true

        binding.rvProjects.visibility = View.GONE
        binding.layoutResources.visibility = View.VISIBLE
        binding.toolbar.subtitle = project.name

        // Setup tabs
        binding.tabLayout.removeAllTabs()
        val resBase = File(Environment.getExternalStorageDirectory(), ".sketchware/resources")
        for (i in resourceTypes.indices) {
            val dir = resolveResDir(resBase, resourceTypes[i], project.id)
            val count = dir.listFiles()?.size ?: 0
            val tab = binding.tabLayout.newTab().setText("${tabLabels[i]} ($count)")
            binding.tabLayout.addTab(tab)
        }

        binding.tabLayout.clearOnTabSelectedListeners()
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                loadResources()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        currentTab = 0
        loadResources()
    }

    // ---- Load resources ----

    private fun loadResources() {
        stopPlayback()
        val project = currentProject ?: return
        val resBase = File(Environment.getExternalStorageDirectory(), ".sketchware/resources")
        val dir = resolveResDir(resBase, resourceTypes[currentTab], project.id)

        val items = mutableListOf<ResourceItem>()
        if (dir.exists()) {
            dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { file ->
                if (file.isFile) {
                    val ext = file.extension.lowercase()
                    val isImg = ext in imageExts
                    items.add(ResourceItem(file, file.name, file.length(), isImg))
                }
            }
        }

        val isImageTab = currentTab == 0 || currentTab == 2 // images or icons
        if (isImageTab && items.isNotEmpty()) {
            binding.rvResources.layoutManager = GridLayoutManager(requireContext(), 3)
        } else {
            binding.rvResources.layoutManager = LinearLayoutManager(requireContext())
        }
        binding.rvResources.adapter = ResourceAdapter(items, isImageTab)
    }

    // ---- Add resource ----

    private fun addResource() {
        val project = currentProject ?: return
        val mimeType = when (currentTab) {
            0 -> "image/*"       // images
            1 -> "audio/*"       // sounds
            2 -> "image/*"       // icons
            3 -> "*/*"           // fonts
            4 -> "image/*"       // widgets
            else -> "*/*"
        }
        pickFileLauncher.launch(mimeType)
    }

    private fun importFile(uri: Uri) {
        val project = currentProject ?: return
        val resBase = File(Environment.getExternalStorageDirectory(), ".sketchware/resources")
        val dir = resolveResDir(resBase, resourceTypes[currentTab], project.id)
        dir.mkdirs()

        try {
            // Get filename from URI
            var fileName = "resource_${System.currentTimeMillis()}"
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            val targetFile = File(dir, fileName)
            if (targetFile.exists()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("文件已存在")
                    .setMessage("\"$fileName\" 已存在，是否覆盖？")
                    .setPositiveButton("覆盖") { _, _ ->
                        writeFile(uri, targetFile)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                writeFile(uri, targetFile)
            }
        } catch (e: Exception) {
            Snackbar.make(binding.root, "导入失败: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun writeFile(uri: Uri, targetFile: File) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Snackbar.make(binding.root, "已导入: ${targetFile.name}", Snackbar.LENGTH_SHORT).show()
            refreshTabCounts()
            loadResources()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "写入失败: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    // ---- Delete resource ----

    private fun deleteResource(item: ResourceItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除资源")
            .setMessage("确定删除 \"${item.name}\"？\n大小: ${formatSize(item.size)}\n\n此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                try {
                    if (item.file.delete()) {
                        Snackbar.make(binding.root, "已删除: ${item.name}", Snackbar.LENGTH_SHORT).show()
                        refreshTabCounts()
                        loadResources()
                    } else {
                        Snackbar.make(binding.root, "删除失败", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Snackbar.make(binding.root, "删除失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---- Preview ----

    private fun previewImage(item: ResourceItem) {
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }

        val ext = item.file.extension.lowercase()
        if (ext != "xml") {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bitmap = BitmapFactory.decodeFile(item.file.absolutePath, opts)
                if (bitmap != null) {
                    val imageView = ImageView(ctx).apply {
                        setImageBitmap(bitmap)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(300)
                        )
                    }
                    layout.addView(imageView)

                    layout.addView(TextView(ctx).apply {
                        text = "尺寸: ${bitmap.width} × ${bitmap.height}"
                        textSize = 12f
                        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                        setPadding(0, dp(8), 0, 0)
                    })
                }
            } catch (_: Exception) {
                layout.addView(TextView(ctx).apply {
                    text = "无法预览此图片"
                    textSize = 14f
                })
            }
        } else {
            layout.addView(TextView(ctx).apply {
                text = item.file.readText().take(2000)
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            })
        }

        layout.addView(TextView(ctx).apply {
            text = "文件: ${item.name}\n大小: ${formatSize(item.size)}"
            textSize = 11f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, dp(8), 0, 0)
        })

        MaterialAlertDialogBuilder(ctx)
            .setTitle(item.name)
            .setView(layout)
            .setNeutralButton("删除") { _, _ -> deleteResource(item) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun previewSound(item: ResourceItem) {
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        layout.addView(TextView(ctx).apply {
            text = "文件: ${item.name}\n大小: ${formatSize(item.size)}"
            textSize = 13f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        })

        // Get duration info
        var mp: MediaPlayer? = null
        try {
            mp = MediaPlayer()
            mp.setDataSource(item.file.absolutePath)
            mp.prepare()
            val durationSec = mp.duration / 1000
            layout.addView(TextView(ctx).apply {
                text = "时长: ${durationSec / 60}:${String.format("%02d", durationSec % 60)}"
                textSize = 13f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, dp(4), 0, 0)
            })
        } catch (_: Exception) {
        } finally {
            mp?.release()
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(item.name)
            .setView(layout)
            .setPositiveButton("播放") { _, _ -> playSound(item) }
            .setNeutralButton("删除") { _, _ -> deleteResource(item) }
            .setNegativeButton("关闭", null)
            .create()
        dialog.show()
    }

    private fun playSound(item: ResourceItem) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(item.file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    _binding?.let { b ->
                        Snackbar.make(b.root, "播放完成", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            Snackbar.make(binding.root, "正在播放: ${item.name}", Snackbar.LENGTH_LONG)
                .setAction("停止") { stopPlayback() }
                .show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "播放失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    // ---- Refresh tab counts ----

    private fun refreshTabCounts() {
        val project = currentProject ?: return
        val resBase = File(Environment.getExternalStorageDirectory(), ".sketchware/resources")
        for (i in resourceTypes.indices) {
            val dir = resolveResDir(resBase, resourceTypes[i], project.id)
            val count = dir.listFiles()?.size ?: 0
            binding.tabLayout.getTabAt(i)?.text = "${tabLabels[i]} ($count)"
        }
    }

    // ---- Adapters ----

    inner class ProjectListAdapter(
        private val projects: List<SkProject>,
        private val onClick: (SkProject) -> Unit
    ) : RecyclerView.Adapter<ProjectListAdapter.VH>() {

        private val projectDetails: List<String> = projects.map { project ->
            val resBase = File(Environment.getExternalStorageDirectory(), ".sketchware/resources")
            val counts = resourceTypes.map { type ->
                val dir = resolveResDir(resBase, type, project.id)
                dir.listFiles()?.size ?: 0
            }
            val details = mutableListOf<String>()
            if (counts[0] > 0) details.add("${counts[0]}\u56fe\u7247")
            if (counts[1] > 0) details.add("${counts[1]}\u97f3\u9891")
            if (counts[2] > 0) details.add("${counts[2]}\u56fe\u6807")
            if (counts[3] > 0) details.add("${counts[3]}\u5b57\u4f53")
            if (counts[4] > 0) details.add("${counts[4]}\u63a7\u4ef6")
            if (details.isNotEmpty()) "ID: ${project.id} \u00b7 ${details.joinToString(" \u00b7 ")}"
            else "ID: ${project.id} \u00b7 \u65e0\u8d44\u6e90"
        }

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
                isClickable = true; isFocusable = true
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
                text = "管理 →"
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
                text = projectDetails[position]
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
            holder.card.setOnClickListener { onClick(project) }
        }

        override fun getItemCount() = projects.size
    }

    inner class ResourceAdapter(
        private val items: List<ResourceItem>,
        private val isImageGrid: Boolean
    ) : RecyclerView.Adapter<ResourceAdapter.VH>() {

        inner class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
            val card = MaterialCardView(parent.context).apply {
                radius = dp(10).toFloat()
                strokeWidth = dp(1)
                cardElevation = 0f
                setStrokeColor(resolveColor(com.google.android.material.R.attr.colorOutlineVariant))
                setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurface))
                isClickable = true; isFocusable = true
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                foreground = ContextCompat.getDrawable(context, outValue.resourceId)
            }

            if (isImageGrid) {
                card.layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(120)
                ).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }

                val layout = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                }
                layout.addView(ImageView(parent.context).apply {
                    id = android.R.id.icon
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(80)
                    )
                })
                layout.addView(TextView(parent.context).apply {
                    id = android.R.id.text1
                    textSize = 9f
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                })
                card.addView(layout)
            } else {
                card.layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }

                val layout = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                layout.addView(TextView(parent.context).apply {
                    id = android.R.id.text1
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                layout.addView(TextView(parent.context).apply {
                    id = android.R.id.text2
                    textSize = 11f
                })
                card.addView(layout)
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]

            if (isImageGrid) {
                val imageView = holder.card.findViewById<ImageView>(android.R.id.icon)
                val ext = item.file.extension.lowercase()
                if (ext != "xml") {
                    try {
                        // Load thumbnail
                        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                        val bmp = BitmapFactory.decodeFile(item.file.absolutePath, options)
                        imageView.setImageBitmap(bmp)
                    } catch (_: Exception) {
                        imageView.setImageResource(R.drawable.ic_comp_default)
                    }
                } else {
                    imageView.setImageResource(R.drawable.ic_article)
                }
                holder.card.findViewById<TextView>(android.R.id.text1).apply {
                    text = item.name
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                }
            } else {
                holder.card.findViewById<TextView>(android.R.id.text1).apply {
                    text = item.name
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                }
                holder.card.findViewById<TextView>(android.R.id.text2).apply {
                    text = formatSize(item.size)
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                }
            }

            holder.card.setOnClickListener {
                if (item.isImage || item.file.extension.lowercase() == "xml") {
                    previewImage(item)
                } else if (item.file.extension.lowercase() in soundExts) {
                    previewSound(item)
                } else {
                    previewGeneric(item)
                }
            }

            holder.card.setOnLongClickListener {
                deleteResource(item)
                true
            }
        }

        override fun getItemCount() = items.size
    }

    private fun previewGeneric(item: ResourceItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setMessage("文件: ${item.name}\n大小: ${formatSize(item.size)}\n类型: ${item.file.extension}")
            .setNeutralButton("删除") { _, _ -> deleteResource(item) }
            .setNegativeButton("关闭", null)
            .show()
    }

    // ---- Helpers ----

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onDestroyView() {
        stopPlayback()
        super.onDestroyView()
        _binding = null
    }
}
