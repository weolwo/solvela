<!--
  * 奖池配置
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 09:42:12
  * @Copyright  weolwo
-->
<template>
  <a-modal title="编辑奖池" :width="720" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <!--
        ⚠️ 本表单只负责奖池<b>自身</b>的两个字段：名称与限领重置周期。
        新建奖池已改为去抽奖工作台 —— 只有奖池行、没有坑位映射的池，
        抽奖时快照构造直接抛「奖池快照不能为空」，是个建了就用不了的空壳。
        所以这里不再有新增态，归属活动与奖池编码一律只读展示。
      -->
      <a-form-item label="归属活动">
        <span class="readonly-field">{{ form.activityName || form.activityCode }}</span>
      </a-form-item>
      <!-- 奖池编码被坑位映射与抽奖流水引用，改了会让历史流水指向一个不存在的池 -->
      <a-form-item label="奖池编码">
        <span class="readonly-field">
          <span class="font-mono">{{ form.poolCode }}</span>
          <a-tag color="default" class="ml-2">创建后不可修改</a-tag>
        </span>
      </a-form-item>
      <a-form-item label="奖池名称" name="poolName">
        <a-input style="width: 100%" v-model:value="form.poolName" placeholder="奖池名称" />
      </a-form-item>
      <!--
        重置周期是这个表单上唯一真正生效的业务开关：它决定奖项的「单人限领次数」
        按什么粒度归零（每天/每周/每月/整个活动期间）。
        v3.64 之前它是个装饰字段，运行态压根不读；现已由 DrawPeriodResolver 落到
        限领计数的 Redis key 上，换周期即换 key，计数天然归零。
      -->
      <a-form-item label="限领重置周期" name="resetPeriod">
        <a-select style="width: 100%" v-model:value="form.resetPeriod" :options="RESET_PERIOD_OPTIONS" placeholder="请选择重置周期" />
        <div class="form-hint">
          决定奖项的「单人限领次数」多久归零一次。选「活动期间」表示整个活动内累计不重置。
          限领次数本身在奖项上配（工作台 → 奖项库）。
        </div>
      </a-form-item>

      <!--
        ⚠️ 抽奖算法(draw_mode) 与 前置脚本(script_id) 已从表单摘除。
        全仓库 grep 确认：后端从未读取过这两个字段 —— 选「按库存比例」照样按概率抽，
        填了脚本 id 也不会有任何前置校验。留在界面上只会让运营配一个假开关。
        数据库列暂时保留（见 v3.64.0.sql），将来真做出来了再放开。

        「状态」也已摘除：奖池开关的唯一入口是列表页的「禁用 / 启用」按钮。
        放两个入口意味着同一件事有两种做法，而其中一种（表单里的下拉）
        既没有二次确认、也不走并发闸门 —— 两个人同时改会互相覆盖。
      -->

      <!--
        坑位与概率不在本表单管辖内，但运营在这里最容易想到「顺手把奖项也改了」，
        所以把去处直接放在手边，省得他退出去再找入口
      -->
      <a-alert type="info" show-icon class="mt-1">
        <template #message><span class="text-xs">坑位、概率、奖项库存请到「抽奖工作台」配置</span></template>
        <template #description>
          <a-button type="link" size="small" class="p-0" :disabled="!form.activityCode" @click="gotoWorkbench">
            去工作台配坑位 →
          </a-button>
        </template>
      </a-alert>
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
  import { useRouter } from 'vue-router';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { prizePoolConfigApi } from '/@/api/business/draw/prize-pool-config/prize-pool-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { RESET_PERIOD_OPTIONS } from '/@/constants/business/draw/prize-pool-config/prize-pool-config-const';

  const router = useRouter();

  // ------------------------ 事件 ------------------------

  const emits = defineEmits(['reloadList']);

  // ------------------------ 显示与隐藏 ------------------------
  // 是否显示
  const visibleFlag = ref(false);

  /**
   * 只有编辑态。新建奖池已改为去抽奖工作台，所以这里必然带着一行数据进来。
   *
   * 行数据里还有 slotCount / issueList 等一堆派生字段，只挑表单认识的进来 ——
   * 整个 Object.assign 会把它们一起 POST 回服务端。
   */
  function show(rowData) {
    Object.assign(form, formDefault);
    if (rowData && !_.isEmpty(rowData)) {
      Object.assign(form, _.pick(rowData, Object.keys(formDefault)));
      // 只读展示用，不参与提交
      form.activityName = rowData.activityName;
    }
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

  /*
   * 字段与后端 PrizePoolConfigUpdateForm 一一对应。
   * activityCode / poolCode 只读展示，但仍留在这里：更新接口按 id 定位，
   * 它们不参与写入，去掉只会让回显缺字段。
   *
   * status 刻意不在表单里：奖池开关的唯一入口是列表页的「禁用 / 启用」按钮，
   * 那条路径有二次确认和并发闸门（WHERE status = from），表单里的下拉两样都没有。
   */
  const formDefault = {
    id: undefined, //id
    activityCode: undefined, //活动编码，只读
    poolCode: undefined, //奖池编码，只读（被坑位映射与抽奖流水引用）
    poolName: undefined, //奖池名称
    // 重置周期：奖项「单人限领次数」的归零粒度，运行态由 DrawPeriodResolver 消费
    resetPeriod: undefined,
    // drawMode / scriptId 刻意不提交：后端从未读取过，是两个假开关
    activityName: undefined, //只读展示用，不提交
  };

  let form = reactive({ ...formDefault });

  const rules = {
    poolName: [{ required: true, message: '奖池名称 必填' }],
    resetPeriod: [{ required: true, message: '重置周期 必填' }],
  };

  // ------------------------ 跳转工作台 ------------------------

  /**
   * 坑位与概率是工作台的地盘。运营在编辑奖池时最容易顺手想改奖项，
   * 把去处放在手边省得他退出去再找入口。
   */
  function gotoWorkbench() {
    visibleFlag.value = false;
    router.push({
      path: '/business/draw/draw-workbench',
      query: { activityCode: form.activityCode },
    });
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

  // 只有编辑：新建奖池走抽奖工作台
  async function save() {
    SmartLoading.show();
    try {
      // 只送更新接口认识的字段，派生字段与只读展示字段一律不传
      await prizePoolConfigApi.update({
        id: form.id,
        poolName: form.poolName,
        resetPeriod: form.resetPeriod,
      });
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

<style lang="less" scoped>
  .readonly-field {
    display: inline-flex;
    align-items: center;
    color: #0f172a;
  }

  .form-hint {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.5;
    color: #94a3b8;
  }
</style>
