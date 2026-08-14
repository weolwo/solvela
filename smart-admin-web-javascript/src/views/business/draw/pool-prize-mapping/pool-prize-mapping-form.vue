<!--
  * 奖池奖项映射
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 10:07:03
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="租户id" name="tenantId">
        <a-input style="width: 100%" v-model:value="form.tenantId" placeholder="租户id" />
      </a-form-item>
      <a-form-item label="奖池编码" name="poolCode">
        <a-input style="width: 100%" v-model:value="form.poolCode" placeholder="奖池编码" />
      </a-form-item>
      <a-form-item label="奖项id" name="prizeItemId">
        <a-input-number style="width: 100%" v-model:value="form.prizeItemId" placeholder="奖项id" />
      </a-form-item>
      <a-form-item label="中奖概率(万分位)" name="probability">
        <a-input-number style="width: 100%" v-model:value="form.probability" placeholder="中奖概率(万分位)" />
      </a-form-item>
      <a-form-item label="序号" name="sortWeight">
        <a-input-number style="width: 100%" v-model:value="form.sortWeight" placeholder="序号" />
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
  import { poolPrizeMappingApi } from '/@/api/business/draw/pool-prize-mapping/pool-prize-mapping-api';
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
    tenantId: undefined, //租户id
    poolCode: undefined, //奖池编码
    prizeItemId: undefined, //奖项id
    probability: undefined, //中奖概率(万分位)
    sortWeight: undefined, //序号
    createBy: undefined, //创建人
    createTime: undefined, //创建时间
    updateBy: undefined, //更新人
    updateTime: undefined, //更新时间
  };

  let form = reactive({ ...formDefault });

  const rules = {
    tenantId: [{ required: true, message: '租户id 必填' }],
    poolCode: [{ required: true, message: '奖池编码 必填' }],
    prizeItemId: [{ required: true, message: '奖项id 必填' }],
    probability: [{ required: true, message: '中奖概率(万分位) 必填' }],
    sortWeight: [{ required: true, message: '序号 必填' }],
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
        await poolPrizeMappingApi.update(form);
      } else {
        await poolPrizeMappingApi.add(form);
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
