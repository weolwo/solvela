<!--
  * 任务模板表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 21:12:49
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
      <a-form-item label="模板编码" name="templateCode">
        <a-input style="width: 100%" v-model:value="form.templateCode" placeholder="模板编码" />
      </a-form-item>
      <a-form-item label="模板名称" name="templateName">
        <a-input style="width: 100%" v-model:value="form.templateName" placeholder="模板名称" />
      </a-form-item>
      <a-form-item label="流转类型" name="taskType">
        <a-input style="width: 100%" v-model:value="form.taskType" placeholder="流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型)" />
      </a-form-item>
      <a-form-item label="QLExpress脚本" name="ruleScript">
        <a-textarea style="width: 100%" v-model:value="form.ruleScript" placeholder="QLExpress脚本" />
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
  import { taskTemplateApi } from '/@/api/business/task/task-template/task-template-api';
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
    templateCode: undefined, //模板编码
    templateName: undefined, //模板名称
    taskType: undefined, //流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型)
    uiSchema: undefined, //前端渲染规则
    ruleScript: undefined, //QLExpress脚本
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: 'id 必填' }],
    tenantId: [{ required: true, message: '租户ID 必填' }],
    templateCode: [{ required: true, message: '模板编码 必填' }],
    templateName: [{ required: true, message: '模板名称 必填' }],
    taskType: [{ required: true, message: '流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型) 必填' }],
    uiSchema: [{ required: true, message: '前端渲染规则 必填' }],
    ruleScript: [{ required: true, message: 'QLExpress脚本 必填' }],
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
        await taskTemplateApi.update(form);
      } else {
        await taskTemplateApi.add(form);
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
