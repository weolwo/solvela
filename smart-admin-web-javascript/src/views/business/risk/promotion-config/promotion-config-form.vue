<!--
  * 优惠配置表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:28:25
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="配置ID" name="id">
        <a-input-number style="width: 100%" v-model:value="form.id" placeholder="配置ID" />
      </a-form-item>
      <a-form-item label="优惠配置名称" name="promoName">
        <a-input style="width: 100%" v-model:value="form.promoName" placeholder="优惠配置名称" />
      </a-form-item>
      <a-form-item label="资产类型" name="prizeType">
        <a-input
          style="width: 100%"
          v-model:value="form.prizeType"
          placeholder="资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物)"
        />
      </a-form-item>
      <a-form-item label="总库存" name="totalQuota">
        <a-input-number style="width: 100%" v-model:value="form.totalQuota" placeholder="总库存(个数)：-1为不限制(适用于券/实物)" />
      </a-form-item>
      <a-form-item label="已消耗库存" name="usedQuota">
        <a-input-number style="width: 100%" v-model:value="form.usedQuota" placeholder="已消耗库存(个数)" />
      </a-form-item>
      <a-form-item label="总预算" name="totalAmount">
        <a-input-number style="width: 100%" v-model:value="form.totalAmount" placeholder="总预算(金额)：-1为不限制(适用于积分/现金)" />
      </a-form-item>
      <a-form-item label="已消耗预算" name="usedAmount">
        <a-input-number style="width: 100%" v-model:value="form.usedAmount" placeholder="已消耗预算(金额)" />
      </a-form-item>
      <a-form-item label="审核层级控制" name="reviewLevel">
        <a-input-number style="width: 100%" v-model:value="form.reviewLevel" placeholder="审核层级控制：0-无需审核, 1-单层审批, 2-双层审批" />
      </a-form-item>
      <a-form-item label="一审触发阈值" name="firstReviewThreshold">
        <a-input-number
          style="width: 100%"
          v-model:value="form.firstReviewThreshold"
          placeholder="一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审)"
        />
      </a-form-item>
      <a-form-item label="二审触发阈值" name="secondReviewThreshold">
        <a-input-number
          style="width: 100%"
          v-model:value="form.secondReviewThreshold"
          placeholder="二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2)"
        />
      </a-form-item>
      <a-form-item label="单次最大数量" name="singleMaxQuota">
        <a-input-number style="width: 100%" v-model:value="form.singleMaxQuota" placeholder="单次最大数量兜底，超限阻断" />
      </a-form-item>
      <a-form-item label="单次最大金额" name="singleMaxAmount">
        <a-input-number style="width: 100%" v-model:value="form.singleMaxAmount" placeholder="单次最大金额兜底，超限阻断" />
      </a-form-item>
      <a-form-item label="限制周期" name="limitPeriod">
        <a-input
          style="width: 100%"
          v-model:value="form.limitPeriod"
          placeholder="限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM"
        />
      </a-form-item>
      <a-form-item label="ID限制" name="identifyLimit">
        <a-input-number style="width: 100%" v-model:value="form.identifyLimit" placeholder="同周期内，单会员ID最多领取次数 (-1为不限)" />
      </a-form-item>
      <a-form-item label="单手机号限制" name="phoneLimit">
        <a-input-number style="width: 100%" v-model:value="form.phoneLimit" placeholder="同周期内，单手机号最多领取次数 (-1为不限)" />
      </a-form-item>
      <a-form-item label="IP限制" name="ipLimit">
        <a-input-number style="width: 100%" v-model:value="form.ipLimit" placeholder="同周期内，单IP地址最多领取次数 (-1为不限)" />
      </a-form-item>
      <a-form-item label="设备Id限制" name="deviceLimit">
        <a-input-number style="width: 100%" v-model:value="form.deviceLimit" placeholder="同周期内，单设备硬件号(IMEI/IDFA)最多领取次数 (-1为不限)" />
      </a-form-item>
      <a-form-item label="指纹限制" name="fingerprintLimit">
        <a-input-number style="width: 100%" v-model:value="form.fingerprintLimit" placeholder="同周期内，单客户端指纹最多领取次数 (-1为不限)" />
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
  import { reactive, ref, nextTick } from 'vue';
  import _ from 'lodash';
  import { message } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { promotionConfigApi } from '/@/api/business/risk/promotion-config/promotion-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';

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
    id: undefined, //配置ID
    promoName: undefined, //优惠配置名称
    prizeType: undefined, //资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物)
    totalQuota: undefined, //总库存(个数)：-1为不限制(适用于券/实物)
    usedQuota: undefined, //已消耗库存(个数)
    totalAmount: undefined, //总预算(金额)：-1为不限制(适用于积分/现金)
    usedAmount: undefined, //已消耗预算(金额)
    reviewLevel: undefined, //审核层级控制：0-无需审核, 1-单层审批, 2-双层审批
    firstReviewThreshold: undefined, //一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审)
    secondReviewThreshold: undefined, //二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2)
    singleMaxQuota: undefined, //单次最大数量兜底，超限阻断
    singleMaxAmount: undefined, //单次最大金额兜底，超限阻断
    limitPeriod: undefined, //限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM
    identifyLimit: undefined, //同周期内，单会员ID最多领取次数 (-1为不限)
    phoneLimit: undefined, //同周期内，单手机号最多领取次数 (-1为不限)
    ipLimit: undefined, //同周期内，单IP地址最多领取次数 (-1为不限)
    deviceLimit: undefined, //同周期内，单设备硬件号(IMEI/IDFA)最多领取次数 (-1为不限)
    fingerprintLimit: undefined, //同周期内，单客户端指纹最多领取次数 (-1为不限)
    mutexRule: undefined, //互斥规则：存互斥的优惠配置ID数组
    status: undefined, //状态：0-停用, 1-启用
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: '配置ID 必填' }],
    promoName: [{ required: true, message: '优惠配置名称 必填' }],
    prizeType: [{ required: true, message: '资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物) 必填' }],
    totalQuota: [{ required: true, message: '总库存(个数)：-1为不限制(适用于券/实物) 必填' }],
    usedQuota: [{ required: true, message: '已消耗库存(个数) 必填' }],
    totalAmount: [{ required: true, message: '总预算(金额)：-1为不限制(适用于积分/现金) 必填' }],
    usedAmount: [{ required: true, message: '已消耗预算(金额) 必填' }],
    reviewLevel: [{ required: true, message: '审核层级控制：0-无需审核, 1-单层审批, 2-双层审批 必填' }],
    firstReviewThreshold: [{ required: true, message: '一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审) 必填' }],
    secondReviewThreshold: [{ required: true, message: '二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2) 必填' }],
    singleMaxQuota: [{ required: true, message: '单次最大数量兜底，超限阻断 必填' }],
    singleMaxAmount: [{ required: true, message: '单次最大金额兜底，超限阻断 必填' }],
    limitPeriod: [{ required: true, message: '限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM 必填' }],
  };

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
        await promotionConfigApi.update(form);
      } else {
        await promotionConfigApi.add(form);
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
