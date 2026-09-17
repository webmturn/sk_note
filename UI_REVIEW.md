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
「`colors.xml` 夜间漏配颜色」这个常见头号根因**在本项目不成立**：day 29 名 = night 29 名，零缺失、零多余。`values-night` 未重复声明 3 个 `Widget.SkNote.*` style **不是缺陷**，夜间同样生效 —— 但理由不是「资源按名合并」（此说法有误，见 P0-5），而是：这 3 个 style 在**任何**配置目录下都没有竞争者，唯一的默认变体在所有配置下都会被选中。`colorSurface`/`colorOnSurfaceVariant`/`colorOutline` 两侧都未声明是**正确的**，由 `Theme.Material3.DayNight` 提供。

### P0-5　主题在全部 API 27+ 设备上失效，项目配色从未生效（真机发现，已修复）

**现象**：用户反馈「顶部状态栏颜色很突兀」。深色模式下状态栏是浅紫色 `#D0BCFF`，与深色内容区割裂。

**根因**：`Theme.SkNote` 同时定义在 `values/themes.xml` 与 `values-v27/themes.xml`。同名 style 在不同配置目录下是互相**替代**的资源变体，Android 按配置整体选取其中一个，**不会把两个目录的条目合并**。而 `values-v27` 那份只写了 `android:windowLightNavigationBar` 一项。

`aapt2 dump resources` 的编译产物是铁证：

```
resource 0x7f120279 style/Theme.SkNote
  ()          (style) size=11 ...
  (night)     (style) size=11 ...
  (v27)       (style) size=1    0x0101056c=true
  (night-v27) (style) size=1    0x0101056c=false
```

API 31 的深色设备命中 `(night-v27)`，`size=1` —— 整个主题只剩一项。丢失的条目：

| 丢失项 | 后果 |
|---|---|
| `android:statusBarColor` | 状态栏被 M3 基线 `colorPrimary` 接管 |
| `android:windowLightStatusBar` | 状态栏图标明暗不受控 |
| `android:navigationBarColor` | 导航栏同前 |
| 8 × `colorPrimary*` / `colorSecondary*` | **整套配色退回 M3 基线** |

`Theme.SkNote.Launch` 同一病症（`(v27)`/`(night-v27)` 均 `size=1`），丢失 `windowBackground=@color/splash_background`，启动页底色也不对。

**这不是"状态栏一个颜色"的问题** —— 项目自定义配色在**所有 API 27+ 设备上从未生效过**（即绝大多数在用设备）。

**真机量化证据**（同机同页面前后对比，采样步长 2px）：

| 颜色 | 修复前 | 修复后 |
|---|---|---|
| `@color/surface_card` 夜间 `#1E1E2E` | 0 px | 27 550 px |
| `@color/primary` 夜间 `#90CAF9` | 0 px | 354 px |
| `@color/primary_container` 夜间 `#004A77` | 0 px | 16 608 px |
| M3 基线 `colorPrimary` 深色 `#D0BCFF` | 27 528 px | 0 px |

`#D0BCFF` 与 AAR 中 `m3_sys_color_dark_primary` → `m3_ref_palette_primary80` = `#ffd0bcff` 逐位相符，坐实了"配色被 M3 基线接管"这一判断。

**修复方式**：主题拆成「Base + 薄包装」两层，与 API 级别无关的条目全部下沉到 `Theme.SkNote.Base` / `Theme.SkNote.Launch.Base`（只在 `values/` 与 `values-night/` 之间切换），`Theme.SkNote` / `Theme.SkNote.Launch` 自身只负责叠加 API 相关项，且**所有变体的 parent 都指向同一个 Base**：

```
Theme.SkNote        () size=0  (v27) size=1  (night-v27) size=1   parent 均为 Theme.SkNote.Base
Theme.SkNote.Base   () size=11 (night) size=11
```

这样任何一个变体都不会再丢掉条目。

**同类反模式全项目排查**：写脚本解析 `aapt2 dump resources`，对全部 11 个项目 style 检查「变体条目数不一致且父样式不同」的组合 —— 除已修复的这两个外**无其他残留**。

**回归门禁**：`:app:assembleDebug` + `:app:lintDebug` 通过，lint error 0、warning 2355、`NewApi` 0 条。

> 教训：这条缺陷在任何静态审查里都看不出来——`values/themes.xml` 和 `values-night/themes.xml` 内容完全正确，错的是"同一 style 出现在第三个配置目录"这件事本身。只有把编译产物 dump 出来看配置变体、并在真机上量像素，才能发现。

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

### 第三批（触控目标续清，已执行）

穷尽扫描全部 layout 后，把**所有**可交互控件的触控目标提升到 ≥ 48dp：

| 位置 | 原尺寸 | 修复 |
|---|---|---|
| `fragment_search.xml` / `fragment_reference_search.xml` 返回、清除、搜索 | 40dp ×6 | 48dp（行高变 64dp，即标准 Material 顶栏高度） |
| `fragment_share_list.xml` / `fragment_snippet_list.xml` 卡片内清除、搜索 | 40dp ×4 | 48dp，且卡片 `paddingVertical` 10dp→6dp，整卡高度维持在约 60dp 不无谓变高 |
| `dialog_block_compare.xml` 关闭按钮 | 36dp | 48dp，标题栏 padding 16dp→16/12dp（总高约 72dp） |
| `fragment_reference.xml` 排序按钮 | **24dp** | 48dp |
| `item_comment.xml` 点赞按钮 | **28dp** | 48dp |
| `fragment_discussion_detail.xml` 取消回复 | **28dp** | 48dp |
| `fragment_public_profile.xml` / `fragment_user_profile.xml` 关注与粉丝统计项 | 无最小高度 | 补 `android:minHeight="48dp"` |

**过程中发现报告原先的一处不准**：24dp 排序按钮与 28dp 点赞/取消回复这三处是 `ImageView` + `android:clickable="true"`（不是 `ImageButton`），我先前的扫描只覆盖了 `ImageButton` 标签，所以漏掉了——它们是全 App 最小的可点击目标（比报告里写的 36dp 更严重）。已改用**同时匹配 `clickable="true"` 与按钮标签**的扫描并复查到零遗漏。

不计入修改的项（已确认达标，属扫描器误报）：`fragment_article_detail.xml` 底栏的 `0dp + weight` 子项，其父容器固定 `48dp`。

**门禁**：`:app:compileDebugKotlin` 与 `:app:lintDebug` 均 BUILD SUCCESSFUL，error 保持 0。

#### 第三批的复审与更正（重要）

上表的扫描器有**两处盲区**，导致"零遗漏"的结论不成立，复审后更正如下：

1. 只匹配 `<ImageButton>` 标签 → 漏掉用 `ImageView`/`TextView` + `android:clickable="true"` 实现的按钮（如 24dp 排序、28dp 点赞，前文已述）。
2. 跳过 `wrap_content` 尺寸 → 漏掉高度由内容决定但实测不足 48dp 的控件。

复审（带父容器解析）额外发现并已修复：

| 位置 | 原尺寸 | 说明 |
|---|---|---|
| `item_comment.xml` 回复/复制/编辑/删除 | 约 24dp | 同行点赞图标已是 48dp 并撑高整行，四个文字按钮改 `match_parent` 即随行高达标，**可见文字与行高均不变** |
| `item_notification.xml` 删除按钮 | 约 26dp | 补 `minHeight`/`minWidth` = 48dp |
| `fragment_reference.xml` 细分按钮 | 约 33dp | 补 `minHeight` = 48dp |

**复审同时抓出我自己引入的 3 处误伤**（批量替换 `layout_height="wrap_content"` 时波及了同文件内的非交互 TextView）：`tvAuthor`、`tvTime`、`tvLikeCount` 被误改为 `match_parent`，已全部改回。这说明对该文件做 `replace_all` 是不安全的——已改为逐控件精确替换。

**仍然存在、未修复的 <48dp 控件（22 处）**，按类型分成两类：

- **`ImageButton`（无自动扩展机制，建议修）**：`item_image_preview.xml` 移除图片 22dp、`fragment_create_discussion.xml` 清除附件 28dp / 添加图片 40dp、`fragment_snippet_detail.xml` 复制 32dp、`fragment_reference_detail.xml` 复制代码 32dp、`fragment_share_detail.xml` 点赞与下载 40dp
- **`MaterialButton`（M3 样式自带状态层与语义，影响较小）**：`fragment_create_discussion.xml` 格式工具栏 7 个 36dp / 提交 42dp、`fragment_public_profile.xml` 关注 36dp、`fragment_palette_detail.xml` 全部导入 36dp、`item_block_share_preview.xml` 复制代码与导入 36dp、`fragment_notifications.xml` 全部已读/全部删除 40dp 等

---

### 第四批（无障碍与字号，已执行）

| 项 | 结果 |
|---|---|
| 功能性控件无障碍描述 | Lint 报的 165 处 `ContentDescription` 里，**只有 6 处是真正缺描述的可交互控件**：逻辑可视化新建调色板 FAB、项目资源添加 FAB、参考页回到顶部 FAB、参考页排序按钮、讨论详情取消回复、评论点赞。已全部补齐中文描述（图标按钮缺描述时读屏只能念出资源名）。`ContentDescription` 165 → 159 |
| `SmallSp` 字号下限 | 22 处低于 11sp（21 处 10sp + 1 处 9sp），集中在参考页统计块与列表项的分类/类型/状态徽章、下载量等元信息。统一提升到 11sp，**`SmallSp` 22 → 0**，全仓已无 <11sp 的 `textSize` |

两项都做了防误改核对：`ContentDescription` 的分类覆盖了 `ImageButton` 与 `FloatingActionButton`（它们默认可点击、不含 `android:clickable`，是先前判定的漏洞）；`SmallSp` 改动前逐文件比对 `textSize` 出现次数与 Lint 报数，确认没有误伤不同用途的字号。

**关于剩余 159 处装饰性图片**：它们不是缺陷——头像占位、`ic_chevron_right` 箭头、图标底板等，按 Material 指引可用 `android:importantForAccessibility="no"` 显式排除。**没有做批量脚本改写**：这 159 处涉及约 40 个文件，属于零行为变化的机械改动，用脚本一次性改写风险高于收益（本批已经出现过一次 `replace_all` 误伤同文件非目标控件的先例）。建议在后续改到对应布局时顺带补，或单独做一次带备份与逐文件校验的批量处理。

**门禁**：`:app:compileDebugKotlin` 与 `:app:lintDebug` 均 BUILD SUCCESSFUL，error 保持 0；warning 2545 → 2523。

---

### 第五批（触控目标收尾与下拉框标签，已执行）

| 项 | 结果 |
|---|---|
| 剩余 7 处 <48dp 控件 | 见下表 |
| `LabelFor` | 4 处 → **0** |

**叠在缩略图上的圆形按钮**（不能直接放大，否则遮住图片内容）：改为 48dp 触控区 + `inset` 把可见圆形还原成原尺寸 + 等量负边距保持位置。

| 位置 | 处理 |
|---|---|
| `item_image_preview.xml` `btnRemove` | 22dp → 48dp 触控区，`inset*=13dp` + `margin=-13dp`，可见圆形仍 22dp、位置不变 |
| `fragment_create_discussion.xml` `btnClearAttachment` | 28dp → 48dp 触控区，`inset*=10dp` + `margin=-4dp`，可见圆形仍 28dp、外边距仍 6dp |
| `fragment_snippet_detail.xml` `btnCopy` | 32dp → 48dp，并移除覆盖 M3 默认高度的 `insetTop/Bottom=0dp` |
| `fragment_reference_detail.xml` `btnCopyCode` | 32dp → 48dp |
| `fragment_create_discussion.xml` `btnAddImage` | 40dp → 48dp |
| `fragment_share_detail.xml` `btnLike` / `btnDownload` | 40dp → 48dp，移除 `inset`，底栏 56 → 64dp |

**下拉框无障碍标签**：4 处 `ExposedDropdownMenu` 的 `AutoCompleteTextView` 既无 `hint` 也无 `labelFor`，读屏无法识别字段含义（`选择分类` / `分类` / `编程语言`）。补上与父 `TextInputLayout` 一致的 `hint`，浮动标签行为不变。

**门禁**：`:app:compileDebugKotlin` 与 `:app:lintDebug` 均 BUILD SUCCESSFUL；`error`、`LabelFor`、`SmallSp` 均为 0。

---

### 第六批（创建页去重，已执行）

`fragment_create_snippet.xml` 与 `fragment_create_share.xml` 的**未登录占位块**（图标 + 主标题 + 说明 + 去登录按钮，44 行）完全一致，仅两处文案不同。抽出为 `layout_auth_required.xml`，两页改用 `<include>`，各减少 42 行重复。

| 文件 | 变化 |
|---|---|
| `layout_auth_required.xml`（新增） | 52 行共享结构，两处文案留空由 Fragment 设置 |
| `fragment_create_snippet.xml` / `fragment_create_share.xml` | 各 +2 / −44 |
| `CreateSnippetFragment.kt` / `CreateShareFragment.kt` | 各 +6 / −2（补两行文案 + 调整 include 访问方式） |

**过程中由编译器发现并确认了一个 include 绑定语义**：在 `<include>` 上写 `android:id` 后，生成的字段是**包含布局的绑定对象**（`LayoutAuthRequiredBinding`）而非根视图，根视图与子视图都要经由它访问——`binding.btnGoLogin` 与 `binding.layoutAuthRequired.visibility` 两处旧调用因此报 `Unresolved reference`，已改为 `binding.layoutAuthRequired.btnGoLogin` 与 `binding.layoutAuthRequired.root.visibility`。这一条已写入代码注释，避免后续再踩。

**文案没有放进共享布局**，改由各 Fragment 设置（与 toolbar 标题的既有处理方式一致），避免为两行文字再拆出参数化布局。

**门禁**：`:app:compileDebugKotlin` 与 `:app:lintDebug` 均 BUILD SUCCESSFUL，error 0，warning 2523 → 2517。

---

### 第七批（装饰性图片标记，已执行）

剩余 158 处 `ContentDescription` 全部确认为**装饰性图片**（箭头、占位头像、图标底板、空态插画、与文字并列的品牌 logo），按 Material 指引补 `android:importantForAccessibility="no"` 显式排除，避免读屏把无信息量的图标念成资源名。

**动手前的复核**（这一步排除了"启发式误判"的风险）：

| 疑点 | 核实结果 |
|---|---|
| `@drawable/splash_logo` ×2 | `activity_splash.xml` 的那处 `alpha="0"` **完全不可见**；`fragment_login.xml` 的那处紧邻标题文字 `Sketchware Pro 手册`，标记后可避免与文字重复播报。两处都无需真实描述 |
| 空态插画（`ic_empty*`） | 相邻均有空态文案（`tvEmptyTitle` 等）承担语义 |
| 5 处无固定 `src` 的图片 | `ivIconDetail` / `event_icon` / `ivPreview` / `ivIcon` / `ivEmptyIcon`，均为"图标旁必有文字"或"图片本身即内容但容器已带交互语义"的场景 |

**执行方式**：脚本按 Lint 报告给出的**精确文件与行号**插入，不做全仓正则替换；执行前整目录备份（107 个文件）。

**改后全量校验**：107 个 layout XML 格式正确；158 处属性**逐条回溯确认落在 `ImageView`/`ImageButton` 元素内**（不是只看文件数量）；diff 抽查确认每处只增加了该属性、无其他变化。

**门禁**：`:app:compileDebugKotlin` 与 `:app:lintDebug` 均 BUILD SUCCESSFUL；**`ContentDescription` 158 → 0**，warning 2517 → **2359**，error 保持 0。

### 第八批（图标底板复用样式，已执行）

内联图标底板迁移到 `Widget.SkNote.IconTile`。

**先纠正我自己先前的估算**。我在第六批后写的是「42dp×20 / 40dp×11 / 38dp×7-8 / 36dp×3-4，共约 30 处」——这是凭印象写的。实际扫描 107 个 layout 后的事实是：

| 规格 | 处数 | 结论 |
|---|---|---|
| 42dp / 圆角 12dp / padding 9dp | 25 | 与 `Widget.SkNote.IconTile` 精确相符 |
| 38dp / 圆角 11dp / padding 8dp | 8 | 与 `IconTile.Small` 精确相符 |
| 40dp / 圆角 10dp | 9 | 无对应样式，**未迁移** |
| 40dp / 圆角 12dp | 3 | 无对应样式，**未迁移** |
| 其余（24dp–80dp） | 21 | 角色各异（头像、圆形统计块等），**未迁移** |

即真正同质且能零视觉变化迁移的是 **33 处**，分布在 7 个文件，且这 33 处的属性顺序**完全一致**（`layout_width, layout_height, cardCornerRadius, cardElevation, cardBackgroundColor, layout_marginEnd`）。40dp 那两组若迁移必须先把尺寸改成 42dp，属于视觉变更，不做。

**为什么必须先取证而不能凭记忆**：这次迁移依赖一个关键假设——`layout_width`/`layout_height` 写在 style 里能否对布局中的子 View 生效。若假设错误，33 个底板会全部塌陷。查证过程：

1. 扫 Gradle 缓存中 **163 个 Google/AndroidX AAR**：共 2009 个 `<style>`，其中 25 个声明了 `layout_width/height`，layout 中 `style=` 引用共 60 处，但**两者零交集**——即 Google 自己从不这样用。这是黄灯。
2. 读本机 AOSP 源码（`sdk/sources/android-36.1`）定论：`ViewGroup.java:8379` 的 `LayoutParams(Context, AttributeSet)` 调用 `c.obtainStyledAttributes(attrs, R.styleable.ViewGroup_Layout)`，经 `Resources.java:2645` 走到 `Theme.obtainStyledAttributes(set, attrs, 0, 0)`，其 javadoc 明确写着 *"if the given AttributeSet specifies a style class (through the `style` attribute), that style will be applied on top of the base attributes it defines"*，且四级优先级中第二位就是 `The style resource specified in the AttributeSet`。**假设成立**。
3. 另确认本项目主题未覆盖 `materialCardViewStyle`，故显式 `style=` 不会绕过主题默认值。

**执行方式**：脚本按扫描得到的精确站点（按"最内层包含该 `cardBackgroundColor` 的 MaterialCardView"定位，因为这些底板嵌套在更大的内容卡片内）从文件底部向上改写；执行前备份 7 个文件。

**改后校验**：独立验证器从备份与新文件分别解析每个底板的几何、底色、`layout_marginEnd`、子 `ImageView` 全部属性，新文件的几何**沿 style 链解析**后比对——55 项检查全部通过，几何仍为 25×42dp/12dp 与 8×38dp/11dp。

**diff**：33 增 / 132 删（= 33 × 1 增 4 删）。

### 第九批（筛选 Tab 复用样式，已执行）

8 个 XML 声明的 `TabLayout` 中 6 个内联了完全相同的三行配色，迁移到 `Widget.SkNote.TabLayout`，layout 部分共 11 增 / 23 删（= 6 × 2 增 4 删中的三行配色，其中 `fragment_reference_container` 的 `tabTextColor` 非末行故为 1 增 3 删）。已确认 M3 主题的 `tabStyle` 默认值即 `Widget.Material3.TabLayout`（该样式的父级），且本项目主题未覆盖 `tabStyle`。

另两个（`fragment_project_resource`、`fragment_share_manage`）**未声明任何配色属性**，套用样式会改变外观，故保留原状——这属于遗留不一致，需要时单独决策。

**顺带删除 `Widget.SkNote.Card`**。它是我此前引入但从未被任何 layout 引用的死代码。动手前统计其候选点只有 12 处且角色并不同质：

- `fragment_cache_manage.xml:44` 是 64dp **圆形**「总缓存大小」
- `item_comment.xml:18` 是 32dp **圆形头像**
- 6 个 `item_*_manage.xml` 是**布局根节点**（带 `xmlns` 声明）
- 只有 5 个是真正的页面级内容卡片

以 `Card` 之名归并这四类角色是误导，强行套用会换来 12 处回归面却只省 24 行，故**删除而非套用**。至此 `styles.xml` 中 3 个样式全部被引用。

**门禁**：`:app:processDebugResources` 实际执行并成功（证明改后的 layout 与 `styles.xml` 真被编译链接，不是缓存命中）；`:app:compileDebugKotlin`、`:app:lintDebug` 通过；error 保持 0，本批 warning 2357 → **2355**（第八批为 2359 → 2357）。

### 第十批（主题配置变体替代整个 style，已修复）

用户反馈「顶部状态栏颜色很突兀」后定位到的 **P0-5**：`Theme.SkNote` / `Theme.SkNote.Launch` 在 `values-v27/` 里只写了 `windowLightNavigationBar` 一项，而同名 style 跨配置目录是**互相替代**而非合并，导致 API 27+ 设备上整个主题只剩一项，项目自定义配色从未生效。详见「四之二、P0-5」。

改动 4 个文件：`values/themes.xml`（新增 `.Base` 与薄包装）、`values-night/themes.xml`（只保留 `.Base`）、`values-v27/themes.xml` 与 `values-night-v27/themes.xml`（`parent` 改为对应 `.Base`）。

**真机验证**：修复前后同机同页面采样，`#D0BCFF`（M3 基线紫）27 528 px → **0 px**，`@color/surface_card` 夜间 `#1E1E2E` 0 px → **27 550 px**。

**顺带订正**：我在「前提修正」里写的「资源按名合并」是错的，正确表述是「该 style 在任何配置目录下都没有竞争者，故默认变体在所有配置下生效」。这个错误理解正是本缺陷的成因。

### 第十一批（底部导航栏，已修复）

用户提出「优化底部导航栏」后，在真机上量到三处问题：

**1. 几何挤压**。`activity_main.xml` 写死 `layout_height="64dp"` 与 `itemPaddingTop/Bottom="2dp"`，装不下「32dp 指示条 + 标签」。实测指示条距栏顶 **2dp**、标签底距屏幕底 **2.3dp**，两头贴边。M3 的 `itemPaddingTop/Bottom` 默认是 12dp/16dp，`m3_comp_navigation_bar_container_height` 是 80dp —— `12+32+标签+16 ≈ 80dp`，这正是那些取值的来由。改回 M3 规范后实测：指示条 **32dp 高 × 64dp 宽、距栏顶 12dp**，标签底距 **15dp**，图标 24dp。

**2. 配色样式是死代码**。`Widget.SkNote.BottomNavigation` 与它的两个依赖样式从未被任何布局引用，配套的 `res/color/bottom_nav_icon_color.xml` 也成了孤儿。导航栏跑的一直是 M3 默认色：

| 项 | 修复前（M3 默认） | 修复后（项目原意） |
|---|---|---|
| 指示条 | `colorSecondaryContainer` #324957 | `colorPrimaryContainer` #004A77 |
| 选中图标 | `colorOnSecondaryContainer` #CFE5ED | `colorPrimary` #90CAF9 |
| 底栏背景 | M3 `surfaceContainer` #211F26 | `@color/surface_card` #1E1E2E（与状态栏一致） |
| 阴影 | 3dp | 0dp |

同时删掉样式里为旧的 64dp 紧凑栏配的 `itemPaddingTop/Bottom=8dp`，否则会与 M3 的 12/16 打架。

**3. 隐藏后仍吞点击**。`MainActivity` 隐藏导航栏时只做 `alpha→0` + `translationY`，**从未置 `GONE`**。`alpha=0` 的可点击 View 仍参与命中测试（`View.onTouchEvent` 在 DISABLED 分支依然 `return clickable`），底部这条区域会一直吃掉点击。现改为动画结束后置 `GONE`。实测非顶层页导航栏已不在视图树中，返回后正常滑入。

**顺带的单一来源化**：新增 `res/values/dimens.xml`。原先 5 个布局共 8 处硬编码 `88dp`（= 旧 64dp + 24dp 余量）散落各处，加高时必须逐个手改且容易漏。现统一为 `bottom_nav_content_inset = bottom_nav_height + 24dp = 104dp`。`SnippetListFragment` 是「参考」页的子 Fragment（导航栏可见），它的两处 88dp 也在其中。

**已知残留（未改，属设计选择）**：选中项图标中心距栏顶 **27.7dp**，未选中三项 **39.8dp**，相差 **12.1dp**。这是 `labelVisibilityMode="selected"` 的固有行为——未选中项因标签隐藏而重新居中。若要四项图标对齐，需改为 `labeled`（四项标签常显），那是另一项设计决定，未擅自改动。

**本批引入并已修复的回归**：上面第 3 条修复时顺手加的「滑入」写法，在顶层目标分支里**无条件**执行 `wrapper.translationY = wrapper.height`，而该分支在每次顶层目标变化时都会触发（含顶层 Tab 互切），于是每切一次导航栏先瞬间跳下去（无动画赋值 → 视觉上的硬切）再用 300ms 滑回来。用户反馈的「切换跟之前不一样，还是硬切，还会先隐藏导航栏再显示出来」即此现象。

改为状态感知：用 `navHidden` 标志控制，只有真正「隐藏 → 显示」跨状态时才滑入；已隐藏时（详情页跳详情页）不重复播放收起动画。

> **订正（第十七批时发现）**：此处原写「显示分支必须先置 `navHidden = false` 再调 `cancel()`，因为 `Animator.cancel()` 会同步触发 `onAnimationEnd`，顺序颠倒会让结束回调把导航栏置为 GONE」——**这个机制是错的**。`ViewPropertyAnimator.withEndAction` 的 javadoc 明确写着「动画被取消时该 Runnable 不会执行」，AOSP 实现里 `onAnimationCancel` 会直接把回调从 `mAnimatorOnEndMap` 移除（`ViewPropertyAnimator.java:1092-1093`）。因此 `cancel()` 根本不触发结束回调，不存在该竞态；标志位仍保留（对「详情→详情」不重播动画是必要的），但顺序只是可读性，不是防护。

**用户提到的「硬切」经查不是内容过渡的问题**。为验证内容过渡本身，临时把 `nav_fade_in/out` 从 220/160ms 改为 2400ms 后连拍，抓到了**同时显示新旧两页**的中间帧（旧页「早上好, 管理员」与新页「学习资源 / 共 510 项参考」叠加），证明 `tabNavOptions` 里的自定义淡入淡出确实生效；随后已还原动画文件，`git diff` 为空。

**真机验证**：切 Tab 连拍 8 帧，导航栏上边界恒为 2160px 全程不动；顶层页导航项数 4 → 详情页 0 → 返回后 4。

