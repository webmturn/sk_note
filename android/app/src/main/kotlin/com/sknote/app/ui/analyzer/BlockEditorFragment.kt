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
import com.sknote.app.ui.common.OptionsSheet
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.sknote.app.util.AppSnackbar

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
        // s/d/r/b/c/e/f/h 这套是 Sketchware 的积木形状代号，光看标签说不清，所以每项配一句说明；
        // 文案与积木块图鉴（BlockGuideDialog）里的形状图例一致。
        val descs = listOf(
            "执行操作，无返回值，可上下拼接",
            "C 形块，包裹其他积木块（if / repeat 等）",
            "E 形块，if-else，有两个包裹区域",
            "返回字符串 (String)，椭圆形",
            "返回 true / false，菱形，可嵌入条件位",
            "返回数字 (number)，椭圆形",
            "变量声明与赋值类积木",
            "键值对 (map) 相关操作",
            "终止执行流，如 finish / break / stop",
            "列表 (List) 操作",
            "组件操作与 Intent",
            "事件入口块，位于积木堆栈顶部"
        )
        binding.etType.setText("regular (语句)")
        binding.etType.tag = "regular"

        // 第三十六批：原来是 AlertDialog 的 setSingleChoiceItems + 确定/取消，
        // 现在换成 OptionsSheet，选完立即生效（不选直接下滑关掉就等于取消）
        childFragmentManager.setFragmentResultListener(
            OptionsSheet.REQUEST_KEY, viewLifecycleOwner
        ) { _, bundle ->
            val which = bundle.getInt(OptionsSheet.RESULT_INDEX, -1)
            if (which !in types.indices) return@setFragmentResultListener
            binding.etType.setText(labels[which])
            binding.etType.tag = types[which]
            binding.spec2Layout.visibility = if (types[which] == "e") View.VISIBLE else View.GONE
            updatePreview()
        }

        binding.etType.setOnClickListener {
            val currentTag = binding.etType.tag?.toString()?.trim() ?: ""
            OptionsSheet.newInstance(
                title = "选择类型",
                labels = labels.toList(),
                descs = descs,
                selectedIndex = types.indexOf(currentTag)
            ).show(childFragmentManager, "blockType")
        }
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
            AppSnackbar.make(binding.root, "名称不能为空", Snackbar.LENGTH_SHORT).show()
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

            AppSnackbar.make(binding.root, "已保存", Snackbar.LENGTH_SHORT).show()
            findNavController().navigateUp()
        } catch (e: Exception) {
            AppSnackbar.make(binding.root, "保存失败: ${e.message}", Snackbar.LENGTH_LONG).show()
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
