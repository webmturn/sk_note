package com.sknote.app.util

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import javax.net.ssl.SSLException

object ErrorUtil {
    fun friendlyMessage(e: Exception): String = when (e) {
        is SocketTimeoutException -> "连接超时，请检查网络后重试"
        is UnknownHostException -> "无法连接服务器，请检查网络"
        is ConnectException -> "连接失败，请检查网络"
        is SSLException -> "安全连接失败，请重试"
        else -> "网络错误，请稍后重试"
    }
}
