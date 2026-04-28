package com.sknote.app.util

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.sknote.app.R

data class IconSpec(
    @DrawableRes val drawableRes: Int? = null,
    val text: String? = null,
)

data class CategoryIconOption(
    val key: String,
    val label: String,
    @DrawableRes val drawableRes: Int,
)

private fun resolveCategoryExactKey(raw: String): String? = when (raw.lowercase()) {
    "", "default", "category" -> "default"
    "discussion", "forum", "comment" -> "discussion"
    "article", "doc", "document" -> "article"
    "reference", "manual", "wiki" -> "reference"
    "book", "guide" -> "book"
    "code", "snippet" -> "code"
    "block", "blocks" -> "blocks"
    "palette" -> "palette"
    "bug" -> "bug"
    "feedback" -> "feedback"
    "share", "download", "resource" -> "share"
    "question", "help", "faq" -> "question"
    "feature", "idea", "request" -> "feature"
    "camera", "photo" -> "camera"
    "calendar", "date" -> "calendar"
    "bookmark", "favorite", "favourite" -> "bookmark"
    "badge", "label" -> "badge"
    "android" -> "android"
    "bluetooth", "bt" -> "bluetooth"
    "firebase" -> "firebase"
    "intent" -> "intent"
    "location", "gps" -> "location"
    "person", "user", "profile" -> "person"
    "file", "storage" -> "file"
    "database", "db", "sqlite" -> "database"
    "network", "api", "http", "web" -> "network"
    "media", "video", "audio", "music" -> "media"
    "notification", "notice", "push" -> "notification"
    "dialog", "popup", "modal" -> "dialog"
    "sharedpref", "shared_pref", "preference", "preferences", "sp" -> "sharedpref"
    "timer", "countdown", "stopwatch" -> "timer"
    "folder", "directory" -> "folder"
    "foldercode", "folder_code" -> "folder_code"
    "star" -> "star"
    "map" -> "map"
    "plus", "add_circle" -> "add_circle"
    "cloud" -> "cloud"
    "settings" -> "settings"
    "info" -> "info"
    "alarm", "clock" -> "alarm"
    else -> when (raw) {
        "📁" -> "default"
        "💬" -> "discussion"
        "📄" -> "article"
        "📚", "📘" -> "book"
        "</>", "💻" -> "code"
        "🧩" -> "blocks"
        "🎨" -> "palette"
        "🐛", "🐞" -> "bug"
        "📝", "📣" -> "feedback"
        "❓", "❔" -> "question"
        "💡", "✨" -> "feature"
        "🔗", "📦" -> "share"
        "👤", "🧑" -> "person"
        "🗂", "📂" -> "file"
        "🗄", "🗃" -> "database"
        "🌐" -> "network"
        "🎬", "🎵" -> "media"
        "🔔" -> "notification"
        "📍" -> "location"
        "⏱", "⏲" -> "timer"
        else -> null
    }
}

