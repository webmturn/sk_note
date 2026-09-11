# sk_note Android UI 审查报告

审查对象：`android/app/src/main`（106 个 layout / 496 个 drawable / 12 个 menu / 113 个 `ui/**` Kotlin 文件 / 22 个 Adapter）
方法：Android Lint（`lintDebug`）+ 对照 `.windsurf/rules/rule.md` 的静态审查 + 子代理分区审查
P0 级结论均已由人工逐个复核到行。

### 复核修正记录（第二轮逐条核实后推翻的初版结论）

| 初版结论 | 复核结果 |
|---|---|
| 12 处 `android:tint` 会导致"图标 tint 被静默忽略、显示原色" | **不成立**。这些是平台 `ImageView`/`ImageButton`，`android:tint` 自 API 21 起有效，minSdk 24 实际生效。属 lint 合规问题，非功能缺陷 |
| `AppUpdateManager.kt:190` 缺 `RECEIVER_NOT_EXPORTED`，"Android 14+ 会崩" | **不成立，lint 误报**。`:183-194` 已有 API 33 分支且 `:187` 传了该 flag；命中的 `:190` 是 `else` 分支（API < 33），该参数不存在 |
| 自我保护失效 → "可把自己降级/删除" | **实为 UX 问题**。后端已有护栏（`auth.ts:395` 禁改自己角色、`:424` 禁删自己）；前端只是让菜单项多显示了 |
| 图标选择器黑白图标不可见 | **部分成立**。`CategoryIconPickerAdapter.kt:35` 已用 `imageTintList` 按主题上色；风险仅在未走 tint 路径的预览处 |
| `CategoryManageFragment` 缺 96dp 底部避让 | **不成立**。`fragment_category_manage.xml:43` 已有 `paddingBottom="96dp"`；且它是二级页，底栏本就隐藏 |

---

---

## 一、总体结论

**颜色与主题纪律是本项目最强的部分，几乎无可挑剔；主要短板集中在"样式常量各写各的"与三处工程习惯问题。**

Lint 总量：**22 error / 2545 warning**（warning 中 1220 条是 `UnusedResources`、537 条 `HardcodedText`，二者在本项目属预期噪声，见第五节）。

---

## 二、做得好（已核实，不要动）

| 项 | 核实结果 |
|---|---|
| 布局硬编码颜色 | **0 处**。全部走 `?attr/colorXxx` / `@color/icon_bg_*` / `@color/text_danger` |
| 字号单位 | **0 处** 误写 `dp`，全部 `sp` |
| `colors.xml` 日夜对齐 | day 29 个 = night 29 个，**零缺失、零多余** |
| 主题日夜差异 | 仅 `windowLightStatusBar` / `windowLightNavigationBar` 两处**应有的**差异，无遗漏属性 |
| XML 编码 | 全量扫描**无 U+FFFD 乱码、无 BOM**（控制台里看到的方块字是 PowerShell 显示问题，文件本身正常） |
| 底栏遮挡（上次 commit 的主题） | 已正确解决：`fragment_home.xml:8-12`、`fragment_admin.xml:2-7` 均用 `paddingBottom="88dp"` + `clipToPadding="false"` |
| 字体/UI 主题属性可解析 | `d847774` 用到的 `colorSurfaceContainer*` / `colorOutlineVariant` / `colorTertiaryContainer` 在 material 1.11.0 中确实存在，`Theme.Material3.DayNight` 有 `values-night-v8` 定义 → 无编译中断风险 |
| 语法高亮随主题 | `JavaSyntaxHighlighter.kt:36-40` 按 `UI_MODE_NIGHT_MASK` 分支且 `attachTo` 每次文本变化重算；`SyntaxHighlightUtil` 由 `SnippetDetailFragment.kt:161` 传值 → 运行期切换安全 |
| 卡片高度基线 | 所有 `MaterialCardView` 均 `cardElevation="0dp"`；6 个列表项（article/discussion/share/snippet/release/user）圆角严格 16dp |
| 列表架构 | 22 个 Adapter 基本都是 `ListAdapter` + `DiffUtil`；`onBindViewHolder` 每次重设 visibility 与监听器，无复用错乱 |
| 状态布局 | `layout_empty_state` / `layout_error_state` 本身规范（主题属性 + 中文文案） |
| 应用内主题切换 | `SkNoteApp.applyThemeMode` → `AppCompatDelegate.setDefaultNightMode`，全链路正确 |

