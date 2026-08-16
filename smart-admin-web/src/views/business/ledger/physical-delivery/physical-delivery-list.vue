<!--
  * 发货物流表
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 00:03:01
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
      <a-form-item label="发奖提案ID" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.proposalId" placeholder="发奖提案ID" />
      </a-form-item>
      <a-form-item label="来源类型" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.sourceType" placeholder="来源类型" />
      </a-form-item>
      <a-form-item label="物流单号" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.logisticsNo" placeholder="物流单号" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="DELIVERY_STATUS_OPTIONS" placeholder="全部" allowClear />
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
        <a-button @click="confirmBatchDiscard()" type="primary" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <DeleteOutlined />
          </template>
          批量废弃
        </a-button>
        <a-button @click="showImportModal" type="primary" size="small">
          <template #icon>
            <ImportOutlined />
          </template>
          导入
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.PHYSICAL_DELIVERY" :refresh="queryData" />
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
          <a-tag :color="deliveryStatusOf(text).color">{{ deliveryStatusOf(text).desc }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="showForm(record)" type="link">编辑</a-button>
            <a-button v-if="record.status === DELIVERY_STATUS_ENUM.PENDING.value" @click="onDiscard(record)" danger type="link">废弃</a-button>
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

    <PhysicalDeliveryForm ref="formRef" @reloadList="queryData" />

    <!---------- 导入弹窗 begin ----------->
    <a-modal v-model:open="importModalOpen" title="Excel 导入" :footer="null" @cancel="hideImportModal">
      <!--
        两种模式语义完全不同，必须让人先选：
        「回填物流」按 提案ID+来源类型 更新已有履约单，「新增履约单」是凭空建单。
        选错模式导入，轻则报一堆匹配不上，重则建出一批没有提案的孤儿单。
      -->
      <a-radio-group v-model:value="importMode" button-style="solid" style="margin-bottom: 16px">
        <a-radio-button value="ship">回填物流</a-radio-button>
        <a-radio-button value="add">新增履约单</a-radio-button>
      </a-radio-group>

      <a-alert :message="IMPORT_MODE_TIP[importMode]" type="info" show-icon style="margin-bottom: 16px" />

      <div style="text-align: center">
        <a-button @click="downloadTemplate">
          <template #icon>
            <DownloadOutlined />
          </template>
          第一步：下载模板
        </a-button>
        <br />
        <br />
        <a-upload :file-list="fileList" name="file" :multiple="false" accept=".xls,.xlsx" :before-upload="beforeUpload" @remove="onRemoveFile">
          <a-button>
            <template #icon>
              <UploadOutlined />
            </template>
            第二步：选择文件
          </a-button>
        </a-upload>
        <br />
        <a-button type="primary" :disabled="fileList.length === 0" @click="onImport">
          <template #icon>
            <ImportOutlined />
          </template>
          第三步：开始导入
        </a-button>
      </div>
    </a-modal>
    <!---------- 导入弹窗 end ----------->
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { physicalDeliveryApi } from '/@/api/business/ledger/physical-delivery/physical-delivery-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import PhysicalDeliveryForm from './physical-delivery-form.vue';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    DELIVERY_STATUS_ENUM,
    DELIVERY_STATUS_OPTIONS,
    deliveryStatusOf
  } from '/@/constants/business/ledger/physical-delivery/physical-delivery-const';

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
      title: '发奖提案ID',
      dataIndex: 'proposalId',
      ellipsis: true,
    },
    {
      title: '来源类型',
      dataIndex: 'sourceType',
      ellipsis: true,
    },
    {
      title: '收件人姓名',
      dataIndex: 'receiverName',
      ellipsis: true,
    },
    {
      title: '收件人电话',
      dataIndex: 'receiverPhone',
      ellipsis: true,
    },
    {
      title: '收件详细地址',
      dataIndex: 'receiverAddress',
      ellipsis: true,
    },
    {
      title: '物流公司',
      dataIndex: 'logisticsCompany',
      ellipsis: true,
    },
    {
      title: '物流单号',
      dataIndex: 'logisticsNo',
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
      width: 90,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    memberName: undefined, //会员名
    proposalId: undefined, //发奖提案ID
    sourceType: undefined, //来源类型
    logisticsNo: undefined, //物流单号
    status: undefined, //状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回
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
      let queryResult = await physicalDeliveryApi.queryPage(queryForm);
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

  // ---------------------------- 确认废弃 ----------------------------
  //确认废弃
  function onDiscard(data) {
    Modal.confirm({
      title: '提示',
      content: '确定要确认废弃选吗?',
      okText: '废弃',
      okType: 'danger',
      onOk() {
        requestDiscard(data);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  //请求确认废弃
  async function requestDiscard(data) {
    SmartLoading.show();
    try {
      await physicalDeliveryApi.discard(data.id);
      message.success('操作成功');
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

  // 批量废弃
  function confirmBatchDiscard() {
    Modal.confirm({
      title: '提示',
      content: '确定要批量废弃这些数据吗?',
      okText: '废弃',
      okType: 'danger',
      onOk() {
        requestBatchDiscard();
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  //请求批量废弃
  async function requestBatchDiscard() {
    try {
      SmartLoading.show();
      await physicalDeliveryApi.batchDiscard(selectedRowKeyList.value);
      message.success('操作成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- Excel 导入 ----------------------------

  const IMPORT_MODE_TIP = {
    ship: '按「发奖提案ID + 来源类型」匹配已有履约单，回填物流公司、物流单号与状态。匹配不到的行会报错退回，不会新建单据。',
    add: '整行新建履约单，等价于批量点「新建」。已存在的单据请改用「回填物流」，否则会撞唯一键报错。',
  };

  const importModalOpen = ref(false);
  const importMode = ref('ship');
  const fileList = ref([]);

  function showImportModal() {
    fileList.value = [];
    importMode.value = 'ship';
    importModalOpen.value = true;
  }

  function hideImportModal() {
    importModalOpen.value = false;
  }

  // 返回 false 拦下 antd 的自动上传，文件留在本地，等点「开始导入」再自己发
  function beforeUpload(file) {
    fileList.value = [file];
    return false;
  }

  function onRemoveFile() {
    fileList.value = [];
  }

  async function downloadTemplate() {
    if (importMode.value === 'ship') {
      await physicalDeliveryApi.downloadShipTemplate();
    } else {
      await physicalDeliveryApi.downloadAddTemplate();
    }
  }

  async function onImport() {
    const formData = new FormData();
    formData.append('file', fileList.value[0]);

    SmartLoading.show();
    try {
      let res = importMode.value === 'ship' ? await physicalDeliveryApi.importShip(formData) : await physicalDeliveryApi.importAdd(formData);
      message.success(res.msg);
      hideImportModal();
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>
