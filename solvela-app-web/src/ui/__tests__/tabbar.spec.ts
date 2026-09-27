import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import TabBar from '../TabBar.vue'

/**
 * 底部导航：首页 / 商城 / 任务 / 我的。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>四个入口都在 —— 商城、任务此前压在首页顶部的小 tab 里，每天要做的事被放在了二级位置；</li>
 *   <li>🔴 在商城上只有「商城」高亮，「首页」不跟着亮：首页入口指的是 `feed`，不是四个页面共用的外壳。</li>
 * </ul>
 */

const stub = { template: '<div />' }

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/',
        name: 'home',
        component: { template: '<RouterView />' },
        children: [
          { path: '', name: 'feed', component: stub },
          { path: 'mall', name: 'mall', component: stub },
          { path: 'tasks', name: 'tasks', component: stub },
        ],
      },
      { path: '/me', name: 'mine', component: stub },
    ],
  })
}

describe('底部导航', () => {
  it('四个入口：首页 / 商城 / 任务 / 我的', async () => {
    const router = makeRouter()
    await router.push('/')
    const w = mount(TabBar, { global: { plugins: [router] } })

    expect(w.findAll('.sv-tabbar__label').map((l) => l.text())).toEqual([
      '首页',
      '商城',
      '任务',
      '我的',
    ])
  })

  it('🔴 在商城上只高亮「商城」，「首页」不跟着亮', async () => {
    const router = makeRouter()
    await router.push('/mall')
    const w = mount(TabBar, { global: { plugins: [router] } })

    const active = w.findAll('.sv-tabbar__item--active').map((a) => a.text())
    expect(active).toEqual(['商城'])
  })
})
