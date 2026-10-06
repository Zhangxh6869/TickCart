import { ref } from 'vue'
import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteRecordRaw,
} from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 页面切换动画名，由 App.vue 的 <Transition :name> 消费：
 *   page-slide-left  前进：新页从右侧滑入
 *   page-slide-right 后退：当前页向右滑出
 *   ''               不做动画（同级切换，如底部 tabbar）
 * 具体样式见 App.vue 的全局 <style>。
 */
export type PageTransition = '' | 'page-slide-left' | 'page-slide-right'

export const pageTransition = ref<PageTransition>('')

/**
 * 路由层级：数字越大表示页面越深，用于判断本次导航是「前进」还是「后退」。
 * 未声明的路由按 1 处理（与首页同级）。
 */
function depthOf(route: RouteLocationNormalized): number {
  const depth = route.meta.depth
  return typeof depth === 'number' ? depth : 1
}

function resolvePageTransition(
  to: RouteLocationNormalized,
  from: RouteLocationNormalized
): PageTransition {
  // 首次进入应用（from 是起始占位路由）不播放动画
  if (!from.name) return ''
  const toDepth = depthOf(to)
  const fromDepth = depthOf(from)
  if (toDepth > fromDepth) return 'page-slide-left'
  if (toDepth < fromDepth) return 'page-slide-right'
  return ''
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页', depth: 1 },
  },
  {
    path: '/products',
    name: 'product-list',
    component: () => import('@/views/ProductListView.vue'),
    meta: { title: '商品列表', depth: 2 },
  },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: () => import('@/views/ProductDetailView.vue'),
    meta: { title: '商品详情', depth: 3 },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', depth: 3 },
  },
  {
    // 需要登录的页面统一在此挂 meta.requiresAuth
    path: '/member',
    name: 'member',
    component: () => import('@/views/MemberView.vue'),
    meta: { title: '个人中心', requiresAuth: true, depth: 1 },
  },
  {
    path: '/address',
    name: 'address',
    component: () => import('@/views/AddressListView.vue'),
    meta: { title: '收货地址', requiresAuth: true, depth: 2 },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在', depth: 1 },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 后退时恢复上一页（被 keep-alive 缓存的首页）的滚动位置，其余情况一律回到顶部
  scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: 0 },
})

// 全局前置守卫：登录态校验
router.beforeEach(async (to, from) => {
  // 过渡动画期间两个页面都临时脱离文档流（position: fixed），
  // 需要知道切换前的滚动位置才能把它们钉回原来的文档位置（见 App.vue 的过渡样式）
  document.documentElement.style.setProperty('--page-scroll-y', `${window.scrollY}px`)
  pageTransition.value = resolvePageTransition(to, from)

  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 已登录但用户信息未加载（如刷新页面），补拉一次
  if (userStore.isLoggedIn && !userStore.userInfo) {
    try {
      await userStore.loadUserInfo()
    } catch {
      // 令牌失效等：交给请求拦截器处理，不阻断导航
    }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · TickCart` : 'TickCart 秒杀商城'
})

export default router
