# Article pages — read-only UI review (facts, `file:line`)

Roots: `android\app\src\main\res\layout`, `android\app\src\main\kotlin\com\sknote\app`, `android\app\src\main\res\values*`.
Nothing was modified; this file is a new report artifact only.

**File set (confirmed by glob, 10 layouts):** `fragment_article_list.xml`, `fragment_article_detail.xml`, `fragment_article_editor.xml`, `fragment_article_manage.xml`, `item_article.xml`, `item_article_manage.xml`, `item_article_simple.xml`, `layout_skeleton_article_detail.xml`, `layout_skeleton_card_article.xml`, `layout_skeleton_list_articles.xml`.
**Kotlin:** `ui\article\ArticleListFragment.kt`, `ArticleDetailFragment.kt`, `ArticleListViewModel.kt`, `ArticleDetailViewModel.kt`, `ui\home\ArticleAdapter.kt` (the adapter the article list uses), `ui\manage\article\ArticleManageFragment.kt`, `ArticleManageAdapter.kt`, `ArticleEditorFragment.kt`, `ArticleEditorViewModel.kt`, `ArticleManageViewModel.kt`, `res\menu\menu_article_detail.xml`.

---

## 1. Structure of each page

### 1.1 `fragment_article_list.xml` (88 lines)
- L2-6 root `LinearLayout` vertical, match/match.
  - L8-14 `MaterialToolbar id=toolbar`, `layout_height="?attr/actionBarSize"`, `android:background="?attr/colorSurface"`, `app:title="文章列表"`, `app:navigationIcon="@drawable/ic_arrow_back"`, no `android:padding`. Title overwritten in code: `ArticleListFragment.kt:46` `binding.toolbar.title = categoryName`.
  - L16-20 `SwipeRefreshLayout id=swipeRefresh`, h=`0dp`, `layout_weight=1`.
    - L22-24 `FrameLayout` (no id) — the state stack.
      - L26-31 `RecyclerView id=rvArticles`, `padding="12dp"`, `clipToPadding="false"`.
      - L33-35 `<include android:id="@+id/skeletonContainer" layout="@layout/layout_skeleton_list_articles" />`
      - L37-59 empty block `id=layoutEmpty`, `visibility="gone"`; L45-50 `ImageView` 72×72dp `src=ic_empty`, `layout_marginBottom=12dp`, `importantForAccessibility="no"`; L52-58 `TextView id=tvEmpty` `textSize=14sp`, `textColor="?attr/colorOnSurfaceVariant"`.
      - L61-84 error block `id=layoutError`, `visibility="gone"`; L69-75 `tvError` `textSize=15sp`; L77-83 `MaterialButton id=btnRetry` `layout_marginTop=12dp`, `style=Widget.Material3.Button.TonalButton`.

### 1.2 `fragment_article_detail.xml` (394 lines)
- L2-6 root `LinearLayout` vertical.
  - L8-13 `MaterialToolbar id=toolbar`, `?attr/actionBarSize`, `?attr/colorSurface`, `ic_arrow_back`, **no `app:title`** (set at `ArticleDetailFragment.kt:156` to `"文章详情"`).
  - L15-23 `LinearProgressIndicator id=readingProgress`, `app:trackThickness="2dp"`, `max=100`, `visibility="gone"`.
  - L25-28 `FrameLayout`, h=`0dp`, `weight=1`.
    - L30-32 `<include android:id="@+id/skeletonContainer" layout="@layout/layout_skeleton_article_detail" />` (declared **before** content).
    - L34-218 `NestedScrollView id=scrollView`, `visibility="gone"`; L40-44 inner `LinearLayout` `padding="16dp"`; L46-57 chip `tvCategory` (`bg_chip_primary`, `paddingHorizontal=10dp`, `paddingVertical=3dp`, `11sp` bold, gone); L59-66 `tv_title` `24sp` bold `lineSpacingMultiplier=1.2`; L68-129 author card (`cardCornerRadius=16dp`, `cardBackgroundColor=?attr/colorSurfaceVariant`, inner `padding=12dp`, avatar `MaterialCardView` 28×28dp r=14dp + `ImageView ic_person` `padding=4dp`); L105-111 `tv_author` 13sp bold; L113-119 `tvTime` 11sp; L122-127 `tv_meta` 12sp; L131-134 divider `marginVertical=16dp`; L136-143 `tv_content` `15sp`, `lineSpacingMultiplier=1.6`, `letterSpacing=0.01`; L145-149 divider; L151-215 `cardDiscussions` (`clickable=true`, `focusable=true`, stroke `?attr/colorOutlineVariant`, inner `padding=14dp`, icon tile 36×36dp r=18dp `?attr/colorSecondaryContainer` + `ic_discussion` `padding=8dp`, trailing `ic_chevron_right` 20×20dp `alpha=0.5`).
    - L220-245 error `id=layoutError` `padding="32dp"` `background="?attr/colorSurface"` `visibility="gone"`; `tvError` 15sp (L230-236), `btnRetry` TonalButton (L238-244).
  - L248-392 `MaterialCardView id=bottomBar`, `android:tag="snackbar_anchor"`, `cardCornerRadius=0dp`, `strokeWidth=1dp`, `?attr/colorSurface`; L259-263 inner row fixed `layout_height="48dp"`; four equal-weight clickable `LinearLayout`s `btn_like` (L265-292), `btn_bookmark` (L299-326), `btn_share` (L333-358), `btn_discuss` (L365-390) — each `layout_height="match_parent"`, `background="?attr/selectableItemBackground"`, `clickable=true`, `focusable=true`, icons 20×20dp, labels 13sp; three 1dp `View` separators `layout_height="24dp"` `background="?attr/colorOutlineVariant"` (L294-297, 328-331, 360-363).

### 1.3 `fragment_article_editor.xml` (216 lines)
- L2-6 root `LinearLayout` vertical.
  - L8-14 `MaterialToolbar id=toolbar`, `?attr/actionBarSize`, `?attr/colorSurface`, `app:title="编辑文章"` (overwritten `ArticleEditorFragment.kt:97` → `"创建文章"`/`"编辑文章"`), `ic_arrow_back`.
  - L16-21 `ScrollView`, h=`0dp`, `weight=1`, `fillViewport=true`, `padding="16dp"`; L23-26 inner vertical `LinearLayout`.
    - L32-48 `TextInputLayout id=layoutTitle` `marginBottom=12dp` `hint="文章标题"` `counterMaxLength=100` → `TextInputEditText id=etTitle` `textSize=18sp`, `maxLength=100`.
    - L50-64 `layoutCategory` `marginBottom=12dp`, `style=Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu` → `AutoCompleteTextView id=spinnerCategory`.
    - L66-82 `layoutSummary` `marginBottom=12dp` `counterMaxLength=160` `minLines=2`.
    - L84-107 header row: label `TextView` `"内容（Markdown）"` 14sp bold (L90-96, **no id, no `android:labelFor`**) + `MaterialButton id=btnPreview` TextButton with `android:minHeight="0dp"`, `paddingTop/Bottom=4dp` (L98-106).
    - L109-116 `tvEditorStats` 12sp `?attr/colorOnSurfaceVariant`.
    - L118-138 `layoutContent` `counterMaxLength=50000` → `TextInputEditText id=etContent` `minLines=15`, `gravity=top|start`, `textMultiLine`, `14sp`, `fontFamily="monospace"`, `maxLength=50000`.
    - L140-146 hint line `11sp`; L148-154 `tvPreview` `padding="12dp"` `visibility="gone"` `textSize=14sp`.
  - L159-214 bottom save panel `android:tag="snackbar_anchor"`, `padding="16dp"`; L173-177 `MaterialSwitch id=switchPublished` `checked=true`; L179-187 `tvPublishedHint` 12sp; L195-204 `tvDraftMeta` 12sp `maxLines=2`; L206-211 `MaterialButton id=btnSave` `marginStart=12dp`.