private fun resolveCategoryKeywordKey(source: String): String? = when {
    source.contains("参考") || source.contains("引用") || source.contains("reference") || source.contains("manual") || source.contains("wiki") -> "reference"
    source.contains("书") || source.contains("指南") || source.contains("guide") || source.contains("book") -> "book"
    source.contains("调色") || source.contains("palette") -> "palette"
    source.contains("积木") || source.contains("block") -> "blocks"
    source.contains("bug") || source.contains("错误") || source.contains("缺陷") -> "bug"
    source.contains("反馈") || source.contains("feedback") -> "feedback"
    source.contains("提问") || source.contains("question") || source.contains("help") -> "question"
    source.contains("功能") || source.contains("建议") || source.contains("feature") || source.contains("idea") -> "feature"
    source.contains("代码") || source.contains("snippet") || source.contains("code") -> "code"
    source.contains("文章") || source.contains("article") || source.contains("文档") || source.contains("教程") -> "article"
    source.contains("分享") || source.contains("share") || source.contains("资源") || source.contains("下载") -> "share"
    source.contains("讨论") || source.contains("forum") || source.contains("discussion") || source.contains("交流") -> "discussion"
    source.contains("相机") || source.contains("图片") || source.contains("照片") || source.contains("camera") -> "camera"
    source.contains("日历") || source.contains("时间") || source.contains("calendar") -> "calendar"
    source.contains("收藏") || source.contains("bookmark") || source.contains("favorite") -> "bookmark"
    source.contains("徽章") || source.contains("标签") || source.contains("badge") -> "badge"
    source.contains("安卓") || source.contains("android") -> "android"
    source.contains("蓝牙") || source.contains("bluetooth") || source.contains(" bt ") -> "bluetooth"
    source.contains("firebase") || source.contains("火基") || source.contains("云消息") -> "firebase"
    source.contains("intent") || source.contains("跳转") || source.contains("页面传值") -> "intent"
    source.contains("定位") || source.contains("位置") || source.contains("gps") || source.contains("location") -> "location"
    source.contains("用户") || source.contains("个人") || source.contains("资料") || source.contains("profile") || source.contains("person") || source.contains("user") -> "person"
    source.contains("文件") || source.contains("存储") || source.contains("file") || source.contains("storage") -> "file"
    source.contains("数据库") || source.contains("sqlite") || source.contains("database") || source.contains("db") -> "database"
    source.contains("网络") || source.contains("接口") || source.contains("http") || source.contains("api") || source.contains("web") || source.contains("network") -> "network"
    source.contains("媒体") || source.contains("视频") || source.contains("音频") || source.contains("音乐") || source.contains("media") || source.contains("video") || source.contains("audio") -> "media"
    source.contains("通知") || source.contains("提醒") || source.contains("消息") || source.contains("notification") || source.contains("notice") || source.contains("push") -> "notification"
    source.contains("弹窗") || source.contains("对话框") || source.contains("popup") || source.contains("dialog") || source.contains("modal") -> "dialog"
    source.contains("sharedpref") || source.contains("shared pref") || source.contains("sharedpreferences") || source.contains("preference") || source.contains("偏好") || source.contains("配置存储") -> "sharedpref"
    source.contains("计时") || source.contains("定时") || source.contains("timer") || source.contains("countdown") || source.contains("stopwatch") -> "timer"
    source.contains("文件夹") || source.contains("目录") || source.contains("folder") -> "folder"
    source.contains("地图") || source.contains("定位") || source.contains("map") || source.contains("location") -> "map"
    source.contains("星标") || source.contains("star") -> "star"
    source.contains("云") || source.contains("cloud") -> "cloud"
    source.contains("设置") || source.contains("配置") || source.contains("settings") -> "settings"
    source.contains("信息") || source.contains("关于") || source.contains("info") -> "info"
    source.contains("闹钟") || source.contains("时钟") || source.contains("alarm") || source.contains("clock") -> "alarm"
    else -> null
}

object AppIcons {
    object Category {
        val Default = R.drawable.ic_box
        val Discussion = R.drawable.ic_forum
        val Article = R.drawable.ic_article
        val Reference = R.drawable.ic_reference
        val Book = R.drawable.ic_book
        val Code = R.drawable.ic_code
        val Blocks = R.drawable.ic_blocks
        val Palette = R.drawable.ic_palette
        val Bug = R.drawable.ic_bug_report
        val Feedback = R.drawable.ic_feedback
        val Share = R.drawable.ic_share
        val Question = R.drawable.ic_help
        val Feature = R.drawable.ic_feature
        val Camera = R.drawable.ic_camera
        val Calendar = R.drawable.ic_calendar
        val Bookmark = R.drawable.ic_bookmark
        val Badge = R.drawable.ic_badge
        val Android = R.drawable.ic_android
        val Bluetooth = R.drawable.ic_comp_bluetooth
        val Firebase = R.drawable.ic_comp_firebase
        val Intent = R.drawable.ic_comp_intent
        val Location = R.drawable.ic_comp_location
        val Person = R.drawable.ic_person
        val File = R.drawable.ic_comp_file
        val Database = R.drawable.ic_comp_database
        val Network = R.drawable.ic_comp_network
        val Media = R.drawable.ic_comp_media
        val Notification = R.drawable.ic_comp_notification
        val Dialog = R.drawable.ic_comp_dialog
        val SharedPref = R.drawable.ic_comp_sharedpref
        val Timer = R.drawable.ic_comp_timer
        val Folder = R.drawable.ic_folder
        val FolderCode = R.drawable.ic_folder_code
        val Star = R.drawable.ic_star
        val Map = R.drawable.ic_map
        val AddCircle = R.drawable.ic_add_circle
        val Cloud = R.drawable.ic_cloud
        val Settings = R.drawable.ic_settings
        val Info = R.drawable.ic_info
        val Alarm = R.drawable.ic_history
    }

