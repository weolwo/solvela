<!--
  首页 · 营销作战大屏

  口径全部来自 docs/营销中台-数据统计方案.md，交互参照 docs/活动大屏原型.html（v2.1）。
  界面上把口径印出来是刻意的 —— 避免运营按自己的理解解读数字。

  ══ 三条不要改回去的口径 ══

  🔴 活动只有「启用 / 禁用」两态，判据是 status != 2（历史值 0 按启用处理）。
     「有没有开始」由业务层按起止时间实时算，不是后台开关，大屏不做时间窗判定。
     用 status = 1 过滤是本项目最容易踩的一脚：开发库 6 条活动里 5 条是历史值 0。

  🔴「发了多少条」与「发出多少价值」必须并列，且价值按资产类型拆开。
     实测里 315 条发奖记录中除了券一分钱都没真发出去，只写「累计发奖 315 次」会误导。
     三个状态数之和必须等于总数 —— 原型 v2 曾在这里漏算过 100 条。

  🔴 末端是「已发出」不是「已到账」：营销域只知道自己发出了发奖指令，
     钱有没有到用户手上是账务域的事。

  ⚠️ 冷热轮询分开（方案 §4）：overview / prizeHealth 30 秒，eventHealth / topMembers 5 分钟。
     页面不可见时不轮询 —— 后台标签页刷这些没有意义，只是白占连接。
