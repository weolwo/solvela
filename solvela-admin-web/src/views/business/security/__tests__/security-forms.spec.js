import Antd from 'ant-design-vue';
import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import SecurityGate from '../security-gate.vue';
import SecurityListList from '../security-list-list.vue';
import RateLimitRule from '../rate-limit-rule.vue';

/**
 * 安全中心几个「新增」弹窗的交互。
 *
 * <h3>🔴 这里断言的是「不合法的东西填不出来」，不是「填错了会报错」</h3>
 * 这几条规则本来都长在服务端（会员维度没有黑名单、灰名单必须有分、关键字规则不许命中反例集），
 * 页面把它们做成了联动与预检。做成联动的东西会悄悄坏掉 —— 加一个维度、改一个枚举值，
 * 联动失效了页面照样渲染，只是又回到「填完一屏、被一条红字打回来」。所以要有测试盯着。
 */

const SCENE_RULE = {
  id: 1,
  targetType: 1,
  target: 'LOGIN',
  dimension: 1,
  burst: 3,
  limit3m: 9,
  limit5m: 12,
  limit10m: 20,
  longWindow: null,
  limitLong: null,
  mode: 2,
};

vi.mock('/@/api/business/security/security-api', () => ({
  securityApi: {
    rateLimitPage: () =>
      Promise.resolve({
        rules: [SCENE_RULE],
        routes: [{ endpointKey: 'POST /login', scene: 'LOGIN' }],
        versions: { published: 'v1', instances: [] },
      }),
    getGate: () =>
      Promise.resolve({
        banSeconds: 86400,
        breakerWindowSeconds: 600,
        breakerDistinctIps: 20,
        honeypots: [],
        keywords: [],
      }),
    queryListPage: () => Promise.resolve({ list: [], total: 0 }),
  },
}));

vi.mock('/@/lib/solvela-sentry', () => ({ solvelaSentry: { captureError: vi.fn() } }));

const global = {
  plugins: [Antd],
  directives: { privilege: {} },
  config: { globalProperties: { $privilege: () => true } },
};

async function flush(wrapper) {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await wrapper.vm.$nextTick();
  await wrapper.vm.$nextTick();
}

/** 弹窗 teleport 到 body，所以断言都在 document 上做 */
async function openModal(component, buttonText) {
  const wrapper = mount(component, { global, attachTo: document.body });
  await flush(wrapper);
  const button = wrapper.findAll('button').find((b) => b.text().includes(buttonText));
  await button.trigger('click');
  await flush(wrapper);
  return wrapper;
}

function radioByLabel(text) {
  return [...document.querySelectorAll('.ant-modal label.ant-radio-button-wrapper')].find(
    (el) => el.textContent.trim() === text,
  );
}

function disabled(label) {
  return radioByLabel(label).classList.contains('ant-radio-button-wrapper-disabled');
}

function checked(label) {
  return radioByLabel(label).classList.contains('ant-radio-button-wrapper-checked');
}

async function clickRadio(wrapper, label) {
  radioByLabel(label).querySelector('input').click();
  await flush(wrapper);
}

async function typeInto(wrapper, placeholderPart, text) {
  const input = [...document.querySelectorAll('.ant-modal input, .ant-modal textarea')].find((el) =>
    (el.placeholder || '').includes(placeholderPart),
  );
  input.value = text;
  input.dispatchEvent(new Event('input'));
  await flush(wrapper);
}

/** antd 会在两个汉字之间插一个空格（「保 存」），所以比对前先把空白去掉 */
function saveButton() {
  return [...document.querySelectorAll('.ant-modal-footer button')].find((b) => b.textContent.replace(/\s/g, '') === '保存');
}

function modalText() {
  return document.querySelector('.ant-modal-wrap')?.textContent || '';
}

beforeEach(() => {
  document.body.innerHTML = '';
});

