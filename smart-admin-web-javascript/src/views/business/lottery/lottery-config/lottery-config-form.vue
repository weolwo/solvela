<!--
  * 彩票配置
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 11:16:39
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="活动编码" name="activityCode">
        <a-input style="width: 100%" v-model:value="form.activityCode" placeholder="活动编码" />
      </a-form-item>
      <a-form-item label="彩票编码" name="lotteryCode">
        <a-input style="width: 100%" v-model:value="form.lotteryCode" placeholder="彩票编码" />
      </a-form-item>
      <a-form-item label="彩票名称" name="lotteryName">
        <a-input style="width: 100%" v-model:value="form.lotteryName" placeholder="彩票名称" />
      </a-form-item>
      <a-form-item label="字符集" name="numberCharset">
        <a-input style="width: 100%" v-model:value="form.numberCharset" placeholder="字符集：0-9, A-Z" />
      </a-form-item>
      <a-form-item label="号码长度" name="numberLength">
        <a-input-number style="width: 100%" v-model:value="form.numberLength" placeholder="号码长度" />
      </a-form-item>
      <a-form-item label="号池总数 (如: 1,000,000)" name="totalCount">
        <a-input-number style="width: 100%" v-model:value="form.totalCount" placeholder="号池总数 (如: 1,000,000)" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <SmartEnumSelect width="100%" v-model:value="form.status" enum-name="LOTTERY_STATUS_ENUM" placeholder="请选择状态" />
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
  import { lotteryConfigApi } from '/@/api/business/lottery/lottery-config/lottery-config-api';
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
    activityCode: undefined, //活动编码
    lotteryCode: undefined, //彩票编码
    lotteryName: undefined, //彩票名称
    numberCharset: undefined, //字符集：0-9, A-Z
    numberLength: undefined, //号码长度
    totalCount: undefined, //号池总数 (如: 1,000,000)
    status: undefined, //状态：0-下线, 1-上线
  };

  let form = reactive({ ...formDefault });

  const rules = {
    activityCode: [{ required: true, message: '活动编码 必填' }],
    lotteryCode: [{ required: true, message: '彩票编码 必填' }],
    lotteryName: [{ required: true, message: '彩票名称 必填' }],
    numberCharset: [{ required: true, message: '字符集：0-9, A-Z 必填' }],
    numberLength: [{ required: true, message: '号码长度 必填' }],
    totalCount: [{ required: true, message: '号池总数 (如: 1,000,000) 必填' }],
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
        await lotteryConfigApi.update(form);
      } else {
        await lotteryConfigApi.add(form);
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
