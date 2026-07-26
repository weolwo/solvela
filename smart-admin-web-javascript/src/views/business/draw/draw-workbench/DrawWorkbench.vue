<!--
  * 抽奖配置工作台（主入口）
  *
  * 职责：
  * 1. 顶部持有全局 isOnline（防篡改锁）开关，下发给两个 Tab 子组件。
  * 2. 左右联动：Tab1 物资库改动 -> 实时把最新库存喂给 Tab2，保证沙盘模拟器读到最新库存。
  * 3. 统一保存收口：分别取两个 Tab 的 getData()，组装主子表超级 JSON。
  *
  * @Author:    alaric
  * @Date:      2026-07-20
-->
<template>
  <div>
    <!-- 顶部：活动信息 + 上线态 + 保存发布 -->
    <a-card :bordered="false" class="mb-4 shadow-sm" :body-style="{ padding: '16px 24px' }">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-4">
          <span class="text-lg font-bold">🎯 抽奖配置工作台</span>
          <a-divider type="vertical" />
          <span class="text-sm text-gray-500">当前活动</span>
          <a-select
            v-model:value="currentActivity"
            class="w-64"
            :options="activityOptions"
            :loading="activityLoading"
            placeholder="请选择抽奖活动"
            show-search
            option-filter-prop="label"
          />
        </div>

        <div class="flex items-center gap-4">
          <!-- 上线态由活动真实状态决定，不给运营在此处手动拨动，避免与服务端结构锁不一致 -->
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium text-gray-600">🛡️ 上线锁</span>
            <a-tooltip title="由活动状态决定，如需变更请到「活动配置」页上下线">
              <a-tag :color="ACTIVITY_STATUS_MAP[activityStatus]?.color">{{ activityStatusLabel }}</a-tag>
            </a-tooltip>
          </div>
          <a-popconfirm
            title="确认保存并发布本抽奖活动？"
            ok-text="保存并发布"
            cancel-text="再想想"
            :disabled="!currentActivity"
            @confirm="saveAndPublish"
          >
            <a-button type="primary" :loading="saving" :disabled="!currentActivity">
              <template #icon><CloudUploadOutlined /></template>
              保存并发布抽奖活动
            </a-button>
          </a-popconfirm>
        </div>
      </div>
    </a-card>

    <!-- 主体：两个 Tab -->
    <a-card :bordered="false" class="shadow-sm">
      <a-spin :spinning="detailLoading">
        <a-tabs v-model:activeKey="activeTab">
          <a-tab-pane key="prizeItem" force-render>
            <template #tab>
              <a-badge :dot="tab1Dirty" :offset="[6, 0]"><span>📦 奖项物资库</span></a-badge>
            </template>
            <PrizeItemLibrary ref="tab1Ref" :is-online="isOnline" :activity-code="currentActivity" @change="onTab1Change" />
          </a-tab-pane>

          <a-tab-pane key="probability" force-render>
            <template #tab>
              <a-badge :dot="tab2Dirty" :offset="[6, 0]"><span>🎲 奖池与概率配置</span></a-badge>
            </template>
            <PoolProbabilityEngine ref="tab2Ref" :is-online="isOnline" :prize-items="prizeItems" @change="onTab2Change" />
          </a-tab-pane>
        </a-tabs>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup>
  import { computed, nextTick, onMounted, ref, watch } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { onBeforeRouteLeave } from 'vue-router';
  import { CloudUploadOutlined } from '@ant-design/icons-vue';
  import { drawWorkbenchApi } from '/@/api/business/draw/draw-workbench-api';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import PrizeItemLibrary from './components/PrizeItemLibrary.vue';
  import PoolProbabilityEngine from './components/PoolProbabilityEngine.vue';

  // ---------------------------- 常量 ----------------------------

  // 只拉抽奖类活动（对齐 t_activity_config.activity_type）
  const ACTIVITY_TYPE_DRAW = 'DRAW';

  // 活动状态（对齐 t_activity_config.status）
  const ACTIVITY_STATUS_MAP = {
    0: { label: '未开始', color: 'default' },
    1: { label: '已上线', color: 'red' },
    2: { label: '已下线', color: 'default' },
  };

  // ---------------------------- 顶部活动信息 ----------------------------

  const currentActivity = ref(undefined);
  const activityOptions = ref([]);
  const activityLoading = ref(false);
  const activityStatus = ref(undefined);

  const activityStatusLabel = computed(() => ACTIVITY_STATUS_MAP[activityStatus.value]?.label || '未知');

  // 全局防篡改锁：来自服务端活动状态，下发给两个子组件做 UI 防呆（服务端保存时会再算一遍）
  const isOnline = ref(false);

  const activeTab = ref('prizeItem');

  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList(ACTIVITY_TYPE_DRAW);
      activityOptions.value = (res.data || []).map((item) => ({
        value: item.activityCode,
        label: `${item.activityName}（${item.activityCode}）`,
      }));
      if (!currentActivity.value && activityOptions.value.length > 0) {
        currentActivity.value = activityOptions.value[0].value;
      }
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      activityLoading.value = false;
    }
  }

  // ---------------------------- 子组件 ref 与脏标记 ----------------------------

  const tab1Ref = ref();
  const tab2Ref = ref();
  const tab1Dirty = ref(false);
  const tab2Dirty = ref(false);

  const isDirty = computed(() => tab1Dirty.value || tab2Dirty.value);

  // Tab2 只读消费的物资库快照（来自 Tab1）
  const prizeItems = ref([]);

  // Tab1 改动即把最新物资库喂给 Tab2，保证沙盘模拟器读到最新库存
  function syncFromTab1() {
    if (tab1Ref.value) {
      prizeItems.value = tab1Ref.value.getData();
    }
  }

  function onTab1Change(payload) {
    tab1Dirty.value = payload.dirty;
    syncFromTab1();
  }

  function onTab2Change(payload) {
    tab2Dirty.value = payload.dirty;
  }

  // ---------------------------- 配置回显 ----------------------------

  const detailLoading = ref(false);

  async function loadDetail() {
    if (!currentActivity.value) {
      return;
    }
    detailLoading.value = true;
    try {
      const res = await drawWorkbenchApi.detail(currentActivity.value);
      const detail = res.data || {};
      activityStatus.value = detail.activityStatus;
      isOnline.value = Boolean(detail.online);
      // force-render 下两个 Tab 均已挂载，可直接 setData
      await nextTick();
      tab1Ref.value?.setData(detail.prizeItemList);
      tab2Ref.value?.setData(detail.poolList);
      // 回显后基线归零，脏标记随之复位
      tab1Dirty.value = false;
      tab2Dirty.value = false;
      syncFromTab1();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      detailLoading.value = false;
    }
  }

  // 切活动即整体换血：物资库、奖池、上线态全部重新取服务端数据
  watch(currentActivity, loadDetail);

  onMounted(loadActivityOptions);

  // ---------------------------- 统一保存收口 ----------------------------

  const saving = ref(false);

  async function saveAndPublish() {
    if (!currentActivity.value) {
      message.error('请先选择活动');
      return;
    }
    // 发布前先卡住奖池总概率必须闭环
    if (tab2Ref.value && !tab2Ref.value.isProbabilityValid()) {
      message.error('奖池总概率必须等于 100% 才能发布，请到「奖池与概率配置」调整');
      activeTab.value = 'probability';
      return;
    }

    saving.value = true;
    try {
      // 多池契约：Tab2 V2 的 getData() 返回完整 poolList 数组
      const submitData = {
        activityCode: currentActivity.value,
        prizeItemList: tab1Ref.value ? tab1Ref.value.getData() : [],
        poolList: tab2Ref.value ? tab2Ref.value.getData() : [],
      };
      await drawWorkbenchApi.save(submitData);
      message.success('抽奖配置已保存');
      // 重新回显：拿回服务端权威的 usedStock 等运行态数据，同时把脏标记复位
      await loadDetail();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  // ---------------------------- 离开拦截 ----------------------------

  onBeforeRouteLeave(() => {
    if (!isDirty.value) {
      return true;
    }
    return new Promise((resolve) => {
      Modal.confirm({
        title: '有未保存的抽奖配置',
        content: '物资库或奖池概率存在未保存的改动，离开本页将全部丢失。',
        okText: '仍然离开',
        okType: 'danger',
        cancelText: '留在本页',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  });
</script>
