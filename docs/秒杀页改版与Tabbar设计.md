# 秒杀页改版与 Tabbar 新增「秒杀」设计稿 · v2

> 状态：**已实施并验证（2026-10-20）**
> 关联：`秒杀专场页设计.md`（B4 原稿）
> v2 变更：**弃用 v1 的墨黑 `#1C1C1E` 方案**，全页对齐现有界面的色彩与布局语言
> （实测采样：白底 46px 导航、`#f7f8fa` 页面底、白卡 `radius 10~12px`、
> 强调色一律 `rgb(107,163,200)`、按钮白字 `font-weight:600`、tab 用 `van-tabs`）。

---

## 1. 改版动机（不变）

| 问题 | 具体表现 |
|---|---|
| 配色与全站冲突 | 秒杀页用 Vant 默认红 `#ee0a24`，全站唯一异类；价格/进度/按钮全是红 |
| 信息层级平均 | 卡片标题比商品名还显眼；一屏只放 1.5 个商品 |
| 倒计时语义错 | 顶部「距结束」与下方任一卡片无对应（多场次平铺） |

**改版方向**：仍做「抢购现场」（场次分组 + 双列网格 + 密度提升），
但**视觉完全融入全站**——紧迫感靠结构（场次、倒计时、进度条）表达，不靠换色系。

## 2. 设计系统 v2（对齐全站实测值）

| 角色 | 色值 | 全站出处 |
|---|---|---|
| 页面底 | `#f7f8fa`（`var(--van-background-2)`） | 购物车 / 订单列表 / 我的 |
| 卡片 | `#fff`，`border-radius: 10px`，`padding: 12px`，卡间距 10px | 订单列表 `.order` |
| 强调色 | `rgb(107,163,200)` 品牌浅蓝 | 订单状态字 / 结算按钮 / 退出登录 |
| 按钮 | 浅蓝底 + 白字 + `font-weight: 600`（覆盖 `--van-button-primary-*` 两个变量） | `.cart__empty-btn--brand` |
| 正文 / 价格 | `#323233`，价格 `font-weight: 500` | `ProductCard` / `.order__amount` |
| 次要文字 | `#969799` | 全站统一 |
| 分隔线 | `0.5px solid #f2f3f5` 或 `var(--van-border-color)` | `.member__card-head` |
| 禁用态 | 底 `#f2f3f5`、字 `#969799` | Vant default button |
| 导航 | `PageNavBar` 白底 46px，标题居中 | 所有二级页 |

**红色彻底退场**：`#ee0a24` 在本页不再出现（订单页失效原因那种纯错误提示除外）。
「即将开始 / 进行中」的紧迫感用浅蓝深浅与进度条表达。

## 3. 秒杀页结构 v2

```
┌──────────────────────────────────────┐
│ ←  限时秒杀        距结束 01:57:57     │  PageNavBar 白底（右槽倒计时）
├──────────────────────────────────────┤
│ 📍 张三 138****8000            ›      │  地址栏白条（沿用 B4 结构）
│    广东省深圳市南山区科技园 8 号        │
├──────────────────────────────────────┤
│ 10:00    14:00    20:00    00:00      │  场次栏：van-tabs 样式
│ 已开抢   即将开始  未开始   未开始      │  选中下划线 = 品牌浅蓝
├──────────────────────────────────────┤  页面底 #f7f8fa
│ ┌─────────┐  ┌─────────┐             │
│ │  [图]    │  │  [图]    │            │  白卡 10px 圆角
│ │ 精选牛油果│  │ 冷萃咖啡  │            │
│ │¥9.90 ¥39.9│ │¥29.9     │            │  价格墨色 500
│ │▓▓▓░░ 剩2 │  │▓▓▓▓▓ 剩0 │            │  进度条浅蓝填充
│ │  [立即抢购]│ │ [已抢光]  │            │  浅蓝按钮 / 灰禁用
│ └─────────┘  └─────────┘             │
└──────────────────────────────────────┘
```

### 3.1 导航条