-->
<template>
  <div class="dashboard">
    <!-- ══ 最高优先级告警：逾期未开奖。没有时整条不渲染，不占版面 ══ -->
    <a-alert v-if="overdueList.length" type="error" show-icon banner class="overdue-alert">
      <template #message>
        <b>逾期未开奖 {{ overdueList.length }} 期 —— 已对外承诺开奖时刻却未开奖</b>
      </template>
      <template #description>
        <span v-for="it in overdueList" :key="it.lotteryCode + it.issueNo" class="overdue-item">
          彩票 <b>{{ it.lotteryCode }}</b>
          <span class="dim">（活动 {{ it.activityCode }}）</span>
          / 第 {{ it.issueNo }} 期
          <span class="danger">逾期 {{ num(it.overdueHours) }}h</span>
        </span>
      </template>
    </a-alert>

    <!-- ══ KPI 四格 ══ -->
    <a-row :gutter="[10, 10]">
      <a-col :span="6">
        <a-card size="small" class="kpi-card">
          <div class="kpi-title">{{ scopeLabel }}发奖记录（条）</div>
          <div class="kpi-main">
            <span class="kpi-num">{{ num(prize.total) }}</span>
            <span class="kpi-tag ok">成功 {{ num(prize.successCount) }}</span>
            <span class="kpi-tag bad">失败 {{ num(prize.failedCount) }}</span>
            <span class="kpi-tag warn">等待 {{ num(prize.waitingCount) }}</span>
          </div>
          <div class="caliber">status：1-成功 / 2-失败 / 0-等待 · 三项之和等于总数</div>
        </a-card>
      </a-col>

      <a-col :span="6">
        <a-card size="small" class="kpi-card">
          <div class="kpi-title">{{ scopeLabel }}已发出价值</div>
          <div class="kpi-num money">¥{{ fmt(prize.issuedValue) }}</div>
          <div class="asset-line">
            <span v-for="a in assetList" :key="a.prizeType" class="asset-item">
              {{ a.label }}
              <b>{{ num(a.count) }}条</b>
              <span :class="num(a.issuedValue) > 0 ? 'ok' : 'dim'">¥{{ fmt(a.issuedValue) }}</span>
            </span>
          </div>
          <div class="caliber">仅 status=1 计入；「已发出」不等于「已到账」</div>
        </a-card>
      </a-col>

      <a-col :span="6">
        <a-card size="small" class="kpi-card" :class="{ alarm: num(prize.maxWaitHours) >= 24 }">
          <div class="kpi-title">人工审批积压（笔）</div>
          <div class="kpi-main">
            <span class="kpi-num warn">{{ num(prize.pendingApproveCount) }}</span>
            <span class="kpi-tag" :class="num(prize.maxWaitHours) >= 24 ? 'bad' : 'dim'">
              最长滞留 {{ num(prize.maxWaitHours) }}h
            </span>
          </div>
          <div class="caliber">approve_status = 1（营销域审批，不含账务侧）</div>
        </a-card>
      </a-col>

      <a-col :span="6">
        <a-card size="small" class="kpi-card" :class="{ alarm: num(event.attentionCount) > 0 }">
          <div class="kpi-title">近 {{ event.days || 7 }} 天事件丢弃（次）</div>
          <div class="kpi-main">
            <span class="kpi-num bad">{{ num(event.discardCount) }}</span>
            <span class="kpi-tag" :class="num(event.attentionCount) > 0 ? 'bad' : 'dim'">
              需人介入 {{ num(event.attentionCount) }}
            </span>
            <span class="kpi-tag ok">有效推进 {{ num(event.advanceCount) }}</span>
          </div>
          <div class="caliber">幂等命中不写流水，重复投递在这里看不见</div>
        </a-card>
      </a-col>
    </a-row>

    <!-- ══ 主体三栏 ══ -->
    <a-row :gutter="[10, 10]" class="main-row">
      <!-- 左：活动状态分布 + 活动列表 -->
      <a-col :span="6" class="side-col">
        <a-card size="small" title="活动状态分布" class="panel">
          <div class="status-dist">
            <div class="status-cell">
              <div class="status-num ok">{{ num(overview.enabledCount) }}</div>
              <div class="status-label">启用</div>
            </div>
            <div class="status-cell">
              <div class="status-num bad">{{ num(overview.disabledCount) }}</div>
              <div class="status-label">禁用</div>
            </div>
          </div>
          <div class="caliber">
            口径：status = 2 → 禁用，其余（含历史值 0）→ 启用。活动只有开关两态，<b>不看起止时间</b>
            —— 「有没有开始」由业务层按时间实时算。与参与统计图同口径。
          </div>
        </a-card>

        <a-card size="small" class="panel activity-panel fill-card">
          <template #title>活动列表</template>
          <template #extra>
            <a v-if="selectedCode" @click="selectActivity(null)">退出下钻</a>
            <span v-else class="dim">点击下钻</span>
          </template>
          <div class="activity-list">
            <div
              v-for="act in activityList"
              :key="act.activityCode"
              class="activity-card"
              :class="{ active: selectedCode === act.activityCode }"
              @click="selectActivity(act)"
            >
              <div class="act-head">
                <span class="act-name">{{ act.activityName }}</span>
                <a-tag :color="TYPE_COLOR[act.activityType] || 'default'">
                  {{ TYPE_LABEL[act.activityType] || act.activityType }}
                </a-tag>
              </div>
              <div class="act-meta">
                <span :class="act.enabled ? 'ok' : 'bad'">● {{ act.enabled ? '启用' : '禁用' }}</span>
                <span class="dim">{{ fmtDate(act.startTime) }} ~ {{ fmtDate(act.endTime) || '不限' }}</span>
              </div>
              <div class="act-meta">
                <span class="dim">
                  玩法 <b>{{ num(act.gameplayCount) }}</b>
                  <span v-if="act.configured" class="ok"> ✓ 可上线</span>
                  <span v-else class="bad"> ⚠ 配置不完备</span>
                </span>
                <span class="act-code">{{ act.activityCode }}</span>
              </div>
            </div>
            <a-empty v-if="!activityList.length" :image="simpleImage" description="暂无活动" />
          </div>
          <div class="caliber">
            起止时间只做展示，不参与状态判定。完备度来自 ActivityRefProvider.checkConfigured
            （判据是「能不能上线」，不是「有没有记录」）。
          </div>
        </a-card>
      </a-col>

      <!-- 中：参与统计 + 价值趋势 + 玩法运行态 -->
      <a-col :span="12">
        <ParticipationChart />
        <a-card size="small" :title="`${scopeLabel}发出价值趋势（按资产类型）`" class="panel">
          <PrizeTrendChart :date-list="prize.dateList || []" :trend-list="prize.trendList || []" />
        </a-card>
        <a-card size="small" :title="gameplayTitle" class="panel">
          <GameplayPanel :activity-code="selectedCode" />
        </a-card>
      </a-col>

      <!-- 右：失败原因 + 事件丢弃 + 用户榜 -->
      <a-col :span="6" class="side-col">
        <a-card size="small" title="发奖失败原因 TOP" class="panel">
          <div v-if="!failList.length" class="dim center">暂无失败记录</div>
          <div v-for="f in failList" :key="f.failReason" class="line-item">
            <span class="line-text">{{ f.failReason }}</span>
            <span class="bad">{{ num(f.count) }}</span>
          </div>
        </a-card>

        <a-card size="small" title="事件丢弃原因" class="panel">
          <div v-if="!discardList.length" class="dim center">暂无丢弃事件</div>
          <div v-for="d in discardList" :key="d.discardCode" class="discard-item">
            <div class="line-item">
              <span class="line-text">
                <a-tag v-if="d.needsAttention" color="red">需介入</a-tag>
                {{ d.discardDesc }}
              </span>
              <span class="warn">{{ num(d.count) }}</span>
            </div>
            <div class="act-code">{{ d.discardCode }}</div>
          </div>
          <div class="caliber">
            按 discard_code 聚类，不按自由文本的 discard_reason（后者带具体数值，是给人读的，
            直接分组会炸成几百个值）。「需介入」的判据在后端 TaskDiscardCode 枚举里。
          </div>
        </a-card>

        <a-card size="small" title="Top 获奖用户" class="panel fill-card">
          <div v-if="!topMembers.length" class="dim center">暂无数据</div>
          <div v-for="(m, idx) in topMembers" :key="m.memberName" class="line-item">
            <span class="rank" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
            <span class="line-text mono">{{ m.memberName }}</span>
            <span class="dim">{{ num(m.count) }}次</span>
            <span class="ok money-sm">¥{{ fmt(m.issuedValue) }}</span>
          </div>
          <div class="caliber">按已发出价值降序，仅 status=1 计入</div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
  import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
  import { Empty } from 'ant-design-vue';
  import dayjs from 'dayjs';
  import ParticipationChart from '/@/views/system/home/components/participation-chart.vue';
  import PrizeTrendChart from './prize-trend-chart.vue';
  import GameplayPanel from './gameplay-panel.vue';
  import { marketingStatApi } from '/@/api/business/stat/marketing-stat-api';
  import { PRIZE_TYPE_ENUM } from '/@/constants/business/prize/prize-config/prize-config-const';
  import { smartSentry } from '/@/lib/smart-sentry';

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;

  const TYPE_LABEL = { BASIC: '基础活动', DRAW: '奖池抽奖', TASK: '任务驱动', LOTTERY: 'FPE彩票' };
  const TYPE_COLOR = { BASIC: 'default', DRAW: 'orange', TASK: 'purple', LOTTERY: 'cyan' };

  const HOT_INTERVAL_MS = 30 * 1000;
  const COLD_INTERVAL_MS = 5 * 60 * 1000;

  const overview = reactive({ enabledCount: 0, disabledCount: 0, activityList: [], overdueIssueList: [] });
  const prize = reactive({});
  const event = reactive({});
  const topMembers = ref([]);
  const selectedCode = ref('');
  const selectedType = ref('');

  let hotTimer = null;
  let coldTimer = null;

  /* ⚠️ BigDecimal 经 JsonConfig 序列化成字符串（"1020.0000"）：
     直接对字符串调 toLocaleString() 不报错也不格式化，会静默显示成 1020.0000 */
  const num = (v) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  };
  const fmt = (v) => num(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
  const fmtDate = (v) => (v ? dayjs(v).format('YYYY-MM-DD') : '');

  const activityList = computed(() => overview.activityList || []);
  const overdueList = computed(() => overview.overdueIssueList || []);
  const failList = computed(() => prize.failReasonList || []);
  const discardList = computed(() => event.discardList || []);
  const assetList = computed(() =>
    (prize.byAssetList || []).map((a) => ({
      ...a,
      label: PRIZE_TYPE_ENUM[a.prizeType] ? PRIZE_TYPE_ENUM[a.prizeType].desc : a.prizeType,
    }))
  );

  const scopeLabel = computed(() => (selectedCode.value ? '该活动 ' : '全局 '));
  const gameplayTitle = computed(() => {
    if (!selectedCode.value) return '玩法运行态';
    const act = activityList.value.find((a) => a.activityCode === selectedCode.value);
    const typeName = TYPE_LABEL[selectedType.value] || '';
    return `【${act ? act.activityName : selectedCode.value}】${typeName}运行态`;
  });

  function selectActivity(act) {
    selectedCode.value = act ? act.activityCode : '';
    selectedType.value = act ? act.activityType : '';
    // 下钻必须真的换数：只换标题不换数字，比不下钻更误导
    loadPrizeHealth();
  }

  async function loadOverview() {
    try {
      const res = await marketingStatApi.overview();
      Object.assign(overview, res.data || {});
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  async function loadPrizeHealth() {
    try {
      const res = await marketingStatApi.prizeHealth(selectedCode.value || undefined, 7);
      // 先清再赋：切活动时旧的 byAssetList / failReasonList 不能残留
      Object.keys(prize).forEach((k) => delete prize[k]);
      Object.assign(prize, res.data || {});
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  async function loadCold() {
    try {
      const [eventRes, topRes] = await Promise.all([
        marketingStatApi.eventHealth(7),
        marketingStatApi.topMembers(7),
      ]);
      Object.assign(event, eventRes.data || {});
      topMembers.value = topRes.data || [];
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  function refreshHot() {
    // 页面不可见时不刷 —— 后台标签页刷这些没有意义，只是白占连接
    if (document.hidden) return;
    loadOverview();
    loadPrizeHealth();
  }

  function refreshCold() {
    if (document.hidden) return;
    loadCold();
  }

  onMounted(() => {
    loadOverview();
    loadPrizeHealth();
    loadCold();
    hotTimer = setInterval(refreshHot, HOT_INTERVAL_MS);
    coldTimer = setInterval(refreshCold, COLD_INTERVAL_MS);
  });

  onBeforeUnmount(() => {
    clearInterval(hotTimer);
    clearInterval(coldTimer);
  });
</script>

<style lang="less" scoped>
  .dashboard {
    :deep(.ant-card) {
      margin-bottom: 10px;
    }

    /*
     * 🔴 必须把 DefaultHomeCard 的 height:100% 打掉，否则中间那栏会裂开一大段空白。
     *
     * 成因：a-row 是 flex 且默认 align-items:stretch，每个 a-col 的高度被拉成
     * 「最高那一栏」的高度；而 DefaultHomeCard 的 .card-container 写死了 height:100%，
     * 于是「活动参与统计」这一个卡片就把整栏高度吃满，后面的价值趋势与玩法运行态
     * 被顶到几百像素的空白之下 —— 表现就是「最后那个图跑最下面去了」。
     * 那个 height:100% 在原首页是对的（一栏一个卡片），在大屏这种一栏叠三个卡片的
     * 布局里就不成立。改这里而不是改 DefaultHomeCard：它还被别处用着。
     */
    :deep(.card-container) {
      height: auto;
    }
  }

  .overdue-alert {
    margin-bottom: 10px;

    .overdue-item {
      margin-right: 18px;
      font-size: 12px;
    }
  }

  .main-row {
    margin-top: 0;
  }

  /*
   * 三栏底部对齐：中间那栏（参与统计 + 价值趋势 + 玩法运行态）最高，
   * 左右两栏原先各自按内容收，底部参差不齐，看着像没排版。
   *
   * a-row 是 flex 且默认 stretch，每个 a-col 本来就已经被拉到「最高那栏」的高度 ——
   * 缺的只是把这份高度分下去。所以两侧栏改成 flex 纵向容器，
   * 各自指定一个 .fill-card 去吃掉剩余空间（左边是活动列表、右边是获奖用户榜），
   * 内容超出时卡片体内部滚动，而不是把整页撑长。
   *
   * ⚠️ min-height:0 不能省：flex 子项默认 min-height:auto，
   * 不写的话内容多了会顶破容器，overflow 不生效（滚动条永远不出现）。
   */
  .side-col {
    display: flex;
    flex-direction: column;
  }

  .fill-card {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;

    :deep(.ant-card-body) {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
    }
  }

  .kpi-card {
    height: 100%;

    &.alarm {
      border-color: #ffa39e;
    }
  }

  .kpi-title {
    margin-bottom: 4px;
    font-size: 12px;
    color: #8c8c8c;
  }

  .kpi-main {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: baseline;
  }

  .kpi-num {
    font-family: 'Courier New', Courier, monospace;
    font-size: 26px;
    font-weight: 700;
    line-height: 1.2;

    &.money {
      color: #52c41a;
    }
  }

  .kpi-tag {
    font-size: 12px;
  }

  .asset-line {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 10px;
    margin-top: 4px;

    .asset-item {
      font-size: 11px;
      color: #595959;
    }
  }

  .ok {
    color: #52c41a;
  }

  .bad {
    color: #f5222d;
  }

  .warn {
    color: #faad14;
  }

  .dim {
    color: #8c8c8c;
  }

  .center {
    padding: 16px 0;
    text-align: center;
  }

  .caliber {
    padding-top: 6px;
    font-size: 11px;
    line-height: 1.6;
    color: #8c8c8c;
  }

  .panel {
    :deep(.ant-card-body) {
      padding: 10px 12px;
    }
  }

  .status-dist {
    display: flex;
  }

  .status-cell {
    flex: 1;
    text-align: center;
  }

  .status-num {
    font-family: 'Courier New', Courier, monospace;
    font-size: 22px;
    font-weight: 700;
  }

  .status-label {
    font-size: 11px;
    color: #8c8c8c;
  }

  /* 滚动交给 .fill-card 的卡片体，这里不再自己限高 */
  .activity-list {
    flex: 1;
  }

  .activity-card {
    padding: 8px;
    margin-bottom: 6px;
    cursor: pointer;
    background: #fafafa;
    border: 1px solid transparent;
    border-radius: 6px;

    &:hover {
      background: #f0f5ff;
    }

    &.active {
      background: #e6f4ff;
      border-color: #91caff;
    }
  }

  .act-head {
    display: flex;
    gap: 6px;
    justify-content: space-between;
    align-items: flex-start;
  }

  .act-name {
    font-size: 13px;
    font-weight: 600;
    line-height: 1.3;
  }

  .act-meta {
    display: flex;
    gap: 6px;
    justify-content: space-between;
    margin-top: 3px;
    font-size: 11px;
  }

  .act-code {
    font-family: 'Courier New', Courier, monospace;
    font-size: 10px;
    color: #bfbfbf;
  }

  .line-item {
    display: flex;
    gap: 6px;
    align-items: center;
    padding: 3px 0;
    font-size: 12px;
  }

  .line-text {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mono {
    font-family: 'Courier New', Courier, monospace;
    color: #1677ff;
  }

  .money-sm {
    width: 64px;
    font-family: 'Courier New', Courier, monospace;
    text-align: right;
  }

  .rank {
    width: 16px;
    font-size: 11px;
    color: #bfbfbf;
    text-align: center;

    &.top {
      font-weight: 700;
      color: #faad14;
    }
  }

  .discard-item {
    margin-bottom: 4px;
  }
</style>
