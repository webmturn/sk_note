package com.sknote.app.ui.reference

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sknote.app.data.model.ReferenceItem

object ReferenceData {

    private var blocks: List<ReferenceItem> = emptyList()
    private var components: List<ReferenceItem> = emptyList()
    private var widgets: List<ReferenceItem> = emptyList()
    private var events: List<ReferenceItem> = emptyList()
    private var initialized = false
    private var dataVersion = 0
    private const val CURRENT_DATA_VERSION = 2

    fun init(context: Context) {
        if (initialized && dataVersion == CURRENT_DATA_VERSION) return
        initialized = false
        try {
            val gson = Gson()
            val type = object : TypeToken<List<ReferenceItem>>() {}.type
            fun readAsset(name: String): String =
                context.assets.open(name).bufferedReader().use { it.readText() }

            blocks = gson.fromJson(readAsset("blocks.json"), type)
            components = gson.fromJson(readAsset("components.json"), type)
            widgets = gson.fromJson(readAsset("widgets.json"), type)
            events = gson.fromJson(readAsset("events.json"), type)
            initialized = true
            dataVersion = CURRENT_DATA_VERSION
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val subCategories: Map<String, List<String>>
        get() {
            val result = mutableMapOf<String, List<String>>()
            mapOf("block" to blocks, "component" to components, "widget" to widgets, "event" to events)
                .forEach { (type, items) ->
                    val cats = items.map { it.category }.distinct()
                    if (cats.isNotEmpty()) result[type] = cats
                }
            return result
        }

    fun getAllItems(): List<ReferenceItem> = blocks + components + widgets + events

    fun getItemCount(type: String? = null): Int = when (type) {
        "block" -> blocks.size
        "component" -> components.size
        "widget" -> widgets.size
        "event" -> events.size
        else -> blocks.size + components.size + widgets.size + events.size
    }

    fun getBookmarkedItems(ids: Set<Long>): List<ReferenceItem> =
        getAllItems().filter { ids.contains(it.id) }

    fun getByType(type: String): List<ReferenceItem> = when (type) {
        "block" -> blocks
        "component" -> components
        "widget" -> widgets
        "event" -> events
        else -> getAllItems()
    }

    fun getByTypeAndCategory(type: String, category: String): List<ReferenceItem> =
        getByType(type).filter { it.category == category }

    fun getById(id: Long): ReferenceItem? = getAllItems().find { it.id == id }

    fun getByIds(ids: List<Int>): List<ReferenceItem> =
        ids.mapNotNull { id -> getAllItems().find { it.id == id.toLong() } }

    val shapeLabels: Map<String, String> = mapOf(
        "s" to "语句",
        "d" to "数值",
        "r" to "字符串",
        "b" to "布尔",
        "c" to "C形",
        "e" to "E形",
        "f" to "终止",
        "h" to "帽子"
    )

    fun getShapesForType(type: String): List<String> {
        return getByType(type).map { it.shape }.distinct().sortedBy {
            listOf("s", "d", "r", "b", "c", "e", "f", "h").indexOf(it)
        }
    }

    fun getByShape(items: List<ReferenceItem>, shape: String): List<ReferenceItem> =
        items.filter { it.shape == shape }

    // 事件 → 适用控件/组件 映射
    // key = event ID, value = list of widget/component IDs that support this event
    private val eventTargetMap: Map<Int, List<Int>> = mapOf(
        // Click events → clickable widgets
        3000 to listOf(2000,2001,2002,2003,2004,2005,2006,2007,2008,2009,2011,2012,2013,2016,2017,2020,2021,2022,2023,2024,2025,2026,2051),
        3001 to listOf(2000,2001,2002,2003,2004,2006,2007,2009,2016,2017,2020,2021,2022,2025,2026,2051),
        3002 to listOf(2000,2001,2002,2003,2021,2025,2026,2051),
        // Text events → text input widgets
        3010 to listOf(2002,2022,2015,2054),
        3011 to listOf(2002,2022),
        // Selection events
        3020 to listOf(2005,2047),
        3021 to listOf(2004,2020,2023),
        3022 to listOf(2004,2020),
        // State events
        3030 to listOf(2006,2007,2016),
        3031 to listOf(2008),
        3032 to listOf(2017),
        // Lifecycle events → Activity (no specific widget)
        3040 to emptyList(),
        3041 to emptyList(),
        3042 to emptyList(),
        3043 to emptyList(),
        3044 to emptyList(),
        3045 to emptyList(),
        3046 to emptyList(),
        // Page events
        3050 to listOf(2024),
        3051 to listOf(2024),
        // Scroll events
        3060 to listOf(2043,2044,2050),
        3061 to listOf(2004,2020,2023),
        // Menu events → Activity
        3070 to emptyList(),
        3071 to emptyList(),
        // Result events → Activity
        3080 to emptyList(),
        3081 to emptyList(),
        // Refresh/Tab
        3090 to listOf(2045),
        3091 to listOf(2046),
        // Component events
        3095 to listOf(1040),
        3096 to listOf(1040),
        3097 to listOf(1040),
        3098 to listOf(1030),
        3099 to listOf(1030),
        // Timer
        3100 to listOf(1010),
        // Dialog
        3101 to listOf(1013, 1014),
        3102 to listOf(1013, 1014),
        // MediaPlayer
        3103 to listOf(1020),
        3104 to listOf(1020),
        // SpeechToText
        3105 to listOf(1023),
        // Bluetooth
        3106 to listOf(1006),
        3107 to listOf(1006),
        3108 to listOf(1006),
        // Location
        3109 to listOf(1007),
        // FirebaseAuth
        3110 to listOf(1041),
        3111 to listOf(1041),
        3112 to listOf(1041),
        3113 to listOf(1041),
        // FirebaseStorage
        3114 to listOf(1042),
        3115 to listOf(1042),
        3116 to listOf(1042),
        3117 to listOf(1042),
        // Ad
        3118 to listOf(1043, 1044),
        3119 to listOf(1043, 1044),
        3120 to listOf(1043, 1044),
        // Animator
        3121 to listOf(1050),
        // Gyroscope
        3122 to listOf(1060)
    )

    // 控件/组件 → 可用事件 映射 (反向查询)
    fun getEventsForItem(itemId: Long): List<ReferenceItem> {
        val eventIds = eventTargetMap.filter { (_, targets) ->
            targets.contains(itemId.toInt())
        }.keys.toList().sorted()
        return eventIds.mapNotNull { id -> events.find { it.id == id.toLong() } }
    }

    // 事件 → 适用的控件/组件列表
    fun getTargetsForEvent(eventId: Long): List<ReferenceItem> {
        val targetIds = eventTargetMap[eventId.toInt()] ?: return emptyList()
        if (targetIds.isEmpty()) return emptyList()
        return targetIds.mapNotNull { id ->
            (widgets + components).find { it.id == id.toLong() }
        }
    }

    // 事件是否为 Activity 级事件（无特定控件）
    fun isActivityEvent(eventId: Long): Boolean {
        val targets = eventTargetMap[eventId.toInt()]
        return targets != null && targets.isEmpty()
    }

    // 组件/控件声明方式
    val declarationMap: Map<Int, String> = mapOf(
        // Components
        1000 to "组件面板 → 添加 File/Preference → SharedPreferences\n变量命名如: sp1",
        1001 to "组件面板 → 添加 File/Preference → SQLite\n变量命名如: db1",
        1002 to "组件面板 → 添加 Intent → Intent\n变量命名如: intent1",
        1003 to "组件面板 → 添加 File/Preference → Camera\n变量命名如: camera1",
        1004 to "组件面板 → 添加 File/Preference → FilePicker\n变量命名如: fp1",
        1005 to "组件面板 → 添加 Misc → Vibrator\n变量命名如: vibrator1",
        1006 to "组件面板 → 添加 Connectivity → BluetoothConnect\n变量命名如: bt1",
        1007 to "组件面板 → 添加 Connectivity → LocationManager\n变量命名如: location1",
        1008 to "组件面板 → 添加 Misc → Notification\n变量命名如: notif1",
        1010 to "组件面板 → 添加 Misc → Timer\n变量命名如: timer1",
        1011 to "组件面板 → 添加 Misc → Calendar\n变量命名如: cal1",
        1013 to "组件面板 → 添加 Dialog → Dialog\n变量命名如: dialog1",
        1014 to "组件面板 → 添加 Dialog → ProgressDialog\n变量命名如: pd1",
        1015 to "组件面板 → 添加 Dialog → TimePickerDialog\n变量命名如: tp1",
        1020 to "组件面板 → 添加 Media → MediaPlayer\n变量命名如: mp1",
        1021 to "组件面板 → 添加 Media → SoundPool\n变量命名如: sp1",
        1022 to "组件面板 → 添加 Misc → TextToSpeech\n变量命名如: tts1",
        1023 to "组件面板 → 添加 Misc → SpeechToText\n变量命名如: stt1",
        1030 to "组件面板 → 添加 Connectivity → RequestNetwork\n变量命名如: rn1",
        1040 to "组件面板 → 添加 Firebase → FirebaseDB\n变量命名如: firebase1",
        1041 to "组件面板 → 添加 Firebase → FirebaseAuth\n变量命名如: auth1",
        1042 to "组件面板 → 添加 Firebase → FirebaseStorage\n变量命名如: storage1",
        1043 to "组件面板 → 添加 Google → InterstitialAd\n变量命名如: iad1",
        1044 to "组件面板 → 添加 Google → RewardedVideoAd\n变量命名如: rad1",
        1050 to "组件面板 → 添加 Misc → ObjectAnimator\n变量命名如: anim1",
        1060 to "组件面板 → 添加 Misc → Gyroscope\n变量命名如: gyro1",
        // Widgets
        2000 to "设计页面 → 拖入 Button\n设置 id 如: button1",
        2001 to "设计页面 → 拖入 TextView\n设置 id 如: textview1",
        2002 to "设计页面 → 拖入 EditText\n设置 id 如: edittext1",
        2003 to "设计页面 → 拖入 ImageView\n设置 id 如: imageview1",
        2004 to "设计页面 → 拖入 ListView\n设置 id 如: listview1",
        2005 to "设计页面 → 拖入 Spinner\n设置 id 如: spinner1",
        2006 to "设计页面 → 拖入 CheckBox\n设置 id 如: checkbox1",
        2007 to "设计页面 → 拖入 Switch\n设置 id 如: switch1",
        2008 to "设计页面 → 拖入 SeekBar\n设置 id 如: seekbar1",
        2009 to "设计页面 → 拖入 WebView\n设置 id 如: webview1",
        2010 to "设计页面 → 拖入 ProgressBar\n设置 id 如: progressbar1",
        2021 to "设计页面 → 拖入 ImageButton\n设置 id 如: imagebutton1",
        2051 to "设计页面 → 拖入 FloatingActionButton\n设置 id 如: fab1",
        2016 to "设计页面 → 拖入 RadioButton\n设置 id 如: radiobutton1",
        2017 to "设计页面 → 拖入 RatingBar\n设置 id 如: ratingbar1",
        2023 to "设计页面 → 拖入 RecyclerView\n设置 id 如: recyclerview1",
        2025 to "设计页面 → 拖入 CardView\n设置 id 如: cardview1",
        2054 to "设计页面 → 拖入 TextInputLayout + EditText\n设置 id 如: textinput1",
        2020 to "设计页面 → 拖入 GridView\n设置 id 如: gridview1",
        2024 to "设计页面 → 拖入 ViewPager\n设置 id 如: viewpager1",
        2040 to "设计页面 → 拖入 LinearLayout\n设置 id 如: linear1",
        2043 to "设计页面 → 拖入 ScrollView\n设置 id 如: scrollview1"
    )

    fun getDeclaration(itemId: Long): String? = declarationMap[itemId.toInt()]

    data class WidgetProp(val name: String, val type: String, val desc: String)

    val widgetPropsMap: Map<Int, List<WidgetProp>> = mapOf(
        2000 to listOf( // Button
            WidgetProp("text", "String", "按钮文本"),
            WidgetProp("textColor", "int", "文字颜色"),
            WidgetProp("textSize", "float (sp)", "文字大小"),
            WidgetProp("enabled", "boolean", "是否可点击"),
            WidgetProp("backgroundTint", "ColorStateList", "背景色"),
            WidgetProp("gravity", "int", "文字对齐方式"),
            WidgetProp("allCaps", "boolean", "是否全大写")
        ),
        2001 to listOf( // TextView
            WidgetProp("text", "String", "显示文本"),
            WidgetProp("textColor", "int", "文字颜色"),
            WidgetProp("textSize", "float (sp)", "文字大小"),
            WidgetProp("textStyle", "int", "bold/italic/normal"),
            WidgetProp("maxLines", "int", "最大行数"),
            WidgetProp("ellipsize", "enum", "文本溢出: end/start/middle"),
            WidgetProp("gravity", "int", "文字对齐"),
            WidgetProp("lineSpacingMultiplier", "float", "行间距倍数")
        ),
        2002 to listOf( // EditText
            WidgetProp("text", "String", "输入内容"),
            WidgetProp("hint", "String", "提示文本"),
            WidgetProp("inputType", "int", "输入类型: text/number/email/password"),
            WidgetProp("maxLength", "int", "最大字符数"),
            WidgetProp("singleLine", "boolean", "是否单行"),
            WidgetProp("imeOptions", "int", "键盘动作: actionDone/actionSearch"),
            WidgetProp("textColor", "int", "文字颜色"),
            WidgetProp("hintTextColor", "int", "提示文字颜色")
        ),
        2003 to listOf( // ImageView
            WidgetProp("src", "Drawable", "图片资源"),
            WidgetProp("scaleType", "enum", "缩放: centerCrop/fitCenter/centerInside"),
            WidgetProp("adjustViewBounds", "boolean", "按比例调整边界"),
            WidgetProp("tint", "int", "着色"),
            WidgetProp("alpha", "float", "透明度 0~1")
        ),
        2004 to listOf( // ListView
            WidgetProp("divider", "Drawable", "分隔线"),
            WidgetProp("dividerHeight", "dp", "分隔线高度"),
            WidgetProp("choiceMode", "int", "选择模式: none/single/multiple"),
            WidgetProp("scrollbars", "enum", "滚动条: vertical/none"),
            WidgetProp("fastScrollEnabled", "boolean", "快速滚动")
        ),
        2005 to listOf( // Spinner
            WidgetProp("entries", "String[]", "选项列表"),
            WidgetProp("prompt", "String", "弹窗标题"),
            WidgetProp("spinnerMode", "enum", "模式: dialog/dropdown"),
            WidgetProp("selectedItemPosition", "int", "选中索引")
        ),
        2006 to listOf( // CheckBox
            WidgetProp("text", "String", "显示文本"),
            WidgetProp("checked", "boolean", "是否选中"),
            WidgetProp("buttonTint", "ColorStateList", "勾选框颜色"),
            WidgetProp("textColor", "int", "文字颜色")
        ),
        2007 to listOf( // Switch
            WidgetProp("text", "String", "标签文本"),
            WidgetProp("checked", "boolean", "是否开启"),
            WidgetProp("thumbTint", "ColorStateList", "滑块颜色"),
            WidgetProp("trackTint", "ColorStateList", "轨道颜色"),
            WidgetProp("textOn", "String", "开启文本"),
            WidgetProp("textOff", "String", "关闭文本")
        ),
        2008 to listOf( // SeekBar
            WidgetProp("max", "int", "最大值"),
            WidgetProp("progress", "int", "当前进度"),
            WidgetProp("min", "int", "最小值 (API 26+)"),
            WidgetProp("thumbTint", "ColorStateList", "滑块颜色"),
            WidgetProp("progressTint", "ColorStateList", "进度条颜色")
        ),
        2009 to listOf( // WebView
            WidgetProp("url", "String", "加载网址 (loadUrl)"),
            WidgetProp("javaScriptEnabled", "boolean", "启用JS"),
            WidgetProp("domStorageEnabled", "boolean", "启用DOM存储"),
            WidgetProp("useWideViewPort", "boolean", "宽视图"),
            WidgetProp("loadWithOverviewMode", "boolean", "缩放适配")
        ),
        2010 to listOf( // ProgressBar
            WidgetProp("max", "int", "最大值"),
            WidgetProp("progress", "int", "当前进度"),
            WidgetProp("indeterminate", "boolean", "不确定模式(旋转)"),
            WidgetProp("progressTint", "ColorStateList", "进度颜色"),
            WidgetProp("style", "attr", "样式: horizontal/circular")
        ),
        2016 to listOf( // RadioButton
            WidgetProp("text", "String", "选项文本"),
            WidgetProp("checked", "boolean", "是否选中"),
            WidgetProp("buttonTint", "ColorStateList", "按钮颜色"),
            WidgetProp("textColor", "int", "文字颜色")
        ),
        2017 to listOf( // RatingBar
            WidgetProp("numStars", "int", "星星数量"),
            WidgetProp("rating", "float", "当前评分"),
            WidgetProp("stepSize", "float", "步长"),
            WidgetProp("isIndicator", "boolean", "只读模式"),
            WidgetProp("progressTint", "ColorStateList", "星星颜色")
        ),
        2021 to listOf( // ImageButton
            WidgetProp("src", "Drawable", "图片资源"),
            WidgetProp("scaleType", "enum", "缩放模式"),
            WidgetProp("backgroundTint", "ColorStateList", "背景色"),
            WidgetProp("tint", "int", "图片着色")
        ),
        2023 to listOf( // RecyclerView
            WidgetProp("layoutManager", "LayoutManager", "布局: Linear/Grid/StaggeredGrid"),
            WidgetProp("orientation", "int", "方向: vertical/horizontal"),
            WidgetProp("spanCount", "int", "Grid列数"),
            WidgetProp("clipToPadding", "boolean", "内边距裁剪"),
            WidgetProp("nestedScrollingEnabled", "boolean", "嵌套滚动")
        ),
        2025 to listOf( // CardView
            WidgetProp("cardCornerRadius", "dp", "圆角半径"),
            WidgetProp("cardElevation", "dp", "阴影高度"),
            WidgetProp("cardBackgroundColor", "int", "卡片背景色"),
            WidgetProp("strokeWidth", "dp", "边框宽度"),
            WidgetProp("strokeColor", "int", "边框颜色")
        ),
        2040 to listOf( // LinearLayout
            WidgetProp("orientation", "enum", "方向: vertical/horizontal"),
            WidgetProp("gravity", "int", "子视图对齐"),
            WidgetProp("weightSum", "float", "权重总和"),
            WidgetProp("divider", "Drawable", "分隔线"),
            WidgetProp("showDividers", "int", "分隔线位置")
        ),
        2051 to listOf( // FloatingActionButton
            WidgetProp("src", "Drawable", "图标"),
            WidgetProp("fabSize", "enum", "大小: mini/normal/auto"),
            WidgetProp("backgroundTint", "ColorStateList", "背景色"),
            WidgetProp("tint", "int", "图标着色"),
            WidgetProp("elevation", "dp", "阴影高度")
        ),
        2054 to listOf( // TextInputLayout
            WidgetProp("hint", "String", "浮动提示文本"),
            WidgetProp("helperText", "String", "辅助文本"),
            WidgetProp("errorText", "String", "错误提示"),
            WidgetProp("counterEnabled", "boolean", "字数计数"),
            WidgetProp("counterMaxLength", "int", "最大字数"),
            WidgetProp("boxStrokeColor", "int", "边框颜色"),
            WidgetProp("endIconMode", "enum", "尾部图标: clear/password/custom")
        )
    )

    fun getWidgetProps(itemId: Long): List<WidgetProp>? = widgetPropsMap[itemId.toInt()]

    fun search(query: String): List<ReferenceItem> {
        val q = query.lowercase()
        return getAllItems().filter {
            it.name.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.category.lowercase().contains(q) ||
            it.spec.orEmpty().lowercase().contains(q) ||
            it.code.orEmpty().lowercase().contains(q)
        }
    }

    fun getSuggestions(query: String, limit: Int = 8): List<ReferenceItem> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()
        val all = getAllItems()
        val nameStarts = all.filter { it.name.lowercase().startsWith(q) }
        val nameContains = all.filter {
            !it.name.lowercase().startsWith(q) && it.name.lowercase().contains(q)
        }
        val specContains = all.filter {
            !it.name.lowercase().contains(q) && it.spec.orEmpty().lowercase().contains(q)
        }
        return (nameStarts + nameContains + specContains).take(limit)
    }
}
