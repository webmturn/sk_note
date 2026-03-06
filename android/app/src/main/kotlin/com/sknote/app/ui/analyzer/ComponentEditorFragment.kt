package com.sknote.app.ui.analyzer

import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentComponentEditorBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ComponentEditorFragment : Fragment() {

    private var _binding: FragmentComponentEditorBinding? = null
    private val binding get() = _binding!!

    private val componentFile get() = File(
        Environment.getExternalStorageDirectory(),
        ".sketchware/data/system/component.json"
    )

    private var isEditMode = false
    private var position = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComponentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        position = arguments?.getInt("pos", -1) ?: -1
        isEditMode = position >= 0

        binding.toolbar.title = if (isEditMode) "编辑组件" else "添加组件"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnCancel.setOnClickListener { findNavController().navigateUp() }
        binding.btnSave.setOnClickListener { save() }

        binding.tilComponentIcon.setEndIconOnClickListener { showIconSelectorDialog() }

        binding.componentIcon.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateIconPreview(s?.toString()?.trim() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        JavaSyntaxHighlighter.attachTo(binding.componentBuildClass)
        JavaSyntaxHighlighter.attachTo(binding.componentAddVar)
        JavaSyntaxHighlighter.attachTo(binding.componentDefAddVar)
        JavaSyntaxHighlighter.attachTo(binding.componentImports)

        if (isEditMode) {
            fillUp()
        }
    }

    private fun updateIconPreview(iconIdStr: String) {
        val resId = OldResourceIdMapper.getDrawableFromOldId(requireContext(), iconIdStr)
        binding.iconPreview.setImageResource(resId)
    }

    private fun showIconSelectorDialog() {
        val iconIds = OldResourceIdMapper.getAllIconIds()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        val grid = GridView(requireContext()).apply {
            numColumns = 5
            horizontalSpacing = dp(4)
            verticalSpacing = dp(4)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            adapter = object : BaseAdapter() {
                override fun getCount() = iconIds.size
                override fun getItem(pos: Int) = iconIds[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                    val iv = (convertView as? ImageView) ?: ImageView(requireContext()).apply {
                        layoutParams = ViewGroup.LayoutParams(dp(48), dp(48))
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                    }
                    val resId = OldResourceIdMapper.getDrawableFromOldId(requireContext(), iconIds[pos].toString())
                    iv.setImageResource(resId)
                    return iv
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择图标")
            .setView(grid)
            .setNegativeButton("取消", null)
            .show()

        grid.setOnItemClickListener { _, _, pos, _ ->
            binding.componentIcon.setText(iconIds[pos].toString())
            updateIconPreview(iconIds[pos].toString())
            dialog.dismiss()
        }
    }

    private fun fillUp() {
        try {
            if (componentFile.exists()) {
                val arr = JSONArray(componentFile.readText())
                if (position < arr.length()) {
                    val map = arr.getJSONObject(position)
                    binding.componentName.setText(map.optString("name", ""))
                    binding.componentId.setText(map.optString("id", ""))
                    binding.componentIcon.setText(map.optString("icon", ""))
                    binding.componentVarName.setText(map.optString("varName", ""))
                    binding.componentTypeName.setText(map.optString("typeName", ""))
                    binding.componentBuildClass.setText(map.optString("buildClass", ""))
                    binding.componentTypeClass.setText(map.optString("class", ""))
                    binding.componentDescription.setText(map.optString("description", ""))
                    binding.componentDocUrl.setText(map.optString("url", ""))
                    binding.componentAddVar.setText(map.optString("additionalVar", ""))
                    binding.componentDefAddVar.setText(map.optString("defineAdditionalVar", ""))
                    binding.componentImports.setText(map.optString("imports", ""))
                }
            }
        } catch (_: Exception) {}
    }

    private fun save() {
        val name = binding.componentName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            Snackbar.make(binding.root, "名称不能为空", Snackbar.LENGTH_SHORT).show()
            return
        }

        val list = mutableListOf<JSONObject>()
        try {
            if (componentFile.exists()) {
                val arr = JSONArray(componentFile.readText())
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {}

        val map = if (isEditMode && position < list.size) list[position] else JSONObject()
        map.put("name", name)
        map.put("id", binding.componentId.text?.toString()?.trim() ?: "")
        map.put("icon", binding.componentIcon.text?.toString()?.trim() ?: "")
        map.put("varName", binding.componentVarName.text?.toString()?.trim() ?: "")
        map.put("typeName", binding.componentTypeName.text?.toString()?.trim() ?: "")
        map.put("buildClass", binding.componentBuildClass.text?.toString()?.trim() ?: "")
        map.put("class", binding.componentTypeClass.text?.toString()?.trim() ?: "")
        map.put("description", binding.componentDescription.text?.toString() ?: "")
        map.put("url", binding.componentDocUrl.text?.toString()?.trim() ?: "")
        map.put("additionalVar", binding.componentAddVar.text?.toString() ?: "")
        map.put("defineAdditionalVar", binding.componentDefAddVar.text?.toString() ?: "")
        map.put("imports", binding.componentImports.text?.toString() ?: "")

        if (!isEditMode) list.add(map)

        val arr = JSONArray()
        list.forEach { arr.put(it) }
        componentFile.parentFile?.mkdirs()
        componentFile.writeText(arr.toString())

        Snackbar.make(binding.root, "已保存", Snackbar.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
