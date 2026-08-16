<!--
  * 提案表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:13:50
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="租户ID" name="tenantId">
        <a-input style="width: 100%" v-model:value="form.tenantId" placeholder="租户ID" />
      </a-form-item>
      <a-form-item label="提案单号" name="tradeNo">
        <a-input style="width: 100%" v-model:value="form.tradeNo" placeholder="服务端生成，对外唯一标识" />
      </a-form-item>
      <a-form-item label="会员名" name="memberName">
        <a-input style="width: 100%" v-model:value="form.memberName" placeholder="会员名" />
      </a-form-item>
      <a-form-item label="资产类型" name="assetType">
        <a-select style="width: 100%" v-model:value="form.assetType" :options="ASSET_TYPE_OPTIONS" placeholder="请选择资产类型" allowClear />
      </a-form-item>
      <a-form-item label="资产引用" name="assetRef">
        <a-input style="width: 100%" v-model:value="form.assetRef" placeholder="券模/SKU，值类资产（积分、现金）留空" />
      </a-form-item>
      <a-form-item label="发放金额/积分数" name="amount">
        <a-input-number style="width: 100%" v-model:value="form.amount" placeholder="发放金额/积分数" />
      </a-form-item>
      <a-form-item label="发放数量" name="quantity">
        <a-input-number style="width: 100%" v-model:value="form.quantity" placeholder="扣减优惠配置库存用" />
      </a-form-item>
      <a-form-item label="来源" name="sourceType">
        <a-select style="width: 100%" v-model:value="form.sourceType" :options="PROPOSAL_SOURCE_TYPE_OPTIONS" placeholder="请选择来源" allowClear />
      </a-form-item>
      <a-form-item label="来源单号" name="sourceBizId">
        <a-input style="width: 100%" v-model:value="form.sourceBizId" placeholder="任务记录ID / 抽奖流水号" />
      </a-form-item>
      <a-form-item label="优惠配置ID" name="promotionConfigId">
        <a-input-number style="width: 100%" v-model:value="form.promotionConfigId" placeholder="优惠配置ID" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-select style="width: 100%" v-model:value="form.status" :options="PROPOSAL_STATUS_OPTIONS" placeholder="请选择状态" allowClear />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-input style="width: 100%" v-model:value="form.remark" placeholder="执行失败/风控拦截原因，或场景说明" />
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
  import { proposalRecordApi } from '/@/api/business/risk/proposal-record/proposal-record-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import {
    ASSET_TYPE_OPTIONS,
    PROPOSAL_SOURCE_TYPE_OPTIONS,
    PROPOSAL_STATUS_OPTIONS,
  } from '/@/constants/business/risk/proposal-record/proposal-record-const';

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
    id: undefined, //id
    tenantId: undefined, //租户ID
    tradeNo: undefined, //提案单号，服务端生成，对外唯一标识
    memberName: undefined, //会员名
    assetType: undefined, //SCORE/BALANCE/COUPON/PHYSICAL
    assetRef: undefined, //资产引用：券模/SKU，值类资产为空
    amount: undefined, //发放金额/积分数
    quantity: undefined, //发放数量，扣 used_quota 用
    sourceType: undefined, //来源：TASK(任务), DRAW(抽奖), MANUAL(人工)
    sourceBizId: undefined, //来源单号(task_record_id 或 draw_log_trace_id)
    promotionConfigId: undefined, //优惠配置ID
    status: undefined, //状态：见 PROPOSAL_STATUS_ENUM
    remark: undefined, //执行失败/风控拦截原因
  };

  let form = reactive({ ...formDefault });

  const rules = {
    tenantId: [{ required: true, message: '租户ID 必填' }],
    tradeNo: [{ required: true, message: '提案单号 必填' }],
    memberName: [{ required: true, message: '会员名 必填' }],
    assetType: [{ required: true, message: '资产类型 必填' }],
    amount: [{ required: true, message: '发放金额/积分数 必填' }],
    quantity: [{ required: true, message: '发放数量 必填' }],
    sourceType: [{ required: true, message: '来源 必填' }],
    sourceBizId: [{ required: true, message: '来源单号 必填' }],
    promotionConfigId: [{ required: true, message: '优惠配置ID 必填' }],
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
        await proposalRecordApi.update(form);
      } else {
        await proposalRecordApi.add(form);
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