**门禁**：`:app:assembleDebug`、`:app:lintDebug` 通过；error 0，warning 2355 → **2351**。

> 订正：我原先在此处写「减少的 4 条正是原先『未使用样式』的告警」，但按样式逐个清点只对得上 3 个（`Widget.SkNote.BottomNavigation`、其 `.ActiveIndicator`、`ShapeAppearance.SkNote.BottomNav.Indicator`）。第 4 条我没有核实过，最可能的解释是 `res/color/bottom_nav_icon_color.xml`——lint 似乎不把「来自未使用资源的引用」算作使用，故当引用它的样式本身未被使用时，该 selector 也会被报为未使用；样式启用后一并消失。**这是推断，未经验证**，已从结论降级为猜测。可确认的是：当前 lint 报告中本项目样式**0 条**未使用告警。

### 第十二批（自审：复核最近三轮改动）

对 `fb71bc6`、`a00f949`、`567d8e0`、`c386165`、`1d0d231` 做逐项复核，重新扫描当前文件而非复用当时的脚本。

**复检通过（独立重扫，非复用旧结论）**

| 项 | 结果 |
|---|---|
| IconTile 引用 | 33 处（25 + 8），**0** 处缺 `cardBackgroundColor`，**0** 处唯一子节点不是 `ImageView`，**0** 处残留内联几何属性 |
| 底色分布 | green 4 / orange 7 / purple 6 / teal 4 / blue 7 / red 4 / indigo 1 = 33 ✓ |
| TabLayout | 8 个中 6 个引用样式、2 个（`fragment_project_resource`、`fragment_share_manage`）未引用；**0** 处同时保留内联配色 |
| 导航栏让位内边距 | 残留 `88dp` **0** 处；`bottom_nav_content_inset` 引用 **8** 处 |
| dimens | `bottom_nav_height` 1 处、`bottom_nav_content_inset` 8 处，均已引用 |
| 项目样式未使用告警 | **0** 条 |
| `Theme.SkNote` 配置变体 | `()` size=0、`(v27)` size=1、`(night-v27)` size=1，**三者 parent 均为 `Theme.SkNote.Base`** —— 结构未因后续改动退化 |
| `labelVisibilityMode=0` | 核对 Material 枚举（`auto=-1, selected=0, labeled=1, unlabeled=2`），0 即 `selected`，与真机观察一致 |
| 子页覆盖 | 顶层页嵌套的子 Fragment 只有 `ReferenceFragment` 与 `SnippetListFragment`（ViewPager2 两页），均已带让位内边距；`childFragmentManager` 的两处是 Dialog，不受导航栏影响 |

**发现的问题**

1. **我写的注释是错的**：`activity_main.xml` 里写「高度由样式提供」，但高度是同文件显式指定的 `@dimen/bottom_nav_height`。已改正。
2. **「减少 4 条」是未经验证的推断**（见上）。已降级表述。
3. **状态栏与内容之间残留接缝**：实测顶部 `y 0..98`（33dp）为 `#1E1E2E`（`surface_card`），`y 99+` 立即变为 `#141218`（M3 基线 `colorSurface`）。根因是项目的 `@color/surface_card` 与内容所用的 M3 `colorSurface` 来自**两套不同调色板**——这是项目原有取值，P0-5 修复只是让它按原意生效，并非新引入。若要消除，需把 `statusBarColor` 改为 `?attr/colorSurface`（与内容同色），或反过来把内容底色也纳入项目调色板。**属设计选择，未擅自改动。**
4. **导航栏高度固定为 80dp 的边界**：字号缩放 1.5 时 `12+32+标签(~17)+16 ≈ 77dp` 仍放得下，2.0 时约 83dp 会略超。本机字号缩放为 1.0。这是 M3 默认用 `minHeight` 而此处改用固定高度的代价；原实现固定为 64dp，问题更早出现。
5. **中断动画时的小跳变**：隐藏动画播放中（300ms 内）按返回，显示分支会把 `translationY` 重新设为完整高度再滑回，视觉上会多跳一下。触发窗口很短，属罕见的外观问题，未改动。
6. `wrapper.isEnabled`（`MainActivity`）：`setEnabled(false)` 对 `LinearLayout` 的子 View 无约束作用，该行实际无效——**原有代码**，非本轮引入。
7. `Theme.SkNote.Starting` 在 `values/` 与 `values-night/` 中**内容完全相同**，其引用的 `@color/splash_background` 本就按配置解析，夜间那份是冗余复制。**原有代码**，无害，未改动。

**自审的过程问题**：这次复核脚本自身先出了两个 bug——判断 `IconTile` 条数的正则要求结尾带引号（把 25 报成 0），以及 `AndroidManifest.xml` 路径写成在 `res/` 下。都已修正后重跑。

### 第十三批（补齐缺失的 tertiary 调色板，已修复）

用户反馈「一些界面布局感觉很乱」后定位到的主因之一。

项目在 `values/` 与 `values-night/` 中都**只定义了 primary 与 secondary**，主题里也只设了对应的 8 个 `color*` 属性；但全项目有 **20 处**引用 `?attr/colorTertiary*`（分布在 10 个布局）。这些引用全部落回 M3 基线的酒红。

实测手册页「随机学一个」卡片底色 **#633B48**，与 AAR 中 `m3_sys_color_dark_tertiary_container` → `m3_ref_palette_tertiary30` = `#ff633b48` 逐位相符。

**这是 P0-5 修复暴露出来的**：修复前 primary/secondary 同样走 M3 基线色，与 tertiary 同源故看不出问题；修复后主色系切换为项目的 Material Blue，唯独 tertiary 仍是 M3 酒红，冲突才显现。根因是项目**从未定义 tertiary**，我的修复把它暴露了。

补法沿用项目调色板既有规律（角色取 600/700 调、容器取 100 调；夜间角色取 200 调、容器取 900 调、`on_*_container` 与浅色容器同值），第三色取 **Material Teal**——项目的 `icon_bg_teal` 本就用 Teal 100 / Teal 700，与该色系一致。

真机实测三张快捷卡片底色：

| 卡片 | 修复前 | 修复后 |
|---|---|---|
| 随机学一个（`colorTertiaryContainer`） | #633B48（M3 酒红） | **#004D40**（Teal 900） |
| 工具箱（`colorSecondaryContainer`） | #324957 | #324957 |
| 文件分享（`colorPrimaryContainer`） | #004A77 | #004A77 |

### 第十四批（手册页间距收敛，已修复）

真机实测该页可见区 **687dp**，而第一篇文章出现在 **y≈503dp** 处——七成空间被「顶部留白 + 标题 + 搜索框 + 快捷卡片 + 分类」吃掉。

逐项核算后收紧（**仅 16 行、全部为间距值**，未动字号、颜色与结构）：

| 项 | 变化 |
|---|---|
| 顶部留白 `paddingTop` | 24 → 16dp |
| 搜索框上边距 / 高度 | 16→12dp / 48→44dp |
| 快捷区上边距 | 16 → 12dp |
| 快捷卡片 `marginVertical` | 6 → 4dp |
| 快捷卡片内边距 | 16 → 14dp |
| **图标 → 标题间隔** | **16 → 8dp** |
| 分区容器 `paddingTop` | 20 → 16dp |
| 「最新文章」行上边距 | 24 → 16dp |
| **合计** | **−48dp** |

最大的一处浪费是「图标到它自己的标题之间留 16dp」——126.6dp 的卡片里光这道缝就占 16dp。分区间隔原先 20/24/12dp 三种混用，现统一为 16dp。

> **未复测**：操作途中设备前台被切到其他应用（用户正在使用手机），我随即停止了对设备的自动操作。故本批只经 `:app:assembleDebug` 与 `:app:lintDebug` 验证（error 0、warning 2351），−48dp 为逐项差值算得，**非真机实测**。

### 第十五批（间距/字号体系盘点，未执行）

为把「乱」量化，扫描全部 107 个布局的取值分布：

| 维度 | 取值种数 | 主要离群值 |
|---|---|---|
| `padding` | 25 种 | 14dp×63、9dp×25，以及 18/7/5/1dp 等非 4dp 倍数值 |
| `layout_margin` | 21 种 | 18dp×6、7dp×4、3dp×18 |
| `textSize` | 15 种 | 13sp×106、15sp×62、11sp×61、17/19/21sp 均不在常规字阶上 |
| `cardCornerRadius` | 15 种 | **16dp×38 与 20dp×34 两套约定并存** |
| 固定 dp 高度 | 31 种 | 11/13/18/22dp 等离散值 |

圆角的 16dp / 20dp 并非干净分工：20dp 主要用于页面级卡片（12 个文件），16dp 主要用于列表项卡片，但 `fragment_cache_manage.xml`（20dp×2 + 16dp×2）与 `fragment_theme_selection.xml`（20dp×5 + 16dp×1）**同一文件内两种混用**。

图标底板另有 **42dp×25 / 40dp×12 / 38dp×8** 三种尺寸并存。

**未执行**：统一到哪个尺寸会改变 20–37 处底板的实际大小，属可见视觉变更，待产品决策；间距/字号体系化触及 100+ 布局，需分批并逐批真机验证。

### 第十六批（图标底板统一为 40dp，已执行）

承接上一批的盘点结论。项目原有**三种**「列表行首图标底板」并存，共 **42 处**：

| 尺寸 | 圆角 | 子节点 padding | 处数 | 位置 |
|---|---|---|---|---|
| 42dp | 12dp | 9dp | 25 | 设置5 / 关于6 / 管理中心7 / 缓存管理4 / 反馈2 / 主题选择1 |
| 40dp | 10dp | 8dp | 9 | 工具箱页（内联几何） |
| 38dp | 11dp | 8dp | 8 | 「我的」页（`IconTile.Small`） |

**决定性的一道算术**：

```
42dp 底板 + 9dp 内边距 = 24dp 图标字形
40dp 底板 + 8dp 内边距 = 24dp 图标字形   ← 与 42dp 完全相同
38dp 底板 + 8dp 内边距 = 22dp 图标字形   ← 唯一偏小的一组
```

即 42dp 与 40dp 两组的**字形本来就相同**，故选 40dp：视觉落差最小（2dp 不可辨，但统一后消除了「我的」与「设置」两页之间肉眼可见的差异），且净省 **−34dp**（25 处 −2dp、8 处 +2dp）。

**未援引 M3 规范**：Material 库不含 List 组件（`m3_list*` dimen 0 条），「40dp 是 M3 列表头像规范值」无法从库内证实，故理由只用项目自身数据。

改动：
- `Widget.SkNote.IconTile` 由 42dp 改为 **40dp**（圆角仍 12dp）
- 原 42dp 那 25 处的子 `ImageView` padding 9dp → 8dp（补偿底板缩小，字形不变）
- 工具箱 9 处改为引用该样式，去掉 4 行内联几何
- 「我的」8 处改用 `IconTile`，**删除 `IconTile.Small`**（无其他使用者）

另有约 20 处 `MaterialCardView`+单 `ImageView` 属别的角色（快捷卡片内图标、主题预览色块、圆形头像、品牌 logo、圆形统计块、评论区小图标等），**尺寸各异是对的**，未并入。

**验证**
- 静态：独立脚本沿 style 链解析全部 42 处，有效几何均 `40dp×40dp / 圆角 12dp / 零阴影`，子节点 padding 均 8dp、字形均 **24dp**；0 处不符、0 处仍被内联几何覆盖
- 真机（480dpi，40dp = 120px）：**我的页 8 个、设置页 5 个、工具箱页 8 个**可见底板全部实测 **120×120px**；「我的」页行间距 198→204px（+2dp/行）、设置页分区间距 407→401px（−2dp/处），与预期一致

### 第十七批（预加载界面与动画，已执行）

**订正一处我先前的错误论断**（本批最有价值的部分）

我在「导航栏切 Tab 抖动」那批里写过一个机制理由：*「显示分支必须先置 `navHidden = false` 再调 `cancel()`，因为 `Animator.cancel()` 会同步触发 `onAnimationEnd`，顺序颠倒会让结束回调把正在显示的导航栏又置为 `GONE`」*。

查 AOSP 本机源码后确认**这是错的**：

- `ViewPropertyAnimator.withEndAction` 的 javadoc：「*The action is only run if the animation ends normally; if the ViewPropertyAnimator is canceled during that animation, the runnable will not run.*」
- 实现上 `onAnimationCancel` 直接把回调从 `mAnimatorOnEndMap` 移除（`ViewPropertyAnimator.java:1092-1093`），随后触发的 `onAnimationEnd` 找不到回调，自然不执行。

所以 `cancel()` **不触发** `withEndAction`，那个竞态根本不存在。标志位仍保留（对「详情 → 详情」不重播动画是必要的），但顺序只是可读性。代码注释与本文档相应位置均已订正。

这个错误论断还差点让我去修一个**不存在的 bug**：我原以为 `hideSkeletonAndShow` 在重入时，`cancel()` 会触发上一次的结束回调把骨架置为 `GONE`，导致重新展示时骨架不可见。既然取消不触发回调，该问题不存在，未做改动。

#### 启动页

| 项 | 改前 | 改后 |
|---|---|---|
| 停留时长 | `postDelayed(1800)` 固定 1.8s | 最短 **900ms**；预热完成即进入，上限 **1600ms** |
| 入场动画总时长 | 600+500+400 = **1500ms** | 300+260+220 = **780ms** |
| 标志缩放起点 | 0.5 | 0.88 |
| 标题位移起点 | 30dp | 16dp |
| 插值器 | `set.interpolator = Overshoot(1.2)` 套到**所有**子动画 | 弹性只给缩放；淡入/位移改用减速曲线 |

第二行是关键：启动页总共只展示 1800ms，而入场动画自己就占 1500ms，几乎没有静置时间。改成自适应时长后更必须压缩。

插值器那项的根因：`AnimatorSet.setInterpolator` 会把同一个插值器设到**每个子动画**上（AOSP `AnimatorSet.java:716-719` 逐个 `node.mAnimation.setInterpolator(mInterpolator)`），文档也写明 "Sets the TimeInterpolator for all current child animations"。弹性曲线用在 alpha 上没有意义——透明度会提前顶到 1 再被钳制，观感是「闪一下」。

为此给 `StartupWarmupManager` 增加了 `suspend fun awaitWarmup(timeoutMs)`：原先两个预热都是 `scope.launch` 立即返回、**没有任何可等待的入口**，启动页只能死等。实现上先在 `synchronized` 块内取出 Job 再在块外 `join`，不在持锁状态下挂起。

#### 骨架屏动画

`SkeletonAnimator` 原为 `ofFloat(1f, 0.55f, 1f)` + `LinearInterpolator`：**三角波，谷底是尖角**，且 45% 振幅偏大，观感是「闪」而不是「呼吸」。改为：

```kotlin
ValueAnimator.ofFloat(1f, 0.7f)   // 振幅 30%
    .setDuration(800)              // 单程 800ms（原整周期 1100ms）
    .setRepeatMode(REVERSE)        // 往复而非三关键帧，谷底不再有折角
    .setInterpolator(AccelerateDecelerateInterpolator())  // 两端都有缓动
```

**未做**：启动页底色 `splash_background` 在浅色与夜间**同为 `#FF1976D2`**，深色模式下是一整屏高亮蓝，与本项目的深色表面（`#141218`）反差很大。这属设计取舍（品牌页是否要跟主题走），未擅自改动。

#### 真机实测与调参

设备恢复连接后补测。用 logcat 的 `ActivityTaskManager Displayed` 事件取时间戳，逐档收敛启动页停留时长：

| `MAX_WAIT_MS` | 实测停留 | 备注 |
|---|---|---|
| （原实现固定 `postDelayed(1800)`） | 1800ms | 与预热无关 |
| 1600ms | **1755ms** | 只比原来快 45ms |
| 1400ms | 1327ms | |
| **1100ms** | **1025ms** | 四次 1021/1023/1027/1032，方差 ±5ms |

调参依据来自两处实测：

1. **预热耗时**：把上限临时放宽到 4500ms，测得启动页停留 **2845 / 3924 / 3594ms**，即两个预热请求在手机上要 2.8–3.9s。
2. **接口本身**：同一接口从本机实测，`api/home?limit=10`（响应仅 606 字节）为 **2687 / 1589 / 470ms** —— 首次 2.7s、之后 0.47s，是**后端冷启动**而非网络或包体；`api/auth/me` 稳定约 450ms。手机侧即使后端已热（本机实测 361ms）启动页仍为 1032ms，说明进程内首个请求的 TLS 建连、以及 `prefetchCurrentUser` 前置的 DataStore 读取也要占时间。

**诚实结论**：`awaitWarmup` 在当前网络下**从未在窗口内完成**，实际效果等同于把固定 1800ms 改为固定约 1030ms。保留它是因代价有界（至多比最短展示时间多 200ms）且后端转热后能让首页直接命中缓存。**原先固定 1800ms 反而两头不落好**：既没等到预热（实测 2.8–3.9s），又白等 1.8s —— 也就是说此前 `getWarmHomeData()` 在首页读取时一直是 null，预热基本白做。

> 这一条的教训：我最初把「自适应」当成主要卖点写进提交，实测才发现自适应根本没触发。若不补真机测量，会一直以为它在工作。

#### 首页骨架屏（本批收益最大的一处）

真机抓帧发现：**首页是唯一没有骨架屏的页面**。加载中间态是「分类」「最新文章」两个标题下面**一片空白**，只有 `SwipeRefreshLayout` 的转圈（`HomeFragment` 原先只有 `swipeRefresh.isRefreshing = isLoading` 这一行加载提示）。骨架系统覆盖了其余 8 个列表/详情页，唯独漏了首页 —— 而首页恰恰是最常被看到的屏幕。

而且**缩短启动页会让这个空窗暴露得更早**，所以只调时长是治标。

改动：新增 `layout_skeleton_home.xml`（只覆盖两块动态内容，顶部问候/搜索/快捷卡片是静态的不做占位，占位块尺寸与间距对齐真实结构以免加载完成时跳动），给内容容器加 id `layoutSections`，`HomeFragment` 接入与其余 8 页同一套 `SkeletonAnimator` + `showSkeleton` / `hideSkeletonAndShow`，并用 `contentShown` 区分「首次加载走骨架屏」与「下拉刷新保留内容只用转圈」。

**踩坑**：`<include>` 不带 `android:id` 时 ViewBinding 不会在父布局生成字段；带上 id 后字段类型是被包含布局的 Binding 对象，须走 `.root`。首轮编译报 `Unresolved reference: skeletonHome`，与先前 `layoutAuthRequired` 那次同因。

**修正（用户反馈「呼吸动画之后还是跳变」）**：首版把骨架插成了垂直 `LinearLayout` 里 `layoutSections` 的**兄弟节点**，而 `hideSkeletonAndShow` 是让骨架与内容**同时可见**做 180ms 交叉淡出——于是交叉期间两者各占一份高度、内容被骨架顶到下方，骨架置 `GONE` 时下方内容整体上跳。

既有 8 个页面**无一例外**都把 `skeletonContainer` 放在 `FrameLayout` 内与内容叠加（8 个文件逐一核对过：article_list / article_detail / discussion_list / discussion_detail / share_list / share_detail / snippet_list / snippet_detail），**只有首页这一处是我插错的**。现改为同样的 `FrameLayout` 结构，骨架放最后以保证绘制在上层。

**修正后的验证**：过渡期中间帧在「分类」标题位置取到 `#7E7A82`——正是骨架块 `#202028` 与真实文字 `#E6E0E9` 的混合值；画面中骨架块与真实内容**在同一位置重合**，不再上下堆叠。过渡结束后 4 帧字节完全相同，无残余位移。

> 教训：「把骨架放进页面」不能只看能不能显示，还要看它与内容是否**共占同一空间**。`hideSkeletonAndShow` 的交叉淡出要求两者重叠，这一点在既有的 8 个页面里是隐含约定，我插入新页面时没去核对这个约定。

**修正二（用户反馈「骨架跟界面是否一致，还有界面那些布局不该加载该直接显示」）**：两处都成立。

**其一，骨架与真实界面不一致。** 原分类占位卡几乎每项都对不上 `item_category.xml`：

| 项 | 真实 `item_category` | 原占位 |
|---|---|---|
| 宽度 | **130dp 固定**（横向滚动） | `match_parent` 通栏 |
| 圆角 / 描边 | 16dp / 1dp | 20dp / 无 |
| 底色 | `colorSurfaceVariant` | `colorSurface` |
| 内边距 | 14dp | 16dp |
| 图标 | **32dp** | 40dp |
| 排布 | **垂直**（图标在上） | 水平（图标在左） |

排布方向都反了。分类列表是 HORIZONTAL 的 `LinearLayoutManager`（`HomeFragment:126`），真实形态是横向一行 130dp 卡片，而占位画的是一整张通栏卡。现按 `item_category.xml` 逐项复刻。

**其二，静态内容不该走加载态。**「分类」「最新文章」两个标题与「查看全部 →」不随数据变化，原实现把它们也遮掉换成灰块 —— 加载期间先出现灰块、加载完成再跳成文字，反而**多了一次视觉变化**（与"减少跳动"的初衷相悖）。现改为在骨架里直接显示真实文字，只有卡片是占位。

**同类问题排查**：逐一检查其余 13 个骨架布局，**均不含 Toolbar 或任何真实文字节点**；详情页的 `MaterialToolbar` 在骨架 `FrameLayout` 之外，静态工具栏本就保持可见。即这一原则在既有实现里是遵守的，**只有我新加的首页骨架违反了**。

**真机验证**（480dpi，深色，扫描 y=1150 穿过图标行）：

| 测量 | 实测 | `item_category.xml` |
|---|---|---|
| 卡片左边缘 | x=60 = **20dp** | 容器 `paddingHorizontal` 20dp |
| 卡片总宽 | 390px = **130dp** | 130dp |
| 内边距 | 42px = **14dp** | 14dp |
| 图标块 | 96px = **32dp** | 32dp |
| 卡间距 | 30px = **10dp** | `marginEnd` 10dp |

抓帧确认两个标题以真实文字显示、三张分类卡为「图标块在上 + 两行文字块在下」的垂直排布、第三张被右边缘裁切（与真实横向滚动一致）。

**真机验证**
- 启动约 1.5s 处抓帧：首页显示分类卡片骨架 + 3 张文章卡骨架，静态区不受影响
- 加载窗口内连拍 6 帧，骨架块颜色依次为 `#1C1E26`(≈0.75) → `#1C1C23`(**≈0.70**) → `#1E1D25`(≈0.85) → `#202028`(**1.00**)。中间帧证明是缓动过渡而非跳变，0.7↔1.0 区间与配置一致
- 叠加修正后复测：过渡中间帧骨架与内容同位置重合；过渡后连续 4 帧字节一致

**顺带量到一个新问题**：深色下 `skeleton_base` = `#202028` 与页面底色 `#141218` 的对比度仅约 **1.16:1**，骨架本身很淡；而脉冲是整容器 alpha 动画，可见摆幅正比于这个色差，故实测每通道只有 4/255 左右的变化。也就是说骨架「看得见、但呼吸几乎看不出来」。若要改善需提高 `skeleton_base` 的夜间对比度，而非加大脉冲幅度。**未擅自改动**（涉及配色取舍）。

**另需说明**：把脉冲幅度由 0.55 改为 0.7 是**读代码后的判断，不是观察结论**——我当时并未看到实际效果。实测表明该振幅改动带来的可见差异很小（见上），真正的改进来自把三关键帧 + 线性插值换成 REVERSE + 缓动（消除谷底折角）。

**门禁**：`:app:assembleDebug`、`:app:installDebug` 与 `:app:lintDebug` 通过，error 0、warning 2351（无新增）。

### 第十八批（自审：复核第十七批）

**方法**：不靠肉眼看图，而是对比「重构前后同一行的颜色跳变位置」。骨架卡在呼吸，**颜色会随 alpha 变化、但边界位置不会**，所以位置是稳定可比的量。

**发现一处真实回归**：

| | y=1160 的颜色跳变位置 |
|---|---|
| 抽出 `layout_skeleton_card_category` 之前（正确） | 60, 102, 198, **450, 480**, 522, …, **870, 900**, 942, … |
| 抽出之后 | 60, 102, 198, **492**, 588, …, **882**, 978, … |

`492` 恰好等于 `450 + 42`，即第二张卡图标本该在的起点 —— 说明三张卡**贴在了一起**，`layout_marginEnd` 丢失。

**根因**（AOSP `LayoutInflater.java:1136-1145`，`parseInclude`）：

```java
params = group.generateLayoutParams(context, attrs);        // 先用 <include> 标签自身的属性
if (params == null)
    params = group.generateLayoutParams(context, childAttrs); // 仅在失败时才回退到被包含的根节点
```

只要 `<include>` 标签上写了 `layout_width`/`layout_height`，`generateLayoutParams` 就会**成功**，被包含根节点上的 `layout_margin*` 全部不参与。项目既有写法是不带任何布局属性，正是为了走 `childAttrs` 那条路。

**影响面**：分类卡丢 `marginEnd` 10dp；文章卡丢 `marginBottom` 12dp 与 `marginHorizontal` 4dp —— 后者**自首页骨架第一版起就存在**，不止本次重构引入的那一处。全项目扫描同类问题共 6 处，**全部在新增的 `layout_skeleton_home.xml` 中**，项目其余文件写法正确。

**修复后验证**（480dpi，深色）：分类卡跳变位置恢复为 `450, 480, 870, 900`（卡宽 130dp、间距 10dp）；文章卡首行左边缘 `x=72px = 24dp`，即容器 padding 20dp + 卡 `marginHorizontal` 4dp；全项目复扫 0 处残留。

**顺带按项目约定重构**：三张重复的分类占位卡抽成 `layout_skeleton_card_category.xml`（对应既有的 `layout_skeleton_card_article/_discussion/_share/_snippet` 做法），不再内联复制三遍。

**两处经核实不成立的怀疑**（记录以免重复怀疑）：

1. **`isLoading` 初始值导致多余淡入** —— 不成立。`_isLoading = MutableLiveData<Boolean>()` **没有初始值**，无值的 LiveData 在注册观察者时不会回调，所以 `else` 分支不会在页面创建时误触发。另外确认了 `fetchHomeData(showLoading=false)`（命中预热缓存那条路径）**从不设置 `isLoading`**，因此也不会误播骨架。
2. **`AnimatorSet.setInterpolator` 扭曲整条时间线** —— 不成立。AOSP 文档与实现（`AnimatorSet.java:716-719`）都是**逐个子动画**调用 `setInterpolator`，不改变时间线。但它确实会把弹性曲线套到 alpha 上，这一点成立并已修（见第十七批）。

