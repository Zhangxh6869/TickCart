<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import TabBarIcon from '@/components/TabBarIcon.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

/**
 * tab 顺序的唯一来源：既驱动 v-for 渲染，也决定选中指示条的位置。
 * as const 让 name 收窄成字面量联合，正好对上 TabBarIcon 的 name 入参类型。
 */
const TAB_ITEMS = [
  { name: 'home', label: '首页' },
  { name: 'cart', label: '购物车' },
  { name: 'member', label: '我的' },
] as const

const active = computed(() => route.name as string)

/** 当前选中项在 TAB_ITEMS 中的下标；路由不在 tabbar 上时兜底到首页 */
const activeIndex = computed(() => {
  const index = TAB_ITEMS.findIndex((item) => item.name === route.name)
  return index < 0 ? 0 : index
})

/**
 * 选中指示条的水平位置：每个 tab 均分 1/3 宽，取该格的中心点。
 * 条自身宽 24px，靠样式里的 margin-left: -12px 居中，所以这里只算中心。
 */
const indicatorLeft = computed(() => `calc(${activeIndex.value} * 100% / 3 + 100% / 6)`)

/**
 * 自带全屏层级的页面隐藏 tabbar：
 *   product-detail —— 页面自带底部操作栏（收藏 / 加购 / 立即购买），tabbar 让位
 *   login          —— 独立全屏层，从底部滑入覆盖整个视口；留着 tabbar 会出现
 *                     「三项全灰、指示条却停在首页下面」的坏选中态，且与登录页无关
 *   address        —— 页面自带固定底部按钮条（新增收货地址）。它与 tabbar 都贴视口底，
 *                     而 tabbar 带 z-index: 1、按钮条没写 z-index，实测按钮 44px 里
 *                     只剩顶部 4px 能点到（点中间会命中 tabbar 的「购物车」直接跳走）。
 *                     与其给按钮条加 z-index 硬压 tabbar（那会让 tabbar 彻底点不到），
 *                     不如让 tabbar 在这个二级页整体让位，用户走左上角返回。
 * 顺带 .layout--tabbar 的 50px 底部内边距也会一起去掉，登录页正好占满一屏。
 */
const FULLSCREEN_ROUTES = ['product-detail', 'login', 'address']
const showTabbar = computed(() => !FULLSCREEN_ROUTES.includes(route.name as string))

/** 只有购物车带角标，其余为空字符串（Vant 收到空串不渲染角标） */
function badgeOf(name: string): string {
  return name === 'cart' ? cartStore.badge : ''
}

/** 登录后同步购物车角标；退出登录时清空本地状态 */
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      cartStore.refreshCount()
    } else {
      cartStore.reset()
    }
  },
  { immediate: true }
)

function go(name: string) {
  router.push({ name })
}
</script>

<template>
  <div class="layout" :class="{ 'layout--tabbar': showTabbar }">
    <div class="layout__content">
      <slot />
    </div>

    <!-- 图标走 #icon 插槽自绘 SVG（见 TabBarIcon），不用 Vant 的 icon 字体：
         设计给的图标字体里没有，且需要随选中态变色（currentColor） -->
    <van-tabbar v-if="showTabbar" :model-value="active" @change="go">
      <!-- 选中指示条：绝对定位脱离 flex 布局，跟着选中项滑动。
           van-tabbar 是 fixed 定位，所以这里相对它定位，top: 0 即导航条顶边。 -->
      <span class="tabbar__indicator" :style="{ left: indicatorLeft }" />

      <van-tabbar-item v-for="item in TAB_ITEMS" :key="item.name" :name="item.name" :badge="badgeOf(item.name)">
        <template #icon><TabBarIcon :name="item.name" /></template>
        {{ item.label }}
      </van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
/* 有 tabbar 时给内容区留出底部空间，避免被固定定位的 tabbar 遮住 */
.layout--tabbar {
  padding-bottom: 50px;
}

/* 收掉页面横向滑动时的溢出：新页从 left: 100% 滑入，不夹住的话文档 scrollWidth
   会被撑到两倍宽，还能横向拖动。
   必须是 clip 而不是 hidden —— 实测 hidden 挡不住：程序化 scrollTo(100) 仍能滚过去
   （scrollX 变 100），scrollWidth 也照样是 780。
   clip 不创建滚动容器，overflow-y 因此保持 visible，
   首页 .home__sticky 的吸顶不受影响（实测吸顶 top 仍为 0，分类栏自身的横向滚动也照常）。 */
.layout__content {
  overflow-x: clip;
}

/* ── 选中指示条 ──────────────────────────────────────────────────────
   宽度固定 24px，用 margin-left: -12px 把「中心点」对回所在格子的中线。
   位移用 left 过渡（不用 transform：tabbar 祖先链上没有 sticky / fixed 后代，
   这里其实安全，但与页面过渡保持同一套写法，避免以后有人把 tabbar 挪进页面容器时踩坑）。 */
.tabbar__indicator {
  position: absolute;
  top: 0;
  width: 24px;
  height: 3px;
  margin-left: -12px;
  border-radius: 0 0 2px 2px;
  /* 与选中图标/文字同源：取 Vant 的选中色变量（App.vue 的 :root 里覆盖过），
     改那一个变量时指示条会一起变，不用在这里再抄一份色值 */
  background: var(--van-tabbar-item-active-color, rgb(59, 130, 246));
  transition: left 200ms cubic-bezier(0.32, 0.72, 0, 1);
  pointer-events: none;
}

/* 选中图标回弹。只作用在自绘 SVG 上，不影响 Vant 的布局与点击区。 */
:deep(.van-tabbar-item--active .tabbar-icon) {
  animation: tabbar-icon-pop 320ms cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes tabbar-icon-pop {
  0% {
    transform: scale(1);
  }
  45% {
    transform: scale(1.22);
  }
  100% {
    transform: scale(1);
  }
}

/* 选中文字轻微上浮，和图标回弹形成同一个动作 */
:deep(.van-tabbar-item__text) {
  transition: transform 200ms ease-out;
}
:deep(.van-tabbar-item--active .van-tabbar-item__text) {
  transform: translateY(-1px);
}

@media (prefers-reduced-motion: reduce) {
  .tabbar__indicator {
    transition-duration: 1ms;
  }
  :deep(.van-tabbar-item--active .tabbar-icon) {
    animation: none;
  }
  :deep(.van-tabbar-item__text) {
    transition-duration: 1ms;
  }
}
</style>
