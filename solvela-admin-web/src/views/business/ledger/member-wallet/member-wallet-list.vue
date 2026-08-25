<!--
  * 会员钱包表
  *
  * 只读台账：钱包余额由发奖/消费链路写入，管理端不提供增删改。
  *
  * ⚠️ 顶部统计里，**余额是存量、是全量，不随时间范围变化**：
  * 钱包表一个会员一种资产一行、只有当前余额，没有历史切片 ——
  * 按创建时间筛出来的是「今天新开的钱包」，那几个账户的余额加起来
  * 既不是今天发出去的钱、也不是用户现在手上的钱，是一个什么都不回答的数字。
  * 真正跟时间范围走的只有「本期变动」，它取自交易明细表（与交易明细页同一条 SQL）。
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:56:48
  * @Copyright  weolwo
-->
<template>
  <!---------- 钱包统计 begin ----------->
  <StatPanel title="钱包统计" storage-key="member-wallet" :issue-list="stat.issueList" @change="loadStat">
    <template #summary>
      {{ num(stat.walletCount) }} 个账户 · {{ num(stat.memberCount) }} 名会员
      <span v-if="stat.frozenCount > 0" class="solvela-stat-summary-alert"> · 冻结 {{ num(stat.frozenCount) }} 个 </span>
    </template>

    <a-row :gutter="12" class="solvela-stat-row">
      <a-col :span="6">
        <div class="solvela-stat-card is-primary">
          <div class="solvela-stat-card-label">
            钱包账户
            <span class="solvela-stat-card-tag">全量</span>
            <a-tooltip title="一个会员一种资产一行，所以账户数不等于会员数。钱包表是存量表，这些数字不随时间范围变化">
              <QuestionCircleOutlined class="solvela-stat-card-hint" />
            </a-tooltip>
          </div>
          <div class="solvela-stat-card-value">{{ num(stat.walletCount) }}</div>
          <div class="solvela-stat-card-foot">涉及 {{ num(stat.memberCount) }} 名会员 · 冻结 {{ num(stat.frozenCount) }} 个</div>
        </div>
      </a-col>
      <!-- 余额是存量：按创建时间筛出来的是「今天新开的钱包」，那个数什么也回答不了 -->
      <a-col :span="6" v-for="item in stat.assetList || []" :key="item.assetType">
        <div class="solvela-stat-card is-success">
          <div class="solvela-stat-card-label">
            {{ assetTypeOf(item.assetType).desc }}余额
            <span class="solvela-stat-card-tag">全量</span>
          </div>
          <div class="solvela-stat-card-value">
            {{ money(item.totalBalance) }}
            <span class="solvela-stat-card-unit">{{ assetUnitOf(item.assetType) }}</span>
          </div>
          <div class="solvela-stat-card-foot">
            {{ num(item.walletCount) }} 个账户 · 人均 {{ money(item.avgBalance) }}
            <span v-if="Number(item.frozenBalance) > 0" class="solvela-stat-alert-text"> · 冻结中 {{ money(item.frozenBalance) }} </span>
          </div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="12">
      <a-col :span="12">
        <!-- 这一块才是跟着时间范围走的：数据来自交易明细表 -->
        <a-card size="small" :bordered="false" class="solvela-stat-block">
          <div class="solvela-stat-block-head">
            本期变动
            <span class="solvela-stat-block-sub">跟随上方时间范围，数据取自交易明细表</span>
          </div>
          <div v-if="!stat.flowList || stat.flowList.length === 0" class="solvela-stat-asset-sub">这段时间没有任何资产变动</div>
          <div v-for="item in stat.flowList || []" :key="item.assetType" class="solvela-stat-asset-line">
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
      <a-col :span="12">
        <a-card v-if="stat.assetList && stat.assetList.length > 0" size="small" :bordered="false" class="solvela-stat-block">
          <div class="solvela-stat-block-head">
            资产存量明细
            <span class="solvela-stat-block-sub">全量；不同资产不合计</span>
          </div>
          <div v-for="item in stat.assetList" :key="item.assetType" class="solvela-stat-asset-line">
            <a-tag :color="assetTypeOf(item.assetType).color">{{ assetTypeOf(item.assetType).desc }}</a-tag>
            <span class="solvela-stat-asset-main">{{ money(item.totalBalance) }} {{ assetUnitOf(item.assetType) }}</span>
            <span class="solvela-stat-asset-sub">{{ num(item.walletCount) }} 个账户</span>
            <span class="solvela-stat-asset-sub">人均 {{ money(item.avgBalance) }}</span>
            <span class="solvela-stat-asset-sub" :class="Number(item.frozenBalance) > 0 ? 'solvela-stat-alert-text' : ''">
              冻结中 {{ money(item.frozenBalance) }}
            </span>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </StatPanel>
  <!---------- 钱包统计 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <!-- 查询键是会员号：member_name 已不是关联键（钱包表连这一列都没有了） -->
      <a-form-item label="会员号" class="solvela-query-form-item">
        <a-input-number style="width: 200px" v-model:value="queryForm.memberId" placeholder="会员号" :controls="false" :precision="0" />
      </a-form-item>
      <a-form-item label="资产类型" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.assetType" :options="ASSET_TYPE_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="状态" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="WALLET_STATUS_OPTIONS" placeholder="全部" allowClear />
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
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.MEMBER_WALLET" :refresh="queryData" />
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
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="walletStatusOf(text).color">{{ walletStatusOf(text).desc }}</a-tag>
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
  import { memberWalletApi } from '/src/api/business/ledger/member-wallet-api';
  import StatPanel from '/@/components/business/stat-panel/index.vue';
  import { money, num } from '/@/lib/stat-format';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    ASSET_TYPE_OPTIONS,
    WALLET_STATUS_OPTIONS,
    assetTypeOf,
    assetUnitOf,
    walletStatusOf,
  } from '/src/constants/business/ledger/member-wallet-const';

  // ---------------------------- 统计面板 ----------------------------

  /*
   * 时间范围只作用于「本期变动」（取自交易明细表）；
   * 余额与账户数是存量，服务端按全量算，卡片上也标了「全量」。
   */
  const stat = ref({});

  async function loadStat(form) {
    try {
      const res = await memberWalletApi.stat(form);
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
      // 账号取自会员主表的当前值（钱包是状态表，不留快照）
      title: '会员账号',
      dataIndex: 'memberName',
      ellipsis: true,
    },
    {
      title: '资产类型',
      dataIndex: 'assetType',
      ellipsis: true,
    },
    {
      title: '余额',
      dataIndex: 'balance',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
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
    assetType: undefined, //资产类型：SCORE-积分, BALANCE-现金
    status: undefined, //状态：0-冻结, 1-正常
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
      let queryResult = await memberWalletApi.queryPage(queryForm);
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
