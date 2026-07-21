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
    <!-- 顶部：活动信息 + 全局开关 + 保存发布 -->
    <a-card :bordered="false" class="mb-4 shadow-sm" :body-style="{ padding: '16px 24px' }">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-4">
          <span class="text-lg font-bold">🎯 抽奖配置工作台</span>
          <a-divider type="vertical" />
          <span class="text-sm text-gray-500">当前活动</span>
          <a-select v-model:value="currentActivity" class="w-52" :options="activityOptions" />
        </div>

        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium text-gray-600">🛡️ 上线锁（防篡改）</span>
            <a-switch v-model:checked="isOnline" checked-children="已上线" un-checked-children="未上线" />
          </div>
          <a-popconfirm
            title="确认保存并发布本抽奖活动？"
            ok-text="保存并发布"
            cancel-text="再想想"
            @confirm="saveAndPublish"
          >
            <a-button type="primary" :loading="saving">
              <template #icon><CloudUploadOutlined /></template>
              保存并发布抽奖活动
            </a-button>
          </a-popconfirm>
        </div>
      </div>
    </a-card>

    <!-- 主体：两个 Tab -->
    <a-card :bordered="false" class="shadow-sm">
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="prizeItem" force-render>
          <template #tab>
            <a-badge :dot="tab1Dirty" :offset="[6, 0]"><span>📦 奖项物资库</span></a-badge>
          </template>
          <PrizeItemLibrary ref="tab1Ref" :is-online="isOnline" @change="onTab1Change" />
        </a-tab-pane>

        <a-tab-pane key="probability" force-render>
          <template #tab>
            <a-badge :dot="tab2Dirty" :offset="[6, 0]"><span>🎲 奖池与概率配置</span></a-badge>
          </template>
          <PoolProbabilityEngine ref="tab2Ref" :is-online="isOnline" :prize-items="prizeItems" @change="onTab2Change" />
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup>
  import { nextTick, onMounted, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { CloudUploadOutlined } from '@ant-design/icons-vue';
  import PrizeItemLibrary from './components/PrizeItemLibrary.vue';
  import PoolProbabilityEngine from './components/PoolProbabilityEngine.vue';

  // ---------------------------- 顶部活动信息 ----------------------------

  const currentActivity = ref('ACT_618');
  const activityOptions = [{ value: 'ACT_618', label: '618 年中狂欢抽奖' }];

  // 全局防篡改锁，下发给两个子组件
  const isOnline = ref(false);

  const activeTab = ref('prizeItem');

  // ---------------------------- 子组件 ref 与脏标记 ----------------------------

  const tab1Ref = ref();
  const tab2Ref = ref();
  const tab1Dirty = ref(false);
  const tab2Dirty = ref(false);

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

  onMounted(async () => {
    // force-render 下两个 Tab 均已挂载，取 Tab1 初始物资库作为 Tab2 的初值
    await nextTick();
    syncFromTab1();
  });

  // ---------------------------- 统一保存收口 ----------------------------

  const saving = ref(false);

  async function saveAndPublish() {
    // 发布前先卡住奖池总概率必须闭环
    if (tab2Ref.value && !tab2Ref.value.isProbabilityValid()) {
      message.error('奖池总概率必须等于 100% 才能发布，请到「奖池与概率配置」调整');
      activeTab.value = 'probability';
      return;
    }

    saving.value = true;
    try {
      const prizeItemList = tab1Ref.value ? tab1Ref.value.getData() : [];
      const prizeMappingList = tab2Ref.value ? tab2Ref.value.getData() : [];

      // 组装主子表超级 JSON（对齐后端：活动 + 物资库(t_prize_pool_item) + 奖池映射(t_pool_prize_mapping)）
      const submitData = {
        activityCode: currentActivity.value,
        publishAsOnline: isOnline.value,
        prizeItemList,
        poolConfig: {
          poolCode: 'POOL_FREE',
          prizeMappingList,
        },
      };

      console.log('【抽奖活动 · 保存并发布】超级 JSON：', submitData);
      message.success('已组装保存数据，请在控制台查看');
      // 真实接入时：await drawApi.savePool(submitData)
    } finally {
      saving.value = false;
    }
  }
</script>
