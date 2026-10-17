<!--
  * 安全中心 - 安全事件（网关方案 §6.4）
  *
  * 【只在状态变化时有一行】开始处罚、升档、被哪道保险压下了 —— 一次攻击几行，而不是每个被挡的请求一行。
  * 封禁期间被挡掉的量看名单页的「被挡」那一列。
  *
  * 【🔴 命中片段与 UA 是攻击者可控的内容】只能按纯文本渲染（{{ }}），绝不能 v-html：
  * 一次探测请求就能在运营打开这个页面时执行脚本。本页有用例钉着（security-incident-list.spec.js）。
  *
  * 【必须带时间范围】表按月分区。不选时间时服务端兜最近 7 天，一次最多 31 天。
  *
  * @Date 2026-10-17
-->
<template>
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="类型" class="solvela-query-form-item">
        <a-select style="width: 150px" v-model:value="queryForm.incidentType" :options="SECURITY_INCIDENT_TYPE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="IP" class="solvela-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.clientIp" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="设备号" class="solvela-query-form-item">
        <a-input style="width: 260px" v-model:value="queryForm.deviceId" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="会员号" class="solvela-query-form-item">
        <a-input style="width: 140px" v-model:value="queryForm.memberId" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="时间" class="solvela-query-form-item">
        <a-range-picker v-model:value="timeRange" show-time valueFormat="YYYY-MM-DD HH:mm:ss" />
      </a-form-item>
      <QueryActions @search="onSearch" @reset="resetQuery" />
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false" style="margin-top: 12px">
    <a-table size="small" :dataSource="tableData" :columns="columns" :pagination="false" :loading="loading" rowKey="id" bordered :scroll="{ x: 1500 }">
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.dataIndex === 'incidentType'">
          <a-tag :color="metaOf(SECURITY_INCIDENT_TYPE_ENUM, text).color">{{ metaOf(SECURITY_INCIDENT_TYPE_ENUM, text).desc }}</a-tag>
          <div class="cell-sub">{{ record.ruleCode }}<template v-if="record.penaltyLevel"> · 第 {{ record.penaltyLevel }} 档</template></div>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a-tag :color="metaOf(SECURITY_INCIDENT_ACTION_ENUM, text).color">{{ metaOf(SECURITY_INCIDENT_ACTION_ENUM, text).desc }}</a-tag>
          <div v-if="record.suppressReason" class="cell-sub">
            压下：{{ metaOf(SECURITY_INCIDENT_SUPPRESS_ENUM, record.suppressReason).desc }}
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'who'">
          <div>{{ record.clientIp || '—' }}</div>
          <div v-if="record.deviceId" class="cell-sub cell-mono">{{ record.deviceId }}</div>
          <div v-if="record.memberId" class="cell-sub">会员 {{ record.memberId }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'request'">
          <span class="cell-mono">{{ record.httpMethod }} {{ record.requestPath }}</span>
        </template>

        <!-- 🔴 下面两列是攻击者可控的内容：只用 {{ }}，不许 v-html -->
        <template v-else-if="column.dataIndex === 'matchedSample'">
          <div v-if="record.matchedField" class="cell-sub">
            {{ metaOf(SECURITY_INCIDENT_FIELD_ENUM, record.matchedField).desc }}<template v-if="record.matchedParam"> · {{ record.matchedParam }}</template>
          </div>
          <span class="cell-mono untrusted" data-untrusted="sample">{{ text }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'userAgent'">
          <span class="cell-mono untrusted" data-untrusted="ua" :title="text">{{ text }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'penaltyUntil'">
          <span v-if="text">{{ text }}</span>
          <span v-else class="cell-sub">—</span>
        </template>
      </template>
    </a-table>

    <TablePagination v-model:pageNum="queryForm.pageNum" v-model:pageSize="queryForm.pageSize" :total="total" @change="queryData" />
  </a-card>
</template>

<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { securityApi } from '/@/api/business/security/security-api';
  import QueryActions from '/@/components/framework/query-actions/index.vue';
  import TablePagination from '/@/components/framework/table-pagination/index.vue';
  import {
    SECURITY_INCIDENT_ACTION_ENUM,
    SECURITY_INCIDENT_FIELD_ENUM,
    SECURITY_INCIDENT_SUPPRESS_ENUM,
    SECURITY_INCIDENT_TYPE_ENUM,
    SECURITY_INCIDENT_TYPE_OPTIONS,
  } from '/@/constants/business/security/security-const';
  import { riskMetaOf } from '/@/constants/business/member/risk-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const columns = [
    { title: '时间', dataIndex: 'createTime', width: 165 },
    { title: '类型 / 规则', dataIndex: 'incidentType', width: 160 },
    { title: '处置', dataIndex: 'action', width: 150 },
    { title: '来源', dataIndex: 'who', width: 260 },
    { title: '请求', dataIndex: 'request', width: 240 },
    { title: '命中片段', dataIndex: 'matchedSample', width: 220 },
    { title: 'UA', dataIndex: 'userAgent', width: 220, ellipsis: true },
    { title: '处罚到期', dataIndex: 'penaltyUntil', width: 165 },
    { title: '链路', dataIndex: 'traceId', width: 140, ellipsis: true },
  ];

  const queryFormDefault = () => ({
    incidentType: undefined,
    clientIp: '',
    deviceId: '',
    memberId: '',
    pageNum: 1,
    pageSize: 20,
  });

  const queryForm = reactive(queryFormDefault());
  const timeRange = ref([]);
  const loading = ref(false);
  const tableData = ref([]);
  const total = ref(0);

  function metaOf(enumObj, value) {
    return riskMetaOf(enumObj, value, { desc: value, color: 'default' });
  }

  async function queryData() {
    loading.value = true;
    try {
      const res = await securityApi.queryIncidentPage({
        incidentType: queryForm.incidentType,
        clientIp: queryForm.clientIp || undefined,
        deviceId: queryForm.deviceId || undefined,
        memberId: queryForm.memberId || undefined,
        createTimeBegin: timeRange.value?.[0],
        createTimeEnd: timeRange.value?.[1],
        pageNum: queryForm.pageNum,
        pageSize: queryForm.pageSize,
      });
      tableData.value = res.list;
      total.value = res.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function resetQuery() {
    Object.assign(queryForm, queryFormDefault());
    timeRange.value = [];
    queryData();
  }

  onMounted(queryData);
</script>

<style scoped>
  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .cell-mono {
    font-family: var(--font-family-monospace, monospace);
    font-size: 12px;
    word-break: break-all;
  }
</style>
