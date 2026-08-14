<!--
  * 期号配置
  *
  * @Author:    weolwo
  * @Date:      2026-05-09 16:54:51
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="租户id" name="tenantId">
        <a-input style="width: 100%" v-model:value="form.tenantId" placeholder="租户id" />
      </a-form-item>
      <!-- 彩票编码是 t_lottery_config 的数据而不是枚举，原先绑的 SmartEnumSelect 没给 enum-name，
           下拉永远是空的 —— 改成从彩票配置列表现拉 -->
      <a-form-item label="彩票编码" name="lotteryCode">
        <a-select
          style="width: 100%"
          v-model:value="form.lotteryCode"
          :options="lotteryOptions"
          :loading="lotteryLoading"
          placeholder="请选择彩票"
          allowClear
          show-search
          option-filter-prop="label"
        />
      </a-form-item>
      <a-form-item label="期号" name="issueNo">
        <a-input style="width: 100%" v-model:value="form.issueNo" placeholder="期号" />
      </a-form-item>
      <a-form-item label="已售/已派发数量" name="soldCount">
        <a-input-number style="width: 100%" v-model:value="form.soldCount" placeholder="已售/已派发数量" />
      </a-form-item>
      <a-form-item label="售卖开始时间" name="saleStartTime">
        <a-date-picker
          :show-time="DAY_START_SHOW_TIME"
          valueFormat="YYYY-MM-DD HH:mm:ss"
          v-model:value="form.saleStartTime"
          style="width: 100%"
          placeholder="售卖开始时间"
        />
      </a-form-item>
      <a-form-item label="售卖结束时间" name="saleEndTime">
        <a-date-picker :show-time="DAY_END_SHOW_TIME" valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.saleEndTime" style="width: 100%" placeholder="售卖结束时间" />
      </a-form-item>
      <a-form-item label="开奖时间" name="settleTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.settleTime" style="width: 100%" placeholder="开奖时间" />
      </a-form-item>
      <a-form-item label="开奖号码" name="winningNumber">
        <a-input style="width: 100%" v-model:value="form.winningNumber" placeholder="开奖号码" />
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
  import { DAY_END_SHOW_TIME, DAY_START_SHOW_TIME } from '/@/constants/date-time-const';
  import { reactive, ref, nextTick } from 'vue';
  import _ from 'lodash';
  import { message } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { lotteryIssueApi } from '/@/api/business/lottery/lottery-issue/lottery-issue-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { lotteryConfigApi } from '/@/api/business/lottery/lottery-config/lottery-config-api';

  // ------------------------ 事件 ------------------------

  const emits = defineEmits(['reloadList']);

  // ------------------------ 彩票下拉 ------------------------

  const lotteryOptions = ref([]);
  const lotteryLoading = ref(false);

  // 彩票配置没有专门的 optionList 接口，这里借分页接口一次性拉回 —— 配置表本身就是几十条量级。
  // 只拉一次，弹窗反复开关不重复请求。
  async function loadLotteryOptions() {
    if (lotteryOptions.value.length > 0) {
      return;
    }
    lotteryLoading.value = true;
    try {
      const res = await lotteryConfigApi.queryPage({ pageNum: 1, pageSize: 200 });
      lotteryOptions.value = (res.data?.list || []).map((item) => ({
        value: item.lotteryCode,
        // 编码同时显示：期号是挂在编码上的，纯中文名对不上
        label: `${item.lotteryName}（${item.lotteryCode}）`,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      lotteryLoading.value = false;
    }
  }

  // ------------------------ 显示与隐藏 ------------------------
  // 是否显示
  const visibleFlag = ref(false);

  function show(rowData) {
    Object.assign(form, formDefault);
    if (rowData && !_.isEmpty(rowData)) {
      Object.assign(form, rowData);
    }
    loadLotteryOptions();
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
    issueNo: undefined, //期号
    soldCount: undefined, //已售/已派发数量
    saleStartTime: undefined, //售卖开始时间
    saleEndTime: undefined, //售卖结束时间
    settleTime: undefined, //开奖时间
    winningNumber: undefined, //开奖号码
  };

  let form = reactive({ ...formDefault });

  const rules = {
    tenantId: [{ required: true, message: '租户id 必填' }],
    lotteryCode: [{ required: true, message: '彩票编码 必填' }],
    issueNo: [{ required: true, message: '期号 必填' }],
    soldCount: [{ required: true, message: '已售/已派发数量 必填' }],
    saleStartTime: [{ required: true, message: '售卖开始时间 必填' }],
    saleEndTime: [{ required: true, message: '售卖结束时间 必填' }],
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
        await lotteryIssueApi.update(form);
      } else {
        await lotteryIssueApi.add(form);
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
