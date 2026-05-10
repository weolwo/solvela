<!--
  * 奖励记录表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 20:27:03
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="id" name="id">
        <a-input-number style="width: 100%" v-model:value="form.id" placeholder="id" />
      </a-form-item>
      <a-form-item label="租户ID" name="tenantId">
        <SmartEnumSelect width="100%" v-model:value="form.tenantId" enum-name="" placeholder="租户ID" />
      </a-form-item>
      <a-form-item label="会员名" name="memberName">
        <a-input style="width: 100%" v-model:value="form.memberName" placeholder="会员名" />
      </a-form-item>
      <a-form-item label="奖品编码" name="prizeCode">
        <a-input style="width: 100%" v-model:value="form.prizeCode" placeholder="奖品编码" />
      </a-form-item>
      <a-form-item label="优惠配置ID" name="promotionConfigId">
        <a-input-number style="width: 100%" v-model:value="form.promotionConfigId" placeholder="优惠配置ID" />
      </a-form-item>
      <a-form-item label="活动编码" name="activityCode">
        <a-input style="width: 100%" v-model:value="form.activityCode" placeholder="活动编码" />
      </a-form-item>
      <a-form-item label="奖品级别" name="prizeLevel">
        <a-input-number style="width: 100%" v-model:value="form.prizeLevel" placeholder="奖品级别" />
      </a-form-item>
      <a-form-item label="奖品名称" name="prizeName">
        <a-input style="width: 100%" v-model:value="form.prizeName" placeholder="奖品名称" />
      </a-form-item>
      <a-form-item label="奖励类型：SCORE, BALANCE, COUPON, PHYSICAL" name="prizeType">
        <a-input style="width: 100%" v-model:value="form.prizeType" placeholder="奖励类型：SCORE, BALANCE, COUPON, PHYSICAL" />
      </a-form-item>
      <a-form-item label="奖励体值(积分数/券ID)" name="prizeValue">
        <a-input style="width: 100%" v-model:value="form.prizeValue" placeholder="奖励体值(积分数/券ID)" />
      </a-form-item>
      <a-form-item label="异常原因" name="failReason">
        <a-input style="width: 100%" v-model:value="form.failReason" placeholder="异常原因" />
      </a-form-item>
      <a-form-item label="审批模式" name="approveMode">
        <a-input-number style="width: 100%" v-model:value="form.approveMode" placeholder="审批模式：0-自动免审, 1-人工审批" />
      </a-form-item>
      <a-form-item label="审批状态" name="approveStatus">
        <a-input-number style="width: 100%" v-model:value="form.approveStatus" placeholder="审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回" />
      </a-form-item>
      <a-form-item label="审批人" name="approveBy">
        <a-input style="width: 100%" v-model:value="form.approveBy" placeholder="审批人" />
      </a-form-item>
      <a-form-item label="审批时间" name="approveTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.approveTime" style="width: 100%" placeholder="审批时间" />
      </a-form-item>
      <a-form-item label="过期时间" name="validUntil">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.validUntil" style="width: 100%" placeholder="过期时间" />
      </a-form-item>
      <a-form-item label="执行状态" name="status">
        <a-input-number style="width: 100%" v-model:value="form.status" placeholder="执行状态：0-等待, 1-成功, 2-失败" />
      </a-form-item>
      <a-form-item label="外部单号" name="externalBizNo">
        <a-input style="width: 100%" v-model:value="form.externalBizNo" placeholder="外部单号" />
      </a-form-item>
      <a-form-item label="异常原因" name="remark">
        <a-input style="width: 100%" v-model:value="form.remark" placeholder="异常原因" />
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
  import { prizeLogApi } from '/@/api/business/prize/prize-log/prize-log-api';
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
    tenantId: undefined, //租户ID
    memberName: undefined, //会员名
    prizeCode: undefined, //奖品编码
    promotionConfigId: undefined, //优惠配置ID
    activityCode: undefined, //活动编码
    prizeLevel: undefined, //奖品级别
    prizeName: undefined, //奖品名称
    prizeType: undefined, //奖励类型：SCORE, BALANCE, COUPON, PHYSICAL
    prizeValue: undefined, //奖励体值(积分数/券ID)
    failReason: undefined, //异常原因
    approveMode: undefined, //审批模式：0-自动免审, 1-人工审批
    approveStatus: undefined, //审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回
    approveBy: undefined, //审批人
    approveTime: undefined, //审批时间
    validUntil: undefined, //过期时间
    status: undefined, //执行状态：0-等待, 1-成功, 2-失败
    externalBizNo: undefined, //外部单号
    remark: undefined, //异常原因
  };

  let form = reactive({ ...formDefault });

  const rules = {
    tenantId: [{ required: true, message: '租户ID 必填' }],
    memberName: [{ required: true, message: '会员名 必填' }],
    prizeCode: [{ required: true, message: '奖品编码 必填' }],
    promotionConfigId: [{ required: true, message: '优惠配置ID 必填' }],
    activityCode: [{ required: true, message: '活动编码 必填' }],
    prizeName: [{ required: true, message: '奖品名称 必填' }],
    prizeType: [{ required: true, message: '奖励类型：SCORE, BALANCE, COUPON, PHYSICAL 必填' }],
    prizeValue: [{ required: true, message: '奖励体值(积分数/券ID) 必填' }],
    approveMode: [{ required: true, message: '审批模式：0-自动免审, 1-人工审批 必填' }],
    approveStatus: [{ required: true, message: '审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回 必填' }],
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
        await prizeLogApi.update(form);
      } else {
        await prizeLogApi.add(form);
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
