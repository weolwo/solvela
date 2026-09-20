<!--
  * 等级变更留痕（审计视角）
  *
  * 会员成长值列表里已经有「按会员看留痕」的抽屉了，这个页面存在的理由只有一个：
  * <b>「最近一段时间，谁被人工调过级」</b> —— 那是一个跨会员的问题，
  * 抽屉答不了，而它正是审计第一个会问的。
  *
  * 所以默认筛选是「人工调整」，不是「全部」。
  *
  * @Author:    alaric
  * @Date:      2026-09-18
-->
<template>
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="账号" class="solvela-query-form-item">
        <a-input style="width: 150px" v-model:value="queryForm.memberName" placeholder="支持模糊搜索" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="会员号" class="solvela-query-form-item">
        <a-input style="width: 140px" v-model:value="queryForm.memberId" placeholder="10 位数字" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="类型" class="solvela-query-form-item">
        <a-select style="width: 130px" v-model:value="queryForm.changeType" :options="GRADE_CHANGE_TYPE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="操作人" class="solvela-query-form-item">
        <a-input style="width: 130px" v-model:value="queryForm.operator" placeholder="精确匹配" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="发生时间" class="solvela-query-form-item">
        <a-range-picker v-model:value="createTime" :presets="defaultTimeRanges" style="width: 230px" @change="onChangeCreateTime" />
      </a-form-item>
      <QueryActions @search="onSearch" @reset="resetQuery" />
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block">
        <span class="hint">默认只看人工调整 —— 系统升降级量大，混在一起就看不出人改了什么</span>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table size="small" :dataSource="tableData" :columns="columns" rowKey="id" bordered :loading="tableLoading" :pagination="false">
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'memberName'">
          <div>{{ record.memberName || '—' }}</div>
          <div class="cell-sub">{{ record.memberId }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'changeType'">
          <a-tag :color="gradeChangeTypeMeta(record.changeType).color">
            {{ gradeChangeTypeMeta(record.changeType).desc }}
          </a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'newGrade'">
          <span>{{ record.oldGradeName || `等级 ${record.oldGrade}` }}</span>
          <span class="arrow">→</span>
          <span class="to">{{ record.newGradeName || `等级 ${record.newGrade}` }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'operator'">
          {{ record.operator || '系统' }}
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
  </a-card>
</template>

<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { memberGradeApi } from '/@/api/business/member/member-grade-api';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { GRADE_CHANGE_TYPE_ENUM, GRADE_CHANGE_TYPE_OPTIONS, gradeChangeTypeMeta } from '/@/constants/business/member/member-grade-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import QueryActions from '/@/components/framework/query-actions/index.vue';
  import TablePagination from '/@/components/framework/table-pagination/index.vue';

  const columns = ref([
    { title: '账号 / 会员号', dataIndex: 'memberName', width: 170 },
    { title: '类型', dataIndex: 'changeType', width: 100 },
    { title: '变更', dataIndex: 'newGrade', width: 200 },
    { title: '当时的周期成长值', dataIndex: 'currentPeriodValue', width: 140 },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
    { title: '操作人', dataIndex: 'operator', width: 110 },
    { title: '时间', dataIndex: 'createTime', width: 170 },
  ]);

  const queryFormState = {
    memberId: undefined,
    memberName: undefined,
    // 默认人工调整：这个页面存在的理由就是回答「谁改了谁的等级」
    changeType: GRADE_CHANGE_TYPE_ENUM.MANUAL.value,
    operator: undefined,
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

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    createTime.value = [];
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await memberGradeApi.queryGradeLogPage(queryForm);
      tableData.value = res.list;
      total.value = res.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);
</script>

<style scoped lang="less">
  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .hint {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .arrow {
    margin: 0 6px;
    color: rgba(0, 0, 0, 0.45);
  }

  .to {
    font-weight: 600;
  }
</style>
