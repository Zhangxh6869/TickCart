<script setup lang="ts">
import { RouterView } from 'vue-router'
import TabBarLayout from '@/layouts/TabBarLayout.vue'
import { pageTransition } from '@/router'
</script>

<template>
  <TabBarLayout>
    <!-- 页面切换过渡：方向由路由层级决定（见 router/index.ts），首页缓存下来，
         返回时商品数据与滚动位置都还在 -->
    <RouterView v-slot="{ Component, route }">
      <Transition :name="pageTransition">
        <KeepAlive include="HomeView">
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
  /* 底部 tabbar 选中项颜色：品牌色（与首页搜索框描边同色）。
     Vant 默认取 --van-primary-color（蓝 #1989fa），这里直接改掉选中态变量 */
  --van-tabbar-item-active-color: rgb(107, 163, 200);
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', sans-serif;
  background: rgb(255, 255, 255);
  color: #323233;
}

/* ── 页面切换过渡 ─────────────────────────────────────────────────────
   1) 过渡期间两页都临时脱离文档流（position: fixed），否则一进一出会上下堆叠成两屏。
      离开的那一页用 --page-scroll-y 反向偏移——该变量在路由守卫里写入「切换前的
      window.scrollY」——把它钉回原来的文档位置，所以过渡期间不会纵向跳动。
   2) 位移动画一律用 left，不用 transform：transform 会让祖先元素变成 fixed 后代的
      包含块（van-popup 的遮罩、详情页底部操作栏都可能因此错位），也会干扰祖先链上的
      position: sticky（首页吸顶区）。left 没有这些副作用，代价是每帧重排，页面上限
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
</style>
