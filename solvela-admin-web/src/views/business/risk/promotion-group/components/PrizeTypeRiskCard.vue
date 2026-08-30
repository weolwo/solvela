<!--
  * 一种资产类型的风控卡片 —— 工作台里的一格
  *
  * 字段与单条优惠配置表单是同一套，只是从抽屉搬进了卡片。
  * 刻意不复用 promotion-config-form.vue：那个抽屉自带查询、保存、校验的整套生命周期，
  * 在工作台里这些都由父组件统一做，硬复用要拆掉的比重写的还多。
  *
  * @Author:    alaric
  * @Date:      2026-08-30
-->
<template>
  <a-card size="small" :class="['h-full', 'risk-card', item.configured ? '' : 'opacity-60']">
    <template #title>
      <span class="mr-2">{{ typeDesc }}</span>
      <a-tag color="default" class="text-xs">{{ item.prizeType }}</a-tag>
    </template>

    <template #extra>
      <!--
        没配过的类型给一个「配置」按钮而不是直接摊开表单：
        四种类型全摊开是四屏，而一个活动通常只用其中一两种。
      -->
      <a-button v-if="!item.configured" size="small" type="link" @click="$emit('configure')">配置</a-button>
      <a-tooltip v-else :title="groupDisabled ? '分组已停用，组内所有类型一律停发' : ''">
        <a-switch
          size="small"
          :checked="item.status === ENABLED"
          :disabled="groupDisabled"
          checked-children="发"
          un-checked-children="停"
          @change="(checked) => $emit('toggle', checked)"
        />
      </a-tooltip>
    </template>

    <div v-if="!item.configured" class="py-4 text-center text-slate-400 text-xs">未纳入本分组</div>

    <a-form v-else layout="vertical" size="small">
      <!-- 预算与库存：券/实物论个数，积分/现金论金额，二选一 -->
      <a-form-item :label="isQuotaBased ? '总库存' : '总预算'" class="mb-1">
        <a-input-number
          v-if="isQuotaBased"
          v-model:value="item.totalQuota"
          class="w-full"
          size="small"
          :min="-1"
          :precision="0"
          placeholder="-1 不限"
        />
        <a-input-number v-else v-model:value="item.totalAmount" class="w-full" size="small" :min="-1" placeholder="-1 不限" />
        <!-- 水位只读：由发放链路的原子 SQL 维护，工作台只展示不回传 -->
        <div class="hint">已消耗 {{ isQuotaBased ? item.usedQuota ?? 0 : item.usedAmount ?? 0 }}（自动累加）</div>
      </a-form-item>

      <a-form-item label="审核层级" class="mb-1">
        <a-select v-model:value="item.reviewLevel" size="small" :options="REVIEW_LEVEL_OPTIONS" placeholder="请选择" />
      </a-form-item>

      <!-- 阈值跟着层级置灰。父组件的校验规则也跟着走，两者只做一半就是「填不了却提示必填」 -->
      <a-form-item label="一审阈值" class="mb-1">
        <a-input-number
          v-model:value="item.firstReviewThreshold"
          class="w-full"
          size="small"
          :min="0"
          :disabled="item.reviewLevel === REVIEW_LEVEL_ENUM.NONE.value"
          placeholder="0 笔笔一审"
        />
      </a-form-item>

      <a-form-item class="mb-1">
        <template #label>
          二审阈值
          <a-tooltip title="提案状态机目前只按一审阈值分流，这个值填了不会改变任何行为">
            <span class="text-slate-400 text-xs">(未生效)</span>
          </a-tooltip>
        </template>
        <a-input-number
          v-model:value="item.secondReviewThreshold"
          class="w-full"
          size="small"
          :min="0"
          :disabled="item.reviewLevel !== REVIEW_LEVEL_ENUM.DOUBLE.value"
          placeholder="仅双层审批"
        />
      </a-form-item>

      <!--
        单次兜底：实物只给数量。
        实物的「金额」只是一个参考价值（iPhone 记 7999 是为了让风控预算有个口径），
        拿它做单次上限没有意义 —— 真正要卡的是「一次别发出去 999 件」。
        所以这里对实物隐藏金额兜底，提交时固定送 0（不设兜底）。
      -->
      <a-form-item v-if="!isPhysical" label="单次最大金额" class="mb-1">
        <a-input-number v-model:value="item.singleMaxAmount" class="w-full" size="small" :min="0" placeholder="0 不设兜底" />
      </a-form-item>

      <a-form-item label="单次最大数量" class="mb-1">
        <a-input-number v-model:value="item.singleMaxQuota" class="w-full" size="small" :min="0" :precision="0" placeholder="0 不设兜底" />
        <div v-if="isPhysical" class="hint">实物只卡数量 —— 金额只是参考价值，卡它没有意义</div>
      </a-form-item>

      <a-form-item label="限制周期" class="mb-1">
        <a-select v-model:value="item.limitPeriod" size="small" :options="LIMIT_PERIOD_OPTIONS" placeholder="请选择" />
      </a-form-item>

      <!-- 自定义窗口：起止时间自己写，刻意不跟活动周期联动（优惠配置本就不绑活动） -->
      <a-form-item v-if="isCustomPeriod" label="限制窗口" class="mb-1">
        <a-range-picker
          v-model:value="limitRange"
          class="w-full"
          size="small"
          :show-time="RANGE_SHOW_TIME"
          :placeholder="['开始', '结束']"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="MM-DD HH:mm"
        />
      </a-form-item>

      <a-form-item label="单会员限领" class="mb-1">
        <a-input-number v-model:value="item.identifyLimit" class="w-full" size="small" :min="-1" :precision="0" placeholder="-1 不限" />
      </a-form-item>

      <!--
        其余四个防刷维度默认折叠。
        它们在 t_promotion_config 上都有列、表单也一直能填，但风控链路里
        FrequencyRiskFilter 只读 identifyLimit 一个 —— 这四个没有任何消费方。
        既不能装作没有（库里有列、单条表单里也在），也不该跟生效的那个平起平坐，
        所以收进折叠面板，标题里写明未生效。
      -->
      <a-collapse ghost size="small" class="extra-limits">
        <a-collapse-panel key="more">
          <template #header>
            <span class="text-xs text-slate-500">其他防刷维度（4 项，暂未生效）</span>
          </template>
          <a-form-item v-for="f in INEFFECTIVE_LIMIT_FIELDS" :key="f.field" :label="f.label" class="mb-1">
            <a-input-number v-model:value="item[f.field]" class="w-full" size="small" :min="-1" :precision="0" placeholder="-1 不限" />
          </a-form-item>
          <div class="hint">风控链路目前只消费「单会员限领」，这四项填了不会生效。</div>
        </a-collapse-panel>
      </a-collapse>
    </a-form>
  </a-card>
