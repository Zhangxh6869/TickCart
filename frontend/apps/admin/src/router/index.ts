import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAdminStore } from '@/stores/admin'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '后台登录' },
  },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/products',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'products',
        name: 'products',
        component: () => import('@/views/ProductManageView.vue'),
        meta: { title: '商品管理', requiresAuth: true },
      },
      {
        path: 'categories',
        name: 'categories',
        component: () => import('@/views/CategoryManageView.vue'),
        meta: { title: '分类管理', requiresAuth: true },
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/OrderManageView.vue'),
        meta: { title: '订单管理', requiresAuth: true },
      },
      {
        path: 'search-analytics',
        name: 'search-analytics',
        component: () => import('@/views/SearchAnalyticsView.vue'),
        meta: { title: '搜索分析', requiresAuth: true },
      },
      {
        path: 'seckill',
        name: 'seckill',
        component: () => import('@/views/SeckillManageView.vue'),
        meta: { title: '秒杀管理', requiresAuth: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 后台整棵子树需登录 + admin 角色
router.beforeEach(async (to) => {
  const adminStore = useAdminStore()
  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth)

  if (requiresAuth && !adminStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (adminStore.isLoggedIn && !adminStore.userInfo) {
    try {
      await adminStore.loadUserInfo()
    } catch {
      // 令牌失效由拦截器处理
    }
  }

  // 非管理员访问后台直接拒绝
  if (requiresAuth && adminStore.userInfo && !adminStore.isAdmin) {
    await adminStore.logout()
    return { name: 'login' }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · TickCart 后台` : 'TickCart 管理后台'
})

export default router
