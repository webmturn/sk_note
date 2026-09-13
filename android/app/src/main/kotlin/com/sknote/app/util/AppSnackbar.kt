package com.sknote.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R

/**
 * 提示条的统一构造入口，替代直接调用 `Snackbar.make(...)`。
 *
 * 它解决两件事：
 *
 * **一、提示条压在底部内容上。** 提示条默认贴在对齐底部的位置（实测其文本下沿正好落在
 * 内容区下沿 `y=2301`），而页面底部通常还有别的东西：
 * - 顶层四页（首页/参考/讨论区/我的）的内容区延伸到应用底部导航栏之下（正因如此这些
 *   页面才需要 `bottom_nav_content_inset` 让位内边距），导航栏占 `y=2157..2301`；
 * - 详情页有底部操作栏 —— 实测文章/片段/分享页的「点赞」栏与提示条几乎完全重叠
 *   （点赞栏 `[48,2256][1128,2301]`、提示条 `[72,2235][1104,2301]`），提示条绘制在上层，
 *   那几秒里按钮是看不见的。
 *
 * 因此优先级是：**页面自己的底部操作栏（带 [TAG_BOTTOM_BAR] 标记且当前可见）
 * → 可见的应用底部导航栏 → 不偏移**，一律通过 Material 自己的
 * [Snackbar.setAnchorView] 抬到该视图之上（它内部按锚点高度维护 `bottomMargin`，
 * 并在锚点布局变化时自动更新）。
 *
 * **二、配色。** 容器/文字/动作按钮取的是 `colorSurfaceInverse` / `colorOnSurfaceInverse`
 * / `colorPrimaryInverse`（M3 的 Snackbar 就是这么接线的），这三个角色在项目主题里
 * 显式定义，取值为 `snackbar_container` / `snackbar_on_container` / `snackbar_action`
 * （见 values 与 values-night 两个目录下的 colors.xml）。
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

    /**
     * 页面底部操作栏的标记。带此 tag 的视图会被当作提示条的锚点，提示条落在它**上方**
     * 而不是盖住它。同一个页面可能有多个互斥状态（例如讨论页的「已登录 / 游客 / 已锁定」
     * 三套回复栏），[findBottomBar] 只认当前 **可见** 的那个 —— 不可见的视图高度可能是
     * 上次布局的残留值（底部导航栏上踩过这个坑，见下），拿它当锚点会把提示条抬错位置。
     */
    private const val TAG_BOTTOM_BAR = "snackbar_anchor"

    fun make(view: View, message: CharSequence, duration: Int = Snackbar.LENGTH_SHORT): Snackbar {
        val snackbar = Snackbar.make(view, message, duration)
        val bottomBar = findBottomBar(view)
        // 判 isShown：底部导航在压栈页会被置为 GONE，但 `getHeight()` 仍保留上次布局的
        // 旧值（实测残留约 165px），Material 会拿它当锚点高度、把提示条无故抬高。
        val bottomNav = view.context.findActivity()
            ?.findViewById<View>(R.id.bottom_nav)
            ?.takeIf { it.isShown }
        (bottomBar ?: bottomNav)?.let(snackbar::setAnchorView)
        return snackbar
    }

    private fun findBottomBar(root: View): View? {
        if (root.getTag() == TAG_BOTTOM_BAR && root.isShown) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findBottomBar(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
