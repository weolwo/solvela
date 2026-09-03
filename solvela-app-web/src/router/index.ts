import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 是否允许未登录访问。**默认需要登录**，公开页要显式声明 */
    anonymous?: boolean
    title?: string
    /** 是否是底部导航的一级页。为 true 时 App.vue 渲染 TabBar 并留出底部空间 */
    tab?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { anonymous: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    // 注册当然要匿名可访问。注意默认是【需要登录】的，公开页必须显式开口子 ——
    // 反过来写的话，新加页面忘了标记就是默默裸奔，而那个方向的错误在 C 端是数据泄露
    meta: { anonymous: true, title: '注册' },
  },
  // ---- 底部导航的三个一级页 ----
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: 'Solvela', tab: true },
  },
  {
    path: '/promo',
    name: 'promo',
    component: () => import('@/views/PromoView.vue'),
    meta: { title: '优惠', tab: true },
  },
  {
    path: '/me',
    name: 'mine',
    component: () => import('@/views/MineView.vue'),
    meta: { title: '我的', tab: true },
  },
  // ---- 二级页：从「我的」点进去，不在底部 Tab 里，需要登录（默认） ----
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: { title: '设置' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { anonymous: true, title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: 0 },
})

/**
 * 默认需要登录，`meta.anonymous` 显式开口子。
 *
 * 反过来写（默认公开、需要登录的加标记）意味着新加页面时忘了标记 = 默默裸奔，
 * 而这个方向的错误在 C 端是数据泄露。
 */
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.anonymous === true) {
    return true
  }

  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  await auth.restore()

  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
