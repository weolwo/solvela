<!--
  * 活动配置
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 19:31:49
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <!-- 活动编码：10位大写字母+数字，可手输也可一键生成；创建后即被奖品/奖池/流水引用，编辑时锁死 -->
      <a-form-item label="活动编码" name="activityCode">
        <a-input-group compact>
          <a-input
            style="width: calc(100% - 96px)"
            v-model:value="form.activityCode"
            :disabled="!!form.id"
            :maxlength="10"
            placeholder="如 H88JHKJFNE，或点右侧生成"
            @change="onCodeInput"
          />
          <a-tooltip :title="form.id ? '活动编码创建后不可修改' : '随机生成一个未被占用的编码'">
            <a-button style="width: 96px" :disabled="!!form.id" :loading="codeGenerating" @click="generateCode">生成</a-button>
          </a-tooltip>
        </a-input-group>
      </a-form-item>
      <a-form-item label="活动名称" name="activityName">
        <a-input style="width: 100%" v-model:value="form.activityName" placeholder="活动名称" />
      </a-form-item>
      <!--
        活动类型必须是枚举下拉，不能是自由文本框：
        三个工作台的活动下拉是 optionList?activityType=DRAW 精确匹配的，
        手打成 draw / 抽奖 之类，这个活动就在所有工作台里凭空消失，而且不报任何错。
        编辑态一并禁用 —— 类型创建后不可改（改了下游配置全变孤儿数据），
        服务端 UpdateForm 里压根没有这个字段，这里放开也存不进去。
      -->
      <a-form-item label="活动类型" name="activityType">
        <a-select
          style="width: 100%"
          v-model:value="form.activityType"
          :disabled="!!form.id"
          placeholder="请选择活动类型"
          :options="activityTypeOptions"
        />
        <div v-if="form.id" class="text-xs text-slate-400 mt-1">活动类型创建后不可修改；基础活动可在列表点「升级玩法」。</div>
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-select style="width: 100%" v-model:value="form.status" placeholder="请选择状态" :options="activityStatusOptions" />
      </a-form-item>
      <a-form-item label="活动开始时间" name="startTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.startTime" style="width: 100%" placeholder="活动开始时间" />
      </a-form-item>
      <a-form-item label="活动结束时间" name="endTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.endTime" style="width: 100%" placeholder="活动结束时间" />
      </a-form-item>
      <a-form-item label="规则脚本id" name="scriptId">
        <a-input style="width: 100%" v-model:value="form.scriptId" placeholder="规则脚本id" />
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
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { regular } from '/@/constants/regular-const';
  import { ACTIVITY_STATUS_LIST, ACTIVITY_TYPE_LIST } from '/@/constants/business/activity/activity-config/activity-config-const';

  // 取值对齐后端 ActivityTypeEnum / t_activity_config.status，唯一真源在常量文件（铁律 3）
  const activityTypeOptions = ACTIVITY_TYPE_LIST.map((t) => ({ value: t.value, label: `${t.icon} ${t.desc}` }));
  const activityStatusOptions = ACTIVITY_STATUS_LIST.map((s) => ({ value: s.value, label: s.desc }));

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
    activityCode: undefined, //活动编码
    activityName: undefined, //活动名称
    activityType: undefined, //活动类型
    status: undefined, //状态：1-启用, 2-禁用（不传则落库取默认值，列表上显示为「禁用」）
    startTime: undefined, //活动开始时间
    endTime: undefined, //活动结束时间
    scriptId: undefined, //规则脚本id
  };

  let form = reactive({ ...formDefault });

  const rules = {
    activityCode: [
      { required: true, message: '活动编码 必填' },
      { pattern: regular.bizCode, message: regular.bizCodeDesc },
    ],
    activityName: [{ required: true, message: '活动名称 必填' }],
    startTime: [{ required: true, message: '活动开始时间 必填' }],
    endTime: [{ required: true, message: '活动结束时间 必填' }],
  };

  // ------------------------ 活动编码 ------------------------

  const codeGenerating = ref(false);

  // 手输时统一转大写，省得运营因为小写被规则拦下来
  function onCodeInput() {
    if (form.activityCode) {
      form.activityCode = form.activityCode.toUpperCase();
    }
  }

  async function generateCode() {
    codeGenerating.value = true;
    try {
      const res = await activityConfigApi.generateCode();
      form.activityCode = res.data;
      formRef.value.clearValidate('activityCode');
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
        await activityConfigApi.update(form);
      } else {
        await activityConfigApi.add(form);
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
