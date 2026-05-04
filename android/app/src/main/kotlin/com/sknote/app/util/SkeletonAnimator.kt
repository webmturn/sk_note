package com.sknote.app.util

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

class SkeletonAnimator private constructor(
    private val target: View,
    private val owner: LifecycleOwner
) {
    private val animator: ValueAnimator = ValueAnimator.ofFloat(1f, 0.55f, 1f).apply {
        duration = 1100L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { a ->
            val value = a.animatedValue as? Float ?: return@addUpdateListener
            target.alpha = value
        }
    }

    private var started = false
    private var pendingStart = false

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            if (pendingStart) startIfAttached()
        }
        override fun onViewDetachedFromWindow(v: View) {
            animator.cancel()
            if (started) pendingStart = true
        }
    }

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                animator.cancel()
                if (started) pendingStart = true
            }
            Lifecycle.Event.ON_START -> if (started) startIfAttached()
            Lifecycle.Event.ON_DESTROY -> stop()
            else -> Unit
        }
    }

    private fun beginObserving() {
        owner.lifecycle.addObserver(lifecycleObserver)
    }

    fun start() {
        if (started) return
        started = true
        target.visibility = View.VISIBLE
        startIfAttached()
        target.addOnAttachStateChangeListener(attachListener)
    }

    fun stop() {
        if (!started) return
        started = false
        target.removeOnAttachStateChangeListener(attachListener)
        owner.lifecycle.removeObserver(lifecycleObserver)
        animator.cancel()
        target.alpha = 1f
    }

    private fun startIfAttached() {
        if (target.isAttachedToWindow) {
            pendingStart = false
            if (!animator.isRunning) animator.start()
        } else {
            pendingStart = true
        }
    }

    companion object {
        fun start(owner: LifecycleOwner, target: View): SkeletonAnimator {
            val anim = SkeletonAnimator(target, owner)
            anim.start()
            anim.beginObserving()
            return anim
        }
    }
}

fun showSkeleton(skeleton: View, vararg hide: View) {
    hide.forEach { it.visibility = View.GONE }
    skeleton.visibility = View.VISIBLE
    skeleton.alpha = 1f
}

fun hideSkeletonAndShow(skeleton: View, content: View, duration: Long = 180) {
    skeleton.animate().cancel()
    if (content.visibility != View.VISIBLE || content.alpha < 1f) {
        content.alpha = 0f
        content.visibility = View.VISIBLE
        content.animate().alpha(1f).setDuration(duration).start()
    }
    skeleton.animate().alpha(0f).setDuration(duration).withEndAction {
        skeleton.visibility = View.GONE
        skeleton.alpha = 1f
    }.start()
}
