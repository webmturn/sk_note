package com.sknote.app.util

import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import kotlinx.coroutines.flow.first

suspend fun Fragment.requireLoggedIn(anchorView: View, message: String = "请先登录"): Boolean {
    val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
    if (isLoggedIn) return true

    Snackbar.make(anchorView, message, Snackbar.LENGTH_SHORT)
        .setAction("去登录") {
            if (isAdded) {
                findNavController().navigate(R.id.loginFragment)
            }
        }
        .show()
    return false
}
