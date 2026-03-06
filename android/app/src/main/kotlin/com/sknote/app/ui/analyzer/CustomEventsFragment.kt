package com.sknote.app.ui.analyzer

import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sknote.app.R
import com.sknote.app.databinding.FragmentEventManageBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CustomEventsFragment : Fragment() {

    private var _binding: FragmentEventManageBinding? = null
    private val binding get() = _binding!!

    private val eventsFile get() = File(
        Environment.getExternalStorageDirectory(),
        ".sketchware/data/system/events.json"
    )
    private val listenersFile get() = File(
        Environment.getExternalStorageDirectory(),
        ".sketchware/data/system/listeners.json"
    )

    private var allListeners = mutableListOf<JSONObject>()
    private var allEvents = mutableListOf<JSONObject>()
    private var currentListener: String? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showListenerList()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.toolbar.setNavigationOnClickListener {
            if (currentListener != null) {
                showListenerList()
            } else {
                findNavController().navigateUp()
            }
        }

        binding.activityEvents.setOnClickListener { showEventsForListener("") }
        binding.fab.setOnClickListener { showListenerDialog(-1) }

        readData()
        showListenerList()
    }

    // ── Data I/O ──

    private fun readData() {
        allListeners.clear()
        allEvents.clear()
        try {
            if (listenersFile.exists()) {
                val arr = JSONArray(listenersFile.readText())
                for (i in 0 until arr.length()) allListeners.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {}
        try {
            if (eventsFile.exists()) {
                val arr = JSONArray(eventsFile.readText())
                for (i in 0 until arr.length()) allEvents.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {}
    }

    private fun saveListeners() {
        val arr = JSONArray()
        allListeners.forEach { arr.put(it) }
        listenersFile.parentFile?.mkdirs()
        listenersFile.writeText(arr.toString())
    }

    private fun saveEvents() {
        val arr = JSONArray()
        allEvents.forEach { arr.put(it) }
        eventsFile.parentFile?.mkdirs()
        eventsFile.writeText(arr.toString())
    }

    private fun getNumOfEvents(listenerName: String): String {
        val count = allEvents.count { it.optString("listener", "") == listenerName }
        return "事件: $count"
    }

    // ── Level 1: Listener List ──

    private fun showListenerList() {
        currentListener = null
        backCallback.isEnabled = false
        binding.toolbar.title = "事件管理"
        binding.toolbar.subtitle = null

        // Show listener views, hide event views
        binding.contentNested.visibility = View.VISIBLE
        binding.eventsContent.visibility = View.GONE
        binding.noEventsLayout.visibility = View.GONE

        binding.fab.text = "新建监听器"
        binding.fab.setIconResource(R.drawable.ic_add)
        binding.fab.setOnClickListener { showListenerDialog(-1) }

        binding.activityEventsDescription.text = getNumOfEvents("")
        binding.listenersRecyclerView.adapter = ListenersAdapter()
    }

    // ── Level 2: Events for a Listener ──

    private fun showEventsForListener(listenerName: String) {
        currentListener = listenerName
        backCallback.isEnabled = true
        binding.toolbar.title = "事件详情"
        binding.toolbar.subtitle = listenerName.ifEmpty { "Activity 事件" }

        // Hide listener views, show event views
        binding.contentNested.visibility = View.GONE

        binding.fab.text = "新建事件"
        binding.fab.setIconResource(R.drawable.ic_add)
        binding.fab.setOnClickListener { navigateToEventEditor(-1, listenerName) }

        val events = allEvents.filter { it.optString("listener", "") == listenerName }

        if (events.isEmpty()) {
            binding.noEventsLayout.visibility = View.VISIBLE
            binding.eventsContent.visibility = View.GONE
        } else {
            binding.noEventsLayout.visibility = View.GONE
            binding.eventsContent.visibility = View.VISIBLE
            binding.eventsRecyclerView.adapter = EventsAdapter(events, listenerName)
        }
    }

    // ── Listeners Adapter ──

    private inner class ListenersAdapter : RecyclerView.Adapter<ListenersAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.event_icon)
            val title: TextView = itemView.findViewById(R.id.event_title)
            val subtitle: TextView = itemView.findViewById(R.id.event_subtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_event, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = allListeners[position]
            val name = item.optString("name", "")

            holder.icon.setImageResource(R.drawable.event_on_response_48dp)
            (holder.icon.parent as? LinearLayout)?.gravity = Gravity.CENTER

            holder.title.text = name
            holder.subtitle.text = getNumOfEvents(name)

            holder.itemView.setOnClickListener { showEventsForListener(name) }
            holder.itemView.setOnLongClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(name)
                    .setItems(arrayOf("编辑", "导出", "删除")) { _, which ->
                        when (which) {
                            0 -> showListenerDialog(position)
                            1 -> exportListener(position)
                            2 -> confirmDeleteListener(position)
                        }
                    }
                    .show()
                true
            }
        }

        override fun getItemCount() = allListeners.size
    }

    // ── Events Adapter ──

    private inner class EventsAdapter(
        private val events: List<JSONObject>,
        private val listenerName: String
    ) : RecyclerView.Adapter<EventsAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.event_icon)
            val title: TextView = itemView.findViewById(R.id.event_title)
            val subtitle: TextView = itemView.findViewById(R.id.event_subtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_event, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val event = events[position]
            val name = event.optString("name", "")
            val varName = event.optString("var", "")

            if (listenerName.isEmpty()) {
                holder.icon.setImageResource(R.drawable.ic_mtrl_code)
            } else {
                val iconId = event.optString("icon", "")
                val iconRes = OldResourceIdMapper.getDrawableFromOldId(holder.itemView.context, iconId)
                holder.icon.setImageResource(iconRes)
                holder.icon.imageTintList = null
            }

            holder.title.text = name
            holder.subtitle.text = if (varName.isEmpty()) "Activity 事件" else varName

            holder.itemView.setOnClickListener {
                val idx = allEvents.indexOf(event)
                if (idx >= 0) navigateToEventEditor(idx, listenerName)
            }
            holder.itemView.setOnLongClickListener {
                val idx = allEvents.indexOf(event)
                if (idx < 0) return@setOnLongClickListener true
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(name)
                    .setMessage("确定要删除此事件吗？")
                    .setPositiveButton("删除") { _, _ ->
                        val currentIdx = allEvents.indexOf(event)
                        if (currentIdx >= 0) {
                            allEvents.removeAt(currentIdx)
                            saveEvents()
                            showEventsForListener(listenerName)
                        }
                    }
                    .setNeutralButton("编辑") { _, _ ->
                        val currentIdx = allEvents.indexOf(event)
                        if (currentIdx >= 0) navigateToEventEditor(currentIdx, listenerName)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        }

        override fun getItemCount() = events.size
    }

    // ── Listener Dialog (matching dialog_add_new_listener.xml) ──

    private fun showListenerDialog(index: Int) {
        val isEdit = index >= 0
        val listener = if (isEdit) allListeners[index] else JSONObject()
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        val scroll = ScrollView(ctx).apply {
            setPadding(dp(20), dp(20), dp(20), 0)
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tilName = TextInputLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val etName = TextInputEditText(ctx).apply {
            hint = "监听器名称"
            setText(listener.optString("name", ""))
        }
        tilName.addView(etName)
        layout.addView(tilName)

        val tilImports = TextInputLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }
        val etImports = TextInputEditText(ctx).apply {
            hint = "自定义导入"
            setText(listener.optString("imports", ""))
        }
        tilImports.addView(etImports)
        layout.addView(tilImports)

        val tilCode = TextInputLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }
        val isIndependent = listener.optString("s", "false") == "true"
        val listenerName = listener.optString("name", "")
        val rawCode = listener.optString("code", "")
        val displayCode = if (isIndependent && listenerName.isNotEmpty()) {
            rawCode.replaceFirst("//$listenerName\n", "")
        } else rawCode

        val etCode = TextInputEditText(ctx).apply {
            hint = "代码"
            setText(displayCode)
        }
        tilCode.addView(etCode)
        layout.addView(tilCode)

        val switchIndependent = MaterialSwitch(ctx).apply {
            text = "独立的类或方法"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            }
            isChecked = isIndependent
        }
        layout.addView(switchIndependent)

        scroll.addView(layout)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (isEdit) "编辑监听器" else "新建监听器")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Snackbar.make(binding.root, "名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val obj = if (isEdit) allListeners[index] else JSONObject()
                obj.put("name", name)
                val code = if (switchIndependent.isChecked) {
                    "//$name\n${etCode.text}"
                } else {
                    etCode.text.toString()
                }
                obj.put("code", code)
                obj.put("s", if (switchIndependent.isChecked) "true" else "false")
                obj.put("imports", etImports.text.toString())
                if (!isEdit) allListeners.add(obj)
                saveListeners()
                showListenerList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportListener(index: Int) {
        try {
            val name = allListeners[index].optString("name", "listener")
            val listenerArr = JSONArray().put(allListeners[index])
            val eventsArr = JSONArray()
            allEvents.filter { it.optString("listener", "") == name }.forEach { eventsArr.put(it) }
            val exportDir = File(Environment.getExternalStorageDirectory(), ".sketchware/data/system/export/events")
            exportDir.mkdirs()
            val file = File(exportDir, "$name.txt")
            file.writeText("$listenerArr\n$eventsArr")
            Toast.makeText(requireContext(), "已导出到 ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "导出失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteListener(index: Int) {
        val name = allListeners[index].optString("name", "")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除监听器")
            .setMessage("确定删除 \"$name\" 及其所有事件？")
            .setPositiveButton("确定") { _, _ ->
                allEvents.removeAll { it.optString("listener", "") == name }
                allListeners.removeAt(index)
                saveListeners()
                saveEvents()
                showListenerList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Navigate to Event Editor Fragment ──

    private fun navigateToEventEditor(eventIndex: Int, listenerName: String) {
        val bundle = Bundle().apply {
            putString("lis_name", listenerName)
            putInt("event_index", eventIndex)
        }
        findNavController().navigate(R.id.eventEditorFragment, bundle)
    }

    override fun onResume() {
        super.onResume()
        readData()
        val lis = currentListener
        if (lis != null) showEventsForListener(lis) else showListenerList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