- 沿用 `PageNavBar`（白底、居中标题、深灰返回箭头）——**不做人头图**，
  与我的订单/收藏等二级页完全同构。
- 倒计时放 `#right` 插槽：`距结束 01:57:57`，`color: rgb(107,163,200)`、
  `font-variant-numeric: tabular-nums`、13px（同 B4，只把红色换成品牌蓝）。

### 3.2 地址栏

**保留 B4 的白条结构**（它与全站其他页面的「信息条」一致，且 `seckill-confirm-verify` 等脚本依赖其行为）：

- 白底、`padding: 12px 16px`、下边 `0.5px #f2f3f5`；定位图标浅蓝（现状已是）。
- 三态行为不变（未选 / 已选 / 无地址直跳）；未选点抢购仍 toast + 抖动高亮。
- 抖动高亮底色 `#fff7f7` 改为 `#f0f7fb`（浅蓝提示底，与全站一致）。

### 3.3 场次栏

- **样式直接用 `van-tabs`**（与订单列表页同款：白底、选中加深、品牌色下划线
  `line-width="20px"`），副标题「已开抢/即将开始」写进 `title` 插槽第二行。
- 分组逻辑（前端按 `startTime` 的 `HH:mm` 分组、默认选中第一个「已开抢」、
  零后端改动）与 v1 完全一致。
- 吸顶：`van-tabs sticky`（订单页同款）；祖先链不得有 `overflow: hidden`。
  **实施时改为 CSS sticky**（Vant 的 prop 本项目不生效，见 §9.2）。
- 切场次**不需要**锁商品区最小高度：场次数据是 `activities` 的内存切片，
  切换只触发一次同步重渲染，没有「清空→等接口→再填」的空窗。
  （初版按首页分类切换的写法加了 `minHeight` 锁，实施后复查发现下一帧就解锁、
  实际挡不住任何塌高，属死代码，已删除。）

### 3.4 商品卡（双列）

`grid-template-columns: repeat(2, minmax(0,1fr)); gap: 10px; padding: 12px;`，页面底 `#f7f8fa`。

| 区域 | 规格 |
|---|---|
| 卡片 | 白底 `radius 10px`，`overflow: hidden` |
| 图片 | `aspect-ratio: 1`，`object-fit: cover` |
| 角标 | 左上「限购N」：**固定 30×20px**（flex 居中，不靠 padding 撑），浅蓝底 `rgb(107,163,200)` 白字 11px/500，`radius 0 0 8px 0`，`overflow: hidden` 兜底 |
| 抢光蒙层 | `rgba(255,255,255,0.6)` + 居中「已抢光」12px `#969799` |
| 商品名 | 13px `#323233` 单行省略，`padding: 8px 10px 0`（活动 title 不再单独展示，并进副行） |
| 副行 | 活动标题 + 规格 11px `#969799` 单行省略 |
| 价格行 | 秒杀价 16px/500 `#323233` + 原价 11px `#c8c9cc` 划线（同 `ProductCard` 层级） |
| 进度条 | 高 4px，轨道 `#f2f3f5`，填充 `rgb(107,163,200)`；右侧 `剩 N 件` 11px `#969799` |
| 按钮 | 通栏小按钮：进行中 = 浅蓝底白字 600（13px，`radius 999px`，高 30px）；其余 = 灰底 `#f2f3f5` 灰字禁用 |

**按钮状态机（行为同 B4）**：

| 场次状态 | 库存 | 文案 | 样式 |
|---|---|---|---|
| 即将开始 | — | `14:00 开抢` | 灰底 `#f2f3f5` / `#969799` 禁用 |
| 进行中 | > 0 | `立即抢购` | 浅蓝 `rgb(107,163,200)` 白字 600 |
| 进行中 | 0 | `已抢光` | 灰底禁用 + 图蒙层 |
| 已结束 | — | `已结束` | 灰底禁用 |

已抢光整卡 `opacity: .6`（同购物车失效项 `.cart-item--invalid`）。

### 3.5 抢购确认层

