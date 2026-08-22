<!--
  * 优惠配置表 —— 编辑抽屉
  *
  * 23 个字段平铺在一个 modal 里没人读得完，按 DDL 里本来就分好的 5 组拆开：
  * 基本信息 / 预算与库存 / 审核层级 / 单次兜底 / 风控防刷。
  * 后三组都有合理默认值，绝大多数场景不用碰，默认折叠。
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:28:25
  * @Copyright  weolwo
-->
<template>
  <a-drawer
    :title="form.id ? '编辑优惠配置' : '新建优惠配置'"
    :width="720"
    :open="visibleFlag"
    :maskClosable="false"
    :destroyOnClose="true"
    @close="onClose"
  >
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 7 }" :wrapper-col="{ span: 15 }">
      <a-collapse v-model:activeKey="activeGroups" :bordered="false">
        <!-- ① 基本信息 -->
        <a-collapse-panel key="base" header="基本信息">
          <a-form-item label="优惠配置名称" name="promoName">
            <a-input v-model:value="form.promoName" placeholder="如：618-积分池" />
          </a-form-item>
          <a-form-item label="资产类型" name="prizeType">
            <a-select v-model:value="form.prizeType" :options="PRIZE_TYPE_OPTIONS" placeholder="请选择资产类型" @change="onPrizeTypeChange" />
          </a-form-item>
          <a-form-item label="状态" name="status">
            <a-select v-model:value="form.status" :options="PROMOTION_STATUS_OPTIONS" placeholder="请选择状态" allowClear />
          </a-form-item>
        </a-collapse-panel>

        <!-- ② 预算与库存：按资产类型二选一 -->
        <a-collapse-panel key="budget" :header="budgetHeader">
          <a-alert v-if="!form.prizeType" message="请先在「基本信息」里选择资产类型" type="info" show-icon class="mb-4" />
          <template v-else>
            <a-alert :message="budgetHint" type="info" show-icon class="mb-4" />

            <!-- 券/实物：论个数，看库存 -->
            <template v-if="isQuotaBased">
              <a-form-item label="总库存(个数)" name="totalQuota">
                <a-input-number v-model:value="form.totalQuota" style="width: 100%" :min="-1" :precision="0" placeholder="-1 为不限制" />
              </a-form-item>
              <a-form-item label="已消耗库存">
                <!-- 只读：由发放链路的原子 SQL 维护，后端表单也已去掉该字段 -->
                <span class="text-slate-500">{{ form.usedQuota ?? 0 }}</span>
                <a-tooltip title="由发放链路自动累加，不可手工修改">
                  <QuestionCircleOutlined class="ml-1 text-slate-400" />
                </a-tooltip>
              </a-form-item>
            </template>

            <!-- 积分/现金：论金额，看预算 -->
            <template v-else>
              <a-form-item label="总预算(金额)" name="totalAmount">
                <a-input-number v-model:value="form.totalAmount" style="width: 100%" :min="-1" placeholder="-1 为不限制" />
              </a-form-item>
              <a-form-item label="已消耗预算">
                <span class="text-slate-500">{{ form.usedAmount ?? 0 }}</span>
                <a-tooltip title="由发放链路自动累加，不可手工修改">
                  <QuestionCircleOutlined class="ml-1 text-slate-400" />
                </a-tooltip>
              </a-form-item>
            </template>
          </template>
        </a-collapse-panel>

        <!-- ③ 审核层级 -->
        <a-collapse-panel key="review" header="审核层级">
          <a-form-item label="审核层级控制" name="reviewLevel">
            <a-select v-model:value="form.reviewLevel" :options="REVIEW_LEVEL_OPTIONS" placeholder="请选择审核层级" />
          </a-form-item>
          <!-- 无需审核时两个阈值不起作用，禁掉免得配出「选了无需审核却填了阈值」这种自相矛盾的配置 -->
          <a-form-item label="一审触发阈值" name="firstReviewThreshold">
            <a-input-number
              v-model:value="form.firstReviewThreshold"
              style="width: 100%"
              :min="0"
              :disabled="form.reviewLevel === REVIEW_LEVEL_ENUM.NONE.value"
              placeholder="动账金额 ≥ 此值必须一审，0 表示笔笔一审"
            />
          </a-form-item>
          <a-form-item label="二审触发阈值" name="secondReviewThreshold">
            <a-input-number
              v-model:value="form.secondReviewThreshold"
              style="width: 100%"
              :min="0"
              :disabled="form.reviewLevel !== REVIEW_LEVEL_ENUM.DOUBLE.value"
              :placeholder="form.reviewLevel === REVIEW_LEVEL_ENUM.DOUBLE.value ? '动账金额 ≥ 此值必须二审' : '仅双层审批时生效'"
            />
          </a-form-item>
        </a-collapse-panel>

        <!-- ④ 单次发放兜底 -->
        <a-collapse-panel key="single" header="单次发放兜底">
          <a-alert message="单笔超出即阻断，防的是「一次发出天量」这类配置事故。" type="info" show-icon class="mb-4" />
          <a-form-item label="单次最大数量" name="singleMaxQuota">
            <a-input-number v-model:value="form.singleMaxQuota" style="width: 100%" :min="0" :precision="0" placeholder="单次最大数量" />
          </a-form-item>
          <a-form-item label="单次最大金额" name="singleMaxAmount">
            <a-input-number v-model:value="form.singleMaxAmount" style="width: 100%" :min="0" placeholder="单次最大金额" />
          </a-form-item>
        </a-collapse-panel>

        <!-- ⑤ 风控与防刷 -->
        <a-collapse-panel key="risk" header="风控与防刷">
          <a-form-item label="限制周期" name="limitPeriod">
            <a-select v-model:value="form.limitPeriod" :options="LIMIT_PERIOD_OPTIONS" placeholder="请选择限制周期" />
          </a-form-item>
          <a-alert message="下面五项都是「同一个限制周期内」的领取次数上限，-1 为不限。周期一换，计数重新开始。" type="info" show-icon class="mb-4" />
          <a-form-item v-for="item in LIMIT_FIELDS" :key="item.field" :label="item.label" :name="item.field">
            <a-input-number v-model:value="form[item.field]" style="width: 100%" :min="-1" :precision="0" placeholder="-1 为不限" />
          </a-form-item>
          <a-form-item label="互斥规则" name="mutexRule">
            <a-textarea v-model:value="form.mutexRule" :rows="2" placeholder='互斥的优惠配置ID数组，如 [12, 15]' />
          </a-form-item>
        </a-collapse-panel>
      </a-collapse>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="onClose">取消</a-button>
        <a-button type="primary" @click="onSubmit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup>
  import { computed, reactive, ref, nextTick } from 'vue';
  import _ from 'lodash';
  import { message } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { promotionConfigApi } from '/src/api/business/risk/promotion-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import {
    LIMIT_PERIOD_OPTIONS,
    PRIZE_TYPE_OPTIONS,
    PROMOTION_STATUS_OPTIONS,
    QUOTA_BASED_PRIZE_TYPES,
    REVIEW_LEVEL_ENUM,
    REVIEW_LEVEL_OPTIONS,
    UNLIMITED,
  } from '/src/constants/business/risk/promotion-config-const';

  // ------------------------ 事件 ------------------------

  const emits = defineEmits(['reloadList']);

  // ------------------------ 分组 ------------------------

  // 后三组都有合理默认值，绝大多数场景不用碰 —— 默认只展开前两组
  const DEFAULT_GROUPS = ['base', 'budget'];
  const activeGroups = ref([...DEFAULT_GROUPS]);

  const LIMIT_FIELDS = [
    { field: 'identifyLimit', label: '单会员ID限制' },
    { field: 'phoneLimit', label: '单手机号限制' },
    { field: 'ipLimit', label: '单IP限制' },
    { field: 'deviceLimit', label: '单设备号限制' },
    { field: 'fingerprintLimit', label: '单端指纹限制' },
  ];

  /**
   * 库存(quota) 与 预算(amount) 是二选一的：券/实物论个数看库存，积分/现金论金额看预算。
   * 四个字段一起平铺时，运营根本不知道该填哪两个、另两个为什么要填 -1。
   */
  const isQuotaBased = computed(() => QUOTA_BASED_PRIZE_TYPES.includes(form.prizeType));

  const budgetHeader = computed(() => {
    if (!form.prizeType) {
      return '预算与库存';
    }
    return isQuotaBased.value ? '库存控制（按个数）' : '预算控制（按金额）';
  });

  const budgetHint = computed(() =>
    isQuotaBased.value
      ? '券 / 实物按个数控量，只需填总库存；金额侧不适用，保存时自动置 -1（不限制）。'
      : '积分 / 现金按金额控量，只需填总预算；数量侧不适用，保存时自动置 -1（不限制）。'
  );

  // 切换资产类型时把不适用的那一侧清空，避免留下上一次填的脏值
  function onPrizeTypeChange() {
    if (isQuotaBased.value) {
      form.totalAmount = undefined;
    } else {
      form.totalQuota = undefined;
    }
  }

  // ------------------------ 显示与隐藏 ------------------------
  // 是否显示
  const visibleFlag = ref(false);

  function show(rowData) {
    Object.assign(form, formDefault);
    if (rowData && !_.isEmpty(rowData)) {
      Object.assign(form, rowData);
    }
    activeGroups.value = [...DEFAULT_GROUPS];
    visibleFlag.value = true;
    nextTick(() => {
      formRef.value.clearValidate();
    });
  }

  function onClose() {
    Object.assign(form, formDefault);
    visibleFlag.value = false;
  }

  // ------------------------ 表单 ------------------------

  // 组件ref
  const formRef = ref();

  const formDefault = {
    id: undefined, //配置ID
    promoName: undefined, //优惠配置名称
    prizeType: undefined, //资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物)
    totalQuota: undefined, //总库存(个数)：-1为不限制(适用于券/实物)
    usedQuota: undefined, //已消耗库存(个数)：只读，由发放链路维护
    totalAmount: undefined, //总预算(金额)：-1为不限制(适用于积分/现金)
    usedAmount: undefined, //已消耗预算(金额)：只读，由发放链路维护
    reviewLevel: undefined, //审核层级控制：0-无需审核, 1-单层审批, 2-双层审批
    firstReviewThreshold: undefined, //一审触发阈值
    secondReviewThreshold: undefined, //二审触发阈值
    singleMaxQuota: undefined, //单次最大数量兜底，超限阻断
    singleMaxAmount: undefined, //单次最大金额兜底，超限阻断
    limitPeriod: undefined, //限制周期：LIFETIME / DAILY / WEEKLY / MONTHLY / CUSTOM
    identifyLimit: undefined, //同周期内，单会员ID最多领取次数 (-1为不限)
    phoneLimit: undefined, //同周期内，单手机号最多领取次数 (-1为不限)
    ipLimit: undefined, //同周期内，单IP地址最多领取次数 (-1为不限)
    deviceLimit: undefined, //同周期内，单设备硬件号最多领取次数 (-1为不限)
    fingerprintLimit: undefined, //同周期内，单客户端指纹最多领取次数 (-1为不限)
    mutexRule: undefined, //互斥规则：存互斥的优惠配置ID数组
    status: undefined, //状态：0-停用, 1-启用
  };

  let form = reactive({ ...formDefault });

  // 库存/预算按资产类型二选一必填 —— 不适用的那一侧提交时补 -1，规则也不该拦它
  const rules = {
    promoName: [{ required: true, message: '优惠配置名称 必填' }],
    prizeType: [{ required: true, message: '资产类型 必填' }],
    totalQuota: [{ required: true, message: '总库存 必填' }],
    totalAmount: [{ required: true, message: '总预算 必填' }],
    reviewLevel: [{ required: true, message: '审核层级控制 必填' }],
    firstReviewThreshold: [{ required: true, message: '一审触发阈值 必填' }],
    secondReviewThreshold: [{ required: true, message: '二审触发阈值 必填' }],
    singleMaxQuota: [{ required: true, message: '单次最大数量 必填' }],
    singleMaxAmount: [{ required: true, message: '单次最大金额 必填' }],
    limitPeriod: [{ required: true, message: '限制周期 必填' }],
  };

  // 校验失败时把出问题的分组展开 —— 折叠面板里的红字用户看不见，
  // 否则表现为「点保存没反应」
  function expandGroupsWithError(errorFields) {
    const GROUP_OF_FIELD = {
      promoName: 'base',
      prizeType: 'base',
      status: 'base',
      totalQuota: 'budget',
      totalAmount: 'budget',
      reviewLevel: 'review',
      firstReviewThreshold: 'review',
      secondReviewThreshold: 'review',
      singleMaxQuota: 'single',
      singleMaxAmount: 'single',
      limitPeriod: 'risk',
    };
    const groups = new Set(activeGroups.value);
    (errorFields || []).forEach((e) => {
      const g = GROUP_OF_FIELD[e.name?.[0]];
      if (g) {
        groups.add(g);
      }
    });
    activeGroups.value = [...groups];
  }

  // 点击确定，验证表单
  async function onSubmit() {
    try {
      await formRef.value.validateFields();
      save();
    } catch (err) {
      expandGroupsWithError(err?.errorFields);
      message.error('参数验证错误，请仔细填写表单数据!');
    }
  }

  // 新建、编辑API
  async function save() {
    SmartLoading.show();
    try {
      // 不适用的那一侧补 -1（不限制）：后端两个字段都是 @NotNull，不能留空
      const params = {
        ...form,
        totalQuota: isQuotaBased.value ? form.totalQuota : UNLIMITED,
        totalAmount: isQuotaBased.value ? UNLIMITED : form.totalAmount,
      };
      // usedQuota / usedAmount 是运行态计数器，后端表单已去掉，这里也不往上传
      delete params.usedQuota;
      delete params.usedAmount;

      if (form.id) {
        await promotionConfigApi.update(params);
      } else {
        await promotionConfigApi.add(params);
      }
      message.success('操作成功');
      emits('reloadList');
      onClose();
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      SmartLoading.hide();
    }
  }

  defineExpose({
    show,
  });
</script>
