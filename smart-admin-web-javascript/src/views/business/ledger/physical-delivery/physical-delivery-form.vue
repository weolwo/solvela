<!--
  * 发货物流表
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 00:03:01
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
      <a-form-item label="发奖提案ID" name="proposalId">
        <a-input-number style="width: 100%" v-model:value="form.proposalId" placeholder="发奖提案ID" />
      </a-form-item>
      <a-form-item label="来源类型" name="sourceType">
        <a-input style="width: 100%" v-model:value="form.sourceType" placeholder="来源类型" />
      </a-form-item>
      <a-form-item label="收件人姓名" name="receiverName">
        <a-input style="width: 100%" v-model:value="form.receiverName" placeholder="收件人姓名" />
      </a-form-item>
      <a-form-item label="收件人电话" name="receiverPhone">
        <a-input style="width: 100%" v-model:value="form.receiverPhone" placeholder="收件人电话" />
      </a-form-item>
      <a-form-item label="收件详细地址" name="receiverAddress">
        <a-input style="width: 100%" v-model:value="form.receiverAddress" placeholder="收件详细地址" />
      </a-form-item>
      <a-form-item label="物流公司" name="logisticsCompany">
        <a-input style="width: 100%" v-model:value="form.logisticsCompany" placeholder="物流公司" />
      </a-form-item>
      <a-form-item label="物流单号" name="logisticsNo">
        <a-input style="width: 100%" v-model:value="form.logisticsNo" placeholder="物流单号" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <SmartEnumSelect width="100%" v-model:value="form.status" enum-name="" placeholder="状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回" />
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
  import { physicalDeliveryApi } from '/@/api/business/ledger/physical-delivery/physical-delivery-api';
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
    proposalId: undefined, //发奖提案ID
    sourceType: undefined, //来源类型
    receiverName: undefined, //收件人姓名
    receiverPhone: undefined, //收件人电话
    receiverAddress: undefined, //收件详细地址
    logisticsCompany: undefined, //物流公司
    logisticsNo: undefined, //物流单号
    status: undefined, //状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: 'id 必填' }],
    memberName: [{ required: true, message: '会员名 必填' }],
    proposalId: [{ required: true, message: '发奖提案ID 必填' }],
    sourceType: [{ required: true, message: '来源类型 必填' }],
    receiverName: [{ required: true, message: '收件人姓名 必填' }],
    receiverPhone: [{ required: true, message: '收件人电话 必填' }],
    receiverAddress: [{ required: true, message: '收件详细地址 必填' }],
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
        await physicalDeliveryApi.update(form);
      } else {
        await physicalDeliveryApi.add(form);
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
