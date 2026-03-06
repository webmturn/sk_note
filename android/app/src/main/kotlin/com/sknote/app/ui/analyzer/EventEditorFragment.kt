package com.sknote.app.ui.analyzer

import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentEventEditorBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EventEditorFragment : Fragment() {

    private var _binding: FragmentEventEditorBinding? = null
    private val binding get() = _binding!!

    private val eventsFile get() = File(
        Environment.getExternalStorageDirectory(),
        ".sketchware/data/system/events.json"
    )

    private var isEdit = false
    private var isActivityEvent = false
    private var listenerName = ""
    private var eventIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            listenerName = args.getString("lis_name", "")
            isActivityEvent = listenerName.isEmpty()
            eventIndex = args.getInt("event_index", -1)
            isEdit = eventIndex >= 0
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupViews()

        if (isEdit) fillUp()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        if (isEdit) {
            binding.toolbar.title = "事件属性"
            val events = loadEvents()
            if (eventIndex in events.indices) {
                binding.toolbar.subtitle = events[eventIndex].optString("name", "")
            }
        } else if (isActivityEvent) {
            binding.toolbar.title = "添加 Activity 事件"
        } else {
            binding.toolbar.title = "添加事件"
            binding.toolbar.subtitle = listenerName
        }
    }

    private fun setupViews() {
        if (isActivityEvent) {
            binding.tilVar.visibility = View.GONE
            binding.eventIcon.setText("2131165298")
            binding.iconContainer.visibility = View.GONE
        }

        binding.eventIcon.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateIconPreview(s?.toString()?.trim() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chooseIcon.setOnClickListener { showIconSelectorDialog() }
        binding.btnCancel.setOnClickListener { findNavController().navigateUp() }
        binding.btnSave.setOnClickListener { save() }

        JavaSyntaxHighlighter.attachTo(binding.eventCode)
    }

    private fun updateIconPreview(iconIdStr: String) {
        val resId = OldResourceIdMapper.getDrawableFromOldId(requireContext(), iconIdStr)
        binding.iconPreview.setImageResource(resId)
        binding.iconPreview.imageTintList = null
    }

    private fun fillUp() {
        val events = loadEvents()
        if (eventIndex !in events.indices) return
        val event = events[eventIndex]

        binding.eventName.setText(event.optString("name", ""))
        binding.eventVar.setText(event.optString("var", ""))
        binding.eventIcon.setText(event.optString("icon", ""))
        binding.eventDesc.setText(event.optString("description", ""))
        binding.eventParams.setText(event.optString("parameters", ""))
        binding.eventSpec.setText(event.optString("headerSpec", ""))
        binding.eventCode.setText(event.optString("code", ""))
    }

    private fun save() {
        val name = binding.eventName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            Snackbar.make(binding.root, "名称不能为空", Snackbar.LENGTH_SHORT).show()
            return
        }
        val spec = binding.eventSpec.text?.toString()?.trim() ?: ""
        val code = binding.eventCode.text?.toString() ?: ""
        if (spec.isEmpty() || code.isEmpty()) {
            Snackbar.make(binding.root, "headerSpec 和 code 不能为空", Snackbar.LENGTH_SHORT).show()
            return
        }

        val iconText = binding.eventIcon.text?.toString()?.trim() ?: ""
        if (!isActivityEvent && !OldResourceIdMapper.isValidIconId(iconText)) {
            binding.tilIcon.error = "无效的图标 ID"
            binding.eventIcon.requestFocus()
            return
        }

        val events = loadEvents()

        val obj = if (isEdit && eventIndex in events.indices) events[eventIndex] else JSONObject()
        obj.put("name", name)
        obj.put("var", binding.eventVar.text?.toString()?.trim() ?: "")
        obj.put("listener", if (isActivityEvent) "" else listenerName)
        obj.put("icon", iconText)
        obj.put("description", binding.eventDesc.text?.toString() ?: "")
        obj.put("parameters", binding.eventParams.text?.toString()?.trim() ?: "")
        obj.put("headerSpec", spec)
        obj.put("code", code)

        if (!isEdit) events.add(obj)

        val arr = JSONArray()
        events.forEach { arr.put(it) }
        eventsFile.parentFile?.mkdirs()
        eventsFile.writeText(arr.toString())

        Snackbar.make(binding.root, "已保存", Snackbar.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    private fun loadEvents(): MutableList<JSONObject> {
        val list = mutableListOf<JSONObject>()
        try {
            if (eventsFile.exists()) {
                val arr = JSONArray(eventsFile.readText())
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
            }
        } catch (_: Exception) {}
        return list
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
            binding.eventIcon.setText(iconIds[pos].toString())
            updateIconPreview(iconIds[pos].toString())
            binding.tilIcon.error = null
            dialog.dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
