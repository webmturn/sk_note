package com.sknote.app.util

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.absoluteValue

class NestedScrollableHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0f
    private var initialY = 0f

    private val parentViewPager: ViewPager2?
        get() {
            var current: View? = parent as? View
            while (current != null && current !is ViewPager2) {
                current = current.parent as? View
            }
            return current as? ViewPager2
        }

    private fun canChildScroll(orientation: Int, delta: Float): Boolean {
        val target = getChildAt(0) ?: return false
        val direction = when {
            delta > 0f -> -1
            delta < 0f -> 1
            else -> 0
        }
        return when (orientation) {
            ViewPager2.ORIENTATION_HORIZONTAL -> target.canScrollHorizontally(direction)
            ViewPager2.ORIENTATION_VERTICAL -> target.canScrollVertically(direction)
            else -> false
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        handleInterceptTouchEvent(event)
        return super.onInterceptTouchEvent(event)
    }

    private fun handleInterceptTouchEvent(event: MotionEvent) {
        val viewPager = parentViewPager ?: return
        val target = getChildAt(0) ?: return
        val orientation = viewPager.orientation

        if (
            !target.canScrollHorizontally(-1) &&
            !target.canScrollHorizontally(1) &&
            !target.canScrollVertically(-1) &&
            !target.canScrollVertically(1)
        ) {
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                parent.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - initialX
                val dy = event.y - initialY
                val isVpHorizontal = orientation == ViewPager2.ORIENTATION_HORIZONTAL

                val scaledDx = dx.absoluteValue * if (isVpHorizontal) 0.5f else 1f
                val scaledDy = dy.absoluteValue * if (isVpHorizontal) 1f else 0.5f

                if (scaledDx > touchSlop || scaledDy > touchSlop) {
                    if (isVpHorizontal == (scaledDy > scaledDx)) {
                        parent.requestDisallowInterceptTouchEvent(false)
                    } else {
                        if (canChildScroll(orientation, if (isVpHorizontal) dx else dy)) {
                            parent.requestDisallowInterceptTouchEvent(true)
                        } else {
                            parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
            }
        }
    }
}
