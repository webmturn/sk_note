package com.sknote.app.ui.analyzer

import org.json.JSONObject
import java.io.File

/**
 * Sketchware 项目文件解析器。
 *
 * Sketchware 在 `.sketchware/data/<projectId>/` 下用纯文本（按段）存储以下信息：
 * - `logic` 文件，按 `@<JavaName>.java_<suffix>` 分段：
 *     - `_var`     变量列表，每行 `type:name`（type: 0=boolean,1=int,2=string,3=map）
 *     - `_list`    集合列表，每行 `type:name`（type: 0=int,1=string,2=map）
 *     - `_func`    自定义块（MoreBlock），每行 `name:spec`
 *     - `_components` 非可视组件，每行一个 JSON（ComponentBean）
 *     - `_events`     事件定义，每行一个 JSON（EventBean）
 *     - `_<eventKey>` 积木块链，每行一个 JSON（BlockBean），eventKey 形如
 *                     `targetId_eventName` 或 `funcName_moreBlock`
 * - `view` 文件，按 `@<XmlName>.xml[_fab]` 分段，每行一个 JSON（ViewBean）。
 *
 * 资源文件存放在 `.sketchware/resources/{images,fonts,sounds}/<projectId>/`。
 */
object SkProjectParser {

    // ---- Logic block ----

    data class LogicBlock(
        val blockId: String,
        val opCode: String,
        val spec: String,
        val color: Int,
        val type: String,
        val nextBlock: Int,
        val subStack1: Int,
        val subStack2: Int,
        val parameters: List<String> = emptyList(),
        val disabled: Boolean = false,
        val rawJson: String = ""
    )

    // ---- Variables / lists / more blocks / components / events ----

    /** Variable type → Chinese label / Java type */
    val variableTypeLabels = mapOf(
        0 to "Boolean",
        1 to "Number",
        2 to "String",
        3 to "Map"
    )

    /** List type → Chinese label */
    val listTypeLabels = mapOf(
        0 to "List<Number>",
        1 to "List<String>",
        2 to "List<Map>"
    )

    data class Variable(val type: Int, val name: String) {
        val typeLabel: String get() = variableTypeLabels[type] ?: "Var($type)"
    }

    data class ListVar(val type: Int, val name: String) {
        val typeLabel: String get() = listTypeLabels[type] ?: "List($type)"
    }

    data class MoreBlock(val name: String, val spec: String)

    data class Component(
        val type: Int,
        val componentId: String,
        val param1: String = "",
        val param2: String = "",
        val param3: String = "",
        val rawJson: String = ""
    ) {
        val typeName: String get() = componentTypeLabels[type] ?: "Component($type)"
    }

    data class EventDef(
        val eventName: String,
        val eventType: Int,
        val targetId: String,
        val targetType: Int
    ) {
        /** key in block chain map */
        val eventKey: String get() = "${targetId}_$eventName"
    }

    /** Component type code → name (mirrors ComponentBean.COMPONENT_TYPE_*). */
    val componentTypeLabels = mapOf(
        1 to "Intent", 2 to "SharedPreferences", 3 to "Calendar",
        4 to "Vibrator", 5 to "Timer", 6 to "FirebaseDB",
        7 to "Dialog", 8 to "MediaPlayer", 9 to "SoundPool",
        10 to "ObjectAnimator", 11 to "Gyroscope", 12 to "FirebaseAuth",
        13 to "InterstitialAd", 14 to "FirebaseStorage", 15 to "Camera",
        16 to "FilePicker", 17 to "RequestNetwork", 18 to "TextToSpeech",
        19 to "SpeechToText", 20 to "BluetoothConnect", 21 to "LocationManager",
        22 to "RewardedVideoAd", 23 to "ProgressDialog", 24 to "DatePickerDialog",
        25 to "TimePickerDialog", 26 to "Notification", 27 to "FragmentAdapter",
        28 to "FirebasePhoneAuth", 29 to "SQLiteDatabase",
        30 to "FirebaseCloudMessage", 31 to "FirebaseGoogleLogin",
        35 to "CameraLegacy", 36 to "AsyncTask"
    )

    val eventTypeLabels = mapOf(
        1 to "View",
        2 to "Component",
        3 to "Activity",
        4 to "Drawer",
        5 to "Etc"
    )

    /** Per-Activity logic data, keyed by Java file name (e.g. "MainActivity.java"). */
    data class ActivityLogic(
        val javaName: String,
        val activityName: String,
        val variables: List<Variable>,
        val lists: List<ListVar>,
        val moreBlocks: List<MoreBlock>,
        val components: List<Component>,
        val events: List<EventDef>,
        /** key = event/moreBlock entry key, value = blocks indexed by block id */
        val blockChains: Map<String, BlockChain>
    )

