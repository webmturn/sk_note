package com.sknote.app.ui.discussion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.ui.reference.BlockShapeView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BlockShareHelper {

    private const val BLOCK_TAG_START = "<!--SKBLOCK:"
    private const val BLOCK_TAG_END = ":SKBLOCK-->"
    private const val PALETTE_TAG_START = "<!--SKPALETTE:"
    private const val PALETTE_TAG_END = ":SKPALETTE-->"

    /**
     * Encode block JSON into an HTML-comment format that survives markdown processing.
     */
    fun encodeBlockMarkdown(blockJson: JSONObject): String {
        val base64 = android.util.Base64.encodeToString(
            blockJson.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        return "$BLOCK_TAG_START$base64$BLOCK_TAG_END"
    }

    /**
     * Check if content contains a shared block.
     */
    fun containsBlock(content: String): Boolean {
        return content.contains(BLOCK_TAG_START) && content.contains(BLOCK_TAG_END)
    }

    /**
     * Extract block JSON from content. Returns null if not found.
     */
    fun extractBlockJson(content: String): JSONObject? {
        val startIdx = content.indexOf(BLOCK_TAG_START)
        if (startIdx < 0) return null
        val dataStart = startIdx + BLOCK_TAG_START.length
        val endIdx = content.indexOf(BLOCK_TAG_END, dataStart)
        if (endIdx < 0) return null
        val base64 = content.substring(dataStart, endIdx).trim()
        return try {
            val jsonStr = String(android.util.Base64.decode(base64, android.util.Base64.NO_WRAP), Charsets.UTF_8)
            JSONObject(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get the content without the block tag section (for markdown rendering).
     */
    fun getContentWithoutBlock(content: String): String {
        val startIdx = content.indexOf(BLOCK_TAG_START)
        if (startIdx < 0) return content
        val endIdx = content.indexOf(BLOCK_TAG_END, startIdx + BLOCK_TAG_START.length)
        if (endIdx < 0) return content
        val before = content.substring(0, startIdx).trim()
        val after = content.substring(endIdx + BLOCK_TAG_END.length).trim()
        return "$before\n$after".trim()
    }

    /**
     * Get block type display label.
     */
    fun getTypeLabel(type: String): String = when (type.trim()) {
        "c" -> "C 容器"
        "e" -> "if-else"
        "s" -> "字符串"
        "b" -> "布尔"
        "d" -> "数值"
        "v" -> "变量"
        "a" -> "Map"
        "f" -> "终止"
        "l" -> "列表"
        "p" -> "组件"
        "h" -> "标题"
        else -> "语句"
    }

    /**
     * Map block type string to BlockShapeView shape.
     */
    fun mapShape(type: String): String = when (type.trim()) {
        "b" -> "b"
        "c" -> "c"
        "e" -> "e"
        "d" -> "d"
        "f" -> "f"
        "h" -> "h"
        "s", "v", "a", "l", "p" -> "r"
        else -> "s"
    }

    /**
     * Inflate and configure a block preview card view from block JSON.
     * Returns the inflated view. Action buttons are wired up.
     */
    fun createPreviewView(
        context: Context,
        parent: ViewGroup?,
        blockJson: JSONObject,
        showActions: Boolean = true
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_block_share_preview, parent, false)

        val name = blockJson.optString("name", "")
        val type = blockJson.optString("type", " ")
        val spec = blockJson.optString("spec", "")
        val colorStr = blockJson.optString("color", "")
        val code = blockJson.optString("code", "")
        val imports = blockJson.optString("imports", "")

        // Block shape
        val blockShape = view.findViewById<BlockShapeView>(R.id.blockShapePreview)
        val blockColor = try {
            if (colorStr.isNotEmpty()) Color.parseColor(colorStr) else 0xFFE1A92A.toInt()
        } catch (_: Exception) { 0xFFE1A92A.toInt() }
        blockShape.blockColor = blockColor
        blockShape.blockShape = mapShape(type)
        blockShape.blockScale = 1.5f
        if (spec.isNotEmpty()) {
            blockShape.blockSpec = spec
            blockShape.blockLabel = ""
        } else {
            blockShape.blockSpec = ""
            blockShape.blockLabel = name
        }

        // Name & type
        view.findViewById<TextView>(R.id.tvBlockName).text = name
        view.findViewById<TextView>(R.id.tvBlockType).text = getTypeLabel(type)

        // Code
        val tvCode = view.findViewById<TextView>(R.id.tvBlockCode)
        val tvCodeLabel = view.findViewById<TextView>(R.id.tvCodeLabel)
        if (code.isNotEmpty()) {
            tvCode.text = code
            tvCodeLabel.visibility = View.VISIBLE
        } else {
            tvCode.visibility = View.GONE
            tvCodeLabel.visibility = View.GONE
        }

        // Imports
        val tvImports = view.findViewById<TextView>(R.id.tvBlockImports)
        val tvImportsLabel = view.findViewById<TextView>(R.id.tvImportsLabel)
        val hsvImports = view.findViewById<View>(R.id.hsvImports)
        if (imports.isNotEmpty()) {
            tvImports.text = imports
            tvImportsLabel.visibility = View.VISIBLE
            hsvImports.visibility = View.VISIBLE
        }

        // Actions
        val layoutActions = view.findViewById<View>(R.id.layoutActions)
        if (!showActions) {
            layoutActions.visibility = View.GONE
        } else {
            view.findViewById<MaterialButton>(R.id.btnCopyCode).setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val copyText = buildString {
                    if (imports.isNotEmpty()) appendLine(imports).appendLine()
                    append(code)
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("block_code", copyText))
                Snackbar.make(view, "代码已复制", Snackbar.LENGTH_SHORT).show()
            }

            view.findViewById<MaterialButton>(R.id.btnImportBlock).setOnClickListener {
                importBlock(context, blockJson, view)
            }
        }

        return view
    }

    // ── Palette sharing ──

    fun encodePaletteMarkdown(paletteJson: JSONObject): String {
        val base64 = android.util.Base64.encodeToString(
            paletteJson.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        return "$PALETTE_TAG_START$base64$PALETTE_TAG_END"
    }

    fun containsPalette(content: String): Boolean {
        return content.contains(PALETTE_TAG_START) && content.contains(PALETTE_TAG_END)
    }

    fun extractPaletteJson(content: String): JSONObject? {
        val startIdx = content.indexOf(PALETTE_TAG_START)
        if (startIdx < 0) return null
        val dataStart = startIdx + PALETTE_TAG_START.length
        val endIdx = content.indexOf(PALETTE_TAG_END, dataStart)
        if (endIdx < 0) return null
        val base64 = content.substring(dataStart, endIdx).trim()
        return try {
            val jsonStr = String(android.util.Base64.decode(base64, android.util.Base64.NO_WRAP), Charsets.UTF_8)
            JSONObject(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    fun getContentWithoutPalette(content: String): String {
        val startIdx = content.indexOf(PALETTE_TAG_START)
        if (startIdx < 0) return content
        val endIdx = content.indexOf(PALETTE_TAG_END, startIdx + PALETTE_TAG_START.length)
        if (endIdx < 0) return content
        val before = content.substring(0, startIdx).trim()
        val after = content.substring(endIdx + PALETTE_TAG_END.length).trim()
        return "$before\n$after".trim()
    }

    /**
     * Check if content has any share tag (block or palette).
     */
    fun containsAnyShare(content: String): Boolean = containsBlock(content) || containsPalette(content)

    /**
     * Get clean content without any share tags.
     */
    fun getCleanContent(content: String): String {
        var result = content
        if (containsBlock(result)) result = getContentWithoutBlock(result)
        if (containsPalette(result)) result = getContentWithoutPalette(result)
        return result.trim()
    }

    /**
     * Create a palette preview view showing palette info and all blocks.
     */
    fun createPalettePreviewView(
        context: Context,
        parent: ViewGroup?,
        paletteJson: JSONObject,
        showActions: Boolean = true,
        onViewAll: (() -> Unit)? = null
    ): View {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.item_palette_share_preview, parent, false)

        val palName = paletteJson.optString("palette_name", "调色板")
        val palColor = paletteJson.optString("palette_color", "#9E9E9E")
        val blocksArr = paletteJson.optJSONArray("blocks") ?: JSONArray()

        // Header
        view.findViewById<TextView>(R.id.tvPaletteName).text = palName
        view.findViewById<TextView>(R.id.tvBlockCount).text = "${blocksArr.length()} 个积木块"
        val colorBar = view.findViewById<View>(R.id.paletteColorBar)
        try { colorBar.setBackgroundColor(Color.parseColor(palColor)) }
        catch (_: Exception) { colorBar.setBackgroundColor(Color.GRAY) }

        // Block list
        val blockListContainer = view.findViewById<ViewGroup>(R.id.blockListContainer)
        blockListContainer.removeAllViews()
        for (i in 0 until blocksArr.length()) {
            val block = blocksArr.optJSONObject(i) ?: continue
            val blockItem = inflater.inflate(R.layout.item_palette_block_row, blockListContainer, false)

            val name = block.optString("name", "")
            val type = block.optString("type", " ")
            val spec = block.optString("spec", "")
            val colorStr = block.optString("color", palColor)

            val blockShape = blockItem.findViewById<BlockShapeView>(R.id.blockShapeView)
            val blockColor = try {
                if (colorStr.isNotEmpty()) Color.parseColor(colorStr) else Color.parseColor(palColor)
            } catch (_: Exception) { Color.GRAY }
            blockShape.blockColor = blockColor
            blockShape.blockShape = mapShape(type)
            if (spec.isNotEmpty()) {
                blockShape.blockSpec = spec
                blockShape.blockLabel = ""
            } else {
                blockShape.blockSpec = ""
                blockShape.blockLabel = name
            }

            blockItem.findViewById<TextView>(R.id.tvBlockName).text = name
            blockItem.findViewById<TextView>(R.id.tvBlockType).text = getTypeLabel(type)

            blockListContainer.addView(blockItem)
        }

        // Actions
        val layoutActions = view.findViewById<View>(R.id.layoutActions)
        if (!showActions) {
            layoutActions.visibility = View.GONE
        } else {
            view.findViewById<MaterialButton>(R.id.btnImportPalette).setOnClickListener {
                importPalette(context, paletteJson, view)
            }
            val btnViewAll = view.findViewById<MaterialButton>(R.id.btnViewAll)
            if (onViewAll != null) {
                btnViewAll.setOnClickListener { onViewAll() }
            } else {
                btnViewAll.visibility = View.GONE
            }
        }

        return view
    }

    /**
     * Import a shared palette (with all its blocks) into user's custom blocks.
     * Public so PaletteDetailFragment can call it.
     */
    fun importPalettePublic(context: Context, paletteJson: JSONObject, anchor: View) {
        importPalette(context, paletteJson, anchor)
    }

    private fun importPalette(context: Context, paletteJson: JSONObject, anchor: View) {
        val blocksFile = File(Environment.getExternalStorageDirectory(), ".sketchware/resources/block/My Block/block.json")
        val paletteFile = File(Environment.getExternalStorageDirectory(), ".sketchware/resources/block/My Block/palette.json")

        val palName = paletteJson.optString("palette_name", "调色板")
        val palColor = paletteJson.optString("palette_color", "#9E9E9E")
        val blocksArr = paletteJson.optJSONArray("blocks") ?: JSONArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("导入调色板")
            .setMessage("将导入调色板 \"$palName\" 及其 ${blocksArr.length()} 个积木块")
            .setPositiveButton("导入") { _, _ ->
                try {
                    // Read existing palettes
                    val palettes = mutableListOf<JSONObject>()
                    if (paletteFile.exists()) {
                        val arr = JSONArray(paletteFile.readText())
                        for (i in 0 until arr.length()) palettes.add(arr.getJSONObject(i))
                    }

                    // Add new palette
                    val newPal = JSONObject().apply {
                        put("name", palName)
                        put("color", palColor)
                    }
                    palettes.add(newPal)
                    val newPaletteIndex = palettes.size - 1 + 9

                    val palArr = JSONArray()
                    palettes.forEach { palArr.put(it) }
                    paletteFile.parentFile?.mkdirs()
                    paletteFile.writeText(palArr.toString())

                    // Read existing blocks
                    val blocks = mutableListOf<JSONObject>()
                    if (blocksFile.exists()) {
                        val arr = JSONArray(blocksFile.readText())
                        for (i in 0 until arr.length()) blocks.add(arr.getJSONObject(i))
                    }

                    // Add all blocks with new palette index
                    for (i in 0 until blocksArr.length()) {
                        val b = blocksArr.optJSONObject(i) ?: continue
                        val newBlock = JSONObject(b.toString())
                        newBlock.put("palette", newPaletteIndex.toString())
                        blocks.add(newBlock)
                    }

                    val blockArr = JSONArray()
                    blocks.forEach { blockArr.put(it) }
                    blocksFile.parentFile?.mkdirs()
                    blocksFile.writeText(blockArr.toString())

                    Snackbar.make(anchor, "已导入 \"$palName\" (${blocksArr.length()} 个积木块)", Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Snackbar.make(anchor, "导入失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * Import a shared block into the user's custom blocks.
     */
    private fun importBlock(context: Context, blockJson: JSONObject, anchor: View) {
        val blocksFile = File(Environment.getExternalStorageDirectory(), ".sketchware/resources/block/My Block/block.json")
        val paletteFile = File(Environment.getExternalStorageDirectory(), ".sketchware/resources/block/My Block/palette.json")

        // Read existing palettes
        val palettes = mutableListOf<JSONObject>()
        try {
            if (paletteFile.exists()) {
                val arr = JSONArray(paletteFile.readText())
                for (i in 0 until arr.length()) palettes.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {}

        if (palettes.isEmpty()) {
            Snackbar.make(anchor, "没有调色板，请先创建一个调色板", Snackbar.LENGTH_LONG).show()
            return
        }

        val names = palettes.map { it.optString("name", "") }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle("导入到调色板")
            .setItems(names) { _, which ->
                try {
                    val blocks = mutableListOf<JSONObject>()
                    if (blocksFile.exists()) {
                        val arr = JSONArray(blocksFile.readText())
                        for (i in 0 until arr.length()) blocks.add(arr.getJSONObject(i))
                    }

                    val newBlock = JSONObject(blockJson.toString())
                    newBlock.put("palette", (which + 9).toString())
                    blocks.add(newBlock)

                    val arr = JSONArray()
                    blocks.forEach { arr.put(it) }
                    blocksFile.parentFile?.mkdirs()
                    blocksFile.writeText(arr.toString())

                    Snackbar.make(anchor, "已导入到 \"${names[which]}\"", Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Snackbar.make(anchor, "导入失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
