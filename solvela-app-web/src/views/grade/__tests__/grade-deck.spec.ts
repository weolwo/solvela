import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import GradeView from '../GradeView.vue'

import type { GradeLadderItem, MyGrade } from '@/api/grade'

/**
 * 会员中心的卡片组。
 *
 * <h3>这里只守两条，因为只有这两条错了用户会被骗</h3>
 * <ol>
 *   <li><b>初始停在自己那一档</b> —— 不做的话，每个人打开会员中心看到的都是
 *       最低档，钻石用户要自己划四下才看得见自己。这一条在 jsdom 里测得到，
 *       因为它是「组件挂载后 emit 了哪个下标」，不依赖真实滚动；</li>
 *   <li><b>滑到别档时，页面要说清那不是你的</b> —— 卡片式改版带进来的新风险：
 *       下面整块权益跟着换，不说的话看起来就像「这些我都有」。
 *       用户会去用，然后发现用不了。</li>
 * </ol>
 *
 * <h3>🔴 第三条最容易错的，是 current 与 reached 的关系</h3>
 * 保级缓冲期里用户<b>在</b>白金但成长值<b>够不着</b>白金。第一版就写错了两处：
 * 徽标写成了 if/else-if/else 链（current 一命中，「未解锁」永远出不来），
 * 权益压暗判的是 reached（于是页面一边写「这是你现在享有的权益」、
 * 一边把它们灰掉）。两处都是真机上才看出来的，所以钉在这里。
 *
 * <h3>⚠️ 测不到的：真实的滑动</h3>
 * jsdom 没有布局，`offsetLeft` / `clientWidth` 全是 0，scroll-snap 更不存在 ——
 * 「手指划一下会停在哪一张」这件事这里<b>验证不了</b>，只能靠真机。
 * 所以下面走的是点圆点那条路，它和滑动共用同一个 active 状态与同一套渲染。
 */

const LADDER: GradeLadderItem[] = [
  { gradeCode: 0, gradeName: '普通会员', threshold: 0, reached: true, current: false, privileges: [] },
  {
    gradeCode: 1,
    gradeName: '银卡会员',
    threshold: 1000,
    reached: true,
    current: false,
    privileges: [
      {
        privilegeCode: 'EXCLUSIVE_TASK',
        privilegeName: '银卡专享任务',
        description: null,
        iconFileId: null,
        actionUrl: null,
      },
    ],
  },
  {
    gradeCode: 2,
    gradeName: '金卡会员',
    threshold: 5000,
    reached: false,
    // 🔴 在这一档，但成长值够不着 —— 保级缓冲期就长这样
    current: true,
    privileges: [
      {
        privilegeCode: 'BIRTHDAY_GIFT',
        privilegeName: '生日礼',
        description: '生日当天领取',
        iconFileId: null,
        actionUrl: null,
      },
    ],
  },
  {
    gradeCode: 3,
    gradeName: '白金会员',
    threshold: 20000,
    reached: false,
    current: false,
    privileges: [
      {
        privilegeCode: 'EXCLUSIVE_POOL',
        privilegeName: '白金专享奖池',
        description: null,
        iconFileId: null,
        actionUrl: null,
      },
    ],
  },
]

const MY_GRADE: MyGrade = {
  gradeCode: 2,
  gradeName: '金卡会员',
  gradeSince: '2026-09-01 00:00:00',
  currentValue: 3000,
  totalValue: 3000,
  periodEnd: '2027-01-01 00:00:00',
  nextGradeCode: 3,
  nextGradeName: '白金会员',
  nextThreshold: 20000,
  gapToNext: 17000,
  inProtect: true,
  protectUntil: '2026-11-05 00:00:00',
  protectGrade: 2,
  protectGap: 2000,
  boostMultiplier: 2,
  ladder: LADDER,
}

vi.mock('@/api/grade', () => ({
  fetchMyGrade: () => Promise.resolve(MY_GRADE),
  fetchGrowthLog: () => Promise.resolve([]),
}))

