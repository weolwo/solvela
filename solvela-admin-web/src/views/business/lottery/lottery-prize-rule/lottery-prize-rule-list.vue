<!--
  * 彩票奖励结构分析
  *
  * 定位：跨玩法的赔付模型与配置体检，<b>只读</b>。
  *
  * 与「彩票配置工作台」Tab1 的分工是刻意的：
  *   - 工作台 = 单个玩法的奖级映射编辑（可视化绑定资产）；
  *   - 本页   = 所有玩法横向看「这套奖级一期要赔多少钱、有没有哪一级是死的」，
  *             发现问题后深链回工作台去改。
  *
  * ⚠️ 本页不提供新建/编辑/删除，服务端也已下掉那四个接口。原因有两条，缺一条都不足以决定：
  *   ① 原先这条写入路径绕过了工作台的全部校验（奖级99、匹配长度超号码长度、
  *      奖品不在本活动资产库…），写进去的脏规则会导致「开奖跳过该奖级」
  *      或「用户中了奖拿不到东西」；
  *   ② 就算补上校验也没用 —— 工作台按玩法整表重建奖级（先删后插），
  *      从这里写进去的规则活不过下一次工作台保存。
  * 所以正确做法不是给后门加锁，而是把门拆掉：奖级规则只有工作台一个写入口。
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 11:50:34
  * @Copyright  weolwo
