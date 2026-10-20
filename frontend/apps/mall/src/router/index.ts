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
 *   page-slide-up        进入全屏层（登录页、从确认订单进收货地址）：从视口下方滑上来盖住当前页
 *   page-slide-down      离开全屏层（登录页、收货地址回确认订单）：向下滑走，露出下面已就位的页面
 *   ''                   不做动画（其余同级切换）
 * 具体样式见 App.vue 的全局 <style>。
 */
export type PageTransition =
  | ''
  /** 零过渡：仍是「换页」，但**不做位移动画**（登录页 → 找回密码，用户要求点开即出现） */
  | 'page-slide-none'
  | 'page-slide-left'
  | 'page-slide-right'
  | 'page-slide-tab-left'
  | 'page-slide-tab-right'
  | 'page-slide-up'
  | 'page-slide-down'

export const pageTransition = ref<PageTransition>('')

/**
 * 本次导航的「过渡族 + 方向」，会写进 `<html data-page-dir>`，供 App.vue 的过渡样式做门禁。
 *
 * 为什么不能只靠过渡类名（这是踩过的坑，别再简化掉）：
 *   `<Transition :name>` 把名字烘进类名里（`page-slide-tab-left-enter-from` …），
 *   而 `<KeepAlive>` 复用被缓存的页面时，页面根节点上的 transition hooks 是
 *   **上一次过渡残留的**（Vue 的 `getInnerChild` 在 KeepAlive 已挂载后返回的是缓存的
 *   subTree，新 vnode 上的 hooks 落不到它身上，KeepAlive 内部那段
 *   `if (vnode.transition) setTransitionHooks(...)` 因此被跳过）。
 *   于是页面拿到的类名可能整族整方向都不对。实测：
 *     首页 → 购物车 → 首页 → 我的   进入的 .member 拿到 `page-slide-tab-right-enter-*`，方向反了
 *     我的 → 登录 → 回我的         进入的 .member 拿到 `page-slide-up-enter-*`，整族都不对
 *   类名里唯一可靠的信息是「enter / leave」—— 渲染器一定会给进入的元素挂 enter hooks、
 *   给离开的元素挂 leave hooks。所以**族与方向一律以 router 写下的本次导航意图为准**，
 *   App.vue 里每条过渡规则都带 `:root[data-page-dir=…]` 前缀。
 */
const PAGE_DIR: Record<PageTransition, string> = {
  '': '',
  'page-slide-none': 'none',
  'page-slide-left': 'forward',
  'page-slide-right': 'back',
  'page-slide-tab-left': 'tab-left',
  'page-slide-tab-right': 'tab-right',
  'page-slide-up': 'up',
  'page-slide-down': 'down',
}

/**
 * 底部 tabbar 的平级页面。**值就是它们的左右顺序**，用来决定滑动方向：
 * 切到序号更大的 tab → 新页从右侧滑入；更小则从左侧滑入。
 * 同时这组页面之间互切会各自记住滚动位置（见下方 tabScrollPositions）。
 * ★ 顺序必须与 TabBarLayout.TAB_ITEMS 一致：首页(0) / 秒杀(1) / 购物车(2) / 我的(3)。
 */
