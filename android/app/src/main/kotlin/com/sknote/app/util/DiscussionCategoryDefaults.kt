package com.sknote.app.util

import com.sknote.app.data.model.DiscussionCategory

object DiscussionCategoryDefaults {
    val categories = listOf(
        DiscussionCategory(id = 1, slug = "general", name = "综合", description = "通用讨论", icon = "default", sortOrder = 0),
        DiscussionCategory(id = 2, slug = "question", name = "提问", description = "问题求助与答疑", icon = "question", sortOrder = 10),
        DiscussionCategory(id = 3, slug = "feedback", name = "反馈", description = "体验反馈与意见", icon = "feedback", sortOrder = 20),
        DiscussionCategory(id = 4, slug = "bug", name = "Bug", description = "缺陷和异常问题", icon = "bug", sortOrder = 30),
        DiscussionCategory(id = 5, slug = "feature", name = "功能建议", description = "新功能和改进建议", icon = "feature", sortOrder = 40),
    )

    fun label(slug: String?): String = categories.firstOrNull { it.slug == slug }?.name ?: slug.orEmpty()
}
