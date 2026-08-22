<!--
  * 奖励发放记录 / 发奖审批
  *
  * t_prize_log 是「实际发了什么奖」的审计流水，由派发链路写入。
  * 页面只做两件事：查看 + 审批。
  *
  * ⚠️ 生成器原本给了新增/编辑/删除三个入口，已全部移除 ——
  * 手工增改会让流水与真实发放脱节，而运营看的、对账依据的正是这张表。
  * 需要人工介入的只有审批（approve_mode=1 的奖品）。
  *
  * 顶部漏斗回答翻十页也答不出来的三件事：这段时间**发出去多少**（条数与价值是两个数，
  * 且价值必须按奖励类型分开算）、**有没有人挂在审批池里没人管**、**有没有奖卡在半路**。
  * ⚠️ 全页措辞一律是「已发出」而不是「已发放/已到账」：营销域只知道自己把指令发出去了，
  * 钱有没有真到用户手上是账务域的事，写成「已到账」会让运营以为用户收到了。
  *
  * ⚠️ 本系统有两套并存的审批，别混淆：
  *   · 本页是 prize 域，依据 t_prize_config.approve_mode，回答「这个奖该不该发给这个人」（运营视角）
  *   · 另一套是提案域，依据 t_promotion_config.review_level，回答「这笔钱该不该出」（财务视角）
  *   两层都配了审批就要各批一次，本页批过之后可能还停在提案域等财务。
  *
  * @Author:    alaric
  * @Date:      2026-07-28
