<!--
  * 商城-兑换订单
  *
  * 【只读】订单由 C 端下单链路创建、由履约链路推进状态。后台凭空插一条会绕过
  * 扣积分、锁库存、限兑计数三件事，造出来的是一个账对不上的孤单；手工改状态更危险，
  * 状态机是履约链路的，拨一下不会真的去发货。生成器留的新建/编辑按钮已移除。
  *
  * 【统计可折叠，与列表共用同一套筛选】顶部筛选改了统计跟着变 ——
  * 两套条件的话运营会看到「统计说 100 单，下面列表只有 3 条」，然后不知道该信哪个。
  * 默认展开：打开这个页面第一件想知道的事就是「今天兑了多少、哪个商品最火」。
  *
  * 【按账号查会先换算成会员号】member_name 那一列 DDL 注释写着「不要用于查询」——
  * 没有索引，直接查是全表扫。
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:35:46
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单 ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="订单号" class="solvela-query-form-item">
        <a-input style="width: 170px" v-model:value="queryForm.orderNo" placeholder="精确匹配" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="账号" class="solvela-query-form-item">
        <a-input style="width: 140px" v-model:value="queryForm.memberName" placeholder="会员账号" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="商品" class="solvela-query-form-item">
        <a-input style="width: 150px" v-model:value="queryForm.commodityName" placeholder="商品名，模糊" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="状态" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="queryForm.status" :options="ORDER_STATUS_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="类型" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="queryForm.commodityType" :options="COMMODITY_TYPE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="订单来源" class="solvela-query-form-item">
        <a-select style="width: 130px" v-model:value="queryForm.sourceType" :options="ORDER_SOURCE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="创建时间" class="solvela-query-form-item">
        <a-range-picker v-model:value="createTime" :presets="defaultTimeRanges" style="width: 230px" @change="onChangeCreateTime" />
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

  <!---------- 可折叠统计 ----------->
  <a-card size="small" :bordered="false" class="stat-card" :loading="statLoading">
    <div class="stat-head" @click="statOpen = !statOpen">
      <span class="stat-toggle">
        <DownOutlined v-if="statOpen" />
        <RightOutlined v-else />
      </span>
      <span class="stat-title">兑换统计</span>
      <span class="stat-tip">统计口径与下方列表的筛选条件一致</span>
      <!-- 折叠时也要看得到最关键的两个数，否则收起来就等于没有 -->
      <span v-if="!statOpen" class="stat-collapsed"> {{ stat.orderCount }} 单 · {{ stat.quantitySum }} 件 · 消耗 {{ stat.payPointsSum }} 积分 </span>
    </div>

    <div v-show="statOpen" class="stat-body">
      <a-row :gutter="16">
        <a-col v-for="item in statItems" :key="item.key" :span="3">
          <div class="stat-item">
            <div class="stat-value" :class="item.cls">{{ item.value }}</div>
            <div class="stat-label">
              {{ item.label }}
              <a-tooltip v-if="item.tip" :title="item.tip">
                <QuestionCircleOutlined class="stat-help" />
              </a-tooltip>
            </div>
          </div>
        </a-col>
      </a-row>

      <a-divider style="margin: 14px 0" />

      <div class="rank-head">
        <span class="rank-title">兑换商品排行</span>
        <span class="stat-tip">按兑换件数排；不含待支付与已取消</span>
      </div>
      <a-table size="small" row-key="commodityId" :data-source="stat.commodityRank" :columns="rankColumns" :pagination="false" bordered>
        <template #bodyCell="{ index, column, record }">
          <template v-if="column.dataIndex === 'no'">
            <span class="rank-no" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'commodityName'">
            <div>{{ record.commodityName }}</div>
            <div class="cell-sub">{{ record.commodityCode }}</div>
          </template>
        </template>
        <template #emptyText>
          <span class="cell-sub">当前筛选条件下没有兑换记录</span>
        </template>
      </a-table>
    </div>
  </a-card>

  <!---------- 列表 ----------->
  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block"></div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      :scroll="{ x: 1500 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'commodityName'">
          <div class="commodity-cell">
            <FileThumb v-if="record.coverFileId" :file-id="record.coverFileId" :height="36" />
            <div>
              <div>{{ record.commodityName }}</div>
              <div class="cell-sub">{{ skuAttrsText(record.skuAttrs) }}</div>
            </div>
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'memberName'">
          <div>{{ record.memberName || '—' }}</div>
          <div class="cell-sub">{{ record.memberId }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'commodityType'">
          {{ typeDesc(text) }}
        </template>

        <template v-else-if="column.dataIndex === 'pay'">
          <div>{{ record.payPoints }} 积分</div>
          <div v-if="record.payCash > 0" class="cell-sub">+ ￥{{ record.payCash }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusMeta(text).color">{{ statusMeta(text).desc }}</a-tag>
          <div v-if="record.failReason" class="fail-reason">{{ record.failReason }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'sourceType'">
          {{ sourceDesc(text) }}
        </template>
      </template>
    </a-table>

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
  import { computed, onMounted, reactive, ref } from 'vue';
  import { DownOutlined, QuestionCircleOutlined, RightOutlined } from '@ant-design/icons-vue';
  import { mallOrderApi } from '/@/api/business/mall/mall-order-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { ORDER_SOURCE_ENUM, ORDER_SOURCE_OPTIONS, ORDER_STATUS_ENUM, ORDER_STATUS_OPTIONS } from '/@/constants/business/mall/mall-order-const';
  import { COMMODITY_TYPE_ENUM, COMMODITY_TYPE_OPTIONS } from '/@/constants/business/mall/mall-commodity-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import FileThumb from '/@/components/support/file-thumb/index.vue';

  const columns = ref([
    { title: '订单号', dataIndex: 'orderNo', width: 190, ellipsis: true },
    { title: '会员', dataIndex: 'memberName', width: 150 },
    { title: '商品 / 规格', dataIndex: 'commodityName', width: 240 },
    { title: '类型', dataIndex: 'commodityType', width: 90 },
    { title: '件数', dataIndex: 'quantity', width: 70 },
    { title: '实付', dataIndex: 'pay', width: 120 },
    { title: '状态', dataIndex: 'status', width: 150 },
    { title: '来源', dataIndex: 'sourceType', width: 100 },
    { title: '创建时间', dataIndex: 'createTime', width: 170, ellipsis: true },
    { title: '完成时间', dataIndex: 'finishTime', width: 170, ellipsis: true },
  ]);

  const rankColumns = ref([
    { title: '#', dataIndex: 'no', width: 50, align: 'center' },
    { title: '商品', dataIndex: 'commodityName' },
    { title: '兑换件数', dataIndex: 'quantitySum', width: 100 },
    { title: '订单数', dataIndex: 'orderCount', width: 90 },
    { title: '兑换人数', dataIndex: 'memberCount', width: 100 },
    { title: '消耗积分', dataIndex: 'payPointsSum', width: 110 },
  ]);

  function statusMeta(value) {
    return Object.values(ORDER_STATUS_ENUM).find((item) => item.value === value) || { desc: value, color: 'default' };
  }

  function typeDesc(value) {
    const meta = Object.values(COMMODITY_TYPE_ENUM).find((item) => item.value === value);
    return meta ? meta.desc : value;
  }

  function sourceDesc(value) {
    const meta = Object.values(ORDER_SOURCE_ENUM).find((item) => item.value === value);
    return meta ? meta.desc : value;
  }

  /** skuAttrs 是 JSON 串，展示成「颜色: 星空灰 / 尺码: XL」；无规格时留空 */
  function skuAttrsText(raw) {
    if (!raw) {
      return '';
    }
    try {
      const attrs = typeof raw === 'string' ? JSON.parse(raw) : raw;
      const parts = Object.entries(attrs).map(([k, v]) => `${k}: ${v}`);
      return parts.join(' / ');
    } catch (e) {
      // 脏数据不该让整行渲染不出来
      return '';
    }
  }

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    orderNo: undefined,
    memberName: undefined,
    commodityName: undefined,
    commodityCode: undefined,
    commodityType: undefined,
    status: undefined,
    sourceType: undefined,
    createTimeBegin: undefined,
    createTimeEnd: undefined,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const createTime = ref([]);
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0] || undefined;
    queryForm.createTimeEnd = dateStrings[1] || undefined;
  }

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    createTime.value = [];
    queryData();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    statLoading.value = true;
    try {
      // 统计和列表同时发，用的是同一份 queryForm —— 它们看的必须是同一批数据
      const [pageRes, statRes] = await Promise.all([mallOrderApi.queryPage(queryForm), mallOrderApi.queryStat(queryForm)]);
      tableData.value = pageRes.data.list;
      total.value = pageRes.data.total;
      Object.assign(stat, statRes.data || {});
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
      statLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 统计 ----------------------------

  const statOpen = ref(true);
  const statLoading = ref(false);
  const stat = reactive({
    orderCount: 0,
    memberCount: 0,
    quantitySum: 0,
    finishedCount: 0,
    processingCount: 0,
    cancelledCount: 0,
    failedCount: 0,
    payPointsSum: 0,
    payCashSum: 0,
    commodityRank: [],
  });

  const statItems = computed(() => [
    { key: 'order', label: '订单数', value: stat.orderCount, cls: '' },
    {
      key: 'member',
      label: '兑换人数',
      value: stat.memberCount,
      cls: '',
      tip: '按会员号去重。一个人兑 20 单，订单数是 20、人数是 1',
    },
    { key: 'quantity', label: '兑换件数', value: stat.quantitySum, cls: '' },
    { key: 'finished', label: '已完成', value: stat.finishedCount, cls: 'is-success' },
    { key: 'processing', label: '履约中', value: stat.processingCount, cls: '' },
    { key: 'cancelled', label: '取消/退款', value: stat.cancelledCount, cls: '' },
    {
      key: 'failed',
      label: '履约失败',
      value: stat.failedCount,
      cls: stat.failedCount > 0 ? 'is-fail' : '',
      tip: '不为 0 就得有人去看：积分已经扣了，东西没发出去',
    },
    {
      key: 'points',
      label: '消耗积分',
      value: stat.payPointsSum,
      cls: '',
      tip: '不含待支付与已取消 —— 那些还没真的扣款',
    },
  ]);
</script>

<style scoped lang="less">
  .stat-card {
    margin-bottom: 12px;
  }

  .stat-head {
    display: flex;
    gap: 10px;
    align-items: baseline;
    cursor: pointer;
    user-select: none;
  }

  .stat-toggle {
    color: #94a3b8;
  }

  .stat-title {
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
  }

  .stat-tip,
  .cell-sub {
    font-size: 12px;
    color: #bfbfbf;
  }

  .stat-collapsed {
    margin-left: auto;
    font-size: 13px;
    color: #64748b;
  }

  .stat-body {
    margin-top: 14px;
  }

  .stat-item {
    padding: 4px 0;
  }

  .stat-value {
    font-size: 20px;
    font-weight: 600;
    line-height: 1.3;
    color: #1e293b;
  }

  .stat-value.is-success {
    color: #059669;
  }

  .stat-value.is-fail {
    color: #ef4444;
  }

  .stat-label {
    font-size: 12px;
    color: #64748b;
  }

  .stat-help {
    color: #cbd5e1;
  }

  .rank-head {
    display: flex;
    gap: 10px;
    align-items: baseline;
    margin-bottom: 8px;
  }

  .rank-title {
    font-size: 13px;
    font-weight: 600;
    color: #334155;
  }

  .rank-no {
    display: inline-block;
    width: 20px;
    height: 20px;
    font-size: 12px;
    line-height: 20px;
    color: #64748b;
    text-align: center;
    background: #f1f5f9;
    border-radius: 4px;
  }

  .rank-no.is-top {
    font-weight: 600;
    color: #fff;
    background: #f97316;
  }

  .commodity-cell {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .fail-reason {
    font-size: 12px;
    color: #d97706;
  }
</style>
