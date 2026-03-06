package com.sknote.app.ui.analyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentCustomBlocksBinding
import com.sknote.app.ui.reference.BlockShapeView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CustomBlocksFragment : Fragment() {

    private var _binding: FragmentCustomBlocksBinding? = null
    private val binding get() = _binding!!

    private val blocksDir get() = File(Environment.getExternalStorageDirectory(), ".sketchware/resources/block/My Block/block.json")
    private val paletteDir get() = File(Environment.getExternalStorageDirectory(), ".sketchware/resources/block/My Block/palette.json")

    private var allBlocks = mutableListOf<JSONObject>()
    private var palettes = mutableListOf<JSONObject>()

    // Navigation state: null = palette list, Int = viewing blocks in palette index+9
    private var currentPalette: Int? = null
    private var searchQuery: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCustomBlocksBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showPaletteList()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.toolbar.setNavigationOnClickListener {
            if (currentPalette != null) {
                showPaletteList()
            } else {
                findNavController().navigateUp()
            }
        }

        binding.rvBlocks.layoutManager = LinearLayoutManager(requireContext())

        binding.recycleBinCard.setOnClickListener {
            showBlocksInPalette(-1)
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                if (currentPalette == null) {
                    filterPaletteList()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        readData()
        showPaletteList()
    }

    // ── Data I/O ──

    private fun readData() {
        allBlocks.clear()
        palettes.clear()
        try {
            if (blocksDir.exists()) {
                val arr = JSONArray(blocksDir.readText())
                for (i in 0 until arr.length()) allBlocks.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {}
        try {
            if (paletteDir.exists()) {
                val arr = JSONArray(paletteDir.readText())
                for (i in 0 until arr.length()) palettes.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {}
    }

    private fun saveBlocks() {
        val arr = JSONArray()
        allBlocks.forEach { arr.put(it) }
        blocksDir.parentFile?.mkdirs()
        blocksDir.writeText(arr.toString())
    }

    private fun savePalettes() {
        val arr = JSONArray()
        palettes.forEach { arr.put(it) }
        paletteDir.parentFile?.mkdirs()
        paletteDir.writeText(arr.toString())
    }

    private fun getBlockCount(paletteIndex: Int): Int {
        return allBlocks.count {
            try { it.optString("palette", "") == paletteIndex.toString() } catch (_: Exception) { false }
        }
    }

    // ── Palette List ──

    private fun showPaletteList() {
        currentPalette = null
        backCallback.isEnabled = false
        binding.toolbar.title = "积木管理"
        binding.toolbar.subtitle = null
        binding.searchLayout.visibility = View.VISIBLE
        binding.recycleBinCard.visibility = View.VISIBLE
        binding.tvPaletteCount.visibility = View.VISIBLE

        val recycledCount = getBlockCount(-1)
        binding.tvRecycleBinCount.text = "积木块: $recycledCount"

        binding.fab.visibility = View.VISIBLE
        binding.fab.setImageResource(R.drawable.ic_add)
        binding.fab.setOnClickListener { showAddPaletteDialog() }

        filterPaletteList()
    }

    private fun filterPaletteList() {
        val filtered = if (searchQuery.isEmpty()) {
            palettes.mapIndexed { idx, pal -> idx to pal }
        } else {
            palettes.mapIndexed { idx, pal -> idx to pal }
                .filter { (_, pal) ->
                    pal.optString("name", "").contains(searchQuery, ignoreCase = true)
                }
        }

        if (palettes.isEmpty()) {
            binding.tvPaletteCount.text = "暂无调色板"
            binding.tvEmpty.text = "暂无调色板"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvBlocks.visibility = View.GONE
        } else if (filtered.isEmpty()) {
            binding.tvPaletteCount.text = "调色板 (${palettes.size})"
            binding.tvEmpty.text = "没有匹配的调色板"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvBlocks.visibility = View.GONE
        } else {
            binding.tvPaletteCount.text = if (searchQuery.isEmpty()) "调色板 (${palettes.size})" else "搜索结果 (${filtered.size}/${palettes.size})"
            binding.tvEmpty.visibility = View.GONE
            binding.rvBlocks.visibility = View.VISIBLE
            binding.rvBlocks.adapter = PaletteAdapter(filtered)
        }
    }

    // ── Block List in Palette ──

    private fun showBlocksInPalette(paletteIndex: Int) {
        currentPalette = paletteIndex
        backCallback.isEnabled = true
        binding.searchLayout.visibility = View.GONE
        binding.recycleBinCard.visibility = View.GONE
        binding.tvPaletteCount.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        if (paletteIndex == -1) {
            binding.toolbar.title = "回收站"
            binding.toolbar.subtitle = null
            binding.fab.visibility = View.GONE
        } else {
            val palObj = palettes.getOrNull(paletteIndex - 9)
            binding.toolbar.title = "积木管理"
            binding.toolbar.subtitle = palObj?.optString("name", "调色板 $paletteIndex") ?: "调色板 $paletteIndex"
            binding.fab.visibility = View.VISIBLE
            binding.fab.setImageResource(R.drawable.ic_add)
            binding.fab.setOnClickListener { navigateToEditor("add", -1, paletteIndex) }
        }

        val filtered = allBlocks.filter {
            it.optString("palette", "") == paletteIndex.toString()
        }

        if (filtered.isEmpty()) {
            binding.tvEmpty.text = if (paletteIndex == -1) "回收站为空" else "暂无积木块"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvBlocks.visibility = View.GONE
        } else {
            binding.rvBlocks.visibility = View.VISIBLE
            binding.rvBlocks.adapter = BlockAdapter(filtered, paletteIndex)
        }
    }

    // ── Palette Adapter ──

    private inner class PaletteAdapter(
        private val items: List<Pair<Int, JSONObject>> = palettes.mapIndexed { idx, pal -> idx to pal }
    ) : RecyclerView.Adapter<PaletteAdapter.VH>() {
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val colorBar: View = itemView.findViewById(R.id.colorBar)
            val tvName: TextView = itemView.findViewById(R.id.tvPaletteName)
            val tvSub: TextView = itemView.findViewById(R.id.tvPaletteSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_palette, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (originalIndex, pal) = items[position]
            val name = pal.optString("name", "")
            val colorStr = pal.optString("color", "#9E9E9E")
            val paletteIndex = originalIndex + 9
            val count = getBlockCount(paletteIndex)

            holder.tvName.text = name
            holder.tvSub.text = "积木块: $count"
            try { holder.colorBar.setBackgroundColor(Color.parseColor(colorStr)) }
            catch (_: Exception) { holder.colorBar.setBackgroundColor(Color.GRAY) }

            holder.itemView.setOnClickListener {
                showBlocksInPalette(paletteIndex)
            }

            holder.itemView.setOnLongClickListener {
                val popup = PopupMenu(requireContext(), holder.colorBar)
                popup.menu.add(0, 1, 0, "编辑")
                popup.menu.add(0, 3, 0, "分享到社区")
                popup.menu.add(0, 2, 0, "删除")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> showEditPaletteDialog(originalIndex)
                        2 -> confirmDeletePalette(originalIndex)
                        3 -> sharePaletteToCommunity(originalIndex)
                    }
                    true
                }
                popup.show()
                true
            }
        }

        override fun getItemCount() = items.size
    }

    // ── Block Adapter ──

    private inner class BlockAdapter(
        private val blocks: List<JSONObject>,
        private val paletteIndex: Int
    ) : RecyclerView.Adapter<BlockAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val background: View = itemView.findViewById(R.id.background)
            val tvName: TextView = itemView.findViewById(R.id.tvBlockName)
            val blockShapeView: BlockShapeView = itemView.findViewById(R.id.blockShapeView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_block, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val block = blocks[position]
            val name = block.optString("name", "")
            val spec = block.optString("spec", "")
            val type = block.optString("type", " ")
            val colorStr = block.optString("color", "")

            holder.tvName.text = name

            // Map block type to BlockShapeView shape
            val shape = when (type.trim()) {
                "b" -> "b"
                "c" -> "c"
                "e" -> "e"
                "d" -> "d"
                "f" -> "f"
                "h" -> "h"
                "s", "v", "a", "l", "p" -> "r"
                else -> "s"
            }

            val blockColor = if (paletteIndex == -1) {
                0xFF9E9E9E.toInt()
            } else if (colorStr.isNotEmpty()) {
                try { Color.parseColor(colorStr) } catch (_: Exception) { Color.GRAY }
            } else {
                val palObj = palettes.getOrNull(paletteIndex - 9)
                val palColor = palObj?.optString("color", "") ?: ""
                try { Color.parseColor(palColor) } catch (_: Exception) { Color.GRAY }
            }

            holder.blockShapeView.blockColor = blockColor
            holder.blockShapeView.blockShape = shape
            if (spec.isNotEmpty()) {
                holder.blockShapeView.blockSpec = spec
                holder.blockShapeView.blockLabel = ""
            } else {
                holder.blockShapeView.blockSpec = ""
                holder.blockShapeView.blockLabel = name
            }

            holder.background.setOnClickListener {
                val realIndex = allBlocks.indexOf(block)
                if (realIndex < 0) return@setOnClickListener
                if (paletteIndex == -1) {
                    showRecycleBinPopup(holder.background, realIndex)
                } else {
                    navigateToEditor("edit", realIndex, paletteIndex)
                }
            }

            holder.background.setOnLongClickListener {
                val realIndex = allBlocks.indexOf(block)
                if (realIndex < 0) return@setOnLongClickListener true
                if (paletteIndex == -1) {
                    showRecycleBinPopup(holder.background, realIndex)
                } else {
                    showBlockPopup(holder.background, realIndex)
                }
                true
            }
        }

        override fun getItemCount() = blocks.size
    }

    // ── Popup Menus ──

    private fun showBlockPopup(anchor: View, blockIndex: Int) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "编辑")
        popup.menu.add(0, 2, 0, "复制代码")
        popup.menu.add(0, 3, 0, "复制")
        popup.menu.add(0, 4, 0, "移动到调色板")
        popup.menu.add(0, 6, 0, "分享到社区")
        popup.menu.add(0, 5, 0, "删除")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> navigateToEditor("edit", blockIndex, currentPalette ?: 9)
                2 -> {
                    val code = allBlocks[blockIndex].optString("code", "")
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("block_code", code))
                    Snackbar.make(binding.root, "代码已复制", Snackbar.LENGTH_SHORT).show()
                }
                3 -> duplicateBlock(blockIndex)
                4 -> showMoveToPaletteDialog(blockIndex)
                5 -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除积木块")
                        .setMessage("选择删除方式")
                        .setPositiveButton("移到回收站") { _, _ -> moveToRecycleBin(blockIndex) }
                        .setNeutralButton("永久删除") { _, _ -> deleteBlock(blockIndex) }
                        .setNegativeButton("取消", null)
                        .show()
                }
                6 -> shareBlockToCommunity(blockIndex)
            }
            true
        }
        popup.show()
    }

    private fun shareBlockToCommunity(blockIndex: Int) {
        val block = allBlocks[blockIndex]
        val paletteIndex = block.optString("palette", "9").toIntOrNull() ?: 9
        val palColor = palettes.getOrNull(paletteIndex - 9)?.optString("color", "") ?: ""

        val shareJson = JSONObject().apply {
            put("name", block.optString("name", ""))
            put("type", block.optString("type", " "))
            put("typeName", block.optString("typeName", ""))
            put("spec", block.optString("spec", ""))
            put("spec2", block.optString("spec2", ""))
            put("color", block.optString("color", palColor))
            put("code", block.optString("code", ""))
            put("imports", block.optString("imports", ""))
            put("parameters", block.optString("parameters", ""))
        }

        val bundle = Bundle().apply {
            putString("block_json", shareJson.toString())
        }
        findNavController().navigate(R.id.createDiscussionFragment, bundle)
    }

    private fun sharePaletteToCommunity(paletteIdx: Int) {
        val pal = palettes.getOrNull(paletteIdx) ?: return
        val paletteIndex = paletteIdx + 9
        val palColor = pal.optString("color", "#9E9E9E")
        val palName = pal.optString("name", "调色板")

        val blocksInPalette = allBlocks.filter {
            it.optString("palette", "") == paletteIndex.toString()
        }

        if (blocksInPalette.isEmpty()) {
            Snackbar.make(binding.root, "该调色板没有积木块", Snackbar.LENGTH_SHORT).show()
            return
        }

        val blocksArr = JSONArray()
        for (block in blocksInPalette) {
            val b = JSONObject().apply {
                put("name", block.optString("name", ""))
                put("type", block.optString("type", " "))
                put("typeName", block.optString("typeName", ""))
                put("spec", block.optString("spec", ""))
                put("spec2", block.optString("spec2", ""))
                put("color", block.optString("color", palColor))
                put("code", block.optString("code", ""))
                put("imports", block.optString("imports", ""))
                put("parameters", block.optString("parameters", ""))
            }
            blocksArr.put(b)
        }

        val shareJson = JSONObject().apply {
            put("palette_name", palName)
            put("palette_color", palColor)
            put("blocks", blocksArr)
        }

        val bundle = Bundle().apply {
            putString("palette_json", shareJson.toString())
        }
        findNavController().navigate(R.id.createDiscussionFragment, bundle)
    }

    private fun showRecycleBinPopup(anchor: View, blockIndex: Int) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "恢复")
        popup.menu.add(0, 2, 0, "永久删除")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showMoveToPaletteDialog(blockIndex)
                2 -> deleteBlock(blockIndex)
            }
            true
        }
        popup.show()
    }

    // ── Block Operations ──

    private fun duplicateBlock(blockIndex: Int) {
        val original = allBlocks[blockIndex]
        val copy = JSONObject(original.toString())
        val name = copy.optString("name", "block")
        copy.put("name", "${name}_copy${(10..99).random()}")
        allBlocks.add(blockIndex + 1, copy)
        saveBlocks()
        currentPalette?.let { showBlocksInPalette(it) }
    }

    private fun moveToRecycleBin(blockIndex: Int) {
        allBlocks[blockIndex].put("palette", "-1")
        saveBlocks()
        currentPalette?.let { showBlocksInPalette(it) }
    }

    private fun deleteBlock(blockIndex: Int) {
        allBlocks.removeAt(blockIndex)
        saveBlocks()
        currentPalette?.let { showBlocksInPalette(it) }
    }

    private fun showMoveToPaletteDialog(blockIndex: Int) {
        if (palettes.isEmpty()) {
            Snackbar.make(binding.root, "没有调色板", Snackbar.LENGTH_SHORT).show()
            return
        }
        val names = palettes.map { it.optString("name", "") }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("移动到调色板")
            .setItems(names) { _, which ->
                allBlocks[blockIndex].put("palette", (which + 9).toString())
                saveBlocks()
                currentPalette?.let { showBlocksInPalette(it) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Palette Dialogs ──

    private fun showAddPaletteDialog() {
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        layout.addView(TextView(ctx).apply { text = "名称"; textSize = 12f })
        val etName = EditText(ctx).apply { textSize = 14f; isSingleLine = true; hint = "My Palette" }
        layout.addView(etName)
        layout.addView(TextView(ctx).apply { text = "颜色 (Hex)"; textSize = 12f; setPadding(0, dp(8), 0, dp(4)) })
        val etColor = EditText(ctx).apply { textSize = 14f; isSingleLine = true; hint = "#FF673AB7"; setText("#FF673AB7") }
        layout.addView(etColor)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("创建调色板")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Snackbar.make(binding.root, "名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val obj = JSONObject().apply {
                    put("name", name)
                    put("color", etColor.text.toString().trim())
                }
                palettes.add(obj)
                savePalettes()
                showPaletteList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditPaletteDialog(position: Int) {
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val pal = palettes[position]
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        layout.addView(TextView(ctx).apply { text = "名称"; textSize = 12f })
        val etName = EditText(ctx).apply { textSize = 14f; isSingleLine = true; setText(pal.optString("name", "")) }
        layout.addView(etName)
        layout.addView(TextView(ctx).apply { text = "颜色 (Hex)"; textSize = 12f; setPadding(0, dp(8), 0, dp(4)) })
        val etColor = EditText(ctx).apply { textSize = 14f; isSingleLine = true; setText(pal.optString("color", "")) }
        layout.addView(etColor)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("编辑调色板")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                palettes[position].put("name", etName.text.toString().trim())
                palettes[position].put("color", etColor.text.toString().trim())
                savePalettes()
                showPaletteList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeletePalette(position: Int) {
        val palName = palettes[position].optString("name", "")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(palName)
            .setMessage("删除此调色板？")
            .setPositiveButton("永久删除") { _, _ ->
                val paletteIndex = position + 9
                // Remove blocks in this palette
                allBlocks.removeAll { it.optString("palette", "") == paletteIndex.toString() }
                // Shift palette indices for blocks above
                allBlocks.forEach {
                    val p = it.optString("palette", "").toIntOrNull() ?: return@forEach
                    if (p > paletteIndex) it.put("palette", (p - 1).toString())
                }
                palettes.removeAt(position)
                saveBlocks()
                savePalettes()
                showPaletteList()
            }
            .setNeutralButton("移到回收站") { _, _ ->
                val paletteIndex = position + 9
                allBlocks.forEach {
                    if (it.optString("palette", "") == paletteIndex.toString()) {
                        it.put("palette", "-1")
                    }
                }
                // Shift palette indices
                allBlocks.forEach {
                    val p = it.optString("palette", "").toIntOrNull() ?: return@forEach
                    if (p > paletteIndex) it.put("palette", (p - 1).toString())
                }
                palettes.removeAt(position)
                saveBlocks()
                savePalettes()
                showPaletteList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Navigation to Block Editor ──

    private fun navigateToEditor(mode: String, blockIndex: Int, paletteIndex: Int) {
        val palColor = palettes.getOrNull(paletteIndex - 9)?.optString("color", "#FF673AB7") ?: "#FF673AB7"
        val bundle = Bundle().apply {
            putString("mode", mode)
            putInt("blockIndex", blockIndex)
            putInt("paletteIndex", paletteIndex)
            putString("paletteColor", palColor)
        }
        findNavController().navigate(R.id.blockEditorFragment, bundle)
    }

    override fun onResume() {
        super.onResume()
        readData()
        val p = currentPalette
        if (p != null) {
            showBlocksInPalette(p)
        } else {
            showPaletteList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