沿用 B4 结构，仅对齐色板：标题 15/500；商品块底 `#f7f8fa`；确认按钮改为
浅蓝底白字（`--van-button-primary-background: rgb(107,163,200)`），取消键保持
plain 白底。

### 3.6 交互细节（全部延续 B4 已验证行为）

轮询 200/400/800/1600/2000 总 15s 失活即停；成功跳订单详情；`submitting`/`buying`
防连点；抢购中（`buying`）禁切场次并置灰场次栏。

## 4. 首页秒杀模块 v2

位置不变（`.home__sticky` 内、搜索栏与分类栏之间），替换红色渐变横条：

```
┌────────────────────────────────────┐
│ ⚡ 限时秒杀      距结束 01:57:57  ›  │  14/600 #323233 + 浅蓝倒计时
│ ┌────┐┌────┐┌────┐                 │
│ │ 图 ││ 图 ││ 图 │                 │  72px，radius 8px
│ │¥9.9││¥19.9││¥29.9│              │  12/500 #323233 + 9px 划线
│ └────┘└────┘└────┘                 │
└────────────────────────────────────┘
```

- 外壳：白底 `radius 10px`，`margin: 0 12px 8px`，`border: 0.5px solid #f2f3f5`
  （比红色渐变横条轻一档，与分类栏的白底区分靠卡片描边）。
- ★ 商品行**固定 3 列网格**（`grid-template-columns: repeat(3, minmax(0,1fr))`），
  不用 `flex: 1`：只有 1 个活动时 `flex: 1` 会把那一项拉满整行，
  正方形缩略图随之变成 344×344，模块高度飙到 **429px（占半屏）**。
  固定列后单项恒为 1/3 宽，1/2/3 个活动模块高度都稳定在 ~194px。
- 不足 3 个时补一格「查看全部 ›」（`grid-column: span 剩余列`，浅灰底、品牌蓝字），
  既占住空白又给出进专场的入口。
- 标题行：左「限时秒杀」14/600 + 小闪电 SVG（浅蓝）；右倒计时 12px 浅蓝 `tabular-nums`；
  无进行中活动时显示「下一场 14:00 开抢」；无任何活动整块不渲染。
- 数据与点击行为同 v1：`onActivated` 拉 3 个进行中活动；整块与单品点击都进 `/seckill`。

## 5. Tabbar 新增「秒杀」（与 v1 一致，未变）

顺序 **首页 · 秒杀 · 购物车 · 我的**，四项均分。改动清单照旧：

| 文件 | 改动 | 漏掉的后果 |
|---|---|---|
| `layouts/TabBarLayout.vue` | `TAB_ITEMS` 第 2 位插入 | — |
| 同上 | `indicatorLeft` 的 `/3→/4`、`/6→/8` | ★ 指示条位置全错 |
| 同上 | `FULLSCREEN_ROUTES` 删 `'seckill'` | ★ 秒杀页 tabbar 消失 |
| `router/index.ts` | `TAB_ORDER` → `home:0, seckill:1, cart:2, member:3`；`seckill` 路由 `depth: 2→1` | ★ 滑动方向反 / tab 互切走 forward |
| `components/TabBarIcon.vue` | `IconDef` 改 `paths: string[]`；接入 `限时.svg` 两条 path | — |

图标接入规范不变：删元数据、保留 `viewBox 0 0 1024 1024`、`d` 逐字符照抄、`fill: currentColor`。

## 6. 文件改动清单（同 v1）

`SeckillView.vue`（重写 template/style，script 逻辑保留 + 场次分组 computed）、
`HomeView.vue`（换秒杀模块）、`TabBarLayout.vue`、`router/index.ts`、`TabBarIcon.vue`、
本设计稿对应更新。**后端 / 数据库 / api / shared-types 零改动**。

## 7. 验证计划

