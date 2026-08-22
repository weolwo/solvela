<!--
  * 商城-会员限兑计数
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:33:25
  * @Copyright  weolwo
-->
<template>
  <a-modal
      :title="form.id ? '编辑' : '添加'"
      :width="800"
      :open="visibleFlag"
      @cancel="onClose"
      :maskClosable="false"
      :destroyOnClose="true"
  >
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }" >
        <a-form-item label="id"  name="id">
          <a-input-number style="width: 100%" v-model:value="form.id" placeholder="id" />
        </a-form-item>
        <a-form-item label="会员号：关联键"  name="memberId">
          <a-input-number style="width: 100%" v-model:value="form.memberId" placeholder="会员号：关联键" />
        </a-form-item>
        <a-form-item label="商品id"  name="commodityId">
          <a-input-number style="width: 100%" v-model:value="form.commodityId" placeholder="商品id" />
        </a-form-item>
        <a-form-item label="周期标识：NONE(终身) / 20260819(日) / 2026W34(周) / 202608(月)。取值口径对齐 t_task_record.period_key"  name="periodKey">
          <a-input style="width: 100%" v-model:value="form.periodKey" placeholder="周期标识：NONE(终身) / 20260819(日) / 2026W34(周) / 202608(月)。取值口径对齐 t_task_record.period_key" />
        </a-form-item>
        <a-form-item label="该周期内已兑件数"  name="usedCount">
          <a-input-number style="width: 100%" v-model:value="form.usedCount" placeholder="该周期内已兑件数" />
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
  import { mallExchangeLimitApi } from '/@/api/business/mall/mall-exchange-limit-api';
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
      memberId: undefined, //会员号：关联键
      commodityId: undefined, //商品id
      periodKey: undefined, //周期标识：NONE(终身) / 20260819(日) / 2026W34(周) / 202608(月)。取值口径对齐 t_task_record.period_key
      usedCount: undefined, //该周期内已兑件数
  };

  let form = reactive({ ...formDefault });

  const rules = {
      id: [{ required: true, message: 'id 必填' }],
      memberId: [{ required: true, message: '会员号：关联键 必填' }],
      commodityId: [{ required: true, message: '商品id 必填' }],
      periodKey: [{ required: true, message: '周期标识：NONE(终身) / 20260819(日) / 2026W34(周) / 202608(月)。取值口径对齐 t_task_record.period_key 必填' }],
      usedCount: [{ required: true, message: '该周期内已兑件数 必填' }],
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
        await mallExchangeLimitApi.update(form);
      } else {
        await mallExchangeLimitApi.add(form);
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
