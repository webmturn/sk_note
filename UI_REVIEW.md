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

1. **状态栏与内容之间残留接缝**：顶部 33dp 为 `#1E1E2E`（`surface_card`），其下立即变 `#141218`（M3 `colorSurface`）。根因是两者来自不同调色板。消除方式：`statusBarColor` 改用 `?attr/colorSurface`，或把内容底色纳入项目调色板
2. `fragment_create_discussion.xml` 有 8 处 `android:tooltipText`（API 26 起生效，minSdk 24）——在 API 24/25 上静默失效，属原有问题
3. **讨论详情的通知入口仍走整页骨架**（其余 5 个入口已改为预填真实页头）：该入口只带 `discussion_id`，没有可预填的数据。若要完全统一，需给 `DiscussionHeaderAdapter` 增加「占位页头」viewType，与评论占位同样作为 item 渲染

**再下一批（体验打磨）**
1. 处置 `layout_empty_state` / `layout_error_state`（复用或删除）
2. `ProjectAnalyzerFragment` 的 `containerColors` 6 个固定饱和色改 M3 surface 角色
3. 启动页 `splash_background` 夜间值、`surface_card` 夜间值与 M3 `colorSurface` 对齐
4. 死资源清理（`chip_background_selector.xml`、`ic_abc.xml`、约 297 个零引用 drawable）

**需要产品决策（其他）**
1. `SyntaxHighlightUtil` 与 `JavaSyntaxHighlighter` 两套配色的浅色值归一（需确认以哪套为准）
2. `AuthActionHelper.requireRolesOrExit` 的 9 个调用点是否传入 `anchorView` 以彻底去掉 Toast
3. 导航栏 `labelVisibilityMode` 是否由 `selected` 改为 `labeled`（四项图标对齐，但标签常显）
