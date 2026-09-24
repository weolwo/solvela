import Antd from 'ant-design-vue';
import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import MemberGradeConfigList from '../member-grade-config-list.vue';

/**
 * 等级配置页。
 *
 * <h3>🔴 这个文件存在的原因是一个真实的、活了好几天的 bug</h3>
 * 页面通篇写的是 `record.currentGrade`，而 `/memberGrade/config/list` 返回的是
 * `MemberGrade`，那个字段叫 **`gradeCode`**。后果：
 * <ul>
 *   <li>「等级 N」标签一直是空的；</li>
 *   <li>等级 0 的停用开关<b>从来没有被禁用过</b>（服务端还拦着，所以只是白点一次）；</li>
 *   <li>「最低档不能停用」那句 tooltip 永远不出现。</li>
 * </ul>
 * 它不报错、不抛异常、控制台干净 —— 只是少显示了一点东西。
 * 管理端当时没有任何测试，所以没有任何东西会发现它。
 *
 * <h3>⚠️ 所以这里的断言故意「笨」</h3>
 * 断的是<b>渲染出来的字</b>，不是内部状态。字段名写错时内部状态看着也没问题
 *（`record.currentGrade` 就是 undefined，一个合法的值），
 * 只有渲染结果能把它抖出来。
 */

/** 服务端真实返回的形状 —— 字段名照着 MemberGrade 实体写，别照着页面写 */
const GRADES = [
  { id: 1, gradeCode: 0, gradeName: '普通会员', threshold: 0, pointsDiscount: 100, status: 1 },
  { id: 2, gradeCode: 1, gradeName: '银卡会员', threshold: 1000, pointsDiscount: 98, status: 1 },
  { id: 4, gradeCode: 3, gradeName: '白金会员', threshold: 20000, pointsDiscount: 92, status: 1 },
  { id: 5, gradeCode: 4, gradeName: '钻石会员', threshold: 60000, pointsDiscount: null, status: 0 },
];

vi.mock('/@/api/business/member/member-grade-api', () => ({
  memberGradeApi: {
    listConfig: () => Promise.resolve(GRADES.map((g) => ({ ...g }))),
    saveConfig: vi.fn(() => Promise.resolve()),
    updateConfigStatus: vi.fn(() => Promise.resolve()),
  },
}));

vi.mock('/@/lib/solvela-sentry', () => ({ solvelaSentry: { captureError: vi.fn() } }));

/* v-privilege 是全局指令，测试里不关心鉴权，注册一个空实现即可 */
const global = {
  plugins: [Antd],
  directives: { privilege: {} },
  stubs: { TableOperator: true },
};

async function mountPage() {
  const w = mount(MemberGradeConfigList, { global });
  // 等 onMounted 里那次 listConfig 落定
  await new Promise((resolve) => setTimeout(resolve, 0));
  await w.vm.$nextTick();
  return w;
}

describe('等级配置页', () => {
  it('🔴 每一行都要显示「等级 N」—— 字段名写错时这里是空的', async () => {
    const w = await mountPage();
    const html = w.html();

    expect(html).toContain('等级 0');
    expect(html).toContain('等级 3');
    expect(html).toContain('普通会员');
    expect(html).toContain('白金会员');
  });

  it('🔴 等级 0 的停用开关必须是禁用的', async () => {
    /*
     * 它是新会员的落点，也是判级函数的兜底。服务端也拦了这一条，
     * 这里禁用只是别让运营白点一次 —— 但「白点一次」正是当时唯一的症状，
     * 而没有人会为此提 bug。
     */
    const w = await mountPage();

    const switches = w.findAll('.ant-switch');
    expect(switches.length).toBe(GRADES.length);
    // 第一行是等级 0
    expect(switches[0].attributes('disabled')).toBeDefined();
    expect(switches[1].attributes('disabled')).toBeUndefined();
    expect(w.html()).toContain('不可停用');
  });

  it('商城积分折扣列：填了显示「几折」，留空显示「不打折」', async () => {
    const w = await mountPage();
    const html = w.html();

    // 92 → 9.2 折；运营填的是 92，但他要确认的是「白金打几折」
    expect(html).toContain('9.2 折');
    expect(html).toContain('9.8 折');
    // 100 与 null 都算不打折
    expect(html).toContain('不打折');
    expect(html).not.toContain('10 折');
  });

  it('⚠️ 表头必须是「最低积分价」那种说清楚的名字，不是光秃秃一个「积分」', async () => {
    const w = await mountPage();
    expect(w.html()).toContain('商城积分折扣');
  });
});
