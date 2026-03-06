package com.sknote.app.ui.reference

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * 精确复刻 Sketchware Pro BaseBlockView 的积木块形状渲染。
 * 参数和绘制逻辑直接对应 BaseBlockView.java 源码。
 */
class BlockShapeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- Sketchware 原始参数（dp 值，与源码一致）----
    private val density = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
    private val borderWidth = (3 * density).toInt()
    private val cornerRadius = (15 * density).toInt()
    private val notchWidth = (3 * density).toInt()
    private val notchDepth = (2 * density).toInt()
    private val topPadding = (15 * density).toInt()
    private val bottomPadding = (15 * density).toInt()
    private val connectorOffset = (15 * density).toInt()
    private val connectorStart = (18 * density).toInt()  // connectorOffset + borderWidth
    private val connectorEnd = (28 * density).toInt()     // connectorStart + 10
    private val connectorEndOffset = (31 * density).toInt() // connectorEnd + borderWidth
    private val connectorW = (6 * density).toInt()
    private val defaultMinWidth = (60 * density).toInt()
    private val minHeight = (12 * density).toInt()
    private val textHeight = (14 * density).toInt()
    private val topSpacing = (2 * density).toInt()
    private val bottomSpacing = (2 * density).toInt()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xCC000000.toInt()
        strokeWidth = maxOf(2f, 1.5f * density)
    }
    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x60FFFFFF.toInt()
        strokeWidth = maxOf(2f, 1.5f * density)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 10f * density
    }
    private val path = Path()

    var blockColor: Int = 0xFFE1A92A.toInt()
        set(value) { field = value; invalidate() }
    var blockShape: String = "s"
        set(value) { field = value; requestLayout(); invalidate() }
    var blockLabel: String = ""
        set(value) { field = value; invalidate() }
    var blockScale: Float = 1f
        set(value) { field = value; requestLayout(); invalidate() }
    var blockSpec: String = ""
        set(value) {
            field = value
            parsedSegments = parseSpec(value)
            requestLayout(); invalidate()
        }

    // ---- Spec 解析 ----
    private sealed class Seg {
        data class Txt(val t: String) : Seg()
        object SStr : Seg()   // %s
        object SNum : Seg()   // %d
        object SBool : Seg()  // %b
        data class SMenu(val n: String) : Seg()  // %m.xxx
    }
    private var parsedSegments: List<Seg> = emptyList()

    private fun parseSpec(spec: String): List<Seg> {
        if (spec.isEmpty()) return emptyList()
        val segs = mutableListOf<Seg>()
        val pat = Regex("%([sdb]|m\\.\\w+)")
        var last = 0
        for (m in pat.findAll(spec)) {
            if (m.range.first > last) {
                val txt = spec.substring(last, m.range.first).trim()
                if (txt.isNotEmpty()) segs.add(Seg.Txt(txt))
            }
            when {
                m.groupValues[1] == "s" -> segs.add(Seg.SStr)
                m.groupValues[1] == "d" -> segs.add(Seg.SNum)
                m.groupValues[1] == "b" -> segs.add(Seg.SBool)
                m.groupValues[1].startsWith("m.") -> segs.add(Seg.SMenu(m.groupValues[1].removePrefix("m.")))
            }
            last = m.range.last + 1
        }
        if (last < spec.length) {
            val rem = spec.substring(last).trim()
            if (rem.isNotEmpty()) segs.add(Seg.Txt(rem))
        }
        return segs
    }

    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val slotShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0x55000000.toInt()
        strokeWidth = maxOf(1f, density * 0.8f)
    }
    private val triPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF999999.toInt() }
    private val menuLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = 0xFF555555.toInt()
        textSize = 7f * density; textAlign = Paint.Align.LEFT
    }
    private val segPad = (3 * density)
    private val slotH = (11 * density)
    private val slotW = (22 * density)
    private val boolW = (18 * density)
    private val specPadH = (8 * density)
    private val menuPadInner = (4 * density)
    private val triSzConst = (2.5f * density)

    private fun menuLabel(name: String): String {
        return name.replaceFirstChar { it.uppercase() } + " :"
    }
    private fun menuSlotWidth(name: String): Float {
        return menuLabelPaint.measureText(menuLabel(name)) + triSzConst * 2 + menuPadInner * 3
    }

    // 计算积木尺寸
    private var bw = 0  // blockWidth
    private var bh = 0  // blockHeight (top bar height)
    private var ch = 0  // contentHeight (substack)
    private var ih = 0  // innerHeight (substack2 for e-block)

    private fun computeSizes() {
        // 源码 L147-154: topSpacing 根据 blockType 不同
        // " "/c/e/f → topSpacing=4, h → topSpacing=8, 其他 → topSpacing=2
        val actualTopSpacing = when (blockShape) {
            "c", "e", "f" -> (4 * density).toInt()
            "h" -> (8 * density).toInt()
            else -> topSpacing
        }
        bh = textHeight + actualTopSpacing + bottomSpacing
        ch = minHeight
        ih = minHeight

        val contentW = if (parsedSegments.isNotEmpty()) {
            var w = specPadH
            for (seg in parsedSegments) {
                w += when (seg) {
                    is Seg.Txt -> textPaint.measureText(seg.t) + segPad
                    is Seg.SStr -> slotW + segPad
                    is Seg.SNum -> slotW + segPad
                    is Seg.SBool -> boolW + segPad
                    is Seg.SMenu -> menuSlotWidth(seg.n) + segPad
                }
            }
            (w + specPadH).toInt()
        } else {
            if (blockLabel.isNotEmpty()) (textPaint.measureText(blockLabel) + 20 * density).toInt() else 0
        }
        bw = maxOf(contentW, defaultMinWidth)
    }

    // 源码 getTotalHeight() L648-662:
    // shapeType 4(语句)/7(帽子)/10(C块) → +borderWidth
    // shapeType 12(E块) → +borderWidth
    // shapeType 5(终止块)/2(布尔)/3(数值) → 不加
    private fun totalHeight(): Int = when (blockShape) {
        "c" -> bh + bottomPadding + ch   // singleSubstack: bh + bottomPadding + contentHeight - borderWidth + borderWidth
        "e" -> bh + ch + bottomPadding + topPadding + ih - borderWidth  // doubleSubstack
        "h" -> bh + borderWidth
        "s" -> bh + borderWidth  // 语句块 shapeType=4
        "f" -> bh               // 终止块 shapeType=5, 无额外
        "b" -> bh               // 布尔块 shapeType=2
        "d" -> bh               // 数值块 shapeType=3
        "r" -> bh               // 字符串报告块, 无额外
        else -> bh + borderWidth
    }

    private val strokePad = (outlinePaint.strokeWidth + 1f).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        computeSizes()
        setMeasuredDimension(
            ((bw + strokePad * 2) * blockScale).toInt(),
            ((totalHeight() + strokePad * 2) * blockScale).toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        computeSizes()
        canvas.save()
        canvas.scale(blockScale, blockScale)
        canvas.translate(strokePad.toFloat(), strokePad.toFloat())
        fillPaint.color = blockColor

        when (blockShape) {
            "b" -> drawBooleanShape(canvas)
            "d" -> drawNumberShape(canvas)
            "r" -> drawRectShape(canvas)
            "c" -> drawSingleSubstackShape(canvas)
            "e" -> drawDoubleSubstackShape(canvas)
            "f" -> drawStatementShape(canvas, hasBottomNotch = false)
            "h" -> drawHatShape(canvas)
            else -> drawStatementShape(canvas, hasBottomNotch = true)
        }

        // 文字标签 / Spec 渲染
        if (parsedSegments.isNotEmpty()) {
            drawSpecSegments(canvas)
        } else if (blockLabel.isNotEmpty()) {
            val textY = when (blockShape) {
                "h" -> connectorW + bh / 2f + textPaint.textSize / 3f
                "c", "e" -> bh / 2f + textPaint.textSize / 3f
                else -> bh / 2f + textPaint.textSize / 3f
            }
            canvas.drawText(blockLabel, bw / 2f, textY, textPaint)
        }
        canvas.restore()
    }

    private fun drawSpecSegments(canvas: Canvas) {
        val centerY = when (blockShape) {
            "h" -> connectorW + bh / 2f
            else -> bh / 2f
        }
        val textY = centerY + textPaint.textSize / 3f
        val leftTextPaint = Paint(textPaint).apply { textAlign = Paint.Align.LEFT }
        var x = specPadH

        for (seg in parsedSegments) {
            when (seg) {
                is Seg.Txt -> {
                    canvas.drawText(seg.t, x, textY, leftTextPaint)
                    x += leftTextPaint.measureText(seg.t) + segPad
                }
                is Seg.SStr -> {
                    val rect = RectF(x, centerY - slotH / 2, x + slotW, centerY + slotH / 2)
                    canvas.drawRect(rect, slotPaint)
                    canvas.drawRect(rect, slotShadowPaint)
                    x += slotW + segPad
                }
                is Seg.SNum -> {
                    val r = slotH / 2
                    val rect = RectF(x, centerY - slotH / 2, x + slotW, centerY + slotH / 2)
                    canvas.drawRoundRect(rect, r, r, slotPaint)
                    canvas.drawRoundRect(rect, r, r, slotShadowPaint)
                    x += slotW + segPad
                }
                is Seg.SBool -> {
                    val half = slotH / 2
                    val bp = Path()
                    bp.moveTo(x + half, centerY + half)
                    bp.lineTo(x, centerY)
                    bp.lineTo(x + half, centerY - half)
                    bp.lineTo(x + boolW - half, centerY - half)
                    bp.lineTo(x + boolW, centerY)
                    bp.lineTo(x + boolW - half, centerY + half)
                    bp.close()
                    canvas.drawPath(bp, slotPaint)
                    canvas.drawPath(bp, slotShadowPaint)
                    x += boolW + segPad
                }
                is Seg.SMenu -> {
                    val mw = menuSlotWidth(seg.n)
                    val menuRect = RectF(x, centerY - slotH / 2, x + mw, centerY + slotH / 2)
                    canvas.drawRect(menuRect, slotPaint)
                    canvas.drawRect(menuRect, slotShadowPaint)
                    val label = menuLabel(seg.n)
                    val labelY = centerY + menuLabelPaint.textSize / 3f
                    canvas.drawText(label, x + menuPadInner, labelY, menuLabelPaint)
                    val triX = x + mw - menuPadInner - triSzConst
                    val tp = Path()
                    tp.moveTo(triX - triSzConst, centerY - triSzConst / 2)
                    tp.lineTo(triX + triSzConst, centerY - triSzConst / 2)
                    tp.lineTo(triX, centerY + triSzConst / 2)
                    tp.close()
                    canvas.drawPath(tp, triPaint)
                    x += mw + segPad
                }
            }
        }
    }

    // ===== 语句块 (shapeType 4/5) =====
    private fun drawStatementShape(canvas: Canvas, hasBottomNotch: Boolean) {
        path.reset()
        drawTopPath(path)
        drawBottomPath(path, bh, hasBottomNotch, 0)
        canvas.drawPath(path, fillPaint)
        // shadow (right + bottom)
        canvas.drawLines(getRightShadow(0, bh), outlinePaint)
        canvas.drawLines(getBottomShadow(bh, hasBottomNotch, 0), outlinePaint)
        // reflection (top)
        canvas.drawLines(getTopReflection(), reflectionPaint)
    }

    // ===== 矩形报告块 shapeType 1 (纯矩形, 无凹槽) =====
    private fun drawRectShape(canvas: Canvas) {
        val w = bw.toFloat()
        val h = bh.toFloat()
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        // shadow: right edge + bottom edge
        val sw = outlinePaint.strokeWidth / 2f
        canvas.drawLine(w - sw, 0f, w - sw, h - sw, outlinePaint)
        canvas.drawLine(w - sw, h - sw, 0f, h - sw, outlinePaint)
        // reflection: top edge + left edge
        canvas.drawLine(0f, sw, w - sw, sw, reflectionPaint)
        canvas.drawLine(sw, 0f, sw, h - sw, reflectionPaint)
    }

    // ===== 布尔块 (shapeType 2) =====
    private fun drawBooleanShape(canvas: Canvas) {
        val h = bh
        val half = h / 2f
        path.reset()
        path.moveTo(half, h.toFloat())
        path.lineTo(0f, half)
        path.lineTo(half, 0f)
        path.lineTo(bw - half, 0f)
        path.lineTo(bw.toFloat(), half)
        path.lineTo(bw - half, h.toFloat())
        path.close()
        canvas.drawPath(path, fillPaint)
        // shadow: right-bottom edges
        val sw = outlinePaint.strokeWidth / 2f
        canvas.drawLine(bw.toFloat() - sw, half, bw - half, h.toFloat() - sw, outlinePaint)
        canvas.drawLine(bw - half, h.toFloat() - sw, half, h.toFloat() - sw, outlinePaint)
        // reflection: left-top edges
        canvas.drawLine(sw, half, half, sw, reflectionPaint)
        canvas.drawLine(half, sw, bw - half, sw, reflectionPaint)
    }

    // ===== 数字块 (shapeType 3) =====
    private fun drawNumberShape(canvas: Canvas) {
        val h = bh.toFloat()
        val half = h / 2f
        path.reset()
        path.moveTo(half, h)
        path.arcTo(RectF(0f, 0f, h, h), 90f, 180f)
        path.lineTo(bw - half, 0f)
        path.arcTo(RectF(bw - h, 0f, bw.toFloat(), h), 270f, 180f)
        path.close()
        canvas.drawPath(path, fillPaint)
        // shadow: bottom arc + line
        val sw = outlinePaint.strokeWidth / 2f
        canvas.drawArc(RectF(bw - h, 0f, bw - sw, h - sw), 330f, 120f, false, outlinePaint)
        canvas.drawLine(bw - half, h - sw, half, h - sw, outlinePaint)
        canvas.drawArc(RectF(sw, 0f, h, h - sw), 90f, 30f, false, outlinePaint)
        // reflection: top arc + line
        canvas.drawArc(RectF(sw, sw, h, h), 150f, 120f, false, reflectionPaint)
        canvas.drawLine(half, sw, bw - half, sw, reflectionPaint)
        canvas.drawArc(RectF(bw - h, sw, bw - sw, h), 270f, 30f, false, reflectionPaint)
    }

    // ===== 帽子块 (shapeType 7) =====
    private fun drawHatShape(canvas: Canvas) {
        path.reset()
        path.moveTo(0f, connectorW.toFloat())
        path.arcTo(RectF(0f, 0f, defaultMinWidth.toFloat(), (connectorW * 2).toFloat()), 180f, 180f)
        path.lineTo((bw - notchWidth).toFloat(), connectorW.toFloat())
        path.lineTo(bw.toFloat(), (connectorW + notchWidth).toFloat())
        drawBottomPath(path, bh, true, 0)
        canvas.drawPath(path, fillPaint)
        canvas.drawLines(getRightShadow(connectorW, bh), outlinePaint)
        canvas.drawLines(getBottomShadow(bh, true, 0), outlinePaint)
    }

    // ===== 单子栈 C块 (shapeType 10) =====
    private fun drawSingleSubstackShape(canvas: Canvas) {
        val substackBottom = bh + ch - borderWidth
        path.reset()
        drawTopPath(path)
        drawBottomPath(path, bh, true, cornerRadius)
        drawSubstackBottomPath(path, substackBottom)
        drawBottomPath(path, topPadding + substackBottom, true, 0)
        canvas.drawPath(path, fillPaint)
        // shadows
        canvas.drawLines(getRightShadow(0, bh), outlinePaint)
        canvas.drawLines(getBottomShadow(bh, true, cornerRadius), outlinePaint)
        canvas.drawLines(getLeftSideShadow(bh, substackBottom), outlinePaint)
        canvas.drawLines(getRightShadow(substackBottom, topPadding + substackBottom), outlinePaint)
        canvas.drawLines(getBottomShadow(topPadding + substackBottom, true, 0), outlinePaint)
        // reflections
        canvas.drawLines(getTopReflection(), reflectionPaint)
        canvas.drawLines(getSubstackReflection(substackBottom, cornerRadius), reflectionPaint)
    }

    // ===== 双子栈 E块 (shapeType 12) =====
    private fun drawDoubleSubstackShape(canvas: Canvas) {
        val sub1 = bh + ch - borderWidth
        val sub2 = bottomPadding + sub1 + ih - borderWidth
        path.reset()
        drawTopPath(path)
        drawBottomPath(path, bh, true, cornerRadius)
        drawSubstackBottomPath(path, sub1)
        drawBottomPath(path, bottomPadding + sub1, true, cornerRadius)
        drawSubstackBottomPath(path, sub2)
        drawBottomPath(path, topPadding + sub2, true, 0)
        canvas.drawPath(path, fillPaint)
        // shadows
        canvas.drawLines(getRightShadow(0, bh), outlinePaint)
        canvas.drawLines(getBottomShadow(bh, true, cornerRadius), outlinePaint)
        canvas.drawLines(getLeftSideShadow(bh, sub1), outlinePaint)
        canvas.drawLines(getRightShadow(sub1, bottomPadding + sub1), outlinePaint)
        canvas.drawLines(getBottomShadow(bottomPadding + sub1, true, cornerRadius), outlinePaint)
        canvas.drawLines(getLeftSideShadow(bottomPadding + sub1, sub2), outlinePaint)
        canvas.drawLines(getRightShadow(sub2, topPadding + sub2), outlinePaint)
        canvas.drawLines(getBottomShadow(topPadding + sub2, true, 0), outlinePaint)
        // reflections
        canvas.drawLines(getTopReflection(), reflectionPaint)
        canvas.drawLines(getSubstackReflection(sub1, cornerRadius), reflectionPaint)
        canvas.drawLines(getSubstackReflection(sub2, cornerRadius), reflectionPaint)
    }

    // ===== 路径构建方法（直接对应 BaseBlockView.java）=====

    private fun drawTopPath(p: Path) {
        p.moveTo(0f, notchWidth.toFloat())
        p.lineTo(notchWidth.toFloat(), 0f)
        p.lineTo(connectorOffset.toFloat(), 0f)
        p.lineTo(connectorStart.toFloat(), borderWidth.toFloat())
        p.lineTo(connectorEnd.toFloat(), borderWidth.toFloat())
        p.lineTo(connectorEndOffset.toFloat(), 0f)
        p.lineTo((bw - notchWidth).toFloat(), 0f)
        p.lineTo(bw.toFloat(), notchWidth.toFloat())
    }

    private fun drawBottomPath(p: Path, y: Int, hasNotch: Boolean, indent: Int) {
        val yf = y.toFloat()
        p.lineTo(bw.toFloat(), yf - notchWidth)
        p.lineTo((bw - notchWidth).toFloat(), yf)
        if (hasNotch) {
            p.lineTo((connectorEndOffset + indent).toFloat(), yf)
            p.lineTo((connectorEnd + indent).toFloat(), (borderWidth + y).toFloat())
            p.lineTo((connectorStart + indent).toFloat(), (borderWidth + y).toFloat())
            p.lineTo((connectorOffset + indent).toFloat(), yf)
        }
        if (indent > 0) {
            p.lineTo((notchDepth + indent).toFloat(), yf)
            p.lineTo(indent.toFloat(), (y + notchDepth).toFloat())
        } else {
            p.lineTo((indent + notchWidth).toFloat(), yf)
            p.lineTo(0f, yf - notchWidth)
        }
    }

    private fun drawSubstackBottomPath(p: Path, y: Int) {
        p.lineTo(cornerRadius.toFloat(), (y - notchDepth).toFloat())
        p.lineTo((cornerRadius + notchDepth).toFloat(), y.toFloat())
        p.lineTo((bw - notchWidth).toFloat(), y.toFloat())
        p.lineTo(bw.toFloat(), (y + notchWidth).toFloat())
    }

    // ===== 阴影和反射线（对应源码）=====

    private fun getRightShadow(top: Int, bottom: Int): FloatArray {
        val sw = outlinePaint.strokeWidth / 2f
        val x = bw - sw
        return floatArrayOf(x, (top + notchWidth).toFloat(), x, (bottom - notchWidth).toFloat())
    }

    private fun getTopReflection(): FloatArray {
        val sw = reflectionPaint.strokeWidth / 2f
        val nw = notchWidth.toFloat()
        return floatArrayOf(
            sw, nw, nw, sw,  // left corner
            nw, sw, connectorOffset.toFloat(), sw,  // top to connector
            connectorStart.toFloat(), borderWidth + sw, connectorEnd.toFloat(), borderWidth + sw,  // connector top
            connectorEndOffset.toFloat(), sw, (bw - notchWidth).toFloat(), sw  // top right
        )
    }

    private fun getBottomShadow(y: Int, hasNotch: Boolean, indent: Int): FloatArray {
        val sw = outlinePaint.strokeWidth / 2f
        val yf = y - sw
        val nw = notchWidth
        if (!hasNotch) {
            return floatArrayOf(
                bw.toFloat(), (y - nw - sw), (bw - nw).toFloat(), yf,
                (bw - nw).toFloat(), yf, (indent + nw).toFloat(), yf
            )
        }
        return floatArrayOf(
            bw.toFloat(), (y - nw - sw), (bw - nw).toFloat(), yf,
            (bw - nw).toFloat(), yf, (connectorEndOffset + indent).toFloat(), yf,
            (connectorEndOffset + indent).toFloat(), yf, (connectorEnd + indent).toFloat(), (borderWidth + y - sw),
            (connectorEnd + indent).toFloat(), (borderWidth + y - sw), (connectorStart + indent).toFloat(), (borderWidth + y - sw),
            (connectorStart + indent).toFloat(), (borderWidth + y - sw), (connectorOffset + indent).toFloat(), yf,
            (connectorOffset + indent).toFloat(), yf, if (indent > 0) (notchDepth + indent).toFloat() else (indent + nw).toFloat(), yf
        )
    }

    private fun getLeftSideShadow(top: Int, bottom: Int): FloatArray {
        val sw = outlinePaint.strokeWidth / 2f
        val cr = cornerRadius.toFloat()
        val nd = notchDepth.toFloat()
        return floatArrayOf(
            cr + nd, top - sw, cr - sw, top + nd,
            cr - sw, top + nd, cr - sw, bottom - nd
        )
    }

    private fun getSubstackReflection(y: Int, indent: Int): FloatArray {
        val sw = reflectionPaint.strokeWidth / 2f
        return floatArrayOf(
            (indent + notchDepth).toFloat(), y + sw, (bw - notchWidth).toFloat(), y + sw
        )
    }
}