/*
 * ui/ 里的几个壳组件在这条测试里只要能把插槽渲染出来就行。
 *
 * ⚠️ Section 的 stub 必须把 title 渲染出来 —— 这一页有一条断言正是
 * 「标题要带着档名」（「白金会员的权益」而不是「我的权益」）。
 * stub 掉 title 的话那条断言永远查的是一个不存在的元素，
 * 红了也不是因为功能坏了。
 */
const STUBS = {
  NavBar: { template: '<div />' },
  Section: { props: ['title'], template: '<section><h2>{{ title }}</h2><slot /></section>' },
  Card: { template: '<div><slot /></div>' },
}

async function mountPage() {
  const wrapper = mount(GradeView, { global: { stubs: STUBS } })
  await flushPromises()
  await flushPromises()
  return wrapper
}

describe('会员中心卡片组', () => {
  it('🔴 一进页面就停在用户自己那一档，不是最低档', async () => {
    const wrapper = await mountPage()

    const active = wrapper.find('.deck__card--active')
    expect(active.exists()).toBe(true)
    expect(active.find('.deck__name').text()).toBe('金卡会员')
    // 下面的权益区也要跟着是自己那一档，不能先渲染成普通会员再闪一下
    expect(wrapper.text()).toContain('金卡会员的权益')
  })

  it('🔴 缓冲期：同一张卡要同时挂「当前」和「未解锁」', async () => {
    const wrapper = await mountPage()

    const badges = wrapper
      .find('.deck__card--active')
      .findAll('.deck__badge')
      .map((b) => b.text())

    // 写成 if/else-if/else 链的话，current 一命中就短路，「未解锁」永远出不来 ——
    // 而「你正挂在一档自己撑不住的等级上」正是这一页最该说出口的一句
    expect(badges).toContain('当前')
    expect(badges).toContain('未解锁')
  })

  it('🔴 缓冲期：权益是他真在享有的，不许压暗成「未解锁」', async () => {
    const wrapper = await mountPage()

    // reached=false 但 current=true。拿 reached 当「是不是你的」，
    // 页面就会一边写「这是你现在享有的权益」一边把它们灰掉，自己跟自己打架
    expect(wrapper.findAll('.priv--locked')).toHaveLength(0)
    expect(wrapper.find('.own').text()).toContain('你现在享有')
    // 但也要把「暂未达标」说出来，否则他以为一切正常，而他其实正要掉下去
    expect(wrapper.find('.own').text()).toContain('暂未达标')
  })

  it('🔴 换到没解锁的一档：权益跟着换，并且要说清那不是你的', async () => {
    const wrapper = await mountPage()

    // 第 4 个圆点 = 白金会员（reached=false, current=false）
    await wrapper.findAll('.deck__dot')[3]!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('白金会员的权益')
    expect(wrapper.text()).toContain('白金专享奖池')
    // 标题带档名 +「还没解锁」+ 差多少，三样都要有
    expect(wrapper.find('.own').text()).toContain('未解锁')
    expect(wrapper.find('.own').text()).toContain('17000')
    expect(wrapper.findAll('.priv--locked').length).toBeGreaterThan(0)
  })

  it('换到已达成的低档：说「你已达成」，不压暗', async () => {
    const wrapper = await mountPage()

    await wrapper.findAll('.deck__dot')[1]!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('银卡会员的权益')
    expect(wrapper.find('.own').text()).toContain('已达成')
    expect(wrapper.findAll('.priv--locked')).toHaveLength(0)
  })

  it('⚠️ 滑动只换权益，不换「我自己的进度」', async () => {
    const wrapper = await mountPage()

    await wrapper.findAll('.deck__dot')[3]!.trigger('click')
    await flushPromises()

    // 放进卡片里的话，滑到白金卡时那条进度会被读成「我离白金还有这么近」。
    // 它讲的始终是用户自己的进度，所以滑动之后必须一个字都没变
    expect(wrapper.find('.mine').text()).toContain('本期成长值')
    expect(wrapper.find('.mine').text()).toContain('17000')
  })
})
