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
    <!-- 页面切换过渡：方向由路由层级决定，tab 三页互切走淡入（见 router/index.ts）；
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
      淡入过渡（tab 互切）不用这套写法，原因见下方 .page-fade-* 的注释。
   2) 位移动画一律用 left / top，不用 transform：transform 会让祖先元素变成 fixed 后代的
      包含块（van-popup 的遮罩、详情页底部操作栏都可能因此错位），也会干扰祖先链上的
      position: sticky（首页吸顶区）。left / top 没有这些副作用，代价是每帧重排，页面上限
      一个屏幕，300ms 内可以接受。
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

/* ── 底部 tabbar 同级切换：新页覆盖旧页淡入 ──────────────────────────────
   三页是平级关系，没有前进/后退语义，所以不做横向滑动，只做淡入。

   【为什么进入页留在文档流里】
   滑动过渡让两页都是 fixed，文档高度会瞬间塌缩到 min-height: 100vh，
   浏览器随即把 window.scrollY 夹到 0 —— 切回一个滚过一段的 tab 页时，
   scrollBehavior 想恢复的位置根本滚不过去，恢复就静默失效了。
   把进入页留在流里，文档高度始终由它撑着，滚动位置才能正常恢复。

   【为什么由新页淡入、而不是旧页淡出】
   旧页用 fixed + top: -var(--page-scroll-y) 钉在视口上（fixed 相对视口定位，
   过渡期间窗口从旧页滚动位置滚到新页目标位置时它纹丝不动，显示的内容始终正确），
   并保持完全不透明当底色；新页抬到它上面淡入。
   反过来做（旧页淡出、新页留在下面）会让两页同时处于半透明，
   白底透出来，中间那段明显发灰。

   z-index：旧页 0、新页 1，与 van-tabbar（--van-tabbar-z-index: 1）同层——
   tabbar 在 DOM 里更靠后，绘制在上，所以过渡期间底部导航不会被盖住。 */
.page-fade-leave-active {
  position: fixed;
  top: calc(-1 * var(--page-scroll-y, 0px));
  left: 0;
  width: 100%;
  z-index: 0;
  /* 旧页不做视觉变化（它会被新页盖住），但 transition 必须声明：
     Vue 的 <Transition> 是靠 computedStyle 上的 transition-duration 判断
     何时移除离开元素的，不声明就会被当成「没有过渡」而立刻删掉，新页就没了底色。
     from / to 都是 opacity: 1，所以声明了也看不出任何变化。 */
  transition: opacity 180ms linear;
}

/* 新页淡入。用 position: relative 抬层级，不用 transform——
   transform 会变成祖先链上的 transform，首页 .home__sticky 的吸顶会直接失效
   （见长期记忆）。relative 不会破坏 sticky，也不会成为 fixed 后代的包含块。 */
.page-fade-enter-active {
  position: relative;
  z-index: 1;
  transition: opacity 180ms ease-out;
}
.page-fade-enter-from {
  opacity: 0;
}

/* 用户开启「减弱动态效果」时，过渡时长压到接近瞬时，只保留状态切换 */
@media (prefers-reduced-motion: reduce) {
  .page-slide-left-enter-active,
  .page-slide-left-leave-active,
  .page-slide-right-leave-active,
  .page-fade-enter-active,
  .page-fade-leave-active {
    transition-duration: 1ms;
  }
}
</style>
