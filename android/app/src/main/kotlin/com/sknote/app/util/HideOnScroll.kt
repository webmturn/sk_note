package com.sknote.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

/**
 * 滚动时收起 / 回滚时展开的「顶部浮层」（筛选 chip 行这类）。
 *
 * ## 为什么是「浮层 + padding」，而不是「占位容器 + 改高度」
 *
 * 前两版都在改筛选行（或它的占位容器）的高度，那一改就会重新布局 RecyclerView，而
 * **RecyclerView 被改尺寸后会自己回调 `onScrolled`** 报告一个反向的小位移。日志实测（第三十七批）：
 *
 * ```
 * onScrolled dy=1  h=33 → applyHeight v=32
 * onScrolled dy=-2 h=32 → applyHeight v=34     ← 这一下不是我推的，是我上一帧改高度顶出来的
 * ```
 *
 * 手指匀速拖动时 `dy` 本该一路同号，却每隔一帧冒出一个反向值 —— 这就是自激：改高度 → 被顶 →
 * 又把顶出来的位移当用户滚动去改高度。手指停住时没有真实滚动掩盖它，表现就是「界面抖动，
 * 停在某个位置抖得更明显」。
 *
 * 现在：[content] 是 [overlay] 里唯一的浮层，列表用**固定**的 `paddingTop` 给它留出位置
 * （`clipToPadding=false`，条目从浮层下面滑进滑出），收起只是 `translationY` 的纯位移 ——
 * **列表尺寸全程不变**，没有重新布局，也就没有回授。顺带：位移只走渲染属性，比改高度省得多。
 *
 * 调用方需要保证：
 * - [overlay] 高度 = 浮层高度、`clipChildren=true`（滑出去的浮层要被裁掉，不能盖到上面那几行）；
 * - [content] 自身有**不透明背景**（否则列表条目会从它底下透出来）；
 * - RecyclerView 的 `clipToPadding="false"`（本函数会在量到高度后补上 paddingTop）。
 */
fun RecyclerView.hideOnScroll(overlay: View, content: View, settleDurationMs: Long = 120L) {

    var overlayHeight = 0
    var offset = 0f              // 0 = 完全展开；overlayHeight = 完全收起
    var animator: ValueAnimator? = null

    fun translateTo(value: Float) {
        offset = value.coerceIn(0f, overlayHeight.toFloat())
        content.translationY = -offset
    }

    fun animateTo(target: Float) {
        val running = animator
        animator = null
        running?.cancel()
        if (offset == target) return
        animator = ValueAnimator.ofFloat(offset, target).apply {
            duration = settleDurationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { va -> translateTo(va.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    translateTo(target)
                }
            })
            start()
        }
    }

    /** 首次布局后量高度：给列表补等高的 paddingTop，浮层从此只做位移。 */
    fun ensureMeasured(): Boolean {
        if (overlayHeight > 0) return true
        val h = overlay.height
        if (h <= 0) return false
        overlayHeight = h
        setPadding(paddingLeft, paddingTop + h, paddingRight, paddingBottom)
        return true
    }

    /** 列表已在顶部时，浮层必须是完全展开的（否则用户滑到顶却看不到筛选行）。 */
    fun settleAtTop() {
        if (offset > 0f && !canScrollVertically(-1)) animateTo(0f)
    }

    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!ensureMeasured()) return
            if (dy != 0) {
                val running = animator
                animator = null
                running?.cancel()
                translateTo(offset + dy)
            }
            settleAtTop()
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) return
            if (!ensureMeasured()) return
            // 到顶必然展开；其余情况停在半开就就近归位（要么全展开、要么全收起），不留半截
            if (offset > 0f && !canScrollVertically(-1)) {
                animateTo(0f)
            } else if (offset > 0f && offset < overlayHeight) {
                animateTo(if (offset * 2 >= overlayHeight) overlayHeight.toFloat() else 0f)
            }
        }
    })

    // 已经在顶部时，向下拖没有可滚动距离 —— 既没有 onScrolled 也没有滚动状态变化，
    // 只靠滚动回调会留下「行停在收起/半开态、怎么拖都不出来」的死角。抬手时兜一次。
    addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
        override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> settleAtTop()
            }
        }
    })
}