| 脚本 | 改动 |
|---|---|
| `seckill-page-verify.mjs` | `.addr-bar` 保留；`.card`→`.sk-card`、`.card__btn`→`.sk-card__btn`、`.card__progress-bar`→`.sk-card__bar` |
| `seckill-confirm-verify.mjs` | 类名 `.confirm*` 不变，确认按钮文案不变 |
| `tabbar-icon-verify.mjs` / `tabbar-layout-probe.mjs` | 3 项 → 4 项 |
| 新增 `seckill-tab-verify.mjs` | 4 项等宽、指示条中心在 2/4 处、点「秒杀」进 `/seckill` 且 tabbar 仍在、方向 `tab-left` |
| 新增 `seckill-session-verify.mjs` | 场次按 `HH:mm` 升序、默认选中第一个已开抢、切未开始场次倒计时变「距开抢」 |

手动验收（390×844）：导航倒计时不抖动；地址三态 + 未选不发请求；场次栏吸顶
`rect.top === 0`；切场次不塌高；抢购成功跳订单详情可返回；首页模块无活动不渲染；
全页**无一处 `#ee0a24`**（截图比色）。

## 8. 实施顺序（不变）

① tabbar 加项+图标 → ② 秒杀页改版 → ③ 首页模块 → ④ 脚本同步 + 新增验证 → ⑤ 文档更新。
每步一条 commit。

---

## 9. 实施记录（2026-10-20，390×844 CDP 实测）

### 9.1 实际改动

| 文件 | 内容 |
|---|---|
| `components/TabBarIcon.vue` | `IconDef.path` → `paths: string[]`；新增 `seckill` 图标（限时.svg 两条 path，d 逐字符照抄） |
| `layouts/TabBarLayout.vue` | `TAB_ITEMS` 4 项；`indicatorLeft` 分母改 `/4`、`/8`；`FULLSCREEN_ROUTES` 删 `seckill` |
| `router/index.ts` | `TAB_ORDER` 加 `seckill:1`（cart→2、member→3）；`seckill` 路由 `depth` 2→1 |
| `App.vue` | `CACHED_VIEWS` 加 `SeckillView`（秒杀页成为 tab 页，切回保留滚动位置与数据） |
| `views/SeckillView.vue` | 按 v2 重写 template/style；script 逻辑保留 B4 全部行为，新增场次分组与倒计时联动 |
| `views/HomeView.vue` | `.seckill-entry` 红色横条 → `.seckill-module` 白卡模块；`onActivated` 拉活动 + 启动秒级时钟，`onDeactivated` 停 |

### 9.2 实施中发现并修正的两处（与设计稿有出入）

1. **Vant `van-tabs` 的 `sticky` prop 在本项目不生效**：DOM 里有 `.van-sticky` 包裹层，
   但计算样式恒为 `static`，滚动后不会切 fixed（`.sk-head` 的 `position: sticky` 却验证可用）。
   → 改为在 `.sk-tabs` 根上直接写 `position: sticky; top: 44px; z-index: 2`，去掉 prop 与 offset-top。
2. **`.van-tab__text` 高度只按一行算（实测 18px）且 `overflow: hidden`**：两行标题
   （时刻 + 状态）会被整行裁掉，视觉上只剩一行加省略号。
   → 用 `:deep(.van-tab__text) { display: block; height: auto; padding: 4px 0 }` 放开高度。

另：抢购进行中（`buying`）禁止切场次的逻辑，实现改为在 `onSessionClick` 入口直接 return
（设计稿提的「置灰场次栏」未做，光标与禁用反馈由按钮自己的 disabled 承担）。

### 9.3 实测结果

```
tabbar: 4 项 [首页 秒杀 购物车 我的]，选中指示条 left = calc(37.5%)（第 2 格中心）
tab→seckill: 路径 /seckill、active=秒杀、data-page-dir=tab-left、tabbar 仍在
场次分组: 2 条活动 → [00:00 已开抢][21:30 即将开始]，默认选中第一个已开抢
切到未开始场次: 倒计时变「距开抢」、按钮「21:30 开抢」且 disabled；切回恢复「距本场结束」+「立即抢购」
吸顶: 滚动到中部 .sk-head top=0、场次栏 top=44（两者组成吸顶区，不重叠）
商品卡: 双列（前两张 rect.top 相同=172）、限购角标/划线价/进度条/浅蓝按钮；整页无 #ee0a24
首页模块: 渲染在 top=54（搜索栏下）、标题行含倒计时、点击整块进 /seckill
```

