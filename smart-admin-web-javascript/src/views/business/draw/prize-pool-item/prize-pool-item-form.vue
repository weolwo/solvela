<!--
  * 奖池奖项库
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 09:52:45
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="id" name="id">
        <a-input-number style="width: 100%" v-model:value="form.id" placeholder="id" />
      </a-form-item>
      <a-form-item label="活动编码" name="activityCode">
        <a-input style="width: 100%" v-model:value="form.activityCode" placeholder="归属活动编码" />
      </a-form-item>
      <a-form-item label="奖品Id" name="prizeCode">
        <SmartEnumSelect width="100%" v-model:value="form.prizeCode" enum-name="" placeholder="关联(t_prize_config)" />
      </a-form-item>
      <a-form-item label="单人限领次数" name="userMaxCount">
        <a-input-number style="width: 100%" v-model:value="form.userMaxCount" placeholder="单人限领次数: -1不限, 1表示每人最多中一次" />
      </a-form-item>
      <a-form-item label="总库存" name="totalStock">
        <a-input-number style="width: 100%" v-model:value="form.totalStock" placeholder="本次活动总共出几个？-1不限" />
      </a-form-item>
      <a-form-item label="已用库存" name="usedStock">
        <a-input-number style="width: 100%" v-model:value="form.usedStock" placeholder="跨奖池累计已出数量" />
      </a-form-item>
      <a-form-item label="白名单" name="whiteList">
        <a-textarea style="width: 100%" v-model:value="form.whiteList" placeholder="活动级白名单：指定用户必中" />
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
  import { prizePoolItemApi } from '/@/api/business/draw/prize-pool-item/prize-pool-item-api';
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
    activityCode: undefined, //归属活动编码
    prizeCode: undefined, //关联(t_prize_config)
    userMaxCount: undefined, //单人限领次数: -1不限, 1表示每人最多中一次
    totalStock: undefined, //本次活动总共出几个？-1不限
    usedStock: undefined, //跨奖池累计已出数量
    whiteList: undefined, //活动级白名单：指定用户必中
  };

  let form = reactive({ ...formDefault });

  const rules = {
    activityCode: [{ required: true, message: '归属活动编码 必填' }],
    prizeCode: [{ required: true, message: '关联(t_prize_config) 必填' }],
    userMaxCount: [{ required: true, message: '单人限领次数: -1不限, 1表示每人最多中一次 必填' }],
    totalStock: [{ required: true, message: '本次活动总共出几个？-1不限 必填' }],
    usedStock: [{ required: true, message: '跨奖池累计已出数量 必填' }],
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
        await prizePoolItemApi.update(form);
      } else {
        await prizePoolItemApi.add(form);
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
