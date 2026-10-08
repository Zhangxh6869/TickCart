<script setup lang="ts">
import { RouterView } from 'vue-router'
import TabBarLayout from '@/layouts/TabBarLayout.vue'
import { pageTransition } from '@/router'

/**
 * 需要缓存的页面，按组件名匹配（<script setup> 由文件名推断组件名）。
 * 底部 tabbar 的三个页面全部缓存：切走再切回不再重新请求、滚动位置也还在。
 * 购物车的数据新鲜度由页面自身的 onActivated 静默刷新兜底（见 CartView）。
 */
const CACHED_VIEWS = ['HomeView', 'CartView', 'MemberView']
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

:root {
  /* 页面背景色：同步覆盖 Vant 的页面背景变量，避免 van-image 等组件仍用旧的灰色底 */
  --van-background: rgb(255, 255, 255);
  /* 底部 tabbar 选中项颜色（图标与文字同源，改这一个变量即可）。
     Vant 默认取 --van-primary-color（蓝 #1989fa），这里直接改掉选中态变量。
     注意：与首页搜索框描边用的 --color-brand（rgb(107,163,200)）目前**不是同一个色值**，
     两处是独立维护的，别默认它们会一起变。 */
  --van-tabbar-item-active-color: rgb(59, 130, 246);
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

/* ── 登录页：全屏层，从底部滑入 / 向下滑出 ─────────────────────────────
   进入（page-slide-up）：登录页 fixed 在视口下方（top: 100%），滑到 top: 0 盖住当前页。
   退出（page-slide-down）：登录页从 top: 0 滑回 top: 100%，露出下面已经就位的页面。

   旧页全程不动、只是被盖住（或露出来），所以**不给它任何位移**，
   但 `transition` 必须声明 —— Vue 的 <Transition> 靠 computedStyle 的 transition-duration
   判断何时移除离开元素，不声明会被当成「无过渡」立刻删掉，新页就没有东西可盖了。
   （属性没真正变化时不会有 transitionend，Vue 内部有 timeout+1 的兜底，实测 300ms 后移除。）

   为什么新页用 fixed 而不是「relative + top」：
   relative 的 top: 100% 会把文档撑出额外一屏的纵向可滚动区域。横向那套有
   .layout__content { overflow-x: clip } 收口，纵向没有对应手段（给祖先加 overflow-y: clip
   有可能影响首页 .home__sticky 的吸顶），而 fixed 元素不参与文档滚动尺寸，
   过渡那 300ms 里不会出现「能上下滚」的穿帮。

   【⚠ 选择器必须绑定 .login】
   这两组类名描述的是「登录页这一层的出现 / 消失」，只有登录页会用到
   （router 里只有 to/from 是 login 时才返回 up/down）。但**必须把 .login 写进选择器**：
   Vue 在 KeepAlive 复用被缓存的页面时，会把**上一次过渡残留的 hooks 一起复用**，
   别的页面因此也会被挂上 page-slide-up-enter-*。
   实测（拦截 pageTransition 写入 + MutationObserver 记录类名变更时刻，同一份数据里 pt 全程为
   page-slide-down）：
     购物车 → 登录 → 回购物车   进入的 .cart   拿到 page-slide-down-enter-*  ✓ 与 pt 一致
     我的   → 登录 → 回我的     进入的 .member 拿到 page-slide-up-enter-*    ✗ 上一次过渡的残留
   不绑定时，后者会被 fixed + top: 100% 拉出文档流，和正在下移的登录页叠在一起
   （.member 自身没有背景色，过渡期间会直接透出登录页）。
   绑定后：只有登录页会上滑 / 下滑，其它页面不管被挂上什么类名都留在文档流里当 static。
   再加上 data-page-dir 门禁：`up` / `down` 之外的导航里，这两组规则一律不生效，
   被 KeepAlive 残留污染的元素也不会被 fixed 拉出文档流。 */
:root[data-page-dir='up'] .login.page-slide-up-enter-active,
:root[data-page-dir='down'] .login.page-slide-down-leave-active {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 10;
  transition: top 300ms cubic-bezier(0.32, 0.72, 0, 1);
}
:root[data-page-dir='up'] .login.page-slide-up-enter-from,
:root[data-page-dir='down'] .login.page-slide-down-leave-to {
  top: 100%;
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
  transition: opacity 300ms linear;
}

/* 用户开启「减弱动态效果」时，过渡时长压到接近瞬时，只保留状态切换。
   门禁写法必须与上面的规则**同权重或更高**（`:root[data-page-dir]` + `:is(...)` = 0,3,0，
   与 `:root[data-page-dir='x'] .cls` 相同），否则会被上面那条 transition 覆盖掉。 */
@media (prefers-reduced-motion: reduce) {
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