-->
<template>
  <!---------- 概览 begin ----------->
  <a-row :gutter="12" class="overview-row">
    <a-col :span="6">
      <div class="overview-card tone-danger" :class="{ active: queryForm.onlyIssue }" @click="toggleOnlyIssue">
        <div class="overview-card-label">
          体检告警
          <a-tooltip title="DANGER 会导致开奖跳过奖级、或中奖后发不出奖品；WARN 是配置可疑但能跑">
            <QuestionCircleOutlined class="overview-card-hint" />
          </a-tooltip>
        </div>
        <div class="overview-card-value">
          {{ result.dangerCount ?? '-' }}
          <span v-if="result.warnCount > 0" class="overview-card-sub">+{{ result.warnCount }} 警告</span>
        </div>
        <div class="overview-card-foot">{{ queryForm.onlyIssue ? '已筛选 · 再点取消' : '点击只看有问题的玩法' }}</div>
      </div>
    </a-col>
    <a-col :span="6">
      <div class="overview-card tone-primary">
        <div class="overview-card-label">配了奖级的玩法</div>
        <div class="overview-card-value">{{ result.lotteryCount ?? '-' }}</div>
        <div class="overview-card-foot">共 {{ result.ruleCount ?? '-' }} 条奖级规则</div>
      </div>
    </a-col>
    <a-col :span="12">
      <div class="overview-card tone-warning">
        <div class="overview-card-label">
          预计赔付总成本
          <a-tooltip title="每个玩法各跑满一期（发行量全部售出）时的预计赔付合计 = Σ(净中奖率 × 单期发行量 × 奖品单价)">
            <QuestionCircleOutlined class="overview-card-hint" />
          </a-tooltip>
        </div>
        <div class="overview-card-value">￥{{ money(result.totalExpectedCost) }}</div>
        <div class="overview-card-foot">各玩法单期跑满的赔付合计，按当前筛选统计</div>
      </div>
    </a-col>
  </a-row>
  <!---------- 概览 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="彩票玩法" class="solvela-query-form-item">
        <a-select
          style="width: 240px"
          v-model:value="queryForm.lotteryCode"
          :options="lotteryOptions"
          :loading="lotteryLoading"
          placeholder="全部玩法"
          allowClear
          show-search
          option-filter-prop="label"
          @change="onSearch"
        />
      </a-form-item>
      <a-form-item class="solvela-query-form-item">
        <a-checkbox v-model:checked="queryForm.onlyIssue" @change="onSearch">只看有体检告警的玩法</a-checkbox>
      </a-form-item>
      <a-form-item class="solvela-query-form-item">
        <a-button type="primary" @click="onSearch">
          <template #icon>
            <SearchOutlined />
          </template>
          查询
        </a-button>
        <a-button @click="resetQuery" class="solvela-margin-left10">
          <template #icon>
            <ReloadOutlined />
          </template>
          重置
        </a-button>
      </a-form-item>
    </a-row>
  </a-form>
  <!---------- 查询表单form end ----------->

  <a-spin :spinning="loading">
    <a-alert type="info" show-icon class="mb-3">
      <template #message>
        <span class="text-xs">奖级规则只能在「彩票配置工作台」修改，本页只做分析与体检</span>
      </template>
      <template #description>
        <span class="text-xs"> 工作台保存奖级时按玩法整表重建，所以任何其他写入路径都会被覆盖 —— 本页刻意不提供编辑入口，避免改了个寂寞。 </span>
      </template>
    </a-alert>

    <a-empty v-if="!loading && list.length === 0" class="py-16">
      <template #description>
        <span class="text-sm text-slate-400">
          {{ queryForm.onlyIssue ? '当前筛选下没有体检告警 —— 奖级配置是干净的' : '还没有任何玩法配置奖级规则' }}
        </span>
      </template>
    </a-empty>

    <!-- 按玩法分组：奖级只有放在一起才有意义 ——
         净中奖率要扣更高奖级的抢占、赔付成本要逐级相加，单看一行规则什么都判断不了 -->
    <a-card
      v-for="item in list"
      :key="item.lotteryCode"
      size="small"
      :bordered="false"
      :hoverable="true"
      class="lottery-card"
      :class="{ 'has-danger': item.dangerCount > 0 }"
    >
      <!-- 玩法头部 -->
      <div class="lottery-head">
        <div class="lottery-title">
          <span class="lottery-name">{{ item.lotteryName || '（玩法配置已缺失）' }}</span>
          <span class="lottery-code">{{ item.lotteryCode }}</span>
          <a-tag :color="lotteryStatusOf(item.lotteryStatus).color">{{ lotteryStatusOf(item.lotteryStatus).desc }}</a-tag>
          <a-tag v-if="item.dangerCount > 0" color="red">{{ item.dangerCount }} 项危险</a-tag>
          <a-tag v-else-if="item.warnCount > 0" color="orange">{{ item.warnCount }} 项警告</a-tag>
          <a-tag v-else color="green">体检通过</a-tag>
        </div>
        <a-button type="link" size="small" :disabled="!item.activityCode" @click="gotoWorkbench(item)"> 去工作台修改 → </a-button>
      </div>

      <!-- 玩法级赔付模型 -->
      <div class="lottery-metrics">
        <div class="metric">
          <div class="metric-label">号码长度</div>
          <div class="metric-value">{{ item.numberLength ?? '-' }} 位</div>
        </div>
        <div class="metric">
          <div class="metric-label">单期发行量</div>
          <div class="metric-value">{{ item.totalCount == null ? '-' : item.totalCount.toLocaleString() }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">总中奖率</div>
          <div class="metric-value">{{ percent(item.totalNetRate) }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">预计中奖注数</div>
          <div class="metric-value">{{ count(item.totalExpectedWinCount) }}</div>
        </div>
        <div class="metric metric-cost">
          <div class="metric-label">预计单期赔付</div>
          <div class="metric-value">￥{{ money(item.totalExpectedCost) }}</div>
        </div>
      </div>

      <!-- 玩法级告警 -->
      <a-alert v-for="(issue, idx) in item.issueList" :key="idx" :type="issue.level === 'DANGER' ? 'error' : 'warning'" show-icon class="mb-2">
        <template #message
          ><span class="text-xs">{{ issue.message }}</span></template
        >
      </a-alert>

      <!-- 奖级明细 -->
      <a-table
        size="small"
        :data-source="item.ruleList"
        :columns="ruleColumns"
        row-key="id"
        bordered
        :pagination="false"
        :row-class-name="(record) => (hasDanger(record) ? 'row-danger' : '')"
      >
        <template #bodyCell="{ record, column }">
          <template v-if="column.dataIndex === 'prizeLevel'">
            <span class="level-badge">{{ record.prizeLevel }} 等奖</span>
          </template>

          <!-- 匹配规则：把 TAIL/3 翻译成人话，光看编码判断不了这条规则有多宽 -->
          <template v-if="column.dataIndex === 'matchRule'">
            <div class="cell-stack">
              <a-tag class="cell-tag">{{ record.matchRuleDesc }}</a-tag>
              <span class="cell-sub">{{ ruleExplain(record, item) }}</span>
            </div>
          </template>

          <template v-if="column.dataIndex === 'hitRate'">
            <span class="text-slate-500">{{ percent(record.hitRate) }}</span>
          </template>

          <!-- 净中奖率才是乘发行量的那个数：扣掉了被更高奖级抢先认领的部分。
               为 0 意味着这一级永远认领不到票 -->
          <template v-if="column.dataIndex === 'netRate'">
            <span v-if="record.netRate == null" class="cell-sub">-</span>
            <span v-else-if="isZero(record.netRate)" class="text-dead">0（永不中奖）</span>
            <span v-else class="font-bold">{{ percent(record.netRate) }}</span>
          </template>

          <template v-if="column.dataIndex === 'expectedWinCount'">
            <span :class="isZero(record.netRate) ? 'text-dead' : ''">{{ count(record.expectedWinCount) }}</span>
          </template>

          <template v-if="column.dataIndex === 'prizeCode'">
            <div class="cell-stack">
              <span v-if="record.prizeName"> {{ prizeIcon(record.prizeType) }} {{ record.prizeName }} </span>
              <span v-else class="text-dead">奖品缺失</span>
              <span class="cell-sub font-mono">{{ record.prizeCode || '—' }}</span>
            </div>
          </template>

          <template v-if="column.dataIndex === 'prizeValue'">
            <span class="cell-sub">{{ record.prizeValue == null ? '—' : `￥${money(record.prizeValue)}` }}</span>
          </template>

          <template v-if="column.dataIndex === 'expectedCost'">
            <span class="font-bold">{{ record.expectedCost == null ? '—' : `￥${money(record.expectedCost)}` }}</span>
          </template>

          <!-- 只标记不修复：奖级配置是资损敏感数据，一键改错的代价远大于让人多点两下 -->
          <template v-if="column.dataIndex === 'issue'">
            <div v-if="record.issueList.length === 0" class="text-ok">✓</div>
            <div v-else class="cell-stack">
              <a-tooltip v-for="(issue, idx) in record.issueList" :key="idx" :title="issue.message">
                <a-tag :color="issue.level === 'DANGER' ? 'red' : 'orange'" class="cell-tag issue-tag">
                  {{ issue.level === 'DANGER' ? '危险' : '警告' }} · {{ shortIssue(issue.message) }}
                </a-tag>
              </a-tooltip>
            </div>
          </template>
        </template>
      </a-table>
    </a-card>

    <div v-if="list.length > 0" class="solvela-query-table-page">
      <a-pagination
        showSizeChanger
        showQuickJumper
        show-less-items
        :pageSizeOptions="PAGE_SIZE_OPTIONS"
        :defaultPageSize="queryForm.pageSize"
        v-model:current="queryForm.pageNum"
        v-model:pageSize="queryForm.pageSize"
        :total="total"
        @change="queryData"
        @showSizeChange="queryData"
        :show-total="(total) => `共${total}个玩法`"
      />
    </div>
  </a-spin>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { lotteryPrizeRuleApi } from '/src/api/business/lottery/lottery-prize-rule-api';
  import { lotteryConfigApi } from '/src/api/business/lottery/lottery-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { lotteryStatusOf, prizeIcon } from '/@/constants/business/lottery/lottery-const';

  const router = useRouter();

  // ---------------------------- 奖级明细列 ----------------------------

  /*
   * 列的排布按「从左往右读一遍就知道这一级贵不贵、活没活着」来定：
   *   哪一级 → 规则多宽 → 单条命中率 → 净中奖率 → 预计中奖 → 发什么 → 单价 → 要赔多少 → 有没有毛病
   *
   * 单条命中率与净中奖率必须并排显示：两者一旦不等，差额就是被更高奖级抢走的部分，
   * 这是理解「为什么二等奖没我想的那么多」的唯一线索。
   */
  const ruleColumns = [
    { title: '奖级', dataIndex: 'prizeLevel', width: 90 },
    { title: '匹配规则', dataIndex: 'matchRule', width: 190 },
    { title: '单条命中率', dataIndex: 'hitRate', width: 110 },
    { title: '净中奖率', dataIndex: 'netRate', width: 120 },
    { title: '预计中奖', dataIndex: 'expectedWinCount', width: 110 },
    { title: '奖品', dataIndex: 'prizeCode', width: 200 },
    { title: '单价', dataIndex: 'prizeValue', width: 110 },
    { title: '预计赔付', dataIndex: 'expectedCost', width: 130 },
    { title: '体检', dataIndex: 'issue', width: 200 },
  ];

  // ---------------------------- 展示辅助 ----------------------------

  /*
   * 概率的量级跨度极大：9 位号码的全号命中率是 1e-9，而尾号1位是 0.1。
   * 固定小数位数会把小的那头全显示成 0.00%，所以按量级动态选有效位数。
   */
  function percent(rate) {
    if (rate == null) {
      return '-';
    }
    const value = Number(rate) * 100;
    if (value === 0) {
      return '0%';
    }
    if (value >= 1) {
      return `${value.toFixed(2)}%`;
    }
    // 小于 1% 的一律用有效数字，保证 0.00001% 这种量级也看得见
    return `${value.toPrecision(3)}%`;
  }

  function money(value) {
    if (value == null) {
      return '0.00';
    }
    return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function count(value) {
    if (value == null) {
      return '-';
    }
    const num = Number(value);
    // 预计注数是期望值，可能是小数（如 0.99 注）。小于 1 时保留两位，否则取整更好读
    return num < 1 ? num.toFixed(2) : Math.round(num).toLocaleString();
  }

  function isZero(rate) {
    return rate != null && Number(rate) === 0;
  }

  function hasDanger(record) {
    return record.issueList?.some((i) => i.level === 'DANGER');
  }

  // 告警文案很长（要说清「为什么这是问题」），标签上只放前半句，完整内容进 tooltip
  function shortIssue(message) {
    const cut = message.indexOf('，');
    return cut > 0 ? message.slice(0, cut) : message;
  }

  /**
   * 把匹配规则翻译成一句人话，说明这条规则「锁死几位、多少个号里中一个」。
   * 这是判断奖级宽窄最直观的说法，比 TAIL/3 直观得多。
   */
  function ruleExplain(record, lottery) {
    if (!lottery.numberLength) {
      return '玩法配置缺失，无法计算';
    }
    if (record.matchRule === 'EXACT') {
      return `全 ${lottery.numberLength} 位一致，${Math.pow(10, lottery.numberLength).toLocaleString()} 个号中 1 个`;
    }
    if (record.matchRule === 'TAIL' || record.matchRule === 'HEAD') {
      const side = record.matchRule === 'TAIL' ? '末' : '首';
      if (!record.matchLength || record.matchLength > lottery.numberLength) {
        return '长度非法，永不命中';
      }
      return `${side} ${record.matchLength} 位一致，${Math.pow(10, record.matchLength).toLocaleString()} 个号中 1 个`;
    }
    return '规则值非法';
  }

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    lotteryCode: undefined,
    onlyIssue: false,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const loading = ref(false);
  const result = ref({});
  const list = ref([]);
  const total = ref(0);

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    queryData();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function toggleOnlyIssue() {
    queryForm.onlyIssue = !queryForm.onlyIssue;
    onSearch();
  }

  async function queryData() {
    loading.value = true;
    try {
      // 概览与明细同一次请求返回，卡片数字与下面列出来的必然一致
      const res = await lotteryPrizeRuleApi.analysis(queryForm);
      result.value = res.data || {};
      list.value = res.data?.list || [];
      total.value = res.data?.total || 0;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  onMounted(() => {
    queryData();
    loadLotteryOptions();
  });

  // ---------------------------- 玩法下拉 ----------------------------

  const lotteryOptions = ref([]);
  const lotteryLoading = ref(false);

  async function loadLotteryOptions() {
    lotteryLoading.value = true;
    try {
      const res = await lotteryConfigApi.queryPage({ pageNum: 1, pageSize: 200 });
      lotteryOptions.value = (res.data?.list || []).map((item) => ({
        value: item.lotteryCode,
        label: `${item.lotteryName}（${item.lotteryCode}）`,
      }));
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      lotteryLoading.value = false;
    }
  }

  // ---------------------------- 跳转工作台 ----------------------------

  /**
   * 奖级规则的唯一写入口是工作台，本页只负责把人准确送过去。
   * 带 activityCode + lotteryCode 深链，落地即定位到这个玩法的引擎配置 Tab。
   */
  function gotoWorkbench(item) {
    router.push({
      path: '/business/lottery/lottery-workbench',
      query: {
        activityCode: item.activityCode,
        lotteryCode: item.lotteryCode,
      },
    });
  }
</script>

<style lang="less" scoped>
  .overview-row {
    margin-bottom: 12px;
  }

  .overview-card {
    padding: 12px 16px;
    background: #fff;
    border: 1px solid #e2e8f0;
    border-left: 4px solid #cbd5e1;
    border-radius: 6px;
    transition: all 0.2s;
  }

  .tone-danger {
    cursor: pointer;
    border-left-color: #ef4444;

    &:hover {
      box-shadow: 0 2px 10px rgb(15 23 42 / 8%);
    }

    &.active {
      background: #f8fafc;
      box-shadow: 0 2px 10px rgb(15 23 42 / 10%);
    }

    .overview-card-value {
      color: #dc2626;
    }
  }
  .tone-primary {
    border-left-color: #3b82f6;
  }
  .tone-warning {
    border-left-color: #f59e0b;

    .overview-card-value {
      color: #d97706;
    }
  }

  .overview-card-label {
    font-size: 12px;
    color: #64748b;
  }

  .overview-card-hint {
    margin-left: 4px;
    color: #cbd5e1;
  }

  .overview-card-value {
    margin-top: 2px;
    font-size: 26px;
    font-weight: 700;
    line-height: 1.2;
    color: #0f172a;
  }

  .overview-card-sub {
    margin-left: 6px;
    font-size: 12px;
    font-weight: 500;
    color: #f59e0b;
  }

  .overview-card-foot {
    margin-top: 2px;
    font-size: 11px;
    color: #cbd5e1;
  }

  .lottery-card {
    margin-bottom: 12px;
    border-left: 3px solid #e2e8f0;

    &.has-danger {
      border-left-color: #ef4444;
    }
  }

  .lottery-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  .lottery-title {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .lottery-name {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }

  .lottery-code {
    font-family: monospace;
    font-size: 12px;
    color: #94a3b8;
  }

  .lottery-metrics {
    display: flex;
    gap: 28px;
    padding: 10px 14px;
    margin-bottom: 10px;
    background: #f8fafc;
    border-radius: 6px;
  }

  .metric-label {
    font-size: 11px;
    color: #94a3b8;
  }

  .metric-value {
    font-size: 15px;
    font-weight: 600;
    color: #0f172a;
  }

  .metric-cost .metric-value {
    color: #d97706;
  }

  .level-badge {
    font-weight: 600;
    color: #0f172a;
  }

  .cell-stack {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .cell-sub {
    font-size: 11px;
    color: #94a3b8;
  }

  .cell-tag {
    margin: 0;
    align-self: flex-start;
  }

  .issue-tag {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  // 净中奖率为 0 = 这一级永远认领不到票，是最隐蔽的一种坏配置，必须显眼
  .text-dead {
    font-weight: 600;
    color: #dc2626;
  }

  .text-ok {
    color: #10b981;
  }

  :deep(.row-danger) > td {
    background-color: #fef2f2;
  }
</style>
