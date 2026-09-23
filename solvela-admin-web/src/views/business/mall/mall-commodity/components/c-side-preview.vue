<!--
  * C端效果预览
  *
  * 运营在后台填的是一堆分散的字段，但用户看到的是一屏。中间隔着想象力 ——
  * 「划线原价填了但纯积分商品不展示」「兑换须知写太长会被截断」这类问题，
  * 不摆出来就只能等上线后被投诉才发现。
  *
  * 【🔴 这一屏的价全部由服务端算好，本组件一个都不算】
  * 它调的是 /mallCommodity/preview：服务端拿【当前表单】走
  *   保存那条路的同一份映射（MallCommodityService.toEntity / toSkuEntity）
  *   ＋ C 端详情页的同一个渲染（MallClientFacade.toDetailView）
  * 渲染出来，不落库。所以「最低价取哪个规格」「折扣打几折」「要不要加『起』」
  * 「专享锁不锁」，这里<b>没有任何一处在自己判断</b>。
  *
  * 为什么要这么绕：此前这里是一段自己算价的 JS，和真实 C 端漂过一次 ——
  * 而且是【预览对、C 端错】（C 端发商品基准价，某件商品因此在列表上显示成
  * 真实价格的 100 倍）。两份实现里哪一份对都无所谓，
  * 问题是没有任何机制会发现它们不一样。
  *
  * 【⚠️ 本组件仍然自己做的一件事：把数字写成人话】
  * 「45,000 积分 + ¥299.00 起」这类文案在 C 端是 app-web/utils/cost.ts，
  * 两个应用没法共享代码，所以这里还留着一份 formatPoints / formatCash / 折扣文案。
  * 它们只管【逗号点在哪】，不管【钱是多少】——
  * 漂了顶多是格式难看，不会再出现把价格显示成 100 倍那种事。改 cost.ts 时回来对一眼。
  *
  * 【⚠️ 和真实 C 端刻意不同的地方】
  *   1. 没有会员：等级由运营在「预览身份」里选，收藏恒为 false，
  *      「还可兑几件」按一个从没兑过的人算 —— 那是新用户看到的样子。
  *   2. 新建商品还没有 id，查不到单品覆盖价（它按 commodity_id 配），
  *      这时预览只反映全场折扣率。保存一次之后就准了，抽屉里有提示。
  *   3. 规格是静态展示，不还原 C 端「选了颜色之后尺码会灰掉」那套可选性收窄。
  *   4. 轮播图与图文详情不铺开 —— 抽屉里放不下，也不是这个预览要回答的问题。
  *
  * @Copyright  weolwo
