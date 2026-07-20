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
      <a-form-item label="触发事件" class="smart-query-form-item">
        <a-input
          style="width: 200px"
          v-model:value="queryForm.triggerEvent"
          placeholder="触发事件：ORDERPAID(支付), MEMBERREGISTER(注册), DAILYSIGN(签到), PAGEVIEW(浏览), CUSTOM(自定义)"
        />
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
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
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
  import TaskConfigForm from './task-config-form.vue';

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
      title: '触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义)',
      dataIndex: 'triggerEvent',
      ellipsis: true,
    },
    {
      title: '任务分组：NEWBIE(新手), DAILY(日常), PROMO(大促), VIP(会员专属)',
      dataIndex: 'taskGroup',
      ellipsis: true,
    },
    {
      title: '目标人群：ALL(全部), NEW_MEMBER(新会员), OLD_MEMBER(老会员)',
      dataIndex: 'targetAudience',
      ellipsis: true,
    },
    {
      title: '参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制)',
      dataIndex: 'limitType',
      ellipsis: true,
    },
    {
      title: '配合频次类型，限制次数',
      dataIndex: 'limitCount',
      ellipsis: true,
    },
    {
      title: '规则配置',
      dataIndex: 'ruleConfig',
      ellipsis: true,
    },
    {
      title: '排序权重，越大越靠前',
      dataIndex: 'sortWeight',
      ellipsis: true,
    },
    {
      title: '跳转地址',
      dataIndex: 'actionUrl',
      ellipsis: true,
    },
    {
      title: '展示UI(图标/角标等)',
      dataIndex: 'uiConfig',
      ellipsis: true,
    },
    {
      title: '任务状态 1-待生效, 2-生效中, 3-已下线',
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
    triggerEvent: undefined, //触发事件：ORDERPAID(支付), MEMBERREGISTER(注册), DAILYSIGN(签到), PAGEVIEW(浏览), CUSTOM(自定义)
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

  // 支持从任务配置向导成功页跳转而来：按 query 回填查询条件，直接定位刚创建的任务
  onMounted(() => {
    const { taskName, activityCode } = route.query;
    if (taskName) {
      queryForm.taskName = taskName;
    }
    if (activityCode) {
      queryForm.activityCode = activityCode;
    }
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