### 1.4 `fragment_article_manage.xml` (58 lines)
- L2-5 root `CoordinatorLayout`.
  - L7-10 `LinearLayout` vertical match/match.
    - L12-18 `MaterialToolbar id=toolbar`, `?attr/actionBarSize`, `?attr/colorSurface`, `app:title="文章管理"` (no code-side title assignment — `ArticleManageFragment.kt:36-47` only sets the nav listener).
    - L20-23 `FrameLayout` h=`0dp` `weight=1`.
      - L25-28 `SwipeRefreshLayout id=swipeRefresh`.
        - L30-34 `RecyclerView id=rvArticles`, `padding="8dp"`, **no `clipToPadding`**.
      - L37-45 `TextView id=tvEmpty` match/match `gravity=center` `textSize=16sp` `visibility="gone"`.
  - L49-56 `FloatingActionButton id=fabAdd`, `layout_gravity="end|bottom"`, `layout_margin="16dp"`, `src=ic_add`, `contentDescription="创建文章"`.

### 1.5 Item layouts
- `item_article.xml` (102): L2-11 `MaterialCardView` root, `marginBottom=12dp`, `marginHorizontal=4dp`, `cardCornerRadius=16dp`, `cardElevation=0dp`, `strokeWidth=1dp`, `strokeColor=?attr/colorOutlineVariant`; L13-18 inner `LinearLayout` vertical `padding="16dp"` `background="?attr/selectableItemBackground"`; L26-34 chip `tv_category` 11sp `bg_chip_primary` `paddingHorizontal=8dp`/`Vertical=2dp`; L36-43 `tv_author` 12sp; L46-55 `tv_title` 16sp bold `maxLines=2`; L57-65 `tv_summary` 14sp `maxLines=2`; L67-99 stats row: `tv_views` 12sp, `tv_likes` 12sp `marginStart=16dp`, 0dp×0dp weighted spacer (L88-91), `tv_time` 11sp. No ids with `contentDescription`; the whole card is the click target (set in code).
- `item_article_manage.xml` (60): root card `layout_margin="4dp"`, r=16dp, elevation 0, **no stroke**; inner horizontal row `padding="16dp"`; `tvTitle` 16sp bold `maxLines=2`; `tvMeta` 12sp `marginTop=4dp`; `ImageButton id=btnEdit` and `id=btnDelete` each 48×48dp, `padding="8dp"`, `background=?attr/selectableItemBackgroundBorderless`, `contentDescription="编辑"` / `"删除"` (L41-57).
- `item_article_simple.xml` (100): used **only** by `BookmarkListFragment.kt:92` and `ReadingHistoryFragment.kt:122` (bookmark/history pages) — marginHorizontal 4dp + marginVertical 6dp, r=16dp, stroke 1dp, inner `padding="16dp"`; `tvCategory` 11sp chip; `tvTimestamp` 11sp `alpha=0.7`; `tvTitle` **15sp**; `tvSummary` **13sp**; `tvAuthor`/`tvViews` 11sp.
- Skeletons: `layout_skeleton_list_articles.xml` = `NestedScrollView id=skeletonRoot`, `paddingHorizontal=12dp`, `paddingTop=12dp`, `paddingBottom=16dp`, 5× `<include layout="@layout/layout_skeleton_card_article" />` (L17-25). `layout_skeleton_card_article.xml` mirrors `item_article.xml` geometry exactly (`marginBottom=12dp`, `marginHorizontal=4dp`, r=16dp, stroke 1dp, inner `padding=16dp`), header comment L2-3 "Mirrors item_article.xml … No avatar". `layout_skeleton_article_detail.xml` = `LinearLayout id=skeletonRoot` `padding="16dp"` with 12 hardcoded `View` blocks (20/22/22/28/10/8/10/12dp rows, margins 6-24dp) and one inner row `padding="12dp"` (L32).

### 1.6 Value distribution vs. same role elsewhere (grep over `res\layout\*.xml`)
| Role | Article pages | Elsewhere |
|---|---|---|
| List RecyclerView padding | **12dp**: `fragment_article_list.xml:30` | 12dp also `fragment_bookmark_list.xml:29`, `fragment_reading_history.xml:30`, `fragment_category_manage.xml:39` (paddingHorizontal); **8dp** in the 6 manage pages: `fragment_article_manage.xml:34`, `fragment_discussion_manage.xml:45`, `fragment_share_manage.xml:58`, `fragment_snippet_manage.xml:34`, `fragment_release_manage.xml:34`, `fragment_user_manage.xml:52` |
| `clipToPadding="false"` on list RV | present `fragment_article_list.xml:31`; absent `fragment_article_manage.xml:30-34` | present `fragment_bookmark_list.xml:30`, `fragment_reading_history.xml:31`, `fragment_category_manage.xml:38`, `fragment_share_manage.xml:59`, `fragment_release_manage.xml:35` |
| Item-card inner padding | 16dp (`item_article.xml:17`, `item_article_manage.xml:14`, `item_article_simple.xml:17`) | 16dp in `item_discussion.xml:17`, `item_share.xml:17`, `item_snippet.xml:17`, `item_profile_content.xml:19`, `item_category_manage.xml:17`, `item_discussion.xml`; 12dp only in `item_related_block.xml:17`, `item_reference.xml:30` |
| Item-card vertical margin | `item_article.xml:6` = 12dp; `item_article_simple.xml:7` = 6dp; `item_article_manage.xml:6` = 4dp | `item_discussion.xml:6` = 10dp; other manage items all 4dp (`item_discussion_manage.xml:6`, `item_snippet_manage.xml:6`, `item_share_manage.xml:6`, `item_user_manage.xml:6`, `item_release_manage.xml:6`) |
| Card corner radius | 16dp (`item_article.xml:8`, `item_article_simple.xml:8`, `item_article_manage.xml:7`, `layout_skeleton_card_article.xml:10`, `fragment_article_detail.xml:72,156,253`) | 16dp is the app-wide item radius; exception `item_category_manage.xml:8` = 18dp |
| Category chip padding | 8dp/2dp (`item_article.xml:32-33`, `item_article_simple.xml:33-34`) vs **10dp/3dp** (`fragment_article_detail.xml:52-53`) | 8dp/2dp pattern elsewhere (`item_discussion.xml:58`, `item_share.xml:43`, `item_share.xml:72`) |
| Category chip text | 11sp (`item_article.xml:34`, `item_article_simple.xml:35`, `fragment_article_detail.xml:54` bold) | 11sp is the norm; `chip_discussion_category.xml:7` = 13sp |
| Detail title size | **24sp** (`fragment_article_detail.xml:63`) | 20sp `fragment_share_detail.xml:75`, 20sp `fragment_snippet_detail.xml:76`, 21sp `layout_discussion_detail_header.xml:28` |
| List item title size | **16sp** (`item_article.xml:51`, `item_article_manage.xml:27`) | 15sp `item_article_simple.xml:56`, `item_discussion.xml:89`, `item_share.xml:27`, `item_snippet.xml:27`, `item_discussion_manage.xml:24`, `item_share_manage.xml:35`, `item_snippet_manage.xml:24`; 14sp `item_notification.xml:61`; 13sp `item_share_compact.xml:31` |
| Empty-state text size | 14sp `fragment_article_list.xml:57`; 16sp `fragment_article_manage.xml:43` | 16sp in all 6 other manage pages (`fragment_discussion_manage.xml:54`, `fragment_share_manage.xml:68`, `fragment_snippet_manage.xml:43`, `fragment_release_manage.xml:44`, `fragment_user_manage.xml:61`, `fragment_category_manage.xml:49`); shared `layout_empty_state.xml:22` = 15sp |
| Error-state text size / padding | 15sp + (no padding) `fragment_article_list.xml:74,62`; 15sp + `padding=32dp` `fragment_article_detail.xml:235,226` | shared `layout_error_state.xml:24` (15sp) / `:11` (`padding="32dp"`) — article list duplicates the block inline instead of including it; only `fragment_bookmark_list.xml:67` and `fragment_reading_history.xml:68` include `layout_error_state` |
| Empty icon | 72dp `ic_empty` **without `alpha`** `fragment_article_list.xml:45-50` | `layout_empty_state.xml:13-18` = 72dp with `android:alpha="0.3"`; `layout_error_state.xml:14-20` = 64dp `ic_error_outline` `alpha=0.4` |
| Toolbar background | `?attr/colorSurface` in all four (`fragment_article_list.xml:12`, `fragment_article_detail.xml:12`, `fragment_article_editor.xml:12`, `fragment_article_manage.xml:16`) | same attribute used by 30+ other fragments; `fragment_profile_edit.xml:29-42`, `fragment_create_discussion.xml:8-21` etc. use `AppBarLayout` instead |