-->
<template>
  <a-drawer :open="open" title="C端效果预览" width="420" placement="right" @update:open="(v) => $emit('update:open', v)">
    <!--
      等级选择器。C 端的价是跟人走的，没有它，预览只能回答「未登录看到什么」——
      而运营刚配完的往往正是「白金看到什么」。
    -->
    <div class="preview-bar">
      <span class="preview-bar-label">预览身份</span>
      <a-select v-model:value="gradeCode" :options="gradeOptions" size="small" style="width: 190px" />
    </div>

    <a-alert
      v-if="payload && !payload.id"
      type="info"
      show-icon
      class="mb-2"
      message="这是个还没保存的新商品"
      description="单品覆盖价按商品 id 配，新商品还没有 id，所以这一屏只反映全场折扣率。保存一次之后再看就准了。"
    />

    <a-spin :spinning="loading">
      <div v-if="view" class="phone">
        <div class="phone-body">
          <div class="phone-cover">
            <img v-if="view.coverUrl" class="phone-cover-img" :src="view.coverUrl" alt="" />
            <span v-else class="phone-cover-empty">未设置主图</span>
          </div>

          <div class="phone-content">
            <div class="flex items-baseline gap-2 flex-wrap">
              <span class="price-main">{{ costText }}</span>
              <span v-if="discountText" class="tag-discount">{{ discountText }}</span>
            </div>

            <!-- 两个划线位：先「原本要多少分」，再「值多少钱」。与 C 端详情页同序 -->
            <div v-if="listText || worthText" class="flex items-baseline gap-2 flex-wrap price-was-row">
              <s v-if="listText">{{ listText }}</s>
              <s v-if="worthText">{{ worthText }}</s>
            </div>

            <div v-if="view.gradeLocked || soldOut" class="flex items-center gap-2 mt-2">
              <span v-if="view.gradeLocked" class="tag-lock">{{ lockLabel }}</span>
              <span v-else class="tag-out">已兑完</span>
            </div>

            <div class="commodity-name">{{ view.commodityName || '未命名商品' }}</div>
            <div class="commodity-intro">{{ view.commodityIntro }}</div>

            <div class="flex items-center gap-2 mt-3 text-xs flex-wrap">
              <a-tag v-if="view.limitCount > 0" color="orange">{{ limitText }}</a-tag>
              <a-tag color="blue">{{ typeLabel }}</a-tag>
              <span class="form-tip">{{ soldOut ? '已兑完' : `现货 ${view.availableStock} 件` }}</span>
            </div>

            <a-divider style="margin: 14px 0" />

            <template v-if="specGroups.length">
              <div v-for="spec in specGroups" :key="spec.name" class="mb-3">
                <div class="spec-title">{{ spec.name }}</div>
                <div class="flex flex-wrap gap-2">
                  <span v-for="v in spec.values" :key="v" class="spec-chip">{{ v }}</span>
                </div>
              </div>
              <a-divider style="margin: 14px 0" />
            </template>

            <div class="notice-title">兑换须知</div>
            <div class="notice-body">{{ view.exchangeNotice || '（未填写）' }}</div>
          </div>
        </div>
        <div class="phone-cta" :class="{ 'phone-cta--off': soldOut || view.gradeLocked }">{{ ctaText }}</div>
      </div>
      <a-empty v-else-if="!loading" description="预览渲染不出来，多半是必填项还没填完" />
    </a-spin>
  </a-drawer>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { COMMODITY_TYPE_ENUM, LIMIT_PERIOD_ENUM, PAY_TYPE_ENUM } from '/@/constants/business/mall/mall-commodity-const';
  import { mallCommodityApi } from '/@/api/business/mall/mall-commodity-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const props = defineProps({
    open: { type: Boolean, default: false },
    /** 编辑页当前的表单。与保存发出去的是同一份（见 buildPayload）。抽屉没开时为 null */
    payload: { type: Object, default: null },
    /** 启用中的等级，只用来渲染「预览身份」下拉 */
    grades: { type: Array, default: () => [] },
  });
  defineEmits(['update:open']);

  const gradeCode = ref(0);
  const view = ref(null);
  const loading = ref(false);

  const gradeOptions = computed(() => [
    { value: 0, label: '未登录 / 普通会员' },
    ...props.grades
      .filter((g) => g.status === 1 && g.gradeCode > 0)
      .map((g) => ({ value: g.gradeCode, label: `${g.gradeName}（等级 ${g.gradeCode}）` })),
  ]);

  async function load() {
    if (!props.open || !props.payload) {
      return;
    }
    loading.value = true;
    try {
      view.value = await mallCommodityApi.preview(props.payload, gradeCode.value);
    } catch (e) {
      /*
       * 必填项没填完时服务端按 @Valid 打回来。这不是异常，是「还没到能预览的程度」——
       * 但也没什么可画的，所以清空 view 让 a-empty 出来说一句人话。
       */
      view.value = null;
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  /*
   * 开抽屉、换身份都要重拉。
   * ⚠️ payload 也要盯：运营改完表单不关抽屉直接再点预览，靠的就是它。
   */
  watch(() => [props.open, gradeCode.value, props.payload], load, { immediate: true });

  /* ---------- 以下只管把数字写成人话，不管数字是多少。对齐 app-web/utils/cost.ts ---------- */

  /** `45,000 积分` */
  function formatPoints(points) {
    return `${Number(points || 0).toLocaleString('en-US')} 积分`;
  }

  /** `¥299.00` —— 半角 ¥、千分位、两位小数 */
  function formatCash(cash) {
    return `¥${Number(cash || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  const costText = computed(() => {
    const v = view.value;
    if (!v) {
      return '';
    }
    const base =
      v.payType === PAY_TYPE_ENUM.POINTS_CASH.value
        ? `${formatPoints(v.pointsPrice)} + ${formatCash(v.cashPrice)}`
        : formatPoints(v.pointsPrice);
    // 各规格不同价时加「起」—— 不加的话运营看不出这是最低价
    return v.priceVaries ? `${base} 起` : base;
  });

  /*
   * 🔴 判据是两个价不相等，不是折扣率小于 100。
   * 商品可以单独退出等级折扣，那时这个人的折扣率照样是 88 而这件商品一分没便宜 ——
   * 按折扣率判会在一件没便宜的商品上挂出「8.8折」。同 cost.ts 的 formatDiscount。
   */
  const discountText = computed(() => {
    const v = view.value;
    if (!v || v.listPointsPrice <= v.pointsPrice || v.gradeDiscountPercent >= 100 || v.gradeDiscountPercent <= 0) {
      return '';
    }
    const tenths = v.gradeDiscountPercent / 10;
    return `${Number.isInteger(tenths) ? tenths : tenths.toFixed(1)}折`;
  });

  /** 划掉的挂牌积分价。没享到折扣就为空 */
  const listText = computed(() => {
    const v = view.value;
    return v && v.listPointsPrice > v.pointsPrice ? formatPoints(v.listPointsPrice) : '';
  });

  /** 🔴「价值 ¥1,999.00」—— 它是「值多少钱」，不是「原来要多少积分」 */
  const worthText = computed(() => {
    const v = view.value;
    return v && Number(v.originalPrice) > 0 ? `价值 ${formatCash(v.originalPrice)}` : '';
  });

  const soldOut = computed(() => (view.value ? view.value.availableStock <= 0 : false));

  const lockLabel = computed(() => `${view.value?.minGradeName || '等级'}专享`);

  const ctaText = computed(() => {
    if (view.value?.gradeLocked) {
      return lockLabel.value;
    }
    return soldOut.value ? '已兑完' : '立即兑换';
  });

  const limitText = computed(() => {
    const v = view.value;
    if (!v) {
      return '';
    }
    const head = `${periodLabel.value}限 ${v.limitCount} 件`;
    return v.remainingCount === null || v.remainingCount === undefined
      ? head
      : `${head}，还可兑 ${v.remainingCount} 件`;
  });

  const typeLabel = computed(() => {
    const meta = Object.values(COMMODITY_TYPE_ENUM).find((t) => t.value === view.value?.commodityType);
    return meta ? meta.desc : view.value?.commodityType;
  });

  const periodLabel = computed(() => {
    const meta = Object.values(LIMIT_PERIOD_ENUM).find((p) => p.value === view.value?.limitPeriod);
    return meta ? meta.desc : '';
  });

  /** 规格分组从【服务端给的】SKU 列表推 —— 和 C 端 groupSkuAttributes 吃的是同一份数据 */
  const specGroups = computed(() => {
    const groups = [];
    const indexByName = new Map();
    for (const sku of view.value?.skus || []) {
      for (const [name, value] of Object.entries(sku.skuAttrs || {})) {
        if (!indexByName.has(name)) {
          indexByName.set(name, groups.length);
          groups.push({ name, values: [] });
        }
        const group = groups[indexByName.get(name)];
        if (value && !group.values.includes(value)) {
          group.values.push(value);
        }
      }
    }
    return groups;
  });
</script>

<style scoped lang="less">
  .phone {
    width: 320px;
    margin: 0 auto;
    overflow: hidden;
    background: #fff;
    border: 8px solid #1e293b;
    border-radius: 28px;
  }

  .phone-body {
    height: 520px;
    overflow-y: auto;
  }

  .phone-cover {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 208px;
    background: linear-gradient(135deg, #dbeafe, #e0e7ff);
  }

  .phone-cover-empty {
    color: #94a3b8;
  }

  .phone-content {
    padding: 16px;
  }

  .price-main {
    font-size: 24px;
    font-weight: 700;
    color: #f97316;
  }

  .price-unit {
    font-size: 14px;
    color: #f97316;
  }

  .preview-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
  }

  .preview-bar-label {
    font-size: 12px;
    color: #64748b;
  }

  .phone-cover-img {
    width: 100%;
    height: 208px;
    object-fit: contain;
  }

  .price-was-row {
    margin-top: 2px;
    font-size: 12px;
    color: #94a3b8;
  }

  .tag-discount {
    padding: 1px 6px;
    border-radius: 4px;
    background: #ff4d3a;
    color: #ffffff;
    font-size: 11px;
    font-weight: 600;
    white-space: nowrap;
  }

  .tag-lock,
  .tag-out {
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 11px;
    line-height: 18px;
  }

  .tag-lock {
    background: #fff1f0;
    color: #cf1322;
  }

  .tag-out {
    background: #f1f5f9;
    color: #64748b;
  }



  .commodity-name {
    margin-top: 8px;
    font-weight: 500;
    color: #1e293b;
  }

  .commodity-intro,
  .form-tip {
    font-size: 12px;
    color: #94a3b8;
  }

  .commodity-intro {
    margin-top: 4px;
  }

  .spec-title {
    margin-bottom: 4px;
    font-size: 12px;
    color: #64748b;
  }

  .spec-chip {
    padding: 4px 8px;
    font-size: 12px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 4px;
  }

  .notice-title {
    margin-bottom: 4px;
    font-size: 12px;
    font-weight: 500;
    color: #64748b;
  }

  .notice-body {
    font-size: 12px;
    color: #94a3b8;
    white-space: pre-wrap;
  }

  .phone-cta {
    padding: 12px 0;
    font-size: 14px;
    font-weight: 500;
    color: #fff;
    text-align: center;
    background: #f97316;
  }

  .phone-cta--off {
    background: #cbd5e1;
    color: #ffffff;
  }
</style>