    /** A single block chain (event handler / moreBlock body). */
    data class BlockChain(
        val sectionKey: String,            // e.g. @MainActivity.java_button1_onClick
        val javaName: String,              // MainActivity.java
        val activityName: String,          // MainActivity
        val entryKey: String,              // button1_onClick
        val isMoreBlock: Boolean,
        val blocks: Map<Int, LogicBlock>,
        val rootId: Int,
        val rawLines: List<String>
    )

    // ---- View nodes ----

    data class ViewNode(
        val id: String,
        val type: Int,
        val typeName: String,
        val parentId: String,
        val text: String,
        val hint: String,
        val customView: String,
        val backgroundResource: String,
        val imageResource: String,
        val raw: JSONObject?,
        val rawJson: String,
        val children: MutableList<ViewNode> = mutableListOf()
    )

    /** Per-XML view tree, keyed by xml name (e.g. "main.xml"). */
    data class LayoutTree(
        val xmlName: String,
        val activityName: String,
        val roots: List<ViewNode>,
        val fab: ViewNode?
    )

    // ---- Resource scan ----

    data class ResourceFiles(
        val images: List<File>,
        val fonts: List<File>,
        val sounds: List<File>
    ) {
        val total: Int get() = images.size + fonts.size + sounds.size
    }

    // ---- Parsing ----

    /**
     * Parses the project's logic file and returns one [ActivityLogic] per Java file.
     * Sections that cannot be parsed are tolerated and skipped.
     */
    fun parseLogic(logicFile: File): Map<String, ActivityLogic> {
        if (!logicFile.exists() || !logicFile.isFile) return emptyMap()

        val varMap = LinkedHashMap<String, MutableList<Variable>>()
        val listMap = LinkedHashMap<String, MutableList<ListVar>>()
        val funcMap = LinkedHashMap<String, MutableList<MoreBlock>>()
        val compMap = LinkedHashMap<String, MutableList<Component>>()
        val evtMap = LinkedHashMap<String, MutableList<EventDef>>()
        // javaName → (entryKey → BlockChain)
        val blockMap = LinkedHashMap<String, LinkedHashMap<String, BlockChain>>()

        val lines = try {
            logicFile.readLines()
        } catch (_: Exception) {
            return emptyMap()
        }

        var sectionKey = ""
        var sectionLines = mutableListOf<String>()

        fun finalize() {
            if (sectionKey.isEmpty() || sectionLines.isEmpty()) return
            try {
                processSection(sectionKey, sectionLines, varMap, listMap, funcMap, compMap, evtMap, blockMap)
            } catch (_: Exception) {
                // ignore section-level parse errors
            }
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("@")) {
                finalize()
                sectionKey = line
                sectionLines = mutableListOf()
                continue
            }
            if (line.isEmpty()) continue
            sectionLines.add(line)
        }
        finalize()

