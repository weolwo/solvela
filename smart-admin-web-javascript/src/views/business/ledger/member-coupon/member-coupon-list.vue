<!--
  * 会员优惠券
  *
  * 只读台账：券由发奖链路发放、由核销链路回写，管理端不提供增删改。
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:42:44
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
      <a-form-item label="券模编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.couponCode" placeholder="券模编码" />
      </a-form-item>
      <a-form-item label="有效期开始" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.validStartTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeValidStartTime" />
      </a-form-item>
      <a-form-item label="券名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.couponName" placeholder="券名称" />
      </a-form-item>
      <a-form-item label="券类型" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.couponType" placeholder="券类型" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="COUPON_STATUS_OPTIONS" placeholder="全部" allowClear />
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
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.MEMBER_COUPON" :refresh="queryData" />
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
    >
      <template #bodyCell="{ text, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="couponStatusOf(text).color">{{ couponStatusOf(text).desc }}</a-tag>
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
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { memberCouponApi } from '/@/api/business/ledger/member-coupon/member-coupon-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { COUPON_STATUS_OPTIONS, couponStatusOf } from '/@/constants/business/ledger/member-coupon/member-coupon-const';

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
      title: '券模编码',
      dataIndex: 'couponCode',
      ellipsis: true,
    },
    {
      title: '券类型',
      dataIndex: 'couponType',
      ellipsis: true,
    },
    {
      title: '券名称',
      dataIndex: 'couponName',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      ellipsis: true,
    },
    {
      title: '来源',
      dataIndex: 'sourceType',
      ellipsis: true,
    },
    {
      title: '关联单号',
      dataIndex: 'sourceBizId',
      ellipsis: true,
    },
    {
      title: '有效期开始',
      dataIndex: 'validStartTime',
      ellipsis: true,
    },
    {
      title: '有效期结束',
      dataIndex: 'validEndTime',
      ellipsis: true,
    },
    {
      title: '核销时间',
      dataIndex: 'usedTime',
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
    tenantId: undefined, //租户ID
    memberName: undefined, //会员名
    couponCode: undefined, //券模编码
    validStartTime: [], //有效期开始
    validStartTimeBegin: undefined, //有效期开始 开始
    validStartTimeEnd: undefined, //有效期开始 结束
    couponName: undefined, //券名称
    couponType: undefined, //券类型
    status: undefined, //状态：0-未使用, 1-已使用, 2-已过期, 3-已作废
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
      let queryResult = await memberCouponApi.queryPage(queryForm);
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

  onMounted(queryData);
</script>
