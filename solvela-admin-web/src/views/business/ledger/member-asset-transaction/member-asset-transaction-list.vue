<!--
  * 交易明细表
  *
  * 只读流水：由钱包变动链路逐笔落账，管理端不提供增删改。
  *
  * 顶部统计回答财务每天要问的：这段时间净发出去多少（**按资产类型分开算**，
  * 积分和现金不能相加）、钱花在哪个业务上、有没有人在手工调账。
  * ⚠️ 方向只看 transaction_type —— change_amount 存的是绝对值，
  * 收入和支出在数值上都是正数，直接相加出来的数既不是收入也不是支出。
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:49:03
  * @Copyright  weolwo
-->
<template>
  <!---------- 交易统计 begin ----------->
  <StatPanel title="交易统计" storage-key="member-asset-transaction" :issue-list="stat.issueList" @change="loadStat">
    <template #summary>
      共 {{ num(stat.txCount) }} 笔 · {{ num(stat.memberCount) }} 人
      <span v-if="stat.manualAdjustCount > 0" class="solvela-stat-summary-alert"> · 人工调账 {{ num(stat.manualAdjustCount) }} 笔 </span>
    </template>

    <a-row :gutter="12" class="solvela-stat-row">
      <a-col :span="6">
        <div class="solvela-stat-card is-primary">
          <div class="solvela-stat-card-label">流水笔数</div>
          <div class="solvela-stat-card-value">{{ num(stat.txCount) }}</div>
          <div class="solvela-stat-card-foot">涉及 {{ num(stat.memberCount) }} 名会员</div>
        </div>
      </a-col>
      <!-- 收支必须按资产类型分开：积分和现金加在一起的那个数没有任何含义 -->
      <a-col :span="6" v-for="item in stat.assetList || []" :key="item.assetType">
        <div class="solvela-stat-card" :class="Number(item.netAmount) < 0 ? 'is-warning' : 'is-success'">
          <div class="solvela-stat-card-label">
            {{ assetTypeOf(item.assetType).desc }}净额
            <a-tooltip title="净额 = 收入 - 支出。为负说明这段时间用户手上的这种资产是净减少的（消耗大于发放）">
              <QuestionCircleOutlined class="solvela-stat-card-hint" />
            </a-tooltip>
          </div>
          <div class="solvela-stat-card-value">
            {{ money(item.netAmount) }}
            <span class="solvela-stat-card-unit">{{ assetUnitOf(item.assetType) }}</span>
          </div>
          <div class="solvela-stat-card-foot">
            收入 {{ money(item.incomeAmount) }}（{{ num(item.incomeCount) }} 笔） · 支出 {{ money(item.expenseAmount) }}（{{ num(item.expenseCount) }}
            笔）
          </div>
        </div>
      </a-col>
      <a-col :span="6">
        <!-- 系统自己发的奖不需要人插手，这一类是有人手工改了别人的钱 -->
        <div class="solvela-stat-card" :class="stat.manualAdjustCount > 0 ? 'is-critical' : 'is-muted'">
          <div class="solvela-stat-card-label">
            人工调账
            <a-tooltip title="biz_type = MANUAL_ADJUST。系统自己发的奖不需要人插手，这一类是有人手工改了别人的钱，量再小也建议逐笔确认操作人和事由">
              <QuestionCircleOutlined class="solvela-stat-card-hint" />
            </a-tooltip>
          </div>
          <div class="solvela-stat-card-value">{{ num(stat.manualAdjustCount) }}</div>
          <div class="solvela-stat-card-foot">财务对账第一个要查的</div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="12">
      <a-col :span="12">
        <a-card v-if="stat.bizTypeList && stat.bizTypeList.length > 0" size="small" :bordered="false" class="solvela-stat-block">
          <div class="solvela-stat-block-head">
            业务类型分布
            <span class="solvela-stat-block-sub">只给笔数：同一类型下混着积分和现金，给金额就是跨量纲相加</span>
          </div>
          <div v-for="item in stat.bizTypeList" :key="item.bizType || 'none'" class="solvela-stat-line">
            <a-tooltip :title="item.bizType || '（写入侧没写业务类型）'">
              <div class="solvela-stat-name">{{ item.bizType || '（未记录）' }}</div>
            </a-tooltip>
            <div class="solvela-stat-bar-wrap">
              <div class="solvela-stat-bar" :style="{ width: barPercent(item.txShare) + '%' }"></div>
            </div>
            <div class="solvela-stat-num">{{ num(item.txCount) }} 笔 · {{ num(item.memberCount) }} 人</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card v-if="stat.assetList && stat.assetList.length > 0" size="small" :bordered="false" class="solvela-stat-block">
          <div class="solvela-stat-block-head">
            资产收支明细
            <span class="solvela-stat-block-sub">不同资产不合计</span>
          </div>
          <div v-for="item in stat.assetList" :key="item.assetType" class="solvela-stat-asset-line">
            <a-tag :color="assetTypeOf(item.assetType).color">{{ assetTypeOf(item.assetType).desc }}</a-tag>
            <span class="solvela-stat-asset-main">
              净额
              <span :class="Number(item.netAmount) < 0 ? 'solvela-stat-negative' : ''">{{ money(item.netAmount) }}</span>
              {{ assetUnitOf(item.assetType) }}
            </span>
            <span class="solvela-stat-asset-sub">收入 {{ money(item.incomeAmount) }} / {{ num(item.incomeCount) }} 笔</span>
            <span class="solvela-stat-asset-sub">支出 {{ money(item.expenseAmount) }} / {{ num(item.expenseCount) }} 笔</span>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </StatPanel>
  <!---------- 交易统计 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <!-- 查询键是会员号：member_name 只是展示快照，且身上没有索引 -->
      <a-form-item label="会员号" class="solvela-query-form-item">
        <a-input-number style="width: 200px" v-model:value="queryForm.memberId" placeholder="会员号" :controls="false" :precision="0" />
      </a-form-item>
      <a-form-item label="资产类型" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.assetType" :options="ASSET_TYPE_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="资金流向" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.transactionType" :options="TRANSACTION_TYPE_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="关联务外ID" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.bizRefId" placeholder="关联外部业务ID(如 prizeCode)" />
      </a-form-item>
      <a-form-item label="创建时间" class="solvela-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCreateTime" />
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
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.MEMBER_ASSET_TRANSACTION" :refresh="queryData" />
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
      <template #bodyCell="{ text, column }">
        <template v-if="column.dataIndex === 'assetType'">
          <a-tag :color="assetTypeOf(text).color">{{ assetTypeOf(text).desc }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'transactionType'">
          <a-tag :color="transactionTypeOf(text).color">{{ transactionTypeOf(text).desc }}</a-tag>
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
  import { reactive, ref, onMounted } from 'vue';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { memberAssetTransactionApi } from '/src/api/business/ledger/member-asset-transaction-api';
  import StatPanel from '/@/components/business/stat-panel/index.vue';
  import { barPercent, money, num } from '/@/lib/stat-format';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    ASSET_TYPE_OPTIONS,
    TRANSACTION_TYPE_OPTIONS,
    assetTypeOf,
    assetUnitOf,
    transactionTypeOf,
  } from '/src/constants/business/ledger/member-asset-transaction-const';

  // ---------------------------- 统计面板 ----------------------------

  /*
   * 面板的时间范围由 StatPanel 自己管（默认当天），通过 @change 抛下来，
   * 与下面列表的筛选条件<b>刻意不联动</b>：面板回答的是「这段时间整体发生了什么」，
   * 跟着某个会员名筛之后它就不再是概览了。
   */
  const stat = ref({});

  async function loadStat(form) {
    try {
      const res = await memberAssetTransactionApi.stat(form);
      stat.value = res.data || {};
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: 'id',
      dataIndex: 'id',
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
      title: '资金流向',
      dataIndex: 'transactionType',
      ellipsis: true,
    },
    {
      title: '变动绝对值',
      dataIndex: 'changeAmount',
      ellipsis: true,
    },
    {
      title: '变动后最新余额',
      dataIndex: 'balanceAfter',
      ellipsis: true,
    },
    {
      title: '业务类型',
      dataIndex: 'bizType',
      ellipsis: true,
    },
    {
      title: '关联业务ID',
      dataIndex: 'bizRefId',
      ellipsis: true,
    },
    {
      title: '备注',
      dataIndex: 'remark',
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
    assetType: undefined, //资产类型：SCORE, BALANCE
    transactionType: undefined, //资金流向：1-收入, 2-支出
    bizRefId: undefined, //关联外部业务ID(如 prizeCode)
    createTime: [], //创建时间
    createTimeBegin: undefined, //创建时间 开始
    createTimeEnd: undefined, //创建时间 结束
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

  // 重置查询条件
  function resetQuery() {
    let pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    queryData();
  }

  // 搜索
  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  // 查询数据
  async function queryData() {
    tableLoading.value = true;
    try {
      let queryResult = await memberAssetTransactionApi.queryPage(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0];
    queryForm.createTimeEnd = dateStrings[1];
  }

  onMounted(queryData);
</script>
