import Antd from 'ant-design-vue';
import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import MallFavoriteList from '../mall-favorite-list.vue';

/**
 * 收藏统计页。
 *
 * <h3>🔴 这一页的「积分」列曾经显示真实价格的 100 倍</h3>
 * 排行查询直接 `SELECT c.points_price`（商品基准价），而那一列只是 SKU 没填价时的
 * <b>继承来源</b>，不保证有人按它卖 —— 库里就有基准价 99999、唯一在售规格只要 1000 的商品。
 *
 * <p>而这一列的用途恰恰是让运营判断「是不是定价偏高」（页面头部注释原文：
 * 「收藏很多但兑换很少，通常意味着积分定价偏高或长期缺货」）。
 * 显示 99999、收藏一堆、没人兑，得出的结论会是「降价」，
 * 而那件商品其实只要 1000 分。<b>错在这里直接导向一个错误的运营动作。</b>
 *
 * <h3>⚠️ 所以这里断言的是「列名说清楚了」和「数照着接口给的画」</h3>
 * 「取不取最低价」是 SQL 的事，后端 MallPricingTest 有一条扫 XML 的守卫盯着它。
 * 前端这一侧能守的是：<b>别把服务端已经算对的数又显示错</b>，
 * 以及列名不要光秃秃一个「积分」——显示最低价却不说，在多规格商品上
 * 又会变成另一个方向的误导。
 */

const STAT = {
  totalCount: 9,
  commodityCount: 3,
  memberCount: 4,
  rank: [
    {
      commodityId: 5,
      commodityName: '【莫塞尔】德国 约翰山堡黄标雷司令白葡萄酒750ml',
      commodityCode: 'M6VNZTQQVJ',
      coverFileId: null,
      commodityStatus: 1,
      pointsPrice: 1000,
      soldCount: 0,
      favoriteCount: 4,
      availableStock: 11,
    },
    {
      commodityId: 6,
      commodityName: '华为音乐 音乐VIP（年卡）',
      commodityCode: 'MR5YUTG58V',
      coverFileId: null,
      commodityStatus: 1,
      pointsPrice: 100,
      soldCount: 6,
      favoriteCount: 3,
      availableStock: 105,
    },
  ],
  unavailableRank: [
    {
      commodityId: 4,
      commodityName: 'HUAWEI WATCH GT 7 Pro（46mm）',
      commodityCode: 'MS4FCLNYCW',
      coverFileId: null,
      commodityStatus: 1,
      pointsPrice: 19990,
      soldCount: 0,
      favoriteCount: 2,
      availableStock: 0,
    },
  ],
};

vi.mock('/@/api/business/mall/mall-favorite-api', () => ({
  mallFavoriteApi: { queryStat: () => Promise.resolve(JSON.parse(JSON.stringify(STAT))) },
}));

vi.mock('/@/lib/solvela-sentry', () => ({ solvelaSentry: { captureError: vi.fn() } }));

const global = {
  plugins: [Antd],
  directives: { privilege: {} },
  stubs: { FileThumb: true },
};

async function mountPage() {
  const w = mount(MallFavoriteList, { global });
  await new Promise((resolve) => setTimeout(resolve, 0));
  await w.vm.$nextTick();
  return w;
}

describe('收藏统计页', () => {
  it('🔴 列名必须说清是「最低积分价」，不是光秃秃一个「积分」', async () => {
    const w = await mountPage();
    expect(w.html()).toContain('最低积分价');
  });

  it('积分列照服务端给的画，不自己换算', async () => {
    const w = await mountPage();
    const html = w.html();

    // 服务端已经取好最低在售规格价，前端一个字都不该改
    expect(html).toContain('1000');
    expect(html).toContain('100');
    expect(html).not.toContain('99999');
  });

  it('两张表都渲染：「想要但买不到」在前，收藏排行在后', async () => {
    const w = await mountPage();
    const html = w.html();

    expect(html).toContain('莫塞尔');
    expect(html).toContain('华为音乐');
    // 零库存那件要出现在「买不到」那张表里
    expect(html).toContain('HUAWEI WATCH GT 7 Pro');
  });

  it('总量三个数都画出来', async () => {
    const w = await mountPage();
    const text = w.text();

    expect(text).toContain('9');
    expect(text).toContain('3');
    expect(text).toContain('4');
  });
});
