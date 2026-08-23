<!--
  * 会员登录日志
  *
  * 【顶部统计与列表共用同一套筛选条件】改了顶部的筛选，统计跟着变。
  * 两套条件的话运营会看到「统计说今天 100 次登录，下面列表只有 3 条」，然后不知道该信哪个。
  *
  * 【默认统计当天】进页面就把注册时间筛成今天 —— 这张表是 append-only 且按月分区的，
  * 不给默认时间范围的话首屏是全表扫描，而运营真正想知道的是「今天有多少人登录」。
  *
  * 【没有增删改】登录日志是 append-only 的（DDL 注释写明），生成器留的 add / update
  * 对着一张只进不出的表，调了只会写脏数据，已移除。
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 20:58:39
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="账号" class="smart-query-form-item">
        <a-input style="width: 150px" v-model:value="queryForm.memberName" placeholder="支持模糊搜索" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="会员号" class="smart-query-form-item">
        <a-input style="width: 140px" v-model:value="queryForm.memberId" placeholder="10 位数字" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 110px" v-model:value="queryForm.status" :options="LOGIN_STATUS_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="设备端" class="smart-query-form-item">
        <a-select style="width: 110px" v-model:value="queryForm.deviceType" :options="DEVICE_TYPE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="客户端IP" class="smart-query-form-item">
        <a-input style="width: 150px" v-model:value="queryForm.clientIp" placeholder="精确匹配" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="登录时间" class="smart-query-form-item">
        <a-range-picker v-model:value="createTime" :presets="defaultTimeRanges" style="width: 230px" @change="onChangeCreateTime" />
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

  <!---------- 统计 begin ----------->
  <a-card size="small" :bordered="false" class="stat-card" :loading="statLoading">
    <div class="stat-head">
      <span class="stat-title">{{ statRangeLabel }}</span>
      <span class="stat-tip">统计口径与下方列表的筛选条件一致</span>
    </div>
    <a-row :gutter="16">
      <a-col v-for="item in statItems" :key="item.key" :span="4">
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
  </a-card>
  <!---------- 统计 end ----------->

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block"></div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      :scroll="{ x: 1300 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'memberName'">
          <div>{{ record.memberName || '—' }}</div>
          <div class="cell-sub">{{ record.memberId }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusMeta(text).color">{{ statusMeta(text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'clientIp'">
          <div>{{ record.clientIp || '—' }}</div>
          <div class="cell-sub">{{ record.ipRegion }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'deviceType'">
          <div>{{ record.deviceType || '—' }}</div>
          <div class="cell-sub">{{ [record.osName, record.browserName].filter(Boolean).join(' / ') }}</div>
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
  import { computed, onMounted, reactive, ref } from 'vue';
  import dayjs from 'dayjs';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { memberLoginLogApi } from '/@/api/business/member/member-login-log-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    DEVICE_TYPE_OPTIONS,
    LOGIN_STATUS_ENUM,
    LOGIN_STATUS_OPTIONS,
    metaOf,
  } from '/@/constants/business/member/member-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';

  const DATE_FORMAT = 'YYYY-MM-DD';

  const columns = ref([
    { title: '账号 / 会员号', dataIndex: 'memberName', width: 160 },
    { title: '状态', dataIndex: 'status', width: 90 },
    { title: '客户端IP / 归属地', dataIndex: 'clientIp', width: 200 },
    { title: '设备端 / 系统', dataIndex: 'deviceType', width: 180 },
    { title: '提示信息', dataIndex: 'remark', width: 220, ellipsis: true },
    { title: '登录时间', dataIndex: 'createTime', width: 170, ellipsis: true },
    { title: 'traceId', dataIndex: 'traceId', width: 200, ellipsis: true },
  ]);

  function statusMeta(value) {
    return metaOf(LOGIN_STATUS_ENUM, value, { desc: value, color: 'default' });
  }

  // ---------------------------- 查询 ----------------------------

  const today = dayjs().format(DATE_FORMAT);

  /**
   * 默认就是当天。这张表 append-only 且按月分区，不给时间范围的话首屏是全表扫描；
   * 而运营打开这个页面想知道的第一件事就是「今天有多少人登录」。
   */
  const queryFormState = {
    memberId: undefined,
    memberName: undefined,
    status: undefined,
    deviceType: undefined,
    clientIp: undefined,
    createTimeBegin: today,
    createTimeEnd: today,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const createTime = ref([dayjs(), dayjs()]);
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
    // 重置回「当天」而不是清空：清空等于全表扫描，那不是一个合理的默认态
    createTime.value = [dayjs(), dayjs()];
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
      const [pageRes, statRes] = await Promise.all([
        memberLoginLogApi.queryPage(queryForm),
        memberLoginLogApi.queryStat(queryForm),
      ]);
      tableData.value = pageRes.data.list;
      total.value = pageRes.data.total;
      Object.assign(stat, statRes.data || {});
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
      statLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 统计 ----------------------------

  const statLoading = ref(false);
  const stat = reactive({
    totalCount: 0,
    successCount: 0,
    failCount: 0,
    memberCount: 0,
    successMemberCount: 0,
    ipCount: 0,
  });

  const statRangeLabel = computed(() => {
    const begin = queryForm.createTimeBegin;
    const end = queryForm.createTimeEnd;
    if (!begin && !end) {
      return '全部时间';
    }
    if (begin === end) {
      return begin === today ? '今日登录统计' : `${begin} 登录统计`;
    }
    return `${begin || '最早'} ~ ${end || '至今'} 登录统计`;
  });

  const statItems = computed(() => [
    { key: 'total', label: '登录次数', value: stat.totalCount, cls: '' },
    { key: 'success', label: '成功', value: stat.successCount, cls: 'is-success' },
    { key: 'fail', label: '失败', value: stat.failCount, cls: stat.failCount > 0 ? 'is-fail' : '' },
    {
      key: 'member',
      label: '登录人数',
      value: stat.memberCount,
      cls: '',
      // 这条提示是有必要的：把「次数」当「人数」用会把日活算错一个量级
      tip: '按会员号去重。一个人登录 20 次，次数是 20，人数是 1',
    },
    { key: 'successMember', label: '成功人数', value: stat.successMemberCount, cls: '' },
    {
      key: 'ip',
      label: '独立IP',
      value: stat.ipCount,
      cls: '',
      tip: 'IP 数远小于人数时，多半是同一出口的批量登录，值得看一眼',
    },
  ]);
</script>

<style scoped lang="less">
  .stat-card {
    margin-bottom: 12px;
  }

  .stat-head {
    display: flex;
    gap: 12px;
    align-items: baseline;
    margin-bottom: 12px;
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

  .stat-item {
    padding: 4px 0;
  }

  .stat-value {
    font-size: 22px;
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
</style>
