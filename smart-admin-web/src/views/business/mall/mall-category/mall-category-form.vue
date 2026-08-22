<!--
  * 商城-商品分类
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:28:16
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
        <a-form-item label="父级id"  name="parentId">
          <a-input-number style="width: 100%" v-model:value="form.parentId" placeholder="父级id：0-顶级分类。业务上限死两级" />
        </a-form-item>
        <a-form-item label="分类名称"  name="categoryName">
          <a-input style="width: 100%" v-model:value="form.categoryName" placeholder="分类名称：如 数码3C / 虚拟权益" />
        </a-form-item>
        <a-form-item label="排序"  name="sort">
          <a-input-number style="width: 100%" v-model:value="form.sort" placeholder="排序：从小到大" />
        </a-form-item>
        <a-form-item label="状态"  name="status">
          <a-input-number style="width: 100%" v-model:value="form.status" placeholder="状态：0-禁用, 1-启用" />
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
  import { mallCategoryApi } from '/@/api/business/mall/mall-category-api';
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
      parentId: undefined, //父级id：0-顶级分类。业务上限死两级
      categoryName: undefined, //分类名称：如 数码3C / 虚拟权益
      sort: undefined, //排序：从小到大
      status: undefined, //状态：0-禁用, 1-启用
  };

  let form = reactive({ ...formDefault });

  const rules = {
      id: [{ required: true, message: 'id 必填' }],
      parentId: [{ required: true, message: '父级id：0-顶级分类。业务上限死两级 必填' }],
      categoryName: [{ required: true, message: '分类名称：如 数码3C / 虚拟权益 必填' }],
      sort: [{ required: true, message: '排序：从小到大 必填' }],
      status: [{ required: true, message: '状态：0-禁用, 1-启用 必填' }],
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
        await mallCategoryApi.update(form);
      } else {
        await mallCategoryApi.add(form);
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
