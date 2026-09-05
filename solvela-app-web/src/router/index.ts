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
  // ---- 底部导航的两个一级页 ----
  /*
   * 首页是个壳，三个顶部 tab 是它的子路由，不是壳里的一个 activeTab 变量。
   * 这样返回键能在 tab 之间退、能深链到某个 tab、三个 pane 各自懒加载。
   *
   * meta 会沿 matched 链合并，所以子路由的 to.meta.tab 也是 true，
   * App.vue 照常渲染底部导航；anonymous 没开口子，三个 tab 都需要登录。
   */
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/home/HomeView.vue'),
    meta: { title: 'Solvela', tab: true },
    redirect: { name: 'feed' },
    children: [
      /*
       * 首页 tab 自己。名字不能也叫 home —— 那个名字归外壳（父路由），
       * 底部导航指的是外壳，这样在商城/任务/活动三个子路由上「首页」也保持高亮。
       */
      {
        path: '',
        name: 'feed',
        component: () => import('@/views/home/Home.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'mall',
        name: 'mall',
        component: () => import('@/views/home/Mall.vue'),
        meta: { title: '商城' },
      },
      {
        path: 'tasks',
        name: 'tasks',
        component: () => import('@/views/home/Tasks.vue'),
        meta: { title: '任务中心' },
      },
      {
        path: 'activities',
        name: 'activities',
        component: () => import('@/views/home/Activities.vue'),
        meta: { title: '活动中心' },
      },
    ],
  },
  // 老的底部「优惠」入口。链接可能已经被分享/收藏出去了，留一条 redirect 而不是让它 404
  { path: '/promo', redirect: { name: 'activities' } },
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
  // ---- 活动专题页：从「优惠」点进去。分享入口，匿名可看（对齐后端 @Anonymous），
  //      抽奖那一步再要求登录 ----
  {
    path: '/activity/:code',
    name: 'activity',
    component: () => import('@/views/activity/ActivityView.vue'),
    meta: { anonymous: true, title: '活动' },
  },
  // ---- 商品详情：从商城/首页点进去。分享入口，匿名可看（对齐活动专题页），
  //      加入购物车那一步再要求登录 ----
  {
    path: '/product/:id',
    name: 'product',
    component: () => import('@/views/product/ProductView.vue'),
    meta: { anonymous: true, title: '商品详情' },
  },
  // ---- 兑换确认页：从商品详情点「立即兑换」进来。要登录（默认）----
  //      规格选择走 query（?attr_COLOR=NAVY），刷新和「去地址簿挑地址再回来」都不丢
  {
    path: '/redeem/:id',
    name: 'redeem',
    component: () => import('@/views/redeem/RedeemView.vue'),
    meta: { title: '确认兑换' },
  },
  /*
   * ---- 我的记录：两页，各进各的 ----
   *
   * 「我的」页只放入口，不直接铺记录列表：那一页本来就要放钱包、收藏、
   * 地址簿、设置，再铺一段列表会把它变成一个什么都有、什么都看不清的页面。
   *
   * 兑换（我花积分换的）和优惠（平台发给我的）分成两页，是因为它们的
   * 状态机、金额口径、用户想知道的事完全不同 —— 合成一页每条只能显示最小公约数。
   *
   * 🔴 奖励记录<b>不在这里</b>：它是「我在某个活动里中了什么」，
   * 属于活动，展示在活动专题页上（按 activityCode 过滤）。
   */
  {
    path: '/records/exchange',
    name: 'records-exchange',
    component: () => import('@/views/records/ExchangeRecordsView.vue'),
    meta: { title: '兑换记录' },
  },
  {
    path: '/records/promo',
    name: 'records-promo',
    component: () => import('@/views/records/PromoRecordsView.vue'),
    meta: { title: '优惠记录' },
  },
  // ---- 我的收藏：从「我的」进去。要登录（默认）——收藏本来就是「我的」东西 ----
  {
    path: '/favorites',
    name: 'favorites',
    component: () => import('@/views/favorite/FavoriteView.vue'),
    meta: { title: '我的收藏' },
  },
  // ---- 地址簿：从「我的」进是管理，从兑换页进（?pick=1）是挑一个 ----
  {
    path: '/address',
    name: 'address-list',
    component: () => import('@/views/address/AddressListView.vue'),
    meta: { title: '地址簿' },
  },
  {
    path: '/address/new',
    name: 'address-new',
    component: () => import('@/views/address/AddressFormView.vue'),
    meta: { title: '新增收货地址' },
  },
  {
    path: '/address/:id',
    name: 'address-edit',
    component: () => import('@/views/address/AddressFormView.vue'),
    meta: { title: '编辑收货地址' },
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
