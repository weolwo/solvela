<!--
  * C端效果预览（原型 docs/mall.html 的抽屉）
  *
  * 运营在后台填的是一堆分散的字段，但用户看到的是一屏。中间隔着想象力 ——
  * 「划线原价填了但纯积分商品不展示」「兑换须知写太长会被截断」这类问题，
  * 不摆出来就只能等上线后被投诉才发现。
  *
  * 展示价取的是**最便宜那个在售 SKU 的价**，与 C 端同口径：
  * 主表基准价只是 SKU 留空时的继承值，多规格商品实际卖的是 SKU 价。
  *
  * 【🔴 它是 C 端渲染的第二份实现，会漂】
  * app-web 与 admin-web 是两个应用，没法共享代码。2026-09-23 就吃过一次亏：
  * 这里一直在取最低 SKU 价（对的），而真实 C 端发的是商品基准价（错的，
  * 某件商品因此显示成真实价格的 100 倍）—— 两边对不上，而没有任何机制会发现。
  * 所以下面每一处都标了「对齐 C 端的谁」，改 C 端时请回来看一眼。
  *
  * 【⚠️ 故意不还原的东西，改之前先读这份清单】
  *   1. 等级价（折扣率 / 单品覆盖价）—— 预览是**未登录视角**。
  *      要还原就得在 admin-web 里再写一份算价规则（取整方向、覆盖价优先级、
  *      参与开关…），那正是上面那段说的「第二份实现」，只会多一处会漂的地方。
  *      改成用一条提示说清「这件商品参不参与等级折扣」，不猜具体数字。
  *   2. 规格可选性收窄（C 端选了颜色之后尺码会灰掉）—— 预览的规格是静态展示。
  *   3. 本周期还可兑几件 —— 那要按会员算，预览没有会员。
  *   4. 轮播图、图文详情 —— 抽屉里放不下，也不是这个预览要回答的问题。
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
          <!--
            对齐 C 端 utils/cost.ts 的 formatCost：
            积分带千分位、现金半角 ¥ 带两位小数、各规格不同价时加「起」。
            预览里写成另一种格式的话，运营看不出真实页面上数字会长什么样。
          -->
          <div class="flex items-baseline gap-2 flex-wrap">
            <span class="price-main">{{ costText }}</span>
            <!--
              🔴 划线原价是「值多少钱」，不是「原来要多少积分」。
              C 端走 formatWorth 输出「价值 ¥1,999.00」，<b>不划线</b>。
              这里此前是一个划掉的裸数字 ￥1999 —— 那正是 cost.ts 那条红字
              在防的读法（第一版前端把它当成积分原价）。
            -->
            <span v-if="worthText" class="price-worth">{{ worthText }}</span>
          </div>

          <!-- 对齐 C 端 ProductCard 的两个角标。缺了它们，运营配完专享等级看不出任何变化 -->
          <div v-if="exclusiveTag || soldOut || gradePriceHint" class="flex items-center gap-2 mt-2 flex-wrap">
            <span v-if="exclusiveTag" class="tag-lock">{{ exclusiveTag }}</span>
            <span v-else-if="soldOut" class="tag-out">已兑完</span>
            <span v-if="gradePriceHint" class="tag-grade">{{ gradePriceHint }}</span>
          </div>

          <div class="commodity-name">{{ form.commodityName || '未命名商品' }}</div>
          <div class="commodity-intro">{{ form.commodityIntro }}</div>

          <div class="flex items-center gap-2 mt-3 text-xs">
            <a-tag v-if="form.limitCount > 0" color="orange">{{ periodLabel }}限 {{ form.limitCount }} 件</a-tag>
            <a-tag color="blue">{{ typeLabel }}</a-tag>
            <!--
              ⚠️ 这里显示的是【库存】，不是「已兑 N 件」——
              C 端任何页面都不显示已兑数（只在售罄时盖一个「已兑完」）。
              预览多出一个 C 端没有的元素，会让运营以为用户看得到销量。
            -->
            <span class="form-tip">{{ soldOut ? '已兑完' : `现货 ${availableTotal} 件` }}</span>
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
      <!-- C 端在售罄/等级不够时按钮是不可点的，预览恒为可点会让人以为那两种态不存在 -->
      <div class="phone-cta" :class="{ 'phone-cta--off': soldOut || exclusiveTag !== '' }">
        {{ ctaText }}
      </div>
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
    /** 启用中的等级，用来把 minGrade 翻成「白金会员专享」。拿不到时退回「等级 N」 */
    grades: { type: Array, default: () => [] },
  });
  defineEmits(['update:open']);

  /**
   * 在售规格。
   *
   * ⚠️ 判据是 `=== 1`（ENABLED），不是 `!== 0` ——
   * 服务端查的是 `sku_status = 1`，两边口径要一样。
   * 编辑器新建的行一定带 skuStatus=1（见 sku-editor 的 buildRows），所以不会误伤。
   */
  const onSaleSkus = computed(() => props.skuList.filter((sku) => sku.skuStatus === 1));

  /** SKU 留空就继承主表基准价 —— 对齐 MallPricing.listPoints */
  function inherit(value, base) {
    return value === null || value === undefined ? base || 0 : value;
  }

  /**
   * 最便宜那个在售规格。<b>对齐 MallPricing.cheapest。</b>
   *
   * 🔴 按 (积分, 现金) <b>依次比</b>，不是各取各的最小值。
   * 各取最小会拼出一个【没有任何规格真的这么卖】的组合：
   * A 规格 8800 分 + ¥5000、B 规格 9000 分 + ¥100 → 拼成 8800 + ¥100。
   * 这里此前就是各取各的。依次比总能指向一个真实存在的规格。
   */
  const cheapest = computed(() => {
    const basePoints = props.form.pointsPrice || 0;
    const baseCash = Number(props.form.cashPrice) || 0;
    const skus = onSaleSkus.value;
    if (!skus.length) {
      // 一个在售规格都没有：退回主表基准价（同 MallPricing.cheapest 的空列表分支）
      return { points: basePoints, cash: baseCash, varies: false };
    }
    let best = null;
    let varies = false;
    for (const sku of skus) {
      const points = inherit(sku.skuPointsPrice, basePoints);
      const cash = Number(inherit(sku.skuCashPrice, baseCash)) || 0;
      if (best === null) {
        best = { points, cash };
        continue;
      }
      if (points !== best.points || cash !== best.cash) {
        varies = true;
      }
      if (points < best.points || (points === best.points && cash < best.cash)) {
        best = { points, cash };
      }
    }
    return { ...best, varies };
  });

  /* ---------- 展示格式：对齐 C 端 utils/cost.ts ---------- */

  /** `45,000 积分` */
  function formatPoints(points) {
    return `${Number(points || 0).toLocaleString('en-US')} 积分`;
  }

  /** `¥299.00` —— 半角 ¥、千分位、两位小数 */
  function formatCash(cash) {
    return `¥${Number(cash || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  const costText = computed(() => {
    const { points, cash, varies } = cheapest.value;
    const base =
      props.form.payType === PAY_TYPE_ENUM.POINTS_CASH.value
        ? `${formatPoints(points)} + ${formatCash(cash)}`
        : formatPoints(points);
    // 各规格不同价时 C 端加「起」—— 不加的话运营看不出这是最低价
    return varies ? `${base} 起` : base;
  });

  /** 🔴「价值 ¥1,999.00」，不划线 —— 它是「值多少钱」，不是「原来要多少积分」 */
  const worthText = computed(() =>
    Number(props.form.originalPrice) > 0 ? `价值 ${formatCash(props.form.originalPrice)}` : '',
  );

  /* ---------- 三个 C 端有、这里此前没有的态 ---------- */

  const availableTotal = computed(() =>
    onSaleSkus.value.reduce(
      (sum, sku) => sum + Math.max(0, (sku.totalStock || 0) - (sku.lockedStock || 0) - (sku.soldCount || 0)),
      0,
    ),
  );

  const soldOut = computed(() => availableTotal.value <= 0);

  /**
   * 专享等级标。对齐 C 端 ProductCard.exclusiveTag。
   *
   * ⚠️ C 端是拿「这个会员够不够格」算的（服务端给的 gradeLocked），
   * 而预览没有会员 —— 这里按<b>未登录</b>算，也就是只要设了门槛就显示锁。
   * 那正是一个没登录的用户看到的样子。
   */
  const exclusiveTag = computed(() => {
    const min = props.form.minGrade || 0;
    if (min <= 0) {
      return '';
    }
    const hit = props.grades.find((g) => g.gradeCode === min);
    return `${hit ? hit.gradeName : `等级 ${min}`}专享`;
  });

  /**
   * 等级价只给一句话，<b>不算具体数字</b>。
   *
   * 🔴 要算就得在 admin-web 里再写一份算价规则（取整方向、覆盖价优先级、
   * 参与开关…），而这个组件的头部注释刚说过「第二份实现会漂」——
   * 再加一份只会多一处会漂的地方。预览是未登录视角，这句话补上缺的那半信息。
   */
  const gradePriceHint = computed(() => {
    if (props.form.gradePriceFlag === 0) {
      return '不参与等级折扣';
    }
    return '高等级会员看到的价更低';
  });

  const ctaText = computed(() => {
    if (exclusiveTag.value) {
      return exclusiveTag.value;
    }
    return soldOut.value ? '已兑完' : '立即兑换';
  });

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

  /* 🔴 不划线：它是「值多少钱」，不是「原来要多少积分」。划一道就成了原价促销的读法 */
  .price-worth {
    margin-left: 4px;
    font-size: 12px;
    color: #94a3b8;
  }

  .tag-lock,
  .tag-out,
  .tag-grade {
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

  .tag-grade {
    background: #fff7e6;
    color: #d46b08;
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