验证脚本：`seckill-page-verify.mjs`（已同步选择器）、`seckill-tab-verify.mjs`（新增，
含 tabbar / 页签跳转 / 场次切换 / 吸顶 / 首页模块；场次切换用例需要 ≥2 个不同 HH:mm 的活动，
不足时打印 SKIP）。

---

## 10. 完成度复查（2026-10-20 15:20）

### 10.1 验收项逐条

| 验收项 | 结果 | 证据 |
|---|---|---|
| tabbar 4 项、图标为 SVG、无字体图标残留 | ✅ | `tabbar-icon-verify.mjs` PASS（含 seckill 页，viewBox 逐图标 1024/1024/1028/1024） |
| 指示条落在第 2 格中心 | ✅ | `indicatorLeft = calc(37.5%)` |
| 点「秒杀」进专场页且 tabbar 仍在 | ✅ | `seckill-tab-verify.mjs`：path=/seckill、active=秒杀、tabbarVisible=true、dir=tab-left |
| 场次按 `HH:mm` 分组、默认第一个已开抢 | ✅ | 2 场时 `[00:00 已开抢][21:30 即将开始]`，默认选中 00:00 |
| 切场次联动倒计时与按钮 | ✅ | 切 21:30 → 「距开抢」+「21:30 开抢」disabled；切回 → 「距本场结束」+「立即抢购」 |
| 场次栏吸顶 | ✅ | 滚动后 `.sk-head` top=0、场次栏 top=44 |
| 倒计时不抖动 | ✅ | 连续 3 秒测量宽度恒 114.98px、右缘位置不变（`tabular-nums`） |
| 双列卡片 / 无红色 | ✅ | 前两张卡 rect.top 相同；`hasRed=false` |
| 抢购确认层 | ✅ | `seckill-confirm-verify.mjs`：标题/商品/规格/价格/地址/两按钮齐全，确认键品牌浅蓝 |
| 无活动空态 | ✅ | `enabled=0` 时首页模块整块不渲染（分类栏上移到 54）、秒杀页显示「暂无秒杀活动」，验后已恢复 |
| 未选地址不发请求 | 沿用 B4 | 逻辑未改动（`onBuyClick` 在 `selectedAddress` 为空时 return），本次只改外观 |
| 抢购成功跳订单详情 | 沿用 B4 | 轮询与跳转逻辑未改动 |

### 10.2 复查中修掉的问题

- **删除死代码**：初版给商品区加了 `sectionMinHeight` 高度锁（照抄首页分类切换），
  但场次数据在内存里、没有加载空窗，锁在 `nextTick` 就解除，挡不住任何塌高。
  已删除 `sectionRef` / `sectionMinHeight` / `onSessionChange` 与模板上的 `:style`、`ref`。
- `seckill-confirm-verify.mjs` 选择器同步为 `.sk-card__btn`；`tabbar-icon-verify.mjs`
  断言 3 项 → 4 项并新增 seckill 页检查。

### 10.3 遗留（非本次范围）

- `vue-tsc` 仍有 2 个既有错误：`src/utils/request.ts:57`（拦截器返回类型）、
  `vite.config.ts:3`（缺 `@types/node`），与本次改动无关，未顺手改。
- `vite build` 因 dist/assets 文件数超过安全删除阈值被环境拦截，本次用 `vue-tsc --noEmit` 代偿。

---

## 11. 变更：删除标题条倒计时，场次时间下沉到卡片（v2.1）

### 11.1 为什么改

