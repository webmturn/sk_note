package com.sknote.app.ui.reference

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sknote.app.R

class BlockGuideDialog : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_block_guide, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val colorLegend = view.findViewById<LinearLayout>(R.id.layoutColorLegend)
        val shapeLegend = view.findViewById<LinearLayout>(R.id.layoutShapeLegend)
        val specLegend = view.findViewById<LinearLayout>(R.id.layoutSpecLegend)

        // Color legend
        val colors = listOf(
            Triple("#FFEE7D16", "橙色", "变量 (Variable) — 变量声明与赋值"),
            Triple("#FFCC5B22", "棕色", "列表 (List) — 列表操作"),
            Triple("#FFE1A92A", "黄色", "控制 (Control) — 条件、循环、流程控制"),
            Triple("#FF5CB722", "绿色", "运算 (Operator) — 字符串、比较、逻辑运算"),
            Triple("#FF23B9A9", "青色", "数学 (Math) — 数学函数"),
            Triple("#FF4A6CD4", "蓝色", "视图 (View) — UI控件操作"),
            Triple("#FF2CA5E2", "天蓝", "组件 (Component) — 组件操作、Intent"),
            Triple("#FFA1887F", "灰棕", "文件 (File) — 文件读写、SQLite"),
            Triple("#FF8A55D7", "紫色", "更多积木 (More Block) — 自定义积木块")
        )

        colors.forEach { (colorHex, label, desc) ->
            val row = createColorRow(colorHex, label, desc)
            colorLegend.addView(row)
        }

        // Shape legend
        val shapes = listOf(
            Pair("s 语句块", "执行操作，无返回值。可上下拼接。"),
            Pair("d 数值报告块", "返回数字 (number)。椭圆形，可嵌入数字参数位。"),
            Pair("r 字符串报告块", "返回字符串 (String)。椭圆形，可嵌入字符串参数位。"),
            Pair("b 布尔块", "返回 true/false。菱形，可嵌入条件位。"),
            Pair("c C形块", "包裹其他积木块。用于 if、repeat 等。"),
            Pair("e E形块", "if-else 块，有两个包裹区域。"),
            Pair("f 终止块", "终止执行流。如 finish、break、stop。"),
            Pair("h 帽子块", "事件入口块，位于积木堆栈顶部。")
        )

        shapes.forEach { (title, desc) ->
            val row = createTextRow(title, desc)
            shapeLegend.addView(row)
        }

        // Spec placeholder legend
        val specs = listOf(
            Pair("%s", "字符串参数 — 可输入文本或嵌入字符串报告块"),
            Pair("%d", "数值参数 — 可输入数字或嵌入数值报告块"),
            Pair("%b", "布尔参数 — 可嵌入布尔块 (true/false)"),
            Pair("%m.xxx", "下拉菜单 — 从预定义列表中选择\n如 %m.var(变量)、%m.list(列表)、%m.view(控件)")
        )

        specs.forEach { (token, desc) ->
            val row = createSpecRow(token, desc)
            specLegend.addView(row)
        }
    }

    private fun createColorRow(colorHex: String, label: String, desc: String): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val colorDot = View(ctx).apply {
            val size = (20 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (12 * resources.displayMetrics.density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorHex))
            }
        }
        row.addView(colorDot)

        val textLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvLabel = TextView(ctx).apply {
            text = label
            textSize = 14f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        textLayout.addView(tvLabel)

        val tvDesc = TextView(ctx).apply {
            text = desc
            textSize = 12f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        textLayout.addView(tvDesc)

        row.addView(textLayout)
        return row
    }

    private fun createTextRow(title: String, desc: String): View {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val tvTitle = TextView(ctx).apply {
            text = title
            textSize = 14f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        layout.addView(tvTitle)

        val tvDesc = TextView(ctx).apply {
            text = desc
            textSize = 12f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, (2 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        layout.addView(tvDesc)

        return layout
    }

    private fun createSpecRow(token: String, desc: String): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.TOP
            setPadding(0, 8, 0, 8)
        }

        val tvToken = TextView(ctx).apply {
            text = token
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val w = (72 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(tvToken)

        val tvDesc = TextView(ctx).apply {
            text = desc
            textSize = 12f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(tvDesc)

        return row
    }

    private fun resolveColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
