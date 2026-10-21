<script setup lang="ts">
import { RouterView } from 'vue-router'
import TabBarLayout from '@/layouts/TabBarLayout.vue'
import { pageTransition } from '@/router'

/**
 * 需要缓存的页面，按组件名匹配（<script setup> 由文件名推断组件名）。
 * 底部 tabbar 的四个页面全部缓存：切走再切回不再重新请求、滚动位置也还在。
 * 购物车的数据新鲜度由页面自身的 onActivated 静默刷新兜底（见 CartView）；
 * 秒杀页同理，切回来时 onActivated 静默重拉活动与库存（见 SeckillView）。
 *
 * CheckoutView（确认订单）也在这里：它跟收货地址页之间是「底部上滑层」关系，
 * 不缓存的话每次从地址页返回都要重新挂载并重拉购物车 + 地址列表，
 * 上滑露出的那一瞬间下面还是空的，比硬切更明显。
 * 缓存后返回时商品与地址立刻就在，再由它自己的 onActivated 静默刷新（见 CheckoutView）。
 */
const CACHED_VIEWS = ['HomeView', 'SeckillView', 'CartView', 'MemberView', 'CheckoutView']
</script>

<template>
  <TabBarLayout>
    <!-- 页面切换过渡：方向由路由层级决定，tab 三页互切按左右顺序滑动（见 router/index.ts）；
         被缓存的页面切回来时商品数据与滚动位置都还在 -->
    <RouterView v-slot="{ Component, route }">
      <Transition :name="pageTransition">
        <KeepAlive :include="CACHED_VIEWS">
          <component :is="Component" :key="route.path" />
        </KeepAlive>
      </Transition>
    </RouterView>
  </TabBarLayout>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

/* 关掉视口级的 overscroll 链：移动端浏览器（Android Chrome / 微信 webview）自带的下拉刷新
   会和首页的 van-pull-refresh 叠着动 —— 页面被拉下来、松手又被浏览器弹回去，两层动画打架。
   contain 只切断「滚到边界后继续拖拽交给浏览器」这一层，桌面端与内部滚动容器（分类栏横滑、
   分享面板等）都不受影响。 */
html,
body {
  overscroll-behavior-y: contain;
}

:root {
  /* 页面背景色：同步覆盖 Vant 的页面背景变量，避免 van-image 等组件仍用旧的灰色底 */
  --van-background: rgb(255, 255, 255);
  /* 页面底色（灰底白卡用的那一层灰）。
     ★★ 不要用 Vant 的 --van-background-2 当页面底色：它是「白色表面」变量，
       nav-bar / tabbar / tabs 导航 / cell / search / popup 等 34 个组件都继承它
       （`--van-nav-bar-background: var(--van-background-2)` 之类），
       改它等于把 tabbar、吸顶导航、搜索框、弹层一起染灰。
     ★ 各页面根节点（.home / .seckill / .cart / .order-list）用它，
       目前只有 .seckill 接上；其余仍是白底，见 docs/秒杀页改版与Tabbar设计.md §11.12。 */
  --tc-bg-page: #f7f8fa;
  /* 底部 tabbar 选中项颜色（图标与文字同源，改这一个变量即可）。
     Vant 默认取 --van-primary-color（蓝 #1989fa），这里直接改掉选中态变量。
     注意：与首页搜索框描边用的 --color-brand（rgb(107,163,200)）目前**不是同一个色值**，
     两处是独立维护的，别默认它们会一起变。 */
  --van-tabbar-item-active-color: rgb(59, 130, 246);
  /* 全屏层（登录页 / 收货地址页）上下滑动的时长。原为 300ms —— 整屏 844px 在 300ms 内
     滑完约 2.8px/ms，观感偏"赶"，放慢到 450ms 后从容很多。
     ★ 下面三处（进入页上滑 / 离开页下滑 / 被盖住的旧页淡出）必须用同一个值：
       新页滑 450ms 而旧页 300ms 就淡完的话，后半程会直接透出底下的空白。 */
  --page-layer-duration: 450ms;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', sans-serif;
  background: rgb(255, 255, 255);
  color: #323233;
}