    object Discussion {
        val Pinned = R.drawable.ic_pin
        val PaletteShare = R.drawable.ic_palette
        val BlockShare = R.drawable.ic_blocks
        val General = R.drawable.ic_forum
        val Question = R.drawable.ic_help
        val Feedback = R.drawable.ic_feedback
        val Bug = R.drawable.ic_bug_report
        val Feature = R.drawable.ic_feature
    }

    object Analyzer {
        val Blocks = R.drawable.ic_blocks
        val Categories = R.drawable.ic_list
        val Components = R.drawable.ic_comp_default
        val Events = R.drawable.ic_event_default
        val Projects = R.drawable.ic_category
    }
}

object IconViewBinder {
    fun bind(spec: IconSpec, iconView: ImageView, fallbackTextView: TextView) {
        val drawableRes = spec.drawableRes
        if (drawableRes != null) {
            iconView.visibility = View.VISIBLE
            fallbackTextView.visibility = View.GONE
            iconView.setImageResource(drawableRes)
            return
        }

        val text = spec.text.orEmpty()
        if (text.isNotEmpty()) {
            iconView.visibility = View.GONE
            fallbackTextView.visibility = View.VISIBLE
            fallbackTextView.text = text
            return
        }

        iconView.visibility = View.GONE
        fallbackTextView.visibility = View.GONE
    }
}

object CategoryIconCatalog {
    val options = listOf(
        CategoryIconOption("default", "默认", AppIcons.Category.Default),
        CategoryIconOption("discussion", "讨论", AppIcons.Category.Discussion),
        CategoryIconOption("article", "文章", AppIcons.Category.Article),
        CategoryIconOption("reference", "参考", AppIcons.Category.Reference),
        CategoryIconOption("book", "书籍", AppIcons.Category.Book),
        CategoryIconOption("code", "代码", AppIcons.Category.Code),
        CategoryIconOption("blocks", "积木", AppIcons.Category.Blocks),
        CategoryIconOption("palette", "调色板", AppIcons.Category.Palette),
        CategoryIconOption("bug", "Bug", AppIcons.Category.Bug),
        CategoryIconOption("feedback", "反馈", AppIcons.Category.Feedback),
        CategoryIconOption("share", "分享", AppIcons.Category.Share),
        CategoryIconOption("question", "提问", AppIcons.Category.Question),
        CategoryIconOption("feature", "功能建议", AppIcons.Category.Feature),
        CategoryIconOption("camera", "相机", AppIcons.Category.Camera),
        CategoryIconOption("calendar", "日历", AppIcons.Category.Calendar),
        CategoryIconOption("bookmark", "收藏", AppIcons.Category.Bookmark),
        CategoryIconOption("badge", "徽章", AppIcons.Category.Badge),
        CategoryIconOption("android", "安卓", AppIcons.Category.Android),
        CategoryIconOption("bluetooth", "蓝牙", AppIcons.Category.Bluetooth),
        CategoryIconOption("firebase", "Firebase", AppIcons.Category.Firebase),
        CategoryIconOption("intent", "Intent", AppIcons.Category.Intent),
        CategoryIconOption("location", "定位", AppIcons.Category.Location),
        CategoryIconOption("person", "用户", AppIcons.Category.Person),
        CategoryIconOption("file", "文件", AppIcons.Category.File),
        CategoryIconOption("database", "数据库", AppIcons.Category.Database),
        CategoryIconOption("network", "网络", AppIcons.Category.Network),
        CategoryIconOption("media", "媒体", AppIcons.Category.Media),
        CategoryIconOption("notification", "通知", AppIcons.Category.Notification),
        CategoryIconOption("dialog", "弹窗", AppIcons.Category.Dialog),
        CategoryIconOption("sharedpref", "SharedPref", AppIcons.Category.SharedPref),
        CategoryIconOption("timer", "计时器", AppIcons.Category.Timer),
        CategoryIconOption("folder", "文件夹", AppIcons.Category.Folder),
        CategoryIconOption("folder_code", "代码文件夹", AppIcons.Category.FolderCode),
        CategoryIconOption("star", "星标", AppIcons.Category.Star),
        CategoryIconOption("map", "地图", AppIcons.Category.Map),
        CategoryIconOption("add_circle", "圆形添加", AppIcons.Category.AddCircle),
        CategoryIconOption("cloud", "云", AppIcons.Category.Cloud),
        CategoryIconOption("settings", "设置", AppIcons.Category.Settings),
        CategoryIconOption("info", "信息", AppIcons.Category.Info),
        CategoryIconOption("alarm", "闹钟", AppIcons.Category.Alarm),
    )

