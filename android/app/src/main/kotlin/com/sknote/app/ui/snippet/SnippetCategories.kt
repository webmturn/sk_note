package com.sknote.app.ui.snippet

object SnippetCategories {
    val keys = listOf("general", "ui", "network", "firebase", "file", "media",
        "sensor", "dialog", "animation", "database", "permission", "intent", "list", "control", "math", "string")
    val labels = listOf("通用", "界面", "网络", "Firebase", "文件", "媒体",
        "传感器", "对话框", "动画", "数据库", "权限", "Intent", "列表", "控制", "数学", "字符串")

    private val map = keys.zip(labels).toMap()

    fun getLabel(category: String): String = map[category] ?: category
}
