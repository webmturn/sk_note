package com.sknote.app.util

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

/**
 * 「向下滚动收起、向上滚动展开」的一行（筛选 chip 行这类）。
 *
 * 实现要点：**用高度动画，不用 translationY**。translationY 只是把这一行滑出视野，它占的高度
 * 还在，下面的列表不会跟着上移 —— 看起来像收起来了，实际一点空间都没让出来。高度动画才是真收起。
 *
 * 细节：
 * - 收起结束时才置 `GONE`、展开开始时先置 `VISIBLE` 且高度为 0 —— 两个方向都不会因为布局
 *   突变而跳一下；
 * - 触顶时强制展开（否则用户滑回顶部却看不到筛选行，会以为功能丢了）；
 * - 手势中途反向：直接取消上一个动画朝反方向走，不排队；
 * - 高度按开始动画那一刻的实测值取，不写死 dp。
 */
fun RecyclerView.hideOnScroll(row: View, hideThreshold: Int = 8, durationMs: Long = 180L) {
    if (row.id == View.NO_ID) return
    var collapsed = false

    // 展开态的高度只在收起时记一次：展开时行是 VISIBLE + height=0，此时 measuredHeight 量不到真实值
    var expandedHeight = 0
    var animator: ValueAnimator? = null

    fun animateTo(from: Int, to: Int, endState: () -> Unit) {
        // 先摘引用再 cancel：ValueAnimator.cancel() 也会回调 onAnimationEnd（先 cancel 后 end），
        // 若引用还在，收起的收尾动作（置 GONE）会在「展开到一半被反向取消」时被执行，行会凭空消失。
        val previous = animator
        animator = null
        previous?.cancel()
        row.layoutParams = row.layoutParams.apply { height = from }
        row.requestLayout()
        animator = ValueAnimator.ofInt(from, to).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                row.layoutParams = row.layoutParams.apply { height = va.animatedValue as Int }
                row.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator !== animation) return
                    endState()
                    animator = null
                }
            })
            start()
        }
    }

    fun collapse() {
        // 只在静止态启动收起：若此刻正在播动画，行高是中间值，拿它当 expandedHeight 会把
        // 「展开高度」记成半截（实测踩过：收起途中反向，记住 55px，之后永远只展开到 55px）。
        if (animator != null) return
        val h = row.height.takeIf { it > 0 } ?: row.measuredHeight
        if (h <= 0) return
        expandedHeight = h
        collapsed = true
        animateTo(h, 0) { row.visibility = View.GONE }
    }

    fun expand() {
        if (!collapsed) return
        // 展开允许打断正在播的收起（手势反向时不排队），此时 collapsed 仍为 true、expandedHeight 可信
        collapsed = false
        if (expandedHeight <= 0) {
            row.visibility = View.VISIBLE
            return
        }
        row.layoutParams = row.layoutParams.apply { height = 0 }
        row.visibility = View.VISIBLE
        animateTo(0, expandedHeight) { }
    }

    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (row.height <= 0 && !collapsed) return // 还没量过，先不动
            when {
                dy > hideThreshold && !collapsed && canScrollVertically(1) -> collapse()
                dy < -hideThreshold && collapsed -> expand()
                // 已经在顶部就保证是展开的
                !canScrollVertically(-1) && collapsed -> expand()
            }
        }
    })
}