---

## 2. States (loading / empty / error)

| Page | Loading (skeleton) | Empty | Error |
|---|---|---|---|
| Article list | **有** `<include android:id="@+id/skeletonContainer" layout="@layout/layout_skeleton_list_articles" />` `fragment_article_list.xml:33-35` | **有** `layoutEmpty`/`tvEmpty` `fragment_article_list.xml:38,53` | **有** `layoutError`/`tvError`/`btnRetry` `fragment_article_list.xml:62,70,78` |
| Article detail | **有** `<include android:id="@+id/skeletonContainer" layout="@layout/layout_skeleton_article_detail" />` `fragment_article_detail.xml:30-32` | **不存在** | **有** `layoutError`/`tvError`/`btnRetry` `fragment_article_detail.xml:221,231,239` |
| Article editor | **不存在** (no `skeletonContainer`; the id occurs in only 8 layout files, editor not among them) | **不存在** | **不存在** as a view (no `layoutError`/`tvError`/`btnRetry` in `fragment_article_editor.xml`) |
| Article manage | **不存在** | **有** `tvEmpty` `fragment_article_manage.xml:38` | **不存在** as a view (no `layoutError`/`btnRetry` in `fragment_article_manage.xml`) |

Kotlin toggling:
- List: `showSkeleton(binding.skeletonContainer.root, binding.rvArticles)` `ArticleListFragment.kt:78`; `SkeletonAnimator.start(...)` `:79`; `hideSkeletonAndShow(...)` `:90`; `binding.skeletonContainer.root.visibility = View.GONE` `:101`; empty `binding.layoutEmpty.fadeIn()/fadeOut()` `:76,93,104`; error `binding.layoutError.fadeIn()/fadeOut()` `:103,106`; retry `:110-117`. Cache-hit path skips the skeleton entirely `:71-80`.
- Detail: `showSkeleton(binding.skeletonContainer.root, binding.scrollView, binding.layoutError)` `ArticleDetailFragment.kt:108`; prefill path hides skeleton directly `:106`; `revealContent()` `:285-296` (`binding.scrollView.visibility = View.VISIBLE` `:290`, `hideSkeletonAndShow(...)` `:294`); error `:174-183` (`layoutError.visibility = VISIBLE/GONE` `:178,181`); retry `:185-192`. `isLoading` is observed in the ViewModel (`ArticleDetailViewModel.kt:18,37,53`) but **not** consumed by the fragment (no `viewModel.isLoading.observe` in `ArticleDetailFragment.kt`).
- Manage: `binding.tvEmpty.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE` `ArticleManageFragment.kt:103`; `swipeRefresh.isRefreshing = isLoading` `:107`; errors arrive as messages → Snackbar `:110-115` (`ArticleManageViewModel.kt:49,52,67,70` set `_message`).
- Editor: no state views; errors → Snackbar `ArticleEditorFragment.kt:198-203`; loading → `btnSave.text = if (isLoading) "保存中..." else "保存文章"` `:206` and `tvDraftMeta` `:353-361`; stats text `:343-352`.
- Animation helpers: `fadeIn`/`fadeOut` `util\ViewAnimUtil.kt:8,19`; `showSkeleton`/`hideSkeletonAndShow` `util\SkeletonAnimator.kt:94,100`; breathing animator `util\SkeletonAnimator.kt:10,85`.

---

## 3. Dark mode / hardcoded colors

**Hardcoded color literals in the article layouts: 不存在.** No `android:textColor="#…"`, no `android:background="#…"`, no `#RRGGBB` in any of the 10 layouts (verified by per-file scan).

**Hardcoded colors reachable from article pages via drawables / Kotlin:**
- `res\drawable\ic_favorite.xml:7` — `android:fillColor="@android:color/white"`, **no `android:tint`**; used untinted as `android:src` at `fragment_article_detail.xml:280` (`ivLike`, bottom bar over `?attr/colorSurface`), and untinted at `item_share.xml:126`, `item_snippet.xml:117`. (`item_share_manage.xml:90`, `item_snippet_manage.xml:50` add `app:tint="?attr/colorOnSurfaceVariant"`.)
- `res\drawable\ic_empty.xml:8` — `android:fillColor="@android:color/transparent"`; used at `fragment_article_list.xml:48` (72dp empty-state ImageView) and `fragment_home.xml:400`.
- `res\drawable\ic_bookmark_outline.xml` / `ic_bookmark_filled.xml` — path `fillColor="@android:color/white"` **plus** `android:tint="?attr/colorOnSurface"` inside the vector. The fragment swaps these by resource id at `ArticleDetailFragment.kt:300,306`.
- `res\drawable\bg_chip_primary.xml:4` — `solid android:color="?attr/colorPrimaryContainer"` (theme attr, used by `fragment_article_detail.xml:50`, `item_article.xml:30`, `item_article_simple.xml:31`).
- `res\drawable\skeleton_block.xml:4` / `skeleton_block_lg.xml:4` / `skeleton_circle.xml:4` — `@color/skeleton_base`, defined `res\values\colors.xml:61` `#FFE8ECEF` and `res\values-night\colors.xml:63` `#FF202028`.
- **No** `Color.parseColor`, `0x…` int, or `setBackgroundColor(literal)` in `ui\article\*` or `ui\manage\article\*` (grep of both packages: only the two `resolveAttribute` blocks below).

**Colors resolved from theme attrs in Kotlin (article pages):**
- `ArticleDetailFragment.kt:302-304` — `resolveAttribute(com.google.android.material.R.attr.colorPrimary, …)` then `resources.getColor(tv.resourceId, null)` → `binding.tvBookmarkLabel` when bookmarked.
- `ArticleDetailFragment.kt:308-310` — same pattern with `colorOnSurfaceVariant` when not bookmarked.
- `ui\manage\article\*`: 不存在 (no `resolveAttribute`/`setTextColor`/`getColor` hits).