**过程问题**：本轮我误敲了一条 `git -c config.txt`，并在未改动 `UI_REVIEW.md` 的情况下 `git add` 后用了 `--allow-empty`，造出一个**内容为空、说明却写着「记录自审发现」的提交**。已补写本节内容并修正该提交 —— 空提交配不实说明属于假记录，不能留。

### 第十九批（讨论详情页改用「占位即真实 item」，已修复）

**问题**：讨论详情页从整页骨架到加载完成有明显跳变。实测骨架页头到 347dp、真实页头约 423dp，差 **76dp**。

**先调研数据，再定方案**（这一步推翻了我自己上一轮的结论）

| 项 | 结论 | 出处 |
|---|---|---|
| 讨论区列表接口 | 返回**完整正文** + `view_count` + `reply_count` + `author_avatar` | `backend/src/routes/discussions.ts` 列表查询 |
| 用户主页列表接口 | `SELECT d.*` → 正文与计数**同样完整**；但**不带** `author_avatar`，也**没有** `category_name` 别名 | `backend/src/routes/follows.ts:191` |
| 反馈入口 | 提交时**有**本地 `title/content/category` 与 `createdId` | `FeedbackFragment` |
| 通知入口 | **只有** `discussion_id` | `NotificationFragment` |
| `kotlin-parcelize` | **未启用**，且插件 Gradle marker **不在本机缓存**（只有 `kotlin-parcelize-runtime` 1.9.22，Kotlin 版本是 1.9.20） | Gradle 缓存实测 |

**关键更正**：我上一轮判断「正文高度在列表期不可知，所以跳变无法消除」是**错的**。列表接口本来就返回完整正文与各项计数 —— 页头所需数据在点击那一刻已全部在手上，只是没有传过来。因此官方做法（占位即真实 item）在这里能把页头跳变**彻底消除**，而不是只能缓解。

**改造**（9 个文件改动 + 2 个新文件）

1. 新增 `DiscussionNavArgs.kt`：把整条 `Discussion` 经 Gson 放进导航参数。不用 `Parcelable` 的理由是上面那条缓存实测 —— 启用插件要联网拉取，构建风险高于收益；Gson 本就是本工程的序列化方式，`Discussion` 在生产里一直由它反序列化。
2. 新增 `CommentPlaceholderAdapter.kt`：评论占位作为 `ConcatAdapter` 独立一段，插在页头与真实评论之间。**不改 `CommentAdapter`**。
3. `layout_skeleton_comment_row.xml` 重写为**精确等高**：色块不再是固定高度的 `View`，而是「与真实控件同字号同字距的 `TextView` + 空文本 + `minLines` + 块背景」。高度由平台按**同一套字体度量**算出，因此不需要手算 dp，也不会因 `includeFontPadding`、字体缩放或用户改字号而失配。旧实现按钮行只有 11dp，而真实 `btnLike` 是 48dp 的触控区。
4. `DiscussionDetailFragment`：抽出 `revealContent()`，冷/热两条路径收敛到一处；预填数据经 `viewModel.setPrefilledDiscussion()` 走**同一条**渲染路径（观察者），不产生「预填一套 / 接口一套」两段页头代码。
5. 5 个入口改传整条 `Discussion`：讨论区、讨论管理、公开主页、我的主页、反馈。

**自查中发现并修掉的两个真实缺陷**

1. `View.post` 在视图未 attach 时排进 `HandlerActionQueue`，由 `dispatchAttachedToWindow` 执行，**早于首次 layout**，此刻 `headerBinding` 仍为 null。旧代码 `binding.rvMain.post { prefillHeader() }` 里的 `hb ?: return` 会把预填**静默吞掉**（是否生效取决于 attach 与 layout 的先后）。改为逐帧重试到页头就绪，上限 10 帧。
2. 配置变更后 `ViewModel` 存活、`comments` 已有数据、观察者先触发；此时预填若无条件插占位行，会把占位行**追加到真实评论下方**。加 `commentsLoaded` 守卫。

**实证**

| 项 | 方法 | 结果 |
|---|---|---|
| Gson 承载无损 | 用**真实编译产物**（`kotlin-classes/debug`）+ 真实 `gson-2.10.1.jar` 跑 `toJson`→`fromJson` 比对 | `ROUNDTRIP_EQUAL=true` |
| 体积安全 | 60 段中文正文（含引号、反斜杠、换行） | JSON **4444 字节**，Binder 事务上限 524288 |
| 构建 | `:app:assembleDebug` | 通过 |
| Lint | `:app:lintDebug` | **0 errors**、2356 warnings |

**lint 的一个插曲**：首次 lint 在 `DiscussionDetailFragment.kt:391`（`R.id.action_pin -> {`）报 `SuspiciousIndentation`。那行**一行未改**，同一个 `when` 的其余 4 个分支也都没报；报错称该行「缩进了但没嵌套在 394 行的 `if (_binding == null)` 之下」，时序上不可能，属启发式被新代码带偏。把我新加的那处单行无花括号 `if`（`post { if (_binding != null) renderPendingPrefill(...) }`）改为花括号写法后消失。**未用 `lint.xml` 压制**。

**真机逐帧实测**（HUAWEI LIO-AN00 / 1176×2400 / 480dpi / 夜间；连拍 12 帧，非肉眼判断，比的是**颜色跳变位置**——位置不随呼吸 alpha 变化）

主路径「讨论区 → 详情」（选第一条，16 回复 / 44 阅读）：

| 帧 | 时间 | 评论卡顶边位置 | `#202028` 骨架块 |
|---|---|---|---|
| f1 | 115ms | 1476, 1509, 1898（**仍是列表页**） | — |
| **f2** | **574ms** | **1536**, 1758, 1974, … | **1637, 2075**（仅在评论行内部） |
| f3 | 981ms | **1536**, 1910, 2056, 2185 | 无 |
| f12 | 5474ms | **1536**, 1910, 2056, 2185 | 无 |

1. **整页骨架从未出现**。`#202028` 只在 **一帧**（574ms）出现，且只出现在评论行内部的位置（1637/2075）；若整页骨架在显示，会在 chip、标题、头像、正文各行都出现色块——一个都没有。
2. **页头零位移**。y=300…1536 的跳变序列在 f2 与 f12 之间**逐项相同**（位置与长度全部相等）：`300/179, 480/12, 495/83, 579..627, 627/203, 833/61, 898/11, 914/236, 1155/25, 1185/351`。
3. **首条评论零位移**。首张卡顶边在占位态与加载态**都是 y=1536**。
4. **981ms 后页面再无任何移动**：f3 与 f12 的跳变序列完全相同。
5. 占位行距实测 **438px = 146dp**（卡 138dp + 8dp 边距），卡间缝隙 `len=24` = **8dp**，与 `item_comment` 的 `layout_marginBottom=8dp` 一致。

公开主页入口（`PublicProfileFragment` → 第二条，回复 1）：首帧（563ms）与稳定帧（2850ms）跳变签名**完全相同**，chip 显示 **「提问」**——该接口不带 `category_name`，走的是 `DiscussionCategoryDefaults.label(slug)` 兜底，**兜底生效**。评论区滚动后回复缩进、操作行、头像均正常。

**残留（明确未消除，勿当成已解决）**

1. **通知入口仍是整页骨架**：只带 `discussion_id`，没有可预填数据，正文高度在数据到达前本质不可知。这正是 web.dev《Optimize CLS》所述无法消除位移的场景。本机账号该入口**无通知可点，未在真机覆盖**。
2. **评论行高只对「两行评论」精确 —— 已量化为一个行盒**：实测首条真实评论是 1 行（卡高 350px），占位按两行构造（卡高 414px），于是第二张卡 **1974 → 1910，差 64px = 21.3dp**，恰为一个行盒。滚动可见真实评论有 1/2/3/4 行多种，**评论行数本就参差，这个差无法预知**。首条评论的顶边不受影响（1536，见上）。
3. **用户主页两个入口不带 `author_avatar`/`category_name`**：分类走 slug 兜底（已实测正确）；头像在详情接口回来前显示默认图标，之后补齐（36dp 固定，无位移）。

### 第二十批（文章 / 分享详情页预填，未提交）

**范围**：用户选定「文章 + 分享详情页预填」。

**调研 —— 先纠正我自己的一个漏检**

按字面量 `articleDetailFragment` grep，只找到 5 个入口，**漏掉了首页那两个用 action id 导航的入口**（`HomeFragment` 走 `R.id.action_home_to_articleDetail` / `action_home_to_shareDetail`），而首页是最高频入口。改为重扫 `nav_graph.xml` 中所有以这三个详情页为目的地的 `action`，才补全清单。

｜页面｜入口｜点击时手上的数据｜后端出处｜能否预填｜
|---|---|---|---|
| 文章 | 文章列表 | `Article`，但**无 `content`** | `articles.ts:22-23` 列表查询是明确列列名的 | ❌ |
| 文章 | 搜索 | 同上（同一接口 + `search` 参数） | 同上 | ❌ |
| 文章 | 首页最新文章 | 仅标题/摘要，**无 `content`** | `aggregated.ts:16-17` 明确列列名 | ❌ |
| 文章 | 收藏 | `BookmarkItem`，**无 `content`** | `bookmarks.ts:16-18` | ❌ |
| 文章 | 阅读历史 | `HistoryItem`，**无 `content`** | `bookmarks.ts:82-84` | ❌ |
| 文章 | 通知 | 仅 `article_id` | — | ❌ 冷路径 |
| 分享 | 分享列表 / 分享管理 / 公开主页 / 我的主页 / 首页最新分享 | 完整 `Share` | `shares.ts:21`、`:259`、`follows.ts:249`、`aggregated.ts:25` 全部 `SELECT *` | ✅ 整页 ×5 |
| 分享 | 通知 | 仅 `share_id` | — | ❌ 冷路径 |

**关键结论：文章页不能做「部分预填」，而文章目前也没有任何入口可预填**

`fragment_article_detail.xml` 里「相关讨论」卡片与点赞/收藏操作栏**都在正文下方、且同在 `scrollView` 内**。若只预填页头、正文留空，这些元素会先出现在屏幕上、再被正文顶下去 —— **比现状更糟**。因此加了 `content` 守卫：**只有 `content` 非空才走预填**，否则退回骨架路径。

**⚠️ 我在这里犯过一个错，并已订正**：我最初按 grep 结果把 `articles.ts:75` 的 `SELECT a.*` 当成了**列表**查询，据此断言「文章列表与搜索接口含正文」。实际读文件后确认 —— **`:22-23` 才是列表查询（明确列列名，不含 `a.content`），`:75` 是 `GET /:id` 详情查询**。生产接口返回也印证了这一点（只有 id/title/summary/category_id/author_id/view_count/like_count/sort_order/created_at/updated_at/author_name/author_avatar/category_name，**没有 content**）。

所以修正后的结论是：**文章侧没有任何列表接口返回正文**（列表 `articles.ts:22`、首页 `aggregated.ts:16`、收藏 `bookmarks.ts:16`、历史 `bookmarks.ts:82` 全都没有取这一列），**预填对文章全部入口都会被守卫拒绝、走骨架** —— 与改造前一致，不会更糟，但也**没有带来任何改善**。

分享侧经逐行核实成立：`shares.ts:14`（公开列表）、`follows.ts:249`（用户主页列表）、`shares.ts:259`（管理列表）、`aggregated.ts:25`（首页最新分享）**都是列表查询且都用 `SELECT *`**，`Share` 模型自带详情页所需全部字段，可整页预填。

**改动**（12 个文件，+约 200/−90）

1. 新增 `util/NavPrefill.kt`：把承载机制从「讨论页专用」提升为通用（Gson 序列化进 Bundle 的理由同第十九批）。`DiscussionNavArgs` 改为两行委派 —— **键名与序列化方式都没变，讨论页行为等价**。
2. `ArticleDetailFragment`：抽出 `revealContent()`，热/冷两条路径收敛；预填经 `setPrefilledArticle` 走**同一条**观察者渲染路径。这一页没有需要等待 ViewHolder 的列表，渲染是同步的，因此内容与可见性都在首次布局之前就绪，不会出现空白帧。
3. `ShareDetailFragment`：把原本内联在 `loadShare` 里的渲染抽成 `renderShare(share)`，预填与详情接口**共用同一份**，避免「预填一套 / 接口一套」。
4. 6 个入口改传完整对象：文章列表、搜索、分享列表、分享管理、公开主页、我的主页、首页最新分享。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors**、2356 warnings（无新增）。

**承载层实证（不需要真机，用真实编译产物 + 真实 `gson-2.10.1.jar` 跑往返）**

| 项 | 结果 |
|---|---|
| `Article` 经新的通用承载器往返 | `ARTICLE_EQUAL=true`；80 段中文正文 JSON **5780 字节**（Binder 上限 524288） |
| `Share` 往返 | `SHARE_EQUAL=true`；JSON 299 字节 |
| 键不存在（未预填的入口） | `MISSING_KEY_DECODES_TO_NULL=true` → 退回骨架路径，不崩 |
| 载荷损坏 | `GARBAGE_DECODES_TO_NULL=true` → 退回骨架路径，不崩 |

后两项是这次新加的兜底：预填是「有则更好、没有就退回原行为」，任何异常都不得影响可用性。

**真机验证（本轮已做，结论与预期不同）**

工具换成 `uiautomator dump` 读视图树：既能确定当前在哪一页，也能拿到控件精确 bounds —— 解决了上一轮「帧无法归因」与「坐标靠猜」两个问题。底部导航容器实际是 `[0,2157][1176,2301]`（中心 y=2229），此前按截图估的 2262 偏下，那正是上一轮点击时灵时不灵的原因。

| 项 | 方法 | 结果 |
|---|---|---|
| 讨论页回归（承载重构后） | 连拍 12 帧比对颜色跳变位置 | 与重构前**逐位相同**：k2(536ms) == k12(5372ms)，`300/179 480/12 495/83 … 1155/25 1185/351`；首条评论卡顶边 1536/1536；骨架块仅在评论行内部 |
| `content` 守卫的回退 | 后端尚未返回正文时，加 8 秒延迟连拍 | **显示骨架** → 守卫拒绝预填并走骨架，没有出现「页头真实、操作栏被顶下去」的坏情况 |
| **文章热路径**（后端补列并部署后） | 加 8 秒延迟，从首页文章卡进入 | `ah3@854ms` 已是**完整真实文章**（公告/标题/管理员/30 阅读/正文/相关讨论/操作栏）。8 秒延迟意味着网络响应不可能已返回，内容只能来自预填 ✓ |
| **分享热路径** | 加 8 秒延迟，从分享列表进入 | `art3@914ms` 已是**完整真实分享**（描述、作者、8分钟前、0 浏览、下载信息卡、密码、点赞/下载按钮）✓ |
| 文章列表 / 搜索两个入口 | — | **未单独跑通**：设备的合成点击两次把「查看全部」落到了分享入口。二者与已验证的首页入口走**完全相同**的预填与守卫代码，且两个接口现在都返回 `content`（已从外部 API 确认），但没有它们的独立实测帧 |

**一次差点写错的结论**：12 帧连拍下，文章列表入口的 `w2@561ms` 已是完整文章且无骨架，我一度以为热路径生效。随后用首页入口做对照 —— 它没有任何预填、必然是冷路径，却在 `569ms` 也已完整渲染。**这个网络下 ~460ms 就能整页渲染完，冷路径同样抓不到骨架窗口**，所以那条「证据」根本不成立。改成临时加 8 秒延迟后才得到真实结论：文章预填没有生效，因为接口没有正文。

验证用的 8 秒延迟已移除并重新构建；它不是交付内容的一部分。

**后端改动（让文章预填真正有数据可填）**

按 grep 结果直接读文件核实后，在四条**列表**查询里补上 `a.content`：

| 文件 | 位置 | 影响入口 |
|---|---|---|
| `articles.ts` | 列表查询（`edgeCache(120)`） | 文章列表、搜索 |
| `aggregated.ts` | 首页文章查询（`edgeCache(300)`） | 首页最新文章 |
| `bookmarks.ts` | 收藏列表 | 收藏 |
| `bookmarks.ts` | 阅读历史列表 | 阅读历史 |

四处都加了注释说明「这一列是给客户端预填用的，删掉不会崩但会丢掉优化」，避免以后被当成冗余列清理掉。

Android 侧配套：`HomeFragment`、`BookmarkListFragment`、`ReadingHistoryFragment` 三个入口改传整条对象；`BookmarkItem`/`HistoryItem` 补 `content` 字段，并各加一个 `toPrefillArticle()`（这两条查询不返回 `author_id`/`category_id`/`author_avatar`，用 0/null 占位 —— 守卫只要求正文非空，其余字段由详情接口在同一行内补齐，不引起位移）。

**SQL 已用真实 schema 实证**（`node:sqlite` + 仓库里的 `schema.sql`，建表后插入一行真实数据再跑改过的查询）：

```
OK   articles list   (articles.ts)      rows=1  content="BODY-TEXT"
OK   home articles   (aggregated.ts)    rows=1  content="BODY-TEXT"
OK   bookmarks       (bookmarks.ts)     rows=1  content="BODY-TEXT"
OK   reading history (bookmarks.ts)     rows=1  content="BODY-TEXT"
ALL_SQL_VALID
```

**部署过程中的一次生产事故（我造成的，已修复）**

`deploy_now.py` 上传的是**整棵后端源码树**（25 个文件），不只是本次改动。执行后生产立刻 502，`pm2` 状态 `errored`、连续重启 23 次。日志：

```
Error: Cannot find module '../clientIp'
  at /opt/sk-note-backend/src/middleware/rateLimit.ts:3:29
```

**根因**：脚本的上传清单里有 `src/middleware/rateLimit.ts`，却**没有它新引入的依赖 `src/clientIp.ts`**。新版 `rateLimit.ts` 覆盖了线上旧版，而那个依赖文件从未被上传过 → 模块解析失败、进程起不来。核对后：本机 `src/` 下共 **22 个 `.ts`**，清单漏了 **2 个** —— `src/clientIp.ts`、`src/sqlUtils.ts`。

**这正是我事先提示过的「整树上传」风险，只是以我没预料到的方式炸了**：我一行都没碰 `rateLimit.ts`，但整树上传把它一起带上去了。

**修复过程**：上传缺失的两个文件 → `pm2 restart sk-note-api` → 服务恢复（`online`，内存 104MB，四个接口全部 HTTP 200，`/api/articles` 与 `/api/home` 均已带 `content`）。

**顺带修掉脚本本身的三处缺陷，避免复发**：

1. **加预检**（最重要）：部署前比对 `src/**/*.ts` 与实际清单，缺任何一个就**在建立连接之前**中止。这类「漏传依赖导致线上起不来」的事故以后不可能再发生 —— 宁可拒绝部署，也不要在上传到一半之后才发现。
2. `local_base` 改为由脚本自身位置推导（`os.path.dirname(os.path.abspath(__file__))`），不再写死 `C:\Users\Administrator\Desktop\laster\sk_note\backend` —— 原值在其它机器/用户下根本不存在。
3. `sys.stdout.reconfigure(encoding='utf-8', errors='replace')`：原脚本在打印 pm2 输出时会因控制台 GBK 编不出 `✓` 而抛 `UnicodeEncodeError`，导致**部署成功了却仍以异常退出**（本次就遇到了，一度让我无法从脚本输出判断 pm2 是否重启成功）。

**残留**

1. **文章列表 / 搜索两个入口未单独实测**：二者与已验证的首页入口走完全相同的预填与守卫路径，接口也已确认返回 `content`，但没有独立实测帧。
2. 通知入口（文章 / 分享的冷路径）保持骨架不变。
3. 部署脚本的上传清单仍需人工维护 —— 预检只能拦住「本地有、清单没有」，拦不住反向（清单里有而线上多余，无害）。
4. 按既定流程未提交。

### 第二十一批（片段详情页预填，已修复）

**调研**：片段页与文章/分享页同构，而且更省事 —— `Snippet` 模型**本身就有 `code` 字段**（无需改模型），`SnippetDetailFragment` 的标题/描述/代码/语言/分类/标签/作者/计数**全部由一个 `snippet` 对象渲染**（无 ViewModel，渲染内联在 `loadSnippet` 里）。

**后端只有一条列表查询**：`snippets.ts:14` 的 `GET /`（`edgeCache(120)`），而且**片段管理页用的就是它** —— `SnippetManageViewModel` 调的是 `getSnippets(page = 1, limit = 100)`。所以加一列就同时覆盖「片段列表」与「片段管理」两个入口；两个主页入口走 `follows.ts:221` 的 `SELECT *`，本来就有 code。

**先量体积再决定加列**（没有拍脑袋）：改前列表 **6329 字节 / 20 条**，逐条取详情统计出 `code` 平均约 150 字符；加列后实测 **10792 字节**（+4.5KB）。`code` 的写入上限是 50000 字符（`snippets.ts:112`），理论最坏值偏大 —— 这个取舍已写进代码注释，不是隐含假设。

**改动**：后端 1 行（列表查询加 `s.code`）；`SnippetDetailFragment` 抽出 `renderSnippet()` 与 `revealContent()`，热/冷两条路径收敛；4 个入口接入（片段列表、片段管理、公开主页、我的主页）。

**真机验证**（LIO-AN00；临时给 `loadSnippet` 加 8 秒延迟让预填态可见）：`sn3@1034ms` 已是**完整真实内容** —— 工具栏标题、JAVA/通用 chips、标题、描述、admin / 03-04 / 24 浏览、**语法高亮后的代码块**（`showMessage("Hello World!");`）、以及**动态生成的标签 chips**（#toast / #消息 / #基础）。8 秒延迟意味着网络响应不可能已返回，内容只能来自预填。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors**、2356 warnings（无新增）。改后的 SQL 已用真实 `schema.sql` 实证（`node:sqlite`，插入真实行后跑查询，返回 `code="CODE-BODY"`）。

**部署**：本次部署**干净通过**，`Deploy complete!` 正常打印（上一批修的 UTF-8 生效）。生产 `/api/snippets` 已带 code，四个接口全部 HTTP 200。

**残留**

1. 通知入口（片段的冷路径）保持骨架不变。
2. **四个详情页至此全部收敛**（讨论 / 文章 / 分享 / 片段）；剩下的是 5 个列表页与首页，仍未做。
3. 部署脚本的上传清单仍是人工维护 —— 预检**只在本机磁盘上**（该文件被 `.gitignore` 忽略，因为含明文密码），换机器就没有这层保护。

### 第二十二批（讨论列表：分类 chip 行造成的 48dp 顶推，已修复）

**起因**：用户指出「有差异的原因应该是讨论的分类加载后填充导致」。这个判断比我先前的方向准得多 —— 我当时在比骨架卡与真实卡的高度，而真正的大头在别处。

**实测**（`uiautomator` 读 bounds；480dpi，1dp = 3px）。分类行是包住 `ChipGroup` 的 `HorizontalScrollView`：

| 页面 | 分类未到 | 分类已加载 | 结论 |
|---|---|---|---|
| **讨论区** | 12 dp ／ `[0,299][1176,335]` | 60 dp ／ `[0,299][1176,479]` | **长高 48dp，把下面整个列表（含骨架）向下推** |
| 片段列表 | 48 dp | 48 dp | 恒定 |
| 分享列表 | 56 dp | 56 dp | 恒定 |

**根因**：`fragment_discussion_list.xml` 的 `ChipGroup` 在 XML 里是**空的** —— 连「全部」都是 `renderCategoryChips` 在网络回来后动态添加的。所以分类到达前这一行只剩 HSV 的 12dp padding，到达后长到 60dp。片段与分享两页的 `ChipGroup` 里 **XML 已静态放了一个「全部」chip**，行高恒定，因此没有这个问题。

**修复**：在 `DiscussionListViewModel.loadCategories()` 开头先用本地兜底表 `DiscussionCategoryDefaults.categories` 把 chip 行填上，再去请求网络。这不是性能优化，是修布局跳变；兜底表与该页原有的失败分支用的是**同一份数据**，先填后填内容一致。`observeData()` 在 `loadCategories()` 之前注册观察者，因此赋值会同步触发渲染，chips 在首次布局前就已就位。

**修复后实测**（保留 10 秒延迟冻结「分类未到」态）：两态 `bounds` **完全相同** `[0,299][1176,479]`，均为 60dp，且 chips 从第一帧就在。撤掉插桩后复测同样成立。

**顺带纠正我自己的一个错误方向 —— 本轮原本要做的「列表页改成占位即真实 item」被实测证明达不到目的，因此没有做**：

- 骨架卡实测 **148 dp**，而真实讨论卡随内容在 **130.7 / 151.3 / 172.0 dp** 之间变化（标题 1–2 行 × 预览 1–2 行）；
- 也就是说骨架卡本就落在真实区间的**中间**，离最常见的 (1,2) 只差 **3.3 dp** —— 与详情页当初「页头差 76dp、评论每行差 57dp」完全不是一个量级；
- 残余偏差来自真实卡片**自身随内容变化**，任何固定占位都消不掉。把它换成 RV 里的占位 item 只能对齐其中一个配置，工作量翻几倍而位移仍在。

真正在制造跳变的是上面那条 chip 行 —— **48dp，是卡片模态差的 15 倍**。

**残留**

1. 讨论卡片的行数差异（130.7–172.0 dp）仍在，但量级只有几 dp。若要彻底消除，需给 `tv_title`/`tv_preview` 加 `minLines="2"` 让卡片定高，代价是所有卡片变高等高（短卡多出约 41dp 空白）—— 属产品决策，未擅自改。
2. 若服务端返回的分类集合与本地兜底表不同，chips 会在网络回来后变化（文字变、行高不变，不产生位移）。本地兜底表本就是该页原有的失败兜底，两者当前一致（实测冻结态与加载态可见 chip 文字相同）。
3. 首页与文章列表未发现同类问题：文章列表没有 chip 行；首页的分类是骨架里的占位卡。

### 第二十三批（提示条：补齐反色角色，明暗两套都不再落回 M3 基线，已修复）

**问题**：用户反馈「提示条 UI 不好，而且还没做夜间效果」。