describe('新增名单', () => {
  it('🔴 会员维度选不出黑名单，也选不出「长期」', async () => {
    const wrapper = await openModal(SecurityListList, '新增名单');
    expect(disabled('黑名单')).toBe(false);

    await clickRadio(wrapper, '会员');

    // 封号走会员状态（冻结），名单里再放一份就有了两个答案
    expect(disabled('黑名单')).toBe(true);
    // 会员名单必须有到期：一个人不该因为某个月的一次异常，永远比别人多验几道码
    expect(disabled('长期')).toBe(true);
    // 本来选中的黑名单被就地换成灰名单，而不是留在一个存不进去的组合上
    expect(checked('灰名单')).toBe(true);

    wrapper.unmount();
  });

  it('「命中加分」只在灰名单上出现 —— 黑白名单带着分提交会被后端拒', async () => {
    const wrapper = await openModal(SecurityListList, '新增名单');
    expect(modalText()).not.toContain('命中加分');

    await clickRadio(wrapper, '灰名单');
    expect(modalText()).toContain('命中加分');

    wrapper.unmount();
  });
});

describe('新增关键字规则', () => {
  it('🔴 裸词正则在保存之前就被反例集拦下，并指出命中的是哪一条', async () => {
    const wrapper = await openModal(SecurityGate, '新增关键字规则');

    await typeInto(wrapper, 'sleep', 'or\\(');

    expect(modalText()).toContain('命中了正常用户真的会输入的东西');
    // 断言要落在命中清单那一块里，不能落在「不用裸词」那句说明上 —— 说明里也写着一个例子
    const hits = [...document.querySelectorAll('.ant-modal .ant-alert-error .hit-row')].map((el) => el.textContent);
    expect(hits.some((t) => t.includes('Floor(落地灯)') && t.includes('or('))).toBe(true);
    expect(hits.some((t) => t.includes('Color(2024)!'))).toBe(true);

    wrapper.unmount();
  });

  it('带词边界的探测特征预检通过', async () => {
    const wrapper = await openModal(SecurityGate, '新增关键字规则');

    await typeInto(wrapper, 'sleep', '\\bsleep\\s*\\(\\s*\\d+\\s*\\)');

    expect(modalText()).toContain('本地预检通过');
    expect(modalText()).not.toContain('命中了正常用户');

    wrapper.unmount();
  });

  it('正则写不对就说写不对，不用等服务端', async () => {
    const wrapper = await openModal(SecurityGate, '新增关键字规则');

    await typeInto(wrapper, 'sleep', '(unclosed');

    expect(modalText()).toContain('正则写不对');

    wrapper.unmount();
  });
});

describe('新增接口覆盖', () => {
  it('默认阈值自己就是合法的（满足亚线性），保存键不是灰的', async () => {
    const wrapper = await openModal(RateLimitRule, '新增接口覆盖');

    expect(document.querySelectorAll('.ant-modal .ant-form-item-explain-error').length).toBe(0);
    expect(saveButton().disabled).toBe(false);

    wrapper.unmount();
  });

  it('🔴 不满足亚线性时当场标红，并说清这一格最多能填多少', async () => {
    const wrapper = await openModal(RateLimitRule, '新增接口覆盖');

    // 四个格子依次是 突发 / 3 / 5 / 10 分钟。把 10 分钟改成 5 分钟的两倍：10 分钟窗口就永远不会先触发
    const tenMinutes = [...document.querySelectorAll('.ant-modal .ant-input-number-input')][3];
    tenMinutes.value = '30';
    tenMinutes.dispatchEvent(new Event('input'));
    await flush(wrapper);

    expect(modalText()).toContain('10 分钟最多 29');
    expect(saveButton().disabled).toBe(true);

    wrapper.unmount();
  });

  it('四个窗口都折算成「次 / 分」摆着 —— 亚线性是看出来的，不是读出来的', async () => {
    const wrapper = await openModal(RateLimitRule, '新增接口覆盖');

    // 默认 10 / 15 / 25 对应 3.3、3.0、2.5 次 / 分
    expect(modalText()).toContain('3.3 次 / 分');
    expect(modalText()).toContain('3.0 次 / 分');
    expect(modalText()).toContain('2.5 次 / 分');

    wrapper.unmount();
  });
});