### 顺带修正我自己的一个怀疑
审查中我怀疑「底栏叠在 FragmentContainerView 上会遮挡内容」，**经核实不成立**——两个顶层页都已用 `paddingBottom="88dp"` 让位。已排除，不是问题。

---

## 三、P0 功能缺陷（3 条，均已复核到行）

### P0-1　编辑讨论分类必然变成"新建"（会写出重复数据）
`app/src/main/kotlin/com/sknote/app/ui/manage/discussioncategory/DiscussionCategoryEditorFragment.kt:32-33`

```kotlin
private val categoryId: Long by lazy { arguments?.getLong(ARG_CATEGORY_ID) ?: 0L }
private var slugEditedManually = categoryId != 0L   // ← 构造期就读了，此时 arguments 仍为 null
```

属性初始化器在 **Fragment 构造期**执行，而 Navigation 是「先构造、后 `fragment.arguments = args`」。因此 `categoryId` 被 lazy **永久缓存为 `0L`**。

可达路径已确认：`CategoryManageFragment.kt:200-206` 的 `openDiscussionCategoryEditor(categoryId)` 会带 `category_id` 参数导航到 `R.id.discussionCategoryEditorFragment`（`nav_graph.xml:333-341` 声明了该参数）。

连锁后果：
- `:53` 标题恒为"添加讨论分类"；`:54` 按钮恒为"创建讨论分类"
- `:89` 恒走新建分支 → `:95 loadCategory()` **永不执行**，编辑时表单为空
- `:179` 恒调 `createDiscussionCategory` → **点"编辑"实际创建一条重复分类**，`:188` 提示恒为"创建成功"

修复（对照同目录 `CategoryEditorFragment.kt:29` 的正确写法）：
```kotlin
private var slugEditedManually = false          // 不在此处读 categoryId
// onViewCreated 中，紧跟 savedInstanceState?.let { ... } 之后：
if (savedInstanceState == null) slugEditedManually = categoryId != 0L
```

### P0-2　`setVisibility` 动画竞态导致管理中心入口永久消失
`app/src/main/kotlin/com/sknote/app/ui/admin/AdminFragment.kt:230-247`

`if (view.visibility == target) return` 只看 visibility，不管 alpha。淡出动画在飞的 150ms 内 `visibility` 仍是 `VISIBLE`，此时若来一次 `show=true`：
1. 提前 return，**没有取消在飞动画**
2. 挂起的 `withEndAction` 照常执行 → 把 view 置为 `GONE` + `alpha=0`

触发点 `:181`（隐藏）与 `:197`（显示）在本地 role 与 `getMe()` 返回 role 不一致时会先后触发，"管理中心"卡片会彻底消失且点了没反应。

修复：
```kotlin
private fun setVisibility(view: View, show: Boolean, animate: Boolean) {
    view.animate().cancel()                                   // 先取消在飞动画
    val target = if (show) View.VISIBLE else View.GONE
    val targetAlpha = if (show) 1f else 0f
    if (view.visibility == target && view.alpha == targetAlpha) return
    if (!animate) { view.alpha = targetAlpha; view.visibility = target; return }
    if (show) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setDuration(200).start()
    } else {
        view.animate().alpha(0f).setDuration(150).withEndAction {
            if (view.alpha == 0f) view.visibility = View.GONE   // 期间被重新显示则不吞掉
        }.start()
    }
}
```

### P0-3　发布结果走回调回传，旋转后结果丢失且列表不刷新
`app/src/main/kotlin/com/sknote/app/ui/manage/release/ReleaseEditorFragment.kt:99-108`（配合 `ReleaseManageViewModel.kt:45-60`）

回调 lambda 捕获了 Fragment 的 `binding` 与 `findNavController()`。请求在途时旋转/切后台：`:100` 的 `if (_binding == null) return@createRelease` 直接返回 → 新界面**既无成功提示，也不写 `refresh_releases`**（`:103`），列表不刷新、用户以为失败而重复提交，**后端已建了 release**。

修复：改成与 `ArticleEditorViewModel` 一致的单向状态流，去掉回调。
```kotlin
// ReleaseManageViewModel
private val _submitResult = MutableLiveData<Pair<Boolean, String?>?>()
val submitResult: LiveData<Pair<Boolean, String?>?> = _submitResult
fun onSubmitHandled() { _submitResult.value = null }
// createRelease 内：_submitResult.value = true to null / false to "发布失败: ${response.code()}"

// ReleaseEditorFragment.observeData()
viewModel.submitResult.observe(viewLifecycleOwner) { result ->
    result ?: return@observe
    viewModel.onSubmitHandled()
    binding.btnSave.isEnabled = true
    if (result.first) {
        findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_releases", true)
        findNavController().navigateUp()
    } else {
        Snackbar.make(binding.root, result.second ?: "发布失败", Snackbar.LENGTH_SHORT).show()
    }
}
```

