<!--
  * 抽奖记录与转化漏斗
  *
  * 定位：抽奖流水的查询与效果分析，<b>只读</b>。
  *
  * ⚠️ 本页不提供新建/编辑/删除，服务端也已下掉那四个接口。
  * 抽奖流水是发奖凭证与对账依据：用户说「我明明抽中了」、财务对「这个月发了多少奖」，
  * 依据都是这张表。后台能改能删，等于这套审计不存在。
  * 而且它们从来就没有正当用途 —— 流水由抽奖链路自己写入，
  * 没有任何场景需要人工补录或修改一条抽奖记录。
  *
  * 顶部漏斗回答的是「翻十页流水也答不出来」的问题：中奖率多少、
  * 多少人撞上「手慢了」、哪个奖发得最多。其中<b>库存不足率</b>最该被盯住 ——
  * 它不是「没中奖」，而是「系统没东西可给」，偏高说明奖池缺货或兜底失效。
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 09:21:26
  * @Copyright  weolwo
-->
<template>
  <!---------- 转化漏斗 begin ----------->
  <!--
    折叠开关：漏斗默认展开（它是本页新增的价值所在），但排查具体某条流水时
    这几张卡片纯属占地方 —— 折叠后把关键结论压成一行摘要仍留在视线里，
    不至于收起来就完全看不见了。折叠状态记在 localStorage，
    免得每次进页面都要再点一次。
  -->
  <div class="funnel-toggle">
    <a-button type="link" size="small" class="funnel-toggle-btn" @click="funnelOpen = !funnelOpen">
      <template #icon>
        <DownOutlined v-if="funnelOpen" />
        <RightOutlined v-else />
      </template>
      转化漏斗
    </a-button>
    <span v-if="!funnelOpen" class="funnel-summary">
      共 {{ num(funnel.totalCount) }} 次 · 中奖 {{ percent(funnel.hitRate) }} ·
      <span :class="Number(funnel.noStockRate ?? 0) >= 0.2 ? 'summary-alert' : ''">
        库存不足 {{ percent(funnel.noStockRate) }}
      </span>
    </span>
  </div>

  <div v-show="funnelOpen">
  <a-row :gutter="12" class="overview-row">
    <a-col :span="5">
      <div class="overview-card tone-primary">
        <div class="overview-card-label">抽奖总次数</div>
        <div class="overview-card-value">{{ num(funnel.totalCount) }}</div>
        <div class="overview-card-foot">
          {{ num(funnel.memberCount) }} 人参与 · 人均 {{ funnel.drawPerMember ?? '-' }} 次
        </div>
      </div>
    </a-col>
    <a-col :span="5">
      <div class="overview-card tone-success">
        <div class="overview-card-label">中奖</div>
        <div class="overview-card-value">{{ percent(funnel.hitRate) }}</div>
        <div class="overview-card-foot">{{ num(funnel.hitCount) }} 次中奖</div>
      </div>
    </a-col>
    <a-col :span="5">
      <div class="overview-card" :class="noStockTone">
        <div class="overview-card-label">
          库存不足
          <a-tooltip title="用户看到的是「手慢了，奖品已被抽完」。这不是「没中奖」，而是系统没东西可给 —— 偏高说明奖池缺货或兜底奖项失效">
            <QuestionCircleOutlined class="overview-card-hint" />
          </a-tooltip>
        </div>
        <div class="overview-card-value">{{ percent(funnel.noStockRate) }}</div>
        <div class="overview-card-foot">{{ num(funnel.noStockCount) }} 次「手慢了」</div>
      </div>
    </a-col>
    <a-col :span="5">
      <div class="overview-card tone-muted">
        <div class="overview-card-label">未中奖</div>
        <div class="overview-card-value">{{ num(funnel.missCount) }}</div>
        <div class="overview-card-foot">正常未中奖，非缺货</div>
      </div>
    </a-col>
    <a-col :span="4">
      <div class="overview-card" :class="funnel.errorCount > 0 ? 'tone-danger' : 'tone-muted'">
        <div class="overview-card-label">异常</div>
        <div class="overview-card-value">{{ num(funnel.errorCount) }}</div>
        <div class="overview-card-foot">{{ funnel.errorCount > 0 ? '需要排查' : '无异常' }}</div>
      </div>
    </a-col>
  </a-row>

  <!-- 奖品发放分布：哪个奖发得最多、发出去多少钱。
       中奖流水里全都有，只是从来没人算过 -->
  <a-card v-if="funnel.prizeHitList && funnel.prizeHitList.length > 0" size="small" :bordered="false" class="prize-card">
    <div class="prize-head">奖品发放分布（共 {{ num(funnel.hitCount) }} 次中奖）</div>
    <div class="prize-list">
      <div v-for="item in funnel.prizeHitList" :key="item.prizeCode" class="prize-row">
        <div class="prize-name">
          {{ prizeIcon(item.prizeType) }} {{ item.prizeName || item.prizeCode }}
        </div>
        <div class="prize-bar-wrap">
          <div class="prize-bar" :style="{ width: sharePercent(item) + '%' }"></div>
        </div>
        <div class="prize-num">{{ num(item.hitCount) }} 次 · {{ percent(item.hitShare) }}</div>
        <div class="prize-value">{{ item.issuedValue == null ? '—' : `￥${money(item.issuedValue)}` }}</div>
      </div>
    </div>
  </a-card>
  </div>
  <!---------- 转化漏斗 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <!-- 活动与奖池都是十位随机码，手输等于必须先去别处抄一遍，而且是精确匹配 —— 改成下拉 -->
      <a-form-item label="活动" class="smart-query-form-item">
        <a-select
          style="width: 240px"
          v-model:value="queryForm.activityCode"
          :options="activityOptions"
          :loading="activityLoading"
          placeholder="全部活动"
          allowClear
          show-search
          option-filter-prop="label"
          @change="onActivityChange"
        />
      </a-form-item>
      <a-form-item label="奖池" class="smart-query-form-item">
        <a-select
          style="width: 240px"
          v-model:value="queryForm.poolCode"
          :options="poolOptions"
          :loading="poolLoading"
          :placeholder="queryForm.activityCode ? '本活动全部奖池' : '全部奖池'"
          allowClear
          show-search
          option-filter-prop="label"
          @change="onSearch"
        />
      </a-form-item>
      <a-form-item label="会员名" class="smart-query-form-item">
        <a-input-number style="width: 160px" v-model:value="queryForm.memberId" placeholder="会员号" :controls="false" :precision="0" @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 140px" v-model:value="queryForm.status" :options="DRAW_STATUS_OPTIONS" placeholder="全部" allowClear @change="onSearch" />
      </a-form-item>
      <a-form-item label="请求ID" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.traceId" placeholder="排查单次抽奖用" @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 230px" @change="onChangeCreateTime" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button type="primary" @click="onSearch">
          <template #icon>
            <SearchOutlined />
          </template>
          查询
        </a-button>
        <a-button @click="resetQuery" class="smart-margin-left10">
          <template #icon>
            <ReloadOutlined />
          </template>
          重置
        </a-button>
      </a-form-item>
    </a-row>
  </a-form>
  <!---------- 查询表单form end ----------->

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-alert type="info" show-icon class="mb-3">
      <template #message><span class="text-xs">抽奖流水只读：它是发奖凭证与对账依据，不提供修改与删除</span></template>
    </a-alert>

    <a-table
      size="small"
      :scroll="{ x: 1100, y: 800 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
      :row-class-name="(record) => (record.status === DRAW_STATUS_ENUM.ERROR.value ? 'row-danger' : '')"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="drawStatusOf(text).color">{{ drawStatusOf(text).desc }}</a-tag>
        </template>

        <!-- 中奖才显示奖品；未中奖/缺货行里的 prize_code 是「本来要给你的那个候选奖项」，
             直接当奖品展示会让人以为发出去了 -->
        <template v-if="column.dataIndex === 'prizeCode'">
          <div v-if="record.status === DRAW_STATUS_ENUM.WIN.value" class="cell-stack">
            <span class="font-bold">{{ record.prizeCode }}</span>
            <span class="cell-sub">奖项 #{{ record.prizeItemId }}</span>
          </div>
          <div v-else-if="record.prizeCode" class="cell-stack">
            <span class="cell-sub">候选 {{ record.prizeCode }}</span>
            <span class="cell-sub">（未发出）</span>
          </div>
          <span v-else class="cell-sub">—</span>
        </template>

        <template v-if="column.dataIndex === 'poolCode'">
          <div class="cell-stack">
            <span class="font-mono">{{ poolNameOf(record.poolCode) }}</span>
            <span class="cell-sub font-mono">{{ record.activityCode }}</span>
          </div>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
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
        :show-total="(total) => `共${total}条`"
      />
    </div>
  </a-card>