/* ── 页面切换过渡 ─────────────────────────────────────────────────────
   1) 滑动过渡（前进/后退）期间两页都临时脱离文档流（position: fixed），否则一进一出
      会上下堆叠成两屏。离开的那一页用 --page-scroll-y 反向偏移——该变量在路由守卫里
      写入「切换前的 window.scrollY」——把它钉回原来的文档位置，所以过渡期间不会纵向跳动。
      tab 互切（.page-slide-tab-*）不这么写，原因见下方注释。
   2) 位移动画一律用 left / top，不用 transform。实测依据（不是推测）：
      页面根加 transform 后，页面内的 position: fixed 元素会从「贴视口底」变成
      「贴页面内容底」——塞一个 fixed 探针实测 top 由 836 变 1021。购物车的
      van-submit-bar 正是这种元素，长购物车里会飞出屏幕。
      （注：早先这里写的是「transform 会破坏首页吸顶」，实测该说法不成立——
      吸顶在 transform 下仍然正常，真正会坏的是 fixed 后代。）
      left / top 没有这些副作用，代价是每帧重排，页面上限一个屏幕，300ms 内可以接受。
   3) 过渡结束类名被移除，页面自动回到正常文档流。
   4) 【每条规则都带 `:root[data-page-dir=…]` 门禁 —— 别去掉】
      过渡类名里的「名字」不可信：页面被 KeepAlive 复用（切走再切回）时，挂在缓存 vnode 上的
      transition hooks 是上一次过渡残留的，页面拿到的可能是**上一族、甚至反方向**的类名
      （实测：首页→购物车→首页→我的，进入的 .member 拿到 page-slide-tab-right-enter-*；
      从登录页回来时拿到 page-slide-up-enter-*）。类名只可靠地表达「enter / leave」，
      族与方向一律以 router 写在 `<html data-page-dir>` 上的本次导航意图为准（见 router/index.ts）。
      data-page-dir 的取值：forward / back / tab-left / tab-right / up / down / ''（空 = 不动画）。 */
:root[data-page-dir='forward'] .page-slide-left-enter-active,
:root[data-page-dir='forward'] .page-slide-left-leave-active,
:root[data-page-dir='back'] .page-slide-right-leave-active {
  position: fixed;
  left: 0;
  width: 100%;
}
/* 离开的页面：钉回切换前的文档位置 */
:root[data-page-dir='forward'] .page-slide-left-leave-active,
:root[data-page-dir='back'] .page-slide-right-leave-active {
  top: calc(-1 * var(--page-scroll-y, 0px));
}

/* 前进：新页从右侧滑入 */
:root[data-page-dir='forward'] .page-slide-left-enter-active {
  top: 0;
  z-index: 10;
  transition: left 300ms cubic-bezier(0.32, 0.72, 0, 1);
}
/* 前进：旧页在下层轻微左移，且更早退场（250ms < 300ms）——
   必须在新页滑到位之前离开文档流，否则两页会同时占位把页面撑成两屏 */
:root[data-page-dir='forward'] .page-slide-left-leave-active {
  z-index: 5;
  transition: left 250ms cubic-bezier(0.32, 0.72, 0, 1);
}

/* 后退：当前页向右滑出；被缓存的上一页不参与动画，
   直接以 vue-router 恢复的滚动位置就位 */
:root[data-page-dir='back'] .page-slide-right-leave-active {
  z-index: 10;
  transition: left 280ms cubic-bezier(0.32, 0.72, 0, 1);
}

/* 起止位置：必须排在上面基础 left: 0 之后，靠声明顺序覆盖 */
:root[data-page-dir='forward'] .page-slide-left-enter-from {
  left: 100%;
}
:root[data-page-dir='forward'] .page-slide-left-leave-to {
  left: -25%;
}
:root[data-page-dir='back'] .page-slide-right-leave-to {
  left: 100%;
}

