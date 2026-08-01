<!--
  * 用户号码记录
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 11:57:08
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="租户id" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.tenantId" placeholder="租户id" />
      </a-form-item>
      <a-form-item label="彩票编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.lotteryCode" placeholder="彩票编码" />
      </a-form-item>
      <a-form-item label="归属期号" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.issueNo" placeholder="归属期号" />
      </a-form-item>
      <a-form-item label="彩票号码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.ticketNumber" placeholder="彩票号码" />
      </a-form-item>
      <a-form-item label="创建时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCreateTime" />
      </a-form-item>
      <a-form-item label="中奖状态" class="smart-query-form-item">
        <SmartEnumSelect width="200px" v-model:value="queryForm.winStatus" enum-name="WIN_STATUS_ENUM" placeholder="请选择中奖状态" />
      </a-form-item>
      <a-form-item label="会员名" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.memberName" placeholder="会员名" />
      </a-form-item>
      <a-form-item label="奖励等级" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.prizeLevel" placeholder="奖励等级" />
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
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.LOTTERY_RECORD" :refresh="queryData" />
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
    />
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
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { lotteryRecordApi } from '/@/api/business/lottery/lottery-record/lottery-record-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  // 生成器产物漏了这行：模板里用了 <SmartEnumSelect> 却没导入，
  // 表现是控制台一直报 Failed to resolve component（组件不渲染，但页面其余部分正常）
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: 'id',
      dataIndex: 'id',
      ellipsis: true,
    },
    {
      title: '租户id',
      dataIndex: 'tenantId',
      ellipsis: true,
    },
    {
      title: '彩票编码',
      dataIndex: 'lotteryCode',
      ellipsis: true,
    },
    {
      title: '归属期号',
      dataIndex: 'issueNo',
      ellipsis: true,
    },
    {
      title: 'FPE算号基数',
      dataIndex: 'sequenceNo',
      ellipsis: true,
    },
    {
      title: '彩票号码',
      dataIndex: 'ticketNumber',
      ellipsis: true,
    },
    {
      title: '会员名',
      dataIndex: 'memberName',
      ellipsis: true,
    },
    {
      title: '领取时间',
      dataIndex: 'obtainTime',
      ellipsis: true,
    },
    {
      title: '中奖状态',
      dataIndex: 'winStatus',
      ellipsis: true,
    },
    {
      title: '奖励等级',
      dataIndex: 'prizeLevel',
      ellipsis: true,
    },
    {
      title: '签名',
      dataIndex: 'securitySign',
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
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    tenantId: undefined, //租户id
    lotteryCode: undefined, //彩票编码
    issueNo: undefined, //归属期号
    ticketNumber: undefined, //彩票号码
    createTime: [], //创建时间
    createTimeBegin: undefined, //创建时间 开始
    createTimeEnd: undefined, //创建时间 结束
    winStatus: undefined, //中奖状态: 0-未开奖, 1-未中奖, 已开奖
    memberName: undefined, //会员名
    prizeLevel: undefined, //奖励等级
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
      let queryResult = await lotteryRecordApi.queryPage(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
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

  onMounted(queryData);
</script>
