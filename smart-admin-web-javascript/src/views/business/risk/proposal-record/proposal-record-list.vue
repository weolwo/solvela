<!--
  * 提案表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:13:50
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
      <a-form-item label="更新时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.updateTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeUpdateTime" />
      </a-form-item>
      <a-form-item label="创建时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCreateTime" />
      </a-form-item>
      <a-form-item label="优惠配置ID" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.promotionConfigId" placeholder="优惠配置ID" />
      </a-form-item>
      <a-form-item
        label="状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截"
        class="smart-query-form-item"
      >
        <a-input
          style="width: 200px"
          v-model:value="queryForm.status"
          placeholder="状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截"
        />
      </a-form-item>
      <a-form-item label="来源：TASK(任务), DRAW(抽奖), MANUAL(人工)" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.sourceType" placeholder="来源：TASK(任务), DRAW(抽奖), MANUAL(人工)" />
      </a-form-item>
      <a-form-item label="来源单号(taskRecordId 或 drawLogTraceId)" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.sourceBizId" placeholder="来源单号(taskRecordId 或 drawLogTraceId)" />
      </a-form-item>
      <a-form-item label="一审人" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.firstReviewer" placeholder="一审人" />
      </a-form-item>
      <a-form-item label="提案单号，服务端生成，对外唯一标识" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.tradeNo" placeholder="提案单号，服务端生成，对外唯一标识" />
      </a-form-item>
      <a-form-item label="SCORE/BALANCE/COUPON/PHYSICAL" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.assetType" placeholder="SCORE/BALANCE/COUPON/PHYSICAL" />
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

    <ProposalRecordForm ref="formRef" @reloadList="queryData" />
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { proposalRecordApi } from '/@/api/business/risk/proposal-record/proposal-record-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import ProposalRecordForm from './proposal-record-form.vue';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';

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
      title: '提案单号，服务端生成，对外唯一标识',
      dataIndex: 'tradeNo',
      ellipsis: true,
    },
    {
      title: '会员名',
      dataIndex: 'memberName',
      ellipsis: true,
    },
    {
      title: 'SCORE/BALANCE/COUPON/PHYSICAL',
      dataIndex: 'assetType',
      ellipsis: true,
    },
    {
      title: '资产引用：券模/SKU，值类资产为空',
      dataIndex: 'assetRef',
      ellipsis: true,
    },
    {
      title: '发放金额/积分数',
      dataIndex: 'amount',
      ellipsis: true,
    },
    {
      title: '发放数量，扣 used_quota 用',
      dataIndex: 'quantity',
      ellipsis: true,
    },
    {
      title: '来源：TASK(任务), DRAW(抽奖), MANUAL(人工)',
      dataIndex: 'sourceType',
      ellipsis: true,
    },
    {
      title: '来源单号(task_record_id 或 draw_log_trace_id)',
      dataIndex: 'sourceBizId',
      ellipsis: true,
    },
    {
      title: '优惠配置ID',
      dataIndex: 'promotionConfigId',
      ellipsis: true,
    },
    {
      title: '状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截',
      dataIndex: 'status',
      ellipsis: true,
    },
    {
      title: '执行失败/风控拦截原因',
      dataIndex: 'remark',
      ellipsis: true,
    },
    {
      title: '一审人',
      dataIndex: 'firstReviewer',
      ellipsis: true,
    },
    {
      title: '一审时间',
      dataIndex: 'firstReviewTime',
      ellipsis: true,
    },
    {
      title: '二审人',
      dataIndex: 'secondReviewer',
      ellipsis: true,
    },
    {
      title: '二审时间',
      dataIndex: 'secondReviewTime',
      ellipsis: true,
    },
    {
      title: '审核意见/驳回理由',
      dataIndex: 'reviewComment',
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
    memberName: undefined, //会员名
    updateTime: [], //更新时间
    updateTimeBegin: undefined, //更新时间 开始
    updateTimeEnd: undefined, //更新时间 结束
    createTime: [], //创建时间
    createTimeBegin: undefined, //创建时间 开始
    createTimeEnd: undefined, //创建时间 结束
    promotionConfigId: undefined, //优惠配置ID
    status: undefined, //状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截
    sourceType: undefined, //来源：TASK(任务), DRAW(抽奖), MANUAL(人工)
    sourceBizId: undefined, //来源单号(taskRecordId 或 drawLogTraceId)
    firstReviewer: undefined, //一审人
    tradeNo: undefined, //提案单号，服务端生成，对外唯一标识
    assetType: undefined, //SCORE/BALANCE/COUPON/PHYSICAL
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
      let queryResult = await proposalRecordApi.queryPage(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function onChangeUpdateTime(dates, dateStrings) {
    queryForm.updateTimeBegin = dateStrings[0];
    queryForm.updateTimeEnd = dateStrings[1];
  }

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0];
    queryForm.createTimeEnd = dateStrings[1];
  }

  onMounted(queryData);

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
      await proposalRecordApi.delete(data.id);
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
      await proposalRecordApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>