/* ── 底部 tabbar 同级切换：按 tab 左右顺序滑动 ───────────────────────────
   方向由 tab 的左右顺序决定（首页 0 / 购物车 1 / 我的 2，见 router/index.ts）：
   切到右边的 tab → 新页从右侧滑入、旧页向左让位；切到左边则反之。

   【为什么新页留在文档流里、只有旧页 fixed】
   滑动过渡（前进/后退）两页都 fixed，文档高度会瞬间塌缩到 min-height: 100vh，
   浏览器随即把 window.scrollY 夹到 0 —— 切回一个滚过一段的 tab 页时，
   scrollBehavior 想恢复的位置根本滚不过去，恢复就静默失效。
   把新页留在流里，文档高度始终由它撑着，滚动位置才能正常恢复。

   【为什么新页用 relative + left，而不是 transform】
   transform 会让页面根变成 fixed 后代的包含块。实测：页面里塞一个 fixed 探针，
   加 transform 后它从「贴视口底」（top 836）变成「贴页面内容底」（top 1021）——
   购物车的 van-submit-bar 正是这种元素，长购物车里会直接飞出屏幕。
   position: relative 不会影响 fixed 后代，实测位置零变化；left 只对定位元素生效，
   所以新页必须是 relative 才能用 left 位移。

   【横向溢出】
   新页 left: 100% 会把文档 scrollWidth 撑到两倍宽、还能横向滚。已在
   TabBarLayout 给 .layout__content 加 overflow-x: clip 收口（必须是 clip 不是 hidden）。
   这里不用再管。

   旧页是 fixed，fixed 元素不参与文档的滚动溢出，所以它横移不会撑宽文档。

   【方向由 data-page-dir 决定，不看类名】
   同一个方向的两组类名（tab-left / tab-right）在这里被**别名到同一套规则**上：
   `-active` 本来就完全一样，只有 `-enter-from` / `-leave-to` 的位移符号不同，
   所以按 data-page-dir 分成两支写，谁被挂上都不会跑偏。
   不这么做的话，被 KeepAlive 复用的页面拿到残留类名时会「往右切却从左边划入」。 */

/* 起止位置基线：left 必须显式写 0（relative 元素的 left 初始值是 auto，不可插值，
   从 100% 过渡到 auto 不会产生动画）。下面 -enter-from / -leave-to 靠声明顺序覆盖它。
   方向门禁成对写：tab-left 和 tab-right 两个方向都要认这两组类名。 */
:root[data-page-dir='tab-left'] .page-slide-tab-left-leave-active,
:root[data-page-dir='tab-left'] .page-slide-tab-right-leave-active,
:root[data-page-dir='tab-right'] .page-slide-tab-left-leave-active,
:root[data-page-dir='tab-right'] .page-slide-tab-right-leave-active {
  position: fixed;
  top: calc(-1 * var(--page-scroll-y, 0px));
  left: 0;
  width: 100%;
  z-index: 0;
  transition: left 300ms cubic-bezier(0.32, 0.72, 0, 1);
}
:root[data-page-dir='tab-left'] .page-slide-tab-left-enter-active,
:root[data-page-dir='tab-left'] .page-slide-tab-right-enter-active,
:root[data-page-dir='tab-right'] .page-slide-tab-left-enter-active,
:root[data-page-dir='tab-right'] .page-slide-tab-right-enter-active {
  position: relative;
  left: 0;
  z-index: 1;
  transition: left 300ms cubic-bezier(0.32, 0.72, 0, 1);
}

/* 往右切（首页→购物车→我的）：新页从右边进，旧页向左让位 */
:root[data-page-dir='tab-left'] .page-slide-tab-left-enter-from,
:root[data-page-dir='tab-left'] .page-slide-tab-right-enter-from {
  left: 100%;
}
:root[data-page-dir='tab-left'] .page-slide-tab-left-leave-to,
:root[data-page-dir='tab-left'] .page-slide-tab-right-leave-to {
  left: -30%;
}

/* 往左切（我的→购物车→首页）：新页从左边进，旧页向右让位 */
:root[data-page-dir='tab-right'] .page-slide-tab-left-enter-from,
:root[data-page-dir='tab-right'] .page-slide-tab-right-enter-from {
  left: -100%;
}
:root[data-page-dir='tab-right'] .page-slide-tab-left-leave-to,
:root[data-page-dir='tab-right'] .page-slide-tab-right-leave-to {
  left: 30%;
}

