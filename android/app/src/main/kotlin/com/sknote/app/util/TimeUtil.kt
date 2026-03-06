package com.sknote.app.util

object TimeUtil {

    fun formatRelative(isoTime: String?): String {
        if (isoTime == null) return ""
        return try {
            val clean = isoTime.replace("T", " ").take(19)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = sdf.parse(clean) ?: return clean.take(16)

            val now = System.currentTimeMillis()
            val diff = now - date.time
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            when {
                seconds < 60 -> "刚刚"
                minutes < 60 -> "${minutes}分钟前"
                hours < 24 -> "${hours}小时前"
                days < 7 -> "${days}天前"
                days < 365 -> {
                    val localSdf = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                    localSdf.format(date)
                }
                else -> {
                    val localSdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    localSdf.format(date)
                }
            }
        } catch (e: Exception) {
            isoTime.take(10)
        }
    }

    fun formatDateTime(isoTime: String?): String {
        if (isoTime == null) return ""
        return try {
            val clean = isoTime.replace("T", " ").take(19)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = sdf.parse(clean) ?: return clean.take(16)
            val localSdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            localSdf.format(date)
        } catch (e: Exception) {
            isoTime.take(16).replace("T", " ")
        }
    }
}
