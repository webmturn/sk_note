package com.sknote.app.ui.common

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.sknote.app.R

/**
 * 「从一列选项里挑一个」的通用底部弹层：排序方式、参考详情页的快速跳转、积木块类型、
 * 调色板选择都走它。
 *
 * 为什么不再用 `MaterialAlertDialogBuilder#setItems / setSingleChoiceItems`（第三十四/三十六批）：
 * 1. 系统默认列表：一排小字、没有说明、长列表就是一堵墙，看起来像未加工的占位；
 * 2. 单选态只有一个系统 radio，选中项与未选中项在视觉上几乎没有差别；
 * 3. 参考页原先的排序菜单更极端 —— 靠 `it.title = "✓ ${it.title}"` 拼字符串假装选中。
 *
 * 现在：底部弹层 + 标题 + 每项「名称 +（可选）一句说明」+ 当前项左侧实心勾
 * （未选中项用 `alpha=0` 的勾占位，文字左边缘不会跳）。之所以能用弹层：本项目的
 * [com.sknote.app.ui.reference.BlockGuideDialog] 与 `BlockCompareDialog` 已经是底部弹层。
 *
 * **注意**：动作类菜单（编辑 / 导出 / 删除 / 设为某角色…）**不适用**这个组件 —— 它们没有
 * 「当前选中项」的概念，继续用对话框更轻。判断标准是「这列选项里有没有一个『当前值』」。
 *
 * 结果通过 FragmentResult 回传（不用回调属性）：弹层开着时若发生配置变更，回调属性会随旧实例
 * 丢失，FragmentResult 不会。
 *
 * 用法：
 * ```
 * childFragmentManager.setFragmentResultListener(OptionsSheet.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
 *     val index = bundle.getInt(OptionsSheet.RESULT_INDEX, -1)
 *     if (index >= 0) { ... }
 * }
 * OptionsSheet.newInstance("排序方式", labels, descs, selectedIndex)
 *     .show(childFragmentManager, "optionsSheet")
 * ```
 */
class OptionsSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_sort_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val labels = arguments?.getStringArrayList(ARG_LABELS).orEmpty()
        val descs = arguments?.getStringArrayList(ARG_DESCS).orEmpty()
        val selected = arguments?.getInt(ARG_SELECTED, -1) ?: -1

        view.findViewById<TextView>(R.id.tvSheetTitle).text =
            arguments?.getString(ARG_TITLE) ?: "请选择"

        val container = view.findViewById<LinearLayout>(R.id.layoutOptions)
        labels.forEachIndexed { index, label ->
            container.addView(buildRow(index, label, descs.getOrNull(index).orEmpty(), index == selected))
        }
    }

    private fun buildRow(index: Int, label: String, desc: String, isSelected: Boolean): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val primary = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorPrimary)
        val onSurface = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant =
            MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurfaceVariant)

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (56 * d).toInt()
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            isClickable = true
            isFocusable = true
            background = selectableItemBackground()
            setOnClickListener { pick(index) }
        }

        // 勾图标：未选中时用 alpha=0 占位（而不是 GONE），这样两项的文字左边缘对齐、不跳动
        val check = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_check)
            imageTintList = ColorStateList.valueOf(primary)
            alpha = if (isSelected) 1f else 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt())
                .apply { marginEnd = (12 * d).toInt() }
        }
        row.addView(check)

        val textBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textBox.addView(TextView(ctx).apply {
            text = label
            textSize = 15f
            setTextColor(if (isSelected) primary else onSurface)
            if (isSelected) setTypeface(typeface, Typeface.BOLD)
        })
        if (desc.isNotEmpty()) {
            textBox.addView(TextView(ctx).apply {
                text = desc
                textSize = 12f
                setTextColor(onSurfaceVariant)
                setPadding(0, (2 * d).toInt(), 0, 0)
            })
        }
        row.addView(textBox)
        return row
    }

    private fun pick(index: Int) {
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_INDEX to index))
        dismiss()
    }

    private fun selectableItemBackground() = TypedValue().let { tv ->
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        ContextCompat.getDrawable(requireContext(), tv.resourceId)
    }

    companion object {
        const val REQUEST_KEY = "options_sheet"
        const val RESULT_INDEX = "result_index"

        private const val ARG_TITLE = "title"
        private const val ARG_LABELS = "labels"
        private const val ARG_DESCS = "descs"
        private const val ARG_SELECTED = "selected"

        fun newInstance(
            title: String,
            labels: List<String>,
            descs: List<String> = emptyList(),
            selectedIndex: Int = -1
        ) = OptionsSheet().apply {
            arguments = bundleOf(
                ARG_TITLE to title,
                ARG_LABELS to ArrayList(labels),
                ARG_DESCS to ArrayList(descs),
                ARG_SELECTED to selectedIndex
            )
        }
    }
}
