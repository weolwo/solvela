import Antd from 'ant-design-vue';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import CSidePreview from '../c-side-preview.vue';

/**
 * C 端效果预览。
 *
 * <h3>🔴 这个组件的历史就是「第二份实现会漂」</h3>
 * 它曾经自己算最低价、自己拼对价文案，和真实 C 端漂过一次 ——
 * 而且是<b>预览对、C 端错</b>（C 端发商品基准价，某件商品因此在列表上
 * 显示成真实价格的 100 倍）。两份实现里哪一份对都无所谓，
 * 问题是没有任何机制会发现它们不一样。
 *
 * <p>2026-09-24 改成调 `/mallCommodity/preview`，价全部由服务端算。
 * 所以<b>这个文件不测价格是多少</b> —— 那是后端 MallPricingTest 的事。
 * 这里只测两件本组件仍然负责的事：
 * <ol>
 *   <li>把服务端给的数字<b>写成人话</b>（千分位、¥ 两位小数、「起」、「几折」）；</li>
 *   <li>把服务端给的<b>结论</b>画对（专享锁、售罄、CTA 置灰）。</li>
 * </ol>
 *
 * <p>⚠️ 断言写的是<b>渲染出来的字</b>。这些值全是服务端塞进来的，
 * 内部状态怎么看都对 —— 只有渲染结果能抖出「字段名接错了」这类问题。
 */

/** 服务端 MallCommodityDetailView 的形状。字段名照着那个 record 写，别照着组件写 */
function view(overrides = {}) {
  return {
    commodityId: 1,
    commodityCode: 'M5YSA66CS2',
    commodityName: 'Huawei Mate 90',
    commodityIntro: '一台手机',
    commodityType: 'PHYSICAL',
    coverUrl: null,
    payType: 2,
    pointsPrice: 8800,
    listPointsPrice: 10000,
    gradeDiscountPercent: 88,
    cashPrice: '5000.00',
    priceVaries: false,
    originalPrice: '200000.00',
    availableStock: 199,
    limitPeriod: 'LIFETIME',
    limitCount: 1,
    remainingCount: 1,
    exchangeNotice: '兑完不退',
    skus: [{ skuId: 2, skuCode: 'S1', skuAttrs: { 颜色: '白色' }, pointsPrice: 8800, listPointsPrice: 10000 }],
    minGrade: 0,
    minGradeName: null,
    gradeLocked: false,
    ...overrides,
  };
}

/*
 * 🔴 预览接口的桩<b>不能用 vi.fn</b>，尽管那是最顺手的写法。
 *
 * vi.fn 为了填 `mock.settledResults`，会在被测代码拿到的那个 promise 上
 * 再挂一条 .then 链。返回值是 rejected promise 时，那条<b>派生链没有人 catch</b>，
 * 于是 vitest 报一条 "Unknown Error" 把用例判红 ——
 * 而组件的 try/catch 明明接住了（实测：preview 只被调了一次，视图也正确变成了空态）。
 *
 * 这个假失败查起来很费时间：报错指向桩的那一行，看上去像是「组件没处理异常」。
 * 所以这里用普通函数做桩，调用记录自己存一份。
 */
let previewImpl = () => Promise.resolve(null);
const previewCalls = [];

vi.mock('/@/api/business/mall/mall-commodity-api', () => ({
  mallCommodityApi: {
    preview: (...args) => {
      previewCalls.push(args);
      return previewImpl(...args);
    },
  },
}));

vi.mock('/@/lib/solvela-sentry', () => ({ solvelaSentry: { captureError: vi.fn() } }));

const global = { plugins: [Antd], directives: { privilege: {} } };

const PAYLOAD = { id: 1, commodityName: 'Huawei Mate 90' };

/*
 * ⚠️ a-drawer 把内容 teleport 到 body，所以 wrapper.html() 里只有一对
 *    <!--teleport start/end--> 注释 —— 断言必须读 document.body。
 *
 *    别用 stubs:{teleport:true} 绕：那个会把整段内容换成一个
 *    <teleport-stub> 占位符，等于把要测的东西 stub 掉了，
 *    用例会以「找不到那段字」的形式红，很容易被误判成组件坏了。
 */
function html() {
  return document.body.innerHTML;
}

function el(selector) {
  return document.body.querySelector(selector);
}

let wrapper = null;

async function mountPreview(v, payload = PAYLOAD) {
  previewImpl = () => Promise.resolve(v);
  wrapper = mount(CSidePreview, {
    props: { open: true, payload, grades: [] },
    attachTo: document.body,
    global,
  });
  await new Promise((resolve) => setTimeout(resolve, 0));
  await wrapper.vm.$nextTick();
  return wrapper;
}

