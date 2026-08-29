<!--
  * 奖池配置
  *
  * 定位：奖池自身配置的<b>唯一编辑入口</b> + 「这个池现在能不能正常抽」的体检。
  *
  * ⚠️ 与另外三个抽奖页面不同，本页<b>保留编辑</b>，因为抽奖工作台保存奖池时只管 poolName
  * （见 PrizePoolConfigService.workbenchSave 第 7 步注释），
  * 限领重置周期与奖池开关只有这里能改 —— 收掉编辑这两个字段就没有入口了。
  *
  * 一个池能不能真的抽，取决于四件事的组合：活动上线了吗、奖池开着吗、坑位配了吗、概率闭环吗。
  * 这四个分散在三张表里，原先的列表把奖池表字段一列列摆出来，一个都答不上。
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 09:42:12
  * @Copyright  weolwo
-->
<template>
  <!---------- 概览 begin ----------->
  <a-row :gutter="12" class="overview-row">
    <a-col :span="8">
      <!-- 「配了 10 个池、只有 3 个能抽」是本页最该先回答的问题 -->
      <div class="overview-card" :class="drawableTone">
        <div class="overview-card-label">
          当前可抽的奖池
          <a-tooltip title="四个条件同时成立才算：活动已上线 + 奖池开启 + 配了坑位 + 概率闭环到 100%">
            <QuestionCircleOutlined class="overview-card-hint" />
          </a-tooltip>
        </div>
        <div class="overview-card-value">
          {{ result.drawableCount ?? '-' }}
          <span class="overview-card-slash">/ {{ result.poolCount ?? '-' }}</span>
        </div>
        <div class="overview-card-foot">
          {{ notDrawableCount > 0 ? `${notDrawableCount} 个配了却抽不了` : '全部奖池均可正常抽奖' }}
        </div>
      </div>
    </a-col>
    <a-col :span="8">
      <div class="overview-card tone-danger" :class="{ active: queryForm.onlyIssue }" @click="toggleOnlyIssue">
        <div class="overview-card-label">体检告警</div>
        <div class="overview-card-value">
          {{ result.dangerCount ?? '-' }}
          <span v-if="result.warnCount > 0" class="overview-card-sub">+{{ result.warnCount }} 警告</span>
        </div>
        <div class="overview-card-foot">{{ queryForm.onlyIssue ? '已筛选 · 再点取消' : '点击只看有问题的奖池' }}</div>
      </div>
    </a-col>
    <a-col :span="8">
      <div class="overview-card tone-primary">
        <div class="overview-card-label">本页职责</div>
        <div class="overview-card-value overview-card-text">限领重置周期 · 奖池开关</div>
        <div class="overview-card-foot">坑位与概率请到抽奖工作台配置</div>
      </div>
    </a-col>
  </a-row>
  <!---------- 概览 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <!-- 活动编码是十位随机码，手输等于必须先去别处抄一遍，而且是精确匹配 —— 改成下拉 -->
      <a-form-item label="活动" class="solvela-query-form-item">
        <a-select
          style="width: 240px"
          v-model:value="queryForm.activityCode"
          :options="activityOptions"
          :loading="activityLoading"
          placeholder="全部活动"
          allowClear
          show-search
          option-filter-prop="label"
          @change="onSearch"
        />
      </a-form-item>
      <a-form-item label="奖池名称" class="solvela-query-form-item">
        <a-input style="width: 180px" v-model:value="queryForm.poolName" placeholder="支持模糊搜索" @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="开关" class="solvela-query-form-item">
        <a-select
          style="width: 130px"
          v-model:value="queryForm.status"
          :options="POOL_STATUS_OPTIONS"
          placeholder="全部"
          allowClear
          @change="onSearch"
        />
      </a-form-item>
      <a-form-item class="solvela-query-form-item">
        <a-checkbox v-model:checked="queryForm.onlyIssue" @change="onSearch">只看有告警的</a-checkbox>
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
        <!-- 新建奖池去工作台：只有奖池行、没有坑位的池，抽奖时会抛「奖池快照不能为空」，
             是个建了就用不了的空壳。工作台把「池 + 坑位 + 概率闭环校验」放在一个事务里 -->
        <a-button @click="gotoWorkbenchCreate" type="primary" size="small">
          <template #icon>
            <PlusOutlined />
          </template>
          去工作台新建奖池
        </a-button>
        <!-- 批量禁用而不是批量删除：删池会让 t_draw_prize_log 里的流水指向一个不存在的奖池，
             那是发奖凭证与对账依据。禁用只关新请求的口子，不动任何历史数据 -->
        <a-button @click="confirmBatchOffline" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <StopOutlined />
          </template>
          批量禁用
        </a-button>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.PRIZE_POOL_CONFIG" :refresh="queryData" />
      </div>
    </a-row>
    <!---------- 表格操作行 end ----------->

    <!---------- 表格 begin ----------->
    <a-table
      size="small"
      :scroll="{ x: 1300, y: 800 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
      :row-class-name="(record) => (record.dangerCount > 0 ? 'row-danger' : '')"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'poolName'">
          <div class="cell-stack">
            <span class="font-bold">{{ record.poolName }}</span>
            <span class="cell-sub font-mono">{{ record.poolCode }}</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'activityCode'">
          <div class="cell-stack">
            <span v-if="record.activityName">{{ record.activityName }}</span>
            <span v-else class="text-dead">活动已不存在</span>
            <a-tag v-if="record.activityStatus != null" :color="record.activityStatus === 1 ? 'green' : 'default'" class="cell-tag">
              {{ record.activityStatus === 1 ? '已上线' : '未上线' }}
            </a-tag>
          </div>
        </template>

        <!-- 「能不能抽」是四个条件的与，单看任何一个字段都答不出来 -->
        <template v-if="column.dataIndex === 'drawable'">
          <a-tag v-if="record.drawable" color="green">可抽奖</a-tag>
          <a-tag v-else color="red">抽不了</a-tag>
        </template>

        <template v-if="column.dataIndex === 'slotCount'">
          <div class="cell-stack">
            <span>{{ record.slotCount }} 个坑位</span>
            <span class="cell-sub" :class="record.probabilityClosed ? '' : 'text-dead'">
              概率 {{ num(record.probabilitySum) }}%{{ record.probabilityClosed ? '' : ' 未闭环' }}
            </span>
          </div>
        </template>

        <!-- 重置周期与限领奖项数必须放在一起看：没有限领奖项时，重置周期配了也不起作用 -->
        <template v-if="column.dataIndex === 'resetPeriod'">
          <div class="cell-stack">
            <span>{{ resetPeriodOf(text) }}</span>
            <span class="cell-sub" :class="periodHintClass(record)">{{ periodHint(record) }}</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="poolStatusOf(text).color">{{ poolStatusOf(text).desc }}</a-tag>
        </template>

        <template v-if="column.dataIndex === 'issue'">
          <div v-if="record.issueList.length === 0" class="text-ok">✓</div>
          <div v-else class="cell-stack">
            <a-tooltip v-for="(issue, idx) in record.issueList" :key="idx" :title="issue.message">
              <a-tag :color="issue.level === 'DANGER' ? 'red' : 'orange'" class="cell-tag issue-tag">
                {{ issue.level === 'DANGER' ? '危险' : '警告' }} · {{ shortIssue(issue.message) }}
              </a-tag>
            </a-tooltip>
          </div>
        </template>

        <!--
          操作列只有两个动作：编辑（本页职责：奖池名称 + 限领重置周期）与 禁用/启用。
          「配坑位」不再单独占一个按钮 —— 它的入口挪进了编辑弹窗，
          省得两个按钮看起来像是能改同一批东西，实际改的是完全不同的字段。
        -->
        <template v-if="column.dataIndex === 'action'">
          <div class="solvela-table-operate">
            <a-button @click="showForm(record)" type="link">编辑</a-button>
            <a-popconfirm
              v-if="record.status === POOL_STATUS_ENUM.OPEN.value"
              title="禁用后运行态立即拒绝新的抽奖请求。已有的抽奖流水与坑位配置不受影响，随时可以再启用。"
              ok-text="确认禁用"
              cancel-text="再想想"
              @confirm="requestOffline(record)"
            >
              <a-button danger type="link">禁用</a-button>
            </a-popconfirm>
            <a-button v-else type="link" @click="requestOnline(record)">启用</a-button>
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

    <PrizePoolConfigForm ref="formRef" @reloadList="queryData" />
  </a-card>