**根因（静态链路已完整展开）**：`Theme.SkNote.Base` 只定义了 **12 个颜色角色**（primary / secondary / tertiary 三族各 4 个），**一个反色角色都没有**。而从 `material-1.11.0.aar` 的 macro 展开可以看到，M3 的 Snackbar 恰好全靠这三个：

| 提示条部件 | 样式来源 | 引用的 attr |
|---|---|---|
| 容器底 | `@macro/m3_comp_snackbar_container_color` | `?attr/colorSurfaceInverse` |
| 消息文字 | `@macro/m3_comp_snackbar_supporting_text_color` | `?attr/colorOnSurfaceInverse` |
| 动作按钮 | `ThemeOverlay.Material3.Button.TextButton.Snackbar` 的 `colorOnContainer` | `?attr/colorPrimaryInverse` |

未定义 → 回落到 M3 基线：

| 部件 | 改前（基线） | 改后（项目） |
|---|---|---|
| 容器底 | 浅 `#322F35`｜夜 `#E6E0E9` | 浅 `#263238`｜夜 `#ECEFF1` |
| 消息文字 | 浅 `#F5EFF7`｜夜 `#322F35` | 浅 `#ECEFF1`｜夜 `#263238` |
| **动作按钮** | 浅 **`#D0BCFF` 淡紫**｜夜 **`#6750A4` 深紫** | 浅 `#90CAF9`｜夜 `#1976D2` |

**动作按钮在明暗两种模式下都是 M3 基线的紫色** —— 与第十三批的 tertiary 属同一类问题：项目自定调色板后，未声明的角色仍走基线，于是出现「蓝色系 App 里跳出紫色按钮」。这就是「提示条 UI 不好」的主要来源。

**改动**：`values/colors.xml` 与 `values-night/colors.xml` 各加 3 色，`values/themes.xml` 与 `values-night/themes.xml` 的 `.Base` 各加 3 个 attr。取值沿用项目自己的规律（浅色取 900/50 调 Blue Grey 作底、动作文字用夜间主色；夜间整体倒过来），并符合 M3 对反色角色的语义。

**踩到一个坑**：attr 名是「角色名 + Inverse」（`colorSurfaceInverse`），不是「Inverse + 角色名」。我第一次按记忆写成 `colorInverseSurface`，资源链接直接失败。以 `material-1.11.0.aar` 的 `public.txt` / `R.txt` 为准改正。

**真机验证**（截图取像素，夜间模式）：

- 提示条容器实测 **`#ECEFF1`** —— 正是项目的夜间反色底，而非基线的 `#E6E0E9`（两者可分辨，因此这是一次判定性测量）；
- 文字为深色 `#263238`，在浅底上清晰可读。

门禁：`:app:assembleDebug` 通过、`:app:lintDebug` **0 errors**、2356 warnings（无新增）。

**同批修掉的位置问题**

**观测**：提示条被压在应用自己的底部导航栏上。顶层四页的内容区延伸到导航栏之下（正因如此这些页面才需要 `bottom_nav_content_inset` 让位内边距），而提示条默认贴在对齐底部的位置 —— 实测其文本下沿正好落在内容区下沿 `y=2301`，而底部导航栏占 `y=2157..2301`，于是整条压在上面。顶层页的提示条只有 **6 处**（首页 1、讨论区 2、片段列表 2、我的 1）加 MainActivity 3 处，其余约 170 处都在压栈页上。

**修法**：新增 `util/AppSnackbar`，用 Material 自己的 `setAnchorView(bottom_nav)` 把提示条抬到导航栏之上 —— 它内部按锚点高度维护 `bottomMargin` 并在锚点布局变化时自动更新；压栈页导航栏为 `GONE`（高度按 0 计）等价于不偏移。然后把全部 **179 处 `Snackbar.make` 调用**（47 个文件）机械替换为 `AppSnackbar.make`，集中到一处，避免以后新增调用点又退回原样。

**两条走不通的路（写进注释里，免得重蹈）**

1. **自己设 `bottomMargin`**：`snackbar.view.layoutParams` 在 `show()` 之前是基类 `ViewGroup.LayoutParams`，**不是** `MarginLayoutParams`，`is ViewGroup.MarginLayoutParams` 判断为假，设 margin 的代码根本不执行（实测提示条位置毫无变化）。
2. **自己读系统 inset**：`ViewCompat.getRootWindowInsets(...)` 返回的底部 inset 是 **0** —— Activity 根有 `fitsSystemWindows="true"`，inset 已被它消费，子视图读到的是 0，因此算不出该偏移多少。

这两点是用「把中间量打进提示条文字再从屏幕读回来」的办法测出来的：`inset=0 nav=-2 act=true lp=LayoutParams`。

**踩到的第三个坑**：`setAnchorView` 必须判 `isShown`。底部导航在压栈页被置为 `GONE`，但 `getHeight()` 仍保留上次布局的旧值，Material 会拿它当锚点高度 —— 实测提示条被无故抬高 165px（`[72,2163][1104,2301]` → `[72,1995][1104,2136]`）。加上 `isShown` 判断后回到 `[72,2235][1104,2301]`，下沿正好是内容区下沿，**与改动前一致、无回归**。

**验证**（LIO-AN00，夜间）：

- 片段详情「复制」→ 提示条 `[72,2235][1104,2301]`，下沿 = 内容区下沿，无偏移 ✓（压栈页不回归）
- 参考详情「复制」→ 同上 ✓（冒烟：确认 47 个文件的替换没有破坏调用）
- 顶层页（导航栏可见时抬升）**只做了机制确认，未真机实测** —— 顶层页触发提示条需要「退出登录」或一次写操作，我没有为了观察去写生产数据或登出（登出后没有密码可登回）。抬升量由 `setAnchorView` 按导航栏实际高度算出，与导航栏自身几何自洽。

门禁：`:app:assembleDebug` 通过、`:app:lintDebug` **0 errors**、2356 warnings（无新增）。中途一次 `Unresolved reference: R`（新文件漏 import）与一次 `uiautomator` 的 `could not get idle state`（界面在动画中导致 dump 为空）都已处理。

**补充（用户进一步明确「提示条 UI 不好」= 夜间太刺眼 + 盖住底部操作栏，两项已修并实测）**

**一、夜间不再是一条亮白条。** M3 规范性要求提示条取 `inverseSurface`，深色主题里就是浅底（本项目未定义时落回基线 `#E6E0E9`）。用户明确不要这个效果，于是**刻意偏离 M3 的「反色」语义**：两个模式都用深色底，颜色也改为按用途命名 `snackbar_container` / `snackbar_on_container` / `snackbar_action`，并把这个决定写进了颜色与主题的注释。

| | 改前（夜间） | 改后（夜间） |
|---|---|---|
| 容器 | `#ECEFF1` 亮白条 | **`#2B2930`**（M3 surfaceContainerHigh 深色值，比页面 `#141218`、卡片 `#1E1E2E` 都亮一档） |
| 文字 | 深色 | `#ECEFF1` 浅色 |

实测（截图取像素，y=2080..2200 区间）：`#2B2930` 出现 1783 次，`#ECEFF1` 仅 39 次 —— **后者是文字像素**（浅字画在深底上），不是底色。

**二、不再盖住底部操作栏。** 实测提示条与详情页的「点赞」栏几乎完全重叠（点赞文字 `[48,2256][1128,2301]` vs 提示条 `[72,2235][1104,2301]`），提示条绘制在上层，那几秒里按钮不可见。

修法：给四个详情页的底部操作栏打 `android:tag="snackbar_anchor"` —— 文章页 `bottomBar`、片段页与分享页的点赞栏、讨论页的三套互斥回复栏（`layoutReply` / `layoutReplyGuest` / `layoutReplyClosed`）。`AppSnackbar` 递归查找**当前可见且带此 tag** 的视图作锚点；不可见视图的高度是上次布局的残留值（底部导航栏上刚踩过这个坑），所以必须判 `isShown`。找不到底部栏时退回可见的应用底部导航栏。

实测：提示条文本由 `[72,2235][1104,2301]` 上移到 **`[72,2067][1104,2208]`** —— 下沿 2208 在点赞按钮（2256）之上，按钮全程可见 ✓ 截图可见提示条完整落在「点赞」栏上方。

**过程中的自造错误**：KDoc 里写了 `` `values*/colors.xml` ``，其中 `*/` 提前终止了块注释，导致一连串 `Expecting a top level declaration`。已改正 —— 块注释里不能出现 `*/`。

**真机验证被环境中断过一次**：手机熄屏且锁屏（`mScreenState=OFF`、`isKeyguardShowing=true`），我后续 8 轮循环的点击全落在锁屏上；`KEYCODE_WAKEUP` 无法恢复焦点，需用户解锁。解锁后复测通过。另外屏幕上出现过「已连接 USB 调试」提示，**它写着「点击可停用」—— 不能点，一点就断 adb**，只能等它自行消失。

### 第二十四批（个人资料页统计行错位，已修复）

**问题**：用户反馈「个人资料页面也有问题」。截图一眼就能看出统计行是歪的 —— 「0 关注 / 0 粉丝」比「0 讨论 / 20 片段」低一截。

**实测坐实**（`uiautomator` 读父容器 bounds，480dpi）：

| 统计格 | 可点击 | 父容器高度 | 文字 top |
|---|---|---|---|
| 关注 | ✓ | **144px = 48dp** | 666 |
| 粉丝 | ✓ | **144px = 48dp** | 666 |
| 讨论 | ✗ | **68px = 22.7dp** | **628** |
| 片段 | ✗ | **68px = 22.7dp** | **628** |

**根因**：布局里只有前两格（可点击的）带 `android:minHeight="48dp"`（给点击热区），后两格没有；而这一行是横向 `LinearLayout` 且**未设 `gravity`** → 矮的两个贴顶对齐，比高的两个高 38px。四格都有 `gravity="center"`，但那只是让**格内**内容居中，管不了格子自身在行内的位置。

**公开主页 `fragment_public_profile.xml` 是同一套结构、同一个问题**（同样只有前两格有 `minHeight`），一并修了。

**改动**：给两个布局里「讨论」「片段」四格各补 `android:minHeight="48dp"`。行高本来就由前两格决定（48dp），补上后行高不变，**不产生位移**。

**真机验证**：修复后四格父容器高度均为 **144px = 48dp**，截图确认四格落在同一基线上。

门禁：`:app:assembleDebug` 通过、`:app:lintDebug` **0 errors**、2356 warnings（无新增）。

**同批发现并修掉的相邻问题：我的页那行数字混了语义**

`AdminFragment`（我的页）原来的绑定：

- `tvStatArticles` ← `stats.totalArticles` → 接口的 `total_articles` = **全站**已发布文章数
- `tvStatDiscussions` ← `stats.totalDiscussions` → `total_discussions` = **全站**讨论数
- `tvStatNotifications` ← `stats.unreadNotifications` → `unread_notifications` = **该用户自己的**未读数

接口另有 `my_*` 三个字段，我的页一个都没用。于是那一行把「全站 3 条讨论」和「我的 0 条未读」并排显示，标签只写「文章 / 讨论 / 通知」，读起来就是「我发了 3 条讨论」—— 而管理员实际 **0 条**。

**改成显示自己的之后，又暴露了后端一个名不副实的字段**：`my_discussions` 的 SQL 是

```sql
SELECT COUNT(DISTINCT d.id) ... WHERE d.author_id = ? OR c.author_id = ?
```

即「我发的**或我评论过的**」，而 `my_snippets` / `my_articles` 都是纯 `author_id = ?` —— 只有讨论这一个字段是特例。管理员在那 3 条讨论里都留过言，所以这个字段给 3；个人资料页按纯作者口径给 0。**两个数都没算错，是口径不同**。

**修复**：把该 SQL 改成 `SELECT COUNT(*) as count FROM discussions WHERE author_id = ?`，与另外两个 `my_*` 及字段名统一；App 侧 `AdminFragment` 改读 `stats.myArticles` / `stats.myDiscussions`，本地缓存键也换成 `my_` 前缀（旧键缓存的是全站数字，换键后自然失效，升级后第一次启动不会闪出全站数）。

**SQL 用真实 schema 复现并验证**（`node:sqlite`，造「用户1 在用户2的 3 条讨论里都评论过、自己发 0 条」这一场景）：

```
旧 SQL（参与过）-> 3      ← 正是页面上显示的 3
新 SQL（我发的）-> 0      ← 正确
REPRODUCES_THE_BUG_AND_FIX
```

**消费方核查**：`my_discussions` 在本仓库内只有那一行响应引用；`myDiscussions`/`mySnippets`/`myArticles` 在 App 里改我之前一处都没用；仓库只有 `android/` 与 `backend/`，后端也不提供任何网页（无 `text/html` / `serveStatic` / 静态资源）。因此本仓库内它是死字段 —— 但**无法排除仓外客户端**在用它，故已向用户确认后才改。

**真机复测（补做，通过）**：部署成功（`online`、`Deploy complete!`，四个接口 200）。首次复测时手机息屏锁屏（`mScreenState=OFF`、`isWakefulness=Asleep`、焦点 `NotificationShade`），用户解锁后复测：

| 我的页统计格 | 改动前 | 改动后 |
|---|---|---|
| 文章 | 2 | 2 |
| **讨论** | **3**（全站数） | **0**（我自己 0 条） ✓ |
| 通知 | 0 | 0 |

三格的 bounds 与改动前逐像素相同（`[176,441][207,509]` / `[528,441][559,509]` / `[880,441][911,509]`），**无布局回归**；「0 讨论」与个人资料页的「0 讨论」现已一致。

---

### 第二十五批（编辑资料页布局，已改造）

**问题**：用户反馈「优化编辑资料页面的布局」。先按老规矩实测，不靠读 XML 猜。

**改动前实测**（`uiautomator`，1176×2400 / 480dpi，1dp = 3px）：

| 项 | 实测 |
|---|---|
| 页头卡片 | `x[48,1128] y[339,1239]` = **900px = 300dp**，占可视区（670dp）的 **45%** |
| 同款数据在个人资料页的页头 | **288px = 96dp**（横排 72dp 头像 + 昵称 + `@账号`） |
| 首屏能看到哪 | 只到第 2 个字段（「个人简介」标签在 2235，输入框已在屏幕外） |
| 「保存资料」按钮 | 在首屏之外约 700dp 处 |
| 保存按钮的状态文字 | 在**页头**里（`资料已同步` / `资料已修改，记得保存`），与按钮相距一屏 |
| `progressBar` | 在内容最底部且 `visibility="gone"` → 加载时在屏幕外，页面上只看到字段变灰 |

**同时发现本页是全 App 的「异类」**（这类一致性问题是靠全量 grep 出来的，不是猜的）：

| 写法 | 本页 | 全 App 其余 |
|---|---|---|
| `app:strokeColor="?attr/colorOutline"` 描边卡 | **2 处（仅此页）** | 0 处 |
| 次要文字用 `android:alpha="0.72"` | **3 处（仅此页）** | 0 处，统一 `?attr/colorOnSurfaceVariant` |
| 小节标题 | 16sp 加粗 + 一整段 13sp 说明 | **15 个页面**统一 13sp 加粗 + `colorOnSurfaceVariant`，无说明段 |
| 每个字段上方的加粗标签 | 4 个（昵称/账号/个人简介/头像 URL） | 与浮标重复占 4×27dp = **108dp** |
| 昵称框的浮标 | 用「显示给其他用户的名字，支持中文」整句当标签，横跨整个输入框 | — |
| 「头像 URL」的 `helperText` | 折成 **2 行**（91px = 30dp），比别的字段高一截 | — |
| `ScrollView` 高度 | `match_parent` | 同页的 `fragment_user_profile.xml` 用 `0dp` + `weight=1` |
| 工具栏 | 光秃秃一个 `MaterialToolbar`，滚动无抬升 | 「发起讨论」「讨论详情」用 `AppBarLayout` + `liftOnScroll` |

**改动**：

1. **页头改成与「个人资料」逐项同款**（20dp 水平、上 8dp / 下 16dp、72dp 圆形头像 + `colorSurfaceVariant` 底 + 图内缩 4dp、22sp 加粗昵称、14sp `@账号`、12sp 邮箱、头像与文字间 16dp）。理由：本页编辑的就是那个页面的展示内容，两边同款才叫所见即所得。昵称与账号**随输入实时预览**（账号那一行是新增的 `tvHandle`），头像右下加相机角标提示可点，`更换头像` 收成行尾的 TextButton。`renderAvatar` 里按「有无链接」改 padding 的分支删掉，内缩量交给布局统一决定 —— 否则同一张头像在本页与个人资料页会差一圈。
2. **四个字段去掉重复的加粗标签**，改为 M3 标准解剖：`android:hint` 当浮标标签、`app:helperText` 当辅助说明。
3. **卡片 / 小节标题 / 辅助文字一律回到约定**：20dp 圆角、`strokeWidth="0dp"`；13sp 加粗 `colorOnSurfaceVariant`；次要文字不再用 alpha。两段被删掉的说明文字里，密码那段的意思保留成了密码行的常态文案「留空表示不修改密码」。
4. **工具栏加 `AppBarLayout` + `liftOnScroll`**；背景色写在 `AppBarLayout` 上（写在工具条上会把抬升效果盖住）。
5. **加载条移到工具栏正下方**（`LinearProgressIndicator`，2dp，`pinned` 在滚动区之外），并把 `GONE` 改成 `INVISIBLE` 预留这 2dp —— 否则数据到达的瞬间整页会下移 2dp。
6. **状态行拆成两处**：资料一行放「保存资料」上方，密码一行（新增 `tvPasswordStatus`）放「修改密码」上方。原先只有一行且写在页头，于是「请完整填写并确认新密码」这种密码提示会出现在一屏之外的资料按钮旁边。
7. 「头像链接」的 `helperText` 缩短到一行；昵称框的浮标改回「昵称」。

**真机验证**：

- **页头三条文字的 y 坐标与个人资料页完全相同**：昵称 315、`@账号` 412、第三行 481（个人资料页那三行是 315 / 412 / 481）。页头高度 900px → 288px。
- **首屏内容**：4 个字段 + 状态行 + 保存按钮 + 「账号安全」标题全部可见（原先只到第 2 个字段）。
- **加载态**（临时给 `loadProfile` 加 8s `delay` 后实测）：`resource-id=…/progressBar`、`class=android.widget.ProgressBar`、`bounds="[0,291][1176,297]"` —— 就在工具栏下方，截图像素扫描 y=294 一行可见 `#90CAF9` 的滚动色块；同时字段全空、状态行是「正在同步资料...」。
- **加载态与完成态的控件坐标逐像素相同**（更换头像 357、基础资料 585、昵称框 738、helper 917、账号 1018、个人简介 1298）→ 2dp 预留生效，**没有跳变**。
- **状态行随输入切换**（真机打字实测，读的是 `enabled` 属性）：`资料已同步` + 保存按钮 `enabled=false` → 输入一个字符后变 `资料已修改，记得保存` + `enabled=true`；页头的昵称预览同步变成 `管理员A`、计数 3/20 → 4/20。密码行：`留空表示不修改密码` → 只填新密码时变 `请完整填写并确认新密码`。
- **明暗两套坐标一致**（中途切到浅色模式复测，各控件 bounds 相同），验证完已把主题设置改回「跟随系统」。
- 离开页面时「放弃当前修改？」对话框仍正常（未保存时拦截返回）。

**最后一次定位**：最底部「修改密码」按钮在滚到底时 `y[2136,2280]`，底边正好落在系统手势区起点（`GestureNavBottom` 窗口 `[0,2280][1176,2400]`）之上 —— 整颗按钮都在手势区外，可正常点击。App 自己的底部操作栏本来就伸到手势区里（到 2301），本页比它更保守，故不再额外加底部留白。

**未在真机触发的一项**：密码行的第三种状态「密码已准备提交」没截到 —— 用 `TAB` 想把焦点移到「确认新密码」时，焦点落到了该字段的密码可见性切换图标上。那一行只是同一个 `when` 里的字符串，且按钮启用条件 `isPasswordReadyToSubmit()` 本次未改动。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors**、2353 warnings（上一批 2356，减少 3 —— 少的正是上面「异类」写法带走的告警）。

**顺带修正一处旧记录**：本次用 `git cat-file` 复查过行尾 —— 本仓库这两个文件在 blob 里是 **CRLF**（`bareLF=0`），不是此前文档里写的「仓库存 LF」。提交前后都是 CRLF，没有行尾翻转；`git diff --shortstat` 的真实增量是 378+/332-。

---

### 第二十六批（发起讨论页：分类顶推 + 提示条锚点 + 引导卡重复标题，已修复）

**问题**：用户点名「发起讨论」。先实测，不靠读 XML 猜。

**实测（1176×2400 / 480dpi，1dp = 3px）**

| 项 | 实测 |
|---|---|
| 工具栏标题 `发起讨论` | y 151..239 |
| 卡内标题 `发起讨论` | y 357..430 —— **与工具栏是同一个字符串**（`CreateDiscussionFragment.kt` 原先两行写死同一个值；`ArticleEditorFragment.kt:90-91` 是同一套写法） |
| 引导卡整体 | y 315..589 = 274px（91dp）+ 16dp 外边距 = **107dp** |
| 正文下方说明段 | y 2115..2206 = 91px（30dp）—— 与卡内「尽量写清背景、现象和期望结果…」说的是同一件事 |
| `选择分类` | 12sp、不加粗（其余 15 个页面统一 13sp 加粗 + 8dp 缩进） |
| 分类 chip 高 | 96px = **32dp**（`综合 [60,978][277,1074]`） |
| 底部发布栏 | 顶边约 2210（`发布` 按钮 2244..2301+，被无障碍框裁到 2301） |

**改动**

1. **分类 chip 行的 32dp 顶推**。`ChipGroup` 在 XML 里是空的，分类要等网络回来才长出 chip，而它下面紧跟着格式工具条 + 正文输入框 —— 于是整块被推 32dp。这与第二十二批讨论列表页的 48dp 顶推是**同一成因**，那页当时已用「先填本地兜底分类」修掉，本页漏了。现在 `CreateDiscussionViewModel.loadCategories()` 同样先填 `DiscussionCategoryDefaults`（该表与服务器返回的分类完全一致：综合/提问/反馈/Bug/功能建议，先填后填内容相同）。
2. **提示条盖住底部操作栏**。`AppSnackbar` 靠 `android:tag="snackbar_anchor"` 找页面自己的底部操作栏，全仓库此前只有 5 个 view 带这个标记（第二十三批的详情页）。这次把布局过了一遍，逐个确认「按钮所在容器是加权滚动容器的**兄弟节点**」（而不是滚动内容），又找出 5 处：发起讨论（发布栏）、发起分享 `layoutSubmitBar`、发起片段 `layoutSubmitBar`、事件编辑器（取消/保存栏）、文章编辑器（保存面板），各补一个属性。
3. **引导卡只留说明**（用户确认按 A 方案）。删掉卡内那个与工具栏重复的 18sp 加粗标题，卡片成为纯「模式说明条」；工具栏标题成为「当前是哪种模式」的唯一来源，并给原先只有卡片才显示的**「发起文章讨论」补上了标题**。同时删掉正文下方那段与卡内说明重复的提示。
4. **`选择分类` 改 13sp 加粗 + 8dp 缩进**，与全 App 的小节标题一致。

**真机验证**

*顶推（决定性测法：临时给网络加 9s `delay`，兜底在 delay 之前就已填入）*

网络仍在等待时 chip 行**已经就位**，与真实分类到达之后逐项对比 20 个文本控件的 bounds：

```
compared 20 labels; differences = 0
```

改前这一行高度为 0，下方所有内容会低 32dp；现在前后逐像素相同，**顶推消失**。

*提示条 A/B（同一次触发，只差那一个属性）*

| | Snackbar 的 y | 与发布栏（顶边≈2210）的关系 |
|---|---|---|
| 去掉 tag（改前） | **2235..2375** | 正压在 `发布` 按钮（2244..2370）上，按钮几乎完全被盖住 |
| 带上 tag（改后） | **2049..2189** | 抬到发布栏之上 ✓ |

抬升 **186px = 62dp**，正是发布栏自身的高度 —— 即 `setAnchorView` 的应有效果。（另外 4 处的改动与这里逐字相同，机制一致；结构已逐个确认，但只在这一页做了端到端 A/B。）

*布局收益*

| 元素 | 改前 | 改后 |
|---|---|---|
| 卡内重复标题 | 357..430 | **不存在** |
| 标题字段 | 670..799 | **579..708** |
| `选择分类` | 911..960（12sp） | **820..873（13sp 加粗，x 60→84）** |
| 分类 chips | 978..1074 | **891..987** |
| 格式工具条 `预览` | 1177..1285 | **1090..1198** |
| 正文框 | 1313..1994 | **1226..1907** |
| 重复说明段 | 2115..2206 | **不存在** |
| `添加图片` | 2301 之下（完全不可见） | **2058..2202（首屏可见）** |
| 发布栏 / `发布` | 2244..2301 | 2244..2301（不变，无回归） |

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug`（`--rerun-tasks` 重跑，报告时间戳核对过）**0 errors**、2350 warnings（上一批 2353）。

**过程记录**：验证中途手机从 USB 上消失，Windows 侧只剩 WPD/大容量存储两个接口、没有 ADB Interface —— 是手机上「USB 调试」被关掉了（`adb`/`kill-server`/`reconnect` 都无效，不是工具问题）。重新打开调试后继续。`lintDebug` 那次「9 秒成功」是任务被判 UP-TO-DATE、报告时间戳仍是旧的，属于假通过，已用 `--rerun-tasks` 重跑核对。

**结构性观察（非缺陷）**：本 App 有两套表单形态 —— `反馈与建议`/`缓存管理` 一类是「小节标题 + 卡片 + 字段」，`发起讨论`/`文章编辑器` 一类在顶部多一张「引导卡」（`colorSurfaceContainerLow` + `colorOutlineVariant` 描边 + 20dp 圆角，内含 13sp 模式说明）。本次只删掉卡内那个重复的标题，保留引导卡本身与两种形态的并存。

---

### 第二十七批（外壳色带接缝，已修复）

**问题**：全 App 每一页顶部 33dp 是「状态栏一个色、其下内容另一个色」的横向接缝。这是文档里一直挂着的待办（原「已发现待处理」第 1 条）。

**实测（改前，`uiautomator` + 逐点采样）**

| 位置 | 夜间 | 浅色 |
|---|---|---|
| 状态栏 / 导航栏 / 底部导航栏（`@color/surface_card`） | `#1E1E2E` | `#FFFFFF` |
| 内容（M3 `colorSurface`） | `#141218` | `#FEF7FF` |