    fun findByKey(key: String?): CategoryIconOption? = options.firstOrNull { it.key == key.orEmpty() }

    fun normalizeKey(rawIcon: String?, categoryName: String? = null): String {
        val raw = rawIcon.orEmpty().trim()
        val exact = resolveCategoryExactKey(raw)
        if (exact != null) return exact
        val keyword = resolveCategoryKeywordKey(listOf(raw, categoryName.orEmpty()).joinToString(" ").lowercase())
        return keyword ?: "default"
    }
}

object CategoryIconResolver {
    fun resolve(rawIcon: String?, categoryName: String? = null): IconSpec {
        val raw = rawIcon.orEmpty().trim()
        val exactKey = resolveCategoryExactKey(raw)
        if (exactKey != null) {
            return IconSpec(drawableRes = CategoryIconCatalog.findByKey(exactKey)?.drawableRes ?: AppIcons.Category.Default)
        }
        if (looksLikeEmoji(raw)) return IconSpec(text = raw)

        val keywordSource = listOf(raw, categoryName.orEmpty())
            .joinToString(" ")
            .lowercase()

        val keywordKey = resolveCategoryKeywordKey(keywordSource)
        if (keywordKey != null) {
            return IconSpec(drawableRes = CategoryIconCatalog.findByKey(keywordKey)?.drawableRes ?: AppIcons.Category.Default)
        }

        return IconSpec(drawableRes = AppIcons.Category.Default)
    }

    private fun looksLikeEmoji(raw: String): Boolean {
        if (raw.isBlank() || raw.length > 4) return false
        return raw.any {
            val type = Character.getType(it)
            type == Character.SURROGATE.toInt() || type == Character.OTHER_SYMBOL.toInt()
        }
    }
}

object DiscussionIconResolver {
    @DrawableRes
    fun pinned(): Int = AppIcons.Discussion.Pinned

    @DrawableRes
    fun sharePreview(isPalette: Boolean): Int = if (isPalette) {
        AppIcons.Discussion.PaletteShare
    } else {
        AppIcons.Discussion.BlockShare
    }

    @DrawableRes
    fun category(category: String?): Int = when (category.orEmpty()) {
        "question" -> AppIcons.Discussion.Question
        "feedback" -> AppIcons.Discussion.Feedback
        "bug" -> AppIcons.Discussion.Bug
        "feature" -> AppIcons.Discussion.Feature
        else -> AppIcons.Discussion.General
    }
}

object ReferenceTypeLabels {
    fun get(type: String): String = when (type) {
        "block" -> "积木块"
        "component" -> "组件"
        "widget" -> "控件"
        "event" -> "事件"
        else -> type
    }
}

