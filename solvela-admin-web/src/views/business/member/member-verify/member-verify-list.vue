<!--
  * 会员实名信息
  *
  * 【运营在这个页面只做一件事：审核】通过 / 驳回。
  * 姓名和身份证是用户提交的，后台改它们没有任何合法用途 ——
  * 生成器留的那个「能改所有字段」的表单已经删除，后端也没有对应接口。
  *
  * 【姓名与身份证在列表里是脱敏的】而且脱敏发生在<b>服务端</b>：
  * 前端脱敏意味着明文已经过网到浏览器，F12 一开就是全量身份证号。
  * 要看完整信息只有审核弹窗那一条路，走单独的 detail 接口 + 单独的权限点。
  *
  * 【没有按姓名搜索】那一列在库里是密文，SQL 层没法模糊匹配 ——
  * 真要按姓名找人只能全表解密再比对。检索入口只有账号 / 会员号 / 状态 / 时间。
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 21:00:09
  * @Copyright  weolwo
-->
<template>
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="账号" class="solvela-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.memberName" placeholder="支持模糊搜索" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="会员号" class="solvela-query-form-item">
        <a-input style="width: 150px" v-model:value="queryForm.memberId" placeholder="10 位数字" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="认证状态" class="solvela-query-form-item">
        <a-select style="width: 130px" v-model:value="queryForm.verifyStatus" :options="VERIFY_STATUS_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="提交时间" class="solvela-query-form-item">
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

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block">
        <!-- 待审是这个页面的主任务，给它一个一键筛选 -->
        <a-button size="small" :type="onlyPending ? 'primary' : 'default'" @click="togglePending"> 只看待审{{ pendingHint }} </a-button>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      :scroll="{ x: 1200 }"
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
          <div class="cell-sub">{{ record.nickname }} · {{ record.memberId }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'realName'">
          <!-- 服务端脱敏后的值。要看完整的走审核弹窗 -->
          <span class="masked">{{ text || '—' }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'idCard'">
          <span class="masked">{{ text || '—' }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'verifyStatus'">
          <a-tag :color="statusMeta(text).color">{{ statusMeta(text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'failReason'">
          <span v-if="text" class="fail-reason">{{ text }}</span>
          <span v-else class="cell-sub">—</span>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div class="solvela-table-operate">
            <a-button type="link" @click="showAudit(record)">
              {{ record.verifyStatus === VERIFY_STATUS_ENUM.PENDING.value ? '审核' : '查看' }}
            </a-button>
          </div>
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

  <MemberVerifyAuditModal ref="auditModalRef" @reloadList="queryData" />
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { memberVerifyApi } from '/@/api/business/member/member-verify-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { metaOf, VERIFY_STATUS_ENUM, VERIFY_STATUS_OPTIONS } from '/@/constants/business/member/member-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import MemberVerifyAuditModal from './member-verify-audit-modal.vue';

  const columns = ref([
    { title: '账号 / 会员号', dataIndex: 'memberName', width: 180 },
    { title: '真实姓名', dataIndex: 'realName', width: 120 },
    { title: '身份证号', dataIndex: 'idCard', width: 200 },
    { title: '认证状态', dataIndex: 'verifyStatus', width: 100 },
    { title: '驳回原因', dataIndex: 'failReason', width: 220, ellipsis: true },
    { title: '提交时间', dataIndex: 'createTime', width: 170, ellipsis: true },
    { title: '通过时间', dataIndex: 'verifyTime', width: 170, ellipsis: true },
    { title: '操作', dataIndex: 'action', width: 90, fixed: 'right' },
  ]);

  function statusMeta(value) {
    return metaOf(VERIFY_STATUS_ENUM, value, VERIFY_STATUS_ENUM.NONE);
  }

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    memberId: undefined,
    memberName: undefined,
    verifyStatus: undefined,
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

  const onlyPending = computed(() => queryForm.verifyStatus === VERIFY_STATUS_ENUM.PENDING.value);
  const pendingHint = computed(() => (onlyPending.value && total.value ? `（${total.value}）` : ''));

  function togglePending() {
    queryForm.verifyStatus = onlyPending.value ? undefined : VERIFY_STATUS_ENUM.PENDING.value;
    onSearch();
  }

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
    try {
      const res = await memberVerifyApi.queryPage(queryForm);
      tableData.value = res.list;
      total.value = res.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 审核 ----------------------------

  const auditModalRef = ref();

  function showAudit(record) {
    auditModalRef.value.show(record);
  }
</script>

<style scoped lang="less">
  .cell-sub {
    font-size: 12px;
    color: #bfbfbf;
  }

  .masked {
    font-family: Menlo, Consolas, monospace;
    letter-spacing: 0.5px;
  }

  .fail-reason {
    color: #d97706;
  }
</style>