</template>
<script setup>
  import { computed, reactive, ref, watch, onMounted } from 'vue';
  import { DownOutlined, QuestionCircleOutlined, RightOutlined } from '@ant-design/icons-vue';
  import { drawPrizeLogApi } from '/@/api/business/draw/draw-prize-log/draw-prize-log-api';
  import { prizePoolConfigApi } from '/@/api/business/draw/prize-pool-config/prize-pool-config-api';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { DRAW_STATUS_ENUM, DRAW_STATUS_OPTIONS, drawStatusOf } from '/@/constants/business/draw/draw-prize-log/draw-prize-log-const';
  import { prizeIcon } from '/@/constants/business/lottery/lottery-const';

  // 与抽奖工作台同源：活动下拉只取抽奖类活动
  const ACTIVITY_TYPE_DRAW = 'DRAW';

  /*
   * 列按「谁、在哪抽的、结果如何、什么时候」排。
   * id / 租户id / 奖项ID 不再单列：奖项ID 并进奖品列，其余排查时走接口。
   */
  const columns = [
    { title: '会员号', dataIndex: 'memberId', width: 130, ellipsis: true },
    { title: '会员账号', dataIndex: 'memberName', width: 140, ellipsis: true },
    { title: '奖池 / 活动', dataIndex: 'poolCode', width: 220 },
    { title: '状态', dataIndex: 'status', width: 100 },
    { title: '奖品', dataIndex: 'prizeCode', width: 180 },
    { title: '备注', dataIndex: 'remark', width: 200, ellipsis: true },
    { title: '请求ID', dataIndex: 'traceId', width: 200, ellipsis: true },
    { title: '时间', dataIndex: 'createTime', width: 170, ellipsis: true },
  ];

  // ---------------------------- 展示辅助 ----------------------------

  function num(value) {
    return value == null ? '-' : Number(value).toLocaleString();
  }

  function percent(rate) {
    if (rate == null) {
      return '-';
    }
    const v = Number(rate) * 100;
    if (v === 0) {
      return '0%';
    }
    return v >= 1 ? `${v.toFixed(1)}%` : `${v.toPrecision(2)}%`;
  }

  function money(value) {
    if (value == null) {
      return '0.00';
    }
    return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function sharePercent(item) {
    return Math.max(1, Math.round(Number(item.hitShare || 0) * 100));
  }

  /*
   * 库存不足率的分档按「用户体验掉到什么程度」定：
   *   < 5%   偶发，正常
   *   < 20%  已经能被感知，该补货了
   *   ≥ 20%  每五个人就有一个抽了个寂寞，属于事故
   */
  const noStockTone = computed(() => {
    const v = Number(funnel.value.noStockRate ?? 0);
    if (v >= 0.2) {
      return 'tone-critical';
    }
    return v >= 0.05 ? 'tone-warning' : 'tone-muted';
  });

  function poolNameOf(poolCode) {
    return poolMap.value[poolCode] || poolCode;
  }

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    activityCode: undefined,
    poolCode: undefined,
    memberId: undefined,
    traceId: undefined,
    status: undefined,
    createTime: [],
    createTimeBegin: undefined,
    createTimeEnd: undefined,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);
  const funnel = ref({});

  /*
   * 漏斗折叠状态。默认展开 —— 它是本页新增的价值所在，收起来就退回成一张裸流水表了。
   * 但排查具体某条流水时那几张卡片确实占地方，所以给个开关，
   * 并把选择记在 localStorage：折叠是个人习惯，不该每次进页面都重新点一次。
   * 读取时用 try/catch 包住：隐私模式或存储被禁用时 localStorage 会抛异常，
   * 不能让一个折叠状态把整个页面的初始化带崩。
   */
  const FUNNEL_OPEN_KEY = 'draw-prize-log:funnel-open';
  const funnelOpen = ref(readFunnelOpen());

  function readFunnelOpen() {
    try {
      // 只有显式存过 '0' 才折叠，其余情况（含从未存过）都展开
      return localStorage.getItem(FUNNEL_OPEN_KEY) !== '0';
    } catch (e) {
      return true;
    }
  }

  watch(funnelOpen, (open) => {
    try {
      localStorage.setItem(FUNNEL_OPEN_KEY, open ? '1' : '0');
    } catch (e) {
      // 存不了就算了，折叠状态不值得打断用户
    }
  });

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    loadPoolOptions();
    reload();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    reload();
  }

  // 列表与漏斗一起刷：换了筛选条件只刷一个会让两者对不上
  async function reload() {
    await Promise.all([queryData(), loadFunnel()]);
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await drawPrizeLogApi.queryPage(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  async function loadFunnel() {
    try {
      // status 服务端刻意忽略：它是漏斗要拆解的维度本身
      const res = await drawPrizeLogApi.funnel(queryForm);
      funnel.value = res.data || {};
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  function onChangeCreateTime(dates, dateStrings) {
    // 结束日补到当天 23:59:59：否则选「今天~今天」会把今天白天的流水全筛掉
    queryForm.createTimeBegin = dateStrings[0] ? `${dateStrings[0]} 00:00:00` : undefined;
    queryForm.createTimeEnd = dateStrings[1] ? `${dateStrings[1]} 23:59:59` : undefined;
  }

  onMounted(() => {
    reload();
    loadActivityOptions();
    loadPoolOptions();
  });

  // ---------------------------- 活动 / 奖池下拉 ----------------------------

  const activityOptions = ref([]);
  const activityLoading = ref(false);
  const poolOptions = ref([]);
  const poolLoading = ref(false);
  // poolCode -> poolName，列表里把随机码换成人看得懂的名字
  const poolMap = ref({});

  /*
   * includeInactive 传 true：optionList 默认过滤掉已下线/已过期的活动，
   * 而流水恰恰要能查历史活动 —— 活动结束之后才是对账与客诉的高峰期。
   */
  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList(ACTIVITY_TYPE_DRAW, true);
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

  async function loadPoolOptions() {
    poolLoading.value = true;
    try {
      const res = await prizePoolConfigApi.queryPage({
        pageNum: 1,
        pageSize: 200,
        activityCode: queryForm.activityCode || undefined,
      });
      const list = res.data?.list || [];
      poolOptions.value = list.map((item) => ({
        value: item.poolCode,
        label: `${item.poolName}（${item.poolCode}）`,
      }));
      // 名称映射始终按全量维护：筛了某个活动之后，历史流水里别的活动的奖池仍要显示得出名字
      const map = { ...poolMap.value };
      list.forEach((item) => {
        map[item.poolCode] = item.poolName;
      });
      poolMap.value = map;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      poolLoading.value = false;
    }
  }

  async function onActivityChange() {
    queryForm.poolCode = undefined;
    await loadPoolOptions();
    onSearch();
  }
</script>

<style lang="less" scoped>
  .funnel-toggle {
    display: flex;
    gap: 10px;
    align-items: center;
    margin-bottom: 6px;
  }

  .funnel-toggle-btn {
    padding: 0;
    font-size: 13px;
    font-weight: 600;
  }

  // 折叠后仍把最关键的三个数留在这一行，不至于收起来就完全看不见
  .funnel-summary {
    font-size: 12px;
    color: #64748b;
  }

  .summary-alert {
    font-weight: 600;
    color: #b91c1c;
  }

  .overview-row {
    margin-bottom: 12px;
  }

  .overview-card {
    padding: 12px 16px;
    background: #fff;
    border: 1px solid #e2e8f0;
    border-left: 4px solid #cbd5e1;
    border-radius: 6px;
  }

  .tone-primary {
    border-left-color: #3b82f6;
  }

  .tone-success {
    border-left-color: #10b981;

    .overview-card-value {
      color: #059669;
    }
  }

  .tone-warning {
    border-left-color: #f59e0b;

    .overview-card-value {
      color: #d97706;
    }
  }

  .tone-critical {
    background: #fef2f2;
    border-left-color: #b91c1c;

    .overview-card-value {
      color: #b91c1c;
    }
  }

  .tone-danger {
    border-left-color: #ef4444;

    .overview-card-value {
      color: #dc2626;
    }
  }

  .tone-muted {
    border-left-color: #cbd5e1;
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

  .overview-card-foot {
    margin-top: 2px;
    font-size: 11px;
    color: #cbd5e1;
  }

  .prize-card {
    margin-bottom: 12px;
  }

  .prize-head {
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
  }

  .prize-row {
    display: flex;
    gap: 12px;
    align-items: center;
    padding: 3px 0;
  }

  .prize-name {
    width: 220px;
    overflow: hidden;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .prize-bar-wrap {
    flex: 1;
    height: 14px;
    overflow: hidden;
    background: #f1f5f9;
    border-radius: 3px;
  }

  .prize-bar {
    height: 100%;
    background: linear-gradient(90deg, #3b82f6, #60a5fa);
  }

  .prize-num {
    width: 130px;
    font-size: 12px;
    color: #64748b;
    text-align: right;
  }

  .prize-value {
    width: 110px;
    font-size: 12px;
    font-weight: 600;
    color: #d97706;
    text-align: right;
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

  :deep(.row-danger) > td {
    background-color: #fef2f2;
  }
</style>