/* ── 全屏层页面根的常驻层叠位置（★ 别删） ─────────────────────────────
   ★ 为什么不能只靠过渡类名给 z-index（实测踩过，表现为「页面滑到位后突然闪一下旧页」）：
   enter 结束（`-enter-active` 类被移除）后，页面根会立刻回到
   `position: static` + `z-index: auto`，而**旧页的 leave 还没结束** ——
   它只声明了 `transition`（属性不变化、没有 transitionend），靠 Vue 内部的 timeout 兜底移除，
   实测比 enter 晚约 30~50ms（声明 450ms 的那次实际 498ms 才从 DOM 移除）。
   那 1~2 帧里旧页仍是 `fixed` + `z-index: 5`，按层叠规则「正 z-index 的定位元素」
   高于「普通块级元素」，于是整屏被旧页盖住闪一下。
   实测数据（确认订单 → 收货地址）：
     t=766  address fix t0 z10   | checkout fix t0 z5     ← 新页在上，正常
     t=798  address sta t0 zauto | checkout fix t0 z5     ← 新页退化成普通块级元素，被旧页反盖
     t=814  address sta t0 zauto | checkout -             ← 旧页才被移除
   常驻 relative + z-index 后，过渡结束后新页依然压在旧页之上。 */
.login,
.address,
.forgot {
  position: relative;
  z-index: 10;
}

/* ── 全屏层：从底部滑入 / 向下滑出（登录页、收货地址） ────────────────
   进入（page-slide-up）：新页 fixed 在视口下方（top: 100%），滑到 top: 0 盖住当前页。
   退出（page-slide-down）：新页从 top: 0 滑回 top: 100%，露出下面已经就位的页面。

   【谁用这一套】
   登录页，以及收货地址页（从确认订单进入时）。router 的 resolvePageTransition 里
   精确配对返回 up / down。
   ★ 找回密码页（`.forgot`）**不在这里**：它是登录页的下一层，走横向推进
     （page-slide-left / right）。它的根类名只出现在下面那条常驻 z-index 规则里。

   旧页全程不动、只是被盖住（或露出来），所以**不给它任何位移**，
   但 `transition` 必须声明 —— Vue 的 <Transition> 靠 computedStyle 的 transition-duration
   判断何时移除离开元素，不声明会被当成「无过渡」立刻删掉，新页就没有东西可盖了。
   （属性没真正变化时不会有 transitionend，Vue 内部有 timeout+1 的兜底，实测 450ms 后移除。）

   为什么新页用 fixed 而不是「relative + top」：
   relative 的 top: 100% 会把文档撑出额外一屏的纵向可滚动区域。横向那套有
   .layout__content { overflow-x: clip } 收口，纵向没有对应手段（给祖先加 overflow-y: clip
   有可能影响首页 .home__sticky 的吸顶），而 fixed 元素不参与文档滚动尺寸，
   过渡那 450ms 里不会出现「能上下滚」的穿帮。

   【⚠ 选择器必须绑定页面根类名 .login / .address】
   这些类名描述的是「全屏层的出现 / 消失」，只有上面那两类页面会用到。
   但**必须把页面根类名写进选择器**：
   Vue 在 KeepAlive 复用被缓存的页面时，会把**上一次过渡残留的 hooks 一起复用**，
   别的页面因此也会被挂上 page-slide-up-enter-*。
   实测（拦截 pageTransition 写入 + MutationObserver 记录类名变更时刻，同一份数据里 pt 全程为
   page-slide-down）：
     购物车 → 登录 → 回购物车   进入的 .cart   拿到 page-slide-down-enter-*  ✓ 与 pt 一致
     我的   → 登录 → 回我的     进入的 .member 拿到 page-slide-up-enter-*    ✗ 上一次过渡的残留
   不绑定时，后者会被 fixed + top: 100% 拉出文档流，和正在下移的登录页叠在一起
   （.member 自身没有背景色，过渡期间会直接透出登录页）。
   绑定后：只有登录页 / 收货地址页会上滑 / 下滑，其它页面不管被挂上什么类名都留在文档流里当 static。
   再加上 data-page-dir 门禁：`up` / `down` 之外的导航中，这两组规则一律不生效，
   被 KeepAlive 残留污染的元素也不会被 fixed 拉出文档流。
   ★ 新增**走 up/down 的**全屏层页面时，页面根类名要同步加进这里和下面那条常驻 z-index 规则。 */
