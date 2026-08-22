<!--
  * 用户号码记录与购彩漏斗
  *
  * 定位：号码明细查询 + 中奖与派发的效果分析，<b>只读</b>。
  *
  * ⚠️ 本页不提供新建/编辑，服务端也已下掉那两个接口。
  * 这张表存的是用户手里的号码本身，比抽奖流水更不能碰：security_sign 是防篡改签名、
  * win_status/prize_level/prize_code 是派奖依据、ticket_number 与 sequence_no 是 FPE 双射的两端。
  * 记录由领号链路写入、由开奖核销 SQL 批量更新，没有任何场景需要人工补录或修改一张号码。
  *
  * 顶部漏斗回答的是「翻十页号码也答不出来」的问题，其中<b>派发那一段是本页独有的</b>：
  * 中奖只是第一步，奖品要经派发链路真正到用户手上才算完 ——
  * 「已中奖但投递失败」是全模块最该被盯住的数字。
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 11:57:08
  * @Copyright  weolwo
-->
<template>
  <!---------- 购彩漏斗 begin ----------->
  <div class="funnel-toggle">
    <a-button type="link" size="small" class="funnel-toggle-btn" @click="funnelOpen = !funnelOpen">
      <template #icon>
        <DownOutlined v-if="funnelOpen" />
        <RightOutlined v-else />
      </template>
      购彩漏斗
    </a-button>
    <span v-if="!funnelOpen" class="funnel-summary">
      共 {{ num(funnel.totalCount) }} 张 · 中奖 {{ percent(funnel.winRate) }} ·
      <span :class="funnel.dispatchFailedCount > 0 ? 'summary-alert' : ''">
        派发失败 {{ num(funnel.dispatchFailedCount) }}
      </span>
    </span>
  </div>

  <div v-show="funnelOpen">
    <a-row :gutter="12" class="overview-row">
      <a-col :span="6">
        <div class="overview-card tone-primary">
          <div class="overview-card-label">号码总数</div>
          <div class="overview-card-value">{{ num(funnel.totalCount) }}</div>
          <div class="overview-card-foot">
            {{ num(funnel.memberCount) }} 人参与 · 人均 {{ funnel.ticketPerMember ?? '-' }} 张
          </div>
        </div>
      </a-col>
      <a-col :span="6">
        <div class="overview-card tone-success">
          <div class="overview-card-label">
            中奖率
            <a-tooltip title="分母是「已开奖」的号码数，未开奖的不计入 —— 否则活动刚开始时中奖率会无限接近 0，那个数字没有意义">
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">{{ percent(funnel.winRate) }}</div>
          <div class="overview-card-foot">中奖 {{ num(funnel.winCount) }} · 未中奖 {{ num(funnel.loseCount) }}</div>
        </div>
      </a-col>
      <a-col :span="6">
        <div class="overview-card tone-muted">
          <div class="overview-card-label">未开奖</div>
          <div class="overview-card-value">{{ num(funnel.waitCount) }}</div>
          <div class="overview-card-foot">等待所属期号开奖</div>
        </div>
      </a-col>
      <a-col :span="6">
        <!-- 派发失败 = 用户中了奖但东西没发出去，没人主动查就不会被发现 -->
        <div class="overview-card" :class="funnel.dispatchFailedCount > 0 ? 'tone-critical' : 'tone-muted'">
          <div class="overview-card-label">
            奖品派发
            <a-tooltip title="中奖只是第一步，奖品要经派发链路真正到用户手上才算完。投递失败意味着用户中了奖却没拿到东西">
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">
            {{ num(funnel.dispatchFailedCount) }}
            <span class="overview-card-slash">失败</span>
          </div>
          <div class="overview-card-foot">
            已投递 {{ num(funnel.dispatchedCount) }} · 待派发 {{ num(funnel.dispatchWaitCount) }}
          </div>
        </div>
      </a-col>
    </a-row>

    <!-- 数据一致性体检：本页的 add/update 已下掉，但历史脏数据仍可能在 -->
    <a-alert v-for="(issue, idx) in funnel.issueList || []" :key="idx" type="error" show-icon class="mb-2">
      <template #message><span class="text-xs">{{ issue }}</span></template>
    </a-alert>

    <a-card v-if="funnel.prizeLevelList && funnel.prizeLevelList.length > 0" size="small" :bordered="false" class="prize-card">
      <div class="prize-head">奖级分布（共 {{ num(funnel.winCount) }} 注中奖）</div>
      <div class="prize-list">
        <div v-for="item in funnel.prizeLevelList" :key="`${item.prizeLevel}-${item.prizeCode}`" class="prize-row">
          <div class="prize-name">
            <span class="level-badge">{{ item.prizeLevel }} 等奖</span>
            {{ prizeIcon(item.prizeType) }} {{ item.prizeName || item.prizeCode || '（奖品已删除）' }}
          </div>
          <div class="prize-bar-wrap">
            <div class="prize-bar" :style="{ width: sharePercent(item) + '%' }"></div>
          </div>
          <div class="prize-num">{{ num(item.winCount) }} 注 · {{ percent(item.winShare) }}</div>
          <div class="prize-value">{{ item.issuedValue == null ? '—' : `￥${money(item.issuedValue)}` }}</div>
        </div>
      </div>
    </a-card>
  </div>
  <!---------- 购彩漏斗 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <!-- 玩法与期号都是编码，手输且是精确匹配，少打一个字符就是空列表 —— 改成级联下拉 -->
      <a-form-item label="彩票玩法" class="smart-query-form-item">
        <a-select
          style="width: 230px"
          v-model:value="queryForm.lotteryCode"
          :options="lotteryOptions"
          :loading="lotteryLoading"
          placeholder="全部玩法"
          allowClear
          show-search
          option-filter-prop="label"
          @change="onLotteryChange"
        />
      </a-form-item>
      <a-form-item label="期号" class="smart-query-form-item">
        <a-select
          style="width: 180px"
          v-model:value="queryForm.issueNo"
          :options="issueOptions"
          :loading="issueLoading"
          :placeholder="queryForm.lotteryCode ? '本玩法全部期号' : '全部期号'"
          allowClear
          show-search
          option-filter-prop="label"
          @change="onSearch"
        />
      </a-form-item>
      <a-form-item label="号码" class="smart-query-form-item">
        <a-input style="width: 150px" v-model:value="queryForm.ticketNumber" placeholder="精确查号" @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="会员名" class="smart-query-form-item">
        <a-input-number style="width: 150px" v-model:value="queryForm.memberId" placeholder="会员号" :controls="false" :precision="0" @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="中奖状态" class="smart-query-form-item">
        <a-select style="width: 130px" v-model:value="queryForm.winStatus" :options="WIN_STATUS_OPTIONS" placeholder="全部" allowClear @change="onSearch" />
      </a-form-item>
      <a-form-item label="领号时间" class="smart-query-form-item">
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
      <template #message><span class="text-xs">号码记录只读：它是用户的中奖凭证，带防篡改签名，不提供修改</span></template>
    </a-alert>

    <a-table
      size="small"
      :scroll="{ x: 1200, y: 800 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
      :row-class-name="(record) => (record.dispatchStatus === DISPATCH_STATUS_ENUM.FAILURE.value ? 'row-danger' : '')"
    >
      <template #bodyCell="{ text, record, column }">
        <!-- 号码是本页的主角，等宽字体 + 加粗，方便肉眼比对 -->
        <template v-if="column.dataIndex === 'ticketNumber'">
          <div class="cell-stack">
            <span class="ticket-number">{{ record.ticketNumber }}</span>
            <span class="cell-sub">序号 {{ record.sequenceNo }}</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'lotteryCode'">
          <div class="cell-stack">
            <span>{{ lotteryNameOf(record.lotteryCode) }}</span>
            <span class="cell-sub font-mono">{{ record.issueNo }}</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'winStatus'">
          <a-tag :color="winStatusOf(text).color">{{ winStatusOf(text).desc }}</a-tag>
        </template>

        <!-- 未中奖的 prize_level 是 99 占位，不是奖级，直接显示会让人以为中了 99 等奖 -->
        <template v-if="column.dataIndex === 'prizeLevel'">
          <div v-if="record.winStatus === WIN_STATUS_ENUM.SUCCESS_MATCH.value" class="cell-stack">
            <span class="level-badge">{{ record.prizeLevel }} 等奖</span>
            <span class="cell-sub font-mono">{{ record.prizeCode || '未绑定奖品' }}</span>
          </div>
          <span v-else class="cell-sub">—</span>
        </template>

        <!-- 派发状态只对已中奖的号码有意义：未中奖的本就无需派发 -->
        <template v-if="column.dataIndex === 'dispatchStatus'">
          <a-tag v-if="record.winStatus === WIN_STATUS_ENUM.SUCCESS_MATCH.value" :color="dispatchStatusOf(text).color">
            {{ dispatchStatusOf(text).desc }}
          </a-tag>
          <span v-else class="cell-sub">无需派发</span>
        </template>

        <!-- 签名很长，列表里只留个「有/无」的判断，完整值进 tooltip -->
        <template v-if="column.dataIndex === 'securitySign'">
          <a-tooltip v-if="record.securitySign" :title="record.securitySign">
            <span class="text-ok">✓ 已签名</span>
          </a-tooltip>
          <span v-else class="text-dead">✗ 无签名</span>
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
  import { reactive, ref, watch, onMounted } from 'vue';
  import { DownOutlined, QuestionCircleOutlined, RightOutlined } from '@ant-design/icons-vue';
  import { lotteryRecordApi } from '/src/api/business/lottery/lottery-record-api';
  import { lotteryConfigApi } from '/src/api/business/lottery/lottery-config-api';
  import { lotteryIssueApi } from '/src/api/business/lottery/lottery-issue-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    DISPATCH_STATUS_ENUM,
    dispatchStatusOf,
    prizeIcon,
    WIN_STATUS_ENUM,
    WIN_STATUS_OPTIONS,
    winStatusOf,
  } from '/@/constants/business/lottery/lottery-const';

  /*
   * 列按「哪张号码 → 属于谁 → 什么玩法哪一期 → 中没中 → 发出去没有」排。
   * id / 租户id / 创建人 等审计列不再单列，排查时走原始行接口。
   */
  const columns = [
    { title: '号码', dataIndex: 'ticketNumber', width: 150 },
    { title: '会员号', dataIndex: 'memberId', width: 130, ellipsis: true },
    { title: '会员账号', dataIndex: 'memberName', width: 140, ellipsis: true },
    { title: '玩法 / 期号', dataIndex: 'lotteryCode', width: 200 },
    { title: '中奖状态', dataIndex: 'winStatus', width: 110 },
    { title: '奖级 / 奖品', dataIndex: 'prizeLevel', width: 160 },
    { title: '派发状态', dataIndex: 'dispatchStatus', width: 130 },
    { title: '防篡改签名', dataIndex: 'securitySign', width: 110 },
    { title: '领号时间', dataIndex: 'obtainTime', width: 170, ellipsis: true },
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
    return v >= 1 ? `${v.toFixed(2)}%` : `${v.toPrecision(2)}%`;
  }

  function money(value) {
    if (value == null) {
      return '0.00';
    }
    return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function sharePercent(item) {
    return Math.max(1, Math.round(Number(item.winShare || 0) * 100));
  }

  function lotteryNameOf(code) {
    return lotteryMap.value[code] || code;
  }

  // ---------------------------- 漏斗折叠 ----------------------------

  /*
   * 默认展开；排查具体某张号码时可折叠，折叠后把关键结论压成一行摘要留在视线里。
   * 状态记在 localStorage，读写都用 try/catch 包住 ——
   * 隐私模式下 localStorage 会抛异常，不能让折叠状态把页面初始化带崩。
   */
  const FUNNEL_OPEN_KEY = 'lottery-record:funnel-open';
  const funnelOpen = ref(readFunnelOpen());

  function readFunnelOpen() {
    try {
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

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    lotteryCode: undefined,
    issueNo: undefined,
    ticketNumber: undefined,
    memberId: undefined,
    winStatus: undefined,
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

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    loadIssueOptions();
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
      const res = await lotteryRecordApi.queryPage(queryForm);
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
      // winStatus 服务端刻意忽略：它是漏斗要拆解的维度本身
      const res = await lotteryRecordApi.funnel(queryForm);
      funnel.value = res.data || {};
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  function onChangeCreateTime(dates, dateStrings) {
    // 结束日补到当天 23:59:59：否则选「今天~今天」会把今天白天的记录全筛掉
    queryForm.createTimeBegin = dateStrings[0] ? `${dateStrings[0]} 00:00:00` : undefined;
    queryForm.createTimeEnd = dateStrings[1] ? `${dateStrings[1]} 23:59:59` : undefined;
  }

  onMounted(() => {
    reload();
    loadLotteryOptions();
    loadIssueOptions();
  });

  // ---------------------------- 玩法 / 期号级联下拉 ----------------------------

  const lotteryOptions = ref([]);
  const lotteryLoading = ref(false);
  const issueOptions = ref([]);
  const issueLoading = ref(false);
  // lotteryCode -> lotteryName，列表里把编码换成人看得懂的名字
  const lotteryMap = ref({});

  async function loadLotteryOptions() {
    lotteryLoading.value = true;
    try {
      const res = await lotteryConfigApi.queryPage({ pageNum: 1, pageSize: 200 });
      const list = res.data?.list || [];
      lotteryOptions.value = list.map((item) => ({
        value: item.lotteryCode,
        label: `${item.lotteryName}（${item.lotteryCode}）`,
      }));
      const map = {};
      list.forEach((item) => {
        map[item.lotteryCode] = item.lotteryName;
      });
      lotteryMap.value = map;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      lotteryLoading.value = false;
    }
  }

  // 期号按当前玩法收窄；未选玩法时给全量（期号在玩法内唯一，跨玩法可能重名）
  async function loadIssueOptions() {
    issueLoading.value = true;
    try {
      const res = await lotteryIssueApi.queryPage({
        pageNum: 1,
        pageSize: 200,
        lotteryCode: queryForm.lotteryCode || undefined,
      });
      issueOptions.value = (res.data?.list || []).map((item) => ({
        value: item.issueNo,
        label: item.issueNo,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      issueLoading.value = false;
    }
  }

  async function onLotteryChange() {
    queryForm.issueNo = undefined;
    await loadIssueOptions();
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

  .tone-critical {
    background: #fef2f2;
    border-left-color: #b91c1c;

    .overview-card-value {
      color: #b91c1c;
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

  .overview-card-slash {
    margin-left: 4px;
    font-size: 14px;
    font-weight: 500;
    color: #94a3b8;
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
    width: 280px;
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
    background: linear-gradient(90deg, #ef4444, #f87171);
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

  .level-badge {
    margin-right: 4px;
    font-weight: 600;
    color: #dc2626;
  }

  .ticket-number {
    font-family: monospace;
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
    letter-spacing: 2px;
  }

  .cell-stack {
    display: flex;
    flex-direction: column;
    gap: 2px;
    align-items: flex-start;
  }

  .cell-sub {
    font-size: 11px;
    color: #94a3b8;
  }

  .text-ok {
    font-size: 12px;
    color: #10b981;
  }

  .text-dead {
    font-size: 12px;
    font-weight: 600;
    color: #dc2626;
  }

  :deep(.row-danger) > td {
    background-color: #fef2f2;
  }
</style>
