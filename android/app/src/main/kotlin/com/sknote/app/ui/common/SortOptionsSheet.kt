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
 * 排序 / 筛选的选项弹层。讨论区与参考手册两处共用，替代原先的 [android.widget.PopupMenu]。
 *
 * 为什么换掉 PopupMenu（第三十四批）：
 * 1. 参考页有 7 个选项，纯文字的一长条没有分组也没有标题，看起来就是系统默认菜单；
 * 2. 它标当前项的办法是给标题拼一个「✓ 」前缀，选中项文字会右移、也没有真正的选中态；
 * 3. 讨论页那个 3 项的连选中标记都没有 —— 两个入口样式还不一致。
 *
 * 现在：底部弹层 + 标题 + 每项「名称 + 一句说明」+ 当前项左侧实心勾（未选中项保留同宽占位，
 * 文字不会左右跳）。之所以能用弹层：本项目的 [com.sknote.app.ui.reference.BlockGuideDialog]
 * 与 `BlockCompareDialog` 已经是底部弹层，两处排序跟着统一。
 *
 * 结果通过 FragmentResult 回传（不用回调属性）：弹层开着时若发生配置变更，回调属性会随旧实例
 * 丢失，FragmentResult 不会。
 *
 * 用法：
 * ```
 * childFragmentManager.setFragmentResultListener(SortOptionsSheet.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
 *     val index = bundle.getInt(SortOptionsSheet.RESULT_INDEX, -1)
 *     if (index >= 0) { ... }
 * }
 * SortOptionsSheet.newInstance("排序方式", labels, descs, selectedIndex)
 *     .show(childFragmentManager, "sortSheet")
 * ```
 */
class SortOptionsSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_sort_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val labels = arguments?.getStringArrayList(ARG_LABELS).orEmpty()
        val descs = arguments?.getStringArrayList(ARG_DESCS).orEmpty()
        val selected = arguments?.getInt(ARG_SELECTED, -1) ?: -1

        view.findViewById<TextView>(R.id.tvSheetTitle).text =
            arguments?.getString(ARG_TITLE) ?: "排序方式"

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
        const val REQUEST_KEY = "sort_options_sheet"
        const val RESULT_INDEX = "result_index"

        private const val ARG_TITLE = "title"
        private const val ARG_LABELS = "labels"
        private const val ARG_DESCS = "descs"
        private const val ARG_SELECTED = "selected"

        fun newInstance(
            title: String,
            labels: List<String>,
            descs: List<String>,
            selectedIndex: Int
        ) = SortOptionsSheet().apply {
            arguments = bundleOf(
                ARG_TITLE to title,
                ARG_LABELS to ArrayList(labels),
                ARG_DESCS to ArrayList(descs),
                ARG_SELECTED to selectedIndex
            )
        }
    }
}
