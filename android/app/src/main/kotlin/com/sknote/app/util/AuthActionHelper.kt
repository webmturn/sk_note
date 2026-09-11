package com.sknote.app.util

import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import kotlinx.coroutines.flow.first

fun Fragment.slideNavOptions(): NavOptions {
    return NavOptions.Builder()
        .setEnterAnim(R.anim.slide_in_right)
        .setExitAnim(R.anim.slide_out_left)
        .setPopEnterAnim(R.anim.slide_in_left)
        .setPopExitAnim(R.anim.slide_out_right)
        .build()
}

suspend fun Fragment.requireLoggedIn(anchorView: View, message: String = "请先登录"): Boolean {
    val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
    if (isLoggedIn) return true

    Snackbar.make(anchorView, message, Snackbar.LENGTH_SHORT)
        .setAction("去登录") {
            if (isAdded) {
                findNavController().navigate(R.id.loginFragment, null, slideNavOptions())
            }
        }
        .show()
    return false
}

suspend fun Fragment.requireRolesOrExit(
    allowedRoles: Set<String>,
    deniedMessage: String = "无权访问此页面",
    anchorView: View? = null
): Boolean {
    val tokenManager = ApiClient.getTokenManager()
    val isLoggedIn = tokenManager.isLoggedIn().first()
    if (!isLoggedIn) {
        notifyAndExit("请先登录", anchorView)
        return false
    }

    val role = tokenManager.getUserRole().first() ?: "user"
    if (role in allowedRoles) {
        return true
    }

    notifyAndExit(deniedMessage, anchorView)
    return false
}

/**
 * 门禁提示：本函数随后就会 navigateUp()，Fragment 的 Snackbar 会随视图销毁一起消失，
 * 因此有锚点视图时优先用 Snackbar，没有时退回 Toast 才能保证用户看得到。
 */
private fun Fragment.notifyAndExit(message: String, anchorView: View?) {
    if (anchorView != null) {
        Snackbar.make(anchorView, message, Snackbar.LENGTH_SHORT).show()
    } else {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
    if (isAdded) {
        findNavController().navigateUp()
    }
}