/* 进入页：从视口下方滑到 top: 0。
   进入页是刚挂载的新页面，滚动位置必然是 0，所以不需要 --page-scroll-y 偏移。 */
:root[data-page-dir='up'] :is(.login, .address).page-slide-up-enter-active {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 10;
  transition: top var(--page-layer-duration) cubic-bezier(0.32, 0.72, 0, 1);
}
/* 离开页：可能已经滚过一段（收货地址列表长的时候就会），
   一脱离文档流它就会跳回视口顶部，所以起点要先按 --page-scroll-y 钉回原来的内容位置；
   终点同理减去这段偏移，保证「向下移出」的距离正好是一个视口高。
   （登录页内容永远一屏，--page-scroll-y 恒为 0，这两处 calc 退化成原来的 top: 0 / top: 100%） */
:root[data-page-dir='down'] :is(.login, .address).page-slide-down-leave-active {
  position: fixed;
  top: calc(-1 * var(--page-scroll-y, 0px));
  left: 0;
  width: 100%;
  z-index: 10;
  transition: top var(--page-layer-duration) cubic-bezier(0.32, 0.72, 0, 1);
}
:root[data-page-dir='up'] :is(.login, .address).page-slide-up-enter-from {
  top: 100%;
}
:root[data-page-dir='down'] :is(.login, .address).page-slide-down-leave-to {
  top: calc(100% - var(--page-scroll-y, 0px));
}

/* ── 零过渡替换（page-slide-none）────────────────────────────────────────
   用于「换页但不做位移动画」的场景：登录页 → 找回密码。
   用户要求「点开即出现，和登录页里点立即注册一样」，所以 duration 为 0、不写位移。

   【为什么还需要一套规则，不能直接让 router 返回 空串】
   Vue 的 <Transition> 是按 name 挂类的：name 为空串时类名会退化成 `-enter-from`
   / `-enter-active`（首尾没有名字），而且两页会在 DOM 里共存一两帧。
   此时两页都没被 fixed、都留在文档流里 → 新页被排到旧页下方。
   实测（390×844，逐帧量 .forgot 的 rectTop）：
     t=0   login l0 t0  | forgot l0 t844   ← 新页跑到第二屏，文档 height 翻到 1688
     t+1  login 卸载    | forgot l0 t0
   也就是「看似没有动画，其实有一帧整页跳动 + 滚动条闪一下」。
   所以仍然需要一个具名过渡，只把两页 fixed 钉在视口里、不给任何位移：
   替换在原点发生，文档高度不变。

   【必须同时固定进入页和离开页】
   只固定进入页的话，它的 `-enter-active` 一被移除就退回 position: relative
   重新回到文档流，而旧页的 leave 还要晚一两帧才结束 —— 又会堆成两屏。
   两页都 fixed：窗口内文档高度由内容塌到接近 0，但本类场景只用于
   「一屏高整页 ↔ 一屏高整页」（scrollY 恒为 0），塌一下没有可感知的副作用；
   旧页卸载后新页回到 position: relative，文档高度恢复一屏。
   （这与 page-slide-left/right 那两条同时固定两页是同一套思路。） */
:root[data-page-dir='none'] :is(.page-slide-none-enter-active, .page-slide-none-leave-active) {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
}

