<!--
  * 会员优惠券
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:42:44
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="id" name="id">
        <a-input-number style="width: 100%" v-model:value="form.id" placeholder="id" />
      </a-form-item>
      <a-form-item label="租户ID" name="tenantId">
        <a-input style="width: 100%" v-model:value="form.tenantId" placeholder="租户ID" />
      </a-form-item>
      <a-form-item label="会员名" name="memberName">
        <a-input style="width: 100%" v-model:value="form.memberName" placeholder="会员名" />
      </a-form-item>
      <a-form-item label="券模编码" name="couponCode">
        <a-input style="width: 100%" v-model:value="form.couponCode" placeholder="券模编码" />
      </a-form-item>
      <a-form-item label="券类型" name="couponType">
        <a-input style="width: 100%" v-model:value="form.couponType" placeholder="券类型" />
      </a-form-item>
      <a-form-item label="券名称" name="couponName">
        <a-input style="width: 100%" v-model:value="form.couponName" placeholder="券名称" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-input-number style="width: 100%" v-model:value="form.status" placeholder="状态：0-未使用, 1-已使用, 2-已过期, 3-已作废" />
      </a-form-item>
      <a-form-item label="来源" name="sourceType">
        <a-input style="width: 100%" v-model:value="form.sourceType" placeholder="来源：DRAW, TASK, MANUAL_SEND" />
      </a-form-item>
      <a-form-item label="关联单号" name="sourceBizId">
        <a-input style="width: 100%" v-model:value="form.sourceBizId" placeholder="关联单号" />
      </a-form-item>
      <a-form-item label="有效期开始" name="validStartTime">
        <a-date-picker :show-time="DAY_START_SHOW_TIME" valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.validStartTime" style="width: 100%" placeholder="有效期开始" />
      </a-form-item>
      <a-form-item label="有效期结束" name="validEndTime">
        <a-date-picker :show-time="DAY_END_SHOW_TIME" valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.validEndTime" style="width: 100%" placeholder="有效期结束" />
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
  import { DAY_END_SHOW_TIME, DAY_START_SHOW_TIME } from '/@/constants/date-time-const';
  import { reactive, ref, nextTick } from 'vue';
  import _ from 'lodash';
  import { message } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { memberCouponApi } from '/@/api/business/ledger/member-coupon/member-coupon-api';
  import { smartSentry } from '/@/lib/smart-sentry';

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
    tenantId: undefined, //租户ID
    memberName: undefined, //会员名
    couponCode: undefined, //券模编码
    couponType: undefined, //券类型
    couponName: undefined, //券名称
    status: undefined, //状态：0-未使用, 1-已使用, 2-已过期, 3-已作废
    sourceType: undefined, //来源：DRAW, TASK, MANUAL_SEND
    sourceBizId: undefined, //关联单号
    validStartTime: undefined, //有效期开始
    validEndTime: undefined, //有效期结束
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: 'id 必填' }],
    tenantId: [{ required: true, message: '租户ID 必填' }],
    memberName: [{ required: true, message: '会员名 必填' }],
    couponCode: [{ required: true, message: '券模编码 必填' }],
    couponType: [{ required: true, message: '券类型 必填' }],
    couponName: [{ required: true, message: '券名称 必填' }],
    status: [{ required: true, message: '状态：0-未使用, 1-已使用, 2-已过期, 3-已作废 必填' }],
    sourceType: [{ required: true, message: '来源：DRAW, TASK, MANUAL_SEND 必填' }],
    sourceBizId: [{ required: true, message: '关联单号 必填' }],
    validStartTime: [{ required: true, message: '有效期开始 必填' }],
    validEndTime: [{ required: true, message: '有效期结束 必填' }],
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
        await memberCouponApi.update(form);
      } else {
        await memberCouponApi.add(form);
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
