<!--
  * 任务记录与任务漏斗
  *
  * 顶部漏斗回答的是「翻十页记录也答不出来」的问题：达标率多少、哪个任务没人做得完、
  * 用户明明做了事进度却为什么不涨。
  *
  * 其中两段是本页独有的：
  * ① 「已过有效期仍在进行中」—— 工程里没有过期扫描任务，这些记录永远不会自己收口，
  *    用户端会一直看到一个做不完的任务，而没人查就发现不了；
  * ② 「事件丢弃分类」—— 被丢弃的事件压根没建记录，在下面的列表里怎么翻都看不到，
  *    答案只在流水表的 discard_code 里。
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 21:02:56
  * @Copyright  weolwo
-->
<template>
  <!---------- 任务漏斗 begin ----------->
  <div class="funnel-toggle">
    <a-button type="link" size="small" class="funnel-toggle-btn" @click="funnelOpen = !funnelOpen">
      <template #icon>
        <DownOutlined v-if="funnelOpen" />
        <RightOutlined v-else />
      </template>
      任务漏斗
    </a-button>
    <span v-if="!funnelOpen" class="funnel-summary">
      共 {{ num(funnel.totalCount) }} 条 · 达标 {{ percent(funnel.reachRate) }} ·
      <span :class="funnel.staleRunningCount > 0 ? 'summary-alert' : ''"> 过期未收口 {{ num(funnel.staleRunningCount) }} </span>
    </span>
  </div>

  <div v-show="funnelOpen">
    <a-row :gutter="12" class="overview-row">
      <a-col :span="6">
        <div class="overview-card tone-primary">
          <div class="overview-card-label">接取总数</div>
          <div class="overview-card-value">{{ num(funnel.totalCount) }}</div>
          <div class="overview-card-foot">{{ num(funnel.memberCount) }} 人参与 · 人均 {{ funnel.recordPerMember ?? '-' }} 个</div>
        </div>
      </a-col>
      <a-col :span="6">
        <div class="overview-card tone-success">
          <div class="overview-card-label">
            达标率
            <a-tooltip title="分母是接取总数。任务不像开奖有明确的揭晓时刻，「进行中」本身就是一种结果（做不完也是做不完），把它剔出分母会让达标率虚高">
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">{{ percent(funnel.reachRate) }}</div>
          <div class="overview-card-foot">已发奖 {{ num(funnel.dispatchedCount) }} · 进行中 {{ num(funnel.runningCount) }}</div>
        </div>
      </a-col>
      <a-col :span="6">
        <!-- 没有过期扫描任务，这些记录不会自己收口，用户端会一直看到一个做不完的任务 -->
        <div class="overview-card" :class="funnel.staleRunningCount > 0 ? 'tone-critical' : 'tone-muted'">
          <div class="overview-card-label">
            过期未收口
            <a-tooltip title="已过有效期却仍是「进行中」。工程里没有过期扫描任务，它们不会自己变成已过期，用户端会一直看到一个永远完不成的任务。可勾选后用「批量禁用」置为已过期">
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">{{ num(funnel.staleRunningCount) }}</div>
          <div class="overview-card-foot">已过期 {{ num(funnel.expiredCount) }} · 完成未发奖 {{ num(funnel.completedCount) }}</div>
        </div>
      </a-col>
      <a-col :span="6">
        <!-- 需要人介入的丢弃：正常业务拦截量再大也不用管，这三类几条都该查 -->
        <div class="overview-card" :class="funnel.discardAttentionCount > 0 ? 'tone-critical' : 'tone-muted'">
          <div class="overview-card-label">
            事件丢弃
            <a-tooltip title="用户做了事进度却不涨，原因都在这里。被丢弃的事件压根没建记录，下面的列表里看不到它们">
              <QuestionCircleOutlined class="overview-card-hint" />
            </a-tooltip>
          </div>
          <div class="overview-card-value">
            {{ num(funnel.discardAttentionCount) }}
            <span class="overview-card-slash">需排查</span>
          </div>
          <div class="overview-card-foot">丢弃合计 {{ num(funnel.discardTotalCount) }}（含正常规则拦截）</div>
        </div>
      </a-col>
    </a-row>

    <!-- 漏斗刻意不吃这两个筛选项，筛了却不生效比不筛更让人困惑，故明说 -->
    <div v-if="queryForm.status !== undefined || queryForm.completeTimeBegin" class="funnel-note">
      漏斗不受「状态」「达标时间」筛选影响：它们正是漏斗要拆解的维度，跟着筛会让达标率恒为 100%
    </div>

    <a-alert v-for="(issue, idx) in funnel.issueList || []" :key="idx" type="error" show-icon class="mb-2">
      <template #message>
        <span class="text-xs">{{ issue }}</span>
      </template>
    </a-alert>

    <a-row :gutter="12">
      <a-col :span="14">
        <a-card v-if="funnel.taskList && funnel.taskList.length > 0" size="small" :bordered="false" class="stat-card">
          <div class="stat-head">任务达标分布（接取量 TOP 20）</div>
          <div v-for="item in funnel.taskList" :key="item.taskConfigId" class="stat-row">
            <div class="stat-name">
              {{ item.taskName || `（配置已删除 #${item.taskConfigId}）` }}
              <span v-if="item.staleRunningCount > 0" class="stat-stale">过期未收口 {{ num(item.staleRunningCount) }}</span>
            </div>
            <div class="stat-bar-wrap">
              <div class="stat-bar" :style="{ width: barPercent(item.reachRate) + '%' }"></div>
            </div>
            <div class="stat-num">{{ num(item.reachedCount) }}/{{ num(item.recordCount) }} · {{ percent(item.reachRate) }}</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="10">
        <a-card v-if="funnel.discardList && funnel.discardList.length > 0" size="small" :bordered="false" class="stat-card">
          <div class="stat-head">
            事件丢弃原因（共 {{ num(funnel.discardTotalCount) }} 条）
            <span class="stat-head-sub">红色的三类不是正常业务拦截，要去找上游或修配置</span>
          </div>
          <div v-for="item in funnel.discardList" :key="item.discardCode || 'UNKNOWN'" class="stat-row">
            <div class="stat-name" :class="item.needsAttention ? 'stat-name-alert' : ''">
              {{ item.needsAttention ? '🔴' : '' }} {{ item.discardDesc }}
            </div>
            <div class="stat-bar-wrap">
              <div class="stat-bar" :class="item.needsAttention ? 'stat-bar-alert' : ''" :style="{ width: barPercent(item.discardShare) + '%' }"></div>
            </div>
            <div class="stat-num">{{ num(item.discardCount) }} · {{ percent(item.discardShare) }}</div>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
  <!---------- 任务漏斗 end ----------->

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

    <TaskRecordForm ref="formRef" @reloadList="reload" />
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
  import { reactive, ref, watch, onMounted } from 'vue';
  import { DownOutlined, QuestionCircleOutlined, RightOutlined } from '@ant-design/icons-vue';
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

  // 漏斗数据
  const funnel = ref({});

  // 重置查询条件
  function resetQuery() {
    let pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    reload();
  }

  // 搜索
  function onSearch() {
    queryForm.pageNum = 1;
    reload();
  }

  // 列表与漏斗一起刷：换了筛选条件只刷一个会让两者对不上
  async function reload() {
    await Promise.all([queryData(), loadFunnel()]);
  }

  async function loadFunnel() {
    try {
      // status / completeTime 服务端刻意忽略：它们是漏斗要拆解的维度本身
      const res = await taskRecordApi.funnel(queryForm);
      funnel.value = res.data || {};
    } catch (e) {
      smartSentry.captureError(e);
    }
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

  onMounted(reload);

  // ---------------------------- 漏斗展示辅助 ----------------------------

  function num(value) {
    return value == null ? '-' : Number(value).toLocaleString();
  }

  function percent(rate) {
    if (rate == null) {
      return '-';
    }
    const v = Number(rate) * 100;
    if (v === 0) {
      return '0%';
    }
    return v >= 1 ? `${v.toFixed(2)}%` : `${v.toPrecision(2)}%`;
  }

  // 占比再小也给 1% 的宽度，否则条形图上只剩一条看不见的缝，读者会以为渲染坏了
  function barPercent(rate) {
    return Math.max(1, Math.round(Number(rate || 0) * 100));
  }

  // ---------------------------- 漏斗折叠 ----------------------------

  /*
   * 默认展开；排查具体某条记录时可折叠，折叠后把关键结论压成一行摘要留在视线里。
   * 状态记在 localStorage，读写都用 try/catch 包住 ——
   * 隐私模式下 localStorage 会抛异常，不能让折叠状态把页面初始化带崩。
   */
  const FUNNEL_OPEN_KEY = 'task-record:funnel-open';
  const funnelOpen = ref(readFunnelOpen());

  function readFunnelOpen() {
    try {
      return localStorage.getItem(FUNNEL_OPEN_KEY) !== '0';
    } catch (e) {
      return true;
    }
  }

  watch(funnelOpen, (open) => {
    try {
      localStorage.setItem(FUNNEL_OPEN_KEY, open ? '1' : '0');
    } catch (e) {
      // 存不了就算了，折叠状态不值得打断用户
    }
  });

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
      // 禁用会把「过期未收口」的记录搬进「已过期」，漏斗必须跟着刷新，否则数字还停在改动之前
      await reload();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>

<style lang="less" scoped>
  .funnel-toggle {
    display: flex;
    gap: 10px;
    align-items: center;
    margin-bottom: 6px;
  }

  .funnel-toggle-btn {
    padding: 0;
    font-size: 13px;
    font-weight: 600;
  }

  .funnel-summary {
    font-size: 12px;
    color: #64748b;
  }

  .summary-alert {
    font-weight: 600;
    color: #b91c1c;
  }

  .funnel-note {
    margin-bottom: 8px;
    font-size: 11px;
    color: #94a3b8;
  }

  .overview-row {
    margin-bottom: 12px;
  }

  .overview-card {
    padding: 12px 16px;
    background: #fff;
    border: 1px solid #e2e8f0;
    border-left: 4px solid #cbd5e1;
    border-radius: 6px;
  }

  .tone-primary {
    border-left-color: #3b82f6;
  }

  .tone-success {
    border-left-color: #10b981;

    .overview-card-value {
      color: #059669;
    }
  }

  .tone-critical {
    background: #fef2f2;
    border-left-color: #b91c1c;

    .overview-card-value {
      color: #b91c1c;
    }
  }

  .tone-muted {
    border-left-color: #cbd5e1;
  }

  .overview-card-label {
    font-size: 12px;
    color: #64748b;
  }

  .overview-card-hint {
    margin-left: 4px;
    color: #cbd5e1;
  }

  .overview-card-value {
    margin-top: 2px;
    font-size: 26px;
    font-weight: 700;
    line-height: 1.2;
    color: #0f172a;
  }

  .overview-card-slash {
    margin-left: 4px;
    font-size: 14px;
    font-weight: 500;
    color: #94a3b8;
  }

  .overview-card-foot {
    margin-top: 2px;
    font-size: 11px;
    color: #cbd5e1;
  }

  .stat-card {
    margin-bottom: 12px;
  }

  .stat-head {
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
  }

  .stat-head-sub {
    margin-left: 6px;
    font-size: 11px;
    font-weight: 400;
    color: #94a3b8;
  }

  .stat-row {
    display: flex;
    gap: 12px;
    align-items: center;
    padding: 3px 0;
  }

  .stat-name {
    flex: 0 0 200px;
    overflow: hidden;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .stat-name-alert {
    font-weight: 600;
    color: #b91c1c;
  }

  .stat-stale {
    margin-left: 6px;
    font-size: 11px;
    color: #b91c1c;
  }

  .stat-bar-wrap {
    flex: 1;
    height: 14px;
    overflow: hidden;
    background: #f1f5f9;
    border-radius: 3px;
  }

  .stat-bar {
    height: 100%;
    background: linear-gradient(90deg, #10b981, #6ee7b7);
  }

  .stat-bar-alert {
    background: linear-gradient(90deg, #ef4444, #f87171);
  }

  .stat-num {
    flex: 0 0 150px;
    font-size: 12px;
    color: #64748b;
    text-align: right;
  }
</style>
