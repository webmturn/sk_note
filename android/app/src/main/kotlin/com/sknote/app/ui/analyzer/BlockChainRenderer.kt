package com.sknote.app.ui.analyzer

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.sknote.app.ui.reference.BlockShapeView

/**
 * Wraps a [BlockShapeView] of shape "c" or "e" together with one (or two) substack
 * containers, embedding the substack content inside the shape's empty substack
 * region. The shape view's `substack1ExtraHeight` / `substack2ExtraHeight` /
 * `substackExtraWidth` are dynamically set from the substack containers' measured
 * sizes so the c/e shape grows to enclose its real children.
 */
class BlockContainerView(
    ctx: Context,
    val isDoubleStack: Boolean
) : ViewGroup(ctx) {

    val shapeView: BlockShapeView = BlockShapeView(ctx).apply {
        blockShape = if (isDoubleStack) "e" else "c"
        blockScale = 1f
    }
    val substack1: LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }
    val substack2: LinearLayout? =
        if (isDoubleStack) LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        else null

    /**
     * Inner padding added around the embedded substack content.
     * 0 让子块贴合 BlockShapeView 自带的 substack 区边界，最接近 Sketchware-Pro。
     */
    private val substackPad = 0

    init {
        // Order matters for z-order: shape underneath, substacks on top.
        addView(shapeView)
        addView(substack1)
        substack2?.let { addView(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        substack1.measure(unspec, unspec)
        substack2?.measure(unspec, unspec)

        val s1H = substack1.measuredHeight
        val s2H = substack2?.measuredHeight ?: 0
        val maxSubW = maxOf(substack1.measuredWidth, substack2?.measuredWidth ?: 0)

        // Tell the shape view how much room the substack regions need.
        shapeView.substack1ExtraHeight = s1H + substackPad * 2
        shapeView.substack2ExtraHeight = if (substack2 != null) s2H + substackPad * 2 else 0
        shapeView.substackExtraWidth = maxSubW + substackPad * 2

        shapeView.measure(unspec, unspec)
        setMeasuredDimension(shapeView.measuredWidth, shapeView.measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        shapeView.layout(0, 0, shapeView.measuredWidth, shapeView.measuredHeight)
        // computedSubstack*Top / computedSubstackLeftIndent are drawn-coords, but the
        // child block has its own strokePad rim that supplies the strokePad offset
        // automatically; adding another pad here would create a visible gap between
        // the c-block's substack-opening male notch and the first child's female slot.
        val sx = shapeView.computedSubstackLeftIndent() + substackPad
        val sy1 = shapeView.computedSubstack1Top() + substackPad
        substack1.layout(sx, sy1, sx + substack1.measuredWidth, sy1 + substack1.measuredHeight)
        substack2?.let { s2 ->
            val sy2 = shapeView.computedSubstack2Top() + substackPad
            s2.layout(sx, sy2, sx + s2.measuredWidth, sy2 + s2.measuredHeight)
        }
    }
}

/**
 * 把 Sketchware 积木块链渲染成更接近真实编辑器的视觉：
 * - 普通语句块按顺序竖排，块之间用连接线相连。
 * - c 形（控制流，如 if / forEach）块渲染成带左侧凹槽的容器：头部块在上，
 *   子栈在右侧缩进，左侧用与块同色的连接条贯穿，底部用一条短"闭合块"收尾。
 * - e 形（if-else）块在 c 形基础上再画一个 "else" 分隔条 + 第二条子栈。
 * - 终结块（type=`f`）后面的 nextBlock 不再绘制连接线（视觉上断开）。
 */
class BlockChainRenderer(
    private val ctx: Context,
    private val onBlockClick: (SkProjectParser.LogicBlock, String) -> Unit,
    private val onBlockLongClick: (SkProjectParser.LogicBlock, String) -> Unit,
    private val resolveColorAttr: (Int) -> Int
) {

    private val density = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 1f, ctx.resources.displayMetrics
    )
    private fun dp(v: Int): Int = (v * density).toInt()

    /**
     * How much sequential blocks must overlap vertically so the previous block's
     * male connector lands in the next block's female slot AND the strokePad
     * empty rim around both shapes is collapsed.
     */
    private val interlockOverlap =
        BlockShapeView.interlockOverlap(ctx.resources.displayMetrics.density)

    fun render(
        container: LinearLayout,
        blocks: Map<Int, SkProjectParser.LogicBlock>,
        rootId: Int
    ) {
        renderChain(container, blocks, rootId, depth = 0)
    }

    /** Resolve a block to a human-readable inline expression, recursively. */
    fun resolveExpression(
        blocks: Map<Int, SkProjectParser.LogicBlock>,
        blockId: Int,
        depth: Int = 0
    ): String {
        if (depth > 10) return "..."
        val b = blocks[blockId] ?: return "?"
        return fillSpec(b.spec, b.parameters, blocks, depth)
    }

    fun fillSpec(
        spec: String,
        parameters: List<String>,
        blocks: Map<Int, SkProjectParser.LogicBlock>,
        depth: Int = 0
    ): String {
        if (parameters.isEmpty()) return spec
        val sb = StringBuilder()
        var paramIndex = 0
        var i = 0
        while (i < spec.length) {
            if (spec[i] == '%' && i + 1 < spec.length) {
                val start = i
                i++
                while (i < spec.length && spec[i] != ' ' && spec[i] != '%') i++
                if (paramIndex < parameters.size) {
                    sb.append(resolveParamValue(parameters[paramIndex], blocks, depth))
                    paramIndex++
                } else {
                    sb.append(spec.substring(start, i))
                }
            } else {
                sb.append(spec[i]); i++
            }
        }
        return sb.toString().trim()
    }

    private fun resolveParamValue(
        value: String,
        blocks: Map<Int, SkProjectParser.LogicBlock>,
        depth: Int
    ): String {
        if (value.isEmpty()) return "⬚"
        if (value.startsWith("@")) {
            val refId = value.removePrefix("@").toIntOrNull()
            if (refId != null) {
                return "[" + resolveExpression(blocks, refId, depth + 1) + "]"
            }
        }
        return value
    }

    // ---- internal ----

    private fun renderChain(
        container: LinearLayout,
        blocks: Map<Int, SkProjectParser.LogicBlock>,
        startId: Int,
        depth: Int
    ) {
        var currentId = startId
        val visited = HashSet<Int>()
        var first = true
        while (currentId >= 0 && currentId !in visited) {
            visited.add(currentId)
            val block = blocks[currentId] ?: break
            val isTerminal = block.type.trim() == "f"

            val view: View = when (block.type.trim()) {
                "c" -> buildControlBlock(blocks, block, depth, hasElse = false)
                "e" -> buildControlBlock(blocks, block, depth, hasElse = true)
                else -> buildBlockRow(blocks, block)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // 让上一块的凸连接嵌入本块顶部凹槽，与 Sketchware-Pro 一致。
            if (!first) lp.topMargin = -interlockOverlap
            container.addView(view, lp)
            first = false

            if (isTerminal) return
            currentId = block.nextBlock
        }
    }

    private fun buildControlBlock(
        blocks: Map<Int, SkProjectParser.LogicBlock>,
        block: SkProjectParser.LogicBlock,
        depth: Int,
        hasElse: Boolean
    ): View {
        val color = if (block.disabled) Color.GRAY else block.color
        val filled = fillSpec(block.spec, block.parameters, blocks)
        val cv = BlockContainerView(ctx, isDoubleStack = hasElse)
        cv.shapeView.apply {
            blockColor = color
            blockSpec = filled
            if (block.disabled) alpha = 0.5f
            isClickable = true
            isFocusable = true
            setOnClickListener { onBlockClick(block, filled) }
            setOnLongClickListener { onBlockLongClick(block, filled); true }
        }
        if (block.subStack1 >= 0) {
            renderChain(cv.substack1, blocks, block.subStack1, depth + 1)
        }
        if (hasElse && block.subStack2 >= 0 && cv.substack2 != null) {
            renderChain(cv.substack2, blocks, block.subStack2, depth + 1)
        }
        return cv
    }

    private fun buildBlockRow(
        blocks: Map<Int, SkProjectParser.LogicBlock>,
        block: SkProjectParser.LogicBlock
    ): View {
        val filled = fillSpec(block.spec, block.parameters, blocks)
        val shape = mapBlockShape(block.type)
        return BlockShapeView(ctx).apply {
            blockColor = if (block.disabled) Color.GRAY else block.color
            blockShape = shape
            blockSpec = filled
            blockScale = 1f
            if (block.disabled) alpha = 0.5f
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onBlockClick(block, filled) }
            setOnLongClickListener { onBlockLongClick(block, filled); true }
        }
    }

    private fun mapBlockShape(type: String): String = when (type.trim()) {
        "c" -> "c"
        "e" -> "e"
        "f" -> "f"
        "b" -> "b"
        "d" -> "d"
        "r" -> "r"
        "h" -> "h"
        else -> "s"
    }

}