---

## 四、P1 规范违反（按收益排序）

| # | 问题 | 位置 | 修复 |
|---|---|---|---|
| P1-1 | 可点击容器缺 ripple/焦点 | `fragment_admin.xml:16`（`cardUserProfile`，`AdminFragment.kt:56` 绑定点击）；`item_category_manage.xml:7`（用 `foreground` 而非 `background`） | 加 `android:background="?attr/selectableItemBackground"` + `clickable` + `focusable`；`item_category_manage` 的 `foreground` 改为 `background` |
| P1-2 | 卡片圆角未用 16dp 基线 | `fragment_admin.xml:223,527`=20dp、`:261/305/357/401/445/489/564/620`=11dp、`:34`=32dp、`:176`=36dp；`fragment_admin_center.xml:40/281/343`=20dp、`:65/108/151/194/237/300/362`=12dp；`fragment_cache_manage.xml:31`=24dp、`:95/230`=20dp、`:117/171/255/298`=12dp；`item_category_manage.xml:8`=18dp；`item_user_manage.xml:20`=21dp | 菜单卡片统一 16dp；图标底抽 `Widget.SkNote.IconTile`（12dp）作为唯一例外并写进规范；圆形头像改 `app:shapeAppearanceOverlay="@style/ShapeAppearance.Material3.Corner.Full"`，不要写 21/32/36dp |
| P1-3 | ID 命名与类型不符 | `fragment_admin.xml:16,247,291,343,387,431,596` 用 `cardXxx` 但是 `LinearLayout`；`:606` 用 `btnLogout` 但是 `LinearLayout`；`row*` 前缀（`fragment_admin.xml:475,550`、`fragment_cache_manage.xml:241,284`）不在规范清单 | 改 `layoutUserProfile` / `layoutLogoutRow` 等，同步 `AdminFragment.kt:56,64,68,72,76,80,92`；`rowXxx` 要么登记进规范要么改名 |
| P1-4 | `notifyDataSetChanged()` 滥用 | `ArticleManageAdapter.kt:26`、`DiscussionManageAdapter.kt:27`、`ShareManageAdapter.kt:27`、`SnippetManageAdapter.kt:26` | 用户上下文变化用 `if (itemCount > 0) notifyItemRangeChanged(0, itemCount)`，保留动画与局部刷新，也与 `AsyncListDiffer` 更兼容 |
| P1-5 | 使用 `PopupMenu`（规范：Dialog 只用 `MaterialAlertDialogBuilder`，不要 popup） | `UserManageFragment.kt:10,125-145` | 改 `MaterialAlertDialogBuilder(...).setItems(...)`；`UserManageAdapter.kt:15,73-75` 的 `onMoreClick: (User, View) -> Unit` 简化为 `(User) -> Unit`。另有实害：锚点 `btnMore` 在列表复用时会被回收 |
| P1-6 | 重置密码对话框用裸 `EditText` + **px** 内边距 + 字段校验走 Snackbar | `UserManageFragment.kt:165-184`（`setPadding(48, 32, 48, 16)` 是 px） | 改 `TextInputLayout` + `TextInputEditText`，圆角 `12f * density`，padding 用 `dp()`，校验改 `inputLayout.error = "密码至少 6 位"`。另 `:19` 已 import `TextInputEditText` 但未使用 |
| P1-7 | 用 emoji 当图标（规范：drawable-first，禁止 emoji/文字当图标） | `ShareManageAdapter.kt:52`（`"↓ … ❤ …"`）、`SnippetManageAdapter.kt:44`（`"❤ …"`） | 布局加 16dp `ImageView`（`ic_download` / `ic_favorite`，`app:tint="?attr/colorOnSurfaceVariant"`），文本只留数字 |
| P1-8 | 尺寸单位写成 px（未乘 density） | `UserManageAdapter.kt:48,57,66`（`cornerRadius = 8f` = 8px）；`CategoryIconPickerAdapter.kt:38`（`strokeWidth = 2` = 2px） | 乘 `resources.displayMetrics.density`；徽章建议改用 drawable 背景 |
| P1-9 | 分享管理筛选状态与 Tab 不一致 | `ShareManageFragment.kt:72-83`：`_filter` 在 ViewModel（跨旋转保留），但 TabLayout 重建后默认选第 0 项且监听器注册太晚不回调 | 按 `viewModel.filter.value` 反查 index 并 `getTabAt(index)?.select()` |
| P1-10 | 自我保护依赖异步字段（**实为 UX 问题，后端已兜底**） | `UserManageFragment.kt:90-92,122`：`currentUsername` 异步填充，未就绪时 `isSelf()` 恒 false → 自己那行会显示"删除用户"、也允许给自己降级；提交后才被后端拒绝 | 后端已有护栏：`auth.ts:395` 禁改自己角色、`auth.ts:424` 禁删自己。修复仅为 UX：`getUsername().first()` 挪到建 adapter **之前**，并推荐改用 id 判断（`getUserId()` 已存在） |

