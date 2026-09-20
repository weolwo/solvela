<!--
  * 某个会员的等级变更留痕
  *
  * 🔴 periodValue 是「变更那一刻」的快照，不是现在的值。
  *    这件事必须在列头上说出来 —— 不说的话，客服会拿它和列表页的当前成长值
  *    对比，发现对不上，然后当成数据错乱去提工单。
  *
  * @Author:    alaric
  * @Date:      2026-09-18
-->
<template>
  <a-drawer :open="visibleFlag" :width="820" title="等级变更留痕" @close="onClose">
    <div class="head">
      <span class="head-main">{{ member.memberName || '—' }}</span>
      <span class="sub">会员号 {{ member.memberId }}</span>
    </div>

    <a-empty v-if="!tableLoading && tableData.length === 0" description="这个会员的等级还没变过" />

    <a-table v-else size="small" :dataSource="tableData" :columns="columns" rowKey="id" bordered :loading="tableLoading" :pagination="false">
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'changeType'">
          <a-tag :color="gradeChangeTypeMeta(record.changeType).color">
            {{ gradeChangeTypeMeta(record.changeType).desc }}
          </a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'newGrade'">
          <span class="from">{{ record.oldGradeName || `等级 ${record.oldGrade}` }}</span>
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
      v-if="tableData.length > 0"
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
  import { gradeChangeTypeMeta } from '/@/constants/business/member/member-grade-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TablePagination from '/@/components/framework/table-pagination/index.vue';

  const columns = ref([
    { title: '类型', dataIndex: 'changeType', width: 100 },
    { title: '变更', dataIndex: 'newGrade', width: 200 },
    { title: '当时的周期成长值', dataIndex: 'currentPeriodValue', width: 140 },
    { title: '原因', dataIndex: 'reason', width: 200, ellipsis: true },
    { title: '操作人', dataIndex: 'operator', width: 110 },
    { title: '时间', dataIndex: 'createTime', width: 170 },
  ]);

  const visibleFlag = ref(false);
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);
  const member = reactive({ memberId: null, memberName: '' });

  const queryForm = reactive({ memberId: undefined, pageNum: 1, pageSize: 10 });

  function show(row) {
    Object.assign(member, { memberId: row.memberId, memberName: row.memberName });
    Object.assign(queryForm, { memberId: row.memberId, pageNum: 1 });
    visibleFlag.value = true;
    queryData();
  }

  function onClose() {
    visibleFlag.value = false;
    tableData.value = [];
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

  .sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
    margin-left: 8px;
  }

  .arrow {
    margin: 0 6px;
    color: rgba(0, 0, 0, 0.45);
  }

  .to {
    font-weight: 600;
  }
</style>
