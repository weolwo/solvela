<!--
  * 商城-SKU与库存
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:37:50
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
        <a-form-item label="关联id"  name="commodityId">
          <a-input-number style="width: 100%" v-model:value="form.commodityId" placeholder="关联 t_mall_commodity.id" />
        </a-form-item>
        <a-form-item label="SKU编码"  name="skuCode">
          <a-input style="width: 100%" v-model:value="form.skuCode" placeholder="SKU编码：10位大写字母+数字，全局唯一" />
        </a-form-item>
        <a-form-item label="总库存"  name="totalStock">
          <a-input-number style="width: 100%" v-model:value="form.totalStock" placeholder="总库存：运营投放量，恒定不变，补货改这里" />
        </a-form-item>
        <a-form-item label="锁定库存"  name="lockedStock">
          <a-input-number style="width: 100%" v-model:value="form.lockedStock" placeholder="锁定库存：已下单未履约（仅 pay_type=2 会悬挂）" />
        </a-form-item>
        <a-form-item label="已售数量"  name="soldCount">
          <a-input-number style="width: 100%" v-model:value="form.soldCount" placeholder="已售数量：履约成功累加" />
        </a-form-item>
        <a-form-item label="状态"  name="skuStatus">
          <a-input-number style="width: 100%" v-model:value="form.skuStatus" placeholder="状态：0-停用, 1-启用" />
        </a-form-item>
        <a-form-item label="排序"  name="sort">
          <a-input-number style="width: 100%" v-model:value="form.sort" placeholder="排序" />
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
  import { mallSkuApi } from '/@/api/business/mall/mall-sku-api';
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
      commodityId: undefined, //关联 t_mall_commodity.id
      skuCode: undefined, //SKU编码：10位大写字母+数字，全局唯一
      skuAttrs: undefined, //规格组合：{"颜色":"星空灰","尺码":"XL"}。无规格商品填 {}
      totalStock: undefined, //总库存：运营投放量，恒定不变，补货改这里
      lockedStock: undefined, //锁定库存：已下单未履约（仅 pay_type=2 会悬挂）
      soldCount: undefined, //已售数量：履约成功累加
      skuStatus: undefined, //状态：0-停用, 1-启用
      sort: undefined, //排序
  };

  let form = reactive({ ...formDefault });

  const rules = {
      id: [{ required: true, message: 'id 必填' }],
      commodityId: [{ required: true, message: '关联 t_mall_commodity.id 必填' }],
      skuCode: [{ required: true, message: 'SKU编码：10位大写字母+数字，全局唯一 必填' }],
      skuAttrs: [{ required: true, message: '规格组合：{"颜色":"星空灰","尺码":"XL"}。无规格商品填 {} 必填' }],
      totalStock: [{ required: true, message: '总库存：运营投放量，恒定不变，补货改这里 必填' }],
      lockedStock: [{ required: true, message: '锁定库存：已下单未履约（仅 pay_type=2 会悬挂） 必填' }],
      soldCount: [{ required: true, message: '已售数量：履约成功累加 必填' }],
      skuStatus: [{ required: true, message: '状态：0-停用, 1-启用 必填' }],
      sort: [{ required: true, message: '排序 必填' }],
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
        await mallSkuApi.update(form);
      } else {
        await mallSkuApi.add(form);
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