/* 收货地址页自带一条 position: fixed 的底部按钮条（「新增收货地址」）。
   fixed 元素的包含块是**视口**，不会跟着祖先一起位移 —— 页面整体上滑 / 下滑时，
   按钮条会死死钉在视口底部不动，看起来像被"甩"在原地。
   （登录页没有任何 fixed 后代，所以这套 up / down 过渡在只服务登录页时从没暴露过这个问题。）
   过渡期间把它改成 absolute：包含块变成同样在动的 .address，它就会跟着页面一起走，
   过渡结束类名移除后自动变回 fixed。 */
:root[data-page-dir='up'] .address.page-slide-up-enter-active .address__footer,
:root[data-page-dir='down'] .address.page-slide-down-leave-active .address__footer {
  position: absolute;
}
/* 旧页：钉回它原来的文档位置，并且**必须保持 fixed、不能留在文档流里**。
   实测（逐帧量 documentElement.scrollHeight）：留在流里时 enter 比 leave 早结束约一帧，
   会出现「新页已回到 static、旧页还在流里」的瞬间 —— 文档高度从 844 翻到 1688，
   滚动条闪一下且过渡期间能上下滚。fixed 元素不参与文档滚动尺寸，任何重叠窗口都不会撑高文档。 */
:root[data-page-dir='up'] .page-slide-up-leave-active {
  position: fixed;
  top: calc(-1 * var(--page-scroll-y, 0px));
  left: 0;
  width: 100%;
  z-index: 5;
  transition: opacity var(--page-layer-duration) linear;
}

/* ── 过渡期间隐藏页内的「回到顶部」浮动按钮 ──────────────────────────────
   fixed 的包含块是视口、不跟着页面滑：后退时当前页向右滑出，按钮却钉在视口右下角，
   会以「悬在原位」的样子漂在露出的上一页上约 280ms（tab 互切时它整体被新页盖住，
   只有新页左缘扫过之前那几十毫秒可见）。在页面根的过渡类上把按钮藏掉即可 ——
   滑完类名被移除，按钮按滚动位置自然恢复。
   与 .address__footer 过渡期间改 absolute 是同一类问题、同一套门禁思路：
   只认「页面根 + leave-active + data-page-dir」，up/down 两个全屏层没有这个按钮。 */
:root[data-page-dir='forward'] .page-slide-left-leave-active .back-to-top,
:root[data-page-dir='back'] .page-slide-right-leave-active .back-to-top,
:root[data-page-dir='tab-left'] .page-slide-tab-left-leave-active .back-to-top,
:root[data-page-dir='tab-left'] .page-slide-tab-right-leave-active .back-to-top,
:root[data-page-dir='tab-right'] .page-slide-tab-left-leave-active .back-to-top,
:root[data-page-dir='tab-right'] .page-slide-tab-right-leave-active .back-to-top {
  display: none;
}

/* 用户开启「减弱动态效果」时，过渡时长压到接近瞬时，只保留状态切换。
   权重必须 ≥ 上面的主规则，否则会被它们的 transition 简写覆盖掉。
   注意两处主规则的权重并不一样（原注释以为都是 0,3,0，实际不是）：
     `.page-slide-left-enter-active` 这类「单类」选择器 = 0,3,0，与下面 :is(...) 那条同权重，靠声明顺序胜出；
     `:is(.login, .address).page-slide-up-enter-active` 这类「双类」= 0,4,0，**压不住**，
     所以必须单独用同等权重再写一遍。顺带修掉了登录页减弱动效一直只生效一半的问题
     （旧页淡出被压到 1ms，新页上滑仍是完整时长）。 */
@media (prefers-reduced-motion: reduce) {
  :root[data-page-dir] :is(.login, .address).page-slide-up-enter-active,
  :root[data-page-dir] :is(.login, .address).page-slide-down-leave-active,
  :root[data-page-dir] :is(
      .page-slide-left-enter-active,
      .page-slide-left-leave-active,
      .page-slide-right-leave-active,
      .page-slide-tab-left-enter-active,
      .page-slide-tab-left-leave-active,
      .page-slide-tab-right-enter-active,
      .page-slide-tab-right-leave-active,
      .page-slide-up-enter-active,
      .page-slide-up-leave-active,
      .page-slide-down-leave-active
    ) {
    transition-duration: 1ms;
  }
}
</style>
