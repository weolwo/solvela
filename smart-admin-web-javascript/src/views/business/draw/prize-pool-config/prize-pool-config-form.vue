<!--
  * 奖池配置
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 09:42:12
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="租户id" name="tenantId">
        <a-input style="width: 100%" v-model:value="form.tenantId" placeholder="租户id" />
      </a-form-item>
      <!-- 活动编码是随机码，让人手打不现实，改为从活动列表选 -->
      <a-form-item label="归属活动" name="activityCode">
        <a-select
          style="width: 100%"
          v-model:value="form.activityCode"
          :options="activityOptions"
          :loading="activityLoading"
          :disabled="!!form.id"
          show-search
          option-filter-prop="label"
          placeholder="请选择归属活动"
        />
      </a-form-item>
      <!-- 奖池编码：10位大写字母+数字，规则与活动/奖品编码一致；被坑位映射与抽奖流水引用，编辑时锁死 -->
      <a-form-item label="奖池编码" name="poolCode">
        <a-input-group compact>
          <a-input
            style="width: calc(100% - 96px)"
            v-model:value="form.poolCode"
            :disabled="!!form.id"
            :maxlength="10"
            placeholder="如 H88JHKJFNE，或点右侧生成"
            @change="onCodeInput"
          />
          <a-tooltip :title="form.id ? '奖池编码创建后不可修改' : '随机生成一个未被占用的编码'">
            <a-button style="width: 96px" :disabled="!!form.id" :loading="codeGenerating" @click="generateCode">生成</a-button>
          </a-tooltip>
        </a-input-group>
      </a-form-item>
      <a-form-item label="奖池名称" name="poolName">
        <a-input style="width: 100%" v-model:value="form.poolName" placeholder="奖池名称" />
      </a-form-item>
      <a-form-item label="消耗资产类型" name="costAssetType">
        <a-select style="width: 100%" v-model:value="form.costAssetType" :options="COST_ASSET_TYPE_OPTIONS" placeholder="请选择消耗资产类型" allowClear />
      </a-form-item>
      <!-- 选了「无消耗」这一项就没意义了，免得配出「免费抽但扣 5 积分」这种自相矛盾的奖池 -->
      <a-form-item label="消耗数值(单价)" name="costValue">
        <a-input-number
          style="width: 100%"
          v-model:value="form.costValue"
          :disabled="form.costAssetType === COST_ASSET_TYPE_ENUM.NONE.value"
          placeholder="消耗数值(单价)"
        />
      </a-form-item>
      <a-form-item label="重置周期" name="resetPeriod">
        <a-select style="width: 100%" v-model:value="form.resetPeriod" :options="RESET_PERIOD_OPTIONS" placeholder="请选择重置周期" allowClear />
      </a-form-item>
      <a-form-item label="抽奖算法" name="drawMode">
        <a-select style="width: 100%" v-model:value="form.drawMode" :options="DRAW_MODE_OPTIONS" placeholder="请选择抽奖算法" allowClear />
      </a-form-item>
      <a-form-item label="前置脚本" name="scriptId">
        <a-input style="width: 100%" v-model:value="form.scriptId" placeholder="进入该奖池的前置脚本" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-select style="width: 100%" v-model:value="form.status" :options="POOL_STATUS_OPTIONS" placeholder="请选择状态" allowClear />
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
  import { prizePoolConfigApi } from '/@/api/business/draw/prize-pool-config/prize-pool-config-api';
  import { drawWorkbenchApi } from '/@/api/business/draw/draw-workbench-api';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { regular } from '/@/constants/regular-const';
  import {
    COST_ASSET_TYPE_ENUM,
    COST_ASSET_TYPE_OPTIONS,
    DRAW_MODE_OPTIONS,
    POOL_STATUS_OPTIONS,
    RESET_PERIOD_OPTIONS,
  } from '/@/constants/business/draw/prize-pool-config/prize-pool-config-const';

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
    loadActivityOptions();
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
    activityCode: undefined, //活动编码
    poolCode: undefined, //奖池唯一编码 (如: VIP_POOL)
    poolName: undefined, //奖池名称
    costAssetType: undefined, //消耗资产类型: CREDIT(积分), TICKET(抽奖券), NONE(无消耗)
    costValue: undefined, //消耗数值(单价)
    resetPeriod: undefined, //重置周期，天，周，月，活动期间
    drawMode: undefined, //抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio)
    scriptId: undefined, //进入该奖池的前置脚本
    status: undefined, //0关闭，1开启
  };

  let form = reactive({ ...formDefault });

  const rules = {
    tenantId: [{ required: true, message: '租户id 必填' }],
    activityCode: [{ required: true, message: '归属活动 必选' }],
    poolCode: [
      { required: true, message: '奖池编码 必填' },
      { pattern: regular.bizCode, message: regular.bizCodeDesc },
    ],
    poolName: [{ required: true, message: '奖池名称 必填' }],
    costAssetType: [{ required: true, message: '消耗资产类型 必填' }],
    costValue: [{ required: true, message: '消耗数值(单价) 必填' }],
    resetPeriod: [{ required: true, message: '重置周期 必填' }],
  };

  // ------------------------ 归属活动下拉 ------------------------

  const activityOptions = ref([]);
  const activityLoading = ref(false);

  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList('DRAW');
      activityOptions.value = (res.data || []).map((item) => ({
        value: item.activityCode,
        label: `${item.activityName}（${item.activityCode}）`,
      }));
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      activityLoading.value = false;
    }
  }

  // ------------------------ 奖池编码 ------------------------

  const codeGenerating = ref(false);

  // 手输时统一转大写，省得运营因为小写被规则拦下来
  function onCodeInput() {
    if (form.poolCode) {
      form.poolCode = form.poolCode.toUpperCase();
    }
  }

  async function generateCode() {
    codeGenerating.value = true;
    try {
      const res = await drawWorkbenchApi.generatePoolCode();
      form.poolCode = res.data;
      formRef.value.clearValidate('poolCode');
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      codeGenerating.value = false;
    }
  }

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
        await prizePoolConfigApi.update(form);
      } else {
        await prizePoolConfigApi.add(form);
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
