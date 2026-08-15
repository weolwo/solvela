<!--
  * 期号配置
  *
  * @Author:    weolwo
  * @Date:      2026-05-09 16:54:51
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
      <a-form-item label="期号" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.issueNo" placeholder="期号" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="ISSUE_STATUS_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="创建时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCreateTime" />
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
        <!-- 批量停售而不是批量删除：期号删了会让 t_lottery_record 里的记录变成孤儿，
             而那些记录既是用户凭证也是开奖核销的依据。停售只关发号口子，不动任何已有数据 -->
        <a-button @click="confirmBatchStopSale" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <StopOutlined />
          </template>
          批量停售
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.LOTTERY_ISSUE" :refresh="queryData" />
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
          <a-tag :color="issueStatusOf(text).color">{{ issueStatusOf(text).desc }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <!-- 已开奖/核销中的期不能改（服务端也拦），编辑只对待开奖开放 -->
            <a-button @click="showForm(record)" type="link" :disabled="record.status !== ISSUE_STATUS_ENUM.WAIT.value">编辑</a-button>
            <!--
              停售不按前端时钟判「现在是否在售」：售卖窗口的判据是数据库时钟（铁律 9/10），
              浏览器再算一遍就是第二个时钟源。这里只按状态放开按钮，
              「本来就已停售」由服务端返回原话
            -->
            <a-popconfirm
              v-if="record.status === ISSUE_STATUS_ENUM.WAIT.value"
              title="停售会把售卖结束时间提前到此刻，立即停止发号。已发出的号码不受影响，本期照常可以开奖。"
              ok-text="确认停售"
              cancel-text="再想想"
              @confirm="requestStopSale(record)"
            >
              <a-button danger type="link">停售</a-button>
            </a-popconfirm>
            <span v-else class="text-xs text-slate-300">已开奖</span>
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

    <LotteryIssueForm ref="formRef" @reloadList="queryData" />
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { StopOutlined } from '@ant-design/icons-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { lotteryIssueApi } from '/@/api/business/lottery/lottery-issue/lottery-issue-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import LotteryIssueForm from './lottery-issue-form.vue';
  import { ISSUE_STATUS_ENUM, ISSUE_STATUS_OPTIONS, issueStatusOf } from '/@/constants/business/lottery/lottery-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';

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
      title: '期号',
      dataIndex: 'issueNo',
      ellipsis: true,
    },
    {
      title: '已售/已派发数量',
      dataIndex: 'soldCount',
      ellipsis: true,
    },
    {
      title: '售卖开始时间',
      dataIndex: 'saleStartTime',
      ellipsis: true,
    },
    {
      title: '售卖结束时间',
      dataIndex: 'saleEndTime',
      ellipsis: true,
    },
    {
      title: '开奖时间',
      dataIndex: 'settleTime',
      ellipsis: true,
    },
    {
      title: '开奖号码',
      dataIndex: 'winningNumber',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
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
      width: 140,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    tenantId: undefined, //租户id
    lotteryCode: undefined, //彩票编码
    issueNo: undefined, //期号
    status: undefined, //状态: 0-待开奖, 1-售卖中, 2-已开奖
    createTime: [], //创建时间
    createTimeBegin: undefined, //创建时间 开始
    createTimeEnd: undefined, //创建时间 结束
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
      let queryResult = await lotteryIssueApi.queryPage(queryForm);
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

  // ---------------------------- 添加/修改 ----------------------------
  const formRef = ref();

  function showForm(data) {
    formRef.value.show(data);
  }

  // ---------------------------- 停售 ----------------------------

  /*
   * 「禁用」在期号上的落点是售卖窗口，不是状态位。
   *
   * t_lottery_issue.status 是生命周期（0-待开奖 / 1-核销中 / 2-已开奖），不是开关；
   * 而运行态 TicketIssueService 判「现在还能不能领号」只认三条：
   * 玩法已上线、期号是待开奖、当前时间落在售卖窗口内。
   * 再加一个禁用标志位就是第四个判据，与售卖窗口重叠 —— 两个判据一定会漂移。
   *
   * 所以停售 = 把售卖结束时间提前到此刻。可逆：想恢复就在编辑里把结束时间改回未来。
   */
  async function requestStopSale(data) {
    SmartLoading.show();
    try {
      await lotteryIssueApi.stopSale(data.id);
      message.success('已停售，本期停止发号');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 批量停售 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function confirmBatchStopSale() {
    Modal.confirm({
      title: '提示',
      content: `确定停售选中的 ${selectedRowKeyList.value.length} 期吗？停售后立即停止发号，已发出的号码不受影响，本期照常可以开奖。`,
      okText: '确认停售',
      okType: 'danger',
      onOk() {
        requestBatchStopSale();
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function requestBatchStopSale() {
    try {
      SmartLoading.show();
      const res = await lotteryIssueApi.batchStopSale(selectedRowKeyList.value);
      // 服务端回的是「已停售 N 期，跳过 M 期」的汇总，原样透出来 ——
      // 批量里混着已停售/已开奖的期是常态，笼统提示一句「操作成功」会把跳过的那几期盖掉
      message.success(res.data || '操作成功');
      selectedRowKeyList.value = [];
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>
