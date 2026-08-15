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

          <!--
            内嵌模式下活动由外壳锁定，隐藏活动下拉。
            但「玩法」下拉必须保留 —— 一个彩票活动下可以有多个玩法（5位/10万、7位/1000万…），
            锁的是活动这一层，不是玩法这一层。
          -->
          <template v-if="!embedded">
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
          </template>

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
            <a-tag :color="statusTag.color">{{ statusTag.label }}</a-tag>

            <!-- 上下线是显式动作，不与「保存配置」混在一起：
                 保存只改配置，上线是「开始对外发号」这件事本身，语义不同 -->
            <a-popconfirm
              v-if="configured && currentStatus !== LOTTERY_STATUS_ENUM.ONLINE.value"
              title="上线后即可对外发号。请确认奖级规则已配置正确。"
              ok-text="确认上线"
              cancel-text="再想想"
              @confirm="switchOnline(true)"
            >
              <a-button size="small" type="primary" ghost :loading="statusChanging">上线</a-button>
            </a-popconfirm>
            <a-popconfirm
              v-else-if="configured"
              title="下线后立即停止发号。已发出的号码不受影响，期号照常可以开奖。"
              ok-text="确认下线"
              cancel-text="再想想"
              @confirm="switchOnline(false)"
            >
              <a-button size="small" danger ghost :loading="statusChanging">下线</a-button>
            </a-popconfirm>
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
      <!-- 内嵌时活动一定有值（外壳传入），这个空态只属于独立入口 -->
      <a-empty v-if="!embedded && !currentActivity && !activityLoading" class="py-16">
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
  import { computed, onMounted, ref, watch } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { onBeforeRouteLeave, useRoute } from 'vue-router';
  import { CloudUploadOutlined } from '@ant-design/icons-vue';
  import { lotteryWorkbenchApi } from '/@/api/business/lottery/lottery-workbench/lottery-workbench-api';
  import { lotteryConfigApi } from '/@/api/business/lottery/lottery-config/lottery-config-api';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { LOTTERY_STATUS_ENUM } from '/@/constants/business/lottery/lottery-const';
  import EngineConfigPanel from './components/EngineConfigPanel.vue';
  import IssueConsole from './components/IssueConsole.vue';

  // ---------------------------- 内嵌契约 ----------------------------

  /*
   * 两种用法（与 DrawWorkbench 同构）：
   * ① 独立入口（默认）：顶部自带活动下拉，行为与改造前完全一致。
   * ② 内嵌于活动创建向导：活动由外壳锁定，隐藏活动下拉；玩法下拉保留（一个活动可有多个玩法）。
   *
   * 保存按钮不上交外壳，内嵌时保存成功后额外 emit('saved')。
   */
  const props = defineProps({
    activityCode: { type: String, default: '' },
    embedded: { type: Boolean, default: false },
  });

  const emit = defineEmits(['saved']);

  const route = useRoute();

  /*
   * 「新建玩法」意图（来自 ?mode=new，即彩票配置列表页顶部那个按钮）。
   *
   * 它不是一次性的开关，而是一直保持到运营真的选了某个已有玩法、或者新玩法保存成功为止：
   * 带着建新玩法的意图进来，中途换个活动，要的仍然是在新活动下建一个新的，
   * 而不是被塞进那个活动的第一个已有玩法里。
   */
  const pendingNew = ref(false);

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
  const statusChanging = ref(false);

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

  // ---------------------------- 上下线 ----------------------------

  async function switchOnline(toOnline) {
    statusChanging.value = true;
    try {
      const api = toOnline ? lotteryConfigApi.online : lotteryConfigApi.offline;
      await api(loadedLottery.value);
      message.success(toOnline ? '已上线，现在可以对外发号' : '已下线，已停止发号');
      // 状态以服务端为准重新拉一次：上线会触发结构锁，面板里的禁用态要跟着变
      await loadLotteries(currentActivity.value);
      await loadDetail(currentActivity.value, loadedLottery.value);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      statusChanging.value = false;
    }
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
        // 走 openActivity 而不是在这里重写一遍：换活动后默认打开第一个玩法、一个都没有就进新建态，
        // 以及「带着新建意图时不接管已有玩法」这几条判断只该有一处
        await openActivity(activityCode);
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
        // 运营明确挑了一个已有玩法，新建意图到此为止
        pendingNew.value = false;
        loadedLottery.value = lotteryCode;
        currentStatus.value = lotteryOptions.value.find((o) => o.value === lotteryCode)?.status ?? null;
        loadDetail(currentActivity.value, lotteryCode);
      },
      () => {
        currentLottery.value = prev;
      }
    );
  }

  /**
   * 进入「新建玩法」态：玩法下拉清空，detail 传空 lotteryCode 会拿到带预生成编码的空壳。
   * 顶部的 ➕ 按钮与 ?mode=new 深链走的是同一段。
   */
  function enterCreateMode(activityCode) {
    pendingNew.value = true;
    currentLottery.value = undefined;
    loadedLottery.value = undefined;
    currentStatus.value = null;
    return loadDetail(activityCode, '');
  }

  function onCreateNew() {
    const prev = loadedLottery.value;
    guard(
      () => {
        enterCreateMode(currentActivity.value);
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
      // 新玩法已经落库，接下来加载的就是它本身，不再是「待新建」
      pendingNew.value = false;
      // 新建的玩法要进下拉并选中；结构锁、已发号数一律以服务端为准，不拿本地状态猜
      await loadLotteries(currentActivity.value);
      currentLottery.value = param.lotteryCode;
      loadedLottery.value = param.lotteryCode;
      await loadDetail(currentActivity.value, param.lotteryCode);
      // 内嵌时告诉外壳「这一步配完了」；独立入口下没人监听，无副作用
      emit('saved', { activityCode: currentActivity.value, lotteryCode: param.lotteryCode });
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

  /**
   * 打开一个活动：拉它的玩法列表，选中一个并回显。
   * 独立入口的「只有一个活动就自动打开」与内嵌模式的「外壳指定活动」走的是同一段逻辑。
   *
   * @param preferLotteryCode 指定要定位的玩法（列表页「编辑」带 query 跳过来时用）；
   *                          不传或在该活动下找不到时退回第一个玩法
   */
  async function openActivity(activityCode, preferLotteryCode) {
    currentActivity.value = activityCode;
    loadedActivity.value = activityCode;
    await loadLotteries(activityCode);

    // 带着新建意图进来就停在空壳态，不自动接管一个已有玩法
    if (pendingNew.value) {
      enterCreateMode(activityCode);
      return;
    }

    const target = lotteryOptions.value.find((o) => o.value === preferLotteryCode) || lotteryOptions.value[0];
    currentLottery.value = target?.value;
    loadedLottery.value = target?.value;
    currentStatus.value = target?.status ?? null;
    await loadDetail(activityCode, target?.value);
  }

  /*
   * 内嵌模式：活动来自 props，onMounted 与 watch 两条路径都要处理。
   * 只写 onMounted 的话，外壳从活动 A 切到活动 B 若没触发组件重新挂载，页面会停在上一个活动
   * （彩票 P4 验收踩过同族的坑：导航到同一 hash 路由不会重新挂载）。
   */
  watch(
    () => props.activityCode,
    (code) => {
      if (props.embedded && code && code !== loadedActivity.value) {
        openActivity(code);
      }
    }
  );

  onMounted(async () => {
    if (props.embedded) {
      // 内嵌时活动已定，不拉全量活动列表
      if (props.activityCode) {
        await openActivity(props.activityCode);
      }
      return;
    }
    await loadActivities();

    /*
     * ?mode=new：彩票配置列表页顶部那个按钮，意图是「配一个新玩法」。
     * 必须先于下面的自动打开生效 —— 否则只有一个彩票活动时会被自动带进该活动的第一个已有玩法，
     * 点「新建」却进了别人的配置页。
     */
    pendingNew.value = route.query.mode === 'new';

    /*
     * 深链：彩票配置列表页的「编辑」带 ?activityCode=&lotteryCode= 跳过来，直接定位到那一个玩法。
     *
     * 不校验 activityCode 是否在下拉里：optionList 默认过滤掉已下线/已过期的活动，
     * 而列表页恰恰能查到这些活动下的玩法 —— 拿不到选项也照样要能打开，否则「编辑」按钮时灵时不灵。
     */
    const queryActivity = route.query.activityCode;
    if (queryActivity) {
      await openActivity(String(queryActivity), route.query.lotteryCode ? String(route.query.lotteryCode) : undefined);
      return;
    }

    // 只有一个活动时直接打开，省掉一次点击；多个时让运营自己选，避免默认选错还没察觉。
    // 带 mode=new 时 openActivity 会停在空壳态，不会接管已有玩法
    const firstActivity = activityOptions.value[0];
    if (activityOptions.value.length === 1 && firstActivity) {
      await openActivity(firstActivity.value);
    }
  });
</script>
