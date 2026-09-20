<!--
  * 会员成长值与等级
  *
  * 【为什么「距下一级」是一列而不是让运营自己减】
  * 客服接到的问题九成是「我还差多少升级」。这个数如果要靠「门槛表 − 当前成长值」
  * 心算，客服就会算错，而算错的后果是给用户一个错误的承诺。
  *
  * 【🔴 等级与成长值可能对不上，那不是 bug】
  * 人工调级只改等级不改成长值，所以「白金 / 本周期 300」是合法状态。
  * 页面上用一个标记说出来，否则运营会当成数据错乱去提工单。
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
      <a-form-item label="等级区间" class="solvela-query-form-item">
        <a-select style="width: 130px" v-model:value="queryForm.gradeMin" :options="gradeOptions" placeholder="最低" allow-clear />
        <span class="range-sep">~</span>
        <a-select style="width: 130px" v-model:value="queryForm.gradeMax" :options="gradeOptions" placeholder="最高" allow-clear />
      </a-form-item>
      <!--
        这个筛选是做召回用的：缓冲期里的人正处在「可能掉级」的窗口，
        是推送和挽留的第一批目标。阶段 4 有了缓冲期之后才会有数据。
      -->
      <a-form-item label="保级缓冲" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="queryForm.inProtect" :options="PROTECT_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <QueryActions @search="onSearch" @reset="resetQuery" />
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block"></div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      :scroll="{ x: 1200 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="memberId"
      bordered
      :loading="tableLoading"
      :pagination="false"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'memberName'">
          <div>{{ record.memberName || '—' }}</div>
          <div class="cell-sub">{{ record.memberId }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'currentGrade'">
          <a-tag color="blue">{{ record.gradeName || `等级 ${record.currentGrade}` }}</a-tag>
          <a-tag v-if="record.inProtect" color="orange">保级中</a-tag>
          <!--
            🔴 等级高于成长值该到的档 = 人工调过级（或运营调高了门槛）。
            不标出来的话，运营看到「白金 / 300 成长值」会当成数据错乱。
          -->
          <a-tooltip v-else-if="isAboveEarned(record)" title="当前等级高于成长值对应的档位：多半是人工调级，期末结算会按真实成长值重判">
            <a-tag color="purple">人工/超档</a-tag>
          </a-tooltip>
          <div class="cell-sub">{{ record.gradeSince ? `${record.gradeSince} 起` : '—' }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'currentPeriodValue'">
          <span class="num">{{ record.currentPeriodValue ?? 0 }}</span>
          <div class="cell-sub">终身 {{ record.totalValue ?? 0 }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'nextGap'">
          <template v-if="record.nextGap === null || record.nextGap === undefined">
            <span class="cell-sub">已是最高档</span>
          </template>
          <template v-else>
            <span class="num">{{ record.nextGap }}</span>
            <div class="cell-sub">距「{{ record.nextGradeName }}」</div>
          </template>
        </template>

        <template v-else-if="column.dataIndex === 'periodEnd'">
          <div>{{ record.periodEnd || '—' }}</div>
          <div class="cell-sub">{{ record.periodStart ? `${record.periodStart} 起` : '' }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div class="solvela-table-operate">
            <a-button type="link" size="small" @click="showGrowthLog(record)">成长值流水</a-button>
            <a-button type="link" size="small" @click="showGradeLog(record)">变更留痕</a-button>
            <a-button v-privilege="'memberGrade:adjust'" type="link" size="small" danger @click="showAdjust(record)">人工调级</a-button>
          </div>
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

  <MemberGrowthLogDrawer ref="growthLogDrawer" />
  <MemberGradeLogDrawer ref="gradeLogDrawer" />
  <MemberGradeAdjustModal ref="adjustModal" @reloadList="queryData" />
</template>

<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { memberGradeApi } from '/@/api/business/member/member-grade-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import QueryActions from '/@/components/framework/query-actions/index.vue';
  import TablePagination from '/@/components/framework/table-pagination/index.vue';
  import MemberGrowthLogDrawer from './member-growth-log-drawer.vue';
  import MemberGradeLogDrawer from './member-grade-log-drawer.vue';
  import MemberGradeAdjustModal from './member-grade-adjust-modal.vue';

  const PROTECT_OPTIONS = [
    { value: true, label: '缓冲期内' },
    { value: false, label: '不在缓冲期' },
  ];

  const columns = ref([
    { title: '账号 / 会员号', dataIndex: 'memberName', width: 170 },
    { title: '当前等级', dataIndex: 'currentGrade', width: 220 },
    { title: '本周期成长值', dataIndex: 'currentPeriodValue', width: 130 },
    { title: '距下一级', dataIndex: 'nextGap', width: 140 },
    { title: '本周期截止', dataIndex: 'periodEnd', width: 190 },
    { title: '操作', dataIndex: 'action', fixed: 'right', width: 230 },
  ]);

  const queryFormState = {
    memberId: undefined,
    memberName: undefined,
    gradeMin: undefined,
    gradeMax: undefined,
    inProtect: undefined,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);
  const gradeOptions = ref([]);

  const growthLogDrawer = ref();
  const gradeLogDrawer = ref();
  const adjustModal = ref();

  /**
   * 当前等级是不是高于「成长值该到的档」。
   *
   * 判据就是 nextGap：服务端的 nextGrade 是按成长值算的下一档，
   * 所以 nextGrade <= currentGrade 就意味着他挂着的牌子已经超过了自己攒到的位置。
   */
  function isAboveEarned(record) {
    return record.nextGrade !== null && record.nextGrade !== undefined && record.nextGrade <= record.currentGrade;
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await memberGradeApi.queryGrowthPage(queryForm);
      tableData.value = res.list;
      total.value = res.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  async function loadGrades() {
    try {
      const list = (await memberGradeApi.listConfig()) || [];
      // 等级是配置，运营随时能加一档 —— 下拉必须来自接口，不能在前端写死
      gradeOptions.value = list.map((item) => ({ value: item.gradeCode, label: `${item.gradeName}（${item.gradeCode}）` }));
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  function showGrowthLog(record) {
    growthLogDrawer.value.show(record);
  }

  function showGradeLog(record) {
    gradeLogDrawer.value.show(record);
  }

  function showAdjust(record) {
    adjustModal.value.show(record);
  }

  onMounted(() => {
    loadGrades();
    queryData();
  });
</script>

<style scoped lang="less">
  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .num {
    font-weight: 600;
  }

  .range-sep {
    margin: 0 6px;
    color: rgba(0, 0, 0, 0.45);
  }
</style>