**两处订正**（都与我先前的判断/说法有关）：

1. **这不是「只有深色有问题」**。此前文档把它写成深色现象，实际浅色也有：`#FFFFFF` vs `#FEF7FF`，Δ=(1,8,0) —— M3 的浅色表面是偏暖的淡紫白而非纯白，33dp 横条上仍是一条带子。
2. **我上一轮口头汇报把浅色 `colorSurface` 说成 `#FFFBFE`，那是凭记忆写的**。从 `material-1.11.0.aar` 的 `res/values/values.xml` 读出来是：

```
m3_sys_color_light_surface  = m3_ref_palette_neutral98 = #FEF7FF
m3_sys_color_dark_surface   = m3_ref_palette_neutral6  = #141218
m3_sys_color_light_background = m3_ref_palette_neutral98   （窗口底色与表面同源）
```

**改动：2 个取值 + 注释（结构不动）**

| 资源 | 改前 | 改后 |
|---|---|---|
| `values/surface_card` | `#FFFFFF` | **`#FFFEF7FF`** |
| `values-night/surface_card` | `#FF1E1E2E` | **`#FF141218`** |

状态栏、导航栏、底部导航栏三处**仍引用 `@color/surface_card`**，因此第十一批「底栏与状态栏同色」那层关系保持成立，也没有资源被孤立或删除。

**为什么改「取值」而不是改用 `?attr/colorSurface`**：两者都能消缝。选改值是为了保住项目对外壳配色的控制点 —— 第十一批正是建立在这个控制点上（那一行 `backgroundTint=@color/surface_card` 是刻意从 M3 默认的 `surfaceContainer(#211F26)` 换过来的，有真机采样证据）。代价已写进代码注释：material 升级若改 `colorSurface`，这里要跟着同步（与 `dimens.xml` 里 `bottom_nav_height` ↔ `bottom_nav_content_inset` 同类）。

> **复查抓到我自己的一个错**：本次第一版把底栏改成 `?attr/colorSurfaceContainer` —— 那**正是**第十一批刻意替换掉的 M3 默认值，等于静默回退一个已验证的决定。查文档发现后改为只动取值。

**真机验证（逐点采样 x=600）**

| 模式 | 采样点 | 改后 |
|---|---|---|
| 夜间 | y=20/60/95（状态栏）、110/300/700（页头/内容）、2180..2295（底栏） | 全部 **`#141218`** |
| 浅色 | 同上各点 | 全部 **`#FEF7FF`** |

两个模式下外壳与内容都落在同一个色值上，接缝消失；改前状态栏与底栏分别是 `#1E1E2E` / `#FFFFFF`。验证完已把主题设置改回「跟随系统」。

**未纳入本次的**：工具栏那条抬升带（`liftOnScrollColor` = `?attr/colorSurfaceContainer`，已从 aar 资源确认静止色是 `?attr/colorSurface`、抬升色是 `?attr/colorSurfaceContainer`，`setLiftedState()` 只在 4 处滚动相关回调里被调用）。此前实测到静止时出现过 `#211F26`，推断是「打字唤出输入法触发一次滚动 → 抬升态残留」，**这一条仍是推断**，仍留在待办里。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors**、2350 warnings（与改前一致）。

---

### 第二十八批（讨论页：向其他开发者对齐，已修 3 项）

**背景**：用户要求「优化讨论页UI，向其他开发者对齐」。这类要求容易变成凭感觉改，所以先去找**可引用的参照**再逐项核对。

**调研**

1. **Discourse 的 topic list 解剖**（论坛软件的参考实现）：标题 / 分类标签 / 摘要 / 回复数 / 浏览数 / 活动时间，另有「posters」（参与者头像堆叠）。本次取到的是一手讨论（`meta.discourse.org` 的 topic-list 组件帖与站内「Related topics」表的列结构）。
2. **本 App 同级列表项**（`item_share.xml` / `item_snippet.xml`）：卡片外壳、内边距、元数据行的写法。
3. M3 列表规范：`m2.material.io/components/lists` 对程序化抓取只返回空壳（JS 渲染），**没取到正文**，因此没有拿它当依据；改用 aar 里的真实资源定义交叉验证。

**实测后修掉的 3 项**

| # | 问题 | 证据 | 改动 |
|---|---|---|---|
| 1 | **空态/错误态被排到整页最底部**，正好贴在右下角「发起讨论」FAB 旁 | `layoutEmpty`/`layoutError` 是加权 `FrameLayout` 的**兄弟**节点，「暂无讨论」bounds y **2199..2256**（内容区 455..2301），截图可见它挤在底部 | 移进列表容器内部并 `layout_gravity="center"` |
| 2 | **摘要与标题相同时卡片把同一句话显示两遍**（正文为空时还留一行空白） | `DiscussionAdapter` 直接 `tvPreview.text = preview`，无去重、无空值处理；截图第 3 张卡标题=摘要 | 正文为空、或摘要与标题**全等**时整行隐藏（连带隐藏分享类型图标）；只做全等判断 —— 仅前缀相同的往往还带链接等额外信息（实测第 1 张卡就是标题+链接），那种保留 |
| 3 | **筛选到空分类时页头计数整条消失** | `DiscussionListFragment` 原本 `list.isEmpty()` 时写空字符串 | 始终显示「N 条讨论」，空结果即「0 条讨论」 |

**真机验证**

| 项 | 改前 | 改后 |
|---|---|---|
| 空态文字 y | 2199..2256 | **1537..1594**（列表区中部，与 FAB 不再相邻） |
| 页头计数（空分类） | 整条消失 | **「0 条讨论」**（切回「全部」为「3 条讨论」） |
| 第 3 张卡摘要行 | 与标题重复 | **消失**，卡片高度少 53px（stats 行 1809 → 1756） |
| 第 1、2 张卡摘要 | — | **保留**（第 1 张带链接，属额外信息） |

**核过但判定「不用改」的 4 项**（这轮调研的另一半产出，避免误改）

1. **时间格式**：`DiscussionAdapter` 早已用 `TimeUtil.formatRelative`，屏幕上看到的 `03-27` 是它对较早日期的输出，不是绝对日期。我一开始把它当成「用了绝对日期」是**错的**。
2. **chip 行缩进**：讨论页/分享页/片段页的 `HorizontalScrollView` 都是 12dp，一致（先前看到分享页的 14dp 是那张**搜索卡**的内边距，不是 chip 行）。
3. **chip 取消选中**：每个 chip 的点击监听会先把所有兄弟置为未选中再选中自己，不存在「无选中」状态，不需要 `selectionRequired`。
4. **卡片外壳**（16dp 圆角 / 1dp `colorOutlineVariant` 描边 / 内边距 16dp / 外边距 4dp）与 `item_share`、`item_snippet` 完全一致 —— 20dp 的卡片左右边距是全 App 约定，不是本页偏差。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors**、2351 warnings（较改前 +1）。**这 +1 已用 A/B 定位**：把本批三个文件临时 `git stash` 后重跑 lint 得 2350，两份报告按 (id,文件,行号) 求差 —— 差异集合里 7 条只是行号位移（同一告警换了行），唯一真正新增的是 `DiscussionListFragment` 计数那行的 `SetTextI18n`：原写法是条件表达式、lint 不报，改成直接插值后报。项目内同类告警已有 20+ 处，且文案全为硬编码中文，故不为此把代码写扭。

**新发现的待办**：同级列表页（分享 / 片段 / 收藏 / 浏览历史）的空态与错误态是**同一套结构**（`fragment_share_list.xml:150-200` 逐行相同），因此同样被排到底部 —— 需要同样的两行移动。本次只改了讨论页。

---

### 第二十九批（发起界面按主流发布器重排：去说明卡 / 图片收进工具条 / 预览图标化）

**起因**：用户说「发起界面我不喜欢」，方向选的是「**主流的是怎么做的**」，范围含文章编辑器。所以这轮先查真实发布器的解剖，再动手。

**调研（可引用的出处）**

- Discourse 的两轮发布器改版讨论（`meta.discourse.org/t/help-us-test-a-new-composer-style/74552` 与 `.../introducing-our-new-composer.../369779`）里，设计者自己写下的理由：去掉「一片灰」（*less "sea of grey"*）、更轻更透气、**减少边框**（*less borders*）、**顶部多留白**（*more breathing room at the top*），并且**只让两样东西抢注意力 —— 编辑器本身与操作按钮**（*only the exact right two things we want to pop*）。
- 2025 年的新版把「Markdown / 富文本」做成**工具条上的一个模式切换**；上传图片同样是工具条上的一个图标。（其实现基于 ProseMirror。）
- GitHub 的 issue/discussion 发布器同构：标题 + 正文区（Write/Preview 切换 + 格式工具条）。
- ⚠️ `m2.material.io/components/lists` 对程序化抓取**只返回 JS 空壳**，没取到正文，故本轮未把它当依据。

**改动（4 个文件）**

1. **删掉两页顶部的「引导卡」**（`colorSurfaceContainerLow` + 描边 + 20dp 圆角 + 一句话）。发起讨论页把模式提示语**移进正文框占位符**（该框原本就有占位符槽位，提示与正文强相关，输入前照样看得见、一落笔就让位）；文章编辑器**不补替代文案** —— 它的字段本就都带标题、正文下方已有 Markdown 语法速查、工具栏标题已说明创建还是编辑。若硬塞进 `layoutContent.hint`，会在「内容（Markdown）」之外多出一个浮标标签，正是第二十五批修掉的那种重复。
2. **图片入口从正文下方的 122dp×48dp 胶囊按钮收进格式工具条**（Discourse 同款）；那条常驻的「最多 9 张…」灰字取消，改为只在实际有图片时显示缩略图 + 一行数量状态（`layoutImages` 整块控制可见性），「9 张 / 1MB」信息移到该图标的 tooltip。
3. **「预览」由带字 TextButton 改为图标按钮**。实测原因：这一行会溢出可视区 —— 图标实际宽 **46dp**（受 IconButton 样式内边距影响，不是 `minWidth` 的 36dp），可视宽度只有 392−40 = **352dp**，72dp 的文字按钮 + 7 个格式图标 + 图片图标必然放不下。图片图标放在格式组**最前**：放最后等于看不见；溢出的应是尾巴上少用的「引用 / 项目列表」，它们仍可横向滚动到。
4. **底栏那行改成说状态**（还差一个标题／正文还是空的／可以发布了）。原先写「先写一个清晰的标题…」，与标题字段的 helper「建议用一句话概括你的问题或观点」相隔 500px 说同一件事。

**真机验证（1176×2400 / 480dpi）**

| 项 | 结果 |
|---|---|
| 发起讨论首屏标题字段 | y 670..799 → **360..489**（上移 310px = 103dp，说明卡不再占位） |
| 工具条可见顺序（读 bounds） | 预览 \| 添加图片 \| 加粗 斜体 行内代码 代码块 插入链接 引用 → 超出的是「项目列表」（可滚动到） |
| 正文框占位符 | 已变为模式提示语（截图确认：「尽量写清背景、现象和期望结果…」） |
| 预览开关 | 点眼睛 → 文案变「返回编辑」、出现预览卡「暂无可预览内容…」，进程未崩 |
| 文章编辑器 | 引导卡与重复标题均消失；`整理好标题…` 在页面上 **0 次**出现；正文框保留自己的占位符「在此输入 Markdown 内容...」 |

**⚠️ 门禁抓到我自己的一个真错误**：我在 Kotlin 里直接调了 `View.setTooltipText`（API 26 起，minSdk 24），lint 报 `NewApi` **error**、`BUILD FAILED`。已改用 `ViewCompat.setTooltipText`（内部自带版本判断）。这条值得记下来：XML 里的 `android:tooltipText` 只是会被静默忽略，**在代码里调同名 setter 是硬错误**。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug`（`--rerun-tasks`）**0 errors**、2350 warnings（与改前一致）。

**订正第二十六批的一条观察**：那批写的「本 App 有两套表单形态（反馈一类无引导卡、发起讨论一类有）」—— 本批之后**这个差异不存在了**，引导卡已从发起讨论与文章编辑器移除。

---

### 第三十批（讨论区补三件主流功能：排序 / 标签 / 未读，已修复并部署）

**起因**：用户看了第二十九批末尾那份「主流讨论平台功能面」的对照表，直接点了三条缺口：排序、未读状态、标签。

**动工前的取舍（都已落到代码注释里）**

1. **标签沿用 App 内既有做法，不引入 Discourse 那套**：`snippets.tags` 就是一个逗号分隔的 TEXT 列（`schema.sql:184`），既能被搜索（`snippets.ts:35` 的 `tags LIKE ?`），详情页又渲染成 `#chips`（`SnippetDetailFragment.bindTags`）。讨论照抄 → 只需 `ALTER TABLE ADD COLUMN`（非破坏性），而不是两张新表 + 关联表。
2. **未读不能塞进列表接口**：列表路由挂着 `edgeCache(120)`，而缓存键就是 URL（`cache.ts:43/70`）—— 响应里一旦有按用户不同的字段，先访问那个人的未读状态就会被缓存并展示给所有人。所以未读独立成接口，列表继续共享缓存。
3. **也不能复用 `content_views`**：它是 `INSERT OR IGNORE`（首次浏览即定、`viewed_at` 永不更新），而且 `server.ts:53-58` 有个 30 天清理任务。阅读位置需要「最后一次读到哪」且不能过期 → 单开一张表。
4. **排序公式要能一眼看懂**：`hot = 阅读数`、`replies = 回复数`，不做时间衰减（Discourse 的 Top 带时间窗、Reddit 的 hot 带时间衰减，这个体量不值得引入说不清的评分）。

**改动**

| 层 | 内容 |
|---|---|
| 后端 | 列表接口 `sort` 参数（latest 默认 = 现状按 `updated_at`，向后兼容；hot；replies；非法值回退；置顶恒最前）；`tags` 列 + `normalizeTags`（逗号/顿号/空白切分、去空、忽略大小写去重、单个 ≤20 字符、最多 5 个）；`GET /unread`（需登录）；`POST /:id/read`（需登录、幂等 upsert）；两个迁移文件 |
| Android | 列表页头排序控件（取代原先那个纯装饰、不可点的图标）；发布页「标签」输入框 + 进草稿/回填；详情页 `#chips`；列表项未读蓝点 + 「N 条新回复」；详情打开上报已读并让列表刷新 |

**本地 SQL 验证**（`node:sqlite`，两个脚本用完即删）

```
# 标签与排序
BEFORE_MIGRATION_HAS_TAGS=false → AFTER=true（默认 ''）；重复执行报 duplicate column name: tags
  （deploy_now.py 逐条 try/catch，故幂等）
三种排序顺序 / 置顶恒首位 / 标签搜索 / INSERT+UPDATE 带 tags → ALL_SQL_VALID

# 未读
READS_TABLE_CREATED / READS_INDEX_CREATED = true；迁移两遍无异常
ADMIN_UNREAD = [{"id":1,"n":2}]   ← 3 条评论里「读到第 1 条之后」只算 2 条
BOB_UNREAD = []                   ← 读在最后一条评论之后
CAROL_UNREAD = []                 ← 从没读过任何帖（否则新用户满屏红点）
AFTER_MARK_READ = []；READS_ROW_COUNT = 1（upsert 不重复插入）；CASCADE_OK = true
```

**生产验证**（已部署两次，迁移均执行成功，pm2 分别 ↺26 / ↺27）

```
/api/discussions                → 11, 8, 7（按最后活动；向后兼容）
/api/discussions?sort=hot       → 7, 11, 8（阅读 50/45/36）
/api/discussions?sort=replies   → 11, 8, 7（回复 16/1/1）
/api/discussions?sort=bogus     → 回退 latest
GET  /api/discussions/unread    → 401（路由存在且受保护，未被 /:id 吞掉）
POST /api/discussions/11/read   → 401
```

**真机验证**

- 排序：点「最新 ▾」→ 菜单 最新/最热/最多回复；选「最热」后首条变成 50 阅读那条 ✓
- 标签端到端：发布页填 `test,TEST,#tags,extra1,extra2,extra3,extra4` → 详情页 chips 实际是
  **`#test #tags #extra1 #extra2 #extra3`** —— 大小写去重、存储去 `#` 显示补 `#`、上限 5
  全部按设计生效 ✓（测试帖随后删除）
- 未读：初始无红点（从没读过）→ 打开一帖 → 返回无红点 → 在该帖回一条 → 列表出现
  **标题前蓝点 +「17 回复 1 条新回复」**（标题 x 108 → 150）→ 删除该回复 → 全部复原 ✓
- 两次测试数据都已清理：讨论仍为 11/8/7 三条、reply 16/1/1、view 45/36/50 ✓

**⚠️ 验证时抓到并修掉的真 bug**：从详情/发布页返回后排序被重置回「最新」。原因是
`onViewCreated` 里 `currentSort` 被无条件赋成 `savedInstanceState?…?: 默认值`，而视图重建时
`savedInstanceState` 可能为 null，于是把用户刚选的排序打回默认。改为「仅在确有存档时覆盖」，
复测：选「最热」→ 进详情 → 返回，按钮仍是「最热」、顺序仍是 50/45/36 ✓

**⚠️ 过程中的一个操作失误（记下来避免重犯）**：我用 `Get-Content -Raw` + `Set-Content` 改
`discussions.ts`，把文件写成了系统 ANSI 编码（`Set-Content` 默认编码），导致该文件不再是合法
UTF-8（`read` 工具直接报 invalid UTF-8）。已 `git checkout` 还原并全部改用 `edit` 工具重做。
**这个仓库里不要用 PowerShell 写文件** —— 与之前 `deploy_now.py` 的 GBK 事故同一类。

**门禁**：两批次均 `assembleDebug` 通过；`lintDebug --rerun-tasks` **0 errors**，2353 warnings
（批次 1 +3 = 新增三条中文硬编码文案；批次 2 持平）。

**部署脚本**：`migrate_discussion_tags.sql` 与 `migrate_discussion_reads.sql` 已登记进
`deploy_now.py` 的上传清单与 migrate 段落。该脚本被 `.gitignore` 忽略，**这些改动只存在于本机**。

### 第三十一批（对第三十批交付的三项功能做一次独立复验）

**性质**：用户要求「验证」。本轮**不写新代码**，只做观测 + 一次可逆的测试数据往返，目的是把
第三十批的结论重新独立证一遍，而不是复述。

**门禁复跑**：`:app:assembleDebug` 通过；`:app:lintDebug --rerun-tasks`（2m30s）**0 errors /
2353 warnings**，与第三十批基线逐位相同 —— 说明此后没有引入新告警。重建后的 APK 重新安装到
真机（`install -r` 保留数据），因此这一轮验的就是已提交的那份代码。

**生产 API（只读）：四态逐项复核，与第三十批记录一致**

```
/api/discussions                → 11, 8, 7
/api/discussions?sort=hot       → 7, 11, 8（阅读 50/45/36）
/api/discussions?sort=replies   → 11, 8, 7（回复 16/1/1）
/api/discussions?sort=bogus     → 回退 latest
```

**真机复验**

- **排序切换与持久化**：「最新 ▾」→ 菜单三项（截图存档）→ 选「最热」，列表首条变成 50 阅读
  那条，顺序 7/11/8；**进详情再返回，按钮仍是「最热」、顺序仍是 7/11/8** —— 第三十批修掉的
  「返回后排序被打回默认」复验通过。
- **未读三态**：读过一帖后无点 → 回一条测试评论 → 列表出现 **`iv_unread_dot` +「2 回复
  1 条新回复」**（标题 x 由 588 被挤到 609）→ 再进详情返回，**点消失、回复数保留 2**。
- **标签端到端（意外收获）**：打开发布页时弹出「恢复未发布草稿？」，选择载入后
  `etTags` **原样恢复** `test,TEST,#tags,extra1,extra2,extra3,extra4` —— 这直接实证了草稿把标签
  一并持久化（第三十批只验到发布链路，没验草稿这条）。发布后生产存
  `test,tags,extra1,extra2,extra3`（大小写去重 / 去 `#` / 上限 5），详情页 chips 实拍为
  `#test #tags #extra1 #extra2 #extra3`。

**⚠️ 复验抓到的一个此前未记录的 bug：发布成功后草稿没有被清掉**

- **复现**：发布成功 → 立刻再进「发起讨论」→ **又弹「恢复未发布草稿？」**，内容正是刚发布出去
  的那一篇。
- **证据**：`run-as` 读 `shared_prefs/discussion_draft.xml`，发布后仍有
  `draft_general_title` / `_content` / `_category` / `_tags` 四个键。
- **根因**（`CreateDiscussionFragment.kt`）：success 分支先 `clearDraft()`，紧接着 `navigateUp()`
  触发 `onDestroyView()`；而 `onDestroyView()` 里有一句「Flush any pending draft write」→
  `persistDraftNow()`。此时输入框里的文字还在，于是**刚清掉的草稿被立刻原样写回**。
  `persistDraftNow()` 只在三个字段全空时才走删除键的分支，所以这次写入必然发生。
- **归属**：`persistDraftNow` 与 `clearDraft` 同出于 `6984cfc`（2026-05-04，
  "improve discussion editor attachments"），**早于本次改造**，不是第三十批引入的；那一批只是给
  这两个函数各加了一行 `tags`。
- **影响**：每次发布后都留一份「已发布的草稿」，用户若载入再发一次就会产生重复帖。
- **修法（已按指示修掉）**：加一个 `draftConsumed` 闸门，**只在 success 分支置位**；
  `scheduleDraftSave()` 与 `persistDraftNow()` 开头各加一行 `if (draftConsumed) return`。
  共 **11 行，全为新增、无删除**（提交 `3aaf62c`）。
- **为什么不把闸门也压在「丢弃草稿」那条路径上**：`clearDraft()` 有两个调用点，另一个是恢复
  弹窗的「丢弃草稿」按钮。用户丢弃后往往会继续写，若那里也置位就等于把草稿功能关掉了；
  所以只认「发布成功」这一个事件。
- **修后真机复验（4 项，全部通过）**
  1. 先打字再离开 → `draft_general_title=verify-draft-clear` 等 4 键正常落盘（草稿功能没被改坏）；
  2. 发布成功 → `discussion_draft.xml` 变成 **`<map />`**（修前是 4 个键原样留着）；
  3. 发布后再进发布页 → **不再弹「恢复未发布草稿？」**；
  4. **回归**：真正的未发布草稿仍会弹恢复框 → 点「丢弃草稿」→ 继续打字 → 再离开，
     **草稿照样存得下**（`verify-after-discard` 落盘）—— 专门验闸门没有误伤丢弃路径。
- **门禁**：`assembleDebug` 通过；`lintDebug` **0 errors / 2353 warnings**，与修前逐位相同。
- **顺带记一个 Git 坑**：本仓库 `core.autocrlf=true`、工作区 CRLF、库里存 LF。
  `git commit` **自己打印**的统计是「1048 insertions / 1037 deletions」（整文件雪崩），而实际入库的
  blob 与父提交只差 **11 行**；复核方式是 `git -c core.autocrlf=false diff <c>^ <c>`。
  这不是推测：我第一次提交时被这个打印吓到，`reset --soft` 重做了一遍，事后复核**被丢掉的那次
  提交其实也是干净的 11 行**，白折腾一次。**结论：以 raw diff 为准，不要凭 commit 的打印回滚。**

**残留（本轮复验自己造成的一处，如实记录）**

- 测试评论的发布/删除让讨论 **#7 的 `updated_at` 从 `2026-03-06 05:54:09` 变成
  `2026-09-13 16:30:41`**。后端 `POST /:id/comments` 写的是 `updated_at = datetime('now')`
  （`discussions.ts:408`），而**删除评论不回退该字段**（删除路径没有对应 UPDATE），所以删掉测试
  评论也恢复不了。后果是 #7 在「最新」下排到第一位（原先 #11 第一）。
- 计数类数据**全部复原**：讨论仍 3 条，reply 16/1/1，tags 全空，
  `?search=TagCheck` 命中 0，`shared_prefs` 中草稿键已清空（`<map />`）；view 从 45/36/50 变成
  **51/45/36**（#7 +1，见下条）。
- 测试帖 `verify-draft-clear`（id 13）与 `verify-unread-temp` 评论均已删除。
- 要还原 `updated_at`（连同把 #7 的 view 打回 50）只能直接写库（SSH + SQL），超出本机
  `deploy_now.py` 的能力范围，未做。用户已明确选择**保留现状**。

**顺带确认无回归**：发布页格式工具条第一枚仍是「添加图片」（第三十批的重排仍在）；列表卡片、
分类 chips、底部导航均与第三十批一致。

### 第三十二批（底部导航栏：81dp → 65dp，紧凑到主流尺寸）

**起因**：用户反馈「感觉底部导航栏占用过大」。

**调研（先查库、再量同行，最后才定数字）**

Material 库里的实证（解包 `material-1.11.0.aar` 的 `res/values/values.xml`）：

| 常量 | 值 | 用途 |
|---|---|---|
| `m3_comp_navigation_bar_container_height` | **80dp** | 被 `Base.Widget.Material3.BottomNavigationView` 用作 **`android:minHeight`** |
| `m3_bottom_nav_item_padding_top` / `_bottom` | 12dp / 16dp | 条目的上下内边距 |
| `m3_comp_navigation_bar_active_indicator_height` / `_width` | 32dp / 64dp | 选中态胶囊 |
| `m3_comp_navigation_bar_icon_size` | 24dp | 图标 |

**关键发现**：80dp 是 `android:minHeight`，是**下限**。所以只把 `layout_height` 写成 64dp 会被顶回
80dp —— 这正是两天前 `c386165` 那次「64dp 失败」的真实原因：当时是把 `itemPaddingTop/Bottom`
压到 2dp 硬塞（内容 12+32+16+16 装不进 64），而不是覆盖 `minHeight`。**「64dp 装不下」这个结论
是错的**，正确的三件套是 `minHeight` + 上下各 8dp + 32dp 指示条 + 16dp 标签 = 64dp。

主流对照：M3 规范 80dp（我们当时照抄的）／Material 2 默认 **56dp**／iOS TabBar **49pt**（+34pt
安全区）／**知乎**在本机实测（`tab_divider` 上沿 y=2224）**59dp**。也就是说我们处在规范围里最大的
那一档，比用同一台机器量的同行高 20dp 以上。

