<!--
  * 奖励发放记录 / 发奖审批
  *
  * t_prize_log 是「实际发了什么奖」的审计流水，由派发链路写入。
  * 页面只做两件事：查看 + 审批。
  *
  * ⚠️ 生成器原本给了新增/编辑/删除三个入口，已全部移除 ——
  * 手工增改会让流水与真实发放脱节，而运营看的、对账依据的正是这张表。
  * 需要人工介入的只有审批（approve_mode=1 的奖品）。
  *
  * ⚠️ 本系统有两套并存的审批，别混淆：
  *   · 本页是 prize 域，依据 t_prize_config.approve_mode，回答「这个奖该不该发给这个人」（运营视角）
  *   · 另一套是提案域，依据 t_promotion_config.review_level，回答「这笔钱该不该出」（财务视角）
  *   两层都配了审批就要各批一次，本页批过之后可能还停在提案域等财务。
  *
  * @Author:    alaric
  * @Date:      2026-07-28
-->
<template>
  <!---------- 查询表单 ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="会员名" class="smart-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.memberName" placeholder="会员名" allow-clear />
      </a-form-item>
      <a-form-item label="活动编码" class="smart-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.activityCode" placeholder="活动编码" allow-clear />
      </a-form-item>
      <a-form-item label="奖品编码" class="smart-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.prizeCode" placeholder="奖品编码" allow-clear />
      </a-form-item>
      <a-form-item label="审批状态" class="smart-query-form-item">
        <a-select
          style="width: 140px"
          v-model:value="queryForm.approveStatus"
          :options="APPROVE_STATUS_OPTIONS"
          placeholder="全部"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="执行状态" class="smart-query-form-item">
        <a-select
          style="width: 140px"
          v-model:value="queryForm.status"
          :options="DISPATCH_STATUS_OPTIONS"
          placeholder="全部"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="发放时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 220px" @change="onChangeCreateTime" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button type="primary" @click="onSearch">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="resetQuery" class="smart-margin-left10">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </a-form-item>
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <!-- 待审批是这个页面唯一需要「找出来处理」的东西，给个一键筛选 -->
        <a-button :type="onlyPending ? 'primary' : 'default'" @click="togglePending">
          <template #icon><AuditOutlined /></template>
          {{ onlyPending ? '显示全部' : '只看待审批' }}
        </a-button>
        <a-tag v-if="pendingCount > 0" color="orange" class="ml-2">当前筛选下有 {{ pendingCount }} 条待审批</a-tag>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      :scroll="{ x: 1500, y: 700 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'prizeType'">
          <span>{{ prizeIconOf(record.prizeType) }} {{ record.prizeType }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'approveStatus'">
          <a-tag :color="approveStatusOf(record.approveStatus).color" class="m-0">
            {{ approveStatusOf(record.approveStatus).desc }}
          </a-tag>
          <div v-if="record.approveBy" class="text-[11px] text-slate-400 mt-0.5">
            {{ record.approveBy }} · {{ fmt(record.approveTime) }}
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="dispatchStatusOf(record.status).color" class="m-0">
            {{ dispatchStatusOf(record.status).desc }}
          </a-tag>
          <!-- status=0 的正常含义只有「等待人工审批」；其余情况停在 0 说明链路中断了 -->
          <a-tooltip v-if="record.status === 0 && record.approveStatus !== 1" title="非待审批却停在等待执行，说明派发链路可能中断，需排查">
            <ExclamationCircleOutlined class="text-orange-500 ml-1" />
          </a-tooltip>
        </template>

        <template v-else-if="column.dataIndex === 'failReason'">
          <a-tooltip v-if="record.failReason" :title="record.failReason">
            <span class="text-red-500 text-xs">{{ record.failReason }}</span>
          </a-tooltip>
          <span v-else class="text-slate-300">-</span>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div v-if="record.approveStatus === 1" class="flex gap-1">
            <a-popconfirm
              :title="`确认发放【${record.prizeName}】给 ${record.memberName}？通过后立即派发，不可撤销。`"
              ok-text="确认通过"
              cancel-text="取消"
              @confirm="doApprove(record)"
            >
              <a-button type="link" size="small" class="p-0">通过</a-button>
            </a-popconfirm>
            <a-divider type="vertical" class="mx-1" />
            <a-button type="link" danger size="small" class="p-0" @click="openReject(record)">驳回</a-button>
          </div>
          <span v-else class="text-slate-300 text-xs">-</span>
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

    <!-- 驳回要填理由：留痕是审批的意义之一，没有理由的驳回事后无法复盘 -->
    <a-modal v-model:open="rejectVisible" title="驳回发放" ok-text="确认驳回" cancel-text="取消" @ok="doReject">
      <div v-if="rejectTarget" class="text-sm mb-3">
        将驳回给 <b>{{ rejectTarget.memberName }}</b> 的
        <b>{{ rejectTarget.prizeName }}</b>（{{ rejectTarget.prizeType }} / {{ rejectTarget.prizeValue }}）
      </div>
      <a-textarea v-model:value="rejectReason" :rows="3" placeholder="请填写驳回理由，将记入审批留痕" />
    </a-modal>
  </a-card>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { SearchOutlined, ReloadOutlined, AuditOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue';
  import { prizeLogApi } from '/@/api/business/prize/prize-log/prize-log-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    APPROVE_STATUS_OPTIONS,
    DISPATCH_STATUS_OPTIONS,
    approveStatusOf,
    dispatchStatusOf,
  } from '/@/constants/business/prize/prize-log/prize-log-const';
  import { prizeIcon } from '/@/constants/business/lottery/lottery-const';

  const columns = ref([
    { title: '会员名', dataIndex: 'memberName', width: 120, ellipsis: true },
    { title: '奖品名称', dataIndex: 'prizeName', width: 140, ellipsis: true },
    { title: '类型', dataIndex: 'prizeType', width: 110 },
    { title: '价值', dataIndex: 'prizeValue', width: 90 },
    { title: '奖级', dataIndex: 'prizeLevel', width: 60 },
    { title: '活动编码', dataIndex: 'activityCode', width: 120, ellipsis: true },
    { title: '审批状态', dataIndex: 'approveStatus', width: 130 },
    { title: '执行状态', dataIndex: 'status', width: 110 },
    { title: '失败原因', dataIndex: 'failReason', width: 180, ellipsis: true },
    { title: '来源单号', dataIndex: 'externalBizNo', width: 140, ellipsis: true },
    { title: '发放时间', dataIndex: 'createTime', width: 160, ellipsis: true },
    { title: '操作', dataIndex: 'action', width: 110, fixed: 'right' },
  ]);

  const queryFormState = {
    memberName: undefined,
    activityCode: undefined,
    prizeCode: undefined,
    approveStatus: undefined,
    status: undefined,
    createTime: [],
    createTimeBegin: undefined,
    createTimeEnd: undefined,
    pageNum: 1,
    pageSize: 20,
  };
  const queryForm = reactive({ ...queryFormState });
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);
  const rejectVisible = ref(false);
  const rejectTarget = ref(null);
  const rejectReason = ref('');

  const onlyPending = computed(() => queryForm.approveStatus === 1);
  const pendingCount = computed(() => tableData.value.filter((r) => r.approveStatus === 1).length);

  function prizeIconOf(type) {
    return prizeIcon(type);
  }

  function fmt(value) {
    return value ? String(value).replace('T', ' ').slice(0, 16) : '';
  }

  function togglePending() {
    queryForm.approveStatus = onlyPending.value ? undefined : 1;
    onSearch();
  }

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    queryData();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await prizeLogApi.queryPage(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0];
    queryForm.createTimeEnd = dateStrings[1];
  }

  // ---------------------------- 审批 ----------------------------

  async function doApprove(record) {
    try {
      await prizeLogApi.approve(record.id);
      message.success('已通过，正在派发');
      // 重新拉取而不是本地改状态：通过后会立即走完整派发链路，
      // 执行状态可能变成成功、也可能失败（如预算耗尽），必须以服务端为准
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  function openReject(record) {
    rejectTarget.value = record;
    rejectReason.value = '';
    rejectVisible.value = true;
  }

  async function doReject() {
    if (!rejectReason.value.trim()) {
      message.error('请填写驳回理由');
      return;
    }
    try {
      await prizeLogApi.reject(rejectTarget.value.id, rejectReason.value.trim());
      message.success('已驳回');
      rejectVisible.value = false;
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  onMounted(queryData);
</script>
