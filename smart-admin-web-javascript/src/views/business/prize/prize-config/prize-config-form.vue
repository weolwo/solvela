<!--
  * 奖品配置表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 20:20:44
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <!-- 活动编码是随机码，让人手打不现实，改为从活动列表选 -->
      <a-form-item label="归属活动" name="activityCode">
        <a-select
          style="width: 100%"
          v-model:value="form.activityCode"
          :options="activityOptions"
          :loading="activityLoading"
          :disabled="!!form.id"
          show-search
          option-filter-prop="label"
          placeholder="请选择归属活动"
        />
      </a-form-item>
      <!-- 资产类型决定下面能选哪些优惠配置，必须放在优惠配置之前选 -->
      <a-form-item label="资产类型" name="prizeType">
        <a-select
          style="width: 100%"
          v-model:value="form.prizeType"
          :options="PRIZE_TYPE_OPTIONS"
          placeholder="请选择资产类型"
          @change="onPrizeTypeChange"
        />
      </a-form-item>
      <!-- 优惠配置：预算与风控的载体。全量拉回后按 prizeType 本地过滤，避免切一次类型打一次接口 -->
      <a-form-item label="优惠配置" name="promotionConfigId">
        <a-select
          style="width: 100%"
          v-model:value="form.promotionConfigId"
          :options="promotionOptions"
          :loading="promotionLoading"
          :disabled="!form.prizeType"
          show-search
          option-filter-prop="label"
          :placeholder="form.prizeType ? '请选择优惠配置' : '请先选择资产类型'"
        />
        <div v-if="form.prizeType && promotionOptions.length === 0 && !promotionLoading" class="mt-1 text-xs text-orange-500">
          该资产类型下还没有启用中的优惠配置，请先到「优惠配置」页创建，否则发奖时会被判「资产配置异常」
        </div>
      </a-form-item>
      <a-form-item label="奖品名称" name="prizeName">
        <a-input style="width: 100%" v-model:value="form.prizeName" placeholder="奖品名称" />
      </a-form-item>
      <!-- 奖品编码：10位大写字母+数字，可手输也可一键生成；创建后被奖池物资/流水引用，编辑时锁死 -->
      <a-form-item label="奖品编码" name="prizeCode">
        <a-input-group compact>
          <a-input
            style="width: calc(100% - 96px)"
            v-model:value="form.prizeCode"
            :disabled="!!form.id"
            :maxlength="10"
            placeholder="如 H88JHKJFNE，或点右侧生成"
            @change="onCodeInput"
          />
          <a-tooltip :title="form.id ? '奖品编码创建后不可修改' : '随机生成一个未被占用的编码'">
            <a-button style="width: 96px" :disabled="!!form.id" :loading="codeGenerating" @click="generateCode">生成</a-button>
          </a-tooltip>
        </a-input-group>
      </a-form-item>
      <a-form-item label="奖品级别" name="prizeLevel">
        <a-input-number style="width: 100%" v-model:value="form.prizeLevel" placeholder="奖品级别" />
      </a-form-item>
      <a-form-item label="奖励价值" name="prizeValue">
        <a-input-number style="width: 100%" v-model:value="form.prizeValue" placeholder="奖励价值" />
      </a-form-item>
      <a-form-item label="审批模式" name="approveMode">
        <a-input-number style="width: 100%" v-model:value="form.approveMode" placeholder="审批模式：0-自动免审, 1-人工审批" />
      </a-form-item>
      <a-form-item label="排序权重" name="sortWeight">
        <a-input-number style="width: 100%" v-model:value="form.sortWeight" placeholder="排序权重" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-input-number style="width: 100%" v-model:value="form.status" placeholder="状态：0-停用, 1-启用" />
      </a-form-item>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="onClose">取消</a-button>
        <a-button type="primary" @click="onSubmit">保存</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
