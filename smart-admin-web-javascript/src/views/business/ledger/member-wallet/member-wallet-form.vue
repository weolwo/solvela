<!--
  * 会员钱包表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:56:48
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
        <a-select style="width: 100%" v-model:value="form.assetType" :options="ASSET_TYPE_OPTIONS" placeholder="资产类型" :disabled="!!form.id" />
      </a-form-item>
      <a-form-item label="余额" name="balance">
        <a-input-number style="width: 100%" v-model:value="form.balance" placeholder="余额" />
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
  import { memberWalletApi } from '/@/api/business/ledger/member-wallet/member-wallet-api';
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

  // 资产类型（取值对齐后端 PrizeTypeEnum，与流水表同一字典）
  const ASSET_TYPE_OPTIONS = [
    { value: 'SCORE', label: '积分' },
    { value: 'BALANCE', label: '现金' },
  ];

  const formDefault = {
    id: undefined, //id
    memberName: undefined, //会员名
    assetType: undefined, //资产类型：SCORE-积分, BALANCE-现金
    balance: undefined, //余额
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: 'id 必填' }],
    memberName: [{ required: true, message: '会员名 必填' }],
    assetType: [{ required: true, message: '资产类型 必填' }],
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
        await memberWalletApi.update(form);
      } else {
        await memberWalletApi.add(form);
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
