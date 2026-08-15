<!--
  * 任务记录表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 21:02:56
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="租户ID" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.tenantId" placeholder="租户ID" />
      </a-form-item>
      <a-form-item label="会员名" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.memberName" placeholder="会员名" />
      </a-form-item>
      <a-form-item label="活动编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.activityCode" placeholder="活动编码" />
      </a-form-item>
      <a-form-item label="开始时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.validStartTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeValidStartTime" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="RECORD_STATUS_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="达标时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.completeTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCompleteTime" />
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

  <a-card size="small" :bordered="false" :hoverable="true">
    <!---------- 表格操作行 begin ----------->
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button @click="showForm" type="primary" size="small">
          <template #icon>
            <PlusOutlined />
          </template>
          新建
        </a-button>
        <!-- 只提供批量禁用：任务记录是运行态流水（含 rule_snapshot / prize_snapshot），
             删掉等于抹掉这个人当初参与过的证据，客诉自证就没了依据；
             禁用 = 置为已过期，不再推进也不再发奖 -->
        <a-button @click="confirmBatchDisable" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <StopOutlined />
          </template>
          批量禁用
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.TASK_RECORD" :refresh="queryData" />
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
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="recordStatusOf(text).color">{{ recordStatusOf(text).desc }}</a-tag>
        </template>

        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="showFlow(record)" type="link">事件流水</a-button>
            <a-button @click="showForm(record)" type="link">编辑</a-button>
          </div>
        </template>
      </template>
    </a-table>
    <!---------- 表格 end ----------->

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

    <TaskRecordForm ref="formRef" @reloadList="queryData" />
  </a-card>

  <!---------- 事件流水抽屉：客诉自证入口 ----------->
  <a-drawer :open="flowVisible" title="任务事件流水" :width="900" @close="flowVisible = false">
    <a-alert type="info" show-icon class="mb-3">
      <template #message>
        这里是这条任务记录收到过的<b>全部事件</b>，含<b>被丢弃的</b>及其原因。
        「用户下了 99 元的单为什么没进度」这类问题，答案就在丢弃原因里。
      </template>
    </a-alert>

    <a-spin :spinning="flowLoading">
      <a-empty v-if="!flowLoading && flowList.length === 0" description="该任务记录还没有任何事件流水" />
      <a-timeline v-else class="mt-2">
        <a-timeline-item v-for="item in flowList" :key="item.id" :color="item.flowType === 1 ? 'green' : 'red'">
          <div class="flex items-center gap-2 flex-wrap">
            <a-tag :color="item.flowType === 1 ? 'green' : 'red'">
              {{ item.flowType === 1 ? '已推进' : '已丢弃' }}
            </a-tag>
            <b>{{ item.eventCode }}</b>
            <span class="text-gray-500!">单号 {{ item.eventBizId }}</span>
            <span class="text-gray-400! text-xs">{{ item.createTime }}</span>
          </div>

          <div v-if="item.flowType === 1" class="mt-1">
            进度
            <a-tag color="blue">+{{ item.deltaMetric }}</a-tag>
            →
            <a-tag color="blue">{{ item.afterMetric }}</a-tag>
          </div>
          <div v-else class="mt-1 text-red-600!">丢弃原因：{{ item.discardReason || '（未记录）' }}</div>

          <a-button v-if="item.eventPayload" type="link" size="small" class="pl-0!" @click="togglePayload(item.id)">
            {{ expandedPayloadId === item.id ? '收起事件原文' : '查看事件原文' }}
          </a-button>
          <pre v-if="expandedPayloadId === item.id" class="bg-gray-50 p-2 rounded text-xs overflow-x-auto">{{ item.eventPayload }}</pre>
        </a-timeline-item>
      </a-timeline>
    </a-spin>
  </a-drawer>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { taskRecordApi } from '/@/api/business/task/task-record/task-record-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { RECORD_STATUS_ENUM, RECORD_STATUS_OPTIONS, recordStatusOf } from '/@/constants/business/task/task-record/task-record-const';
  import TaskRecordForm from './task-record-form.vue';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { taskEventApi } from '/@/api/business/task/task-event/task-event-api';

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: 'id',
      dataIndex: 'id',
      ellipsis: true,
    },
    {
      title: '租户ID',
      dataIndex: 'tenantId',
      ellipsis: true,
    },
    {
      title: '会员名',
      dataIndex: 'memberName',
      ellipsis: true,
    },
    {
      title: '任务配置ID',
      dataIndex: 'taskConfigId',
      ellipsis: true,
    },
    {
      title: '活动编码',
      dataIndex: 'activityCode',
      ellipsis: true,
    },
    {
      title: '周期',
      dataIndex: 'periodKey',
      ellipsis: true,
    },
    {
      title: '开始时间',
      dataIndex: 'validStartTime',
      ellipsis: true,
    },
    {
      title: '过期时间',
      dataIndex: 'validEndTime',
      ellipsis: true,
    },
    {
      title: '当前进度',
      dataIndex: 'currentMetric',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      ellipsis: true,
    },
    {
      title: '进度详情',
      dataIndex: 'progressData',
      ellipsis: true,
    },
    {
      title: '规则快照',
      dataIndex: 'ruleSnapshot',
      ellipsis: true,
    },
    {
      title: '奖励快照',
      dataIndex: 'prizeSnapshot',
      ellipsis: true,
    },
    {
      title: '达标时间',
      dataIndex: 'completeTime',
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
    {
      title: '操作',
      dataIndex: 'action',
      fixed: 'right',
      width: 190,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    tenantId: undefined, //租户ID
    memberName: undefined, //会员名
    activityCode: undefined, //活动编码
    validStartTime: [], //开始时间
    validStartTimeBegin: undefined, //开始时间 开始
    validStartTimeEnd: undefined, //开始时间 结束
    status: undefined, //状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期
    completeTime: [], //达标时间
    completeTimeBegin: undefined, //达标时间 开始
    completeTimeEnd: undefined, //达标时间 结束
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
      let queryResult = await taskRecordApi.queryPage(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function onChangeValidStartTime(dates, dateStrings) {
    queryForm.validStartTimeBegin = dateStrings[0];
    queryForm.validStartTimeEnd = dateStrings[1];
  }

  function onChangeCompleteTime(dates, dateStrings) {
    queryForm.completeTimeBegin = dateStrings[0];
    queryForm.completeTimeEnd = dateStrings[1];
  }

  onMounted(queryData);

  // ---------------------------- 添加/修改 ----------------------------
  const formRef = ref();

  function showForm(data) {
    formRef.value.show(data);
  }

  // ---------------------------- 事件流水（客诉自证） ----------------------------

  const flowVisible = ref(false);
  const flowLoading = ref(false);
  const flowList = ref([]);
  const expandedPayloadId = ref(null);

  async function showFlow(record) {
    flowVisible.value = true;
    flowLoading.value = true;
    flowList.value = [];
    expandedPayloadId.value = null;
    try {
      const res = await taskEventApi.queryRecordFlow(record.id);
      flowList.value = res.data || [];
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      flowLoading.value = false;
    }
  }

  function togglePayload(id) {
    expandedPayloadId.value = expandedPayloadId.value === id ? null : id;
  }

  // ---------------------------- 批量禁用 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function confirmBatchDisable() {
    Modal.confirm({
      title: '批量禁用',
      content: `确定要禁用选中的 ${selectedRowKeyList.value.length} 条任务记录吗？将置为「已过期」，不再推进进度也不再发奖，历史流水保留。`,
      okText: '禁用',
      okType: 'danger',
      onOk() {
        requestBatchDisable();
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function requestBatchDisable() {
    try {
      SmartLoading.show();
      // 服务端只放行 3-已过期：t_task_record.status 没有「禁用」这一档
      await taskRecordApi.updateStatus({
        idList: selectedRowKeyList.value,
        status: RECORD_STATUS_ENUM.EXPIRED.value,
      });
      message.success('已批量禁用');
      selectedRowKeyList.value = [];
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>