**方案与选择**（用户拍板）：**A 64dp**（8 + 32 + 16 + 8 + 1dp 分割线），标签保持
`labelVisibilityMode=selected` 不变。

**改动（4 个文件，28 增 13 删）**

- `values/dimens.xml`：`bottom_nav_height` 80dp → **64dp**；`bottom_nav_content_inset` 104dp → **88dp**
  （= 64 + 24 呼吸余量，5 个布局共 8 处引用的是同一个 dimen，改一行即全同步）；
- `values/themes.xml`：`Widget.SkNote.BottomNavigation` 增加
  `android:minHeight=@dimen/bottom_nav_height` 与 `itemPaddingTop/Bottom=8dp`，并在注释里写清
  「三者是一组、`minHeight` 才是真正生效的那一个」；
- `layout/activity_main.xml`、`util/AppSnackbar.kt`：把两处写死旧几何的注释改成实测值。

**真机验证（像素级，480dpi → 1dp = 3px）**

| 项 | 改前 | 改后 | 判定 |
|---|---|---|---|
| 分割线 | y=2157 | **y=2205..2207** | 条顶 = 2400 − 195 = 2205 ✓ |
| 栏体 | 2160..2400（80dp） | **2208..2400（192px = 64dp）** | ✓ |
| 指示条 | 96px 高 | **96px 高**（x=480 量，避开圆角），顶边 2232 = 栏顶 + 8dp | 未被压扁 ✓ |
| 标签 | 字形 2304..2352 | **字形 2320..2365**，标签视图底 ≈2380（距屏幕底 8dp） | 无裁切 ✓ |
| 未选中图标 | 中心 2280 | 中心 2304（2268..2340，栏内居中） | ✓ |
| **整条** | **81dp** | **65dp** | **省 16dp（20%）** |

配套项同步验过：

- `fabScrollTop`（回到顶部）底边 = **2136** = 2400 − 264，正是 88dp 让位量 ✓；
- 「我的」页滚到底，最后一项（退出登录卡）底边 2064，距分割线 141px，不被导航栏压住 ✓；
- 详情页导航栏从视图树消失（`wrapper_present=False`），返回后回到 `[0,2205][1176,2301]` ✓；
- **提示条锚点**：顶部页发帖/编辑帖触发，`snackbar_text`「讨论更新成功」实测
  **`[72,2061][1104,2202]`** —— 下沿距分割线只剩 3px（1dp），与改前同构（改前栏顶 2157、提示条
  下沿 2154，同样是 3px）。库内部按锚点高度算 `bottomMargin`，所以缩小栏高不会破坏这个关系 ✓。

**方法论教训（记下来免得下次再绕）**：本项目提示条的容器色是**刻意反着来的**
（`snackbar_container=#FF2B2930`，不随 M3 反色），所以我先按「找一条亮色宽带」连拍两张都没找到，
**连拍也很难命中只有 1.5–2s 的提示条**。最后靠「按容器色匹配 + `uiautomator dump` 连打」才抓到，
而 dump 还能直接给出精确 bounds —— 以后验证短命浮层优先用 dump，不要用截图碰运气。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors / 2353 warnings**（与改前逐位相同）。

**又踩了一次 Git 行尾坑，这次把可复用的判据定下来**（提交 `914ad3d`）：

- 现象：4 个文件里只有 `activity_main.xml` 被报「整文件改写」（commit 打印 73 增 58 删，
  实际只有 28 增 13 删）。
- **判据一（不可信）**：`git -c core.autocrlf=false diff --cached` 这次给出了错误结论 ——
  它对该文件报 49/50 行改写，而两个 blob 实测都是纯 LF。
- **判据二（可信）**：比 blob 本身。`git cat-file -s HEAD^:<path>` = 2227、`git cat-file -s HEAD:<path>`
  = 2215（差值 12 = 我改动的文本净减字节数，行尾字节数相同 → 风格一致）；
  `git grep -c "`r" <rev> -- <path>` 两个版本都为空 = 无 CR。**结论：干净。**
- 根因与正确做法：工作区 CRLF、库内 LF（`core.autocrlf=true`）。`git -c core.autocrlf=false add`
  会把工作区的 CRLF 原样写进索引，与父提交的 LF 不一致 → 雪崩。**用普通 `git add`**；
  若 `git ls-files --eol` 仍显示 `i/crlf`（本次就是），再补一条
  `git add --renormalize <path>`，目标是 `i/lf  w/crlf`。

**生产数据**：为验证提示条锚点发了两个测试帖（`verify-nav-snackbar` id14、`verify-nav-snackbar2` id15），
**均已删除**，生产仍为 7/11/8 三条、reply 1/16/1；view 51/45/36。

**残留**

1. 选中项图标比未选中项高 12dp（`labelVisibilityMode=selected` 的固有行为：选中项换成带标签的
   布局）。用户本次选择**保持现状**；要消掉就得改成 `labeled`（四项都有字，高度不变）。
2. 条的视觉下沿即屏幕底（内容画进手势条区域），与知乎同款做法；本机手势条不可见，所以观感成立。
   若将来遇到手势条可见的机型，可再评估是否给条目加底部 inset。

### 第三十三批（学习资源页统一搜索入口：标题行一个图标）

**起因**：用户提出「参考手册和代码片段都有搜索功能，是否可以合并搜索框，放到『学习资源』同一行，
平时只显示搜索图标」。

**调研先于动手，结论是两边根本不是同一种搜索**：

| | 参考手册 tab | 代码片段 tab |
|---|---|---|
| 形态 | 56dp 的**假搜索框**（整块可点，点了跳页） | 真输入框 + 搜索/清除按钮 |
| 数据源 | **纯本地** `ReferenceData.search()`（510 条离线） | **网络** `getSnippets(search=)` |
| 目标 | `ReferenceSearchFragment`：历史 + 热门标签 + 建议 + 结果 | 无（原地过滤列表） |
| 实测占高 | **204px = 68dp** | **184px ≈ 61dp** |

三者同处一个容器（`ReferenceContainerFragment`：标题行 + TabLayout + ViewPager2），标题行右半边
本来就是空的。另查了库：Material 1.11 有 `SearchBar`/`SearchView`（原地展开的官方实现），
所以「平时一个图标、点开变输入框」不必手写动画。

**用户拍板的形态（混合）**：图标只放**一个**（标题行右端），行为按当前 tab 分 ——
参考手册 tab 点了**跳搜索页**（与旧假框同一目的地，内容不变），代码片段 tab 点了**原地展开成输入框、
输入即过滤下方列表**。旧的两处入口都删。

**动手时发现的一处牵连**：参考页那张卡片里**不只是搜索** —— 右边还挂着排序按钮
（`tvSortMode` + `btnSort`）。删卡片就得给排序找新家：并进了**已有的筛选 chip 行右端**
（把 chip 的 `NestedScrollableHost` 包进一层横向 LinearLayout，排序固定在右侧不随 chip 滚动），
零额外高度。

**改动（7 个文件，290 增 187 删）**

- `fragment_reference_container.xml`：标题行拆成收起态（标题 + 48dp 搜索图标）与展开态
  （返回箭头 + `etHeaderSearch` + 清除），**两态同为 48dp**，切换时 TabLayout 与内容不跳；
- `ReferenceContainerFragment.kt`：按 tab 分发点击、展开/收起、300ms 防抖后下发关键词、
  IME 搜索键立即搜、展开态接返回键先收键盘再退出页面、tab 切换自动收起并清空；
- **新增** `LearningSearchViewModel.kt`：容器与 ViewPager2 里的片段没有直接引用关系，
  用作用域挂在容器上的 ViewModel 传关键词（`by viewModels()` vs `by viewModels({ requireParentFragment() })`），
  顺带扛住旋转重建；
- `SnippetListFragment.kt`：删掉输入框相关的一切（TextWatcher/performSearch/updateSearchActionState），
  改为观察共享关键词；进程被杀重建时把关键词回灌给容器；
- `fragment_reference.xml`：删掉那条 68dp 假搜索卡，排序并入 chip 行；
- `fragment_snippet_list.xml`：删掉行内搜索卡；
- `ReferenceFragment.kt`：移除 `layoutSearchBar` 的点击跳转。

**真机验证（LIO-AN00）**

| 项 | 改前 | 改后 |
|---|---|---|
| 参考手册 tab 列表顶边 | `rvReferences` y=936 | **y=740（+65dp 可视区）** |
| 代码片段 tab 列表顶边 | `rvSnippets` y=804 | **y=620（+61dp 可视区）** |
| 参考页假搜索框 | `layoutSearchBar [48,502][1128,706]` | **节点已不存在** |
| 参考页排序 | 卡片内 | `btnSort [1032,519][1176,663]`，与 `chipAll` 同一行 |
| 标题行 | 仅有标题 | `btnSearchToggle [1008,99][1152,243]` |

行为逐条验过：参考手册 tab 点图标 → 进搜索页（`etSearch` 提示「搜索积木、组件、控件...」+ 历史
+ 热门标签）✓；代码片段 tab 点图标 → `layoutHeaderSearch [0,99][1176,243]` 原地展开（与标题行
等高，无跳动），输入框 hint「搜索代码片段...」✓；输入 `toast` → **「找到 1 个结果」** 且列表只剩
「Toast 显示消息」✓；点返回箭头收起 → **过滤结果保留**（仍「找到 1 个结果」）✓；再次展开 →
输入框**回填 toast** ✓；点清除 → 回到「20 个片段」✓；切到参考手册 tab → 自动收起 ✓；切回 →
**关键词已清空**（回到 20 个片段）✓。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors / 2353 warnings**（与改前逐位相同 ——
删掉两条中文硬编码、新增两条，正好相抵）。

**行尾（按第三十二批定的判据走，这次先查后提交）**：`git ls-files --eol` 七个文件全是 `i/lf`，
提交后复核四个代表文件的 blob 风格 parent/new 均为 LF ✓，`git diff --stat HEAD^ HEAD` =
**290 增 187 删** = 实际改动。**commit 自己打印的「1663 增 1560 删」仍是那个假象**，
再一次印证：以事后 raw diff 为准。

**残留**

1. **首页的「搜索文章…」是第三套搜索入口**，这次没动。若要做全站搜索（文章 + 参考 + 片段 + 讨论），
   是另一个量级的事，建议单独排一批。
2. 按用户选择，**没做跨类合并**：参考手册的搜索页仍只搜参考内容，片段搜索仍只搜片段。
   `LearningSearchViewModel` 目前只有一个关键词字段，将来要合并也从这个类扩展。
3. 展开态是**代码片段 tab 专有**：切 tab 会收起并清空关键词（刻意如此，避免带着片段关键词看参考
   列表时列表莫名变短）。若希望切回来还记得关键词，改 `collapseSearch(clearQuery = true)` 一处即可。

### 第三十四批（学习资源页：图标精修 + 排序选择栏换底部弹层）

**起因**：用户两条反馈 ——「搜索图标过大，且简陋」；「优化一下筛选图标大小以及点击后的选择栏的
布局，现在看起很简陋」，并明确「**不改位置**」（指筛选入口不搬家）。

**一、图标：「过大」是个系统性坑，根因不在图标本身**

`ImageView` 只写容器尺寸、不写 padding 时，`fitCenter` 会把 24dp 的矢量**放大到容器尺寸**。
像素实测：

| 图标 | 容器/padding | 墨迹 |
|---|---|---|
| `btnSearchToggle`（第三十三批新加） | 48dp / 无 | **105px = 35dp** |
| `btnSort`（第三十三批搬进 chip 行） | 48dp / 8dp | 108px = 36dp |
| `btnGuide` / `btnToggleStats`（同屏老图标） | 28dp / 4dp + alpha 0.6 | ≈ 15dp |
| 讨论页 `btnSort` | TextButton + `iconSize=16dp` | 16dp |

也就是说这两枚比同屏邻居大了 **2.4 倍** —— 用户的判断是准的。

「简陋」是另一个原因：`ic_search` 是 2014 版 Material Icons 的**粗杆锐角**放大镜，笔画本来就重，
放大到 35dp 更明显，而且满亮度（`colorOnSurface`）比旁边的 22sp 标题还抢眼。

**改法**

- 新增 `ic_search_rounded.xml`：Material Symbols Rounded 字形（细笔画、圆头）；
- 新增 `ic_sort_rounded.xml`：圆角三横条，替换排序图标（讨论页本来就是 `iconSize=16dp`，两处同步换）；
- **没有动 `ic_search` / `ic_sort`** —— 全项目还有 8 处在用旧字形（首页假搜索条、参考搜索页、
  分享列表等），换掉会波及没验证过的页面；
- 参考页用 padding 控制成像：搜索 11dp、排序 14dp；配色改 `colorOnSurfaceVariant`
  （与它们旁边的「默认 / A→Z」弱化文案同族）。

**墨迹实测**：搜索 105px → **58px（19.3dp）**；排序 108px → **46px（15.3dp）**，距右边缘 24.7dp；
展开态的返回/清除 35dp → **16dp / 14dp**。

**二、选择栏：`PopupMenu` → 底部弹层**

原状（两个入口都用 `PopupMenu`）：

- 参考页 7 个纯文字项、没有标题，标当前项的办法是 `it.title = "✓ ${it.title}"` ——
  **拼字符串糊一个勾**，选中项文字还会被迫右移；
- 讨论页 3 项，**连选中标记都没有**，两个入口样式还不一致。

改法：新增共用组件 `ui/common/SortOptionsSheet.kt` + `layout/dialog_sort_sheet.xml`：

- 底部弹层（本项目的 `BlockGuideDialog` / `BlockCompareDialog` 已是这个模式，跟进统一）；
- 标题「排序方式」+ 每项「名称 + 一句说明」（「按分类」「按 ID ↑」这类不加说明说不清）；
- 当前项：左侧实心勾 + 主色加粗；未选中项用 `alpha=0` 的勾**占位**，保证文字左边缘不跳；
- 结果走 `setFragmentResult` 而非回调属性 —— 弹层开着时若发生配置变更，回调属性会随旧实例丢失。

**真机验证（LIO-AN00）**

| 项 | 结果 |
|---|---|
| 参考页点排序图标 | 弹层打开：标题 + 7 项，每项带说明 ✓ |
| 选中标记 | 初始勾在「默认排序」；改选 A→Z 后勾与主色一起移到「按名称 A→Z」✓ |
| 排序真的生效 | 按钮文案变 `A→Z`，列表首条变成 `!=` / `+` / `-`（名称升序）✓ |
| 讨论页同一弹层 | 3 项 + 说明；选「最热」→ 文案「最热」、首条为 50 阅读那条 ✓；再选「最多回复」→ 首条为 16 回复那条 ✓ |
| 弹层外观 | 28dp 圆角 + 遮罩（页面底色从 `#141218` 压到 `#08070A`）✓ |
| 图标墨迹 | 搜索 19.3dp、排序 15.3dp（原 35dp / 36dp）✓ |

**已知行为（不是缺陷）**：弹层入场动画那 ~300ms 内的点击会被吃掉 —— 脚本用毫秒级连点复现过；
动画结束后点击稳定生效（连续两次选择都通过）。真人手速够不到这个窗口。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors / 2353 warnings**（持平）。

**残留**：旧字形 `ic_search` / `ic_sort` 仍在其它页面使用（本次刻意不动）。若将来要统一图标语言，
应整批替换并逐页真机验证 —— 这次的教训是「同一个 drawable 在不同容器里成像尺寸可能差 2 倍」，
逐个看 padding/iconSize 比换字形本身更重要。

### 第三十五批（同类问题普查：23 个被放大的动作图标一次修完）

**起因**：用户问「还有类似的吗也搞成这样」。

**方法（不靠印象，写脚本扫）**：`sk_icon_audit.ps1` 遍历 `res/layout/**`，对每个
`ImageView` / `ImageButton` 取容器尺寸、`android:padding`（含 paddingHorizontal/Start）、
`scaleType`、`android:src`，再取该 drawable 的固有尺寸（矢量读 `android:width`），
算「有效绘制区 = 容器 − 2×padding」与固有尺寸之比 —— 比值 ≥1.3 且没有阻止放大的 scaleType 就报出来。

**共 35 处，分两类**

| 类别 | 数量 | 判断 |
|---|---|---|
| **动作图标**：工具条返回/清除/搜索、管理列表的查看/编辑/删除/更多 | **23** | **同一个坑，本次全修** |
| 空态/错误态大图：`layout_empty_state.xml` 72dp + `alpha=0.3`、错误态 64dp、登录页 80dp、首页/历史/收藏/分析器等 64–72dp | 12 | **刻意放大**（「大而淡」的空态画法），不动 |

修法是给这 23 个按钮补 `android:padding="8dp"`（点按区仍是 48dp，只有字形缩回正常量级），
涉及 13 个布局文件。

**怎么改的（值得记）**：23 处 × 13 文件的机械替换用 **Python** 脚本做（读字节 → UTF-8 解码 →
只插入一行 → 原样写回）。**没有用 PowerShell** —— 本仓库已被它写坏过两次编码（GBK / ANSI）。
改完每个文件的 diff 只有 1–3 行 ✓。

**真机验证**

- 搜索页（`fragment_reference_search.xml`）：返回箭头墨迹 **16dp**、搜索 **17.7dp**（原都是 35dp），
  与旁边 16sp 的提示文字同量级 —— 截图确认视觉成立；
- 重跑审计脚本：23 个动作图标从**比值 2.0 / 2.67 降到 1.33**，剩下的全是刻意放大的空态图 ✓；
- 门禁：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors / 2353 warnings**（持平）。

**顺带踩到的两个坑**

1. 批量插入的行是 LF，而文件其余行是 CRLF → 工作区变成 `w/mixed`；用 Python 统一成 CRLF 后
   `git status` **反而报 12 个文件被修改**，而 `git diff` 是空的 —— 是索引里的 stat 缓存过期，
   **再 `git add` 一次即刷新**（现在稳定为 `i/lf w/crlf`）。仓库里那 5 个「改了但零内容差异」的
   噪音文件，很可能就是这么来的。
2. 复核提交时 commit 又打印「2055 增 / 2033 删」，实际是 **23 增 1 删** —— 第三十二批定的判据
   再次生效（以事后 raw diff 为准）。

**残留**

1. 空态大图那 12 处**未动**：它们是刻意的「大而淡」画法，真要改等于重做空态插画，另开一批。
2. 另一类「简陋」是**图标字形家族不统一**（旧版 Material Icons 的直角实心 vs 新的圆角细笔画）。
   本次只统一了搜索与排序两枚；全项目统一需整批替换 + 逐页真机验证，建议单开一批。

### 第三十六批（选择类弹窗全部收编到 OptionsSheet）

**起因**：用户指着设备上正开着的一个弹窗问「当前页面的菜单呢」——那是参考详情页的
**快速跳转**（`ReferenceDetailFragment.showQuickJumpDialog()`），用的是
`MaterialAlertDialogBuilder#setSingleChoiceItems`：系统默认单选列表，一列「Number / String /
Boolean / Map / set / increase / decrease / put key / get key…」的小字墙。

**先定判据，再普查**（避免把动作菜单也一起改了）：
**「这列选项里有没有一个『当前值』」** —— 有 = 选择类（该用弹层），没有 = 动作类（对话框更轻）。

全项目 `setItems` / `setSingleChoiceItems` 共 11 处：

| 归类 | 处 | 处理 |
|---|---|---|
| **选择类**（有当前值） | 参考快速跳转、积木块**类型**选择、**移动到调色板** | **本次换 OptionsSheet** |
| 选择类 | 参考页排序、讨论页排序 | 第三十四批已换 |
| 选择类 | `BlockShareHelper#导入到调色板` | **未换**：它是 helper 类，拿不到 FragmentManager |
| **动作类**（无当前值） | `CustomEvents`(编辑/导出/删除)、`UserManage`(角色/重置密码/删除)、`LogicVisualizer` ×5（切 Activity、复制到项目…）、`CustomBlocks` 长按菜单 | **保持对话框** |

**组件改造**：`SortOptionsSheet` → **`OptionsSheet`**（泛化：`descs`/`selectedIndex` 都可省，
`REQUEST_KEY` 改 `options_sheet`），两个排序调用点跟着改名。类注释里写明「动作类菜单不适用本组件」。

**真机验证（4 个入口）**

| 入口 | 结果 |
|---|---|
| 参考页排序 / 讨论页排序 | 第三十四批已验，改名后复跑无异常 ✓ |
| 参考详情**快速跳转** | 弹层标题「积木块 / Variable」，每项带该条目自己的说明；当前项 `Number` 带勾；点 `set` → 详情页真的跳到 `set`（描述变成「将一个值赋给变量。」）✓ |
| 积木管理 → 长按积木 → **移动到调色板** | 弹层列出全部调色板（asset / Listen / Json / …）✓ |
| 积木编辑器 → 点**类型**字段 | 弹层 12 项、每项带该积木形状的含义（s/d/r/b/c/e/f/h 这套代号光看标签说不清）；当前项 `c (if 容器)` 带勾 ✓ |

**一处行为变化**（刻意）：类型选择原来是「确定 / 取消」两段式（选完还要点确定才生效），
现在**选完立即生效**；不选、直接下滑关掉即等于取消 —— 与其它三处一致。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors / 2353 warnings**（持平）。

**残留**：`BlockShareHelper#导入到调色板` 仍是系统单选列表（helper 无 FragmentManager）。
要换得先把它接进某个 fragment，或改用非 Fragment 版的 `BottomSheetDialog`，此处未动。

### 第三十七批（滚动时收起筛选行）

**起因**：用户提「滑动分类的时候，筛选会隐去」。

**效果**：列表向下滑 → 筛选行（分类 chip + 排序）收起；向上滑 → 展开；**滑到顶部一定展开**
（否则用户滑回顶部看不到筛选行，会以为功能没了）。参考手册与代码片段两个 tab 同一套行为。

**关键取舍：用高度动画，不用 `translationY`。** `translationY` 只是把行滑出视野，它占的高度还在，
下面的列表不会跟着上移 —— 看着像收起了，实际一点空间都没让出来。实测印证：收起后 `rvReferences`
顶边 740 → **578**，差值 162px 正好等于筛选行的高度（54dp）✓ **空间是真让出来的**。

**实现**：新增 `util/HideOnScroll.kt`（`RecyclerView.hideOnScroll(row)`，约 90 行）；
两处筛选行补 id `layoutFilterRow`；两个 fragment 各加一行接线。两个方向都避免布局突变
（收起动画结束才置 `GONE`；展开时先置 `VISIBLE` + height=0 再长回来）。

**自查 + 真机一共抓到两个 bug（都已修）**

1. **`ValueAnimator.cancel()` 也会回调 `onAnimationEnd`**（先 cancel 后 end）。原守卫是「引用是否
   还在」，而取消时引用还在 → **收起动画的收尾（置 `GONE`）会在展开途中被执行，筛选行凭空消失**。
   改成「先把引用摘掉再 cancel」。
2. **收起途中反向会把「展开高度」记成半截**：真机实测滑回后筛选行只有 55px（应 162px）、
   `rvReferences` 顶边 633（应 740），且**之后永远只展开到 55px**。改成**收起只在静止态启动**
   （动画进行中不动它），展开仍允许打断收起 —— 这样记下的高度永远可信。

**真机验证（LIO-AN00，用 uiautomator bounds 量）**

| 场景 | 参考手册 tab | 代码片段 tab |
|---|---|---|
| 初始 | 行 `[510,672]`（162px）、列表顶 740 | 行 `[452,620]`（168px）、列表顶 620 |
| 向下滑 | 行**从视图树消失**、列表顶 **578** ✓ | 行**消失**、列表顶 **452** ✓ |
| 向上滑回 | 行恢复 `[510,672]`、列表顶 740 ✓ | 行恢复 `[452,620]`、列表顶 620 ✓ |
| 快速上下反复 3 轮 | 停手后**完整展开**（不再是半截）✓ | — |

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors / 2353 warnings**（持平）。

**过程中的一次中断（如实记）**：做到一半手机的 USB 调试被关掉（Windows 只剩 WPD/MTP 接口，
`adb devices` 为空），按规矩**没验完就不提交**，代码在工作区停了一轮；设备恢复后补齐验证才落提交。

**残留 / 可扩展**：这套 `hideOnScroll` 是通用的，讨论区、文件分享、收藏、浏览历史等列表页同样
适用（各页的筛选行加个 id + 一行接线即可）。本次只做了用户提到的「学习资源」两页。

#### 第三十七批·修正（真机报「有显示BUG / 会闪」之后）

**怎么看出来的**：180ms 的动画用截图根本抓不到中间帧。把 `durationMs` 临时改成 **1500ms**
（和之前「给加载路径加 8 秒延迟看预加载态」同一招）再连拍，一次就看清了 —— 收起途中
**分类 chip 已经消失，而「默认」和排序图标还孤零零留在原地**，下面的「形状」行与列表项还错位重叠。

**根因**：第一版直接对筛选行**自己的高度**做动画。每一帧都是一次重新布局，行内较高的 chip 先被
压没，而 `gravity=center_vertical` 的排序按钮还撑着 —— 于是中间态就是花屏。

**修法**：

1. 结构上加一层只管「占位 + 裁剪」的 `FrameLayout`（`layoutFilterRowSpace`），真正的行
   （`layoutFilterRow`）是它唯一的孩子；
2. 动画前把**内容高度钉死**成 h，然后 `space.height` 在 h↔0 之间动画、`content.translationY`
   同步在 0↔-h 之间动画 —— 内容是「被裁掉」而不是「被压扁」，chip 与按钮同进同出；
3. 顺带加了**迟滞**（累积位移满 48px 才触发），避免滑动末尾的抖动让行反复收起/弹出。

**复验**：慢速版中途帧不再有游离的排序按钮与错位重叠 ✓；改回 180ms 后两页行为不变
（参考 740→578、片段 620→452，滑回恢复；快速反复 3 轮后完整展开）✓；门禁持平 ✓。

**方法收获（值得复用）**：动画类问题在真机上「看不清」时，**把动画时长临时放大一个数量级再连拍**，
比反复推理有效得多 —— 这次从「用户说会闪」到定位根因只花了一次改时长 + 一次抓帧。

#### 第三十七批·第二次修正（用户报「来回移动会存在问题」）