---

## 四之二、深色模式专项（本轮新增，含一条真实失效）

### 前提修正
「`colors.xml` 夜间漏配颜色」这个常见头号根因**在本项目不成立**：day 29 名 = night 29 名，零缺失、零多余。`values-night` 未重复声明 3 个 `Widget.SkNote.*` style **不是缺陷**——资源按名合并，夜间同样生效（它们引用主题属性，会自动翻转）。`colorSurface`/`colorOnSurfaceVariant`/`colorOutline` 两侧都未声明是**正确的**，由 `Theme.Material3.DayNight` 提供。

### P0-4　分析器预览：浅色背景 + 近白文字 → 深色模式下完全不可读
`app/src/main/kotlin/com/sknote/app/ui/analyzer/ProjectAnalyzerFragment.kt:1249-1250`（配合 `:979-989`）

两处启发式**只处理了一半**：

```kotlin
// :979-989  文字：近黑（r,g,b 均 <0x40）就换成 colorOnSurface（深色下≈白）
return if (alpha == 0 || (alpha == 0xFF && r < 0x40 && g < 0x40 && b < 0x40)) {
    resolveColor(com.google.android.material.R.attr.colorOnSurface)
} else rawColor

// :1249-1250  背景：只把"恰好不透明白"当无背景
val alpha = (node.bgColor ushr 24) and 0xFF
if (alpha > 0 && node.bgColor != 0xFFFFFFFF.toInt()) { setColor(node.bgColor) }
```

Sketchware 项目里极常见的 `backgroundColor=#FFF5F5F5` + `textColor=#FF333333`：
- 背景 `#F5F5F5` ≠ `#FFFFFFFF` → **原样保留浅灰**
- 文字 `#333333` 三个分量都 < 0x40 → **换成 `colorOnSurface`，深色下≈白色**

结果：**深色模式下浅灰底 + 近白字，完全不可读**。这是本轮审查确认的唯一真实可复现失效。

修复：对 `bgColor` 施加同类启发式（浅色近似值也回落到 `colorSurfaceContainer`）：
```kotlin
private fun adaptUserBgColor(rawColor: Int): Int {
    val alpha = (rawColor ushr 24) and 0xFF
    if (alpha == 0) return resolveColor(com.google.android.material.R.attr.colorSurfaceContainer)
    val r = (rawColor ushr 16) and 0xFF
    val g = (rawColor ushr 8) and 0xFF
    val b = rawColor and 0xFF
    // 近白/极浅色在深色主题下失去对比，改用主题表面色
    val isNearWhite = r > 0xE0 && g > 0xE0 && b > 0xE0
    return if (alpha == 0xFF && isNearWhite) {
        resolveColor(com.google.android.material.R.attr.colorSurfaceContainer)
    } else rawColor
}
```

同文件次要项：
- `:1153-1156` `containerColors` 6 个固定饱和色（`0x302196F3` / `0x304CAF50` / `0x30FF9800` / `0x309C27B0` / `0x30E91E63` / `0x3000BCD4`）用于 `:1206-1207` 容器底色，深色下浑浊且不随 M3 surface 分层 → 改用 `colorSurfaceContainerHigh` 等角色
- `:597` `0x0CFF9800` 警告底色（低风险）
- `:963-967` `resolveColor` 用 `tv.data`，当前主题属性全是纯 `<color>` 所以正确；若日后改成 color-state-list 会**静默取到资源 ID** → 建议改 `MaterialColors.getColor`

