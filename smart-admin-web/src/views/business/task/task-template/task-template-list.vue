<!--
  * 任务模板表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 21:12:49
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="模板编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.templateCode" placeholder="模板编码" />
      </a-form-item>
      <a-form-item label="模板名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.templateName" placeholder="模板名称" />
      </a-form-item>
      <a-form-item label="流转类型" class="smart-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.taskType" :options="TASK_TYPE_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="TEMPLATE_STATUS_OPTIONS" placeholder="全部" allowClear />
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
        <!-- 模板由 ui_schema + QLExpress 脚本构成，扁平表单编不了，统一走设计器 -->
        <a-button @click="goDesigner" type="primary" size="small">
          <template #icon>
            <PlusOutlined />
          </template>
          任务模板设计
        </a-button>
        <!-- 只提供批量禁用：模板被 t_task_config.template_code 引用，运行态还要按 code 取脚本，
             删掉不会立刻报错，而是让引用它的任务安静地不再推进 -->
        <a-button @click="confirmBatchDisable" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <StopOutlined />
          </template>
          批量禁用
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.TASK_TEMPLATE" :refresh="queryData" />
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
        <template v-if="column.dataIndex === 'taskType'">
          <a-tag>{{ taskTypeOf(text) }}</a-tag>
        </template>
        <!-- 状态：开关就地切换，与奖品配置同一套交互 -->
        <template v-if="column.dataIndex === 'status'">
          <a-switch
            :checked="text === TEMPLATE_STATUS_ENUM.ENABLED.value"
            :loading="statusLoadingId === record.id"
            checked-children="启用"
            un-checked-children="禁用"
            @change="(checked) => onToggleStatus(record, checked)"
          />
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="goDesignerEdit(record)" type="link">编辑</a-button>
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

  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { taskTemplateApi } from '/src/api/business/task/task-template-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { useRouter } from 'vue-router';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    TASK_TYPE_OPTIONS,
    TEMPLATE_STATUS_ENUM,
    TEMPLATE_STATUS_OPTIONS,
    taskTypeOf,
  } from '/src/constants/business/task/task-template-const';

  // 与菜单里「任务模板设计」的路由地址保持一致
  const TASK_TEMPLATE_DESIGNER_PATH = '/business/task/task-template-designer';

  const router = useRouter();

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: 'id',
      dataIndex: 'id',
      ellipsis: true,
    },
    {
      title: '模板编码',
      dataIndex: 'templateCode',
      ellipsis: true,
    },
    {
      title: '模板名称',
      dataIndex: 'templateName',
      ellipsis: true,
    },
    {
      title: '流转类型',
      dataIndex: 'taskType',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
    },
    // ui_schema 与 rule_script 都是大段代码，铺在表格里只会挤掉真正要看的字段，
    // 需要看内容请进设计器
    {
      title: '前端渲染规则',
      dataIndex: 'uiSchema',
      showFlag: false,
      ellipsis: true,
    },
    {
      title: 'QLExpress脚本',
      dataIndex: 'ruleScript',
      showFlag: false,
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
    templateCode: undefined, //模板编码
    templateName: undefined, //模板名称
    taskType: undefined, //流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型)
    status: undefined, //状态：0-禁用, 1-启用
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
      let queryResult = await taskTemplateApi.queryPage(queryForm);
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

  // ---------------------------- 新建 / 编辑：走模板设计器 ----------------------------

  /*
   * 模板由 ui_schema（动态表单定义）+ QLExpress 脚本构成，扁平弹窗改不了这两样。
   * 设计器左侧编代码、右侧实时预览，是唯一能把模板配完整的地方。
   */
  function goDesigner() {
    router.push(TASK_TEMPLATE_DESIGNER_PATH);
  }

  function goDesignerEdit(record) {
    router.push({ path: TASK_TEMPLATE_DESIGNER_PATH, query: { id: record.id } });
  }

  // ---------------------------- 启用 / 禁用 ----------------------------

  const statusLoadingId = ref(null);

  async function onToggleStatus(record, checked) {
    const target = checked ? TEMPLATE_STATUS_ENUM.ENABLED.value : TEMPLATE_STATUS_ENUM.DISABLED.value;
    statusLoadingId.value = record.id;
    try {
      await taskTemplateApi.updateStatus({ idList: [record.id], status: target });
      message.success(checked ? '已启用' : '已禁用');
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      statusLoadingId.value = null;
    }
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
      content: `确定要禁用选中的 ${selectedRowKeyList.value.length} 个模板吗？禁用后不再出现在任务配置向导的候选里，已用它建好的任务不受影响，可随时再启用。`,
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
      await taskTemplateApi.updateStatus({
        idList: selectedRowKeyList.value,
        status: TEMPLATE_STATUS_ENUM.DISABLED.value,
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
