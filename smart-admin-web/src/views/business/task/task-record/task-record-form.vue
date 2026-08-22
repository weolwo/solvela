<!--
  * 任务记录表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 21:02:56
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <!-- 收会员号：任务记录是状态表，不存账号 -->
      <a-form-item label="会员号" name="memberId">
        <a-input-number style="width: 100%" v-model:value="form.memberId" placeholder="会员号" :controls="false" :precision="0" />
      </a-form-item>
      <a-form-item label="任务配置ID" name="taskConfigId">
        <a-input-number style="width: 100%" v-model:value="form.taskConfigId" placeholder="任务配置ID" />
      </a-form-item>
      <a-form-item label="活动编码" name="activityCode">
        <a-input style="width: 100%" v-model:value="form.activityCode" placeholder="活动编码" />
      </a-form-item>
      <a-form-item label="业务期数标识" name="periodKey">
        <a-input style="width: 100%" v-model:value="form.periodKey" placeholder="防重用：NONE 或日期(如 20260402)" />
      </a-form-item>
      <a-form-item label="开始时间" name="validStartTime">
        <a-date-picker :show-time="DAY_START_SHOW_TIME" valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.validStartTime" style="width: 100%" placeholder="开始时间" />
      </a-form-item>
      <a-form-item label="过期时间" name="validEndTime">
        <a-date-picker :show-time="DAY_END_SHOW_TIME" valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.validEndTime" style="width: 100%" placeholder="过期时间" />
      </a-form-item>
      <a-form-item label="当前进度值" name="currentMetric">
        <a-input-number style="width: 100%" v-model:value="form.currentMetric" placeholder="如已签到 3 天" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-select style="width: 100%" v-model:value="form.status" :options="RECORD_STATUS_OPTIONS" placeholder="请选择状态" allowClear />
      </a-form-item>
      <a-form-item label="达标时间" name="completeTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.completeTime" style="width: 100%" placeholder="达标时间" />
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
  import { taskRecordApi } from '/src/api/business/task/task-record-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { RECORD_STATUS_OPTIONS } from '/src/constants/business/task/task-record-const';
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
    memberId: undefined, //会员号（关联键）
    taskConfigId: undefined, //任务配置ID
    activityCode: undefined, //活动编码
    periodKey: undefined, //业务期数标识(防重用)：NONE, 日期(20260402)
    validStartTime: undefined, //开始时间
    validEndTime: undefined, //过期时间
    currentMetric: undefined, //当前进度值：如已签到 3.0000 天
    status: undefined, //状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期
    progressData: undefined, //进度详情
    ruleSnapshot: undefined, //接取任务时的规则快照
    prizeSnapshot: undefined, //接取任务时的奖励快照
    completeTime: undefined, //达标时间
  };

  let form = reactive({ ...formDefault });

  const rules = {
    memberId: [{ required: true, message: '会员号 必填' }],
    taskConfigId: [{ required: true, message: '任务配置ID 必填' }],
    activityCode: [{ required: true, message: '活动编码 必填' }],
    periodKey: [{ required: true, message: '业务期数标识 必填' }],
    validStartTime: [{ required: true, message: '开始时间 必填' }],
    validEndTime: [{ required: true, message: '过期时间 必填' }],
    currentMetric: [{ required: true, message: '当前进度值 必填' }],
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
        await taskRecordApi.update(form);
      } else {
        await taskRecordApi.add(form);
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