beforeEach(() => {
  previewCalls.length = 0;
  previewImpl = () => Promise.resolve(null);
});

/* teleport 出去的节点不会跟着 wrapper 走，不手动清会串到下一条用例 */
afterEach(() => {
  wrapper?.unmount();
  wrapper = null;
  document.body.innerHTML = '';
});

describe('C端预览', () => {
  it('🔴 价全部来自服务端：组件只负责把它写成人话', async () => {
    await mountPreview(view());

    // 千分位 + 半角 ¥ + 两位小数，对齐 app-web/utils/cost.ts
    expect(html()).toContain('8,800 积分 + ¥5,000.00');
    expect(html()).toContain('8.8折');
    expect(html()).toContain('10,000 积分'); // 划线的挂牌价
    // 🔴「价值 ¥」，不是划掉的裸数字 —— 它是「值多少钱」，不是「原来要多少积分」
    expect(html()).toContain('价值 ¥200,000.00');
  });

  it('各规格不同价时加「起」', async () => {
    await mountPreview(view({ priceVaries: true }));
    expect(html()).toContain('8,800 积分 + ¥5,000.00 起');
  });

  it('🔴 商品退出等级折扣：两个价相等就不出角标，哪怕折扣率还是 88', async () => {
    /*
     * 判据是两个价不相等，不是 gradeDiscountPercent < 100。
     * 按折扣率判会在一件一分没便宜的商品上挂出「8.8折」。
     */
    await mountPreview(view({ pointsPrice: 10000, listPointsPrice: 10000 }));

    expect(html()).toContain('10,000 积分 + ¥5,000.00');
    expect(html()).not.toContain('8.8折');
  });

  it('专享商品：出锁标，CTA 变成专享文案且置灰', async () => {
    await mountPreview(view({ minGrade: 3, minGradeName: '白金会员', gradeLocked: true }));

    expect(html()).toContain('白金会员专享');
    expect(el('.phone-cta').className).toContain('phone-cta--off');
    expect(el('.phone-cta').textContent.trim()).toBe('白金会员专享');
  });

  it('售罄：CTA 变「已兑完」且置灰，库存那一行也跟着变', async () => {
    await mountPreview(view({ availableStock: 0 }));

    expect(el('.phone-cta').textContent.trim()).toBe('已兑完');
    expect(el('.phone-cta').className).toContain('phone-cta--off');
    expect(html()).not.toContain('现货');
  });

  it('有货时显示现货件数，而不是「已兑 N 件」—— C 端任何页面都不显示已兑数', async () => {
    await mountPreview(view());

    expect(html()).toContain('现货 199 件');
    expect(html()).not.toContain('已兑 ');
  });

  it('⚠️ 新建商品（还没有 id）要提示「覆盖价要保存后才准」', async () => {
    /*
     * 单品覆盖价按 commodity_id 配，新商品还没有 id，服务端查不到。
     * 不提示的话，运营会以为自己配的特价没生效。
     */
    await mountPreview(view({ commodityId: null }), { id: null, commodityName: '新商品' });
    expect(html()).toContain('还没保存的新商品');
  });

  it('切换预览身份要重新问服务端，而不是自己换算', async () => {
    const w = await mountPreview(view());
    expect(previewCalls.length).toBe(1);
    expect(previewCalls[0]).toEqual([PAYLOAD, 0]);

    previewImpl = () => Promise.resolve(view({ pointsPrice: 1888, gradeDiscountPercent: 19 }));
    w.vm.gradeCode = 4;
    await new Promise((resolve) => setTimeout(resolve, 0));
    await w.vm.$nextTick();

    expect(previewCalls.at(-1)).toEqual([PAYLOAD, 4]);
    expect(html()).toContain('1,888 积分');
    expect(html()).toContain('1.9折');
  });

  it('接口打回来（必填项没填完）时给一句人话，而不是白屏', async () => {
    /*
     * ⚠️ 拒绝值用【归一后的对象】，不是 new Error。
     *    这个应用的 axios 拦截器把后端的 4xx 归一成
     *    { status, code, message, traceId } 再 reject（见 lib/axios.js），
     *    组件拿到的从来不是一个 Error 实例。
     *    用 Error 造的用例会在一个真实不存在的形状上绿或红，两种都没有意义。
     */
    previewImpl = () =>
      Promise.reject({ status: 400, code: 'INVALID_ARGUMENT', message: '请上传商品封面', traceId: 'x' });
    wrapper = mount(CSidePreview, {
      props: { open: true, payload: PAYLOAD, grades: [] },
      attachTo: document.body,
      global,
    });
    await flushPromises();
    await wrapper.vm.$nextTick();

    expect(previewCalls.length).toBe(1);
    expect(el('.phone')).toBeNull();
    expect(html()).toContain('必填项还没填完');
  });
});
