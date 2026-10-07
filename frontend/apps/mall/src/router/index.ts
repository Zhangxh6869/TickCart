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
 *   page-slide-left      前进：新页从右侧滑入
 *   page-slide-right     后退：当前页向右滑出
 *   page-slide-tab-left  同级 tab 往右切（首页→购物车→我的）：新页从右侧滑入
 *   page-slide-tab-right 同级 tab 往左切（我的→购物车→首页）：新页从左侧滑入
 *   page-slide-up        进入登录页：全屏层从视口下方滑上来盖住当前页
 *   page-slide-down      离开登录页：全屏层向下滑走，露出下面已就位的页面
 *   ''                   不做动画（其余同级切换）
 * 具体样式见 App.vue 的全局 <style>。
 */
export type PageTransition =
  | ''
  | 'page-slide-left'
  | 'page-slide-right'
  | 'page-slide-tab-left'
  | 'page-slide-tab-right'
  | 'page-slide-up'
  | 'page-slide-down'

export const pageTransition = ref<PageTransition>('')

/**
 * 底部 tabbar 的平级页面。**值就是它们的左右顺序**，用来决定滑动方向：
 * 切到序号更大的 tab → 新页从右侧滑入；更小则从左侧滑入。
 * 同时这组页面之间互切会各自记住滚动位置（见下方 tabScrollPositions）。
 */
const TAB_ORDER = new Map([
  ['home', 0],
  ['cart', 1],
  ['member', 2],
])

/** 路由层级：数字越大表示页面越深，用于判断本次导航是「前进」还是「后退」。
 * 未声明的路由按 1 处理（与首页同级）。
 */
function depthOf(route: RouteLocationNormalized): number {
  const depth = route.meta.depth
  return typeof depth === 'number' ? depth : 1
}

/** 路由名转字符串，拿不到（如起始占位路由）时返回空串 */
function nameOf(route: RouteLocationNormalized): string {
  return typeof route.name === 'string' ? route.name : ''
}

/**
 * 各 tab 页上次离开时的滚动位置，切回时恢复。
 * 只用于 tab 之间互切；浏览器前进/后退仍以 vue-router 的 savedPosition 为准。
 */
const tabScrollPositions = new Map<string, number>()

/** 本次导航进入页要恢复到的滚动位置，由 beforeEach 写入、scrollBehavior 消费 */
let pendingScrollTop = 0

function resolvePageTransition(
  to: RouteLocationNormalized,
  from: RouteLocationNormalized
): PageTransition {
  // 首次进入应用（from 是起始占位路由）不播放动画
  if (!from.name) return ''
  const toName = nameOf(to)
  const fromName = nameOf(from)

  // 登录页是一个**全屏层**，跟其它页面之间没有「层级深浅」可言（购物车、商品详情、
  // 「我的」页都能进），所以必须在 depth 判断之前拦掉，否则会出现
  // 「从购物车进是右滑、从详情页进是硬切」的割裂感 —— 这正是本次要修的问题。
  if (toName === 'login') return 'page-slide-up'
  if (fromName === 'login') return 'page-slide-down'

  const toDepth = depthOf(to)
  const fromDepth = depthOf(from)
  if (toDepth > fromDepth) return 'page-slide-left'
  if (toDepth < fromDepth) return 'page-slide-right'
  // 深度相同：只有底部 tabbar 三页之间互切做滑动，方向按左右顺序定；
  // 其余同级切换保持无动画
  const toTab = TAB_ORDER.get(toName)
  const fromTab = TAB_ORDER.get(fromName)
  if (toTab === undefined || fromTab === undefined) return ''
  return toTab > fromTab ? 'page-slide-tab-left' : 'page-slide-tab-right'
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
    // 需要登录的页面统一在此挂 meta.requiresAuth。
    // 例外：tabbar 的「购物车」「个人中心」**不挂** —— 未登录时也允许进入，
    // 由页面自身展示登录引导（CartView 显示提示 + 去登录按钮；MemberView 展示与已登录
    // 完全相同的页面结构，只有头部那块是「点击登录」入口），而不是被守卫重定向走。
    // 真正需要登录才能操作的行为（结算、订单、地址等）仍走 requiresAuth。
    path: '/member',
    name: 'member',
    component: () => import('@/views/MemberView.vue'),
    meta: { title: '个人中心', depth: 1 },
  },
  {
    path: '/cart',
    name: 'cart',
    component: () => import('@/views/CartView.vue'),
    meta: { title: '购物车', depth: 1 },
  },
  {
    path: '/address',
    name: 'address',
    component: () => import('@/views/AddressListView.vue'),
    meta: { title: '收货地址', requiresAuth: true, depth: 2 },
  },
  {
    path: '/checkout',
    name: 'checkout',
    component: () => import('@/views/CheckoutView.vue'),
    meta: { title: '确认订单', requiresAuth: true, depth: 2 },
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('@/views/OrderListView.vue'),
    meta: { title: '我的订单', requiresAuth: true, depth: 2 },
  },
  {
    path: '/orders/:orderNo',
    name: 'order-detail',
    component: () => import('@/views/OrderDetailView.vue'),
    meta: { title: '订单详情', requiresAuth: true, depth: 3 },
  },
  {
    path: '/pay/:orderNo',
    name: 'pay',
    component: () => import('@/views/PayView.vue'),
    meta: { title: '收银台', requiresAuth: true, depth: 3 },
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
  // 浏览器前进/后退优先用 vue-router 保存的位置（savedPosition），
  // 其余情况用本次导航算好的 pendingScrollTop：tab 之间互切恢复各自的滚动位置，进新页则回到顶部
  scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: pendingScrollTop },
})

// 全局前置守卫：登录态校验
router.beforeEach(async (to, from) => {
  // 过渡动画期间两个页面都临时脱离文档流（position: fixed），
  // 需要知道切换前后的滚动位置才能把它们各自钉回正确的内容位置（见 App.vue 的过渡样式）
  const scrollY = window.scrollY
  const fromName = nameOf(from)
  const toName = nameOf(to)

  // 离开 tab 页时记住它的滚动位置，切回来时还原
  if (TAB_ORDER.has(fromName)) {
    tabScrollPositions.set(fromName, scrollY)
  }
  pendingScrollTop = TAB_ORDER.has(toName) ? tabScrollPositions.get(toName) ?? 0 : 0

  document.documentElement.style.setProperty('--page-scroll-y', `${scrollY}px`)
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
