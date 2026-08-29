<!--
  * 任务配置表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 20:55:10
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="任务名称" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.taskName" placeholder="任务名称" />
      </a-form-item>
      <a-form-item label="模板Code" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.templateCode" placeholder="模板Code" />
      </a-form-item>
      <a-form-item label="活动编码" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.activityCode" placeholder="活动编码" />
      </a-form-item>
      <a-form-item label="开始时间" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.startTime" placeholder="开始时间" />
      </a-form-item>
      <!-- 触发事件是开放集合，选项来自服务端注册表 t_task_event，不写死在前端 -->
      <a-form-item label="触发事件" class="solvela-query-form-item">
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
      <a-form-item label="任务状态" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="CONFIG_STATUS_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item class="solvela-query-form-item">
        <a-button type="primary" @click="onSearch">
          <template #icon>
            <SearchOutlined />
          </template>
          查询
        </a-button>
        <a-button @click="resetQuery" class="solvela-margin-left10">
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
    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block">
        <!--
          新建走向导而不是这里的扁平表单：任务配置是主子表（t_task_config + t_task_prize_mapping），
          规则参数还要按模板 ui_schema 动态渲染。扁平表单填不出奖励阶梯，建出来的任务没有奖可发。
        -->
        <a-button @click="goWizard" type="primary" size="small">
          <template #icon>
            <PlusOutlined />
          </template>
          任务配置向导
        </a-button>
        <!-- 只提供批量下线：任务记录里存着 task_config_id，删配置会让历史记录指向一条不存在的配置，
             复盘时查不到这条任务当初是怎么配的；下线后运行态不再订阅事件，已在跑的记录按快照走完 -->
        <a-button @click="confirmBatchOffline" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <StopOutlined />
          </template>
          批量下线
        </a-button>
      </div>
      <div class="solvela-table-setting-block">
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
          <div class="solvela-table-operate">
            <a-button @click="showDetail(record)" type="link">详情</a-button>
            <a-button @click="goWizardEdit(record)" type="link">编辑</a-button>
          </div>
        </template>
      </template>
    </a-table>
    <!---------- 表格 end ----------->

    <div class="solvela-query-table-page">
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

    <!---------- 详情抽屉：只读，主表 + 奖励阶梯子表 ----------->
    <a-drawer :title="`任务详情 · ${detail.taskName || ''}`" :width="760" :open="detailVisible" @close="detailVisible = false">
      <a-descriptions title="基础信息" bordered size="small" :column="2" class="mb-6">
        <a-descriptions-item label="任务ID">{{ detail.id }}</a-descriptions-item>
        <a-descriptions-item label="任务状态">
          <a-tag :color="configStatusOf(detail.status).color">{{ configStatusOf(detail.status).desc }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="任务名称" :span="2">{{ detail.taskName }}</a-descriptions-item>
        <a-descriptions-item label="归属活动">{{ detail.activityCode }}</a-descriptions-item>
        <a-descriptions-item label="模板Code">{{ detail.templateCode }}</a-descriptions-item>
        <a-descriptions-item label="触发事件">{{ detail.triggerEvent }}</a-descriptions-item>
        <a-descriptions-item label="任务分组">{{ taskGroupOf(detail.taskGroup) }}</a-descriptions-item>
        <a-descriptions-item label="目标人群">{{ targetAudienceOf(detail.targetAudience) }}</a-descriptions-item>
        <a-descriptions-item label="参与频次">{{ limitTypeOf(detail.limitType) }}（{{ detail.limitCount }} 次）</a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ detail.startTime || '长期有效' }}</a-descriptions-item>
        <a-descriptions-item label="结束时间">{{ detail.endTime || '长期有效' }}</a-descriptions-item>
        <a-descriptions-item label="排序权重">{{ detail.sortWeight }}</a-descriptions-item>
        <a-descriptions-item label="跳转地址">{{ detail.actionUrl || '-' }}</a-descriptions-item>
      </a-descriptions>

      <div class="mb-2 flex items-center gap-2">
        <span class="text-base font-medium">奖励阶梯</span>
        <span class="text-xs text-slate-400">t_task_prize_mapping</span>
      </div>
      <!-- 阶梯是任务能不能发出奖的关键，没有子表的任务等于配了个发不出奖的空壳 -->
      <a-table
        size="small"
        bordered
        :data-source="detailLadders"
        :columns="LADDER_COLUMNS"
        :loading="detailLadderLoading"
        :pagination="false"
        row-key="id"
        class="mb-6"
      >
        <template #emptyText>
          <div class="py-4 text-xs text-orange-500">该任务没有配置奖励阶梯，达标后不会发出任何奖励</div>
        </template>
        <template #bodyCell="{ text, column }">
          <template v-if="column.dataIndex === 'prizeMode'">
            <a-tag>{{ prizeModeOf(text) }}</a-tag>
          </template>
        </template>
      </a-table>

      <a-descriptions title="规则与展示（JSON）" bordered size="small" :column="1">
        <a-descriptions-item label="规则配置 rule_config">
          <pre class="m-0 max-h-40 overflow-auto text-xs">{{ prettyJson(detail.ruleConfig) }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="展示配置 ui_config">
          <pre class="m-0 max-h-40 overflow-auto text-xs">{{ prettyJson(detail.uiConfig) }}</pre>
        </a-descriptions-item>
      </a-descriptions>
    </a-drawer>
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { message, Modal } from 'ant-design-vue';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { taskConfigApi } from '/src/api/business/task/task-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { taskApi } from '/@/api/business/task/task-api';
  import { taskPrizeMappingApi } from '/src/api/business/prize/task-prize-mapping-api';
  import { toEventOptions } from '../task-wizard/task-wizard-const';
  import { prizeModeOf } from '/src/constants/business/prize/task-prize-mapping-const';
  import {
    CONFIG_STATUS_ENUM,
    CONFIG_STATUS_OPTIONS,
    configStatusOf,
    limitTypeOf,
    targetAudienceOf,
    taskGroupOf,
  } from '/src/constants/business/task/task-config-const';

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
  const router = useRouter();

  // 与菜单里「任务配置向导」的路由地址保持一致
  const TASK_WIZARD_PATH = '/business/task/task-wizard';
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
      tableData.value = queryResult.list;
      total.value = queryResult.total;
    } catch (e) {
      solvelaSentry.captureError(e);
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
      eventOptions.value = toEventOptions(res);
    } catch (e) {
      solvelaSentry.captureError(e);
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

  // ---------------------------- 新建：跳向导 ----------------------------

  function goWizard() {
    router.push(TASK_WIZARD_PATH);
  }

  /*
   * 编辑同样走向导：任务是主子表 + 模板驱动的动态表单，
   * 原来那个扁平弹窗改不了规则参数、也改不了奖励阶梯，改完等于只动了几个无关紧要的字段。
   */
  function goWizardEdit(record) {
    router.push({ path: TASK_WIZARD_PATH, query: { id: record.id } });
  }

  // ---------------------------- 详情 ----------------------------

  // 达标值/奖励值取服务端从 stage_condition.target、prize_strategy.value 拆好的字段，
  // 不再直接铺原始 JSON —— 与「任务奖励总览」页看到的是同一份口径
  const LADDER_COLUMNS = [
    { title: '阶梯', dataIndex: 'stageLevel', width: 70 },
    { title: '达标值', dataIndex: 'stageTarget', width: 100 },
    { title: '奖励编码', dataIndex: 'prizeCode', width: 140, ellipsis: true },
    { title: '奖品名称', dataIndex: 'prizeName', width: 140, ellipsis: true },
    { title: '计算类型', dataIndex: 'prizeMode', width: 100 },
    { title: '奖励值', dataIndex: 'prizeValue', width: 100 },
  ];

  const detailVisible = ref(false);
  const detail = ref({});
  const detailLadders = ref([]);
  const detailLadderLoading = ref(false);

  // rule_config / ui_config 在库里是 json，接口下发的是字符串，直接铺出来是一坨
  function prettyJson(raw) {
    if (!raw) {
      return '-';
    }
    try {
      return JSON.stringify(typeof raw === 'string' ? JSON.parse(raw) : raw, null, 2);
    } catch (e) {
      // 不是合法 JSON 就原样显示，不要因为格式化失败把内容藏掉
      return raw;
    }
  }

  async function showDetail(record) {
    detail.value = { ...record };
    detailLadders.value = [];
    detailVisible.value = true;
    detailLadderLoading.value = true;
    try {
      // 子表没有「按任务查」的专用接口，借分页接口按 taskConfigId 过滤；单个任务的阶梯撑死几条
      const res = await taskPrizeMappingApi.queryPage({ taskConfigId: record.id, pageNum: 1, pageSize: 50 });
      detailLadders.value = res?.list || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      detailLadderLoading.value = false;
    }
  }

  // ---------------------------- 批量下线 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function confirmBatchOffline() {
    Modal.confirm({
      title: '批量下线',
      content: `确定要下线选中的 ${selectedRowKeyList.value.length} 个任务吗？下线后不再接收上游事件，已在跑的任务记录按接取时的快照走完，可随时改回。`,
      okText: '下线',
      okType: 'danger',
      onOk() {
        requestBatchOffline();
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function requestBatchOffline() {
    try {
      SolvelaLoading.show();
      await taskConfigApi.updateStatus({
        idList: selectedRowKeyList.value,
        status: CONFIG_STATUS_ENUM.OFFLINE.value,
      });
      message.success('已批量下线');
      selectedRowKeyList.value = [];
      await queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }
</script>
