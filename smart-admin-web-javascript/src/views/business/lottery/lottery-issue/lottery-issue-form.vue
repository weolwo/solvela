<!--
  * 期号配置
  *
  * @Author:    weolwo
  * @Date:      2026-05-09 16:54:51
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="租户id" name="tenantId">
        <a-input style="width: 100%" v-model:value="form.tenantId" placeholder="租户id" />
      </a-form-item>
      <a-form-item label="彩票编码" name="lotteryCode">
        <SmartEnumSelect width="100%" v-model:value="form.lotteryCode" enum-name="" placeholder="彩票编码" />
      </a-form-item>
      <a-form-item label="期号" name="issueNo">
        <a-input style="width: 100%" v-model:value="form.issueNo" placeholder="期号" />
      </a-form-item>
      <a-form-item label="开始偏移量" name="startOffset">
        <a-input-number style="width: 100%" v-model:value="form.startOffset" placeholder="开始偏移量" />
      </a-form-item>
      <a-form-item label="已售/已派发数量" name="soldCount">
        <a-input-number style="width: 100%" v-model:value="form.soldCount" placeholder="已售/已派发数量" />
      </a-form-item>
      <a-form-item label="开始售卖" name="sellStartTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.sellStartTime" style="width: 100%" placeholder="开始售卖" />
      </a-form-item>
      <a-form-item label="结束时间" name="sellEndTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.sellEndTime" style="width: 100%" placeholder="结束时间" />
      </a-form-item>
      <a-form-item label="开奖时间" name="openTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.openTime" style="width: 100%" placeholder="开奖时间" />
      </a-form-item>
      <a-form-item label="是否可重复开奖" name="canRepeat">
        <a-input-number style="width: 100%" v-model:value="form.canRepeat" placeholder="是否可重复开奖：0否，1是" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-input-number style="width: 100%" v-model:value="form.status" placeholder="状态: 0-待开奖, 1-部分开奖, 2-已开奖" />
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
  import { lotteryIssueApi } from '/@/api/business/lottery/lottery-issue/lottery-issue-api';
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
    tenantId: undefined, //租户id
    lotteryCode: undefined, //彩票编码
    issueNo: undefined, //期号
    startOffset: undefined, //开始偏移量
    soldCount: undefined, //已售/已派发数量
    sellStartTime: undefined, //开始售卖
    sellEndTime: undefined, //结束时间
    openTime: undefined, //开奖时间
    canRepeat: undefined, //是否可重复开奖：0否，1是
    winningNumber: undefined, //开奖号码
    status: undefined, //状态: 0-待开奖, 1-部分开奖, 2-已开奖
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: 'id 必填' }],
    tenantId: [{ required: true, message: '租户id 必填' }],
    lotteryCode: [{ required: true, message: '彩票编码 必填' }],
    issueNo: [{ required: true, message: '期号 必填' }],
    startOffset: [{ required: true, message: '开始偏移量 必填' }],
    soldCount: [{ required: true, message: '已售/已派发数量 必填' }],
    sellStartTime: [{ required: true, message: '开始售卖 必填' }],
    sellEndTime: [{ required: true, message: '结束时间 必填' }],
    status: [{ required: true, message: '状态: 0-待开奖, 1-部分开奖, 2-已开奖 必填' }],
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
        await lotteryIssueApi.update(form);
      } else {
        await lotteryIssueApi.add(form);
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