**Theme-attr color usage in the layouts (exhaustive):**
- `fragment_article_list.xml`: `?attr/colorSurface` L12; `?attr/colorOnSurfaceVariant` L58, L75.
- `fragment_article_detail.xml`: `?attr/colorSurface` L12, L227, L257; `?attr/colorOnPrimaryContainer` L51; `?attr/colorOnSurface` L66, L109, L143; `?attr/colorSurfaceVariant` L74; `?attr/colorOnSurfaceVariant` L117, L126, L205, L236, L291, L325, L357, L389; `?attr/colorOutlineVariant` L159, L256, L297, L331, L363; `?attr/colorSecondaryContainer` L175; `?attr/selectableItemBackground` L272, L306, L340, L372.
- `fragment_article_editor.xml`: `?attr/colorSurface` L12; `?attr/colorOnSurfaceVariant` L115, L146, L187, L203.
- `fragment_article_manage.xml`: `?attr/colorSurface` L16; `?attr/colorOnSurfaceVariant` L44.
- `item_article.xml`: `?attr/colorOutlineVariant` L11; `?attr/selectableItemBackground` L18; `?attr/colorOnPrimaryContainer` L31; `?attr/colorOnSurfaceVariant` L43, L63, L78, L86, L98; `?attr/colorOnSurface` L53.
- `item_article_manage.xml`: `?attr/colorOnSurfaceVariant` L37; `?attr/selectableItemBackgroundBorderless` L47, L56.
- `item_article_simple.xml`: `?attr/colorOutlineVariant` L11; `?attr/selectableItemBackground` L18; `?attr/colorOnPrimaryContainer` L32; `?attr/colorOnSurfaceVariant` L48, L67, L84, L96; `?attr/colorOnSurface` L58.
- `layout_skeleton_card_article.xml`: `?attr/colorOutlineVariant` L13; `?attr/colorSurface` L14. `layout_skeleton_article_detail.xml` and `layout_skeleton_list_articles.xml`: 无 (only `@drawable/skeleton_*` backgrounds).

---

## 4. Accessibility

- **`ImageView`/`ImageButton` used as a button without `contentDescription`:**
  - `fragment_article_manage.xml:49-56` FAB — **有** `contentDescription="创建文章"` (L56).
  - `item_article_manage.xml:41-48` `btnEdit` — **有** `contentDescription="编辑"`; `:50-57` `btnDelete` — **有** `contentDescription="删除"`.
  - `fragment_article_detail.xml:276-282` (`ivLike`), `:310-316` (`ivBookmark`), `:344-349` (share), `:376-381` (discuss): each has `android:importantForAccessibility="no"` **and** the click target is the parent clickable `LinearLayout` (`:265,299,333,365`) whose accessible name comes from its sibling `TextView` (13sp labels at `:284-291, :318-325, :351-357, :383-389`). No `contentDescription` on those parents.
  - `fragment_article_list.xml:45-50` `ic_empty` — `importantForAccessibility="no"`, not clickable.
  - `fragment_article_detail.xml:208-213` `ic_chevron_right` — `importantForAccessibility="no"`, not clickable; `:90-96` `ic_person` avatar — `importantForAccessibility="no"`.
  - The whole `item_article.xml` card is clickable via `binding.root.setOnClickListener` (`ArticleAdapter.kt:35`) and has **no** `contentDescription` (text children only).
- **Clickable views whose height is < 40dp:**
  - No article layout declares a fixed clickable height below 40dp. `fragment_article_detail.xml:266,300,334,366` are `match_parent` inside the 48dp row at `:261`; `:151-161` `cardDiscussions` is `wrap_content` (14dp padding + 36dp tile); `item_article_manage.xml:43,52` buttons are 48×48dp; FAB is default size.
  - `fragment_article_editor.xml:98-106` `btnPreview` explicitly zeroes the minimum: `android:minHeight="0dp"` with `paddingTop/paddingBottom="4dp"`, `layout_height="wrap_content"`, no `textSize` → height is text-driven (default button text size), i.e. not fixed and not padded to 40dp by the layout.
  - `fragment_article_list.xml:77-83` / `fragment_article_detail.xml:238-244` `btnRetry`: `wrap_content` height, no explicit `minHeight` (M3 TonalButton style default applies).
- **Text with `textSize` < 11sp: 不存在.** Smallest sizes in the article layouts are 11sp (`fragment_article_detail.xml:54,118`; `fragment_article_editor.xml:145`; `item_article.xml:34,97`; `item_article_simple.xml:35,47,83,95`). A repo-wide grep for `textSize="10sp"` or smaller returns 0 matches.
- `android:labelFor`: 不存在 in any of the four article fragment layouts; the editor's field label `TextView` at `fragment_article_editor.xml:90-96` (`"内容（Markdown）"`) has neither `id` nor `labelFor` (the `TextInputLayout`s themselves carry `android:hint` at L37, L55, L71, L122).

---

## 5. Icons

Drawables referenced by the article pages:
| Drawable | Referenced at |
|---|---|
| `ic_arrow_back` | `fragment_article_list.xml:14`, `fragment_article_detail.xml:13`, `fragment_article_editor.xml:14`, `fragment_article_manage.xml:18` |
| `ic_empty` | `fragment_article_list.xml:48` |
| `ic_person` | `fragment_article_detail.xml:93` |
| `ic_discussion` | `fragment_article_detail.xml:181`, `:379` |
| `ic_chevron_right` | `fragment_article_detail.xml:211` |
| `ic_favorite` | `fragment_article_detail.xml:280` |
| `ic_bookmark_outline` | `fragment_article_detail.xml:314`; `ArticleDetailFragment.kt:306` |
| `ic_bookmark_filled` | `ArticleDetailFragment.kt:300` |
| `ic_share` | `fragment_article_detail.xml:347`; `menu_article_detail.xml:7` |
| `ic_add` | `fragment_article_manage.xml:55` |
| `ic_edit` | `item_article_manage.xml:46`; `menu_article_detail.xml:13` |
| `ic_delete` | `item_article_manage.xml:55` |
| `bg_chip_primary`, `skeleton_block`, `skeleton_block_lg`, `skeleton_circle` | `fragment_article_detail.xml:50`, `item_article.xml:30`, `item_article_simple.xml:31`; the three skeletons as `android:background` throughout `layout_skeleton_*` |