-->
<template>
  <!---------- 奖励漏斗 begin ----------->
  <div class="funnel-toggle">
    <a-button type="link" size="small" class="funnel-toggle-btn" @click="funnelOpen = !funnelOpen">
      <template #icon>
        <DownOutlined v-if="funnelOpen" />
        <RightOutlined v-else />
      </template>
      奖励漏斗
    </a-button>
    <span v-if="!funnelOpen" class="funnel-summary">
      共 {{ num(funnel.totalCount) }} 条 · 已发出 {{ percent(funnel.successRate) }} ·
      <span :class="funnel.approvePendingCount > 0 ? 'summary-alert' : ''"> 待审 {{ num(funnel.approvePendingCount) }} </span>
      ·
      <span :class="funnel.stuckWaitingCount > 0 ? 'summary-alert' : ''"> 卡单 {{ num(funnel.stuckWaitingCount) }} </span>
    </span>
  </div>

  <div v-show="funnelOpen">
    <a-row :gutter="12" class="overview-row">
      <a-col :span="5">
        <div class="overview-card tone-primary">
          <div class="overview-card-label">发奖记录</div>
          <div class="overview-card-value">{{ num(funnel.totalCount) }}</div>
          <div class="overview-card-foot">涉及 {{ num(funnel.memberCount) }} 名会员</div>
        </div>
      </a-col>
      <a-col :span="5">
        <div class="overview-card tone-success">
          <div class="overview-card-label">
            已发出率
            <a-tooltip
              title="分母是记录总数：待审批、卡在等待执行本身就是一种结果（用户就是没拿到奖），剔出分母会让这个比率虚高。另外它只代表指令发出去了，不代表用户已到账——到账要去账务系统看"
            >
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">{{ percent(funnel.successRate) }}</div>
          <div class="overview-card-foot">已发出 {{ num(funnel.successCount) }} · 等待 {{ num(funnel.waitingCount) }}</div>
        </div>
      </a-col>
      <a-col :span="5">
        <!-- approve_mode=1 的奖唯一的出口就是有人来点「通过」，没人点它就一直停在这里 -->
        <div class="overview-card" :class="approveBacklogTone">
          <div class="overview-card-label">
            待审积压
            <a-tooltip
              title="approve_status=1，本页唯一需要人动手处理的东西：不点「通过」这些奖永远发不出去。只看条数看不出「挂了六天」，所以同时给出最久的一条等了多久"
            >
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">{{ num(funnel.approvePendingCount) }}</div>
          <div class="overview-card-foot">最久等待 {{ waitedText }}</div>
        </div>
      </a-col>
      <a-col :span="5">
        <!-- ⚠️ 这个数不等于故障：提案进了财务审批池时，发奖记录本就合理地停在等待执行 -->
        <div class="overview-card" :class="funnel.stuckWaitingCount > 0 ? 'tone-warning' : 'tone-muted'">
          <div class="overview-card-label">
            卡在等待执行
            <a-tooltip
              title="不在等审批却停在「等待执行」且 30 分钟没动过。不一定是故障——提案进了财务审批池时本就停在这里；但工程里没有任何重试/补偿任务，真断链了也不会自己恢复。请拿「来源单号」去提案记录页确认"
            >
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">{{ num(funnel.stuckWaitingCount) }}</div>
          <div class="overview-card-foot">需拿来源单号去提案页核对</div>
        </div>
      </a-col>
      <a-col :span="4">
        <div class="overview-card" :class="funnel.failedCount > 0 ? 'tone-critical' : 'tone-muted'">
          <div class="overview-card-label">发放失败</div>
          <div class="overview-card-value">{{ num(funnel.failedCount) }}</div>
          <div class="overview-card-foot">已驳回 {{ num(funnel.approveRejectedCount) }}</div>
        </div>
      </a-col>
    </a-row>

    <!-- 漏斗刻意不吃这两个筛选，筛了却不生效比不筛更让人困惑，故明说 -->
    <div v-if="queryForm.status !== undefined || queryForm.approveStatus !== undefined" class="funnel-note">
      漏斗不受「审批状态 / 执行状态」筛选影响：它们正是漏斗要拆解的维度，跟着筛会让已发出率恒为 100%
    </div>

    <a-alert v-for="(issue, idx) in funnel.issueList || []" :key="idx" type="error" show-icon class="mb-2">
      <template #message>
        <span class="text-xs">{{ issue }}</span>
      </template>
    </a-alert>

    <a-row :gutter="12">
      <a-col :span="10">
        <!-- 条数与价值是两个数：实测有过 315 条发奖记录里除了券一分钱都没发出去，只看条数完全看不出来 -->
        <a-card v-if="funnel.typeList && funnel.typeList.length > 0" size="small" :bordered="false" class="stat-card">
          <div class="stat-head">
            奖励类型（条数 / 价值双口径）
            <span class="stat-head-sub">积分与现金量纲不同，不合计</span>
          </div>
          <div v-for="item in funnel.typeList" :key="item.prizeType" class="asset-row">
            <a-tag :color="prizeTypeOf(item.prizeType).color">{{ prizeTypeOf(item.prizeType).desc }}</a-tag>
            <span class="asset-main">已发出 {{ money(item.successValue) }} {{ prizeValueUnitOf(item.prizeType) }}</span>
            <span class="asset-sub">{{ num(item.successCount) }}/{{ num(item.logCount) }} 条</span>
            <span class="asset-sub">
              在途 {{ money(item.waitingValue) }} · 失败
              <span :class="Number(item.failedValue) > 0 ? 'text-alert' : ''">{{ money(item.failedValue) }}</span>
            </span>
            <!-- 体值不是数字的行没有计进上面的金额，不标出来就是静默少算 -->
            <a-tooltip v-if="item.badValueCount > 0" title="这些行的奖励体值不是数字：价值没有计入左边的金额，发奖时也会当场失败">
              <span class="asset-sub text-alert">🔴 {{ num(item.badValueCount) }} 条体值异常</span>
            </a-tooltip>
          </div>
        </a-card>
      </a-col>
      <a-col :span="7">
        <a-card v-if="funnel.prizeList && funnel.prizeList.length > 0" size="small" :bordered="false" class="stat-card">
          <div class="stat-head">
            奖品分布（条形为已发出率）
            <span class="stat-head-sub">按发奖量 TOP 20</span>
          </div>
          <div v-for="item in funnel.prizeList" :key="item.prizeCode" class="stat-row">
            <a-tooltip :title="item.prizeName ? item.prizeName + '（' + item.prizeCode + '）' : item.prizeCode">
              <div class="stat-name">{{ item.prizeName || item.prizeCode }}</div>
            </a-tooltip>
            <div class="stat-bar-wrap">
              <div class="stat-bar" :style="{ width: barPercent(item.successRate) + '%' }"></div>
            </div>
            <div class="stat-num">{{ num(item.logCount) }} 条 · {{ percent(item.successRate) }}</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="7">
        <a-card v-if="funnel.failReasonList && funnel.failReasonList.length > 0" size="small" :bordered="false" class="stat-card">
          <div class="stat-head">
            失败原因
            <span class="stat-head-sub">按原文聚类，这张表没有分类编码</span>
          </div>
          <div v-for="item in funnel.failReasonList" :key="item.failReason || 'none'" class="stat-row">
            <a-tooltip :title="item.failReason || '（写入侧没写失败原因，排查时没有依据）'">
              <div class="stat-name" :class="item.failReason ? '' : 'stat-name-alert'">
                {{ item.failReason || '（未记录原因）' }}
              </div>
            </a-tooltip>
            <div class="stat-bar-wrap">
              <div class="stat-bar stat-bar-alert" :style="{ width: barPercent(item.failShare) + '%' }"></div>
            </div>
            <div class="stat-num">{{ num(item.failCount) }} · {{ percent(item.failShare) }}</div>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
  <!---------- 奖励漏斗 end ----------->

  <!---------- 查询表单 ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="会员名" class="smart-query-form-item">
        <a-input-number style="width: 160px" v-model:value="queryForm.memberId" placeholder="会员号" :controls="false" :precision="0" />
      </a-form-item>
      <a-form-item label="活动编码" class="smart-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.activityCode" placeholder="活动编码" allow-clear />
      </a-form-item>
      <a-form-item label="奖品编码" class="smart-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.prizeCode" placeholder="奖品编码" allow-clear />
      </a-form-item>
      <a-form-item label="审批状态" class="smart-query-form-item">
        <a-select
          style="width: 140px"
          v-model:value="queryForm.approveStatus"
          :options="APPROVE_STATUS_OPTIONS"
          placeholder="全部"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="执行状态" class="smart-query-form-item">
        <a-select
          style="width: 140px"
          v-model:value="queryForm.status"
          :options="DISPATCH_STATUS_OPTIONS"
          placeholder="全部"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="发放时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 220px" @change="onChangeCreateTime" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button type="primary" @click="onSearch">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="resetQuery" class="smart-margin-left10">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </a-form-item>
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <!-- 人工补发：新建一条待审批记录，审批通过后才真正走派发链路 -->
        <a-button type="primary" v-privilege="'prizeLog:add'" @click="showForm">
          <template #icon><PlusOutlined /></template>
          人工补发
        </a-button>
        <!-- 待审批是这个页面唯一需要「找出来处理」的东西，给个一键筛选 -->
        <a-button :type="onlyPending ? 'primary' : 'default'" @click="togglePending">
          <template #icon><AuditOutlined /></template>
          {{ onlyPending ? '显示全部' : '只看待审批' }}
        </a-button>
        <a-tag v-if="pendingCount > 0" color="orange" class="ml-2">当前筛选下有 {{ pendingCount }} 条待审批</a-tag>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.PRIZE_LOG" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      :scroll="{ x: 1500, y: 700 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'prizeType'">
          <span>{{ prizeIconOf(record.prizeType) }} {{ record.prizeType }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'approveStatus'">
          <a-tag :color="approveStatusOf(record.approveStatus).color" class="m-0">
            {{ approveStatusOf(record.approveStatus).desc }}
          </a-tag>
          <div v-if="record.approveBy" class="text-[11px] text-slate-400 mt-0.5">
            {{ record.approveBy }} · {{ fmt(record.approveTime) }}
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="dispatchStatusOf(record.status).color" class="m-0">
            {{ dispatchStatusOf(record.status).desc }}
          </a-tag>
          <!-- status=0 的正常含义只有「等待人工审批」；其余情况停在 0 说明链路中断了 -->
          <a-tooltip v-if="record.status === 0 && record.approveStatus !== 1" title="非待审批却停在等待执行，说明派发链路可能中断，需排查">
            <ExclamationCircleOutlined class="text-orange-500 ml-1" />
          </a-tooltip>
        </template>

        <template v-else-if="column.dataIndex === 'failReason'">
          <a-tooltip v-if="record.failReason" :title="record.failReason">
            <span class="text-red-500 text-xs">{{ record.failReason }}</span>
          </a-tooltip>
          <span v-else class="text-slate-300">-</span>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div v-if="record.approveStatus === 1" class="flex gap-1">
            <a-popconfirm
              :title="`确认发放【${record.prizeName}】给 ${record.memberName}？通过后立即派发，不可撤销。`"
              ok-text="确认通过"
              cancel-text="取消"
              @confirm="doApprove(record)"
            >
              <a-button type="link" size="small" class="p-0">通过</a-button>
            </a-popconfirm>
            <a-divider type="vertical" class="mx-1" />
            <a-button type="link" danger size="small" class="p-0" @click="openReject(record)">驳回</a-button>
          </div>
          <span v-else class="text-slate-300 text-xs">-</span>
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

    <!-- 驳回要填理由：留痕是审批的意义之一，没有理由的驳回事后无法复盘 -->
    <a-modal v-model:open="rejectVisible" title="驳回发放" ok-text="确认驳回" cancel-text="取消" @ok="doReject">
      <div v-if="rejectTarget" class="text-sm mb-3">
        将驳回给 <b>{{ rejectTarget.memberName }}</b> 的
        <b>{{ rejectTarget.prizeName }}</b>（{{ rejectTarget.prizeType }} / {{ rejectTarget.prizeValue }}）
      </div>
      <a-textarea v-model:value="rejectReason" :rows="3" placeholder="请填写驳回理由，将记入审批留痕" />
    </a-modal>

    <PrizeLogForm ref="formRef" @reloadList="reload" />
  </a-card>
</template>

<script setup>
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { message } from 'ant-design-vue';
  import {
    SearchOutlined,
    ReloadOutlined,
    AuditOutlined,
    ExclamationCircleOutlined,
    PlusOutlined,
    DownOutlined,
    RightOutlined,
    QuestionCircleOutlined,
  } from '@ant-design/icons-vue';
  import PrizeLogForm from './prize-log-form.vue';
  import { prizeLogApi } from '/src/api/business/prize/prize-log-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    APPROVE_STATUS_OPTIONS,
    DISPATCH_STATUS_OPTIONS,
    approveStatusOf,
    dispatchStatusOf,
    prizeTypeOf,
    prizeValueUnitOf,
  } from '/src/constants/business/prize/prize-log-const';
  import { prizeIcon } from '/@/constants/business/lottery/lottery-const';

  const columns = ref([
    { title: '会员号', dataIndex: 'memberId', width: 130, ellipsis: true },
    { title: '会员账号', dataIndex: 'memberName', width: 120, ellipsis: true },
    { title: '奖品名称', dataIndex: 'prizeName', width: 140, ellipsis: true },
    { title: '类型', dataIndex: 'prizeType', width: 110 },
    { title: '价值', dataIndex: 'prizeValue', width: 90 },
    { title: '奖级', dataIndex: 'prizeLevel', width: 60 },
    { title: '活动编码', dataIndex: 'activityCode', width: 120, ellipsis: true },
    { title: '审批状态', dataIndex: 'approveStatus', width: 130 },
    { title: '执行状态', dataIndex: 'status', width: 110 },
    { title: '失败原因', dataIndex: 'failReason', width: 180, ellipsis: true },
    { title: '来源单号', dataIndex: 'externalBizNo', width: 140, ellipsis: true },
    { title: '发放时间', dataIndex: 'createTime', width: 160, ellipsis: true },
    { title: '操作', dataIndex: 'action', width: 110, fixed: 'right' },
  ]);

  const queryFormState = {
    memberId: undefined,
    activityCode: undefined,
    prizeCode: undefined,
    approveStatus: undefined,
    status: undefined,
    createTime: [],
    createTimeBegin: undefined,
    createTimeEnd: undefined,
    pageNum: 1,
    pageSize: 20,
  };
  const queryForm = reactive({ ...queryFormState });
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);
  // 漏斗数据
  const funnel = ref({});
  const rejectVisible = ref(false);
  const rejectTarget = ref(null);
  const rejectReason = ref('');

  const onlyPending = computed(() => queryForm.approveStatus === 1);
  /*
   * 待审批条数取漏斗的数，不要再数当前页的行 ——
   * 数当前页只能数出「这 20 行里有几条」，翻到第二页数字就变了，
   * 而运营要问的是「一共还有多少条没人处理」。
   */
  const pendingCount = computed(() => Number(funnel.value.approvePendingCount || 0));

  function prizeIconOf(type) {
    return prizeIcon(type);
  }

  function fmt(value) {
    return value ? String(value).replace('T', ' ').slice(0, 16) : '';
  }

  function togglePending() {
    queryForm.approveStatus = onlyPending.value ? undefined : 1;
    onSearch();
  }

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
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

  async function loadFunnel() {
    try {
      // status / approveStatus 服务端刻意忽略：它们是漏斗要拆解的维度本身
      const res = await prizeLogApi.funnel(queryForm);
      funnel.value = res.data || {};
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await prizeLogApi.queryPage(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0];
    queryForm.createTimeEnd = dateStrings[1];
  }

  // ---------------------------- 审批 ----------------------------

  async function doApprove(record) {
    try {
      await prizeLogApi.approve(record.id);
      message.success('已通过，正在派发');
      // 重新拉取而不是本地改状态：通过后会立即走完整派发链路，
      // 执行状态可能变成成功、也可能失败（如预算耗尽），必须以服务端为准。
      // 漏斗一起刷：待审积压与已发出率都跟着这一次审批变了
      await reload();
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  function openReject(record) {
    rejectTarget.value = record;
    rejectReason.value = '';
    rejectVisible.value = true;
  }

  async function doReject() {
    if (!rejectReason.value.trim()) {
      message.error('请填写驳回理由');
      return;
    }
    try {
      await prizeLogApi.reject(rejectTarget.value.id, rejectReason.value.trim());
      message.success('已驳回');
      rejectVisible.value = false;
      await reload();
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  onMounted(reload);

  // ---------------------------- 人工补发 ----------------------------

  const formRef = ref();

  function showForm() {
    formRef.value.show();
  }

  // ---------------------------- 漏斗展示辅助 ----------------------------

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

  // 金额去掉无意义的尾数：DECIMAL(18,4) 直接显示会变成「5830.0000 积分」
  function money(value) {
    if (value == null) {
      return '0';
    }
    return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
  }

  // 占比再小也给 1% 的宽度，否则条形图上只剩一条看不见的缝，读者会以为渲染坏了
  function barPercent(rate) {
    return Math.max(1, Math.round(Number(rate || 0) * 100));
  }

  const MINUTES_PER_HOUR = 60;
  const MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

  // 「1440 分钟」没人读得出是一天，按量级换算成小时/天
  const waitedText = computed(() => {
    if (!funnel.value.approvePendingCount) {
      return '无积压';
    }
    const minutes = Number(funnel.value.approveOldestMinutes || 0);
    if (minutes < MINUTES_PER_HOUR) {
      return `${minutes} 分钟`;
    }
    if (minutes < MINUTES_PER_DAY) {
      return `${Math.floor(minutes / MINUTES_PER_HOUR)} 小时`;
    }
    return `${Math.floor(minutes / MINUTES_PER_DAY)} 天`;
  });

  // 有积压才提醒，超过一天才标红：审批本就不是分钟级的事，门槛太低会天天报警，报警就没人看了
  const approveBacklogTone = computed(() => {
    if (!funnel.value.approvePendingCount) {
      return 'tone-muted';
    }
    return Number(funnel.value.approveOldestMinutes || 0) >= MINUTES_PER_DAY ? 'tone-critical' : 'tone-warning';
  });

  // ---------------------------- 漏斗折叠 ----------------------------

  /*
   * 默认展开；排查具体某条发奖时可折叠，折叠后把关键结论压成一行摘要留在视线里。
   * 状态记在 localStorage，读写都用 try/catch 包住 ——
   * 隐私模式下 localStorage 会抛异常，不能让折叠状态把页面初始化带崩。
   */
  const FUNNEL_OPEN_KEY = 'prize-log:funnel-open';
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

  .funnel-note {
    margin-bottom: 8px;
    font-size: 11px;
    color: #94a3b8;
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
      color: #b45309;
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

  .overview-card-foot {
    margin-top: 2px;
    font-size: 11px;
    color: #cbd5e1;
  }

  .stat-card {
    margin-bottom: 12px;
  }

  .stat-head {
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
  }

  .stat-head-sub {
    margin-left: 6px;
    font-size: 11px;
    font-weight: 400;
    color: #94a3b8;
  }

  .stat-row {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 3px 0;
  }

  .stat-name {
    flex: 0 0 96px;
    overflow: hidden;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .stat-name-alert {
    font-weight: 600;
    color: #b91c1c;
  }

  .stat-bar-wrap {
    flex: 1;
    height: 14px;
    overflow: hidden;
    background: #f1f5f9;
    border-radius: 3px;
  }

  .stat-bar {
    height: 100%;
    background: linear-gradient(90deg, #10b981, #6ee7b7);
  }

  .stat-bar-alert {
    background: linear-gradient(90deg, #ef4444, #f87171);
  }

  .stat-num {
    flex: 0 0 110px;
    font-size: 12px;
    color: #64748b;
    text-align: right;
  }

  .asset-row {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 4px 0;
  }

  .asset-main {
    flex: 0 0 150px;
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
  }

  .asset-sub {
    font-size: 11px;
    color: #94a3b8;
  }

  .text-alert {
    font-weight: 600;
    color: #b91c1c;
  }
</style>
