<!--
  * 抽奖工作台 · 玩法配置
  *
  * 抽奖的三层是：活动 → 抽奖配置 → N 个奖池。本面板管中间那一层。
  * 与彩票工作台把 lotteryCode/numberLength/totalCount 收进同一个表单是同一个形状 ——
  * 配一个抽奖活动本来就该在一个地方配完。
  *
  * 🔴 抽奖编码创建后冻结：脚本挂载点「抽奖配置 - 玩法编排」引用的就是它。
  *    改了等于把已有挂载指向一个不存在的对象，而挂载表不会跟着动 ——
  *    表现是活动照常上线、一抽就报「没挂脚本」。服务端也会忽略前端传来的改动。
  *
  * @Date 2026-08-31
-->
<template>
  <div class="draw-config-panel">
    <a-alert v-if="!configured" type="warning" show-icon class="mb-4">
      <template #message>这个活动还没有抽奖配置</template>
      <template #description>
        编码已经替你生成好了，保存时一并创建。没有它的话抽奖走不起来：脚本没地方挂，单人限领也永远不会重置。
      </template>
    </a-alert>

    <a-form :model="form" :label-col="{ span: 5 }" :wrapper-col="{ span: 14 }">
      <a-form-item label="抽奖编码">
        <span class="readonly-field">
          <span class="font-mono">{{ form.drawCode }}</span>
          <a-tag v-if="codeLocked" color="default" class="ml-2">创建后不可修改</a-tag>
          <a-tag v-else color="blue" class="ml-2">保存时创建</a-tag>
        </span>
        <div class="form-hint">脚本挂载点「抽奖配置 - 玩法编排」填的就是它。</div>
      </a-form-item>

      <a-form-item label="抽奖名称">
        <a-input v-model:value="form.drawName" placeholder="给人看的名字" @change="emitChange" />
      </a-form-item>

      <a-form-item label="重置周期">
        <a-select v-model:value="form.resetPeriod" :options="RESET_PERIOD_OPTIONS" @change="emitChange" />
        <div class="form-hint">
          决定<b>两件事</b>，由同一个开关驱动：<br />
          ① 奖项「单人限领次数」多久归零；<br />
          ② 统计「本轮已经抽了几次」时从哪天起算（脚本里的 <code>draw_countDrawn()</code>）。<br />
          选「活动期间」表示整个活动内累计不重置。
        </div>
      </a-form-item>

      <a-form-item label="抽奖算法">
        <a-select v-model:value="form.drawMode" :options="DRAW_MODE_SELECTABLE_OPTIONS" @change="emitChange" />
        <div class="form-hint warn-hint">⚠️ 「按库存比例」还没实现，所以是禁选的 —— 不是藏起来，是让你知道它存在但还不能用。</div>
      </a-form-item>

      <a-form-item label="抽奖开关">
        <a-select v-model:value="form.drawStatus" :options="STATUS_OPTIONS" @change="emitChange" />
        <div class="form-hint">
          关闭后该活动的抽奖请求会被<b>直接拒绝</b>，脚本不会执行。
          它与「活动上线状态」是两件事：活动可以在线，但抽奖这个玩法先关着。
        </div>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { RESET_PERIOD_OPTIONS, DRAW_MODE_SELECTABLE_OPTIONS, DRAW_MODE_ENUM } from '/@/constants/business/draw/prize-pool-config-const';

  const emit = defineEmits(['change']);

  const STATUS_OPTIONS = [
    { value: 1, label: '开启' },
    { value: 0, label: '关闭' },
  ];

  const configured = ref(false);
  const codeLocked = ref(false);

  const form = reactive({
    drawCode: '',
    drawName: '',
    resetPeriod: 'DAY',
    drawMode: DRAW_MODE_ENUM.PROBABILITY.value,
    drawStatus: 1,
  });

  /** 由工作台回显时调用。detail 里没有的字段一律走默认值，不留 undefined */
  function setData(detail) {
    configured.value = Boolean(detail?.drawConfigured);
    codeLocked.value = Boolean(detail?.drawCodeLocked);
    form.drawCode = detail?.drawCode || '';
    form.drawName = detail?.drawName || '';
    form.resetPeriod = detail?.resetPeriod || 'DAY';
    form.drawMode = detail?.drawMode ?? DRAW_MODE_ENUM.PROBABILITY.value;
    // 新建时后端给的是 null，默认开启 —— 配完就能用，不用再想起来去开一次
    form.drawStatus = detail?.drawStatus ?? 1;
  }

  function getData() {
    return { ...form };
  }

  function emitChange() {
    emit('change');
  }

  defineExpose({ setData, getData });
</script>

<style scoped lang="less">
  .draw-config-panel {
    max-width: 860px;
    padding-top: 8px;
  }

  .readonly-field {
    color: rgba(0, 0, 0, 0.85);
  }

  .font-mono {
    font-family: Consolas, Monaco, monospace;
  }

  .form-hint {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
    line-height: 20px;
    margin-top: 2px;
  }

  .warn-hint {
    color: #d46b08;
  }

  .mb-4 {
    margin-bottom: 16px;
  }

  .ml-2 {
    margin-left: 8px;
  }
</style>