**根因**：`animate()` 里起点是写死的 `from = if (toCollapsed) h else 0`。而**展开可以打断正在播的
收起**（手势反向时），那一刻外层容器停在半截高度（比如 80px）——从 0 起播的话，第一帧会把高度
直接拽到 0 再长回来，来回滑动时看到的就是这一下闪。

**修法**：起点改成**当前高度** `space.height.coerceIn(0, h)`，方向只决定终点。打断时接着从半截
继续，不重头来。

**复验（180ms 正式版）**：快速往返 6 轮后 `行高=162 / 列表顶=740`（无半截、无卡死）✓；
再向下滑 → `GONE / 578` ✓；滑回 → `162 / 740` ✓。代码片段 tab 同样从 620→452→620 ✓。

**过程中的一次自伤（记下来）**：我给那个裁剪用的 `FrameLayout` 加 `tools:ignore="UselessParent"`
（lint 报它「多余」，但它是**必要的**冗余 —— 裁剪就靠它），却**忘了声明 `xmlns:tools`**，
资源编译直接失败（`AttributePrefixUnbound`）。已补上命名空间。
教训：`tools:` 前缀不是免费的，**新加一个前缀属性就要同时加命名空间**，改完必须立刻编译一次 ——
这次是被用户的下一句话打断在「已改但没编译」的状态里才发现的。

#### 第三十七批·第三次修正（用户报「缓慢上滑会闪」→ 最终定位为自激振荡）

**过程**：先按「定时补间与手指脱钩」改成按滚动增量成比例联动（跟手），抖动仍在；改成**加日志抓现场**
才看清真正机制。

**日志证据（关键）**：在 `onScrolled` 与「改高度」处加日志，慢速下拖时 `dy` 本该一路同号，实测却是

```
onScrolled dy=1  h=33 → applyHeight v=32
onScrolled dy=-2 h=32 → applyHeight v=34     ← 这一下不是用户推的
onScrolled dy=-1 h=34 → applyHeight v=35
```

每隔一帧冒出一个反向的小位移 —— **改高度 → RecyclerView 被改尺寸后自己回调 `onScrolled` 报告一个
反向位移 → 又被当成用户滚动去改高度**，形成自激。手指停住时没有真实滚动掩盖它，所以「停在某个位置
抖得更明显」。

**根治：让列表尺寸全程不变。** 结构上把筛选行（以及参考页跟在它下面的细分／子分类／形状三行）从纵向
流里摘出来，改成**浮在列表之上的浮层**：

- 浮层放在列表容器顶部，自带裁剪容器（滑出去的部分被裁掉，不会盖住上面的行）；
- 列表用**固定的** `paddingTop`（= 浮层高度）给它留位，`clipToPadding="false"`，条目从浮层下面滑进滑出；
- 收起／展开只是 `content.translationY` 的**纯位移**，一帧重新布局都没有 → 不存在回授，也比改高度省。

**同时修掉一个死角**：列表已经在顶部时，若浮层停在收起／半开态，向下拖**没有可滚动距离** —— 既没有
`onScrolled` 也没有滚动状态变化，浮层就永远不出来。现在 `onScrolled`、`onScrollStateChanged(IDLE)`
与「触摸抬起」三处都有「到顶必然展开」的兜底。

**验证（真机）**

| 检查 | 参考手册 | 代码片段 |
|---|---|---|
| 初始位置 | `chipAll` 顶 **528**（与原布局一致） | `chipAll` 顶 **464**（一致） |
| 向下滑 | chip 消失（浮层滑出） | 同左 |
| 向上滑回 | 回到 528 | 回到 464 |
| 拉到顶部 | 必然展开 ✓ | ✓ |
| **dy 序列（慢速拖动）** | **5,17,14,9,8,8,7,8,8,7… 严格同号** ✓（修复前是 +1/−2 交替 ✗） | — |

**三次修正的教训**：① 压扁式花屏（改行自身高度）② 打断式跳变（补间起点写死）③ **自激振荡（改尺寸
引发 RecyclerView 回调，又被当成用户输入）**。第三次不是某行代码写错，而是「改变某个 View 的尺寸」这件
事本身会产生输入假象 —— 做这类滚动联动效果时，先问一句：**我这次的改动会不会被系统当成用户输入回传给我**。

---

### 第三十八批（评论回复引用）

**起因**：用户提「做评论区的回复引用功能」。

**先摸现状 —— 这功能已经做了一半。** 数据链路早就是通的：`Comment.parentId`、`btnReply` →
`beginReplyTo()` → `sendComment(..., parentId)`，后端 `POST /comments` 一直在收 `parent_id`，列表也
已有回复缩进。**缺的正是「引用」本身**：回复只显示一行 `→ @某某`，看不到被回复的原文；写作时也看不到；
那行字点了没有任何反应。

**关键判断：不改后端、不加迁移、不需部署。** 依据（逐条核对过后端）：

- `GET /api/discussions/:id` 一次返回**全部**评论（`ORDER BY created_at ASC`，无 LIMIT/OFFSET，**没有分页**）；
- 这个响应**没有任何缓存**（唯一被缓存的评论相关接口是讨论列表 `edgeCache(120)`，且只带 `reply_count`）；
- 服务端只派生一层 `parent_author_name`（`LEFT JOIN comments pcm`），**不返回父评论正文**；
- 而客户端 `DiscussionDetailViewModel.threadedSort()` 本来就已经 `comments.associateBy { it.id }` 建过索引。

→ 父评论原文就在本地列表里，查表即可。给服务端加 `parent_content` 是多余的，还会牵动 `deploy_now.py`
的迁移登记（该脚本被 gitignore，只存在于本机）。

**做了什么（四处）**

1. `item_comment.xml`：删掉 `tvReplyTo`（那行 `→ @某某`），换成引用块 `layoutQuote` —— 3dp 左侧竖条 +
   `@作者` + 原文（2 行截断）。**刻意不加底色**：评论卡本身已是 `colorSurfaceVariant` + 16dp 圆角，
   块内再套一层有底色的块，深色下会叠出三层明度差（页面/卡片/引用），看起来像嵌套错误。
2. `CommentAdapter`：item 从 `Comment` 换成 `CommentRow(comment, parent)`。**这层包装是必要的**：
   DiffUtil 只比较 item 自身，若仍是 `Comment`，父评论被编辑（正文变了、子评论对象没变）时子项不会
   重绑，引用块会一直显示旧正文。顺带新增 `onQuoteClick` 回调与高亮状态。
3. `fragment_discussion_detail.xml`：输入区提示条从「一行 `正在回复 @X`」扩成两行（第二行 `「原文」`），
   整条可点。波纹走 `foreground` —— `background` 已被 `colorSecondaryContainer` 占用。
4. `DiscussionDetailFragment`：本地建表后 `submitList(map { CommentRow(it, parent) })`；
   `jumpToComment()` 按 `ConcatAdapter` 前两个适配器的条数补偏移再 `scrollToPositionWithOffset` +
   高亮 1.2s；**点引用块与点提示条走同一个函数**。

**三种状态**：父评论在列表里 → 可点跳转；父评论已被删除（服务端仍带 `parent_author_name`）→ 显示
「该评论已删除」且**不可点**（点了跳不到任何地方，留着可点状态只会让人以为点击失效）；两者都没有 → 整块 GONE。

**真机踩到的 bug（已修，附日志证据）：高亮不灭。** 加日志抓到：

```
adapter.highlight previous=null -> 18
jumpTo id=18 idx=0 offset=1 posted=1200ms
clearHighlight runnable fired          ← 1.2s 后确实执行了
adapter.highlight previous=18 -> null
```

状态翻转了、**视觉没变** → 子适配器的通知没让列表重绑。这个列表挂在
`ConcatAdapter(页头, 占位行, 评论)` 下，在**子适配器**里调 `notifyItemChanged` / `notifyDataSetChanged`
都不生效；改成由**根适配器**广播（`rvMain.adapter?.notifyDataSetChanged()`）后，`onBindViewHolder`
日志显示 1.2s 后 18/19/20/21 全部重绑、`highlight=false` ✓。

顺带解释了一个巧合：起初「高亮亮起来」看着是生效的 —— 那一下其实是 `scrollToPositionWithOffset`
引发的重绑，不是通知生效。**所以「一半生效」的现象要当成两条独立路径去查，别用一个解释糊过去。**

**验证（LIO-AN00；全部走只读路径，没有往生产库发任何评论）**

| 检查 | 结果 |
|---|---|
| 回复的引用块 | 显示 `@管理员` + 原文，竖条按两行文字撑满 ✓（含「回复的回复」多例） |
| 点引用块 | 列表滚到被引用评论、目标卡片高亮 ✓ |
| 高亮自动灭 | 1.2s 后恢复常态色 ✓（修复前一直亮着 ✗） |
| 点输入区提示条 | 同样跳转 + 高亮，且不丢回复目标 ✓ |
| 输入区预览 | `正在回复 @管理员` + `「哈哈，多谢」`，hint 变 `回复 @管理员` ✓ |

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors / 2356 warnings**。
（提醒：warnings 计数本身不稳 —— 同一份代码三次跑出 2353 / 2355 / 2356，别拿它当回归判据，只有
errors=0 是硬指标。）

**未做 / 残留**

1. **端到端「发一条真回复、看它自己带引用块」没做**：那需要往**生产库**写数据，用户没授权写生产。
   该路径的渲染代码与已验证的完全同一段，但确实没被真机跑过 —— **不等同于已验证**。
2. 父评论被 `ON DELETE CASCADE` 连带删除，所以「该评论已删除」这一态基本不可达，属防御分支。
3. `notifications` 里的回复通知不带评论 id（`related_id` 是讨论 id），所以点通知无法深链到具体评论；
   要做需给通知补字段。
4. 评论输入框仍没有格式工具条（讨论正文编辑器有 `> ` 引用按钮），本次未动。

#### 第三十八批·修正（用户：「我感觉点赞按钮下面有大量空白是吗」）

**用户看到的没错。** 真机 dump 量出来（1dp=3px）：单行评论卡 `[1536..1886]` = 350px = 116.7dp，
动作行 `[1706..1850]` = 48dp，而 `btnLike` 是 48dp 触控目标、里面只有 20dp 图标（上下各 14dp 内边距）。

| 位置 | 空白 | 来源 |
|---|---|---|
| 点赞图标**上方** | **18dp** | 动作行上间距 4dp + 按钮内边距 14dp |
| 点赞图标**下方** | **26dp** | 按钮内边距 14dp + 卡片下内边距 12dp |
| 图标底部 → 下一张卡顶边 | **34dp** | 再加卡间距 8dp |

**根因两条**：① 48dp 触控目标里只放 20dp 图标；② 卡片下内边距 12dp 是动作行上方间距 4dp 的
**三倍** —— 上下差 8dp，眼睛能直接看出来。

**改法**：`btnLike` 48→40dp、`padding` 14→10dp（图标仍 20dp，触控 40dp）；卡片内边距从
`padding="12dp"` 拆成**上 12 / 下 6**。行内其余按钮是 `layout_height="match_parent"`，会自动跟着
收到 40dp，**不必逐个改**。

**复量（同页同位置）**：卡片 `[1536..1844]` = **102.7dp（-14dp，-12%）**；图标上方 14dp / 下方 16dp
（**基本对称**，改前 18/26）；一屏条数 5.2 → **5.8 条**。门禁 `lintDebug` 0 errors ✓。

**残留（未做，属产品选择，不是漏掉）**

- 引用块仍是两行（39dp + 6dp 外边距）。合成一行 `@作者：原文` 可省 21dp，代价是原文只看得到一行。
- 首屏看不到评论的真正瓶颈是**页头 415dp**（分类 chip 32 + 标题 59.7 + 作者行 50 + 正文 **182** +
  评论区标题 44.3），占可视区 644dp 的 64% —— 正文是 Markdown 渲染，长贴更长。

---

### 第三十九批（评论楼中楼折叠：展开其余 N 条回复）

**起因**：用户问「评论折叠功能如何，可以做吗」。先把三种「折叠」按真实数据比了一遍，用户选①。

**为什么选①（打真实接口统计，全站 3 篇讨论）**：只有讨论 11 有量 —— 16 条评论 = **1 个根 + 15 条回复，
其中 11 条是「回复的回复」**，平均 52 字、最长 424 字，**15 条全挂在同一个根下**。展开态粗算
`103dp + 15×(103~160dp) ≈ 2200dp ≈ 3.4 屏`，收起后约一屏。长正文折叠（②）全站只有 1 条超 200 字，
收益低；整体评论区折叠（③）价值有限。

**规则**：每一楼显示根评论 + 前 [COLLAPSED_REPLY_PREVIEW]=2 条回复，其余折叠成一行
「展开其余 N 条回复」；展开后全部显示，末尾补一行「收起回复」。**折叠掉的回复根本不生成行**（不是
隐藏），长楼因此不参与布局与滚动。

**实现要点**

1. 行模型从「纯评论」升级为 sealed `CommentRow`：`Item(comment, parent)` /
   `Expand(rootId, hiddenCount, expanded)`。DiffUtil 身份键：评论用真实 id，折叠行用**负的根 id**
   （不与真实 id 冲突）。
2. 两种 viewType + 新布局 `item_comment_expand.xml`（40dp 高、`ic_expand_more/less`、colorPrimary）。
   左缘在代码里按 `comment_card_inset + comment_reply_indent` 对齐回复卡 —— 与评论卡的缩进逻辑放在
   同一处，避免两套算法各自漂移。
3. 楼根判定复用 ViewModel `threadedSort` 的同一套语义（追到「没有父评论、或父评论不在本篇里」），
   所以**孤儿评论自己就是一楼的根**，不会掉出行列表；带记忆化与环保护。
4. **折叠状态存 fragment 字段 + `onSaveInstanceState`**：fragment 实例随配置变更重建、字段留不住，
   不存的话每次旋转所有楼都会弹开。

**必须一起处理的耦合（上一批的引用跳转）**：目标可能正被折叠在某一楼里 —— 跳转前先展开它所在的楼；
而且 **`submitList` 是异步 diff，滚动必须放进 `submitList(rows, callback)` 的回调里**，否则算的是旧
折叠列表的下标。查找下标也改成按**折叠后的行列表**（`commentAdapter.currentList`），不再是原始评论列表。

**验证（LIO-AN00，几何全部真机 dump 量的）**

| 检查 | 结果 |
|---|---|
| 收起态 | 根 + 2 条预览 + 一行折叠行；**折叠行 `x[108..1152]` 与回复卡左缘一致** ✓ |
| 折叠掉的回复 | 确实不在列表里 —— 滚到底就是折叠行，其后没有内容 ✓ |
| 展开 | 点折叠行 → 变成「收起回复」（标签宽 52dp；折叠态是 113dp）✓ |
| 收起 | 点「收起回复」→ 回到「展开其余 13 条回复」（计数 = 15−2 ✓）✓ |
| **跳转自动展开** | 收起状态下点输入区提示条 → 楼自动展开，目标评论**落在首屏第一行**（`CardView y[291..836]`，正文与提示条里的引用一字不差），**且卡片被高亮**（截图：目标卡 `colorSecondaryContainer`、其余两张 `colorSurfaceVariant`）✓ |

**补上一批一处说得太宽的判断**：我在第三十八批写「子适配器的通知不会让列表重绑」—— 这句不准确。
`submitList`（DiffUtil 发出的范围通知）**一直是生效的**（整个引用功能就靠它渲染），不生效的是**手动**
`notifyItemChanged` / `notifyDataSetChanged`。已在 `CommentAdapter.highlight()` 的注释里改成这个措辞，
免得下次据此推出错误结论。

**残留 / 未做**

1. 预览条数 2 与「收起」入口放在楼尾都是拍的（主流也这么做）。若想要「根评论卡里一行『查看全部 15 条
   回复』」是另一种形态，需另做。
2. 评论输入框仍无格式工具条（讨论正文编辑器有）。
3. 端到端「发一条真回复」仍未做（要写生产库，用户未授权）。

#### 第三十九批·修正（用户：「折叠咋还省了几个没折叠」→ 选 0 = 全部折叠）

**那不是漏折叠**，是我刻意留的「预览 2 条」（`COLLAPSED_REPLY_PREVIEW = 2`）：讨论 11 是 16 条 =
1 个根 + 15 条回复，所以收起态是「根 + 前 2 条 + 展开其余 13 条」。留预览的理由是给上下文（微博、
知乎都留），但**全折叠同样是主流**（B 站就是「共 N 条回复，点击查看」）—— 属于产品口味。

**用户选 0，改动两处**

1. `COLLAPSED_REPLY_PREVIEW = 0` → 收起态只剩根评论。
2. **文案随之要改**：`Expand` 行多带一个 `previewCount`，`previewCount == 0` 时写「**查看全部 N 条
   回复**」而不是「展开其余 N 条」—— 一条预览都不留时「其余」就等于「全部」，原措辞是错的。

**复验（改用 Python 解析 dump，判据是内容哈希）**

```
[start]      查看全部 15 条回复   commentRows=1      ← 收起态只有根评论那一张卡
[expanded]   label=(off-screen)  回复按钮 3 个        ← 展开成功
[collapsed]  查看全部 15 条回复   commentRows=1      ← 收起成功
[after-jump] label=(off-screen)  commentRows=3
             firstComment=len=71 sha=1ec31b45        ← 与回复目标正文哈希一致
jump landed on target: True       thread expanded: True
```

即：**收起态只有根评论 + 一行「查看全部 15 条回复」；点回复提示条能自动展开该楼、把目标滚到首屏
第一行**（`PREVIEW=0` 之后这条路径被触发得更频繁，所以专门重跑了一遍）。

**工具教训（值得记）**：中途三次得出「折叠/展开点了没反应」的错误结论，根因全在**我自己的
PowerShell 辅助函数**——PowerShell 会把**单元素数组自动展开**，于是 `$nodes[0]` 变成了一个整数、
`$nodes[0][0]` 变成 null，`input tap` 实际点在 (0,0)。改用 Python + ElementTree 解析 dump 后
（单元素不再有惊喜），同一套操作一次通过。**另外** `sk_dump.ps1` 还踩过「pull 失败时沿用上一次的
XML」→ 读到陈旧快照；已加「先删本地文件再 pull」。
两个坑都在工具层，却连续两次被误读成产品 bug —— 排查交互问题时，**先确认观测手段本身可信**。

#### 第三十九批·第二次修正（预览 3 条试过又撤掉；入口文案改 B站 口径）

