<!--
  * 某个会员的成长值流水
  *
  * 🔴 basValue × multiplier 两列必须一起显示，不能只给 delta。
  *    客诉的原话是「我这笔为什么是 200 不是 100」—— 答案只有这两个数说得清。
  *
  * 做成抽屉而不是独立页面：它永远是「从某个会员点进来」的，
  * 独立页面就得再做一遍会员筛选，而那个筛选在列表页已经有了。
  *
  * @Author:    alaric
  * @Date:      2026-09-18
-->
<template>
  <a-drawer :open="visibleFlag" :width="900" title="成长值流水" @close="onClose">
    <div class="head">
      <span class="head-main">{{ member.memberName || '—' }}</span>
      <span class="sub">会员号 {{ member.memberId }}</span>
    </div>

    <a-form class="solvela-query-form">
      <a-row class="solvela-query-form-row">
        <a-form-item label="来源" class="solvela-query-form-item">
          <a-select style="width: 140px" v-model:value="queryForm.source" :options="GROWTH_SOURCE_OPTIONS" placeholder="全部" allow-clear />
        </a-form-item>
        <a-form-item label="业务类型" class="solvela-query-form-item">
          <a-input style="width: 160px" v-model:value="queryForm.bizType" placeholder="如 PROPOSAL_REWARD" allow-clear @press-enter="onSearch" />
        </a-form-item>
        <a-form-item label="计入周期" class="solvela-query-form-item">
          <a-input style="width: 120px" v-model:value="queryForm.periodTag" placeholder="yyyyMMdd" allow-clear @press-enter="onSearch" />
        </a-form-item>
        <QueryActions @search="onSearch" @reset="resetQuery" />
      </a-row>
    </a-form>

    <a-table size="small" :dataSource="tableData" :columns="columns" rowKey="id" bordered :loading="tableLoading" :pagination="false">
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'delta'">
          <span class="delta">+{{ record.delta }}</span>
          <!--
            倍率只在 ≠1 时才显示。常态下每行都挂个「×1」是纯噪音，
            而保级期的「×2」正因为少见才需要一眼看见。
          -->
          <a-tag v-if="record.multiplier !== 1" color="orange" class="ml-1">×{{ record.multiplier }}</a-tag>
          <div class="cell-sub">基数 {{ record.baseValue }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'source'">
          <div>{{ record.source }}</div>
          <div class="cell-sub">{{ record.bizType || '—' }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'bizId'">
          <span :title="record.bizId">{{ record.bizId }}</span>
        </template>

        <template v-else>{{ text ?? '—' }}</template>
      </template>
    </a-table>

    <TablePagination
      :defaultPageSize="queryForm.pageSize"
      :currentPage="queryForm.pageNum"
      :total="total"
      @change="queryData"
      v-model:pageSize="queryForm.pageSize"
      v-model:pageNum="queryForm.pageNum"
    />
  </a-drawer>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { memberGradeApi } from '/@/api/business/member/member-grade-api';
  import { GROWTH_SOURCE_OPTIONS } from '/@/constants/business/member/member-grade-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import QueryActions from '/@/components/framework/query-actions/index.vue';
  import TablePagination from '/@/components/framework/table-pagination/index.vue';

  const columns = ref([
    { title: '成长值', dataIndex: 'delta', width: 130 },
    { title: '变动后周期累计', dataIndex: 'afterPeriodValue', width: 130 },
    { title: '来源 / 业务类型', dataIndex: 'source', width: 180 },
    { title: '业务单号', dataIndex: 'bizId', width: 200, ellipsis: true },
    { title: '计入周期', dataIndex: 'periodTag', width: 110 },
    { title: '摘要', dataIndex: 'remark', width: 140, ellipsis: true },
    { title: '时间', dataIndex: 'createTime', width: 170 },
  ]);

  const visibleFlag = ref(false);
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);

  const member = reactive({ memberId: null, memberName: '' });

  const queryFormState = {
    memberId: undefined,
    source: undefined,
    bizType: undefined,
    periodTag: undefined,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });

  function show(row) {
    Object.assign(member, { memberId: row.memberId, memberName: row.memberName });
    Object.assign(queryForm, queryFormState, { memberId: row.memberId });
    visibleFlag.value = true;
    queryData();
  }

  function onClose() {
    visibleFlag.value = false;
    tableData.value = [];
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState, { memberId: member.memberId, pageSize });
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await memberGradeApi.queryGrowthLogPage(queryForm);
      tableData.value = res.list;
      total.value = res.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  defineExpose({ show });
</script>

<style scoped lang="less">
  .head {
    margin-bottom: 12px;
  }

  .head-main {
    font-size: 15px;
    font-weight: 600;
  }

  .sub,
  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .sub {
    margin-left: 8px;
  }

  .delta {
    color: #389e0d;
    font-weight: 600;
  }

  .ml-1 {
    margin-left: 4px;
  }
</style>