<script setup>
  import { computed, reactive, ref, nextTick } from 'vue';
  import _ from 'lodash';
  import { message } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { prizeConfigApi } from '/@/api/business/prize/prize-config/prize-config-api';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { promotionConfigApi } from '/@/api/business/risk/promotion-config/promotion-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { regular } from '/@/constants/regular-const';
  import { PRIZE_TYPE_OPTIONS } from '/@/constants/business/prize/prize-config/prize-config-const';

  // ------------------------ 事件 ------------------------

  const emits = defineEmits(['reloadList']);

  // ------------------------ 显示与隐藏 ------------------------
  // 是否显示
  const visibleFlag = ref(false);

  function show(rowData) {
    Object.assign(form, formDefault);
    if (rowData && !_.isEmpty(rowData)) {
      Object.assign(form, rowData);
    }
    loadActivityOptions();
    loadPromotionOptions();
    // 使用字典时把下面这注释修改成自己的字典字段 有多个字典字段就复制多份同理修改 不然打开表单时不显示字典初始值
    // if (form.status && form.status.length > 0) {
    //   form.status = form.status.map((e) => e.valueCode);
    // }
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
    activityCode: undefined, //活动编码
    promotionConfigId: undefined, //优惠配置ID
    prizeType: undefined, //资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM
    prizeName: undefined, //奖品名称
    prizeCode: undefined, //奖品编码
    prizeLevel: undefined, //奖品级别
    prizeValue: undefined, //奖励价值
    approveMode: undefined, //审批模式：0-自动免审, 1-人工审批
    sortWeight: undefined, //排序权重
    ext: undefined, //扩展信息：如奖品图片URL、跳转链接等
    status: undefined, //状态：0-停用, 1-启用
  };

  let form = reactive({ ...formDefault });

  const rules = {
    activityCode: [{ required: true, message: '归属活动 必选' }],
    prizeType: [{ required: true, message: '资产类型 必选' }],
    // 不选优惠配置的话，发奖时 addProposal 第一步就会判「资产配置异常」，必须卡死
    promotionConfigId: [{ required: true, message: '优惠配置 必选' }],
    prizeCode: [
      { required: true, message: '奖品编码 必填' },
      { pattern: regular.bizCode, message: regular.bizCodeDesc },
    ],
    approveMode: [{ required: true, message: '审批模式：0-自动免审, 1-人工审批 必填' }],
  };

  // ------------------------ 优惠配置级联下拉 ------------------------

  // 全量优惠配置（启用中），只拉一次，按 prizeType 本地过滤
  const promotionAllList = ref([]);
  const promotionLoading = ref(false);

  const promotionOptions = computed(() => {
    if (!form.prizeType) {
      return [];
    }
    return promotionAllList.value
      .filter((item) => item.prizeType === form.prizeType)
      .map((item) => ({
        value: item.id,
        // 标题里直接带预算水位与审批档位，运营选的时候就能看出这个池子还发不发得出来
        label: `${item.promoName}｜预算 ${budgetText(item)}｜数量 ${quotaText(item)}｜${reviewText(item.reviewLevel)}`,
      }));
  });

  const UNLIMITED = -1;
  const budgetText = (item) =>
    Number(item.totalAmount) === UNLIMITED ? '不限' : `${Number(item.usedAmount)}/${Number(item.totalAmount)}`;
  const quotaText = (item) => (item.totalQuota === UNLIMITED ? '不限' : `${item.usedQuota}/${item.totalQuota}`);
  const reviewText = (level) => (level === 0 ? '免审' : level === 1 ? '单层审批' : '双层审批');

  async function loadPromotionOptions() {
    promotionLoading.value = true;
    try {
      const res = await promotionConfigApi.optionList();
      promotionAllList.value = res.data || [];
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      promotionLoading.value = false;
    }
  }

  // 换了资产类型，原来选中的优惠配置多半已经不属于新类型了，直接清掉避免带着脏值提交
  function onPrizeTypeChange() {
    const stillValid = promotionAllList.value.some(
      (item) => item.id === form.promotionConfigId && item.prizeType === form.prizeType
    );
    if (!stillValid) {
      form.promotionConfigId = undefined;
    }
  }

  // ------------------------ 归属活动下拉 ------------------------

  const activityOptions = ref([]);
  const activityLoading = ref(false);

  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList();
      activityOptions.value = (res.data || []).map((item) => ({
        value: item.activityCode,
        label: `${item.activityName}（${item.activityCode}）`,
      }));
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      activityLoading.value = false;
    }
  }

  // ------------------------ 奖品编码 ------------------------

  const codeGenerating = ref(false);

  // 手输时统一转大写，省得运营因为小写被规则拦下来
  function onCodeInput() {
    if (form.prizeCode) {
      form.prizeCode = form.prizeCode.toUpperCase();
    }
  }

  async function generateCode() {
    codeGenerating.value = true;
    try {
      const res = await prizeConfigApi.generateCode();
      form.prizeCode = res.data;
      formRef.value.clearValidate('prizeCode');
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      codeGenerating.value = false;
    }
  }

  // 点击确定，验证表单
  async function onSubmit() {
    try {
      await formRef.value.validateFields();
      save();
    } catch (err) {
      message.error('参数验证错误，请仔细填写表单数据!');
    }
  }

  // 新建、编辑API
  async function save() {
    SmartLoading.show();
    try {
      if (form.id) {
        await prizeConfigApi.update(form);
      } else {
        await prizeConfigApi.add(form);
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
