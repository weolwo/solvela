<!--
  * 彩票奖励配置
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 11:50:34
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="租户id" name="tenantId">
        <a-input style="width: 100%" v-model:value="form.tenantId" placeholder="租户id" />
      </a-form-item>
      <a-form-item label="彩票编码" name="lotteryCode">
        <a-input style="width: 100%" v-model:value="form.lotteryCode" placeholder="彩票编码" />
      </a-form-item>
      <a-form-item label="奖品奖级" name="prizeLevel">
        <a-input-number style="width: 100%" v-model:value="form.prizeLevel" placeholder="奖品奖级" />
      </a-form-item>
      <a-form-item label="匹配规则" name="matchRule">
        <a-select style="width: 100%" v-model:value="form.matchRule" :options="MATCH_RULE_OPTIONS" placeholder="请选择匹配规则" allowClear />
      </a-form-item>
      <a-form-item label="匹配长度" name="matchLength">
        <a-input-number style="width: 100%" v-model:value="form.matchLength" placeholder="匹配长度" />
      </a-form-item>
      <a-form-item label="奖品编码" name="prizeCode">
        <a-input style="width: 100%" v-model:value="form.prizeCode" placeholder="奖品编码" />
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
  import { lotteryPrizeRuleApi } from '/@/api/business/lottery/lottery-prize-rule/lottery-prize-rule-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { MATCH_RULE_OPTIONS } from '/@/constants/business/lottery/lottery-const';

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
    prizeLevel: undefined, //奖品奖级
    matchRule: undefined, //匹配规则,EXACT:全号, TAIL:尾号匹配, HEAD:首号匹配
    matchLength: undefined, //匹配长度
    prizeCode: undefined, //奖品编码
  };

  let form = reactive({ ...formDefault });

  const rules = {
    tenantId: [{ required: true, message: '租户id 必填' }],
    lotteryCode: [{ required: true, message: '彩票编码 必填' }],
    prizeLevel: [{ required: true, message: '奖品奖级 必填' }],
    matchRule: [{ required: true, message: '匹配规则,EXACT:全号, TAIL:尾号匹配, HEAD:首号匹配 必填' }],
    matchLength: [{ required: true, message: '匹配长度 必填' }],
    prizeCode: [{ required: true, message: '奖品编码 必填' }],
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
        await lotteryPrizeRuleApi.update(form);
      } else {
        await lotteryPrizeRuleApi.add(form);
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
