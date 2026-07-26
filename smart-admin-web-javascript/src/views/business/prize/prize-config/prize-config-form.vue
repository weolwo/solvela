<!--
  * 奖品配置表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 20:20:44
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
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
      <a-form-item label="优惠配置ID" name="promotionConfigId">
        <a-input-number style="width: 100%" v-model:value="form.promotionConfigId" placeholder="优惠配置ID" />
      </a-form-item>
      <a-form-item label="资产类型" name="prizeType">
        <a-input style="width: 100%" v-model:value="form.prizeType" placeholder="资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM" />
      </a-form-item>
      <a-form-item label="奖品名称" name="prizeName">
        <a-input style="width: 100%" v-model:value="form.prizeName" placeholder="奖品名称" />
      </a-form-item>
      <!-- 奖品编码：10位大写字母+数字，可手输也可一键生成；创建后被奖池物资/流水引用，编辑时锁死 -->
      <a-form-item label="奖品编码" name="prizeCode">
        <a-input-group compact>
          <a-input
            style="width: calc(100% - 96px)"
            v-model:value="form.prizeCode"
            :disabled="!!form.id"
            :maxlength="10"
            placeholder="如 H88JHKJFNE，或点右侧生成"
            @change="onCodeInput"
          />
          <a-tooltip :title="form.id ? '奖品编码创建后不可修改' : '随机生成一个未被占用的编码'">
            <a-button style="width: 96px" :disabled="!!form.id" :loading="codeGenerating" @click="generateCode">生成</a-button>
          </a-tooltip>
        </a-input-group>
      </a-form-item>
      <a-form-item label="奖品级别" name="prizeLevel">
        <a-input-number style="width: 100%" v-model:value="form.prizeLevel" placeholder="奖品级别" />
      </a-form-item>
      <a-form-item label="奖励价值" name="prizeValue">
        <a-input-number style="width: 100%" v-model:value="form.prizeValue" placeholder="奖励价值" />
      </a-form-item>
      <a-form-item label="审批模式" name="approveMode">
        <a-input-number style="width: 100%" v-model:value="form.approveMode" placeholder="审批模式：0-自动免审, 1-人工审批" />
      </a-form-item>
      <a-form-item label="排序权重" name="sortWeight">
        <a-input-number style="width: 100%" v-model:value="form.sortWeight" placeholder="排序权重" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-input-number style="width: 100%" v-model:value="form.status" placeholder="状态：0-停用, 1-启用" />
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
  import { prizeConfigApi } from '/@/api/business/prize/prize-config/prize-config-api';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { regular } from '/@/constants/regular-const';

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
    activityCode: undefined, //活动编码
    promotionConfigId: undefined, //优惠配置ID
    prizeType: undefined, //资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM
    prizeName: undefined, //奖品名称
    prizeCode: undefined, //奖品编码
    prizeLevel: undefined, //奖品级别
    prizeValue: undefined, //奖励价值
    approveMode: undefined, //审批模式：0-自动免审, 1-人工审批
    sortWeight: undefined, //排序权重
    ext: undefined, //扩展信息：如奖品图片URL、跳转链接等
    status: undefined, //状态：0-停用, 1-启用
  };

  let form = reactive({ ...formDefault });

  const rules = {
    activityCode: [{ required: true, message: '归属活动 必选' }],
    prizeCode: [
      { required: true, message: '奖品编码 必填' },
      { pattern: regular.bizCode, message: regular.bizCodeDesc },
    ],
    approveMode: [{ required: true, message: '审批模式：0-自动免审, 1-人工审批 必填' }],
  };

  // ------------------------ 归属活动下拉 ------------------------

  const activityOptions = ref([]);
  const activityLoading = ref(false);

  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList();
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

  // ------------------------ 奖品编码 ------------------------

  const codeGenerating = ref(false);

  // 手输时统一转大写，省得运营因为小写被规则拦下来
  function onCodeInput() {
    if (form.prizeCode) {
      form.prizeCode = form.prizeCode.toUpperCase();
    }
  }

  async function generateCode() {
    codeGenerating.value = true;
    try {
      const res = await prizeConfigApi.generateCode();
      form.prizeCode = res.data;
      formRef.value.clearValidate('prizeCode');
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
        await prizeConfigApi.update(form);
      } else {
        await prizeConfigApi.add(form);
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
