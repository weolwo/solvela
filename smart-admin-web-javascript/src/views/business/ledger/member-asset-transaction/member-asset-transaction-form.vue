<!--
  * 交易明细表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:49:03
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="id" name="id">
        <a-input-number style="width: 100%" v-model:value="form.id" placeholder="id" />
      </a-form-item>
      <a-form-item label="会员名" name="memberName">
        <a-input style="width: 100%" v-model:value="form.memberName" placeholder="会员名" />
      </a-form-item>
      <a-form-item label="资产类型" name="assetType">
        <SmartEnumSelect width="100%" v-model:value="form.assetType" enum-name="" placeholder="资产类型：SCORE, BALANCE" />
      </a-form-item>
      <a-form-item label="资金流向" name="transactionType">
        <SmartEnumSelect width="100%" v-model:value="form.transactionType" enum-name="" placeholder="资金流向：1-收入, 2-支出" />
      </a-form-item>
      <a-form-item label="变动绝对值" name="changeAmount">
        <a-input-number style="width: 100%" v-model:value="form.changeAmount" placeholder="变动绝对值" />
      </a-form-item>
      <a-form-item label="变动后最新余额" name="balanceAfter">
        <a-input-number style="width: 100%" v-model:value="form.balanceAfter" placeholder="变动后最新余额" />
      </a-form-item>
      <a-form-item label="业务类型" name="bizType">
        <SmartEnumSelect width="100%" v-model:value="form.bizType" enum-name="" placeholder="业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST" />
      </a-form-item>
      <a-form-item label="关联ID" name="bizRefId">
        <a-input style="width: 100%" v-model:value="form.bizRefId" placeholder="关联外部业务ID(如 prize_code)" />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-input style="width: 100%" v-model:value="form.remark" placeholder="备注" />
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
  import { memberAssetTransactionApi } from '/@/api/business/ledger/member-asset-transaction/member-asset-transaction-api';
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
    id: undefined, //id
    memberName: undefined, //会员名
    assetType: undefined, //资产类型：SCORE, BALANCE
    transactionType: undefined, //资金流向：1-收入, 2-支出
    changeAmount: undefined, //变动绝对值
    balanceAfter: undefined, //变动后最新余额
    bizType: undefined, //业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST
    bizRefId: undefined, //关联外部业务ID(如 prize_code)
    remark: undefined, //C端展示摘要
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: 'id 必填' }],
    memberName: [{ required: true, message: '会员名 必填' }],
    assetType: [{ required: true, message: '资产类型：SCORE, BALANCE 必填' }],
    transactionType: [{ required: true, message: '资金流向：1-收入, 2-支出 必填' }],
    changeAmount: [{ required: true, message: '变动绝对值 必填' }],
    balanceAfter: [{ required: true, message: '变动后最新余额 必填' }],
    bizType: [{ required: true, message: '业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST 必填' }],
    bizRefId: [{ required: true, message: '关联外部业务ID(如 prize_code) 必填' }],
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
        await memberAssetTransactionApi.update(form);
      } else {
        await memberAssetTransactionApi.add(form);
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