object ReferenceIconResolver {
    @DrawableRes
    fun resolve(itemName: String, itemType: String): Int {
        val name = itemName.lowercase()
        return when (itemType) {
            "component" -> when {
                name.contains("intent") -> R.drawable.ic_comp_intent
                name.contains("sharedpreferences") -> R.drawable.ic_comp_sharedpref
                name.contains("sqlite") -> R.drawable.ic_comp_database
                name.contains("calendar") -> R.drawable.ic_comp_calendar
                name.contains("vibrator") -> R.drawable.ic_comp_vibrate
                name.contains("timer") -> R.drawable.ic_comp_timer
                name.contains("progressdialog") -> R.drawable.ic_comp_dialog
                name.contains("timepickerdialog") -> R.drawable.ic_comp_timer
                name.contains("dialog") -> R.drawable.ic_comp_dialog
                name.contains("mediaplayer") -> R.drawable.ic_comp_media
                name.contains("soundpool") -> R.drawable.ic_comp_sound
                name.contains("camera") -> R.drawable.ic_comp_camera
                name.contains("filepicker") -> R.drawable.ic_comp_file
                name.contains("requestnetwork") -> R.drawable.ic_comp_network
                name.contains("firebase") -> R.drawable.ic_comp_firebase
                name.contains("interstitial") || name.contains("rewarded") -> R.drawable.ic_comp_ad
                name.contains("objectanimator") -> R.drawable.ic_comp_animation
                name.contains("gyroscope") -> R.drawable.ic_comp_gyroscope
                name.contains("texttospeech") -> R.drawable.ic_comp_tts
                name.contains("speechtotext") -> R.drawable.ic_comp_stt
                name.contains("bluetooth") -> R.drawable.ic_comp_bluetooth
                name.contains("location") -> R.drawable.ic_comp_location
                name.contains("notification") -> R.drawable.ic_comp_notification
                else -> R.drawable.ic_comp_default
            }
            "widget" -> when {
                name.contains("button") && name.contains("image") -> R.drawable.ic_widget_imagebutton
                name.contains("button") && name.contains("float") -> R.drawable.ic_widget_fab
                name.contains("button") && name.contains("radio") -> R.drawable.ic_widget_radio
                name.contains("button") -> R.drawable.ic_widget_button
                name.contains("textview") -> R.drawable.ic_widget_text
                name.contains("edittext") || name.contains("autocomplete") -> R.drawable.ic_widget_edittext
                name.contains("imageview") || name.contains("circleimage") -> R.drawable.ic_widget_image
                name.contains("listview") || name.contains("recyclerview") -> R.drawable.ic_widget_list
                name.contains("gridview") -> R.drawable.ic_widget_grid
                name.contains("spinner") -> R.drawable.ic_widget_spinner
                name.contains("checkbox") -> R.drawable.ic_widget_checkbox
                name.contains("switch") -> R.drawable.ic_widget_switch
                name.contains("seekbar") -> R.drawable.ic_widget_seekbar
                name.contains("ratingbar") -> R.drawable.ic_widget_rating
                name.contains("webview") -> R.drawable.ic_widget_web
                name.contains("progressbar") -> R.drawable.ic_widget_progress
                name.contains("mapview") -> R.drawable.ic_widget_map
                name.contains("calendarview") || name.contains("datepicker") -> R.drawable.ic_widget_calendar
                name.contains("adview") -> R.drawable.ic_widget_ad
                name.contains("videoview") -> R.drawable.ic_widget_video
                name.contains("searchview") -> R.drawable.ic_widget_search
                name.contains("timepicker") -> R.drawable.ic_widget_timer
                name.contains("viewpager") -> R.drawable.ic_widget_viewpager
                name.contains("cardview") -> R.drawable.ic_widget_card
                name.contains("tablayout") -> R.drawable.ic_widget_tab
                name.contains("bottomnavigation") -> R.drawable.ic_widget_bottomnav
                name.contains("drawer") || name.contains("navigation") -> R.drawable.ic_widget_drawer
                name.contains("textinput") -> R.drawable.ic_widget_edittext
                name.contains("scroll") || name.contains("nested") -> R.drawable.ic_widget_scroll
                name.contains("swiperefresh") -> R.drawable.ic_widget_refresh
                name.contains("coordinator") || name.contains("collapsing") -> R.drawable.ic_widget_layout
                name.contains("linear") || name.contains("relative") || name.contains("frame") -> R.drawable.ic_widget_layout
                name.contains("fab") -> R.drawable.ic_widget_fab
                else -> R.drawable.ic_widget_default
            }
            "event" -> when {
                name.contains("longclick") -> R.drawable.ic_event_longclick
                name.contains("doubleclick") -> R.drawable.ic_event_click
                name.contains("click") -> R.drawable.ic_event_click
                name.contains("text") || name.contains("editor") -> R.drawable.ic_event_text
                name.contains("item") || name.contains("selected") -> R.drawable.ic_event_select
                name.contains("checked") -> R.drawable.ic_event_check
                name.contains("seekbar") || name.contains("rating") -> R.drawable.ic_event_seekbar
                name.contains("create") || name.contains("resume") || name.contains("pause") ||
                    name.contains("destroy") || name.contains("start") || name.contains("stop") ||
                    name.contains("backpress") -> R.drawable.ic_event_lifecycle
                name.contains("page") -> R.drawable.ic_event_page
                name.contains("scroll") -> R.drawable.ic_event_scroll
                name.contains("menu") || name.contains("option") -> R.drawable.ic_event_menu
                name.contains("result") || name.contains("permission") -> R.drawable.ic_event_result
                name.contains("firebase") -> R.drawable.ic_event_firebase
                name.contains("network") || name.contains("response") || name.contains("error") -> R.drawable.ic_event_network
                name.contains("refresh") -> R.drawable.ic_event_refresh
                name.contains("tab") -> R.drawable.ic_event_tab
                else -> R.drawable.ic_event_default
            }
            else -> R.drawable.ic_comp_default
        }
    }
}