        // Merge into Activity models. Use union of keys across all maps.
        val javaNames = LinkedHashSet<String>().apply {
            addAll(varMap.keys); addAll(listMap.keys); addAll(funcMap.keys)
            addAll(compMap.keys); addAll(evtMap.keys); addAll(blockMap.keys)
        }
        val out = LinkedHashMap<String, ActivityLogic>()
        for (javaName in javaNames) {
            val activity = javaName.substringBefore(".java")
            out[javaName] = ActivityLogic(
                javaName = javaName,
                activityName = activity,
                variables = varMap[javaName].orEmpty(),
                lists = listMap[javaName].orEmpty(),
                moreBlocks = funcMap[javaName].orEmpty(),
                components = compMap[javaName].orEmpty(),
                events = evtMap[javaName].orEmpty(),
                blockChains = blockMap[javaName].orEmpty()
            )
        }
        return out
    }

    private fun processSection(
        sectionKey: String,
        body: List<String>,
        varMap: MutableMap<String, MutableList<Variable>>,
        listMap: MutableMap<String, MutableList<ListVar>>,
        funcMap: MutableMap<String, MutableList<MoreBlock>>,
        compMap: MutableMap<String, MutableList<Component>>,
        evtMap: MutableMap<String, MutableList<EventDef>>,
        blockMap: MutableMap<String, LinkedHashMap<String, BlockChain>>
    ) {
        // sectionKey looks like "@MainActivity.java_var" / "@MainActivity.java_button1_onClick"
        val clean = sectionKey.removePrefix("@")
        val javaIdx = clean.indexOf(".java")
        if (javaIdx < 0) return
        val javaName = clean.substring(0, javaIdx + ".java".length)
        val activityName = javaName.substringBefore(".java")
        val suffix = clean.substring(javaIdx + ".java".length).trimStart('_')

        when {
            suffix == "var" -> {
                val out = varMap.getOrPut(javaName) { mutableListOf() }
                for (l in body) {
                    val sep = l.indexOf(':')
                    if (sep <= 0) continue
                    val type = l.substring(0, sep).toIntOrNull() ?: continue
                    val name = l.substring(sep + 1)
                    if (name.isNotEmpty()) out.add(Variable(type, name))
                }
            }
            suffix == "list" -> {
                val out = listMap.getOrPut(javaName) { mutableListOf() }
                for (l in body) {
                    val sep = l.indexOf(':')
                    if (sep <= 0) continue
                    val type = l.substring(0, sep).toIntOrNull() ?: continue
                    val name = l.substring(sep + 1)
                    if (name.isNotEmpty()) out.add(ListVar(type, name))
                }
            }
            suffix == "func" -> {
                val out = funcMap.getOrPut(javaName) { mutableListOf() }
                for (l in body) {
                    val sep = l.indexOf(':')
                    if (sep <= 0) continue
                    val name = l.substring(0, sep)
                    val spec = l.substring(sep + 1)
                    if (name.isNotEmpty()) out.add(MoreBlock(name, spec))
                }
            }
            suffix == "components" -> {
                val out = compMap.getOrPut(javaName) { mutableListOf() }
                for (l in body) {
                    val obj = tryJson(l) ?: continue
                    out.add(
                        Component(
                            type = obj.optInt("type", -1),
                            componentId = obj.optString("componentId", ""),
                            param1 = obj.optString("param1", ""),
                            param2 = obj.optString("param2", ""),
                            param3 = obj.optString("param3", ""),
                            rawJson = l
                        )
                    )
                }
            }
            suffix == "events" -> {
                val out = evtMap.getOrPut(javaName) { mutableListOf() }
                for (l in body) {
                    val obj = tryJson(l) ?: continue
                    out.add(
                        EventDef(
                            eventName = obj.optString("eventName", ""),
                            eventType = obj.optInt("eventType", 0),
                            targetId = obj.optString("targetId", ""),
                            targetType = obj.optInt("targetType", -1)
                        )
                    )
                }
            }
            suffix.isNotEmpty() -> {
                // Block chain section
                val chain = parseBlockChain(sectionKey, javaName, activityName, suffix, body) ?: return
                val perFile = blockMap.getOrPut(javaName) { LinkedHashMap() }
                perFile[suffix] = chain
            }
        }
    }

    private fun parseBlockChain(
        sectionKey: String,
        javaName: String,
        activityName: String,
        entryKey: String,
        body: List<String>
    ): BlockChain? {
        val blocks = LinkedHashMap<Int, LogicBlock>()
        val rawLines = mutableListOf<String>()
        var minId = Int.MAX_VALUE
        for (l in body) {
            val obj = tryJson(l) ?: continue
            val idStr = obj.optString("id", "")
            val id = idStr.toIntOrNull() ?: continue
            val opCode = obj.optString("opCode", "")
            if (opCode.isEmpty()) continue
            val params = mutableListOf<String>()
            val arr = obj.optJSONArray("parameters")
            if (arr != null) {
                for (i in 0 until arr.length()) params.add(arr.optString(i, ""))
            }
            blocks[id] = LogicBlock(
                blockId = idStr,
                opCode = opCode,
                spec = obj.optString("spec", opCode),
                color = obj.optInt("color", -0x1E8D5E2),
                type = obj.optString("type", " "),
                nextBlock = obj.optInt("nextBlock", -1),
                subStack1 = obj.optInt("subStack1", -1),
                subStack2 = obj.optInt("subStack2", -1),
                parameters = params,
                disabled = obj.optBoolean("disabled", false),
                rawJson = l
            )
            rawLines.add(l)
            if (id < minId) minId = id
        }
        if (blocks.isEmpty()) return null
        // Find root: an id never referenced by another block as next/subStack1/subStack2.
        val referenced = HashSet<Int>()
        for (b in blocks.values) {
            if (b.nextBlock >= 0) referenced.add(b.nextBlock)
            if (b.subStack1 >= 0) referenced.add(b.subStack1)
            if (b.subStack2 >= 0) referenced.add(b.subStack2)
        }
        val rootId = blocks.keys.firstOrNull { it !in referenced } ?: minId
        val isMoreBlock = entryKey.endsWith("_moreBlock")
        return BlockChain(
            sectionKey = sectionKey,
            javaName = javaName,
            activityName = activityName,
            entryKey = entryKey,
            isMoreBlock = isMoreBlock,
            blocks = blocks,
            rootId = rootId,
            rawLines = rawLines
        )
    }

    private fun tryJson(line: String): JSONObject? = try {
        if (line.isNotEmpty() && line[0] == '{') JSONObject(line) else null
    } catch (_: Exception) {
        null
    }

    // ---- View parsing ----

    fun parseView(viewFile: File): Map<String, LayoutTree> {
        if (!viewFile.exists() || !viewFile.isFile) return emptyMap()
        val sectionToNodes = LinkedHashMap<String, MutableList<ViewNode>>()
        val fabMap = LinkedHashMap<String, ViewNode>()

        val lines = try {
            viewFile.readLines()
        } catch (_: Exception) {
            return emptyMap()
        }

        var current = ""
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("@")) {
                current = line.removePrefix("@")
                continue
            }
            if (line.isEmpty() || current.isEmpty()) continue
            val obj = tryJson(line) ?: continue
            val node = ViewNode(
                id = obj.optString("id", ""),
                type = obj.optInt("type", -1),
                typeName = mapViewTypeName(obj.optInt("type", -1)),
                parentId = obj.optString("parent", ""),
                text = obj.optJSONObject("text")?.optString("text", "") ?: "",
                hint = obj.optJSONObject("text")?.optString("hint", "") ?: "",
                customView = obj.optString("customView", ""),
                backgroundResource = obj.optJSONObject("layout")?.optString("backgroundResource", "") ?: "",
                imageResource = obj.optJSONObject("image")?.optString("resName", "") ?: "",
                raw = obj,
                rawJson = line
            )
            if (current.endsWith("_fab")) {
                fabMap[current.removeSuffix("_fab")] = node
            } else {
                sectionToNodes.getOrPut(current) { mutableListOf() }.add(node)
            }
        }

        val out = LinkedHashMap<String, LayoutTree>()
        for ((section, nodes) in sectionToNodes) {
            val byId = nodes.associateBy { it.id }
            val roots = mutableListOf<ViewNode>()
            // Build parent-child relations
            for (n in nodes) {
                val parent = n.parentId
                if (parent.isEmpty() || parent == "root" || byId[parent] == null) {
                    roots.add(n)
                } else {
                    byId[parent]?.children?.add(n)
                }
            }
            val xmlName = if (section.endsWith(".xml")) section else "$section.xml"
            // Map xml → activity by Sketchware naming convention: foo.xml ↔ FooActivity.java
            val activity = xmlNameToActivity(section)
            val fab = fabMap[section]
            out[xmlName] = LayoutTree(xmlName, activity, roots, fab)
        }
        return out
    }

    /** Sketchware xml file name (e.g. "main") → likely activity name (e.g. "MainActivity"). */
    fun xmlNameToActivity(xml: String): String {
        val core = xml.removeSuffix(".xml")
        if (core.isEmpty()) return ""
        // Sketchware convention: words split by '_'; activity = capitalized words + "Activity"
        val pascal = core.split('_').filter { it.isNotEmpty() }.joinToString("") {
            it.replaceFirstChar { ch -> ch.uppercaseChar() }
        }
        return pascal + "Activity"
    }

    /** ViewBean.type → coarse view type name. */
    fun mapViewTypeName(type: Int): String = when (type) {
        0 -> "LinearLayout"
        1 -> "RelativeLayout"
        2 -> "HorizontalScrollView"
        3 -> "Button"
        4 -> "TextView"
        5 -> "EditText"
        6 -> "ImageView"
        7 -> "WebView"
        8 -> "ProgressBar"
        9 -> "ListView"
        10 -> "Spinner"
        11 -> "CheckBox"
        12 -> "ScrollView"
        13 -> "Switch"
        14 -> "SeekBar"
        15 -> "CalendarView"
        16 -> "FloatingActionButton"
        17 -> "AdView"
        18 -> "MapView"
        19 -> "RadioButton"
        20 -> "RatingBar"
        21 -> "VideoView"
        22 -> "SearchView"
        23 -> "ImageButton"
        24 -> "GridView"
        25 -> "RecyclerView"
        26 -> "ViewPager"
        27 -> "CardView"
        28 -> "TabLayout"
        29 -> "BottomNavigation"
        30 -> "CollapsingToolbar"
        31 -> "SwipeRefresh"
        32 -> "DatePicker"
        33 -> "TimePicker"
        34 -> "CircleImageView"
        35 -> "TextInputLayout"
        else -> "View($type)"
    }

    fun isContainerView(type: Int): Boolean = when (type) {
        0, 1, 2, 12, 24, 25, 26, 27, 28, 30, 31 -> true
        else -> false
    }

    // ---- Resources ----

    /**
     * Scans `<storage>/.sketchware/resources/{images,fonts,sounds}/<projectId>/`.
     * Missing directories simply produce empty lists.
     */
    fun scanResources(externalStorageRoot: File, projectId: String): ResourceFiles {
        fun list(sub: String): List<File> {
            val dir = File(externalStorageRoot, ".sketchware/resources/$sub/$projectId")
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            return dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
        }
        return ResourceFiles(
            images = list("images"),
            fonts = list("fonts"),
            sounds = list("sounds")
        )
    }
}
