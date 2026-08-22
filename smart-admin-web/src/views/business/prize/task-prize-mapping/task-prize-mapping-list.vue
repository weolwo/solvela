<!--
  * 任务奖励总览（t_task_prize_mapping）
  *
  * 🔴 这是一张**只读**的巡检表，故意没有新建 / 编辑 / 删除。
  *
  * 这张表没有独立生命周期：任务向导每次保存都按 task_config_id 全删重插
  * （TaskConfigService.wizardUpdate），在这里改一条，下次向导保存就被无声抹掉。
  * 删除虽然确实能让奖发不出去，但那是物理删除、不可逆，而且删掉中间档位会把
  * 跑着的活动改坏（已拿过第 1 档的用户下次直接跨到第 3 档）——
  * 停发的正确做法是把任务下线，可逆、有状态可查。
  *
  * 它的价值在向导之外：向导只看得见自己那一个任务，看不见奖励编码在活动的奖品配置里
  * 到底存不存在。那类问题不会在配置时报错，要到用户真的达标那一刻才在日志里冒出来。
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 20:41:02
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <!-- 入口条件是活动而不是 taskConfigId：运营记得住活动，记不住子表的任务ID -->
      <a-form-item label="归属活动" class="smart-query-form-item">
        <a-select
          style="width: 260px"
          v-model:value="queryForm.activityCode"
          :options="activityOptions"
          :loading="activityLoading"
          placeholder="全部活动"
          allowClear
          show-search
          option-filter-prop="label"
        />
      </a-form-item>
      <a-form-item label="任务名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.taskName" placeholder="任务名称" />
      </a-form-item>
      <a-form-item label="奖励编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.prizeCode" placeholder="奖励编码" />
      </a-form-item>
      <a-form-item label="任务状态" class="smart-query-form-item">
        <a-select style="width: 160px" v-model:value="queryForm.taskStatus" :options="CONFIG_STATUS_OPTIONS" placeholder="全部" allowClear />
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
        <!-- 没有任何写操作。想改奖励阶梯只有一条路：任务配置向导 -->
        <a-alert v-if="issueCount > 0" type="warning" show-icon banner :message="`本页有 ${issueCount} 档奖励配置存在问题，达标时可能发不出奖`" />
        <span v-else class="text-xs text-slate-400">奖励阶梯只能在「任务配置向导」里修改，本页只读</span>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.TASK_PRIZE_MAPPING" :refresh="queryData" />
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
      :row-class-name="rowClassName"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'taskStatus'">
          <a-tag :color="configStatusOf(text).color">{{ configStatusOf(text).desc }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'stageLevel'">
          <span>第 {{ text }} 档</span>
        </template>
        <template v-if="column.dataIndex === 'stageTarget'">
          <!-- 拆不出 target 就把原文亮出来，别只显示一个「-」让人以为没配 -->
          <span v-if="text !== null && text !== undefined">{{ text }}</span>
          <a-tooltip v-else :title="record.stageCondition">
            <span class="text-orange-500">解析失败</span>
          </a-tooltip>
        </template>
        <template v-if="column.dataIndex === 'prizeCode'">
          <span>{{ text }}</span>
          <div class="text-xs" :class="record.prizeName ? 'text-slate-400' : 'text-red-500'">
            {{ record.prizeName || '奖品配置不存在' }}
          </div>
        </template>
        <template v-if="column.dataIndex === 'prizeMode'">
          <a-tag :color="record.prizeMode === PRIZE_MODE_ENUM.FIXED ? 'default' : 'orange'">{{ prizeModeOf(text) }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'prizeValue'">
          <span v-if="text !== null && text !== undefined">{{ text }}</span>
          <a-tooltip v-else :title="record.prizeStrategy">
            <span class="text-slate-400">按奖品配置</span>
          </a-tooltip>
        </template>
        <template v-if="column.dataIndex === 'issueList'">
          <span v-if="!text || text.length === 0" class="text-slate-300">正常</span>
          <a-tooltip v-else :title="text.join('；')">
            <a-tag color="red">{{ text.length }} 项待修</a-tag>
          </a-tooltip>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <!-- 唯一出口：回到向导。改动统统走那条有校验的路 -->
            <a-button @click="goWizardEdit(record)" type="link">去向导编辑</a-button>
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
  import { computed, reactive, ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { taskPrizeMappingApi } from '/src/api/business/prize/task-prize-mapping-api';
  import { activityConfigApi } from '/src/api/business/activity/activity-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { PRIZE_MODE_ENUM, prizeModeOf } from '/src/constants/business/prize/task-prize-mapping-const';
  import { CONFIG_STATUS_OPTIONS, configStatusOf } from '/src/constants/business/task/task-config-const';

  const router = useRouter();

  // 与 task-config-list 保持同一个常量值，改路由时两处一起改
  const TASK_WIZARD_PATH = '/business/task/task-wizard';

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: '归属活动',
      dataIndex: 'activityCode',
      width: 140,
      ellipsis: true,
    },
    {
      title: '任务名称',
      dataIndex: 'taskName',
      width: 180,
      ellipsis: true,
    },
    {
      title: '任务状态',
      dataIndex: 'taskStatus',
      width: 90,
    },
    {
      title: '阶梯',
      dataIndex: 'stageLevel',
      width: 80,
    },
    {
      title: '达标值',
      dataIndex: 'stageTarget',
      width: 100,
    },
    {
      title: '奖励编码',
      dataIndex: 'prizeCode',
      width: 180,
      ellipsis: true,
    },
    {
      title: '计算类型',
      dataIndex: 'prizeMode',
      width: 100,
    },
    {
      title: '奖励值',
      dataIndex: 'prizeValue',
      width: 100,
    },
    {
      title: '配置体检',
      dataIndex: 'issueList',
      width: 110,
    },
    // 原始 JSON 默认收起：排查时能从列设置里勾出来，平时它只是噪音
    {
      title: '达标条件原文',
      dataIndex: 'stageCondition',
      ellipsis: true,
      showFlag: false,
    },
    {
      title: '发奖策略原文',
      dataIndex: 'prizeStrategy',
      ellipsis: true,
      showFlag: false,
    },
    {
      title: '任务配置ID',
      dataIndex: 'taskConfigId',
      width: 110,
      showFlag: false,
    },
    {
      title: '操作',
      dataIndex: 'action',
      fixed: 'right',
      width: 110,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    activityCode: undefined, //归属活动
    taskName: undefined, //任务名称
    taskStatus: undefined, //任务状态
    prizeCode: undefined, //奖励编码
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

  const issueCount = computed(() => tableData.value.filter((item) => item.issueList && item.issueList.length > 0).length);

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
      let queryResult = await taskPrizeMappingApi.queryPage(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function rowClassName(record) {
    return record.issueList && record.issueList.length > 0 ? 'smart-table-row-warning' : '';
  }

  // ---------------------------- 归属活动下拉 ----------------------------

  const activityOptions = ref([]);
  const activityLoading = ref(false);

  // includeInactive 传 true：巡检要能查已下线/已过期的活动，
  // 复盘「当初那个活动的奖励是怎么配的」正是这页存在的理由之一
  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList('TASK', true);
      activityOptions.value = (res.data || []).map((item) => ({
        value: item.activityCode,
        label: `${item.activityName}（${item.activityCode}）`,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      activityLoading.value = false;
    }
  }

  // ---------------------------- 跳转向导 ----------------------------

  function goWizardEdit(record) {
    router.push({ path: TASK_WIZARD_PATH, query: { id: record.taskConfigId } });
  }

  onMounted(() => {
    loadActivityOptions();
    queryData();
  });
</script>
<style scoped lang="less">
  // 有问题的档位整行标出来：这页往往一屏几十行，只在末列点个红 tag 容易被滑过去
  :deep(.smart-table-row-warning) > td {
    background-color: #fff7e6;
  }
</style>
