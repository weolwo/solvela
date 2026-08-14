<!--
  * 任务配置表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 20:55:10
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="租户ID" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.tenantId" placeholder="租户ID" />
      </a-form-item>
      <a-form-item label="任务名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.taskName" placeholder="任务名称" />
      </a-form-item>
      <a-form-item label="模板Code" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.templateCode" placeholder="模板Code" />
      </a-form-item>
      <a-form-item label="活动编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.activityCode" placeholder="活动编码" />
      </a-form-item>
      <a-form-item label="开始时间" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.startTime" placeholder="开始时间" />
      </a-form-item>
      <!-- 触发事件是开放集合，选项来自服务端注册表 t_task_event，不写死在前端 -->
      <a-form-item label="触发事件" class="smart-query-form-item">
        <a-select
          style="width: 200px"
          v-model:value="queryForm.triggerEvent"
          :options="eventOptions"
          :loading="eventLoading"
          placeholder="全部"
          allowClear
          show-search
          option-filter-prop="label"
        />
      </a-form-item>
      <a-form-item label="任务状态" class="smart-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="CONFIG_STATUS_OPTIONS" placeholder="全部" allowClear />
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
        <a-button @click="confirmBatchDelete" type="primary" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <DeleteOutlined />
          </template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.TASK_CONFIG" :refresh="queryData" />
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
          <a-tag :color="configStatusOf(text).color">{{ configStatusOf(text).desc }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'taskGroup'">
          <span>{{ taskGroupOf(text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'targetAudience'">
          <span>{{ targetAudienceOf(text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'limitType'">
          <span>{{ limitTypeOf(text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="showForm(record)" type="link">编辑</a-button>
            <a-button @click="onDelete(record)" danger type="link">删除</a-button>
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

    <TaskConfigForm ref="formRef" @reloadList="queryData" />
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { useRoute } from 'vue-router';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { taskConfigApi } from '/@/api/business/task/task-config/task-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import TaskConfigForm from './task-config-form.vue';
  import { taskApi } from '/@/api/business/task/task-api';
  import { toEventOptions } from '../task-wizard/task-wizard-const';
  import {
    CONFIG_STATUS_OPTIONS,
    configStatusOf,
    limitTypeOf,
    targetAudienceOf,
    taskGroupOf,
  } from '/@/constants/business/task/task-config/task-config-const';

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: 'id',
      dataIndex: 'id',
      ellipsis: true,
    },
    {
      title: '活动编码',
      dataIndex: 'activityCode',
      ellipsis: true,
    },
    {
      title: '租户ID',
      dataIndex: 'tenantId',
      ellipsis: true,
    },
    {
      title: '任务名称',
      dataIndex: 'taskName',
      ellipsis: true,
    },
    {
      title: '模板Code',
      dataIndex: 'templateCode',
      ellipsis: true,
    },
    {
      title: '触发事件',
      dataIndex: 'triggerEvent',
      ellipsis: true,
    },
    {
      title: '任务分组',
      dataIndex: 'taskGroup',
      ellipsis: true,
    },
    {
      title: '目标人群',
      dataIndex: 'targetAudience',
      ellipsis: true,
    },
    {
      title: '参与频次',
      dataIndex: 'limitType',
      ellipsis: true,
    },
    {
      title: '限制次数',
      dataIndex: 'limitCount',
      ellipsis: true,
    },
    {
      title: '规则配置',
      dataIndex: 'ruleConfig',
      ellipsis: true,
    },
    {
      title: '排序权重',
      dataIndex: 'sortWeight',
      ellipsis: true,
    },
    {
      title: '跳转地址',
      dataIndex: 'actionUrl',
      ellipsis: true,
    },
    {
      title: '展示UI',
      dataIndex: 'uiConfig',
      ellipsis: true,
    },
    {
      title: '任务状态',
      dataIndex: 'status',
      ellipsis: true,
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      ellipsis: true,
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
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
      width: 90,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    tenantId: undefined, //租户ID
    taskName: undefined, //任务名称
    templateCode: undefined, //模板Code
    activityCode: undefined, //活动编码
    startTime: undefined, //开始时间
    triggerEvent: undefined, //触发事件：取值来自注册表 t_task_event
    status: undefined, //任务状态：1-待生效, 2-生效中, 3-已下线
    pageNum: 1,
    pageSize: 10,
  };
  // 查询表单form
  const queryForm = reactive({ ...queryFormState });
  const route = useRoute();
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
      let queryResult = await taskConfigApi.queryPage(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  // ---------------------------- 触发事件下拉（服务端注册表） ----------------------------

  const eventOptions = ref([]);
  const eventLoading = ref(false);

  async function loadEventOptions() {
    eventLoading.value = true;
    try {
      const res = await taskApi.queryEventOptionList();
      eventOptions.value = toEventOptions(res.data);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      eventLoading.value = false;
    }
  }

  // 支持从任务配置向导成功页跳转而来：按 query 回填查询条件，直接定位刚创建的任务
  onMounted(() => {
    const { taskName, activityCode } = route.query;
    if (taskName) {
      queryForm.taskName = taskName;
    }
    if (activityCode) {
      queryForm.activityCode = activityCode;
    }
    loadEventOptions();
    queryData();
  });

  // ---------------------------- 添加/修改 ----------------------------
  const formRef = ref();

  function showForm(data) {
    formRef.value.show(data);
  }

  // ---------------------------- 单个删除 ----------------------------
  //确认删除
  function onDelete(data) {
    Modal.confirm({
      title: '提示',
      content: '确定要删除选吗?',
      okText: '删除',
      okType: 'danger',
      onOk() {
        requestDelete(data);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  //请求删除
  async function requestDelete(data) {
    SmartLoading.show();
    try {
      let deleteForm = {
        goodsIdList: selectedRowKeyList.value,
      };
      await taskConfigApi.delete(data.id);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 批量删除 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  // 批量删除
  function confirmBatchDelete() {
    Modal.confirm({
      title: '提示',
      content: '确定要批量删除这些数据吗?',
      okText: '删除',
      okType: 'danger',
      onOk() {
        requestBatchDelete();
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  //请求批量删除
  async function requestBatchDelete() {
    try {
      SmartLoading.show();
      await taskConfigApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>