v2 把倒计时放在标题条右槽（`#right` 插槽 / `.sk-head__countdown`），语义是「当前选中场次」。
但一个页面有多个场次、同一场次下有多张卡，**顶部只有一个倒计时**，用户看某张卡时
还得回头确认「这个倒计时说的是哪一场」——尤其是切过场次之后。这次把时间与倒计时
都放到卡片自己身上：一张卡只讲自己这场，不用再跨区域对照。

### 11.2 具体改动（`views/SeckillView.vue`）

| 位置 | 改动 |
| --- | --- |
| `.sk-head` | 删除右上角 `{{ countdownLabel }} {{ countdownText }}`，标题条只剩「限时秒杀」（高度仍 44px，场次栏 `sticky top:44px` 不变） |
| script | 删除 `countdownLabel` / `countdownTarget` / `countdownText` 三个 computed（只服务于顶部倒计时）；`activeSession`、`sessionStatus`、`formatDuration` 保留 |
| 卡片 | `.sk-card__body` 顶部新增 `.sk-card__time` 一行：左 `HH:mm-HH:mm`（活动自身的 `startTime`-`endTime`，11px `#646566`），右本场倒计时（10px，走秒时红 `#ee0a24`/500，`已结束` 用 `#969799`） |
| 新增函数 | `timeRange(activity)` → `18:00-20:00`；`cardCountdown(activity)` → 未开始「距开抢 hh:mm:ss」/ 进行中「距结束 hh:mm:ss」/ 结束「已结束」 |
| 样式 | 删 `.sk-head__countdown` / `.sk-head__time`；新增 `.sk-card__time`（flex 两端对齐 + `tabular-nums` 防每秒抖动）、`.sk-card__time-range`、`.sk-card__time-cd`、`.sk-card__time-cd--on` |

要点：
- 卡片时间取自**活动自身**，不读 `activeIndex`，所以切场次时随 `sessionItems` 换切片自然刷新，不会串场。
- 倒计时仍由 `now`（每秒 setInterval）驱动，到点自动从「距开抢」变「距结束」再变「已结束」。
- 场次栏（`van-tabs` 两行标题）保留不动 —— 它是导航，不是「右上角那个时间」。

### 11.3 验证（`seckill-card-time-verify.mjs`，390×844 CDP）

| 用例 | 结果 | 证据 |
| --- | --- | --- |
| 标题条无倒计时 | ✅ | `hasCountdown=false`、`innerText === '限时秒杀'`、子节点 1 个 |
| 标题条高度不变 | ✅ | `height=44`（吸顶区配套） |
| 每张卡有时间区间 | ✅ | `00:00-00:00` / `00:00-23:59`，均匹配 `^\d{2}:\d{2}-\d{2}:\d{2}$` |
| 时间块在盒子内 | ✅ | 每张卡 `rangeRect` 落在 `cardRect` 垂直范围内 |
| 倒计时非空且走秒时为红 | ✅ | 「距结束 02:28:02」等，`color: rgb(238, 10, 36)`；`已结束` 为 `rgb(150,151,153)` |
| 与选中场次一致 | ✅ | 卡片区间前缀 === 当前 tab 时刻 |
| 红色只出现在倒计时 | ✅ | 整页唯一 `#ee0a24` 是 `.sk-card__time-cd--live`，按钮/进度条/下划线仍是浅蓝 |

### 11.4 后续微调：倒计时改红（同 commit 追加）

用户要求「倒计时的颜色改为红色」，把 `.sk-card__time-cd` 从浅蓝改为 Vant 标准红 `#ee0a24`。

- 新增 `isCounting(activity)`（status 1/2）驱动 `--live` 类，替换原来的 `--on`（原语义只覆盖「进行中」）；
  未开始的「距开抢」也在走秒，一并标红。
- `已结束` 不标红 —— 它是静态结论，跟着红会让人误以为还在跑，保持 `#969799`。
- 10px 小字加 `font-weight: 500`，红色在浅底上才压得住。
- 红色语义收窄为「时间在跑」：品牌浅蓝仍是可点/强调色（按钮、进度条、下划线、tab 状态字），
  两套色分工明确，不复用。

