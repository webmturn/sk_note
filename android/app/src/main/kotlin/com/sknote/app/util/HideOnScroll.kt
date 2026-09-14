package com.sknote.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

/**
 * 「向下滚动收起、向上滚动展开」的一行（筛选 chip 行这类）。
 *
 * 结构要求：[space] 是只管占位的容器（`FrameLayout`，`height=wrap_content`，`clipChildren` 默认 true），
 * [content] 是它唯一的孩子 —— 也就是真正的筛选行。
 *
 * **为什么不是简单地把行的高度从 h 动画到 0**（第一版就是这么写的，真机上翻车了）：
 * 那样每一帧都在重新布局这一行，行内的 chip（较高）会先被压没、而垂直居中的排序按钮还留在原地 ——
 * 真机截图里就是「分类 chip 没了、『默认』和排序图标孤零零杵着」，加上下面的行与列表项错位重叠，
 * 观感就是用户报的「会闪」。
 *
 * 现在的做法：**动画开始前把 content 的高度钉死成 h**，之后
 * - `space.height` 在 h↔0 之间动画（真正让出高度，列表跟着上移）；
 * - `content.translationY` 同步在 0↔-h 之间动画（整行往上滑出去，被 space 裁掉）。
 * 于是内容是「被裁掉」而不是「被压扁」，chip 与按钮始终同进同出。
 *
 * 其它细节：
 * - 触顶强制展开（否则滑回顶部看不到筛选行，会以为功能丢了）；
 * - 用累积位移做迟滞，避免滑动末尾的抖动让行反复收起/弹出；
 * - 收起只在静止态启动（动画中不动它），展开允许打断收起；
 * - `cancel()` 前先摘掉引用：`ValueAnimator.cancel()` 也会回调 `onAnimationEnd`，
 *   否则收起的收尾（置 GONE）会在展开途中执行，行会凭空消失。
 */
fun RecyclerView.hideOnScroll(
    space: View,
    content: View,
    hideThreshold: Int = 48,
    durationMs: Long = 180L
) {
    if (space !is ViewGroup || space.childCount == 0) return

    var collapsed = false
    var expandedHeight = 0
    var animator: ValueAnimator? = null
    var accumulated = 0

    fun animate(toCollapsed: Boolean) {
        val h = expandedHeight
        if (h <= 0) return
        // 关键：从**当前高度**接着走，而不是从写死的 0/h 开始。
        // 展开打断正在播的收起时，容器此刻停在半截高度（比如 80px），若从 0 起播，
        // 第一帧会把高度直接拽到 0 再长回来 —— 来回滑动时看到的就是这一下闪。
        val current = space.height.coerceIn(0, h)
        val target = if (toCollapsed) 0 else h

        // 钉死内容高度：space 变矮时内容是「被裁」而不是「被压扁」
        content.layoutParams = content.layoutParams.apply { height = h }

        val previous = animator
        animator = null
        previous?.cancel()

        animator = ValueAnimator.ofInt(current, target).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val v = va.animatedValue as Int
                space.layoutParams = space.layoutParams.apply { height = v }
                content.translationY = -(h - v).toFloat()
                space.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    space.layoutParams = space.layoutParams.apply { height = if (toCollapsed) 0 else h }
                    content.translationY = 0f
                    space.visibility = if (toCollapsed) View.GONE else View.VISIBLE
                    space.requestLayout()
                }
            })
            start()
        }
    }

    fun collapse() {
        if (animator != null) return          // 动画中不动它，等稳定
        if (space.height <= 0) return
        expandedHeight = space.height
        collapsed = true
        animate(true)
    }

    fun expand() {
        if (!collapsed) return                // 展开允许打断正在播的收起
        collapsed = false
        if (expandedHeight <= 0) {
            space.visibility = View.VISIBLE
            return
        }
        space.visibility = View.VISIBLE
        animate(false)
    }

    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            // 迟滞：手在末尾抖一下不应该让筛选行来回弹
            if (dy > 0) accumulated = if (accumulated < 0) dy else accumulated + dy
            else if (dy < 0) accumulated = if (accumulated > 0) dy else accumulated + dy

            when {
                accumulated > hideThreshold && !collapsed && canScrollVertically(1) -> {
                    accumulated = 0
                    collapse()
                }
                accumulated < -hideThreshold && collapsed -> {
                    accumulated = 0
                    expand()
                }
                !canScrollVertically(-1) && collapsed -> {
                    accumulated = 0
                    expand()
                }
            }
        }
    })
}
