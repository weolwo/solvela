<!--
  * 商城-兑换订单
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:35:46
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="id" name="id">
        <a-input-number style="width: 100%" v-model:value="form.id" placeholder="id" />
      </a-form-item>
      <a-form-item label="订单号" name="orderNo">
        <a-input style="width: 100%" v-model:value="form.orderNo" placeholder="订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键" />
      </a-form-item>
      <a-form-item label="会员号" name="memberId">
        <a-input-number style="width: 100%" v-model:value="form.memberId" placeholder="会员号：关联键" />
      </a-form-item>
      <a-form-item label="商品id" name="commodityId">
        <a-input-number style="width: 100%" v-model:value="form.commodityId" placeholder="商品id" />
      </a-form-item>
      <a-form-item label="商品编码" name="commodityCode">
        <a-input style="width: 100%" v-model:value="form.commodityCode" placeholder="商品编码（跨环境稳定的那个）" />
      </a-form-item>
      <a-form-item label="SKUid" name="skuId">
        <a-input-number style="width: 100%" v-model:value="form.skuId" placeholder="SKUid" />
      </a-form-item>
      <a-form-item label="SKU编码" name="skuCode">
        <a-input style="width: 100%" v-model:value="form.skuCode" placeholder="SKU编码" />
      </a-form-item>
      <a-form-item label="商品类型快照" name="commodityType">
        <a-input style="width: 100%" v-model:value="form.commodityType" placeholder="商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它" />
      </a-form-item>
      <a-form-item label="商品名称快照" name="commodityName">
        <a-input style="width: 100%" v-model:value="form.commodityName" placeholder="商品名称快照" />
      </a-form-item>
      <a-form-item label="兑换件数" name="quantity">
        <a-input-number style="width: 100%" v-model:value="form.quantity" placeholder="兑换件数" />
      </a-form-item>
      <a-form-item label="单件积分单价快照" name="pointsPrice">
        <a-input-number style="width: 100%" v-model:value="form.pointsPrice" placeholder="单件积分单价快照" />
      </a-form-item>
      <a-form-item label="单件现金单价快照" name="cashPrice">
        <a-input-number style="width: 100%" v-model:value="form.cashPrice" placeholder="单件现金单价快照" />
      </a-form-item>
      <a-form-item label="实付积分合计" name="payPoints">
        <a-input-number style="width: 100%" v-model:value="form.payPoints" placeholder="实付积分合计" />
      </a-form-item>
      <a-form-item label="实付现金合计" name="payCash">
        <a-input-number style="width: 100%" v-model:value="form.payCash" placeholder="实付现金合计" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-input-number
          style="width: 100%"
          v-model:value="form.status"
          placeholder="状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败"
        />
      </a-form-item>
      <a-form-item label="订单来源" name="sourceType">
        <a-input style="width: 100%" v-model:value="form.sourceType" placeholder="订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次" />
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
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { mallOrderApi } from '/@/api/business/mall/mall-order-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

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
    orderNo: undefined, //订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键
    memberId: undefined, //会员号：关联键
    commodityId: undefined, //商品id
    commodityCode: undefined, //商品编码（跨环境稳定的那个）
    skuId: undefined, //SKUid
    skuCode: undefined, //SKU编码
    commodityType: undefined, //商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它
    commodityName: undefined, //商品名称快照
    skuAttrs: undefined, //规格快照
    quantity: undefined, //兑换件数
    pointsPrice: undefined, //单件积分单价快照
    cashPrice: undefined, //单件现金单价快照
    payPoints: undefined, //实付积分合计
    payCash: undefined, //实付现金合计
    status: undefined, //状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败
    sourceType: undefined, //订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: 'id 必填' }],
    orderNo: [{ required: true, message: '订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键 必填' }],
    memberId: [{ required: true, message: '会员号：关联键 必填' }],
    commodityId: [{ required: true, message: '商品id 必填' }],
    commodityCode: [{ required: true, message: '商品编码（跨环境稳定的那个） 必填' }],
    skuId: [{ required: true, message: 'SKUid 必填' }],
    skuCode: [{ required: true, message: 'SKU编码 必填' }],
    commodityType: [{ required: true, message: '商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它 必填' }],
    commodityName: [{ required: true, message: '商品名称快照 必填' }],
    skuAttrs: [{ required: true, message: '规格快照 必填' }],
    quantity: [{ required: true, message: '兑换件数 必填' }],
    pointsPrice: [{ required: true, message: '单件积分单价快照 必填' }],
    cashPrice: [{ required: true, message: '单件现金单价快照 必填' }],
    payPoints: [{ required: true, message: '实付积分合计 必填' }],
    payCash: [{ required: true, message: '实付现金合计 必填' }],
    status: [{ required: true, message: '状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败 必填' }],
    sourceType: [{ required: true, message: '订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次 必填' }],
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
    SolvelaLoading.show();
    try {
      if (form.id) {
        await mallOrderApi.update(form);
      } else {
        await mallOrderApi.add(form);
      }
      message.success('操作成功');
      emits('reloadList');
      onClose();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
    }
  }

  defineExpose({
    show,
  });
</script>