- **Newer `*_rounded` (Material Symbols Rounded) set:** exactly two files exist in `res\drawable` — `ic_search_rounded.xml` and `ic_sort_rounded.xml` (file listing). Their only usage sites are `fragment_reference_container.xml:39`, `fragment_reference.xml:357`, `fragment_discussion_list.xml:65` → **article pages use none of them** (不存在).
- **Old glyph family used by article pages:** all of the above-listed icons are the 2014 Material Icons vectors (single solid `pathData`, e.g. `ic_bookmark_filled.xml:3` `M17,3H7c-1.1,0 -1.99,0.9 …`; `ic_favorite.xml:7` `M12,21.35l-1.45,-1.32C5.4 …`).
- Embedded vector tints (article pages can't override on plain `ImageView`): `ic_share` `?attr/colorOnSurface`, `ic_person` `?attr/colorOnSurface`, `ic_chevron_right` `?attr/colorOnSurfaceVariant`, `ic_edit` `?attr/colorOnSurface`, `ic_delete` `?attr/colorError`, `ic_add` `?attr/colorOnPrimaryContainer`, `ic_discussion` `?attr/colorOnSecondaryContainer`, `ic_bookmark_outline/_filled` `?attr/colorOnSurface`, `ic_arrow_back` `?attr/colorOnSurface`; `ic_favorite` and `ic_empty` have **no** tint.
- `ic_empty.xml` (9 lines) contains a single path with `fillColor="@android:color/transparent"` (see §3).

---

## 6. Known issues already documented in `UI_REVIEW.md` (59 grep hits)

Directly about the article pages / article code:
| Line | Gist |
|---|---|
| 288 | `SmallSp`: "字号 10sp < 11sp 下限 … `item_article_simple.xml:35` … 改 11sp" (current `item_article_simple.xml:35` = **11sp**; repo-wide `textSize="10sp"` count is now 0) |
| 289 | `LabelFor`: "输入框既无 `labelFor` 也无 `hint` … `fragment_article_editor.xml:90`" (current L90-96 = plain label TextView, still no `labelFor`) |
| 406 | scanner false positive accepted: "`fragment_article_detail.xml` 底栏的 `0dp + weight` 子项，其父容器固定 `48dp`" |
| 950-1015 | 第二十批（文章/分享详情页预填）: entry inventory 960-965, "文章页不能做「部分预填」，而文章目前也没有任何入口可预填" 969, content-guard rationale 971, the `articles.ts:22` vs `:75` correction 973-975, `revealContent()` 982, entries list 984, hot-path device frames 1007-1011, back-end change list 1015 |
| 1120 | "首页与文章列表未发现同类问题：文章列表没有 chip 行" |
| 1191 | snackbar anchor: "给四个详情页的底部操作栏打 `android:tag="snackbar_anchor"` —— 文章页 `bottomBar`" |
| 1331 | "卡内标题 `发起讨论` … `ArticleEditorFragment.kt:90-91` 是同一套写法" |
| 1341 | addition of `snackbar_anchor` to "文章编辑器（保存面板）" |
| 1384 | "本 App 有两套表单形态 … `发起讨论`/`文章编辑器` 一类在顶部多一张「引导卡」" |
| 1480, 1491, 1504, 1510 | 第二十九批: guide card removed from the article editor (1491), verification "文章编辑器 引导卡与重复标题均消失" (1504), and the 1384 observation retracted (1510) |
| 2490 | "~~文章预填对所有入口都不生效（列表查询没取 `a.content`）~~ —— **已修复**" |
| 41, 111, 340 | card-radius baseline row (41), `ArticleEditorViewModel` cited as the one-way-state pattern to copy (111, 340) |

Indirect hits (home / discussion / profile / icon-audit context, listed for completeness):
41, 653 ("第一篇文章出现在 y≈503dp"), 666 ("「最新文章」行上边距 24 → 16dp"), 791 + 801 ("骨架系统覆盖了其余 8 个列表/详情页" / "8 个文件逐一核对过：article_list / article_detail …"), 822, 839, 872, 874, 876 (skeleton margin fix: "文章卡丢 `marginBottom` 12dp 与 `marginHorizontal` 4dp"), 952, 956, 1021-1022 (back-end queries), 1033-1034, 1053, 1063-1064, 1070 ("片段页与文章/分享页同构"), 1087 ("四个详情页至此全部收敛"), 1226, 1230, 1238, 1256, 1821 ("首页的「搜索文章…」是第三套搜索入口"), 1852-1853/1893/1939-1940 (icon-family split: only search/sort unified; "旧字形 `ic_search` / `ic_sort` 仍在其它页面使用"), 2186-2201 (48dp touch target holding a 20dp icon pattern — discussion page), 2329.

---

## 7. Text / formatting

- Date/time: `util\TimeUtil.kt:5-37` `formatRelative(isoTime)` (parses `yyyy-MM-dd HH:mm:ss` as **UTC**, `:9-11`; buckets 刚刚/<60s, `N分钟前`, `N小时前`, `N天前`, `MM-dd`, `yyyy-MM-dd` `:20-33`). Call sites on article surfaces: `ArticleAdapter.kt:34` (`tv_time`), `ArticleDetailFragment.kt:154` (`tvTime`), `ArticleManageAdapter.kt:45` (`tvMeta`); bookmark/history rows: `BookmarkListFragment.kt:104`, `ReadingHistoryFragment.kt` (same adapter pattern).
- `TimeUtil.formatDateTime` (`TimeUtil.kt:39-51`) has **no call site** anywhere in `com\sknote\app` (grep).
- View counts: `ArticleAdapter.kt:32` `binding.tvViews.text = "${article.viewCount} 阅读"`; `ArticleDetailFragment.kt:155` `binding.tvMeta.text = "${article.viewCount} 阅读"`; `ArticleManageAdapter.kt:45` `"… · ${article.viewCount} 阅读 · ${TimeUtil.formatRelative(...)}"`. No thousands separator / abbreviation helper for counts anywhere in the app.
- Like counts: `ArticleAdapter.kt:33` `"${article.likeCount} 赞"`; `ArticleDetailFragment.kt:163` `if (article.likeCount > 0) "${article.likeCount}" else "点赞"` (bottom bar also shows a snackbar `"已点赞"`/`"已取消点赞"` `:206`).
- Read time: **不存在** — no 阅读时长 / 分钟 estimate is computed or displayed on the list, detail or editor (grep for 阅读时长 / 分钟阅读 / 分钟 returns only `TimeUtil.kt:22`).
- Markdown rendering on the detail page: **有**, Markwon 4.6.2 (`android\app\build.gradle.kts:84-87`: `io.noties.markwon:core`, `:ext-tables`, `:syntax-highlight`, `:image-glide`). Configuration: `ArticleDetailFragment.kt:29-30` imports, `:58-60` `Markwon.builder(requireContext()).usePlugin(GlideImagesPlugin.create(requireContext())).build()`, rendering at `:166` `markwon.setMarkdown(binding.tvContent, article.content.orEmpty())` into `fragment_article_detail.xml:137` `tv_content`.
- Editor preview also uses Markwon: `ArticleEditorFragment.kt:67` `Markwon.create(requireContext())` (no plugins), `:240-249` `renderPreview()` → `markwon.setMarkdown(binding.tvPreview, content)`; empty content shows `"暂无可预览内容"` with `alpha = 0.6f` `:243-244`; preview target `fragment_article_editor.xml:148-154`.
- Editor character counters: `tvEditorStats` `ArticleEditorFragment.kt:343-352` ("正文 N 字 · 摘要 N 字", prefix "预览模式 · "), plus `TextInputLayout` counters configured at `fragment_article_editor.xml:38-39, 72-73, 123-124`.

---

## 8. Interaction

Click listeners:
- **Article list** `ArticleListFragment.kt`: toolbar nav → `navigateUp()` `:47`; adapter item click → build bundle (`article_id`, prefill) and `navigate(R.id.articleDetailFragment, …, slideNavOptions())` `:49-61`; `swipeRefresh.setOnRefreshListener { viewModel.loadArticles(currentCategoryId) }` `:65-67`; `btnRetry` → hide error, re-show skeleton if `!contentShown`, reload `:110-117`.
- **Article detail** `ArticleDetailFragment.kt`: toolbar nav `:61`; menu inflated `:62` and handled `:120-141` (`action_share` → `Intent.ACTION_SEND` chooser with title + summary/first 200 chars `:122-133`; `action_edit` → `navigate(R.id.articleEditorFragment)` `:134-138`; `action_edit` visibility = admin or author `:314-317`); `btnRetry` `:185-192`; `btnLike` → `likeArticle` API + snackbar + reload `:194-216`; `btnShare` → `binding.toolbar.menu.performIdentifierAction(R.id.action_share, 0)` `:218-220`; `btnDiscuss` `:229-232` and `cardDiscussions` `:234-237` both → `navigate(R.id.discussionListFragment, {article_id})` with local `NavOptions` `:222-227`; `btnBookmark` → `toggleBookmark` + `updateBookmarkUI()` `:239-261`; on entry, logged-in users get `checkBookmark` + `recordHistory` `:264-278`.
- **Article editor** `ArticleEditorFragment.kt`: toolbar nav and back dispatcher both → `confirmExit()` `:98, 100-104`; `btnPreview` → `togglePreview()` `:106` (view swap `:217-228`); `btnSave` → `saveArticle()` `:108` (validate `:292-314`); `spinnerCategory` click/focus → `showDropDown()` `:139-142`, item click → set category `:151-155`; text watchers on `etTitle`/`etSummary`/`etContent`/`spinnerCategory` `:123-138`.
- **Article manage** `ArticleManageFragment.kt`: toolbar nav `:39`; role gate `requireRolesOrExit(setOf("admin","editor"))` `:41-46`; adapter `onEdit` → editor `:52-55`, `onDelete` → `showDeleteConfirm` `:56` (dialog `:118-125` → `viewModel.deleteArticle`); `fabAdd` → create `:70-72`; `swipeRefresh` → `loadArticles(force = true)` `:74-76`; nav-result observers for refresh/message `:85-99`.
- **Adapters**: `ArticleAdapter.kt:35` `binding.root.setOnClickListener { onClick(article) }`; `ArticleManageAdapter.kt:53-54` `btnEdit`/`btnDelete` listeners, visibility gated at `:49-51`.

Scrolling / refresh:
- Pull-to-refresh: **有** on list (`fragment_article_list.xml:16-20` + `ArticleListFragment.kt:65`) and manage (`fragment_article_manage.xml:25-28` + `ArticleManageFragment.kt:74`); **不存在** on detail and editor.
- Scroll-to-top FAB: **不存在** on any article page. The only FAB is `fabAdd` (create) `fragment_article_manage.xml:49-56`, handled `ArticleManageFragment.kt:70-72`.
- Collapsing toolbar: **不存在** — no `CollapsingToolbarLayout` in any layout under `res\layout`; no article page wraps its toolbar in an `AppBarLayout` (both list/detail/manage use a bare `MaterialToolbar` in a vertical `LinearLayout`; the detail page has no `liftOnScroll`).
- Containers: list = `RecyclerView` (`rvArticles`) inside `SwipeRefreshLayout` + `FrameLayout` state stack (`fragment_article_list.xml:16-35`); manage = `RecyclerView` in `SwipeRefreshLayout` inside `CoordinatorLayout` (`fragment_article_manage.xml:25-35`); detail = `androidx.core.widget.NestedScrollView id=scrollView` (`fragment_article_detail.xml:34-38`); editor = plain `android.widget.ScrollView` with `fillViewport="true"` (`fragment_article_editor.xml:16-21`).
- Detail reading progress: `binding.scrollView.setOnScrollChangeListener` computes `scrollY * 100 / maxScroll` and writes `binding.readingProgress.progress` `ArticleDetailFragment.kt:319-328`; the indicator is switched visible only after content renders `:164`, and its `trackThickness` is 2dp `fragment_article_detail.xml:19`.
- Detail refresh hook: `navHandle.getLiveData<Boolean>("refresh_articles")` re-loads and forwards the flag to the previous back stack entry `ArticleDetailFragment.kt:71-79`; `article_manage_message` is shown as a Snackbar `:81-87`.

---

## 9. 设备实测复核（2026-09-16）

上表是静态事实。以下 4 条是我在真机上复核后**确认为缺陷**的，带证据；另有 1 条功能缺口和 1 条观察项。

设备：HUAWEI LIO-AN00 1176×2400 480dpi，`cmd uimode night no` 切浅色后复现（测试机平时是深色，所以这两个图标问题一直没被发现）。

### 9.1 【真 bug】`ic_favorite` 白色填充 + 无处 tint → 浅色模式下点赞图标不可见

`ic_favorite.xml:8` 是 `@android:color/white` 且 vector 内**没有** `android:tint`（对比 `ic_bookmark_outline.xml` / `ic_bookmark_filled.xml:2` 都带 `android:tint="?attr/colorOnSurface"`）。用在 `fragment_article_detail.xml:276-282` 的 `ivLike` 上时也没有 `app:tint` → 纯白 20dp 心形画在 `?attr/colorSurface` 上，浅色模式等于隐形。

证据：浅色模式截图（`%TEMP%\sk_art_detail_light.png`）底栏四个控件里，只有「点赞」左侧 20dp 是空白，收藏/分享/讨论图标正常。

受影响（raw `android:src`，无 tint）：
- `fragment_article_detail.xml:280`（20dp，**已截图确认**）
- `item_share.xml:123-129`（14dp + `alpha=0.4`，浅色下双重不可见）
- `item_snippet.xml:114-120`（同上）

不受影响：`item_share_manage.xml:90`、`item_snippet_manage.xml:50` 自带 `app:tint="?attr/colorOnSurfaceVariant"`；`fragment_share_detail.xml:275`、`fragment_snippet_detail.xml:246` 走 MaterialButton `app:icon`（M3 按钮样式自带 `iconTint`，未逐一截图验证）。

根因是**一个底栏里两套约定并存**：收藏图标自带 tint，点赞图标不自带。

### 9.2 【真 bug】`ic_empty` 是透明填充 → 空态图标从来没画出来过

`ic_empty.xml:7` = `@android:color/transparent`，无 tint → 该 vector 渲染结果为空。`git log --follow` 显示从 v1.1.0 首次提交（`0a40dd7`）起就是 transparent，**不是回归，是一直如此**。

后果是纯空白占位：`fragment_article_list.xml:45-50`（72dp + `marginBottom=12dp` = 84dp 空洞，且没有共享空态里的 `alpha`）、`fragment_home.xml:397-403`（64dp + 12dp，`alpha=0.5` 但 transparent 无所谓）。

对比：`ic_empty_comment.xml:6` / `ic_empty_notification.xml:6` 都带 `android:tint="?attr/colorOnSurfaceVariant"` + `alpha=0.3`，正常。

### 9.3 【真 bug】文章列表的错误态容器没有背景 → 会叠在已有卡片上

`fragment_article_list.xml:61-84` 的 `layoutError` **没有** `android:background`，而详情页里同一个块有 `?attr/colorSurface`（`fragment_article_detail.xml:227`）。

可复现路径（代码已确认，无需断网即可推断）：`ArticleListFragment.kt:71-76` 命中缓存时 `contentShown=true` 且列表已经显示 → 之后 `loadArticles` 失败 → `:98-104` `layoutError.fadeIn()`，而 `rvArticles` **不会**被隐藏（FrameLayout 状态栈里 `layoutError` 在最后 → 画在最上层）。即「列表先加载成功、再失败」时，「加载失败 + 重试」直接压在文章卡片上，没有底板。

### 9.4 【真 bug（文案）】空态文案写死"该分类暂无文章"

`fragment_article_list.xml:56` 写死 `android:text="该分类暂无文章"`，`ArticleListFragment` 全程不改 `tvEmpty.text`（只切可见性）。但该页也用于**全量**列表：首页入口传 `category_name="全部文章"`（设备实测 toolbar 显示"全部文章"），此时 `currentCategoryId=null`（`ArticleListFragment.kt:42-43`）。全站无文章时全量列表会显示"该分类暂无文章"。

### 9.5 【功能缺口】点赞没有任何"已赞"状态（收藏有）

`ArticleDetailFragment.kt:194-216` 点赞成功后只弹 Snackbar + `loadArticle()` 重载；`:163` 只把数字或"点赞"写进 `tvLikeCount`。**没有** `updateLikeUI()`，没有实心/空心图标切换、没有变色 —— 而收藏有完整三件套（`updateBookmarkUI() :298-311`：图标实心/空心 + 文案 + `colorPrimary`）。

根因在后端：`GET /api/articles/:id` 的响应体不含 `liked`（`data/model/Article.kt:18` 只有 `likeCount`），`liked` 只出现在点赞接口的响应里（`data/model/Common.kt:25`）。所以进入页面时客户端**无法知道**当前用户是否已赞。

同构问题：分享详情（`ShareDetailFragment.kt:181-182,241`）、代码片段详情（`SnippetDetailFragment.kt:135-136,204`）完全一样。

- 纯客户端可做：本地记住本次已赞状态（图标变实心/变色 + 文案"已赞"），代价是重进页面即丢失。
- 要做到跨会话正确，需后端详情接口补 `liked` 字段 → 属于后端改动，**需要单独授权部署**。

### 9.6 【观察项，非 bug】edge-to-edge 未消费导航栏 inset

`dumpsys window` 显示 `com.sknote.app/MainActivity` 窗口 frame = `[0,0][1176,2400]`，即 app 画到物理屏底；uiautomator 报的 `0..2301` 是 ROM 裁掉系统栏后的"可见内容区"（这解释了为什么 `bottomBar` 报 `[2256..2301]`、四个标签报 `[0,0][0,0]` —— **不是布局错误**，`FrameLayout` 是标准的 `0dp + weight=1`，192+6+1959+144=2301 自洽）。

像素测量（`sk_art_detail_light.png` 逐行扫描）：底栏分隔线在 y=2256-2258，栏背景是 `(254,247,255)`，图标/文字从 y=2303 起（48dp 栏 2256..2400 的内容居中位置）。当前设备 `settings get secure navigation_mode = 100`（手势导航），底栏最后 33dp 落在系统手势条区域里 —— 手势模式下显示正常，**三键导航下会被系统栏盖住**。属于全局设计决策，不是文章页独有。

### 9.7 复核过的、**不成立**的疑点

- `fragment_article_detail.xml:25-28` 的 `FrameLayout`（`0dp + weight=1`）写法正确，没有挤压底栏。见 9.6。
- 列表页标题"文章列表"虽写死在布局，但代码用 `category_name` 覆盖（`ArticleListFragment.kt:44-46`），首页传"全部文章"，**行为正确**。
- 文章页没有 `#RRGGBB` 硬编码色值；Kotlin 侧只有 `ArticleDetailFragment.kt:302-310` 两处主题属性解析。
- 文章页没有缺失 `contentDescription` 的图标按钮；没有 < 11sp 的字号（`UI_REVIEW.md:288` 记的 `item_article_simple.xml:35` 10sp **已过时**，现值 11sp）。
- ~~没有固定可点击高度 < 40dp~~ —— **这条我上一轮写错了，见 10.2**：`btnPreview` 实测就是 35dp。

---

## 10. 设备实测复核·续（2026-09-16，覆盖管理页 / 编辑器 / 骨架屏）

> **本节各条的处置（第四十二批，提交 `edeb447`）**：10.1 / 10.2 / 10.3 / 10.4 / 10.5 已修，
> 10.8 已视觉验证；10.6 与 10.7 查过后判断为**不该改**（禁用按钮 + 上下文提示优于错误文案；
> alpha 引导无害），理由与验证数据见 `UI_REVIEW.md` 第四十二批。

上一轮只上了列表页与详情页，这一轮补齐其余入口。设备：LIO-AN00，深色模式（用户日常态）。

覆盖到的界面：管理中心 → 文章管理 → 编辑器（**编辑态 / 预览态 / 创建态 / 空预览 / 禁用保存态**）→ 列表骨架（转场帧）。

### 10.1 【真 bug】编辑器预览态：浮动标签与 helper 文案叠在一起

`applyPreviewMode()`（`ArticleEditorFragment.kt:217-228`）隐藏的是**内层 EditText**：

```kotlin
binding.etContent.visibility = View.GONE      // :220  ← 只藏了输入框
binding.tvPreview.visibility = View.VISIBLE
```

而 `etContent` 是 `TextInputLayout`（id `layoutContent`，`fragment_article_editor.xml:118-138`）唯一的孩子，该布局带
`android:hint` + `app:counterEnabled` + `app:helperText`。输入框没了 → 框体塌缩成一条，但**浮动标签、helper、
计数器仍照原位布局**，于是三者挤在同一带里。

截图证据（编辑态与创建态都复现）：

```
支持标题、列表、引用、代码块等 Markdown        0/50000     ← helper（被挤成两行）
在此输入 Markdown 内容...                                  ← 浮动标签，压在上面
支持 Markdown：# 标题 **加粗** ...                          ← 编辑速查行
```

dump 证据：框体已消失，`textinput_helper_text` 落在 `y[1413..1504]`（**30.3dp = 两行**）、
`textinput_counter` 同在 `y[1413..1462]`，而 `tvPreview` 从 `y[1566]` 才开始 —— 30dp 的带里塞了三个元素。

**修法**：`binding.layoutContent.visibility` 取代 `binding.etContent.visibility`（2 行）。安全性已核：
`renderPreview()`（`:241`）、`saveArticle()`（`:266`）、`currentDraftState()`（`:324`）都只是**读** `etContent.text`，
GONE 状态下照常可读；`doAfterTextChanged`（`:130`）也照常触发。
顺带建议：预览态把 `fragment_article_editor.xml:140-146` 那条 Markdown 语法速查行也隐掉（它是编辑辅助信息，
夹在「内容（Markdown）」标题与预览正文之间是噪音）。

### 10.2 【确认】`btnPreview` 触控高度 35dp，低于 40dp 下限

真机 dump：`btnPreview` `y[1183..1288] x[864..1128]` = **105px = 35.0dp** 高、88dp 宽。
根因 `fragment_article_editor.xml:98-106`：`android:minHeight="0dp"` + 上下 4dp padding，高度完全由文字撑。
同屏其它按钮：`btnSave` 48dp、`text_input_end_icon` 48dp —— 只有它是 35dp。

### 10.3 【测量】管理页与列表页的正文左边距差 4dp

| 页面 | 标题左缘 | 构成 |
|---|---|---|
| 文章管理 | **x=84px = 28dp** | RV `padding=8dp` + item `margin=4dp` + row `padding=16dp` |
| 文章列表 | **x=96px = 32dp** | RV `padding=12dp` + item `margin=4dp` + card `padding=16dp` |

静态清单早就记了「6 个管理页 RV padding 8dp / 4 个列表页 12dp」，这里是它的**可见后果**：两页来回切时正文左缘
会跳 4dp。改一处即可对齐（建议向 12dp 靠，列表页那一套是 4 页一致）。

### 10.4 【文案】管理中心的行标签不齐

实测：文章管理 / 分类管理 / 讨论管理 / **代码片段** / 分享管理 —— 只有「代码片段」没有「管理」后缀，
和它下面「版本发布 / 账号管理」也不成体系。

### 10.5 【文档】`ArticleListFragment.kt:52-57` 的注释已过时，且会误导

注释称「文章列表接口（`articles.ts:22`）是明确列列名的查询，**不含** `a.content`……所以这里带过去的 Article
是「没有正文的」，会被详情页的 content 守卫拒绝并退回骨架路径 —— 这是刻意的」。

实际：`backend/src/routes/articles.ts:21-26` 明确带 `a.content`，注释还写着「**别把这一列删掉**」（第二十批）。
设备证据吻合 —— 从列表进详情，**入场帧就已经有正文**（转场中间帧截图：详情页在滑入过程中已渲染标题/作者/正文/相关讨论，
没有骨架）。

风险：后续读者可能照这条注释去"修"预填逻辑，把已经生效的优化改回去。

### 10.6 【观察·非 bug】`validateBeforeSave()` 的三个错误分支实际不可达

`updateFormState()`（`:333-341`）：`canSave = !isLoading && title.isNotEmpty() && selectedCategoryId != null &&
content.isNotEmpty()`，按钮在这三项齐全前是 `isEnabled=false` + `alpha=0.6f`。所以
`layoutTitle/layoutCategory/layoutContent.error`（`:294-311`）用户永远看不到 —— 是兜底死代码。

实际形态反而更好：**禁用按钮 + 上下文提示**（`tvDraftMeta` 的三态已逐一验过）：
「先填写标题，让文章主题更明确」/「请选择文章分类，方便内容归档」/「正文不能为空，至少补充主要内容」。

### 10.7 【观察】预览按钮用 alpha 表示"不可用"但仍可点

`updateFormState():342`：`btnPreview.alpha = if (content.isNotEmpty() || isPreviewMode) 1f else 0.6f`。
内容为空且非预览态时按钮变淡，但**没有 `isEnabled=false`** —— 点了会进预览并显示「暂无可预览内容」。
视觉（禁用）与行为（可用）不一致，结果无害，属于刻意留的引导。

### 10.8 【已视觉验证】列表骨架屏

转场中间帧抓到（点「查看全部」后立刻 `screencap`）：**5 张骨架卡**，每张为「chip 圆角块 + 标题条 + 摘要条 +
统计行小条」，卡片间距与圆角与 `item_article.xml` 一致 ✓，与 `layout_skeleton_card_article.xml` 的注释
（"Mirrors item_article.xml"）相符。

详情页骨架**未抓到**：从列表进详情时入场帧已有真实正文（见 10.5），当前数据下这条路径不走骨架 ——
与「预填生效」一致，不是缺陷。

---

## 11. 文章详情页逐处复核（2026-09-16）

标的：`fragment_article_detail.xml`（395 行）+ `ArticleDetailFragment.kt`（337 行）。
逐元素过一遍 → **6 处真问题 + 6 处小项**，另列复核通过的项。

### 11.1 【真问题】注释与代码完全相反 —— 且是上一批我修过的那条的**另一份副本**

`ArticleDetailFragment.kt:91-102` 声称：

> **文章没有任何一条列表接口返回 `a.content`** —— `articles.ts:22` …、`aggregated.ts:16` …、
> `bookmarks.ts:16`/`:82` 同样没有取这一列；… 因此这条预填路径**目前对所有入口都会被守卫拒绝，页面走骨架**
> —— 与改造前一致，不会更糟。

实测**四个端点全部带 `a.content`**，而且各自的注释都写明了理由：

| 端点 | 事实 |
|---|---|
| `articles.ts:21-26` | 带；注释：「列表刻意带上 a.content … **别把这一列删掉**」（第二十批） |
| `aggregated.ts:18` | 带（首页聚合） |
| `bookmarks.ts:15-17` | 带；注释：「带 a.content 是为了让收藏点进详情时能立即渲染正文」 |
| `bookmarks.ts:82-84` | 带（阅读历史，同上） |

真机佐证：从文章列表进详情，**转场中间帧就已经有正文**（无骨架）。
⇒ 第四十二批修的是 `ArticleListFragment:52-57`，这是**同一份注释的另一个副本**，当时只改了一处。

### 11.2 【真问题】顶栏在"无预填"与"加载失败"时**没有标题**

`fragment_article_detail.xml:8-13` 没写 `app:title`；而 `binding.toolbar.title = "文章详情"` 在
`ArticleDetailFragment.kt` 里**只出现一次，且在 `:156` 的 article observer 内部**（`toolbar.title` 全文件唯一）。
⇒ 无预填走骨架时、以及**加载失败时**，顶栏是"只有返回箭头的空标题栏"。错误态这条路是可达的。
有预填时 observer 同步触发、首帧就有标题，所以平时看不见这个缺陷。

### 11.3 【真问题】作者头像永远是占位人像（真实头像可达却没用）

`Article.kt:15` 有 `authorAvatar`，接口返回 `author_avatar`（`articles.ts:79`），
而讨论区三处都用 Glide 加载真实头像（`DiscussionAdapter:91`、`CommentAdapter:145`、
`DiscussionDetailFragment:414`）。文章详情这个 ImageView（`fragment_article_detail.xml:90-96`）
**连 `android:id` 都没有** → 代码结构上不可能设置它，永远显示写死的 `ic_person`。

### 11.4 【真问题】正文不能选中复制、链接点不动

`tv_content` 没有 `textIsSelectable`；全 app 搜不到 `setMovementMethod`；
而 Markwon 的 MovementMethodPlugin 是**可选插件**（已在 `markwon-core-4.6.2.aar` 内确认类存在：
`io/noties/markwon/movement/MovementMethodPlugin.class`，同时也有 `core/spans/LinkSpan.class`），
`:58-60` 只 `usePlugin(GlideImagesPlugin)` → `LinkSpan` 收不到点击事件。
修法：`Markwon.builder(...).usePlugin(MovementMethodPlugin.create())` + 正文 `textIsSelectable="true"`。

### 11.5 【真问题】底栏"讨论"图标用了错的配色角色

四个图标逐个取框内最亮像素（深色模式）：

| 按钮 | 实测 RGB | 角色 |
|---|---|---|
| 点赞 | (230,224,233) | colorOnSurface ✓ |
| 收藏 | (230,224,233) | colorOnSurface ✓ |
| 分享 | (230,224,233) | colorOnSurface ✓ |
| **讨论** | **(207,229,237)** | `ic_discussion` 内部 tint = **colorOnSecondaryContainer**（"色块上的图标"角色），却站在 `colorSurface` 上 |

⇒ 同一排四个图标两套来源，讨论那枚偏青。修法与点赞相同：底栏那处加 `app:tint="?attr/colorOnSurface"`。
**副产物**：这条同时证明第四十一批给定点赞的 tint 与旁边两枚**逐像素一致** ✓。

### 11.6 【真问题·轻】不可滚动的文章上，滚动进度条常显一条 0% 轨道

像素实测：轨道带 `y292..296` 均值 `(54,52,59)`，而上下周边是 `(20,18,24)` —— 2dp 全宽轨道确实可见。
短文（"测试"正文只有 1 个字）`maxScroll = 0`，代码守卫 `if (maxScroll > 0)` 正确（**不会除零崩溃** ✓），
但轨道永远停在 0%，看起来像一条坏掉的加载条。建议 `maxScroll <= 0` 时把它置 GONE。

### 11.7 小项（记录备查，非缺陷）

1. 分类 chip 内边距 10/3dp，而 item 里的 chip 是 8/2dp；
2. 标题 24sp vs 分享/片段详情 20sp、讨论页头 21sp（已知一致性项）；
3. 同一页两套卡片语言：作者卡（填充 `colorSurfaceVariant` + 12dp 内边距）vs 相关讨论卡
   （1dp 描边 + 14dp 内边距）；且 `colorSurfaceVariant` 是 M3 弃用角色（第四十批刚把分析器统一到
   surface 阶梯）；
4. 作者卡内混用 13sp / 11sp / 12sp —— 时间(11sp)比阅读数(12sp)还小；
5. 同一文件命名混用：`tv_title`/`tv_author`/`tv_content`（下划线）vs `tvTime`/`tv_meta`/`tvCategory`（驼峰）；
6. 「相关讨论」卡不显示条数 —— **不是客户端漏了**：详情接口 `articles.ts:78-84` 是纯 `SELECT a.*` + join，
   没有回复数子查询，模型里也没有该字段；要显示需后端加（属后端改动）。

### 11.8 复核通过的（本次无需动）

- 骨架 / 错误 / 重试三态齐全，`revealContent()` 的两条进入路径（有预填 / 无预填）都正确；
- 预填守卫 `takeIf { !it.content.isNullOrEmpty() }` 正确；实测入场帧即有正文；
- 阅读进度的除零守卫与 `coerceIn(0,100)` 正确；
- 底栏几何正常（48dp / 四等分 / 三根 1dp×24dp 分隔）；最后 33dp 落在系统手势区属全局现象（见 9.6）；
- 收藏三件套完整（实心/空心图标 + 文案 + 主色）；
- 编辑入口角色门控（admin 或作者本人）正确；`updateEditVisibility()` 在角色缓存前后各调一次，无竞态。