`vue-tsc --noEmit` 仍只有那 2 个既有错误，无新增。

### 11.5 后续微调：限购角标改为 30×20（commit 见 git log）

需求：「左上角的限购框改为 30*20px，里面的文字也按此比例放大」。

改前尺寸是 **padding 撑出来的 41.8×18**（`padding: 2px 7px` + `font-size: 10px` + `line-height: 14px`），
不是设计给定的值，所以这次直接写死盒子：

| 项 | 改前 | 改后 |
| --- | --- | --- |
| 盒子 | 41.8×18（padding 撑开，改字号会跟着变形） | **30×20**（`width/height` 写死 + flex 居中，`padding` 归零） |
| 字号 | 10px | **11px**（高度 18→20 约 1.11 倍，字号同比例放大） |
| 文案 | `限购 {{ n }}` → 渲染为「限购 1」 | `限购{{ n }}` → 「限购1」（30px 宽度装不下空格） |
| 兜底 | 无 | `white-space: nowrap` + `overflow: hidden`（两位限购会到 34px，宁裁半个字也不许撑破盒子盖住图） |

验证（`seckill-limit-badge-verify.mjs`，390×844 CDP）：

| 用例 | 结果 | 证据 |
| --- | --- | --- |
| 盒子恒 30×20 | ✅ | `getBoundingClientRect()` = `30 × 20` |
| 字号 11px | ✅ | `fontSize: 11px` |
| 文案不裁切 | ✅ | `scrollWidth <= clientWidth`（「限购1」实测约 28px） |
| 仍贴图左上角 | ✅ | 角标 `top/left` === `.sk-card__img-wrap` 的 `top/left`（171.8 / 12） |

★ 已知取舍：`limit_per_user ≥ 10` 时（当前数据只有 1）「限购10」约 34px，会被 `overflow: hidden`
裁掉半个字。若以后要支持两位数，把 `width: 30px` 换成 `min-width: 30px` 即可，其余不用动。

### 11.6 后续微调：时间区间带上日期

需求：「开始和终止时间把日期也加上」。

- 新增 `formatDateTime()` → `10-21 18:00`（`MM-DD HH:mm`；秒杀是短周期活动，年没意义）。
- `timeRange()` 由 `18:00-20:00` 改为 `10-19 00:00 ~ 10-22 00:00`，分隔符用 ` ~ ` 而不是 `-`：
  日期里本来就有 `-`，再拿 `-` 当区间分隔符会读成 `00:00-10`。
- **排布由「一行两段」改为「两行」**（`.sk-card__time` 从 `row + space-between` 改 `column + gap:2px`）：
  带日期后区间约 126px，卡片内容区只有 158px，再和倒计时抢一行必有一方被省略号截断。
  现在第一行区间（10px `#646566`）、第二行倒计时（11px，走秒红）各占一行，都不用省略。
- 场次 tab（`van-tabs` 两行标题）仍只显示 `HH:mm`：它是导航，宽度只有 1/4 屏，塞不下日期。

验证（`seckill-time-date-verify.mjs`，390×844 CDP）：

| 用例 | 结果 | 证据 |
| --- | --- | --- |
| 区间带日期 | ✅ | 「10-19 00:00 ~ 10-22 00:00」，匹配 `^\d{2}-\d{2} \d{2}:\d{2} ~ \d{2}-\d{2} \d{2}:\d{2}$` |
| 两行不挤 | ✅ | 倒计时 `top` ≥ 区间 `bottom` |
| 两段都不截断 | ✅ | `scrollWidth <= clientWidth`（区间 158 / 内容区 158） |
| 字号与颜色 | ✅ | 区间 10px、倒计时 11px `rgb(238,10,36)` |

★ 遗留提示（未改，非本次范围）：`sessions` 的分组键仍是 `HH:mm`，如果两个活动分别在不同日期的
同一时刻开抢，会被并成同一场。当前数据都在同一天，暂不触发；要支持跨天，把 `formatClock` 换成
`formatDateTime` 当 key 即可（tab 文案可只取后半段）。
