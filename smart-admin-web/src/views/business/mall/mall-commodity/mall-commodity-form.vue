<!--
  * 商城-商品主表
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:29:59
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
        <a-form-item label="商品编码：10位大写字母+数字，全局唯一，创建后不可改"  name="commodityCode">
          <a-input style="width: 100%" v-model:value="form.commodityCode" placeholder="商品编码：10位大写字母+数字，全局唯一，创建后不可改" />
        </a-form-item>
        <a-form-item label="分类id"  name="categoryId">
          <a-input-number style="width: 100%" v-model:value="form.categoryId" placeholder="分类id" />
        </a-form-item>
        <a-form-item label="商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账)"  name="commodityType">
          <a-input style="width: 100%" v-model:value="form.commodityType" placeholder="商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账)" />
        </a-form-item>
        <a-form-item label="商品名称"  name="commodityName">
          <a-input style="width: 100%" v-model:value="form.commodityName" placeholder="商品名称" />
        </a-form-item>
        <a-form-item label="封面主图 file_id（建议 800x800）"  name="coverFileId">
          <a-input-number style="width: 100%" v-model:value="form.coverFileId" placeholder="封面主图 file_id（建议 800x800）" />
        </a-form-item>
        <a-form-item label="支付方式：1-纯积分, 2-积分+现金"  name="payType">
          <a-input-number style="width: 100%" v-model:value="form.payType" placeholder="支付方式：1-纯积分, 2-积分+现金" />
        </a-form-item>
        <a-form-item label="划线原价：仅前端展示「价值￥199」，纯积分商品可留 0"  name="originalPrice">
          <a-input-number style="width: 100%" v-model:value="form.originalPrice" placeholder="划线原价：仅前端展示「价值￥199」，纯积分商品可留 0" />
        </a-form-item>
        <a-form-item label="基准兑换积分"  name="pointsPrice">
          <a-input-number style="width: 100%" v-model:value="form.pointsPrice" placeholder="基准兑换积分" />
        </a-form-item>
        <a-form-item label="基准兑换现金：pay_type=1 时恒为 0"  name="cashPrice">
          <a-input-number style="width: 100%" v-model:value="form.cashPrice" placeholder="基准兑换现金：pay_type=1 时恒为 0" />
        </a-form-item>
        <a-form-item label="限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月"  name="limitPeriod">
          <a-input style="width: 100%" v-model:value="form.limitPeriod" placeholder="限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月" />
        </a-form-item>
        <a-form-item label="周期内单会员限兑件数：0-不限制"  name="limitCount">
          <a-input-number style="width: 100%" v-model:value="form.limitCount" placeholder="周期内单会员限兑件数：0-不限制" />
        </a-form-item>
        <a-form-item label="上架开始时间：默认值代表不限。不是秒杀场次"  name="startTime">
          <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.startTime" style="width: 100%" placeholder="上架开始时间：默认值代表不限。不是秒杀场次" />
        </a-form-item>
        <a-form-item label="上架结束时间：默认值代表不限。不是秒杀场次"  name="endTime">
          <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.endTime" style="width: 100%" placeholder="上架结束时间：默认值代表不限。不是秒杀场次" />
        </a-form-item>
        <a-form-item label="状态：0-下架, 1-上架, 2-草稿。新建默认落草稿"  name="status">
          <a-input-number style="width: 100%" v-model:value="form.status" placeholder="状态：0-下架, 1-上架, 2-草稿。新建默认落草稿" />
        </a-form-item>
        <a-form-item label="是否首页推荐：0-否, 1-是"  name="isHome">
          <a-input-number style="width: 100%" v-model:value="form.isHome" placeholder="是否首页推荐：0-否, 1-是" />
        </a-form-item>
        <a-form-item label="排序权重：从小到大"  name="sort">
          <a-input-number style="width: 100%" v-model:value="form.sort" placeholder="排序权重：从小到大" />
        </a-form-item>
        <a-form-item label="累计已兑件数（各SKU之和的冗余，用于列表按热销排序）"  name="soldCount">
          <a-input-number style="width: 100%" v-model:value="form.soldCount" placeholder="累计已兑件数（各SKU之和的冗余，用于列表按热销排序）" />
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
  import { mallCommodityApi } from '/@/api/business/mall/mall-commodity-api';
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
      commodityCode: undefined, //商品编码：10位大写字母+数字，全局唯一，创建后不可改
      categoryId: undefined, //分类id
      commodityType: undefined, //商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账)
      commodityName: undefined, //商品名称
      coverFileId: undefined, //封面主图 file_id（建议 800x800）
      payType: undefined, //支付方式：1-纯积分, 2-积分+现金
      originalPrice: undefined, //划线原价：仅前端展示「价值￥199」，纯积分商品可留 0
      pointsPrice: undefined, //基准兑换积分
      cashPrice: undefined, //基准兑换现金：pay_type=1 时恒为 0
      limitPeriod: undefined, //限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月
      limitCount: undefined, //周期内单会员限兑件数：0-不限制
      startTime: undefined, //上架开始时间：默认值代表不限。不是秒杀场次
      endTime: undefined, //上架结束时间：默认值代表不限。不是秒杀场次
      status: undefined, //状态：0-下架, 1-上架, 2-草稿。新建默认落草稿
      isHome: undefined, //是否首页推荐：0-否, 1-是
      sort: undefined, //排序权重：从小到大
      soldCount: undefined, //累计已兑件数（各SKU之和的冗余，用于列表按热销排序）
  };

  let form = reactive({ ...formDefault });

  const rules = {
      id: [{ required: true, message: 'id 必填' }],
      commodityCode: [{ required: true, message: '商品编码：10位大写字母+数字，全局唯一，创建后不可改 必填' }],
      categoryId: [{ required: true, message: '分类id 必填' }],
      commodityType: [{ required: true, message: '商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账) 必填' }],
      commodityName: [{ required: true, message: '商品名称 必填' }],
      coverFileId: [{ required: true, message: '封面主图 file_id（建议 800x800） 必填' }],
      payType: [{ required: true, message: '支付方式：1-纯积分, 2-积分+现金 必填' }],
      originalPrice: [{ required: true, message: '划线原价：仅前端展示「价值￥199」，纯积分商品可留 0 必填' }],
      pointsPrice: [{ required: true, message: '基准兑换积分 必填' }],
      cashPrice: [{ required: true, message: '基准兑换现金：pay_type=1 时恒为 0 必填' }],
      limitPeriod: [{ required: true, message: '限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月 必填' }],
      limitCount: [{ required: true, message: '周期内单会员限兑件数：0-不限制 必填' }],
      startTime: [{ required: true, message: '上架开始时间：默认值代表不限。不是秒杀场次 必填' }],
      endTime: [{ required: true, message: '上架结束时间：默认值代表不限。不是秒杀场次 必填' }],
      status: [{ required: true, message: '状态：0-下架, 1-上架, 2-草稿。新建默认落草稿 必填' }],
      isHome: [{ required: true, message: '是否首页推荐：0-否, 1-是 必填' }],
      sort: [{ required: true, message: '排序权重：从小到大 必填' }],
      soldCount: [{ required: true, message: '累计已兑件数（各SKU之和的冗余，用于列表按热销排序） 必填' }],
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
        await mallCommodityApi.update(form);
      } else {
        await mallCommodityApi.add(form);
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