**起因**：用户「参考B站评论折叠方式」。查了 [bilibili-API-collect 评论区文档](https://gitea.s1f.ren/shiran/bilibili-API-collect/src/branch/master/docs/comment/readme.md)
—— B站 把折叠策略写成了接口字段，可作硬依据：

| B站 字段 | 值 | 含义 |
|---|---|---|
| `replies` 数组 | 最多 3 条 | 收起态预览的回复（"仅可嵌套一层"，按热度排序） |
| `rcount` | 数字 | 回复总数 |
| `reply_control.sub_reply_entry_text` | `共 xx 条回复` | 收起态入口那行的文案 |
| `reply_control.sub_reply_title_text` | `相关回复共有 xx 条` | 点进去那个列表页的标题 |
| `content.max_line` / `reply_control.max_line` | **6** | "用于控制折叠状态下的最大显示行数" |

**试了 3 条预览，又按用户要求撤掉**：「预览三个不搞」。最终 = **全折叠 + B站 的入口文案**。

- `COLLAPSED_REPLY_PREVIEW` 3 → **0**，收起态只剩根评论。
- **文案保留 B站 口径**：`共 N 条回复`（总数，不是"还剩几条"）。`Expand` 行因此只带 `totalCount`，
  上一版那个 `previewCount` 字段直接删掉了 —— 顺带简化了代码。

**真机复量**：收起态可见评论行 = 根评论（无预览）；入口标签 **8 字符 / 74dp** = 「共 16 条回复」✓；
展开仍是「收起回复」，**跳转自动展开**照旧 ✓。

**顺带体检出的 UI 问题（未修，等决定）**：一条 **419 字**的评论（贴了一整段 Telegram 聊天记录）
**单卡占 533dp ≈ 0.83 屏**（全站 17 条里 >200 字 1 条、>400 字 1 条）。这正是 B站 `max_line = 6` 的场景。
拟用方案：`maxLines = 6` + 溢出判定用 **`layout.getEllipsisCount(最后一行) > 0`**（`maxLines` 生效时
`lineCount` 恒为 6，测不出溢出，必须看省略号）+ 「展开/收起」做成**浮在正文右下角的小胶囊**（不占布局，
出现/消失不会让列表跳）。用户尚未表态。

#### 第三十九批·第三次修正（用户：「共多少条回复那里占用不少，而且往下翻必须翻到底才能折叠」）

两条都成立，**第二条是设计缺陷**（入口行放在楼尾，16 条回复的楼要滑 2.7 屏才够得着「收起」）。

1. **入口行 40 → 32dp**：`minHeight` 40→32、`paddingHorizontal` 12→8、图标 18→16、`marginBottom` 8→6。
   实测占可见区 **6.2% → 5.0%**，与它承载的信息（一行 13sp 文字 + 一个小箭头）终于相称。
2. **入口行从楼尾挪到楼顶**（紧贴根评论），**展开与收起共用同一行** —— 行位置不随状态变化，
   点开/收起都不用再滚动。判据从「下一条属于别的楼」简化成「这是楼头」，代码反而短了
   （`nextRoot` 那段判断删掉，`withIndex` 也不再需要）。

**复量（真实 dump）**：收起态 `根评论 102.7dp → 入口 32dp → 下一条评论`；点入口后**入口仍在
`y[1868..1964]` 原位未动**，标签变成 4 字符「收起回复」，回复出现在它**下面** ✓。

**当前界面空间占用（同一份 dump 量的，可见区 644dp）**

| 块 | 高度 | 占比 |
|---|---|---|
| 页头（标题/作者/正文 Markdown/评论数行） | 415.0dp | 64.4% |
| 根评论卡 | 102.7dp | 15.9% |
| **折叠入口「共 16 条回复」** | **32.0dp** | **5.0%** |
| 下一条评论 | 80.3dp+ | 12.5% |
| 空隙 | 14.0dp | 2.2% |

结论没变：**页头仍是首屏最大头（64%），其中正文 Markdown 一项就 182dp**；评论卡与入口加起来
才 20%。要继续腾地方，该动的是页头正文折叠，不是评论区。

---

### 第四十批（项目分析器：解析离开 UI 线程 + 预览配色归位）

**起因**：用户「优化项目分析器功能」。先派子代理盘全貌、**再逐条回代码复核**（复核确认报告的每条
承重结论都成立）：全包只有**一个**协程（zip 备份 `:1746-1748`），扫描与解析全在 UI 线程；
`progressBar` 与解析在**同一帧**里 VISIBLE→GONE（那个加载圈从来没有机会画出来）；无缓存、无 ViewModel，
配置变更（`restoreUiStateAfterLoad`）与返回重进都全量重跑。

**本次做的（P0+P1，只动 `ProjectAnalyzerFragment.kt`，+108/−58）**

1. `loadProjects()` → **`scanProjects()`（IO 扫描 + 读 project JSON）+ 主线程渲染**；补 `_binding == null`
   守卫，加载圈从此真的可见。
2. `analyzeProject()` → **`parseProject()`（IO）+ `renderProject()`（主线程）**。渲染体逻辑一行没改，
   靠 `ParsedProject` 把结果带过去；顺带把旧版对**同一个目录的两遍 `listFiles`**（`:290-295` 找 logic、
   `:444-450` 找 view）合并成一次。
3. `containerColors` 6 个硬编码饱和色（`0x302196F3` 等，19% 不透明）→ **M3 角色阶梯**
   （`colorSurfaceContainerLow/Normal/High/Highest` + `colorSurfaceVariant`，按嵌套深度取，叠 50% 透明）
   —— 清掉 UI_REVIEW 里挂了很久的那条 TODO。

**真机验证（LIO-AN00）**

| 检查 | 结果 |
|---|---|
| 分析器主页 | **「找到 13 个项目」**，13 张项目卡齐全 ✓（IO 扫描结果照常回主线程渲染） |
| 打开项目（CountdownTimer） | **38 积木块 / 15 种类 / 3 组件 / 13 事件** + TOP 积木榜（`addSourceDirectly` 16、`definedFunc` 5、`timerCancel` 3…）✓ **拆分没有丢数据** |
| 构建 / 门禁 | `assembleDebug` ✓、`lintDebug` **0 errors** ✓ |
| **P1 预览配色** | **未肉眼确认**：点「可视化预览」那一步的点击+滑动组合最终停在项目列表页，没截到预览区。改动只是把色表换成主题角色（沿用同一套 `resolveColorWithAlpha`），编译与门禁通过，但**没看到渲染效果**，下次补一眼 |

**过程中断（如实记）**：中途手机 USB 调试又关了（Windows 只剩 WPD、`adb devices` 为空），按规矩
**压住提交**；用户重开后继续。另有一次是我在 ADB Interface 枚举完成前就查了 PnP、误判成"没连上"
（其实已连），白等一轮 —— 设备类问题**要间隔几秒连查两次**再下结论。

**未做（P0 剩余 + P2/P3）**

1. `showCrossProjectStats()`（`:1507`，`allProjects.forEach` 里对每篇再 `parseLogicFile` 一遍）仍在主线程。
2. `LogicVisualizerFragment` / `ProjectResourceFragment` 的 `loadProjects()` 同一模式未动，另有三处主线程
   `BitmapFactory.decodeFile`。
3. P2 抽 `SkProjectRepository` + 内存缓存（旋转/返回不再重解析）；P3「未使用资源」检测 ——
   `scanResources` 目前只被逻辑可视化调用做列表展示，**全项目没有任何"资源是否被引用"的交叉比对**。

#### 第四十批·修正（审查自查 + 继续：跨项目统计入 IO，并修掉我自己引入的回归）

**审查自查发现一处疏漏（自己改的代码）**：两个协程体里**没有异常兜底**。解析若抛出未捕获异常，
`lifecycleScope` 的协程会带着异常结束 → **崩应用**；而且 `progressBar` 已经置 VISIBLE，会**永远转下去**。
已改成 `try { withContext(IO) { … } } catch (e: Exception) { null }` + 失败 Snackbar + 关掉进度条。

**继续做的一条**：`showCrossProjectStats()` 拆成 `scanGlobalStats()`（IO，遍历 13 个项目再各解析一遍
logic）+ 主线程渲染，全页最重的一条路径离开 UI 线程。

**真机抓到我自己引入的回归（重要）**：跨项目统计入口点开只看到项目列表，什么都没发生。
根因：`onViewCreated` 里 `loadProjects()` **原本是同步**的，所以紧随其后的三条路径
（`show_global_stats` 导航参数、`restoreUiStateAfterLoad()`、`show_backup`）都能读到已经填好的
`allProjects`；我把扫描改成 IO 之后它们**抢跑**，读到空列表后静默 return（`:1558` 的
`if (allProjects.isEmpty())` 只弹一句 Snackbar）。

修法：加一层排队 —— `runAfterProjectsLoaded { … }` 把「扫描完成前到来的请求」存进 `pendingAfterScan`，
扫描结束时先填 `allProjects` 再执行它。**教训**：把同步函数改成异步时，要把它**所有隐式的前置条件**
（这里就是"调用即完成"）一起找出来，否则改成异步等于悄悄改了别人的契约。

**复验（LIO-AN00）**：工具箱 → 全局统计 → **「全局统计 (13 个项目)」** 正常渲染：
**2223 总积木块 / 134 种类 / 13 项目 / 9 组件** + TOP20（`addSourceDirectly` 372 次、`setTypeface` 154 次、
`mapPut` 132 次…）✓；分析器列表与单项目分析（38/15/3/13）仍正常 ✓。

**仍未做**：`LogicVisualizerFragment` / `ProjectResourceFragment` 的 `loadProjects()`（同模式，另有三处
主线程 `BitmapFactory.decodeFile`）；P2 仓库+缓存；P3 未使用资源检测。**P1 的预览配色仍未肉眼看**。

#### 第四十批·第二次审查（盯着刚提交的那批再查一遍）

**查过没问题的**

1. **IO 安全**：`parseLogicFile`（`:715-981`）与 `parseViewFile`（`:1152-1262`）的函数体内**没有任何**
   `requireContext` / `resolveColor` / View 构造 —— 那些都在它们之后的绘制辅助函数里（`createCard` `:982`、
   `buildVisualPreview` `:1263`、`buildViewTree` `:1573`）。所以「解析进 IO」不会踩到只能在主线程碰的东西。
2. **`restoreState` 全链路**：`analyzeProject → renderProject`，三个使用点（`:560/:599/:662`）都拿到了值。
3. **空项目路径**：扫描返回 null/空时，仍会先跑排队的那件事（`showCrossProjectStats` 自己会 Snackbar
   「没有找到项目」），再显示空态 —— 顺序正确。

**查出来要补的（已改，2 行）**：`pendingAfterScan` 与 `projectsScanned` 在 `onDestroyView` 里**没有复位**。
不复位的话，视图销毁时留在队列里的请求（捕获的是旧视图上下文）可能在下一轮扫描完成后被误触发。
现在两者一起清掉：旋转后请求会重新排队、随新的一轮扫描完成再执行。

**这条没验证完（如实记）**：它的意义就在旋转路径上，而我在设备上连续几次「点工具箱卡片 + 转屏」把页面
状态带偏了（一次停在工具箱页、一次停到首页），**没测到旋转恢复**。改动本身只有两行清字段、没有可失败的
分支，但仍属**未验证**，下次补测一次旋转。

**顺带记一个操作教训**：`.sketchware` 工具页的 `cardGlobalStats` 在页面**最底部**（卡片中心 y≈2213，
紧贴内容区下沿 2223），点击命中很不稳 —— 连续两次「点了没反应」其实是**没点中**，不是功能坏。要复测这个
入口，先 `uiautomator dump` 拿到实时 bounds 再点，别复用上一轮的坐标。

#### 第四十批·继续（项目资源管理页：同样搬出 UI 线程 + 删掉一处很贵的死代码）

**先审查再动手**：翻了 `ProjectResourceFragment` 的加载路径与 `onViewCreated` —— 后者末尾就一句
`loadProjects()`，**后面没有任何依赖"已完成"的逻辑**（没有 restore 路径），所以这次**不需要**分析器里那层
排队；`LogicVisualizerFragment` 则**有** `restoreUiStateAfterLoad()`（`:214/:356`），拆分时必须照分析器加队列
（已记进待办，别漏）。

**改了两处**

1. `loadProjects()` → `scanProjects()`（IO 扫盘 + 读 project JSON）+ 主线程渲染；补 `_binding == null` 守卫
   与 `try/catch`（读盘异常不能让协程崩）。
2. **删掉死代码 `hasRes`**：旧代码为每个项目算"有没有资源文件"，**算完从来没用过** —— 代价是每个项目
   **5 次 `listFiles`**（13 个项目 = **65 次目录扫描**），而且全在主线程。连同只为它存在的 `resBase` 局部
   变量一起删。**这就是"死代码不只是难看，它是白烧的 IO"** 的实例。

**验证（LIO-AN00）**：项目资源管理页 **「找到 13 个项目」**、13 张卡片齐全，且每张卡的资源计数照常显示
（`DarkAuth Motion / ID: 731 · 1图标`、`Dashboard / ID: 702 · 1图标`…）—— 计数来自另一条路径，删掉 `hasRes`
不影响它，**反证删的确实是死代码** ✓。

**仍未做**：`LogicVisualizerFragment.loadProjects()`（要注意它有 restore 路径，需队列）；三处主线程
`BitmapFactory.decodeFile`（`ProjectResourceFragment:357/654`、`LogicVisualizerFragment:1024`）；P2 仓库+缓存；
P3 未使用资源检测；以及两个**改了但没看过**的项（P1 预览配色、`onDestroyView` 复位）。

---

### 第四十一批（文章页审查：四个必修项）

**起因**：用户「先做文章页面界面审查」。

**做法**：先派子代理静态盘点（10 个布局 + 8 个 Kotlin 文件，逐条 `file:line`，落盘
`ARTICLE_UI_REVIEW_FACTS.md`），我再**逐条回真机复核**。复核推翻了静态清单里的两条结论，并且
查出两个只有浅色模式才暴露的图标缺陷 —— 测试机常年深色，所以它们从 `0a40dd7`（v1.1.0 首次提交）
活到现在。

**四个真缺陷与修法**

| # | 缺陷 | 根因 | 修法 |
|---|---|---|---|
| ① | **点赞图标在浅色模式下不可见** | `ic_favorite.xml:8` 是 `@android:color/white` 且**不自带 tint**，`fragment_article_detail.xml:280` 也没给。同一个底栏里 `ic_bookmark_outline/filled` 却自带 `?attr/colorOnSurface` —— **两套约定并存**才是根因 | 图标补 `android:tint="?attr/colorOnSurfaceVariant"`（与同排 14dp 统计图标 `ic_download` 对齐）；底栏那一处显式 `app:tint="?attr/colorOnSurface"`（与收藏/分享对齐） |
| ② | **空态图标从来没画出来过** | `ic_empty.xml:7` 的 `fillColor` 是 `@android:color/transparent` 且无 tint → 该 vector 渲染为空。`git log --follow` 确认**首次提交起就是透明的，不是回归** | 换 inbox 字形 + `?attr/colorOnSurfaceVariant` + `alpha=0.3`，与 `ic_empty_comment`/`ic_empty_notification` 完全同构 |
| ③ | **列表错误态会叠在卡片上** | `fragment_article_list.xml` 的 `layoutError` 没有背景，而详情页同一个块有 `?attr/colorSurface`（`:227`） | 补同一个背景 |
| ④ | **空态文案写死「该分类暂无文章」** | 布局写死、代码只切可见性；但「查看全部」入口进来的是**全量**列表（`currentCategoryId == null`） | 按入口设文案：全量 →「暂无文章」，分类 →「该分类暂无文章」 |

一处连带：首页错误块用的也是 `ic_empty`（把「空」图标用在「加载失败」上），② 修好后它会**突然变得
可见**，因此换成 `ic_error_outline`。

**验证（LIO-AN00；① 深浅两个模式各截一次，②③④ 用临时包强制显示后截图，看完即还原）**

| 检查 | 结果 |
|---|---|
| ① 浅色 · 文章详情底栏 | 「♥ 点赞」图标可见（修前该位置**空白**，而同排收藏/分享/讨论都正常）✓ |
| ① 深色 · 同上 | 图标同样可见，无回归 ✓ |
| ② 空态图标 | 逐行像素扫描确认是**描边托盘 + 底部 V 形缺口**（`####....#####`），不是实心块 ✓ |
| ② 深色 | `colorOnSurfaceVariant@30%` 在深色下同样成立 ✓ |
| ③ 错误块 | 强制显示后**完整遮住两张卡片**（修前会透出卡片）✓ |
| ④ | 「查看全部」→「暂无文章」；分类入口 →「该分类暂无文章」✓ |

**验证手段（值得记）**：空态与错误态在当前数据下**无法通过正常入口触发**（全站只有 1 个分类、
2 篇文章），所以用一个临时包把它们强制 visible 画出来截图，**截完立即还原并重跑门禁**；临时改动全程
带 `// TEMP-VERIFY` 标记，提交前用 `Select-String` 复查无残留。
同一个陷阱连踩两次：把 `layoutEmpty`/`layoutError` 的 XML `visibility` 改成 visible **也没用** ——
代码里的 `fadeOut()` else 分支会立刻把它设回 `GONE`。**强制显示必须同时中和代码路径。**

**复核推翻的两条静态结论**

1. 静态清单称「`layout_empty_state.xml` 用 `ic_empty` 72dp alpha0.3」—— 实际该文件**全项目零引用**
   （`ivEmptyIcon` 连 `android:src` 都没有），是死布局。
2. 静态清单称「底栏被挤压变形、标签 `bounds=0`」—— 实际 app 是 edge-to-edge（`dumpsys window`：
   MainActivity 窗口 `[0,0][1176,2400]`），uiautomator 报的 `0..2301` 是裁掉系统栏后的**可见区**；
   `0dp + weight=1` 写法正确，192+6+1959+144 = 2301 自洽。**不是布局错误** —— 只是底栏最后 33dp 落在
   手势条区域（`navigation_mode=100`），三键导航下才会被系统栏盖住，属全局设计决策。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors / 0 fatal**。

**残留（本次刻意不做）**

1. **点赞没有「已赞」态**（收藏有完整三件套：实心图标 + 文案 + 主色）。根因在后端：`GET /api/articles/:id`
   的响应不含 `liked`，客户端进页面时无从知道是否已赞。纯客户端只能做到「本次停留内记住」，
   要跨会话正确需后端补字段 —— **属后端改动，需单独授权部署**。分享/代码片段详情同构。
2. 一致性收敛另开一批：详情标题 24sp vs 分享/片段 20sp、条目标题 16 vs 15sp、chip 内边距 10/3 vs 8/2、
   空态文案 14 vs 16sp、`ArticleDetailViewModel.isLoading` 从未被观察（死代码）。
3. 更正 `UI_REVIEW.md:288`：记的 `item_article_simple.xml:35` = 10sp **已过时**，现值 11sp。

---

### 第四十二批（审查续：真机补齐管理页与编辑器，修 5 项）

**起因**：用户「审查继续」→ 补完上一批没上设备的入口（管理中心 → 文章管理 → 编辑器四态 →
列表骨架），报出问题 → 用户「修复」。

**新查到的真 bug（本批主项）：编辑器预览态文字重叠**

`applyPreviewMode()`（`ArticleEditorFragment.kt:217-228`）藏的是**内层 EditText**：

```kotlin
binding.etContent.visibility = View.GONE      // ← 只藏了输入框
```

而 `etContent` 是 `TextInputLayout`（id `layoutContent`，带 `android:hint` + `counterEnabled` +
`helperText`）**唯一的孩子**。输入框没了 → 框体塌缩成一条，但浮动标签、helper、计数器仍照原位布局，
三者挤进 30dp 的带里。真机截图（编辑态与创建态都复现）：

```
支持标题、列表、引用、代码块等 Markdown      0/50000
在此输入 Markdown 内容...                            ← 浮动标签压在上面
```

dump 佐证：helper 落在 `y[1413..1504]`（被挤成**两行**，30.3dp）、counter 同高，
而 `tvPreview` 从 `y[1566]` 才开始。

**修法**：改藏 `binding.layoutContent`。安全性已核 —— `renderPreview():241`、`saveArticle():266`、
`currentDraftState():324` 都只是**读** `etContent.text`，GONE 状态照常可读。
顺带把 Markdown 语法速查行在预览态一并收掉（新加 id `tvMarkdownHint`）：框体不再占位后，
它会正好夹在统计行与预览正文之间，而它是编辑辅助信息。

**其余四项**

| 项 | 现状（真机量的） | 修法 |
|---|---|---|
| `btnPreview` 触控高 | **105px = 35.0dp**（`minHeight="0dp"` + 上下 4dp padding），同屏其它按钮都是 48dp | `minHeight="40dp"`（与第三十八批定的 40dp 触控下限一致） |
| 管理页 vs 列表页正文左缘 | **28dp vs 32dp**（同为 16dp 卡片内边距的 item，差在 RV padding 8dp vs 12dp） | **6 个管理页统一 8→12dp** → 文章管理 28→32dp，与文章列表页对齐 |
| 管理中心行标签 | 文章管理 / 分类管理 / 讨论管理 / **代码片段** / 分享管理 | 改「**代码片段管理**」—— `nav_graph.xml:359` 与 `fragment_snippet_manage.xml:17` 本来就叫这个 |
| `ArticleListFragment.kt:52-57` 注释 | 称列表查询**不含** `a.content`、预填是"刻意被拒" | 与后端实际相反（`articles.ts:21-26` 带着该列，注释还写着「别把这一列删掉」，第二十批）→ 重写 |

**验证（LIO-AN00 深色模式，逐条 dump 量）**

| 检查 | 改前 | 改后 |
|---|---|---|
| 预览态文字带 | helper 两行 + 浮动标签重叠，30dp 带里塞三个元素 | **全部消失**；`tvPreview` 上移到 `y[1400..1529]`，与统计行之间 24px（8dp）✓ |
| 切回编辑态 | — | `etContent` 回到 `y[1416..1992]`、按钮文案回「预览」✓ |
| `btnPreview` 高 | 105px = 35.0dp | **120px = 40.0dp** ✓ |
| 文章管理 `tvTitle` 左缘 | x=84px（28dp） | **x=96px（32dp）**，与文章列表页一致 ✓ |
| 讨论管理 `tvTitle` 左缘 | （未实测，见自查 §3） | 90px ✓ |
| 代码片段管理 `tvTitle` / 账号管理 `tvAvatar` 左缘 | （未实测，见自查 §3） | 均 90px ✓ |
| 管理中心行标签 | 代码片段 | **代码片段管理** ✓ |

**顺带白捡一个验证**：转场中间帧抓到了**列表骨架屏** —— 5 张骨架卡（chip 块 + 标题条 + 摘要条 +
统计行），几何与 `item_article` 一致 ✓。详情页骨架没抓到：从列表进详情时**入场帧就已有正文**
（预填生效，见上表最后一项），当前数据下这条路径不走骨架。

**查过、但判断为「不该改」的两条**

1. `validateBeforeSave()` 的三个 error 分支**不可达**：`updateFormState():338` 的 `canSave` 要求
   标题 + 分类 + 正文全非空，按钮在那之前是 `isEnabled=false`。用户实际看到的是**禁用按钮 +
   上下文提示**（`tvDraftMeta` 三态：「先填写标题，让文章主题更明确」/「请选择文章分类，方便内容归档」/
   「正文不能为空，至少补充主要内容」），比弹错误文案更好 —— 保持。
2. `btnPreview` 在内容为空时用 `alpha=0.6` 表示"不可用"但**仍可点**（点了显示「暂无可预览内容」）。
   视觉与行为不一致，但结果无害，属刻意引导 —— 保持。

**工具补充**：原有 dump 脚本只给 id/bounds，看文案得靠新写的 `%TEMP%\sktext.py`（ElementTree 遍历 →
输出「文本 + bounds + dp」到 UTF-8 文件）。**PowerShell 直读中文会乱码**（`Get-Content` 按 ANSI 解码
无 BOM 的 UTF-8），所以结果一律落文件再用读取工具看 —— 与第三十九批那条「先确认观测手段本身可信」
是同一类教训。

**门禁**：`:app:assembleDebug` 通过；`:app:lintDebug` **0 errors / 0 fatal**。

**生产数据零写入**：这两批的真机操作全程只读 —— 唯一一次点「保存文章」命中的是**禁用**按钮
（`saveArticle()` 验证不过直接 return），编辑器里未输入任何字符，因此也没留下草稿。

#### 第四十二批·自查（回审最近两批改动 `32f7253` + `edeb447`）

**审法**：不看结论、只看证据 —— 逐条回到代码/资源/真机，专找「我声称过但没验过」的地方。
共查 6 项：**0 处代码回归**，但**发现我自己 3 处文档表述不实**（已改）。

**1. 预览态的旋转恢复路径**（我改了 `applyPreviewMode()` 的语义）—— 安全。
`isPreviewMode` 在 `:83` 从 bundle 恢复，`:113` 无条件调用 `applyPreviewMode()`，顺序正确 → 旋转后
`layoutContent` / `tvMarkdownHint` / `tvPreview` 三者可见性一致。另两处 `renderPreview()`
（`:132` 预览态下改正文、`:180` 数据到位）也都带 `isPreviewMode` 守卫。

**2. `ic_favorite` 是共享 drawable，7 个调用点我原先只在设备上看过 1 个** —— 补验后：**改对了，
而且顺带修好了两处原本隐形的图标。**

| 调用点 | 结论 |
|---|---|
| `fragment_article_detail.xml:280`（20dp） | ✓ 浅深两模式均可见（第四十一批已验） |
| `item_snippet.xml:117`（14dp + alpha .4） | ✓ **浅色下已是清晰的灰色实心心形 + 数字**（截图 `♥ 1` / `♥ 0`）—— 改前是白底白图，等于不存在 |
| `fragment_snippet_detail.xml:246`（MaterialButton `app:icon`） | ✓ 可见：图标在**按钮最左端**、标签居中。（像素探针一开始报"没探到"，是我扫错区间 —— 图标不挨着文字，而是贴按钮左内边距；这种"没探到"要先怀疑探针） |
| `item_share.xml:126`、`fragment_share_detail.xml:275` | **无法验证**：分享列表当前**没有数据**（`tvEmpty`「暂无文件分享」），页面渲染不出行；结构与已验的片段版完全同构 |
| `item_share_manage.xml:90`、`item_snippet_manage.xml:50` | 自带 `app:tint`，与本次的默认 tint 同值 → 无变化 |

兜底判断依据：新旧差是「硬编码白 → 主题角色色」，在已知的四种底色（页面 surface / 卡片 / 色调按钮 /
色块）上**只会提高对比度，不可能把原本可见的变成不可见**。

**3. 「6 个管理页内边距都改对了」—— 实测 5/6，剩 1 页数据为空测不了。**
真机左缘：文章管理 **96px**（改前 **84px**，真·前后对照）、讨论管理 / 代码片段管理 / 账号管理 / 版本发布
**均 90px**。后四页的「改前值」只能按「该文件本次仅改 RV padding 8→12dp（= +12px）、item 布局未动」
**反推**为 78px，**不是实测** —— 原文表格把讨论管理的改前值写成 78px，属不实，已改为「未实测」并补上另三页。
`分享管理` 仍未测：当前无数据，连行都渲染不出来。

**4. 「全 app 10 个列表页都是 12dp」—— 说大了，已删。**
写了 `%TEMP%\rvpad.py` 扫全部 **37 个** `RecyclerView`/`ViewPager2`，结果：

- **6 个管理页现在都是 12dp** ✓（本次目标达成）；
- 12dp 的另有 `article_list` / `bookmark_list` / `reading_history` / `category_icon_picker` /
  `logic_visualizer` / `project_analyzer` / `project_resource(rvProjects)` / `reference_search`；
- **`category_manage` 根本没有 `android:padding`**（静态清单记的「12dp」是误读，它靠 item 自身边距），
  所以原文"4 个列表页 12dp"实为 **3 个**；
- 其余列表（讨论/片段/分享列表、通知、参考、关注…）**都没有 RV padding**，靠 item 边距与装饰；
  另有 `project_resource(rvResources)` = 8dp、`search(rvResults)` = 16dp。

→ 准确说法是「**6 个管理页 + 3 个列表页共 9 页同值 12dp**」，不是"全 app 统一"。

**5. 顺带发现（本次未改，低优先级）**：同一份扫描显示，**恰好是本批改的 4 个管理页没有设
`clipToPadding`**（article / discussion / snippet / user manage），而 app 里其余列表几乎全是 `false`。
影响很小（12dp 内边距在滚动时是"留白"还是"内容可滑入"），属同族不一致，可并进一致性批次。

**6. 文档级核对**：`item_article_simple.xml:35` 确认 **11sp** ✓（第四十一批对 `UI_REVIEW.md:288`
的更正成立）；`ArticleListFragment.kt` 的新注释与 `articles.ts:21-26` 一致 ✓。

**结论**：两批代码未发现回归或新引入问题；要改的是**我自己的三处表述**（把反推值写成实测、
`category_manage` 并非 12dp、"全 app 统一"说大了），已在本节与上文表格中改正。

---

## 九、剩余待办

> 「第一批」到「第十四批」均已完成。「第十五批」是盘点结论，未执行。

**待产品决策（会改变外观，需先定方向）**

1. ~~图标底板统一尺寸~~ —— **已完成**，见第十六批（统一为 40dp）
2. **间距 / 字号体系化**：把 25 种 padding、21 种 margin、15 种字号收敛到 4dp 基准的 spacing scale 与 type scale。触及 100+ 布局，需分批 + 逐批真机验证
3. 卡片圆角 16dp / 20dp 两套约定的取舍；并先清掉 `fragment_cache_manage.xml`、`fragment_theme_selection.xml` 两个文件内的混用

**结构一致性（已评估，明确不做）**

1. 其余约 20 处 24dp–80dp 的 `MaterialCardView`+单 `ImageView`：角色各异（快捷卡片内图标、主题预览色块、圆形头像、品牌 logo、圆形统计块、评论区小图标），**尺寸各异是对的**，非同一设计元素

**已发现待处理**

1. ~~**状态栏与内容之间残留接缝**：顶部 33dp 为 `#1E1E2E`（`surface_card`），其下立即变 `#141218`（M3 `colorSurface`）。根因是两者来自不同调色板。消除方式：`statusBarColor` 改用 `?attr/colorSurface`，或把内容底色纳入项目调色板~~ —— **已修复**，见第二十七批（改的是 `surface_card` 的取值，两个模式都验过）
2. **工具栏的抬升带**：`liftOnScrollColor` = `?attr/colorSurfaceContainer`（`#211F26`），与内容的 `#141218` 差一档。静止时本应是 `?attr/colorSurface`，但实测出现过 `#211F26`，推断是输入法触发滚动后抬升态残留。**待一次真机确认**；若确认残留，可用 `Widget.SkNote.AppBarLayout` 把 `liftOnScrollColor` 指回 `colorSurface` 抹掉这条带子
3. **同级列表页的空态/错误态同样被排到底部**：`fragment_share_list.xml`、`fragment_snippet_list.xml`、`fragment_bookmark_list.xml`、`fragment_reading_history.xml` 与讨论页是同一套结构，需做同样的两行移动（见第二十八批）
4. ~~讨论区缺「排序 / 未读 / 标签」~~ —— **已补齐**，见第三十批（排序与标签同一批、未读单独一批）
5. **未读只做了列表标记**：底部导航「讨论」tab 还没有未读角标（Discourse 有）。后端 `GET /unread` 已返回 `total_unread` / `total_new_replies`，接角标只差一个 `BottomNavigationView` badge
6. **标签还不能点按筛选**：现在只有输入、存储、显示、搜索；`GET /discussions` 还没有 `tag` 参数
7. `fragment_create_discussion.xml` 有 8 处 `android:tooltipText`（API 26 起生效，minSdk 24）——在 API 24/25 上静默失效，属原有问题
5. **讨论详情的通知入口仍走整页骨架**（其余 5 个入口已改为预填真实页头）：该入口只带 `discussion_id`，没有可预填的数据。若要完全统一，需给 `DiscussionHeaderAdapter` 增加「占位页头」viewType，与评论占位同样作为 item 渲染
6. ~~文章预填对所有入口都不生效（列表查询没取 `a.content`）~~ —— **已修复**：四条列表查询补上该列并部署，文章预填已生效（见第二十批）
7. **编辑资料页头与个人资料页头是同款但两处各写各的**（20dp/8dp/16dp、72dp 头像、22sp/14sp/12sp）——改一处必须同步另一处，否则「所见即所得」又断了。要做的话应抽成共享 dimen/style，需同时动两个已被真机验证过的布局

**再下一批（体验打磨）**
0. ~~**发布成功后草稿未被清理**（第三十一批复验发现，`6984cfc` 起就存在）：success 分支
   `clearDraft()` → `navigateUp()` → `onDestroyView()` 的 flush `persistDraftNow()` 把刚删掉的键
   原样写回，用户下次进来会被问「恢复未发布草稿？」，载入再发一次就是重复帖~~ —— **已修复**，
   见第三十一批（`draftConsumed` 闸门，提交 `3aaf62c`，真机四条路径复验通过）
1. 处置 `layout_empty_state` / `layout_error_state`（复用或删除）
2. `ProjectAnalyzerFragment` 的 `containerColors` 6 个固定饱和色改 M3 surface 角色
3. 启动页 `splash_background` 夜间值与 M3 `colorSurface` 对齐（~~`surface_card` 夜间值~~ 部分**已完成**，见第二十七批；splash 部分仍受夜间文件注释里那条限制：系统启动窗口只读浅色值，单独改夜间值会造成「蓝启动窗口 → 深色启动页」闪变）
4. 死资源清理（`chip_background_selector.xml`、`ic_abc.xml`、约 297 个零引用 drawable）

**需要产品决策（其他）**
1. `SyntaxHighlightUtil` 与 `JavaSyntaxHighlighter` 两套配色的浅色值归一（需确认以哪套为准）
2. `AuthActionHelper.requireRolesOrExit` 的 9 个调用点是否传入 `anchorView` 以彻底去掉 Toast
3. 导航栏 `labelVisibilityMode` 是否由 `selected` 改为 `labeled`（四项图标对齐，但标签常显）——
   **第三十二批已就此问过，用户选择保持 `selected`**（接受选中项图标高 12dp）