</template>
<script setup>
  import { computed } from 'vue';
  import { RANGE_SHOW_TIME } from '/@/constants/date-time-const';
  import {
    LIMIT_PERIOD_ENUM,
    LIMIT_PERIOD_OPTIONS,
    QUOTA_BASED_PRIZE_TYPES,
    REVIEW_LEVEL_ENUM,
    REVIEW_LEVEL_OPTIONS,
  } from '/src/constants/business/risk/promotion-config-const';
  import { PRIZE_TYPE_ENUM, prizeTypeOf } from '/src/constants/business/prize/prize-config-const';

  const props = defineProps({
    // 直接改父组件传下来的 reactive 对象：工作台是一次性整体保存，
    // 每个字段都走一遍 emit 只会把父组件塞满转发函数
    item: { type: Object, required: true },
    // 分组停用时，单个类型的开关必须锁死 —— 服务端也会强制把子项落成停用，
    // 页面上还能拨动就是在骗人
    groupDisabled: { type: Boolean, default: false },
  });

  defineEmits(['configure', 'toggle']);

  const ENABLED = 1;

  const INEFFECTIVE_LIMIT_FIELDS = [
    { field: 'phoneLimit', label: '单手机号' },
    { field: 'ipLimit', label: '单IP' },
    { field: 'deviceLimit', label: '单设备号' },
    { field: 'fingerprintLimit', label: '单端指纹' },
  ];

  const typeDesc = computed(() => prizeTypeOf(props.item.prizeType));

  const isQuotaBased = computed(() => QUOTA_BASED_PRIZE_TYPES.includes(props.item.prizeType));

  const isPhysical = computed(() => props.item.prizeType === PRIZE_TYPE_ENUM.PHYSICAL.value);

  const isCustomPeriod = computed(() => props.item.limitPeriod === LIMIT_PERIOD_ENUM.CUSTOM.value);

  // range-picker 要一个 [start, end] 数组，接口是两个平铺字段，用可写 computed 桥接
  const limitRange = computed({
    get: () => (props.item.limitStartTime && props.item.limitEndTime ? [props.item.limitStartTime, props.item.limitEndTime] : []),
    set: (val) => {
      props.item.limitStartTime = val?.[0];
      props.item.limitEndTime = val?.[1];
    },
  });
</script>
<style scoped>
  /* 卡片里是四列并排的窄栏，label 竖排更省横向空间，行距也压紧一档 */
  .risk-card :deep(.ant-form-item) {
    margin-bottom: 6px;
  }

  .risk-card :deep(.ant-form-item-label) {
    padding-bottom: 0;
    line-height: 1.4;
  }

  .risk-card :deep(.ant-form-item-label > label) {
    height: auto;
    font-size: 12px;
    color: rgba(0, 0, 0, 0.55);
  }

  .hint {
    margin-top: 2px;
    font-size: 11px;
    line-height: 1.4;
    color: #94a3b8;
  }

  .extra-limits :deep(.ant-collapse-header) {
    padding: 4px 0 !important;
  }

  .extra-limits :deep(.ant-collapse-content-box) {
    padding: 4px 0 0 !important;
  }
</style>
