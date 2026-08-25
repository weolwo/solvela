<!--
  玩法运行态：按活动类型渲染对应的一块

    DRAW    抽奖状态分布（条数 + 参与人数）
    LOTTERY 期号售卖进度与开奖倒计时
    TASK    选一个任务看阶梯流失漏斗
    BASIC   按定义没有玩法运行态

  🔴 刻意不做「全局跨玩法转化漏斗」：跨玩法的统一漏斗需要曝光埋点（营销中台不接，属上游），
     且任务侧幂等命中不写流水会让分母偏小 —— 算出来的转化率是错的。

  ⚠️ 漏斗用的是 series type 'funnel'，已在 /@/lib/echarts.js 注册。
     漏注册的表现是「空白图 + 控制台零报错」，没有任何显式信号。

  ⚠️ 自动化验证时容易误判成「漏注册」：漏斗的入场动画是 opacity 0 → 1，而无头/未合成帧的
     浏览器里 requestAnimationFrame 不跑，动画永远停在第一帧 —— 采样画布只能看到白色的
     标签文字，形状全是 opacity 0，和漏注册的现象一模一样。
     判别方法：读 zrender 显示列表（getZr().storage.getDisplayList()），
     polygon 的 shape/fill 都对、只有 style.opacity 为 0，就是动画没跑，不是没注册；
     或者临时 setOption({animation:false}) 再采样。本组件已实测走过这一遭。
-->
<template>
  <a-spin :spinning="loading">
    <!-- 未选活动 -->
    <div v-if="!activityCode" class="empty-hint">
      <div class="empty-icon">👈</div>
      <div>点击左侧活动卡片，查看它的玩法运行态</div>
      <div class="caliber">
        不提供「全局转化漏斗」：跨玩法的统一漏斗需要曝光埋点（营销中台不接，属上游）， 且任务侧幂等命中不写流水会让分母偏小，算出来的转化率是错的。
      </div>
    </div>

    <!-- 抽奖 -->
    <div v-else-if="data.activityType === 'DRAW'" class="draw-box">
      <div v-if="!drawRows.length" class="empty-hint-sm">该活动暂无抽奖记录</div>
      <div v-for="row in drawRows" :key="row.status" class="draw-row">
        <span class="draw-label">{{ row.label }}</span>
        <a-progress class="draw-bar" :percent="pct(row.count, drawTotal)" :stroke-color="row.color" :show-info="false" size="small" />
        <span class="draw-num">{{ row.count }} 次 / {{ row.memberCount }} 人</span>
      </div>
      <div class="caliber">
        status：0-未中奖 / 1-已中奖 / 2-库存不足 / 3-异常。
        <b>库存水位要读 Redis 不读 DB</b> —— used_stock 是最终一致性兜底，读 DB 会偏小。
      </div>
    </div>

    <!-- 彩票 -->
    <div v-else-if="data.activityType === 'LOTTERY'" class="lottery-box">
      <div v-if="!issueList.length" class="empty-hint-sm">该活动下暂无期号</div>
      <div v-for="issue in issueList" :key="issue.lotteryCode + issue.issueNo" class="issue-item">
        <div class="issue-head">
          <span>
            <a-tag color="cyan">{{ issue.lotteryCode }}</a-tag>
            第 {{ issue.issueNo }} 期
            <a-tag :color="ISSUE_STATUS[issue.status] ? ISSUE_STATUS[issue.status].color : 'default'">
              {{ ISSUE_STATUS[issue.status] ? ISSUE_STATUS[issue.status].label : issue.status }}
            </a-tag>
          </span>
          <span :class="issue.overdueHours > 0 ? 'overdue' : 'plan-time'">
            {{ issue.overdueHours > 0 ? `逾期 ${issue.overdueHours}h 未开奖` : `计划开奖 ${issue.planDrawTime || '未设置'}` }}
          </span>
        </div>
        <a-progress
          :percent="pct(issue.soldCount, issue.totalCount)"
          :stroke-color="issue.overdueHours > 0 ? '#f5222d' : '#1677ff'"
          :show-info="false"
          size="small"
        />
        <div class="issue-foot">已售 {{ num(issue.soldCount) }} / {{ num(issue.totalCount) }}</div>
      </div>
      <div class="caliber">
        玩法编码 lottery_code 与活动编码 activity_code 是<b>两套编码</b>，靠 t_lottery_config.activity_code 关联 ——
        一个活动可以挂多个彩票玩法，别复用同一个串。
      </div>
    </div>

    <!-- 任务 -->
    <div v-else-if="data.activityType === 'TASK'" class="task-box">
      <div v-if="!taskOptions.length" class="empty-hint-sm">该活动下暂无任务配置</div>
      <template v-else>
        <a-select v-model:value="selectedTaskId" class="task-select" size="small" :options="taskOptions" @change="loadFunnel" />
        <div ref="funnelRef" class="funnel-chart"></div>
        <div class="caliber">
          到达人数的判据是 <b>current_metric ≥ 该档 target</b>，不是「发过这档的奖」——
          发奖可能因预算耗尽或等审批停在半路，那是发奖健康度要回答的问题。
        </div>
      </template>
    </div>

    <div v-else class="empty-hint-sm">基础活动（BASIC）没有玩法运行态</div>
  </a-spin>