const TAB_ORDER = new Map([
  ['home', 0],
  ['seckill', 1],
  ['cart', 2],
  ['member', 3],
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

  // 忘记密码是**登录页的下一层**，但**不做过渡动画** —— 点开即整体替换，
  // 与登录页内部点「立即注册」换表单是同一个观感（用户 2026-10-16 要求；
  // 这版之前先做成底部滑入、又改成横向推进，方向都不对）。
  // ★ 必须排在下面 login 那两条之前：「from 是 login 就向下滑走」会命中
  //   「从登录页进找回密码」，那样会变成有动画的、方向也不对。
  if (toName === 'forgot-password' || fromName === 'forgot-password') return 'page-slide-none'

  // 登录页是一个**全屏层**，跟其它页面之间没有「层级深浅」可言（购物车、商品详情、
  // 「我的」页都能进），所以必须在 depth 判断之前拦掉，否则会出现
  // 「从购物车进是右滑、从详情页进是硬切」的割裂感 —— 这正是本次要修的问题。
  if (toName === 'login') return 'page-slide-up'
  if (fromName === 'login') return 'page-slide-down'

  // 收货地址页从「确认订单」进入时，同样按全屏层处理，复用上面那套 up / down 过渡。
  // 原因：结算页里选地址本来就是一个从底部弹起的弹层，「管理收货地址」再让整页从底部
  // 升起来，是同一个动作的延续，比左右硬切自然。
  // **必须精确配对，不能按 depth 一刀切** —— 地址页还有「我的」→ 收货地址这个入口，
  // 那里应该是普通的右滑前进（depth 1 → 2），不能被这条规则改掉。
  if (toName === 'address' && fromName === 'checkout') return 'page-slide-up'
  if (toName === 'checkout' && fromName === 'address') return 'page-slide-down'

  // 商品详情页「立即购买」直达确认订单：语义上是往前走（进入结算），
  // 但 checkout 的 depth(2) 比 product-detail(3) 浅，按 depth 会被判成后退。
  // 与地址页同理，用精确配对修正方向；返回详情页时对称地按后退处理。
  // 另一个入口「购物车 → 确认订单」是 depth 1 → 2，本来就判为前进，不受影响。
  if (toName === 'checkout' && fromName === 'product-detail') return 'page-slide-left'
  if (toName === 'product-detail' && fromName === 'checkout') return 'page-slide-right'

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
    // 独立搜索页。
    // 注意：**这里没有「商品列表页」**。原先的 `/products`（带分类 tab + 排序栏的商品列表）
    // 已随「排序只在搜索结果里用」的结论删除 —— 浏览全部商品由首页承担（首页分类栏原地切换），
    // 排序由本页的结果区承担（见 ProductFeed）。
    // `/products/:id`（商品详情）是另一条路由，分享链接靠它，别一起删掉。
    //
    // depth 与首页的下一层对齐，所以「首页 → 搜索」天然判为 forward（右滑前进）、
    // 返回为 back，无需在 resolvePageTransition 里加配对规则。
    // 结果与空态在同一路由内切换，改词不产生新的历史记录（见 SearchView）。
    path: '/search',
    name: 'search',
    component: () => import('@/views/SearchView.vue'),
    meta: { title: '搜索', depth: 2 },
  },
  {
    // 秒杀专场：tabbar 第二个页签（见 TabBarLayout.TAB_ITEMS / 上面 TAB_ORDER）。
    // ★ depth 必须为 1（与首页同级）—— 否则 tab 互切会被判成「前进」，
    //   走整页左右滑动而不是 tab 平移动画；同时 tabbar 才会正常显示
    //   （它不在 TabBarLayout.FULLSCREEN_ROUTES 里）。
    path: '/seckill',
    name: 'seckill',
    component: () => import('@/views/SeckillView.vue'),
    meta: { title: '限时秒杀', depth: 1 },
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
    // 找回密码（忘记密码）。由登录页的「忘记密码？」进入，是登录页的下一层。
    // ★ **不做过渡动画** —— 点开即整体替换，与登录页内部点「立即注册」换表单
    //   同一个观感（用户 2026-10-16 要求）。配对规则见 resolvePageTransition。
    // ★ 页面根类名 `.forgot` 不在 App.vue 的 `:is(.login, .address)` 那组里 ——
    //   那组只服务 up/down 全屏层过渡，本页不走那套。
    //   但 `.forgot` 要留在常驻 `position: relative; z-index: 10` 规则里：
    //   登录页自己带 z-index: 10，本页没有的话无动画替换的那一两帧里会被它压住。
    // depth 取 4（比登录页深一层）：直接打开该 URL 这类没有 from 的场景按它兜底判方向。
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/ForgotPasswordView.vue'),
    meta: { title: '找回密码', depth: 4 },
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
    path: '/favorites',
    name: 'favorites',
    component: () => import('@/views/FavoriteListView.vue'),
    meta: { title: '我的收藏', requiresAuth: true, depth: 2 },
  },
  {
    // 浏览记录：与收藏页同构（同一层级的二级页、同样的列表与单条移除）。
    // depth 与收藏一致，因此「我的 → 浏览记录」天然判为前进（右滑）、返回为后退，
    // 不需要在 resolvePageTransition 里加配对规则。
    path: '/view-history',
    name: 'view-history',
    component: () => import('@/views/ViewHistoryView.vue'),
    meta: { title: '浏览记录', requiresAuth: true, depth: 2 },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { title: '个人信息', requiresAuth: true, depth: 2 },
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
    // 模拟第三方渠道收银台（微信 / 支付宝）。depth 比收银台深一层，
    // 因此进入是前进、返回是后退，不需要额外配对规则。
    path: '/pay-channel/:payNo',
    name: 'pay-channel',
    component: () => import('@/views/PayChannelView.vue'),
    meta: { title: '支付', requiresAuth: true, depth: 4 },
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
  // 过渡族 + 方向：CSS 只认这个属性，不认过渡类名（类名会被 KeepAlive 残留污染，见 PAGE_DIR 注释）
  document.documentElement.dataset.pageDir = PAGE_DIR[pageTransition.value]

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
