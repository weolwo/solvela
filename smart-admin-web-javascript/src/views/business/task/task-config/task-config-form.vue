<!--
  * 任务配置表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 20:55:10
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="id" name="id">
        <a-input-number style="width: 100%" v-model:value="form.id" placeholder="id" />
      </a-form-item>
      <a-form-item label="活动编码" name="activityCode">
        <a-input style="width: 100%" v-model:value="form.activityCode" placeholder="活动编码" />
      </a-form-item>
      <a-form-item label="租户ID" name="tenantId">
        <a-input style="width: 100%" v-model:value="form.tenantId" placeholder="租户ID" />
      </a-form-item>
      <a-form-item label="任务名称" name="taskName">
        <a-input style="width: 100%" v-model:value="form.taskName" placeholder="任务名称" />
      </a-form-item>
      <a-form-item label="模板Code" name="templateCode">
        <a-input style="width: 100%" v-model:value="form.templateCode" placeholder="模板Code" />
      </a-form-item>
      <a-form-item label="触发事件" name="triggerEvent">
        <a-input
          style="width: 100%"
          v-model:value="form.triggerEvent"
          placeholder="触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义)"
        />
      </a-form-item>
      <a-form-item label="任务分组" name="taskGroup">
        <a-input style="width: 100%" v-model:value="form.taskGroup" placeholder="任务分组：NEWBIE(新手), DAILY(日常), PROMO(大促), VIP(会员专属)" />
      </a-form-item>
      <a-form-item label="目标人群" name="targetAudience">
        <a-input style="width: 100%" v-model:value="form.targetAudience" placeholder="目标人群：ALL(全部), NEW_MEMBER(新会员), OLD_MEMBER(老会员)" />
      </a-form-item>
      <a-form-item label="参与频次" name="limitType">
        <a-input
          style="width: 100%"
          v-model:value="form.limitType"
          placeholder="参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制)"
        />
      </a-form-item>
      <a-form-item label="限制次数" name="limitCount">
        <a-input-number style="width: 100%" v-model:value="form.limitCount" placeholder="配合频次类型，限制次数" />
      </a-form-item>
      <a-form-item label="排序权重" name="sortWeight">
        <a-input-number style="width: 100%" v-model:value="form.sortWeight" placeholder="排序权重，越大越靠前" />
      </a-form-item>
      <a-form-item label="跳转地址" name="actionUrl">
        <a-input style="width: 100%" v-model:value="form.actionUrl" placeholder="跳转地址" />
      </a-form-item>
      <a-form-item label="任务状态" name="status">
        <a-input-number style="width: 100%" v-model:value="form.status" placeholder="任务状态 1-待生效, 2-生效中, 3-已下线" />
      </a-form-item>
      <a-form-item label="开始时间" name="startTime">
        <a-date-picker :show-time="DAY_START_SHOW_TIME" valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.startTime" style="width: 100%" placeholder="开始时间" />
      </a-form-item>
      <a-form-item label="结束时间" name="endTime">
        <a-date-picker :show-time="DAY_END_SHOW_TIME" valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.endTime" style="width: 100%" placeholder="结束时间" />
      </a-form-item>
      <a-form-item label="创建人" name="createBy">
        <a-input style="width: 100%" v-model:value="form.createBy" placeholder="创建人" />
      </a-form-item>
      <a-form-item label="创建时间" name="createTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.createTime" style="width: 100%" placeholder="创建时间" />
      </a-form-item>
      <a-form-item label="更新人" name="updateBy">
        <a-input style="width: 100%" v-model:value="form.updateBy" placeholder="更新人" />
      </a-form-item>
      <a-form-item label="更新时间" name="updateTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.updateTime" style="width: 100%" placeholder="更新时间" />
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
  import { taskConfigApi } from '/@/api/business/task/task-config/task-config-api';
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
    activityCode: undefined, //活动编码
    tenantId: undefined, //租户ID
    taskName: undefined, //任务名称
    templateCode: undefined, //模板Code
    triggerEvent: undefined, //触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义)
    taskGroup: undefined, //任务分组：NEWBIE(新手), DAILY(日常), PROMO(大促), VIP(会员专属)
    targetAudience: undefined, //目标人群：ALL(全部), NEW_MEMBER(新会员), OLD_MEMBER(老会员)
    limitType: undefined, //参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制)
    limitCount: undefined, //配合频次类型，限制次数
    ruleConfig: undefined, //规则配置
    sortWeight: undefined, //排序权重，越大越靠前
    actionUrl: undefined, //跳转地址
    uiConfig: undefined, //展示UI(图标/角标等)
    status: undefined, //任务状态 1-待生效, 2-生效中, 3-已下线
    startTime: undefined, //开始时间
    endTime: undefined, //结束时间
    createBy: undefined, //创建人
    createTime: undefined, //创建时间
    updateBy: undefined, //更新人
    updateTime: undefined, //更新时间
  };

  let form = reactive({ ...formDefault });

  const rules = {
    id: [{ required: true, message: 'id 必填' }],
    activityCode: [{ required: true, message: '活动编码 必填' }],
    tenantId: [{ required: true, message: '租户ID 必填' }],
    taskName: [{ required: true, message: '任务名称 必填' }],
    templateCode: [{ required: true, message: '模板Code 必填' }],
    triggerEvent: [
      { required: true, message: '触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义) 必填' },
    ],
    limitType: [{ required: true, message: '参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制) 必填' }],
    limitCount: [{ required: true, message: '配合频次类型，限制次数 必填' }],
    ruleConfig: [{ required: true, message: '规则配置 必填' }],
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
        await taskConfigApi.update(form);
      } else {
        await taskConfigApi.add(form);
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
