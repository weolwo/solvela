<!--
  * 提案记录与提案漏斗
  *
  * 提案表是「钱出去的必经之路」：提案 → 风控 → 审批 → 执行 → 到账。
  * 顶部漏斗回答运营每天最该问的三件事：今天发出去多少（按资产类型分开算，
  * 积分和现金不能加在一起）、有没有单子压在审批池里没人管、有没有钱卡在半路。
  *
  * 「卡在下发」是本页独有的：下发在提案事务提交后同步调起，进程中途退出就没有第二次机会，
  * 而工程里没有任何重试/补偿任务 —— 卡住的提案会一直停在待执行/执行中，没人查就发现不了。
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:13:50
  * @Copyright  weolwo
-->
<template>
  <!---------- 提案漏斗 begin ----------->
  <div class="funnel-toggle">
    <a-button type="link" size="small" class="funnel-toggle-btn" @click="funnelOpen = !funnelOpen">
      <template #icon>
        <DownOutlined v-if="funnelOpen" />
        <RightOutlined v-else />
      </template>
      提案漏斗
    </a-button>
    <span v-if="!funnelOpen" class="funnel-summary">
      共 {{ num(funnel.totalCount) }} 条 · 到账 {{ percent(funnel.successRate) }} ·
      <span :class="funnel.pendingReviewCount > 0 ? 'summary-alert' : ''"> 待审 {{ num(funnel.pendingReviewCount) }} </span>
      ·
      <span :class="funnel.stuckDispatchCount > 0 ? 'summary-alert' : ''"> 卡单 {{ num(funnel.stuckDispatchCount) }} </span>
    </span>
  </div>

  <div v-show="funnelOpen">
    <a-row :gutter="12" class="overview-row">
      <a-col :span="5">
        <div class="overview-card tone-primary">
          <div class="overview-card-label">提案总数</div>
          <div class="overview-card-value">{{ num(funnel.totalCount) }}</div>
          <div class="overview-card-foot">涉及 {{ num(funnel.memberCount) }} 名会员</div>
        </div>
      </a-col>
      <a-col :span="5">
        <div class="overview-card tone-success">
          <div class="overview-card-label">
            到账率
            <a-tooltip
              title="分母是提案总数。提案链路上每一步都可能把钱拦下来，剔掉任何一段都会让这个比率虚高——运营要的恰恰是「一百个提案里最后几个到账」"
            >
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">{{ percent(funnel.successRate) }}</div>
          <div class="overview-card-foot">成功 {{ num(funnel.successCount) }} · 驳回 {{ num(funnel.rejectedCount) }}</div>
        </div>
      </a-col>
      <a-col :span="5">
        <!-- 提案压在审批池里，对用户就是「奖一直没发」-->
        <div class="overview-card" :class="reviewBacklogTone">
          <div class="overview-card-label">
            待审积压
            <a-tooltip title="待一审 + 待二审。只看条数看不出「压了三天」，所以同时给出最久的一条已经等了多久">
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">{{ num(funnel.pendingReviewCount) }}</div>
          <div class="overview-card-foot">最久等待 {{ waitedText }}</div>
        </div>
      </a-col>
      <a-col :span="5">
        <!-- 没有任何重试/补偿任务，卡住的提案不会自己往下走 -->
        <div class="overview-card" :class="funnel.stuckDispatchCount > 0 ? 'tone-critical' : 'tone-muted'">
          <div class="overview-card-label">
            卡在下发
            <a-tooltip
              title="待执行/执行中且 30 分钟没动过。下发在提案事务提交后同步调起，进程中途退出就没有第二次机会，而工程里没有任何重试任务——钱既没发出去也没标成失败"
            >
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">{{ num(funnel.stuckDispatchCount) }}</div>
          <div class="overview-card-foot">待执行 {{ num(funnel.pendingExecuteCount) }} · 执行中 {{ num(funnel.executingCount) }}</div>
        </div>
      </a-col>
      <a-col :span="4">
        <!-- 失败/部分成功 = 钱没到用户手上；风控拦截是防线生效，两者性质不同，不要混在一起看 -->
        <div class="overview-card" :class="funnel.failedCount > 0 || funnel.partialCount > 0 ? 'tone-critical' : 'tone-muted'">
          <div class="overview-card-label">发放异常</div>
          <div class="overview-card-value">
            {{ num(funnel.failedCount) }}
            <span class="overview-card-slash">失败</span>
          </div>
          <div class="overview-card-foot">部分成功 {{ num(funnel.partialCount) }} · 拦截 {{ num(funnel.blockedCount) }}</div>
        </div>
      </a-col>
    </a-row>

    <!-- 漏斗刻意不吃状态筛选，筛了却不生效比不筛更让人困惑，故明说 -->
    <div v-if="queryForm.status !== undefined" class="funnel-note">漏斗不受「状态」筛选影响：它正是漏斗要拆解的维度，跟着筛会让到账率恒为 100%</div>

    <a-alert v-for="(issue, idx) in funnel.issueList || []" :key="idx" type="error" show-icon class="mb-2">
      <template #message>
        <span class="text-xs">{{ issue }}</span>
      </template>
    </a-alert>

    <a-row :gutter="12">
      <a-col :span="10">
        <!-- 金额按资产类型分开算：积分/现金/券张数/实物件数不是同一个量纲 -->
        <a-card v-if="funnel.assetList && funnel.assetList.length > 0" size="small" :bordered="false" class="stat-card">
          <div class="stat-head">
            资产发放
            <span class="stat-head-sub">积分与现金量纲不同，不合计</span>
          </div>
          <div v-for="item in funnel.assetList" :key="item.assetType" class="asset-row">
            <a-tag :color="assetTypeOf(item.assetType).color">{{ assetTypeOf(item.assetType).desc }}</a-tag>
            <span class="asset-main">已发出 {{ money(item.successAmount) }} {{ assetUnitOf(item.assetType) }}</span>
            <span class="asset-sub">{{ num(item.successCount) }}/{{ num(item.proposalCount) }} 条</span>
            <span class="asset-sub">
              在途 {{ money(item.pendingAmount) }} · 拦截
              <span :class="Number(item.blockedAmount) > 0 ? 'text-alert' : ''">{{ money(item.blockedAmount) }}</span>
            </span>
          </div>
        </a-card>
      </a-col>
      <a-col :span="7">
        <a-card v-if="funnel.sourceList && funnel.sourceList.length > 0" size="small" :bordered="false" class="stat-card">
          <div class="stat-head">来源分布（条形为到账率）</div>
          <div v-for="item in funnel.sourceList" :key="item.sourceType" class="stat-row">
            <div class="stat-name" :class="item.unknownSource ? 'stat-name-alert' : ''">
              {{ item.unknownSource ? '🔴' : '' }} {{ item.sourceDesc }}
            </div>
            <div class="stat-bar-wrap">
              <div class="stat-bar" :style="{ width: barPercent(item.successRate) + '%' }"></div>
            </div>
            <div class="stat-num">{{ num(item.proposalCount) }} 条 · {{ percent(item.successRate) }}</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="7">
        <a-card v-if="funnel.blockReasonList && funnel.blockReasonList.length > 0" size="small" :bordered="false" class="stat-card">
          <div class="stat-head">
            风控拦截原因
            <span class="stat-head-sub">拦截率 {{ percent(funnel.blockRate) }}，红色的不是防刷</span>
          </div>
          <div v-for="item in funnel.blockReasonList" :key="item.riskCode || item.reason" class="stat-row">
            <a-tooltip :title="item.riskCode ? `${item.reason}（${item.riskCode}）` : item.reason">
              <div class="stat-name" :class="item.needsAttention ? 'stat-name-alert' : ''">
                {{ item.needsAttention ? '🔴' : '' }} {{ item.reason }}
              </div>
            </a-tooltip>
            <div class="stat-bar-wrap">
              <div class="stat-bar" :class="item.needsAttention ? 'stat-bar-alert' : ''" :style="{ width: barPercent(item.blockShare) + '%' }"></div>
            </div>
            <div class="stat-num">{{ num(item.blockCount) }} · {{ percent(item.blockShare) }}</div>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
  <!---------- 提案漏斗 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <!-- 查询键是会员号：member_name 只是展示快照，且身上没有索引 -->
      <a-form-item label="会员号" class="solvela-query-form-item">
        <a-input-number style="width: 200px" v-model:value="queryForm.memberId" placeholder="会员号" :controls="false" :precision="0" />
      </a-form-item>
      <a-form-item label="更新时间" class="solvela-query-form-item">
        <a-range-picker v-model:value="queryForm.updateTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeUpdateTime" />
      </a-form-item>
      <a-form-item label="创建时间" class="solvela-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCreateTime" />
      </a-form-item>
      <a-form-item label="优惠配置ID" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.promotionConfigId" placeholder="优惠配置ID" />
      </a-form-item>
      <a-form-item label="状态" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="PROPOSAL_STATUS_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="来源" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.sourceType" :options="PROPOSAL_SOURCE_TYPE_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="来源单号" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.sourceBizId" placeholder="来源单号" />
      </a-form-item>
      <a-form-item label="一审人" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.firstReviewer" placeholder="一审人" />
      </a-form-item>
      <a-form-item label="提案单号" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.tradeNo" placeholder="提案单号" />
      </a-form-item>
      <a-form-item label="资产类型" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.assetType" :options="ASSET_TYPE_OPTIONS" placeholder="全部" allowClear />
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

  <a-card size="small" :bordered="false" :hoverable="true">
    <!---------- 表格操作行 begin ----------->
    <a-row class="solvela-table-btn-block">
      <!--
        新建 / 批量删除已移除（v3.69.0）：提案只能由发奖链路创建，由审批与下发推进状态。
        「新建」造出来的是 status=0 的提案 —— 正常链路只落 10/30/80，
        0 是绕过风控与预算直接插库的产物，不会被任何流程推进；
        上面漏斗里那条「有 N 条提案停在等待中」的告警，来源就是这个按钮。
      -->
      <div class="solvela-table-operate-block"></div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.PROPOSAL_RECORD" :refresh="queryData" />
      </div>
    </a-row>
    <!---------- 表格操作行 end ----------->

    <!---------- 表格 begin ----------->
    <a-table
      size="small"
      :scroll="{ y: 800 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="proposalStatusOf(text).color">{{ proposalStatusOf(text).desc }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'sourceType'">
          <a-tag :color="proposalSourceTypeOf(text).color">{{ proposalSourceTypeOf(text).desc }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'assetType'">
          <a-tag :color="assetTypeOf(text).color">{{ assetTypeOf(text).desc }}</a-tag>
        </template>
      </template>
    </a-table>
    <!---------- 表格 end ----------->

    <div class="solvela-query-table-page">
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
  import { proposalRecordApi } from '/src/api/business/risk/proposal-record-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    ASSET_TYPE_OPTIONS,
    PROPOSAL_SOURCE_TYPE_OPTIONS,
    PROPOSAL_STATUS_OPTIONS,
    assetTypeOf,
    assetUnitOf,
    proposalSourceTypeOf,
    proposalStatusOf,
  } from '/src/constants/business/risk/proposal-record-const';

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: 'id',
      dataIndex: 'id',
      ellipsis: true,
    },
    {
      title: '提案单号',
      dataIndex: 'tradeNo',
      ellipsis: true,
    },
    {
      title: '会员号',
      dataIndex: 'memberId',
      width: 130,
      ellipsis: true,
    },
    {
      title: '会员账号（快照）',
      dataIndex: 'memberName',
      ellipsis: true,
    },
    {
      title: '资产类型',
      dataIndex: 'assetType',
      ellipsis: true,
    },
    {
      title: '资产引用',
      dataIndex: 'assetRef',
      ellipsis: true,
    },
    {
      title: '发放金额/积分数',
      dataIndex: 'amount',
      ellipsis: true,
    },
    {
      title: '发放数量',
      dataIndex: 'quantity',
      ellipsis: true,
    },
    {
      title: '来源',
      dataIndex: 'sourceType',
      ellipsis: true,
    },
    {
      title: '来源单号',
      dataIndex: 'sourceBizId',
      ellipsis: true,
    },
    {
      title: '优惠配置ID',
      dataIndex: 'promotionConfigId',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      ellipsis: true,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      ellipsis: true,
    },
    {
      title: '一审人',
      dataIndex: 'firstReviewer',
      ellipsis: true,
    },
    {
      title: '一审时间',
      dataIndex: 'firstReviewTime',
      ellipsis: true,
    },
    {
      title: '二审人',
      dataIndex: 'secondReviewer',
      ellipsis: true,
    },
    {
      title: '二审时间',
      dataIndex: 'secondReviewTime',
      ellipsis: true,
    },
    {
      title: '审核意见/驳回理由',
      dataIndex: 'reviewComment',
      ellipsis: true,
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      ellipsis: true,
    },
    {
      title: '更新人',
      dataIndex: 'updateBy',
      ellipsis: true,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      ellipsis: true,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    memberId: undefined, //会员号（关联键）
    updateTime: [], //更新时间
    updateTimeBegin: undefined, //更新时间 开始
    updateTimeEnd: undefined, //更新时间 结束
    createTime: [], //创建时间
    createTimeBegin: undefined, //创建时间 开始
    createTimeEnd: undefined, //创建时间 结束
    promotionConfigId: undefined, //优惠配置ID
    status: undefined, //状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截
    sourceType: undefined, //来源：TASK(任务), DRAW(抽奖), MANUAL(人工)
    sourceBizId: undefined, //来源单号(taskRecordId 或 drawLogTraceId)
    firstReviewer: undefined, //一审人
    tradeNo: undefined, //提案单号，服务端生成，对外唯一标识
    assetType: undefined, //SCORE/BALANCE/COUPON/PHYSICAL
    pageNum: 1,
    pageSize: 10,
  };
  // 查询表单form
  const queryForm = reactive({ ...queryFormState });
  // 表格加载loading
  const tableLoading = ref(false);
  // 表格数据
  const tableData = ref([]);
  // 总数
  const total = ref(0);

  // 漏斗数据
  const funnel = ref({});

  // 重置查询条件
  function resetQuery() {
    let pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    reload();
  }

  // 搜索
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
      // status 服务端刻意忽略：它是漏斗要拆解的维度本身
      const res = await proposalRecordApi.funnel(queryForm);
      funnel.value = res || {};
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  // 查询数据
  async function queryData() {
    tableLoading.value = true;
    try {
      let queryResult = await proposalRecordApi.queryPage(queryForm);
      tableData.value = queryResult.list;
      total.value = queryResult.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function onChangeUpdateTime(dates, dateStrings) {
    queryForm.updateTimeBegin = dateStrings[0];
    queryForm.updateTimeEnd = dateStrings[1];
  }

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0];
    queryForm.createTimeEnd = dateStrings[1];
  }

  onMounted(reload);

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

  // 金额去掉无意义的尾数：DECIMAL(13,4) 直接显示会变成「5830.0000 积分」
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
    const minutes = Number(funnel.value.pendingReviewOldestMinutes || 0);
    if (!funnel.value.pendingReviewCount) {
      return '无积压';
    }
    if (minutes < MINUTES_PER_HOUR) {
      return `${minutes} 分钟`;
    }
    if (minutes < MINUTES_PER_DAY) {
      return `${Math.floor(minutes / MINUTES_PER_HOUR)} 小时`;
    }
    return `${Math.floor(minutes / MINUTES_PER_DAY)} 天`;
  });

  // 有积压才提醒，超过一天才标红：审批本就不是分钟级的事，门槛太低会天天报警，报警就没人看了
  const reviewBacklogTone = computed(() => {
    if (!funnel.value.pendingReviewCount) {
      return 'tone-muted';
    }
    return Number(funnel.value.pendingReviewOldestMinutes || 0) >= MINUTES_PER_DAY ? 'tone-critical' : 'tone-warning';
  });

  // ---------------------------- 漏斗折叠 ----------------------------

  /*
   * 默认展开；排查具体某条提案时可折叠，折叠后把关键结论压成一行摘要留在视线里。
   * 状态记在 localStorage，读写都用 try/catch 包住 ——
   * 隐私模式下 localStorage 会抛异常，不能让折叠状态把页面初始化带崩。
   */
  const FUNNEL_OPEN_KEY = 'proposal-record:funnel-open';
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

  /*
   * 增删改整组移除（v3.69.0）：提案只能由发奖链路创建，由审批与下发推进状态。
   * 后端 /proposalRecord 的 add / update / delete / batchDelete 四个接口也已经删掉，
   * 留着按钮只会点出 404。审批相关的操作在上面的 approve / reject 里。
   */
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
