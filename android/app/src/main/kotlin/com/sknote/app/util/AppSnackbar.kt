package com.sknote.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R

/**
 * 提示条的统一构造入口，替代直接调用 `Snackbar.make(...)`。
 *
 * 为什么需要它：顶层四页（首页 / 参考 / 讨论区 / 我的）的内容区**延伸到应用自己的
 * 底部导航栏之下** —— 正因如此这些页面才需要 `bottom_nav_content_inset` 让位内边距。
 * 提示条默认贴在对齐底部的位置（实测其文本下沿正好落在内容区下沿 `y=2301`），
 * 而底部导航栏占 `y=2157..2301`，于是提示条会整条压在导航栏上。
 *
 * 这里用 Material 自己的 [Snackbar.setAnchorView] 把提示条抬到底部导航栏之上：
 * 它内部按锚点高度维护 `bottomMargin`，并在锚点布局变化时自动更新；导航栏在压栈页
 * 会被置为 `GONE`（见 `MainActivity` 的 destination 监听），此时高度按 0 计，
 * 等价于没有偏移。
 *
 * **不要改成自己算偏移量**：试过两条路都走不通，记在这里免得重蹈 ——
 * 1. `snackbar.view.layoutParams` 在 `show()` 之前是基类 `ViewGroup.LayoutParams`，
 *    不是 `MarginLayoutParams`，设置 `bottomMargin` 的代码根本不执行；
 * 2. `ViewCompat.getRootWindowInsets(...)` 拿不到系统 inset：Activity 根有
 *    `fitsSystemWindows="true"`，inset 已被它消费，子视图读到的是 **0**。
 *
 * 用法与 `Snackbar.make` 完全一致（返回值仍是 `Snackbar`，因此
 * `.setAction(…).show()` 这类链式调用不受影响）：
 * ```
 * AppSnackbar.make(binding.root, "已保存").show()
 * AppSnackbar.make(binding.root, "请先登录", Snackbar.LENGTH_LONG).setAction("去登录") { … }.show()
 * ```
 */
object AppSnackbar {

    fun make(view: View, message: CharSequence, duration: Int = Snackbar.LENGTH_SHORT): Snackbar {
        val snackbar = Snackbar.make(view, message, duration)
        val bottomNav = view.context.findActivity()?.findViewById<View>(R.id.bottom_nav)
        // 必须判 isShown：底部导航在压栈页会被置为 GONE，但 `getHeight()` 仍保留上次
        // 布局的旧值（实测残留约 165px），Material 会拿它当锚点高度、把提示条无故抬高。
        if (bottomNav != null && bottomNav.isShown) {
            snackbar.setAnchorView(bottomNav)
        }
        return snackbar
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