### 图标选择器：黑/白图标各在一端不可见
`drawable/ic_mtrl_chip_close_circle.xml:7` 用 `#ff000000` 纯黑，经 `OldResourceIdMapper.kt:1314-1319` → `ComponentEditorFragment.kt:74,97` 确认会在图标选择器**真实渲染** → **深色下不可见**。
反向问题：64 个可达 `ic_mtrl_*` 用 `#FFFFFF`、25 个用 `#e8eaed` → **浅色白底上不可见**（对比度≈1.1:1），深色下反而正常。这一条容易被误报成深色问题，实际方向相反。
修复：给图标选择器宿主加 `app:tint="?attr/colorOnSurfaceVariant"`，或把这些 `fillColor` 改成主题属性。

### 启动页与表面色差
- `splash_background`：`values/colors.xml:22` 与 `values-night/colors.xml:22` **都是 `#FF1976D2`**，夜间覆盖是空操作 → 深色下启动页仍是亮蓝（`:19`、`:27` 与 `activity_splash.xml:7` 都引用它）
- 浅色 `Theme.SkNote.Launch` 设 `windowLightStatusBar=true` 而 `windowBackground=#FF1976D2`（`values/themes.xml:19,22`）→ 深色状态栏图标压在中深蓝上，对比度≈2.6:1，偏低；夜间反而是对的 → 前者改 `false`
- `surface_card` 夜间 `#FF1E1E2E` 与 M3 深色 `colorSurface`（`m3_ref_palette_neutral6` = `#ff141218`）**不同源** → 状态栏/底部导航 vs 卡片/工具栏之间有可见色差接缝（`values-night/colors.xml:24`）
- `res/color/chip_background_selector.xml:3` 引用 `@color/design_default_color_secondary`，本项目两个 `colors.xml` 都未定义 → 解析到 Material 库的 `#03DAC6`，且该色**无夜间变体**。当前该 selector 全工程**零引用**（死资源），属潜伏风险 → 换 `?attr/colorSecondaryContainer` 或删除

### 关于上次那个 fix（`d847774`）
只改了 `ProjectAnalyzerFragment.kt` 一个文件（+53/−31），把约 20 处字面量换成 `resolveColor(?attr/color*)`，**方向正确、无编译风险**，但覆盖不完整——上面 P0-4 就是它留下的真实失效，图标选择器则完全未触及。

---

## 五、Lint 中值得处理的项

### 5.1　全部 22 个 error

| 类别 | 数量 | 位置与说明 |
|---|---|---|
| `UseAppTint` | 12 | `android:tint` 用在普通 `ImageView` / `ImageButton` 上，lint 要求改 `app:tint`。位置：`dialog_category.xml:69`、`dialog_discussion_category.xml:83`、`fragment_category_editor.xml:116,148`、`fragment_feedback.xml:392`、`item_category.xml:30`、`item_category_icon_picker.xml:25`、`item_category_manage.xml:35,76,85`、`item_discussion.xml:75,105`。**改 `android:tint` → `app:tint`**。※初版报告写成"部分版本 tint 被静默忽略"，经复核**不成立**：这些是平台 `ImageView`/`ImageButton`，`android:tint` 自 API 21 起有效，本项目 minSdk 24 实际生效，属 lint 合规问题 |
| `NewApi` | 4+2 | `lineHeight` 属性 setter 需 API 28，而 `minSdk=24` → 24~27 设备上**崩溃**：`BlockCompareDialog.kt:253`、`ReferenceDetailFragment.kt:663,692,706`（改用 `TextViewCompat.setLineHeight`）。`android:windowLightNavigationBar` 需 API 27：`values/themes.xml:15,23`、`values-night/themes.xml:15,23`（移 `values-v27/`） |
| `RecyclerView` | 1 | `LogicVisualizerFragment.kt:1698`：`onBindViewHolder` 里把 `position` 当固定值用，应改 `holder.bindingAdapterPosition` |
| `UnspecifiedRegisterReceiverFlag` | 1 | **误报，无需修复**：`util/AppUpdateManager.kt:183-194` 已有 API 33 分支并在 `:187` 传了 `RECEIVER_NOT_EXPORTED`；lint 命中的 `:190` 是 `else` 分支（API < 33），该 flag 参数那时**尚不存在**。※初版报告写成"Android 14+ 会崩"，经复核**不成立** |
| `UnusedResources` | 1220 | 见 5.3，含 2 个死布局 |

### 5.2　值得处理的 warning

