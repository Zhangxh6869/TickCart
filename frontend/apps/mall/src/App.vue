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
   3) 过渡结束类名被移除，页面自动回到正常文档流。 */
.page-slide-left-enter-active,
.page-slide-left-leave-active,
.page-slide-right-leave-active {
  position: fixed;
  left: 0;
  width: 100%;
}
/* 离开的页面：钉回切换前的文档位置 */
.page-slide-left-leave-active,
.page-slide-right-leave-active {
  top: calc(-1 * var(--page-scroll-y, 0px));
}

/* 前进：新页从右侧滑入 */
.page-slide-left-enter-active {
  top: 0;
  z-index: 10;
  transition: left 300ms cubic-bezier(0.32, 0.72, 0, 1);
}
/* 前进：旧页在下层轻微左移，且更早退场（250ms < 300ms）——
   必须在新页滑到位之前离开文档流，否则两页会同时占位把页面撑成两屏 */
.page-slide-left-leave-active {
  z-index: 5;
  transition: left 250ms cubic-bezier(0.32, 0.72, 0, 1);
}

/* 后退：当前页向右滑出；被缓存的上一页不参与动画，
   直接以 vue-router 恢复的滚动位置就位 */
.page-slide-right-leave-active {
  z-index: 10;
  transition: left 280ms cubic-bezier(0.32, 0.72, 0, 1);
}

/* 起止位置：必须排在上面基础 left: 0 之后，靠声明顺序覆盖 */
.page-slide-left-enter-from {
  left: 100%;
}
.page-slide-left-leave-to {
  left: -25%;
}
.page-slide-right-leave-to {
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

   旧页是 fixed，fixed 元素不参与文档的滚动溢出，所以它横移不会撑宽文档。 */

/* 起止位置基线：left 必须显式写 0（relative 元素的 left 初始值是 auto，不可插值，
   从 100% 过渡到 auto 不会产生动画）。下面 -enter-from / -leave-to 靠声明顺序覆盖它。 */
.page-slide-tab-left-leave-active,
.page-slide-tab-right-leave-active {
  position: fixed;
  top: calc(-1 * var(--page-scroll-y, 0px));
  left: 0;
  width: 100%;
  z-index: 0;
  transition: left 300ms cubic-bezier(0.32, 0.72, 0, 1);
}
.page-slide-tab-left-enter-active,
.page-slide-tab-right-enter-active {
  position: relative;
  left: 0;
  z-index: 1;
  transition: left 300ms cubic-bezier(0.32, 0.72, 0, 1);
}

/* 往右切（首页→购物车→我的）：新页从右边进，旧页向左让位 */
.page-slide-tab-left-enter-from {
  left: 100%;
}
.page-slide-tab-left-leave-to {
  left: -30%;
}

/* 往左切（我的→购物车→首页）：新页从左边进，旧页向右让位 */
.page-slide-tab-right-enter-from {
  left: -100%;
}
.page-slide-tab-right-leave-to {
  left: 30%;
}

/* 用户开启「减弱动态效果」时，过渡时长压到接近瞬时，只保留状态切换 */
@media (prefers-reduced-motion: reduce) {
  .page-slide-left-enter-active,
  .page-slide-left-leave-active,
  .page-slide-right-leave-active,
  .page-slide-tab-left-enter-active,
  .page-slide-tab-left-leave-active,
  .page-slide-tab-right-enter-active,
  .page-slide-tab-right-leave-active {
    transition-duration: 1ms;
  }
}
</style>