</template>
<script setup>
  import { computed, reactive, ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { message, Modal } from 'ant-design-vue';
  import { QuestionCircleOutlined, StopOutlined } from '@ant-design/icons-vue';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { prizePoolConfigApi } from '/src/api/business/draw/prize-pool-config-api';
  import { activityConfigApi } from '/src/api/business/activity/activity-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import PrizePoolConfigForm from './prize-pool-config-form.vue';
  import { POOL_STATUS_ENUM, POOL_STATUS_OPTIONS, poolStatusOf, resetPeriodOf } from '/src/constants/business/draw/prize-pool-config-const';

  const router = useRouter();

  // 与抽奖工作台同源：活动下拉只取抽奖类活动
  const ACTIVITY_TYPE_DRAW = 'DRAW';

  // ---------------------------- 表格列 ----------------------------

  /*
   * 列按「这是哪个池 → 属于哪个活动 → 现在能不能抽 → 为什么不能」排。
   * 「能不能抽」放在靠前位置：它是四个条件的与，也是运营唯一真正关心的结论。
   * id / 租户id / 创建人 等审计列走全局默认隐藏（DEFAULT_HIDDEN_COLUMNS）。
   */
  const columns = ref([
    {
      title: 'id',
      dataIndex: 'id',
      width: 80,
      ellipsis: true,
      showFlag: false,
    },
    {
      title: '奖池',
      dataIndex: 'poolName',
      width: 200,
    },
    {
      title: '所属活动',
      dataIndex: 'activityCode',
      width: 180,
    },
    {
      title: '能否抽奖',
      dataIndex: 'drawable',
      width: 100,
    },
    {
      title: '坑位 / 概率',
      dataIndex: 'slotCount',
      width: 150,
    },
    {
      title: '限领重置周期',
      dataIndex: 'resetPeriod',
      width: 190,
    },
    {
      title: '奖池开关',
      dataIndex: 'status',
      width: 100,
    },
    {
      title: '体检',
      dataIndex: 'issue',
      width: 240,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      ellipsis: true,
      showFlag: false,
    },
    {
      title: '操作',
      dataIndex: 'action',
      fixed: 'right',
      width: 200,
    },
  ]);

  // ---------------------------- 展示辅助 ----------------------------

  function num(value) {
    return value == null ? '-' : Number(value).toString();
  }

  function shortIssue(message) {
    const cut = message.indexOf('：');
    return cut > 0 ? message.slice(0, cut) : message.slice(0, 14);
  }

  /**
   * 重置周期底下那行小字：把「这个周期配置到底起不起作用」直接说出来。
   *
   * 重置周期重置的是奖项的单人限领计数，池内一个限领奖项都没有时，
   * 配成每天还是每月都不影响任何行为 —— 而光看「每天」两个字是看不出来的。
   */
  function periodHint(record) {
    const limited = record.limitedItemCount ?? 0;
    if (limited === 0) {
      return record.slotCount > 0 ? '池内无限领奖项，该配置当前不生效' : '尚未配置奖项';
    }
    return `${limited} 个奖项限领，按此周期归零`;
  }

  function periodHintClass(record) {
    return (record.limitedItemCount ?? 0) === 0 && record.slotCount > 0 ? 'text-warn' : '';
  }

  const notDrawableCount = computed(() => {
    if (result.value.poolCount == null) {
      return 0;
    }
    return result.value.poolCount - (result.value.drawableCount ?? 0);
  });

  const drawableTone = computed(() => (notDrawableCount.value > 0 ? 'tone-warning' : 'tone-success'));

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    activityCode: undefined,
    poolName: undefined,
    status: undefined,
    onlyIssue: false,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);
  const result = ref({});

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    queryData();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function toggleOnlyIssue() {
    queryForm.onlyIssue = !queryForm.onlyIssue;
    onSearch();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      // 概览与明细同一次请求返回，卡片数字与列表必然一致
      const res = await prizePoolConfigApi.board(queryForm);
      result.value = res || {};
      tableData.value = res?.list || [];
      total.value = res?.total || 0;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(() => {
    queryData();
    loadActivityOptions();
  });

  // ---------------------------- 活动下拉 ----------------------------

  const activityOptions = ref([]);
  const activityLoading = ref(false);

  /*
   * includeInactive 传 true：optionList 默认过滤掉已下线/已过期的活动，
   * 而未上线活动下的奖池恰恰是本页的主要工作对象 —— 上线前就该把配置查干净。
   */
  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList(ACTIVITY_TYPE_DRAW, true);
      activityOptions.value = (res || []).map((item) => ({
        value: item.activityCode,
        label: `${item.activityName}（${item.activityCode}）`,
      }));
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      activityLoading.value = false;
    }
  }

  // ---------------------------- 跳转工作台 ----------------------------

  /**
   * 新建奖池去工作台。
   *
   * 带上当前筛选的活动，省得到了那边再选一次；没筛活动时不带参数，
   * 让工作台按它自己的规则决定打开哪个（只有一个活动时会自动打开）。
   */
  function gotoWorkbenchCreate() {
    router.push({
      path: '/business/draw/draw-workbench',
      query: queryForm.activityCode ? { activityCode: queryForm.activityCode } : {},
    });
  }

  // ---------------------------- 编辑 ----------------------------
  const formRef = ref();

  function showForm(data) {
    formRef.value.show(data);
  }

  // ---------------------------- 禁用 / 启用 ----------------------------

  /*
   * 奖池只有禁用，没有删除。
   *
   * t_pool_prize_mapping 按 pool_code 关联、t_draw_prize_log 里也存着 pool_code——
   * 后者是发奖凭证与对账依据。池删了，用户说「我明明在这个池抽中过」就再也自证不了，
   * 而这种事往往几个月后才发生。禁用一个字都不动历史数据，
   * 运行态判 status 直接拒绝新请求，而且可逆 —— 出问题时这才是运营要的止血按钮。
   */
  async function requestOffline(data) {
    SolvelaLoading.show();
    try {
      await prizePoolConfigApi.offline(data.id);
      message.success('已禁用，本奖池停止接受新的抽奖请求');
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }

  async function requestOnline(data) {
    SolvelaLoading.show();
    try {
      await prizePoolConfigApi.online(data.id);
      message.success('已启用');
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }

  // ---------------------------- 批量禁用 ----------------------------

  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function confirmBatchOffline() {
    Modal.confirm({
      title: '提示',
      content: `确定禁用选中的 ${selectedRowKeyList.value.length} 个奖池吗？禁用后立即停止接受新的抽奖请求，已有的流水与坑位配置不受影响。`,
      okText: '确认禁用',
      okType: 'danger',
      onOk() {
        requestBatchOffline();
      },
      cancelText: '取消',
    });
  }

  async function requestBatchOffline() {
    try {
      SolvelaLoading.show();
      const res = await prizePoolConfigApi.batchOffline(selectedRowKeyList.value);
      // 服务端回的是「已禁用 N 个，跳过 M 个」的汇总，原样透出来 ——
      // 批量里混着本来就已关闭的是常态，笼统一句「操作成功」会把跳过的那几个盖掉
      message.success(res || '操作成功');
      selectedRowKeyList.value = [];
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }
</script>

<style lang="less" scoped>
  .overview-row {
    margin-bottom: 12px;
  }

  .overview-card {
    padding: 12px 16px;
    background: #fff;
    border: 1px solid #e2e8f0;
    border-left: 4px solid #cbd5e1;
    border-radius: 6px;
    transition: all 0.2s;
  }

  .tone-success {
    border-left-color: #10b981;

    .overview-card-value {
      color: #059669;
    }
  }

  .tone-warning {
    border-left-color: #f59e0b;

    .overview-card-value {
      color: #d97706;
    }
  }

  .tone-danger {
    cursor: pointer;
    border-left-color: #ef4444;

    &:hover {
      box-shadow: 0 2px 10px rgb(15 23 42 / 8%);
    }

    &.active {
      background: #f8fafc;
      box-shadow: 0 2px 10px rgb(15 23 42 / 10%);
    }

    .overview-card-value {
      color: #dc2626;
    }
  }

  .tone-primary {
    border-left-color: #3b82f6;
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

  .overview-card-text {
    font-size: 15px;
  }

  .overview-card-slash {
    font-size: 16px;
    font-weight: 500;
    color: #cbd5e1;
  }

  .overview-card-sub {
    margin-left: 6px;
    font-size: 12px;
    font-weight: 500;
    color: #f59e0b;
  }

  .overview-card-foot {
    margin-top: 2px;
    font-size: 11px;
    color: #cbd5e1;
  }

  .cell-stack {
    display: flex;
    flex-direction: column;
    gap: 2px;
    align-items: flex-start;
  }

  .cell-sub {
    font-size: 11px;
    color: #94a3b8;
  }

  .cell-tag {
    margin: 0;
  }

  .issue-tag {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .text-dead {
    font-weight: 600;
    color: #dc2626;
  }

  .text-warn {
    color: #d97706;
  }

  .text-ok {
    color: #10b981;
  }

  :deep(.row-danger) > td {
    background-color: #fef2f2;
  }
</style>