| 类别 | 数量 | 说明 |
|---|---|---|
| `ContentDescription` | **165** | 图片缺无障碍描述。**注意**：布局里已有 48 处标注（列表项 `ImageButton` 做得不错），`fragment_about.xml` 一个文件就占 11 处。装饰性图片应显式 `android:importantForAccessibility="no"`，功能按钮必须补描述 |
| `SmallSp` | 22 | 字号 10sp < 11sp 下限。`fragment_reference.xml:82,104,126,148,170`、`item_article_simple.xml:35`、`item_block_share_preview.xml:51`、`item_discussion.xml:59`、`item_discussion_manage.xml:51,61`、`item_palette_block_row.xml:32`、`item_palette_share_preview.xml:51` 等（含更多）。改 11sp |
| `LabelFor` | 4 | 输入框既无 `labelFor` 也无 `hint`，无障碍读屏无法识别：`fragment_article_editor.xml:90`、`fragment_create_share.xml:114`、`fragment_create_snippet.xml:114,129` |
| `NotifyDataSetChanged` | 8 | 与 P1-4 同源 |
| `DisableBaselineAlignment` | 7 | 横向 `LinearLayout` 加 `android:baselineAligned="false"` 可省一次测量：`fragment_admin.xml:80`、`fragment_public_profile.xml:133`、`fragment_reference.xml:58`、`fragment_user_profile.xml:123`、`item_custom_event.xml:2`、`item_notification.xml:16`、`item_reference.xml:25` |
| `NestedWeights` | 2 | 嵌套 weight 影响测量性能：`fragment_logic_visualizer.xml:162`、`item_reference.xml:70` |
| `RtlHardcoded` | 7 | `layout_marginLeft/Right`、`alignParentRight` 改 Start/End：`item_custom_component.xml:21,23,31,41,42,54`、`item_custom_event.xml:28` |
| `ButtonStyle` | 2 | 按钮栏内按钮应 borderless：`fragment_component_editor.xml:259`、`fragment_event_editor.xml:199` |
| `UselessParent` | 7 | 多余嵌套：`fragment_about.xml:425`、`fragment_admin.xml:605`、`fragment_settings.xml:51,129,212`、`fragment_theme_selection.xml:275`、`item_custom_component.xml:11` |
| `Overdraw` | 7 | 背景重复绘制 |
| `HardcodedText` | 537 | **不需要处理**：项目规范明确要求文案不外部化到 `strings.xml`，这是预期噪声 |

### 5.3　死资源（可直接删）
- `layout_empty_state.xml`、`layout_error_state.xml`：**`res/layout` 里只有 1 处 include，Kotlin 侧零引用**（全项目 grep `<layout>` include 只命中 `fragment_bookmark_list.xml:66`、`fragment_reading_history.xml:67`）。其余 30+ 个界面各自内联空态/错误态，共 157 处 `visibility="gone"`。
  → 二选一：**统一复用这两个布局**（推荐，能顺手统一空态文案与间距），或删掉它们。
- 1220 条 `UnusedResources` 中，`drawable/ic_abc.xml`（1700+ 行遗留矢量）是最大单文件，且是第四节硬编码颜色的主要来源。

---

## 六、结构性观察（不属于缺陷，但影响可维护性）

1. **两套语法高亮器，浅色配色已实际不一致**
   `util/SyntaxHighlightUtil.kt:28-35`（通用多语言）与 `ui/analyzer/JavaSyntaxHighlighter.kt:24-29`（Java）各维护一份配色表，深色值相同，**浅色值已经分叉**：

   | token | SyntaxHighlightUtil | JavaSyntaxHighlighter |
   |---|---|---|
   | keyword | `#0000FF` | `#0033B3` |
   | annotation | `#808000` | `#9E880D` |
   | type | `#000080` | `#000000` |

   同一次会话里两个界面的浅色高亮观感不同。两者都读 `configuration.uiMode`，**与应用内主题切换是一致的**（`SkNoteApp.applyThemeMode` → `setDefaultNightMode` 会更新配置），所以这不是深色模式 bug，而是重复实现导致的分叉。
   → 建议保留 `SyntaxHighlightUtil`，让 `JavaSyntaxHighlighter` 委托给它。

2. **`fragment_create_snippet.xml` 与 `fragment_create_share.xml` 约 80% 重复**（差异仅 50 行：分享多了下载链接/提取密码/文件大小三个字段）。可抽公共骨架 + `<include>`。

