<!--
  * C端效果预览（原型 docs/mall.html 的抽屉）
  *
  * 运营在后台填的是一堆分散的字段，但用户看到的是一屏。中间隔着想象力 ——
  * 「划线原价填了但纯积分商品不展示」「兑换须知写太长会被截断」这类问题，
  * 不摆出来就只能等上线后被投诉才发现。
  *
  * 展示价取的是**各 SKU 的最低价**，与 C 端列表页「XX积分起」同口径：
  * 主表基准价只是 SKU 留空时的继承值，多规格商品实际卖的是 SKU 价。
  *
  * @Copyright  weolwo
-->
<template>
  <a-drawer :open="open" title="C端效果预览" width="420" placement="right" @update:open="(v) => $emit('update:open', v)">
    <div class="phone">
      <div class="phone-body">
        <div class="phone-cover">
          <FileThumb v-if="form.coverFileId" :file-id="form.coverFileId" :height="208" />
          <span v-else class="phone-cover-empty">未设置主图</span>
        </div>

        <div class="phone-content">
          <div class="flex items-baseline gap-2">
            <span class="price-main">{{ previewPoints }}</span>
            <span class="price-unit">积分</span>
            <span v-if="form.payType === PAY_TYPE_ENUM.POINTS_CASH.value" class="price-unit">+ ￥{{ previewCash }}</span>
            <span v-if="form.originalPrice > 0" class="price-original">￥{{ form.originalPrice }}</span>
          </div>

          <div class="commodity-name">{{ form.commodityName || '未命名商品' }}</div>
          <div class="commodity-intro">{{ form.commodityIntro }}</div>

          <div class="flex items-center gap-2 mt-3 text-xs">
            <a-tag v-if="form.limitCount > 0" color="orange">{{ periodLabel }}限 {{ form.limitCount }} 件</a-tag>
            <a-tag color="blue">{{ typeLabel }}</a-tag>
            <span class="form-tip">已兑 {{ soldTotal }} 件</span>
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
          <div class="notice-body">{{ form.exchangeNotice || '（未填写）' }}</div>
        </div>
      </div>
      <div class="phone-cta">立即兑换</div>
    </div>
  </a-drawer>
</template>

<script setup>
  import { computed } from 'vue';
  import { COMMODITY_TYPE_ENUM, LIMIT_PERIOD_ENUM, PAY_TYPE_ENUM } from '/@/constants/business/mall/mall-commodity-const';
  import FileThumb from '/@/components/support/file-thumb/index.vue';

  const props = defineProps({
    open: { type: Boolean, default: false },
    form: { type: Object, required: true },
    skuList: { type: Array, default: () => [] },
  });
  defineEmits(['update:open']);

  /**
   * 各 SKU 的最低价；SKU 没覆盖价（留空）时用主表基准价参与比较 —— 那正是它继承到的值。
   */
  function minOf(field, base) {
    const values = props.skuList
      .filter((sku) => sku.skuStatus !== 0)
      .map((sku) => (sku[field] === null || sku[field] === undefined ? base : sku[field]))
      .filter((v) => v !== null && v !== undefined);
    return values.length ? Math.min(...values) : base || 0;
  }

  const previewPoints = computed(() => minOf('skuPointsPrice', props.form.pointsPrice || 0));
  const previewCash = computed(() => minOf('skuCashPrice', props.form.cashPrice || 0));

  const soldTotal = computed(() => props.skuList.reduce((sum, sku) => sum + (sku.soldCount || 0), 0));

  const typeLabel = computed(() => {
    const meta = Object.values(COMMODITY_TYPE_ENUM).find((t) => t.value === props.form.commodityType);
    return meta ? meta.desc : props.form.commodityType;
  });

  const periodLabel = computed(() => {
    const meta = Object.values(LIMIT_PERIOD_ENUM).find((p) => p.value === props.form.limitPeriod);
    return meta ? meta.desc : '';
  });

  /** 从 SKU 的 attrs 反推规格选择区，和 C 端渲染的是同一份数据 */
  const specGroups = computed(() => {
    const groups = [];
    const indexByName = new Map();
    for (const sku of props.skuList) {
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

  .price-original {
    margin-left: 4px;
    font-size: 12px;
    color: #94a3b8;
    text-decoration: line-through;
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
</style>
