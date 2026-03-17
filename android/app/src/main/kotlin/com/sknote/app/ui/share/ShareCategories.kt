package com.sknote.app.ui.share

object ShareCategories {
    val keys = listOf("general", "apk", "mod", "resource", "plugin", "tool", "other")
    val labels = listOf("通用", "APK", "Mod", "资源", "插件", "工具", "其他")

    private val map = keys.zip(labels).toMap()

    fun getLabel(category: String): String = map[category] ?: category
}