3. **缺少样式资源层**：没有 `res/values/styles.xml`，导致 `IconTile` 出现 38dp/40dp/42dp、圆角 11dp/12dp、padding 8dp/9dp **三套规格**；tab 也有两种声明方式（`fragment_share_manage.xml:28-41` 用 XML `TabItem`，`fragment_category_manage.xml:20-29` 用 Kotlin `addTab` 并额外写死 `tabIndicatorColor/tabSelectedTextColor/tabTextColor`）。建一个 `styles.xml` 是 P1-2／P2 那批问题的根治手段。

4. **`AuthActionHelper.kt:43,55` 用 `Toast`** 报"请先登录/无权限"，违反"不要 Toast"，且此时 Fragment 可能已 detach，`requireContext()` 有崩风险。9 个 manage Fragment 全走这条路径。同文件 `requireLoggedIn:26` 已是正确的 Snackbar 写法，可参照改。

---

---

## 八、已执行的修复（本轮完成）

**验收结果：Lint error `22 → 0`，`:app:compileDebugKotlin` 与 `:app:lintDebug` 均 BUILD SUCCESSFUL。**

### 功能正确性（4 个 P0 全部修复）

| 问题 | 改动文件 | 修法 |
|---|---|---|
| P0-1 编辑讨论分类变成新建 | `DiscussionCategoryEditorFragment.kt` | `slugEditedManually` 不再在构造期读 `categoryId`，改到 `onViewCreated` 里（`savedInstanceState == null` 时）赋值 |
| P0-2 管理中心入口永久消失 | `AdminFragment.kt` | `setVisibility` 先 `animate().cancel()`，返回条件加 alpha 判断，`withEndAction` 内加 `alpha == 0f` 守卫 |
| P0-3 发布结果旋转后丢失 | `ReleaseEditorViewModel`／`ReleaseEditorFragment.kt`／`ReleaseManageFragment.kt` | 删掉回调，改为 `saveSuccess` / `error` LiveData + `onSaveHandled()`（对齐 `ArticleEditorViewModel` 既有模式）；刷新 key 抽为 `RESULT_REFRESH_KEY` 常量 |
| P0-4 分析器预览深色下不可读 | `ProjectAnalyzerFragment.kt` | 新增与 `adaptUserTextColor` 对称的 `adaptUserBgColor`，近白不透明背景回落到 `colorSurfaceContainer` |

### Lint error 22 → 0

| 类别 | 数量 | 修法 |
|---|---|---|
| `NewApi` (`lineHeight`) | 4 | 改用 `TextViewCompat.setLineHeight()`（`BlockCompareDialog.kt`、`ReferenceDetailFragment.kt` ×3） |
| `NewApi` (`windowLightNavigationBar`) | 4 | 移入新增的 `values-v27/themes.xml` 与 `values-night-v27/themes.xml`（成对声明，夜间才不会被浅色值覆盖）。注意：v27 文件里 **parent 必须显式写出**，否则点号命名会推断出不存在的 `Theme` 父样式导致 AAPT 链接失败 |
| `UseAppTint` | 12 | `android:tint` → `app:tint`（8 个布局） |
| `RecyclerView` | 1 | `LogicVisualizerFragment.kt` 点击时改用 `holder.bindingAdapterPosition` 并加 `NO_POSITION` 守卫 |
| `UnspecifiedRegisterReceiverFlag` | 1 | **lint 误报**，加 `@Suppress` 并注明 API 33 以下无该参数 |

### 规范与体验

| 问题 | 改动 |
|---|---|
| `notifyDataSetChanged` 滥用 | 4 个 manage Adapter → `notifyItemRangeChanged(0, itemCount)`（Lint 该项 8 → 4） |
| `PopupMenu` | `UserManageFragment` → `MaterialAlertDialogBuilder.setItems`；`UserManageAdapter` 回调简化为 `(User) -> Unit` |
| 重置密码对话框 | 裸 `EditText` + **px** padding → `TextInputLayout` + `TextInputEditText`，`dp()` 换算，字段级 `inputLayout.error`（校验失败不关闭对话框） |
| emoji 当图标 | `item_share_manage.xml` / `item_snippet_manage.xml` 增加 `ic_download` / `ic_favorite` 图标 + 计数/时间独立 TextView；两个 Adapter 去掉 `↓` `❤` |
| px 单位 | `UserManageAdapter` 徽章圆角 `8f` → `8f * density`；`CategoryIconPickerAdapter` 描边 width 乘 density |
| 筛选 Tab 不同步 | `ShareManageFragment` 按 `viewModel.filter.value` 回填 `getTabAt(index).select()` |
| 自我保护竞态 | `UserManageFragment` 把 `getUsername().first()` 提到建 adapter 之前（`setupManageUi` 改 `suspend`） |
| 工具栏滚走 | `fragment_admin_center.xml` / `fragment_cache_manage.xml` 改为 `LinearLayout(vertical)` + `NestedScrollView(weight=1)`，Toolbar 固定在顶部 |
| 门禁 Toast | `AuthActionHelper.requireRolesOrExit` 加可选 `anchorView`，有锚点走 Snackbar、无锚点保留 Toast 兜底（默认参数，9 个调用点行为不变，无回归风险） |

