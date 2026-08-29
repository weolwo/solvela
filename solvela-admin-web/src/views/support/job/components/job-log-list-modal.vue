<!--
  * job log列表
  * @Author:    huke
  * @Date:      2024/06/25
-->
<template>
  <a-drawer v-model:open="showFlag" :width="1100" :title="title" placement="right" :destroyOnClose="true">
    <a-form class="solvela-query-form">
      <a-row class="solvela-query-form-row">
        <a-form-item label="关键字" class="solvela-query-form-item">
          <a-input style="width: 200px" v-model:value="queryForm.searchWord" placeholder="请输入关键字" :maxlength="30" />
        </a-form-item>
        <a-form-item label="执行状态" class="solvela-query-form-item">
          <a-select style="width: 120px" v-model:value="queryForm.status" placeholder="请选择" allowClear>
            <a-select-option v-for="item in statusOptions" :key="item.value" :value="item.value">
              {{ item.desc }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="执行时间" class="solvela-query-form-item">
          <a-space direction="vertical" :size="12">
            <a-range-picker v-model:value="searchDate" style="width: 220px" :presets="defaultTimeRanges" @change="dateChange" />
          </a-space>
        </a-form-item>

        <a-form-item class="solvela-query-form-item solvela-margin-left10">
          <a-button-group>
            <a-button type="primary" @click="onSearch">
              <template #icon>
                <SearchOutlined />
              </template>
              查询
            </a-button>
            <a-button @click="resetQuery">
              <template #icon>
                <ReloadOutlined />
              </template>
              重置
            </a-button>
          </a-button-group>
        </a-form-item>
      </a-row>
    </a-form>

    <a-row justify="end">
      <TableOperator class="solvela-margin-bottom5" v-model="columns" :tableId="TABLE_ID_CONST.SUPPORT.JOB_LOG" :refresh="queryLogList" />
    </a-row>

    <a-table size="small" :loading="tableLoading" bordered :dataSource="tableData" :columns="columns" rowKey="jobLogId" :pagination="false">
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'executeStartTime'">
          <div>
            <a-tag color="green">始</a-tag>
            {{ record.executeStartTime }}
          </div>
          <div style="margin-top: 5px">
            <a-tag color="blue">终</a-tag>
            {{ record.executeEndTime }}
          </div>
        </template>
        <template v-if="column.dataIndex === 'executeTimeMillis'"> {{ record.executeTimeMillis }} ms</template>
        <!-- 🔴 多态状态，不再是「成功/失败」两分。
               「执行中」和「超时中断」以前都只能显示成「失败」，
               而这三者的处置方式完全不同：执行中要等，超时要调参数，失败才要查 bug -->
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="getStatusColor(record.status)">{{ getStatusDesc(record.status) }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'scheduleDelayMs'">
          <!-- 调度延迟持续增长 = 容量不足的扩容信号，不该等 MISFIRE 告警响了才发现 -->
          <span v-if="record.scheduleDelayMs != null" :style="{ color: record.scheduleDelayMs > 60000 ? '#f50' : '' }">
            {{ record.scheduleDelayMs }} ms
          </span>
        </template>
        <template v-if="column.dataIndex === 'operate'">
          <a-button type="link" size="small" @click="onViewLog(record)">日志</a-button>
          <a-popconfirm title="将沿用当初那一次的参数与业务日期重跑，确认？" @confirm="onRerun(record)">
            <a-button type="link" size="small">重跑</a-button>
          </a-popconfirm>
          <!-- 只有「执行中」才给终止入口；文案是「中断」不是「终止」——
                 能否真正停止取决于执行器是否响应中断，措辞不能许诺做不到的事 -->
          <a-popconfirm
            v-if="record.status === EXECUTE_STATUS_ENUM.RUNNING.value"
            title="将向执行节点发出中断信号。若该执行器未响应中断，任务可能仍会继续，确认？"
            @confirm="onTerminate(record)"
          >
            <a-button type="link" size="small" danger>中断</a-button>
          </a-popconfirm>
        </template>
        <template v-if="column.dataIndex === 'traceId'">
          <a-tooltip v-if="record.traceId">
            <template #title>用它去日志系统检索这次执行的完整日志</template>
            <a-typography-text copyable :content="record.traceId" style="font-size: 12px" />
          </a-tooltip>
        </template>
      </template>
    </a-table>

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
        @change="queryLogList"
        :show-total="(total) => `共${total}条`"
      />
    </div>
    <!-- 实时执行日志。取不到就如实说取不到 —— 伪装成「无日志输出」会让人以为任务真的什么都没打 -->
    <a-drawer v-model:open="logDrawerShow" :width="900" title="执行日志" placement="right">
      <a-space style="margin-bottom: 8px">
        <a-button size="small" :loading="logLoading" @click="loadExecuteLog(currentLogId)">刷新</a-button>
        <span style="color: #999; font-size: 12px"> 日志暂存 1 天、最多保留最新 2000 行；执行器内部自起线程打的日志不会被采集 </span>
      </a-space>
      <a-empty v-if="!logLoading && logLines.length === 0" description="没有采集到日志（可能已过期，或该次执行未开启采集）" />
      <pre v-else style="background: #1e1e1e; color: #d4d4d4; padding: 12px; font-size: 12px; max-height: 70vh; overflow: auto">{{ logText }}</pre>
    </a-drawer>
  </a-drawer>
</template>
<script setup>
  import { message } from 'ant-design-vue';
  import { computed, reactive, ref } from 'vue';
  import { jobApi } from '/@/api/support/job-api';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading/index';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { EXECUTE_STATUS_ENUM, getExecuteStatus } from '/@/constants/support/job-const';

  const showFlag = ref(false);
  const title = ref('');

  // ---------------- 执行状态 -----------------------

  // 抢占式调度上线后这些状态都会真实产生，全部放出来供筛选
  const statusOptions = Object.values(EXECUTE_STATUS_ENUM);

  function getStatusDesc(status) {
    return getExecuteStatus(status)?.desc ?? '未知';
  }

  function getStatusColor(status) {
    return getExecuteStatus(status)?.color ?? 'default';
  }

  /**
   * 一键重跑。
   *
   * 🔴 沿用当初那一次的 param_snapshot 与 bizDate，不是拿当前配置跑一遍 ——
   * 配置可能早已被人改过，而运营的意图是「把那次失败的补回来」。
   */
  async function onRerun(record) {
    try {
      SolvelaLoading.show();
      await jobApi.rerunJobLog(record.logId);
      message.success('已提交重跑，稍后刷新查看结果');
      queryLogList();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }

  /**
   * 中断正在执行的任务。
   *
   * 🔴 提示语说「已发出中断信号」而不是「已终止」——
   * cancel(true) 本质是 interrupt，不响应中断的执行器砍不掉。
   * 谎报成功会让运营以为处理完了，转身去做别的，而任务还在跑。
   */
  async function onTerminate(record) {
    try {
      SolvelaLoading.show();
      const res = await jobApi.terminateJobLog(record.logId);
      message.warning(res || '已发出中断信号，请稍后刷新确认');
      queryLogList();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }

  // ---------------- 实时执行日志 -----------------------

  const logDrawerShow = ref(false);
  const logLines = ref([]);
  const logLoading = ref(false);

  async function onViewLog(record) {
    logDrawerShow.value = true;
    await loadExecuteLog(record.logId);
  }

  async function loadExecuteLog(logId) {
    try {
      logLoading.value = true;
      const res = await jobApi.queryExecuteLog(logId);
      logLines.value = res || [];
      currentLogId.value = logId;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      logLoading.value = false;
    }
  }

  const currentLogId = ref(null);

  // 在 computed 里拼接，不在模板表达式里写转义字符 ——
  // Vue 模板先按 HTML 解析再编译表达式，反斜杠转义在这两层之间很容易被吃掉
  const logText = computed(() => logLines.value.join(String.fromCharCode(10)));

  function show(jobId, name) {
    queryForm.jobId = jobId;
    queryLogList();
    showFlag.value = true;
    title.value = name;
  }

  defineExpose({ show });

  const columns = ref([
    {
      title: '执行人',
      dataIndex: 'createName',
      width: 100,
      ellipsis: true,
    },
    {
      title: '执行参数',
      dataIndex: 'paramSnapshot',
      width: 80,
      ellipsis: true,
    },
    {
      title: '执行时间',
      dataIndex: 'executeStartTime',
      width: 200,
    },
    {
      title: '执行用时',
      dataIndex: 'executeTimeMillis',
      width: 100,
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
    },
    {
      title: '调度延迟',
      dataIndex: 'scheduleDelayMs',
      width: 90,
    },
    {
      title: 'traceId',
      dataIndex: 'traceId',
      width: 140,
      ellipsis: true,
    },
    {
      title: '操作',
      dataIndex: 'operate',
      width: 150,
      fixed: 'right',
    },
    {
      title: '执行结果',
      dataIndex: 'resultSummary',
      ellipsis: true,
      width: 100,
    },
    {
      title: 'ip',
      dataIndex: 'ip',
      width: 110,
    },
    {
      title: '进程id',
      dataIndex: 'processId',
      width: 60,
    },
    {
      title: '程序目录',
      dataIndex: 'programPath',
      ellipsis: true,
    },
  ]);

  // ---------------- 查询数据 -----------------------
  const queryFormState = {
    searchWord: '',
    jobId: null,
    status: null,
    endTime: null,
    startTime: null,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });

  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);

  // 日期选择
  let searchDate = ref();

  function dateChange(dates, dateStrings) {
    queryForm.startTime = dateStrings[0];
    queryForm.endTime = dateStrings[1];
  }

  function resetQuery() {
    Object.assign(queryForm, queryFormState);
    queryLogList();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryLogList();
  }

  async function queryLogList() {
    try {
      tableLoading.value = true;
      let responseModel = await jobApi.queryJobLog(queryForm);
      const list = responseModel.list;
      total.value = responseModel.total;
      tableData.value = list;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }
</script>
