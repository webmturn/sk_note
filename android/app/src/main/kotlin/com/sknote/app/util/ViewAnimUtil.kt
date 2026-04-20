package com.sknote.app.util

import android.view.View

/**
 * Fade-in a view over [duration] ms. No-op if already visible with full alpha.
 */
fun View.fadeIn(duration: Long = 200) {
    animate().cancel()
    if (visibility == View.VISIBLE && alpha == 1f) return
    alpha = 0f
    visibility = View.VISIBLE
    animate().alpha(1f).setDuration(duration).setListener(null).start()
}

/**
 * Fade-out a view over [duration] ms, then set GONE. No-op if already gone.
 */
fun View.fadeOut(duration: Long = 200) {
    animate().cancel()
    if (visibility == View.GONE) return
    animate().alpha(0f).setDuration(duration).withEndAction {
        visibility = View.GONE
        alpha = 1f          // reset for next fadeIn
    }.start()
}

/**
 * Swap two state containers with a crossfade: fade-out [from], then fade-in [to].
 */
fun crossfade(from: View, to: View, duration: Long = 200) {
    from.fadeOut(duration)
    to.fadeIn(duration)
}