### 尚未修复（留给后续）

- **165 处 `ContentDescription`**、**22 处 `SmallSp`（10sp < 11sp）**、**4 处缺 `labelFor`/`hint`** —— 纯增量、不影响功能，建议单独一批处理
- 触控目标 < 48dp（各 `item_*_manage.xml` 的 36dp ImageButton）
- `res/values/styles.xml` 样式层与 `IconTile` 统一（可一次性收掉卡片圆角 11/12/18/20/21/24dp 的分散值）
- `layout_empty_state` / `layout_error_state` 死布局的复用或删除；`ic_abc.xml` 等死资源清理
- `ProjectAnalyzerFragment` 容器分层色 `containerColors` 6 个固定饱和色改 M3 surface 角色
- 启动页 `splash_background` 夜间值与浅色相同（空操作）＋ `surface_card` 夜间值与 M3 `colorSurface` 不同源导致的色差接缝

---

### 第二批（已执行）

| 项 | 结果 |
|---|---|
| 新增 `res/values/styles.xml` | 定义 `Widget.SkNote.IconTile`（42dp + 圆角 12dp + `cardElevation=0dp`，尺寸取自规范模板）、`IconTile.Small`、`Card`、`TabLayout`，作为新代码基准。已解包 material 1.11.0 确认 `Widget.Material3.CardView.Elevated` / `Widget.Material3.TabLayout` 等 parent 真实存在 |
| 触控目标 ≥ 48dp | 管理列表 7 个 `item_*.xml`：`ImageButton` 36dp → 48dp（15 个）；`item_share_manage.xml` / `item_release_manage.xml` 的 `TextButton` `minHeight` 36dp → 48dp（4 个），不再覆盖 M3 默认值 |
| 门禁 | `:app:compileDebugKotlin` 与 `:app:lintDebug` 均 BUILD SUCCESSFUL，error 保持 0 |

**关于内联图标底批量化：没有做。** 统计出 4 套并存规格（42dp/9dp ×20 次、40dp/8dp ×11、38dp/11dp ×8、36dp/18dp ×3）。把 30 处内联 `MaterialCardView` 改成 `style=` 属于零功能变化的机械重构，却要一次性引入 30 处回归面，而配色又各不相同（无法用 replace_all 安全批量处理）。因此只建立样式基准并在此登记迁移清单，留待需要改动对应布局时顺带收敛。

---

## 九、剩余待办（按建议顺序）

> 原「第一批（功能正确性）」「第二批（规范一致性）」的主要项均已完成，见第八节。

**下一批**
1. 触控目标续清（10 个 40dp、1 个 36dp）：`fragment_search.xml`、`fragment_reference_search.xml`、`fragment_share_list.xml`、`fragment_snippet_list.xml`、`dialog_block_compare.xml`
2. 内联图标底迁移到 `Widget.SkNote.IconTile`（30 处，建议按文件逐个改，勿批量）
3. `fragment_create_snippet.xml` / `fragment_create_share.xml` 抽公共骨架 + `<include>`

**再下一批（体验打磨）**
4. 165 处 `ContentDescription`（先做 `fragment_about.xml` 与各 `item_*.xml`）
5. 22 处 `SmallSp`（10sp → 11sp）、4 处补 `labelFor` / `hint`
6. 处置 `layout_empty_state` / `layout_error_state`（复用或删除）
7. `ProjectAnalyzerFragment` 的 `containerColors` 6 个固定饱和色改 M3 surface 角色
8. 启动页 `splash_background` 夜间值、`surface_card` 夜间值与 M3 `colorSurface` 对齐
9. 死资源清理（`chip_background_selector.xml`、`ic_abc.xml`、约 297 个零引用 drawable）

**需要产品决策**
10. `SyntaxHighlightUtil` 与 `JavaSyntaxHighlighter` 两套配色的浅色值归一（需确认以哪套为准）
11. `AuthActionHelper.requireRolesOrExit` 的 9 个调用点是否传入 `anchorView` 以彻底去掉 Toast