</template>

<script setup>
  import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue';
  import echarts from '/@/lib/echarts';
  import { marketingStatApi } from '/@/api/business/stat/marketing-stat-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { CHROME, DRAW_STATUS_COLOR, RAMP, tooltipStyle } from './dashboard-theme';

  const props = defineProps({
    activityCode: { type: String, default: '' },
  });

  /*
   * 抽奖状态用**状态色**而不是分类色：它表达的是「好 / 该管 / 危险」，不是「是谁」。
   * 状态色是保留位，绝不拿去当第四个系列色 —— 否则读者会把一个普通系列误读成告警。
   * 每条都带文字标签，颜色只是辅助，不单独承担语义。
   */
  const DRAW_STATUS = {
    0: { label: '未中奖', color: DRAW_STATUS_COLOR[0] },
    1: { label: '已中奖', color: DRAW_STATUS_COLOR[1] },
    2: { label: '库存不足', color: DRAW_STATUS_COLOR[2] },
    3: { label: '异常', color: DRAW_STATUS_COLOR[3] },
  };

  const ISSUE_STATUS = {
    0: { label: '销售中', color: 'blue' },
    1: { label: '开奖中', color: 'orange' },
    2: { label: '已开奖', color: 'green' },
  };

  const loading = ref(false);
  const data = reactive({ activityType: '', drawStatList: [], lotteryIssueList: [], taskOptionList: [] });
  const selectedTaskId = ref(null);
  const funnelRef = ref(null);
  const funnelChart = shallowRef(null);
  const funnelData = shallowRef(null);

  const num = (v) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  };
  const pct = (a, b) => (num(b) ? Math.round((num(a) / num(b)) * 1000) / 10 : 0);

  const drawRows = computed(() =>
    (data.drawStatList || []).map((row) => ({
      status: row.status,
      label: DRAW_STATUS[row.status] ? DRAW_STATUS[row.status].label : `未知(${row.status})`,
      color: DRAW_STATUS[row.status] ? DRAW_STATUS[row.status].color : '#8c8c8c',
      count: num(row.count),
      memberCount: num(row.memberCount),
    }))
  );
  const drawTotal = computed(() => drawRows.value.reduce((s, r) => s + r.count, 0));
  const issueList = computed(() => data.lotteryIssueList || []);
  const taskOptions = computed(() =>
    (data.taskOptionList || []).map((t) => ({
      value: t.taskConfigId,
      label: `${t.taskName}（${num(t.memberCount)} 人参与）`,
    }))
  );

  async function loadGameplay() {
    Object.assign(data, { activityType: '', drawStatList: [], lotteryIssueList: [], taskOptionList: [] });
    funnelData.value = null;
    selectedTaskId.value = null;
    if (!props.activityCode) return;
    loading.value = true;
    try {
      const res = await marketingStatApi.gameplay(props.activityCode);
      Object.assign(data, res.data || {});
      if (data.activityType === 'TASK' && (data.taskOptionList || []).length) {
        selectedTaskId.value = data.taskOptionList[0].taskConfigId;
        await loadFunnel();
      }
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  async function loadFunnel() {
    if (!selectedTaskId.value) return;
    try {
      const res = await marketingStatApi.taskFunnel(selectedTaskId.value);
      funnelData.value = res.data || {};
      // v-if 刚切出来时 DOM 还没挂上，等一帧再画
      await nextTick();
      renderFunnel();
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  function renderFunnel() {
    const vo = funnelData.value;
    if (!vo || !funnelRef.value || !funnelRef.value.clientWidth) return;
    if (!funnelChart.value) funnelChart.value = echarts.init(funnelRef.value);

    const rows = [{ name: '接取任务', value: num(vo.joinedCount) }].concat(
      (vo.stageList || []).map((s) => ({
        name: `阶梯${s.stageLevel}（目标 ${num(s.target)}）`,
        value: num(s.reachCount),
      }))
    );

    /*
     * 漏斗是「同一个量的分档」，所以用**同一个蓝色由浅到深**，不是四种不同的颜色。
     * 换成分类色会暗示这几档是四件不同的事，而它们是一件事的四个阶段。
     * ⚠️ 前两档偏浅，白字压不住，按底色明度切换成深墨色。
     */
    const shades = rows.map((_, i) => RAMP[Math.min(i, RAMP.length - 1)]);

    funnelChart.value.setOption(
      {
        tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 人', ...tooltipStyle },
        series: [
          {
            type: 'funnel',
            left: '6%',
            width: '88%',
            top: 10,
            bottom: 10,
            sort: 'none',
            // 段间留 2px 底色缝，靠留白分隔而不是描边
            gap: 2,
            minSize: '30%',
            label: { show: true, position: 'inside', formatter: '{b}  {c} 人', fontSize: 11, fontWeight: 500 },
            data: rows.map((r, i) => ({
              ...r,
              itemStyle: { color: shades[i], borderColor: CHROME.surface, borderWidth: 1 },
              label: { color: i < 2 ? '#0b0b0b' : '#ffffff' },
            })),
          },
        ],
      },
      true
    );
  }

  watch(() => props.activityCode, loadGameplay, { immediate: true });

  onBeforeUnmount(() => {
    funnelChart.value && funnelChart.value.dispose();
  });
</script>

<style lang="less" scoped>
  .empty-hint {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    height: 260px;
    color: #8c8c8c;

    .empty-icon {
      font-size: 28px;
    }
  }

  .empty-hint-sm {
    padding: 40px 0;
    color: #8c8c8c;
    text-align: center;
  }

  .caliber {
    padding-top: 8px;
    font-size: 11px;
    line-height: 1.6;
    color: #8c8c8c;
  }

  .empty-hint .caliber {
    max-width: 420px;
    text-align: center;
  }

  .draw-box {
    padding: 4px 0;
  }

  .draw-row {
    display: flex;
    gap: 10px;
    align-items: center;
    margin-bottom: 10px;
  }

  .draw-label {
    flex: 0 0 64px;
    font-size: 12px;
    color: #595959;
  }

  .draw-bar {
    flex: 1;
    margin: 0;
  }

  .draw-num {
    flex: 0 0 120px;
    font-size: 12px;
    text-align: right;
  }

  .issue-item {
    padding: 8px 10px;
    margin-bottom: 8px;
    background: #fafafa;
    border-radius: 6px;
  }

  .issue-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
    font-size: 12px;
  }

  .issue-foot {
    margin-top: 4px;
    font-size: 11px;
    color: #8c8c8c;
  }

  .overdue {
    font-weight: 600;
    color: #f5222d;
  }

  .plan-time {
    color: #8c8c8c;
  }

  .task-select {
    width: 100%;
    margin-bottom: 6px;
  }

  .funnel-chart {
    width: 100%;
    height: 230px;
  }
</style>
