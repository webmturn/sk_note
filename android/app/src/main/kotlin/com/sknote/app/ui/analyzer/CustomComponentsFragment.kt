package com.sknote.app.ui.analyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentComponentManageBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CustomComponentsFragment : Fragment() {

    private var _binding: FragmentComponentManageBinding? = null
    private val binding get() = _binding!!

    private val componentFile get() = File(
        Environment.getExternalStorageDirectory(),
        ".sketchware/data/system/component.json"
    )

    private var allComponents = mutableListOf<JSONObject>()
    private var searchQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComponentManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                refreshList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.fab.setOnClickListener { navigateToEditor(-1) }

        readData()
        refreshList()
    }

    private fun readData() {
        allComponents.clear()
        try {
            if (componentFile.exists()) {
                val arr = JSONArray(componentFile.readText())
                for (i in 0 until arr.length()) allComponents.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {}
    }

    private fun saveData() {
        val arr = JSONArray()
        allComponents.forEach { arr.put(it) }
        componentFile.parentFile?.mkdirs()
        componentFile.writeText(arr.toString())
    }

    private fun refreshList() {
        val filtered = if (searchQuery.isEmpty()) {
            allComponents.mapIndexed { idx, obj -> idx to obj }
        } else {
            allComponents.mapIndexed { idx, obj -> idx to obj }
                .filter { (_, obj) ->
                    obj.optString("name", "").contains(searchQuery, ignoreCase = true) ||
                    obj.optString("typeName", "").contains(searchQuery, ignoreCase = true) ||
                    obj.optString("description", "").contains(searchQuery, ignoreCase = true)
                }
        }

        if (allComponents.isEmpty()) {
            binding.tvCount.text = "暂无自定义组件"
            binding.tvEmpty.text = "暂无自定义组件\n点击右下角 + 添加"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvList.visibility = View.GONE
        } else if (filtered.isEmpty()) {
            binding.tvCount.text = "组件 (${allComponents.size})"
            binding.tvEmpty.text = "没有匹配的组件"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvList.visibility = View.GONE
        } else {
            binding.tvCount.text = if (searchQuery.isEmpty()) "组件 (${allComponents.size})" else "搜索结果 (${filtered.size}/${allComponents.size})"
            binding.tvEmpty.visibility = View.GONE
            binding.rvList.visibility = View.VISIBLE
            binding.rvList.adapter = ComponentAdapter(filtered)
        }
    }

    private inner class ComponentAdapter(
        private val items: List<Pair<Int, JSONObject>>
    ) : RecyclerView.Adapter<ComponentAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.img_icon)
            val name: TextView = itemView.findViewById(R.id.tv_component_type)
            val desc: TextView = itemView.findViewById(R.id.tv_component_description)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_component, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (originalIndex, comp) = items[position]
            holder.name.text = comp.optString("name", "")
            holder.desc.text = comp.optString("description", "").ifEmpty { "暂无描述" }
            val iconRes = OldResourceIdMapper.getDrawableFromOldId(
                holder.itemView.context, comp.optString("icon", "")
            )
            holder.icon.setImageResource(iconRes)
            holder.icon.imageTintList = null

            holder.itemView.setOnClickListener { navigateToEditor(originalIndex) }
            holder.itemView.setOnLongClickListener {
                showPopup(holder.itemView, originalIndex)
                true
            }
        }

        override fun getItemCount() = items.size
    }

    private fun showPopup(anchor: View, index: Int) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "编辑")
        popup.menu.add(0, 2, 0, "导出")
        popup.menu.add(0, 3, 0, "复制 JSON")
        popup.menu.add(0, 4, 0, "删除")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> navigateToEditor(index)
                2 -> exportComponent(index)
                3 -> copyJson(index)
                4 -> confirmDelete(index)
            }
            true
        }
        popup.show()
    }

    private fun exportComponent(index: Int) {
        try {
            val comp = allComponents[index]
            val name = comp.optString("name", "component")
            val exportDir = File(Environment.getExternalStorageDirectory(), ".sketchware/data/system/export/components")
            exportDir.mkdirs()
            val file = File(exportDir, "$name.json")
            val arr = JSONArray().put(comp)
            file.writeText(arr.toString())
            Snackbar.make(binding.root, "已导出到 ${file.absolutePath}", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "导出失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun copyJson(index: Int) {
        val json = allComponents[index].toString(2)
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("component_json", json))
        Snackbar.make(binding.root, "JSON 已复制", Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmDelete(index: Int) {
        val name = allComponents[index].optString("name", "")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除组件")
            .setMessage("确定删除 \"$name\"？")
            .setPositiveButton("删除") { _, _ ->
                allComponents.removeAt(index)
                saveData()
                refreshList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun navigateToEditor(index: Int) {
        val bundle = Bundle().apply { putInt("pos", index) }
        findNavController().navigate(R.id.componentEditorFragment, bundle)
    }

    override fun onResume() {
        super.onResume()
        readData()
        refreshList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
