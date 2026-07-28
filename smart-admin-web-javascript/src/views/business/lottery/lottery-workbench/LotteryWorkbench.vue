<!--
  * 彩票配置工作台（主入口）
  *
  * 职责：
  * 1. 顶部「活动 + 玩法」两级下拉；玩法列表按活动过滤，切活动即整体换血。
  * 2. 保存收口：取 Tab1 的 getData() 提交聚合保存，成功后重新拉 detail 拿服务端权威状态。
  * 3. 有未保存改动时拦截切换与路由离开。
  *
  * 为什么是两级：活动是容器，一个活动下可以并存多个玩法
  * （「5位号 10万张」与「7位号 1000万张」就是同一个活动下的两个玩法），
  * 所以选完活动还要再选具体配哪一个。
  *
  * 上线态为什么不给可拨的开关：服务端有结构锁，运营在这里手动拨一下并不会真的改变
  * t_lottery_config.status，只会让界面与服务端判定打架 —— 抽奖工作台已经踩过这个坑。
  *
  * @Author:    alaric
  * @Date:      2026-07-27
-->
<template>
  <div>
    <!-- 顶部 -->
    <a-card :bordered="false" class="mb-4 shadow-sm" :body-style="{ padding: '16px 24px' }">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-3 flex-wrap">
          <span class="text-lg font-bold">✨ 彩票配置工作台</span>
          <a-divider type="vertical" />

          <span class="text-sm text-gray-500">当前活动</span>
          <a-select
            v-model:value="currentActivity"
            class="w-60"
            :options="activityOptions"
            :loading="activityLoading"
            placeholder="请选择彩票活动"
            show-search
            option-filter-prop="label"
            @change="onSwitchActivity"
          />

          <span class="text-sm text-gray-500 ml-2">当前玩法</span>
          <a-select
            v-model:value="currentLottery"
            class="w-64"
            :options="lotteryOptions"
            :loading="lotteryLoading"
            :disabled="!currentActivity"
            :placeholder="currentActivity ? (lotteryOptions.length ? '请选择玩法' : '该活动下暂无玩法') : '请先选择活动'"
            show-search
            option-filter-prop="label"
            @change="onSwitchLottery"
          />
          <a-button type="dashed" :disabled="!currentActivity" @click="onCreateNew">➕ 新建玩法</a-button>
        </div>

        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium text-gray-600">🛡️ 状态</span>
            <a-tooltip title="由彩票配置的上线状态决定，不能在此处手动拨动">
              <a-tag :color="statusTag.color">{{ statusTag.label }}</a-tag>
            </a-tooltip>
          </div>
          <a-popconfirm
            title="确认保存本玩法的配置？"
            ok-text="保存"
            cancel-text="再想想"
            :disabled="!currentActivity"
            @confirm="save"
          >
            <a-button type="primary" :loading="saving" :disabled="!currentActivity">
              <template #icon><CloudUploadOutlined /></template>
              提交全局配置
            </a-button>
          </a-popconfirm>
        </div>
      </div>
    </a-card>

    <!-- 主体 -->
    <a-card :bordered="false" class="shadow-sm">
      <a-empty v-if="!currentActivity && !activityLoading" class="py-16">
        <template #description>
          <span class="text-sm text-slate-400">
            {{ activityOptions.length ? '请先在上方选择一个彩票活动' : '暂无彩票类活动，请先到「活动配置」新建一个 LOTTERY 活动' }}
          </span>
        </template>
      </a-empty>

      <a-spin v-else :spinning="detailLoading">
        <a-tabs v-model:activeKey="activeTab">
          <a-tab-pane key="engine" force-render>
            <template #tab>
              <a-badge :dot="dirty" :offset="[6, 0]"><span class="font-semibold px-2">⚙️ 引擎配置与奖级映射</span></a-badge>
            </template>
            <EngineConfigPanel ref="engineRef" @change="onPanelChange" />
          </a-tab-pane>

          <a-tab-pane key="issue" force-render>
            <template #tab><span class="font-semibold px-2 text-indigo-700">📅 生命周期与开奖大盘</span></template>
            <IssueConsole
              ref="issueRef"
              :lottery-code="loadedLottery"
              :number-length="loadedNumberLength"
              :total-count="loadedTotalCount"
            />
          </a-tab-pane>
        </a-tabs>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup>
  import { computed, onMounted, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { onBeforeRouteLeave } from 'vue-router';
  import { CloudUploadOutlined } from '@ant-design/icons-vue';
  import { lotteryWorkbenchApi } from '/@/api/business/lottery/lottery-workbench/lottery-workbench-api';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { LOTTERY_STATUS_ENUM } from '/@/constants/business/lottery/lottery-const';
  import EngineConfigPanel from './components/EngineConfigPanel.vue';
  import IssueConsole from './components/IssueConsole.vue';

  const activeTab = ref('engine');
  const engineRef = ref();
  const issueRef = ref();

  // Tab2 需要玩法的发号规格来渲染售卖进度与开奖号码格式。
  // 从 detail 的返回里取，而不是让 Tab2 自己再拉一次接口 —— 两处各拉一次迟早不一致
  const loadedNumberLength = ref(5);
  const loadedTotalCount = ref(0);

  // currentXxx 是下拉的 v-model（会被 antd 立即改写），loadedXxx 是「真正加载进面板的那个」。
  // 分开存是为了在用户取消切换时能把下拉还原回去，否则下拉显示的和面板里的对不上。
  const currentActivity = ref(undefined);
  const loadedActivity = ref(undefined);
  const currentLottery = ref(undefined);
  const loadedLottery = ref(undefined);

  const activityOptions = ref([]);
  const lotteryOptions = ref([]);
  const activityLoading = ref(false);
  const lotteryLoading = ref(false);
  const detailLoading = ref(false);
  const saving = ref(false);
  const dirty = ref(false);
  const currentStatus = ref(null);
  const configured = ref(false);

  const statusTag = computed(() => {
    if (!configured.value) {
      return { color: 'blue', label: '🆕 新建中（尚未保存）' };
    }
    if (currentStatus.value === LOTTERY_STATUS_ENUM.ONLINE.value) {
      return { color: 'red', label: '🔒 售卖中（策略锁定）' };
    }
    return { color: 'default', label: '🔓 未上线（允许编辑）' };
  });

  function onPanelChange() {
    dirty.value = true;
  }

  // ---------------------------- 加载 ----------------------------

  async function loadActivities() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList('LOTTERY');
      activityOptions.value = (res.data || []).map((item) => ({
        value: item.activityCode,
        label: `${item.activityName}（${item.activityCode}）`,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      activityLoading.value = false;
    }
  }

  async function loadLotteries(activityCode) {
    lotteryOptions.value = [];
    if (!activityCode) return;
    lotteryLoading.value = true;
    try {
      const res = await lotteryWorkbenchApi.optionList(activityCode);
      lotteryOptions.value = (res.data || []).map((item) => ({
        value: item.lotteryCode,
        // 同活动下的多个玩法往往只差在发行规格上，只显示名称运营分不清点开的是哪个
        label: `${item.lotteryName}（${item.numberLength}位 / ${(item.totalCount || 0).toLocaleString()}张）`,
        status: item.status,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      lotteryLoading.value = false;
    }
  }

  async function loadDetail(activityCode, lotteryCode) {
    if (!activityCode) return;
    detailLoading.value = true;
    try {
      const res = await lotteryWorkbenchApi.detail(activityCode, lotteryCode || '');
      const vo = res.data || {};
      engineRef.value?.setData(vo);
      configured.value = !!vo.configured;
      currentStatus.value = vo.status ?? null;
      loadedNumberLength.value = vo.numberLength || 5;
      loadedTotalCount.value = vo.totalCount || 0;
      // setData 会触发子组件内部的赋值，但那不算运营的改动，回到干净态
      dirty.value = false;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      detailLoading.value = false;
    }
  }

  // ---------------------------- 切换 ----------------------------

  /**
   * 有脏改动时先确认；取消则把下拉还原。onOk/onCancel 两条路径都要给，
   * 否则用户点了取消，下拉停在新值上而面板还是旧内容
   */
  function guard(onOk, onCancel) {
    if (!dirty.value) {
      onOk();
      return;
    }
    Modal.confirm({
      title: '有未保存的改动',
      content: '切换后当前配置将丢失，确认继续？',
      okText: '放弃改动',
      cancelText: '返回',
      onOk,
      onCancel,
    });
  }

  function onSwitchActivity(activityCode) {
    const prevActivity = loadedActivity.value;
    const prevLottery = loadedLottery.value;
    guard(
      async () => {
        loadedActivity.value = activityCode;
        await loadLotteries(activityCode);
        // 换活动后默认打开第一个玩法；一个都没有就进新建态，不让主体区空着
        const first = lotteryOptions.value[0];
        currentLottery.value = first?.value;
        loadedLottery.value = first?.value;
        currentStatus.value = first?.status ?? null;
        await loadDetail(activityCode, first?.value);
      },
      () => {
        currentActivity.value = prevActivity;
        currentLottery.value = prevLottery;
      }
    );
  }

  function onSwitchLottery(lotteryCode) {
    const prev = loadedLottery.value;
    guard(
      () => {
        loadedLottery.value = lotteryCode;
        currentStatus.value = lotteryOptions.value.find((o) => o.value === lotteryCode)?.status ?? null;
        loadDetail(currentActivity.value, lotteryCode);
      },
      () => {
        currentLottery.value = prev;
      }
    );
  }

  function onCreateNew() {
    const prev = loadedLottery.value;
    guard(
      () => {
        // 玩法下拉清空表示「新建态」，detail 传空 lotteryCode 会拿到带预生成编码的空壳
        currentLottery.value = undefined;
        loadedLottery.value = undefined;
        currentStatus.value = null;
        loadDetail(currentActivity.value, '');
      },
      () => {
        currentLottery.value = prev;
      }
    );
  }

  // ---------------------------- 保存 ----------------------------

  async function save() {
    const error = engineRef.value?.validate();
    if (error) {
      message.error(error);
      return;
    }
    saving.value = true;
    try {
      const param = engineRef.value.getData();
      await lotteryWorkbenchApi.save(param);
      message.success('保存成功');
      dirty.value = false;
      // 新建的玩法要进下拉并选中；结构锁、已发号数一律以服务端为准，不拿本地状态猜
      await loadLotteries(currentActivity.value);
      currentLottery.value = param.lotteryCode;
      loadedLottery.value = param.lotteryCode;
      await loadDetail(currentActivity.value, param.lotteryCode);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  onBeforeRouteLeave(() => {
    if (!dirty.value) {
      return true;
    }
    return new Promise((resolve) => {
      Modal.confirm({
        title: '有未保存的改动',
        content: '离开页面后当前配置将丢失，确认离开？',
        okText: '放弃改动并离开',
        cancelText: '留在本页',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  });

  onMounted(async () => {
    await loadActivities();
    // 只有一个活动时直接打开，省掉一次点击；多个时让运营自己选，避免默认选错还没察觉
    const firstActivity = activityOptions.value[0];
    if (activityOptions.value.length === 1 && firstActivity) {
      currentActivity.value = firstActivity.value;
      loadedActivity.value = firstActivity.value;
      await loadLotteries(firstActivity.value);
      const firstLottery = lotteryOptions.value[0];
      currentLottery.value = firstLottery?.value;
      loadedLottery.value = firstLottery?.value;
      currentStatus.value = firstLottery?.status ?? null;
      await loadDetail(firstActivity.value, firstLottery?.value);
    }
  });
</script>
