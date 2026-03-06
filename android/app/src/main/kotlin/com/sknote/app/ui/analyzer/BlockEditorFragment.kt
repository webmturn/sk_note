package com.sknote.app.ui.analyzer

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.ui.reference.BlockShapeView
import com.sknote.app.databinding.FragmentBlockEditorBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BlockEditorFragment : Fragment() {

    private var _binding: FragmentBlockEditorBinding? = null
    private val binding get() = _binding!!

    private val blocksFile get() = File(Environment.getExternalStorageDirectory(), ".sketchware/resources/block/My Block/block.json")

    private var mode = "add" // "add" or "edit"
    private var blockIndex = -1
    private var paletteIndex = 9
    private var paletteColor = "#FF673AB7"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBlockEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mode = arguments?.getString("mode", "add") ?: "add"
        blockIndex = arguments?.getInt("blockIndex", -1) ?: -1
        paletteIndex = arguments?.getInt("paletteIndex", 9) ?: 9
        paletteColor = arguments?.getString("paletteColor", "#FF673AB7") ?: "#FF673AB7"

        binding.toolbar.title = if (mode == "edit") "编辑积木" else "添加积木"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupTypeSelector()
        setupParameters()
        setupPreview()

        binding.etColor.setText(paletteColor)

        if (mode == "edit" && blockIndex >= 0) {
            fillInputs()
        }

        JavaSyntaxHighlighter.attachTo(binding.etCode)
        JavaSyntaxHighlighter.attachTo(binding.etImports)

        binding.btnCancel.setOnClickListener { findNavController().navigateUp() }
        binding.btnSave.setOnClickListener { saveBlock() }
    }

    private fun setupTypeSelector() {
        val types = arrayOf("regular", "c", "e", "s", "b", "d", "v", "a", "f", "l", "p", "h")
        val labels = arrayOf(
            "regular (语句)", "c (if 容器)", "e (if-else)", "s (字符串)",
            "b (布尔)", "d (数值)", "v (变量)", "a (map)",
            "f (终止)", "l (列表)", "p (组件)", "h (标题)"
        )
        binding.etType.setText("regular (语句)")
        binding.etType.setOnClickListener {
            val currentTag = binding.etType.tag?.toString()?.trim() ?: ""
            var selected = types.indexOf(currentTag).coerceAtLeast(0)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择类型")
                .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
                .setPositiveButton("确定") { _, _ ->
                    binding.etType.setText(labels[selected])
                    binding.etType.tag = types[selected]
                    binding.spec2Layout.visibility = if (types[selected] == "e") View.VISIBLE else View.GONE
                    updatePreview()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        binding.etType.tag = "regular"
    }

    private fun setupParameters() {
        val params = listOf(
            "%s.inputOnly " to "inputOnly",
            "%s " to "string",
            "%b " to "boolean",
            "%d " to "number",
            "%m.varMap " to "map",
            "%m.view " to "view",
            "%m.textview " to "textView",
            "%m.edittext " to "editText",
            "%m.imageview " to "imageView",
            "%m.listview " to "listView",
            "%m.list " to "list",
            "%m.listMap " to "listMap",
            "%m.listStr " to "listStr",
            "%m.listInt " to "listInt",
            "%m.intent " to "intent",
            "%m.color " to "color",
            "%m.activity " to "activity",
            "%m.resource " to "resource",
            "%m.customViews " to "customViews",
            "%m.layout " to "layout",
            "%m.anim " to "anim",
            "%m.drawable " to "drawable"
        )

        val dp8 = (8 * resources.displayMetrics.density).toInt()
        for ((menu, name) in params) {
            val tv = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(dp8, 0, dp8, 0)
                text = name
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                setOnClickListener {
                    val et = binding.etSpec
                    val start = et.selectionStart
                    val editable = et.text ?: return@setOnClickListener
                    editable.insert(start, menu)
                }
            }
            binding.parametersHolder.addView(tv)
        }
    }

    private fun setupPreview() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etSpec.addTextChangedListener(watcher)
        binding.etColor.addTextChangedListener(watcher)
        updatePreview()
    }

    private fun updatePreview() {
        binding.blockPreview.removeAllViews()
        val spec = binding.etSpec.text?.toString() ?: ""
        val colorStr = binding.etColor.text?.toString() ?: ""
        val typeTag = binding.etType.tag?.toString()?.trim() ?: " "
        val blockType = if (typeTag == "regular") " " else typeTag

        val blockColor = try {
            if (colorStr.startsWith("#")) Color.parseColor(colorStr) else Color.GRAY
        } catch (_: Exception) { Color.GRAY }

        val shape = when (blockType) {
            "b" -> "b"
            "c" -> "c"
            "e" -> "e"
            "d" -> "d"
            "f" -> "f"
            "h" -> "h"
            "s", "v", "a", "l", "p" -> "r"
            else -> "s"
        }

        val blockView = BlockShapeView(requireContext()).apply {
            this.blockColor = blockColor
            this.blockShape = shape
            this.blockScale = 1.5f
            if (spec.isNotEmpty()) {
                this.blockSpec = spec
                this.blockLabel = ""
            } else {
                this.blockSpec = ""
                this.blockLabel = "积木规格"
            }
        }
        binding.blockPreview.addView(blockView)
    }

    private fun fillInputs() {
        try {
            val arr = JSONArray(blocksFile.readText())
            if (blockIndex < arr.length()) {
                val block = arr.getJSONObject(blockIndex)
                binding.etName.setText(block.optString("name", ""))
                binding.etSpec.setText(block.optString("spec", ""))
                binding.etTypeName.setText(block.optString("typeName", ""))
                binding.etCode.setText(block.optString("code", ""))
                binding.etImports.setText(block.optString("imports", ""))

                val colorVal = block.optString("color", paletteColor)
                if (colorVal.isNotEmpty()) binding.etColor.setText(colorVal)

                val typeVal = block.optString("type", " ").trim()
                val types = arrayOf("regular", "c", "e", "s", "b", "d", "v", "a", "f", "l", "p", "h")
                val labels = arrayOf(
                    "regular (语句)", "c (if 容器)", "e (if-else)", "s (字符串)",
                    "b (布尔)", "d (数值)", "v (变量)", "a (map)",
                    "f (终止)", "l (列表)", "p (组件)", "h (标题)"
                )
                val lookupKey = if (typeVal.isEmpty()) "regular" else typeVal
                val idx = types.indexOf(lookupKey).coerceAtLeast(0)
                binding.etType.setText(labels[idx])
                binding.etType.tag = types[idx]

                if (typeVal == "e") {
                    binding.spec2Layout.visibility = View.VISIBLE
                    binding.etSpec2.setText(block.optString("spec2", ""))
                }

                updatePreview()
            }
        } catch (_: Exception) {}
    }

    private fun saveBlock() {
        val name = binding.etName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            Snackbar.make(binding.root, "名称不能为空", Snackbar.LENGTH_SHORT).show()
            return
        }

        val typeTag = binding.etType.tag?.toString()?.trim() ?: " "
        val type = if (typeTag == "regular" || typeTag.isEmpty()) " " else typeTag

        try {
            val arr = if (blocksFile.exists()) JSONArray(blocksFile.readText()) else JSONArray()

            val block = if (mode == "edit" && blockIndex in 0 until arr.length()) {
                arr.getJSONObject(blockIndex)
            } else {
                JSONObject().also { arr.put(it) }
            }

            block.put("name", name)
            block.put("type", type)
            block.put("typeName", binding.etTypeName.text?.toString() ?: "")
            block.put("spec", binding.etSpec.text?.toString() ?: "")
            block.put("color", binding.etColor.text?.toString()?.trim() ?: paletteColor)
            block.put("code", binding.etCode.text?.toString() ?: "")

            if (type == "e") {
                block.put("spec2", binding.etSpec2.text?.toString() ?: "")
            }

            val imports = binding.etImports.text?.toString() ?: ""
            if (imports.isNotEmpty()) block.put("imports", imports)

            if (mode == "add") {
                block.put("palette", paletteIndex.toString())
            }

            blocksFile.parentFile?.mkdirs()
            blocksFile.writeText(arr.toString())

            Snackbar.make(binding.root, "已保存", Snackbar.LENGTH_SHORT).show()
            findNavController().navigateUp()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "保存失败: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun resolveColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
