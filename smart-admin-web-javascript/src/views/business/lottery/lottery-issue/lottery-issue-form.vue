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
      <a-form-item label="id" name="id">
        <a-input-number style="width: 100%" v-model:value="form.id" placeholder="id" />
      </a-form-item>
      <a-form-item label="租户id" name="tenantId">
        <a-input style="width: 100%" v-model:value="form.tenantId" placeholder="租户id" />
      </a-form-item>
      <a-form-item label="彩票编码" name="lotteryCode">
        <SmartEnumSelect width="100%" v-model:value="form.lotteryCode" enum-name="" placeholder="彩票编码" />
      </a-form-item>
      <a-form-item label="期号" name="issueNo">
        <a-input style="width: 100%" v-model:value="form.issueNo" placeholder="期号" />
      </a-form-item>
      <a-form-item label="已售/已派发数量" name="soldCount">
        <a-input-number style="width: 100%" v-model:value="form.soldCount" placeholder="已售/已派发数量" />
      </a-form-item>
      <a-form-item label="售卖开始时间" name="saleStartTime">
        <a-date-picker
          show-time
          valueFormat="YYYY-MM-DD HH:mm:ss"
          v-model:value="form.saleStartTime"
          style="width: 100%"
          placeholder="售卖开始时间"
        />
      </a-form-item>
      <a-form-item label="售卖结束时间" name="saleEndTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.saleEndTime" style="width: 100%" placeholder="售卖结束时间" />
      </a-form-item>
      <a-form-item label="开奖时间" name="settleTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.settleTime" style="width: 100%" placeholder="开奖时间" />
      </a-form-item>
      <a-form-item label="开奖号码" name="winningNumber">
        <a-input style="width: 100%" v-model:value="form.winningNumber" placeholder="开奖号码" />
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
    soldCount: undefined, //已售/已派发数量
    saleStartTime: undefined, //售卖开始时间
    saleEndTime: undefined, //售卖结束时间
    settleTime: undefined, //开奖时间
    winningNumber: undefined, //开奖号码
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: 'id 必填' }],
    tenantId: [{ required: true, message: '租户id 必填' }],
    lotteryCode: [{ required: true, message: '彩票编码 必填' }],
    issueNo: [{ required: true, message: '期号 必填' }],
    soldCount: [{ required: true, message: '已售/已派发数量 必填' }],
    saleStartTime: [{ required: true, message: '售卖开始时间 必填' }],
    saleEndTime: [{ required: true, message: '售卖结束时间 必填' }],
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
