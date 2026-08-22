<!--
  * 彩票配置
  *
  * 🔴 本页不做写操作，只做「看 + 停售 + 跳转」：
  *   新建 / 编辑 一律跳「彩票配置工作台」—— 一个玩法要同时落彩票配置与奖级规则，
  *   而且号码长度、发行总量在发过号之后永久冻结（结构锁）。扁平弹窗绕开这些校验，
  *   配出来的玩法上不了线、改出来的玩法会把发号引擎参数改坏。
  *
  *   删除换成禁用（下线）：t_lottery_record 里存着 lottery_code，
  *   删配置会让用户手里已发出的号码指向一条不存在的玩法，开奖与客诉自证全断。
  *   下线只停后续发号，已发出的号码照常开奖 —— 出问题时这才是运营要的止血按钮。
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 11:16:39
  * @Copyright  weolwo
-->
<template>
  <!---------- 概览 begin ----------->
  <a-row :gutter="12" class="overview-row">
    <a-col :span="8">
      <!-- 「配了 N 个玩法、只有 M 个此刻真能领号」是本页最该先回答的问题 -->
      <div class="overview-card" :class="sellableTone">
        <div class="overview-card-label">
          当前可领号的玩法
          <a-tooltip title="三条同时成立才算：玩法已上线 + 配了奖级规则 + 有处于售卖窗口内的待开奖期号。判据与运行态领号逐条一致">
            <QuestionCircleOutlined class="overview-card-hint" />
          </a-tooltip>
        </div>
        <div class="overview-card-value">
          {{ boardResult.sellableCount ?? '-' }}
          <span class="overview-card-slash">/ {{ boardResult.lotteryCount ?? '-' }}</span>
        </div>
        <div class="overview-card-foot">
          {{ notSellableCount > 0 ? `${notSellableCount} 个玩法用户现在领不到号` : '全部玩法均可正常领号' }}
        </div>
      </div>
    </a-col>
    <a-col :span="8">
      <div class="overview-card tone-danger" :class="{ active: queryForm.onlyIssue }" @click="toggleOnlyIssue">
        <div class="overview-card-label">体检告警</div>
        <div class="overview-card-value">
          {{ boardResult.dangerCount ?? '-' }}
          <span v-if="boardResult.warnCount > 0" class="overview-card-sub">+{{ boardResult.warnCount }} 警告</span>
        </div>
        <div class="overview-card-foot">{{ queryForm.onlyIssue ? '已筛选 · 再点取消' : '点击只看有问题的玩法' }}</div>
      </div>
    </a-col>
    <a-col :span="8">
      <div class="overview-card tone-primary">
        <div class="overview-card-label">累计已发号</div>
        <div class="overview-card-value">{{ num(boardResult.totalSold) }}</div>
        <div class="overview-card-foot">各玩法各期 sold_count 之和</div>
      </div>
    </a-col>
  </a-row>
  <!---------- 概览 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="彩票编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.lotteryCode" placeholder="彩票编码" />
      </a-form-item>
      <a-form-item label="彩票名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.lotteryName" placeholder="支持模糊搜索" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 150px" v-model:value="queryForm.status" :options="LOTTERY_STATUS_OPTIONS" placeholder="全部" allowClear @change="onSearch" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-checkbox v-model:checked="queryForm.onlyIssue" @change="onSearch">只看有告警的</a-checkbox>
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
        <a-button @click="goWorkbench()" type="primary" size="small">
          <template #icon>
            <PlusOutlined />
          </template>
          彩票配置工作台
        </a-button>
        <!-- 批量禁用而不是批量删除：止血只需要停发号，不需要毁配置 -->
        <a-button @click="confirmBatchOffline" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <StopOutlined />
          </template>
          批量禁用
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.LOTTERY_CONFIG" :refresh="queryData" />
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
        <template v-if="column.dataIndex === 'lotteryName'">
          <div class="cell-stack">
            <span class="font-bold">{{ record.lotteryName }}</span>
            <span class="cell-sub font-mono">{{ record.lotteryCode }}</span>
          </div>
        </template>

        <!-- 占用率顶满 + 已发过号 = 发行量再也加不上去，这一列就是为了让人在发号前看见 -->
        <template v-if="column.dataIndex === 'spaceUsage'">
          <div class="cell-stack">
            <div>
              <span class="font-bold" :class="usageClass(record)">{{ percent(record.spaceUsage) }}</span>
              <a-tag v-if="record.paramsFrozen" color="default" class="cell-tag ml-1">参数已冻结</a-tag>
            </div>
            <span class="cell-sub">
              {{ record.numberLength }} 位 · 上限 {{ num(record.totalCount) }} / 空间 {{ num(record.numberSpace) }}
            </span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'issueCount'">
          <div class="cell-stack">
            <span>{{ record.issueCount }} 期（待开奖 {{ record.waitIssueCount }}）</span>
            <span class="cell-sub">已发 {{ num(record.soldTotal) }} 个号 · 奖级 {{ record.ruleCount }} 条</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'memberCount'">
          <div class="cell-stack">
            <span>{{ num(record.memberCount) }} 人</span>
            <span class="cell-sub">中奖 {{ num(record.winCount) }} 注</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'issue'">
          <div v-if="!record.issueList || record.issueList.length === 0" class="text-ok">✓</div>
          <div v-else class="cell-stack">
            <a-tooltip v-for="(issue, idx) in record.issueList" :key="idx" :title="issue.message">
              <a-tag :color="issue.level === 'DANGER' ? 'red' : 'orange'" class="cell-tag issue-tag">
                {{ issue.level === 'DANGER' ? '危险' : '警告' }} · {{ shortIssue(issue.message) }}
              </a-tag>
            </a-tooltip>
          </div>
        </template>

        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="lotteryStatusOf(text).color">{{ lotteryStatusOf(text).desc }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="showDetail(record)" type="link">详情</a-button>
            <a-button @click="goWorkbench(record)" type="link">编辑</a-button>
            <!-- 上线有前置条件（必须已配奖级规则），服务端会拦；这里只把它的话原样透出来 -->
            <a-popconfirm
              v-if="record.status === LOTTERY_STATUS_ENUM.OFFLINE.value"
              title="启用后即可对外发号。请确认奖级规则已配置正确。"
              ok-text="确认启用"
              cancel-text="再想想"
              @confirm="requestOnline(record)"
            >
              <a-button type="link">启用</a-button>
            </a-popconfirm>
            <a-popconfirm
              v-else
              title="禁用后立即停止发号。已发出的号码不受影响，期号照常可以开奖。"
              ok-text="确认禁用"
              cancel-text="再想想"
              @confirm="requestOffline(record)"
            >
              <a-button danger type="link">禁用</a-button>
            </a-popconfirm>
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

    <!---------- 详情抽屉：只读，玩法 + 发行进度 + 奖级规则 ----------->
    <a-drawer :title="`玩法详情 · ${detail.lotteryName || ''}`" :width="760" :open="detailVisible" @close="detailVisible = false">
      <a-spin :spinning="detailLoading">
        <a-descriptions title="基础信息" bordered size="small" :column="2" class="mb-6">
          <a-descriptions-item label="彩票编码">{{ detail.lotteryCode }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="lotteryStatusOf(detail.status).color">{{ lotteryStatusOf(detail.status).desc }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="彩票名称" :span="2">{{ detail.lotteryName }}</a-descriptions-item>
          <a-descriptions-item label="归属活动">{{ detail.activityName }}</a-descriptions-item>
          <a-descriptions-item label="活动编码">{{ detail.activityCode }}</a-descriptions-item>
          <a-descriptions-item label="号码长度">{{ detail.numberLength }} 位</a-descriptions-item>
          <a-descriptions-item label="单期发行上限">{{ (detail.totalCount || 0).toLocaleString() }} 张</a-descriptions-item>
          <a-descriptions-item label="已创建期号">{{ detail.issueCount }} 期</a-descriptions-item>
          <a-descriptions-item label="累计已发号">{{ (detail.soldTotal || 0).toLocaleString() }} 张</a-descriptions-item>
          <!-- 结构锁是「为什么这两个参数改不了」的答案，比一个禁用态的输入框有用得多 -->
          <a-descriptions-item label="结构锁" :span="2">
            <a-tag v-if="!detail.structureLocked" color="green">未冻结，发号规格仍可调整</a-tag>
            <span v-else class="text-orange-600">🔒 {{ detail.lockReason }}</span>
          </a-descriptions-item>
        </a-descriptions>

        <div class="mb-2 flex items-center gap-2">
          <span class="text-base font-medium">奖级规则</span>
          <span class="text-xs text-slate-400">t_lottery_prize_rule</span>
        </div>
        <!-- 没有奖级规则的玩法上不了线：online 接口的唯一前置条件就是它 -->
        <a-table size="small" bordered :data-source="detail.prizeRuleList || []" :columns="RULE_COLUMNS" :pagination="false" row-key="prizeLevel">
          <template #emptyText>
            <div class="py-4 text-xs text-orange-500">该玩法未配置任何奖级规则，无法上线（号码发出去了却无奖可发）</div>
          </template>
          <template #bodyCell="{ text, record, column }">
            <template v-if="column.dataIndex === 'matchRule'">
              <a-tag>{{ matchRuleOf(text) }}</a-tag>
              <span class="text-xs text-slate-400">{{ record.matchLength }} 位</span>
            </template>
            <template v-if="column.dataIndex === 'prizeCode'">
              <span>{{ text }}</span>
              <div class="text-xs text-slate-400">{{ record.prizeName || '奖品配置不存在' }}</div>
            </template>
          </template>
        </a-table>

        <div class="mt-6">
          <a-button type="primary" ghost @click="goWorkbench(detailRecord)">去工作台编辑</a-button>
        </div>
      </a-spin>
    </a-drawer>
  </a-card>
</template>
<script setup>
  import { computed, reactive, ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { message, Modal } from 'ant-design-vue';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { StopOutlined } from '@ant-design/icons-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { lotteryConfigApi } from '/src/api/business/lottery/lottery-config-api';
  import { lotteryWorkbenchApi } from '/src/api/business/lottery/lottery-workbench-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { LOTTERY_STATUS_ENUM, LOTTERY_STATUS_OPTIONS, lotteryStatusOf, matchRuleOf } from '/@/constants/business/lottery/lottery-const';

  const router = useRouter();

  // 与 activity-config-const.js 里 LOTTERY 玩法的 route 保持一致，改路由时两处一起改
  const LOTTERY_WORKBENCH_PATH = '/business/lottery/lottery-workbench';

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
      width: 160,
      ellipsis: true,
    },
    {
      // 玩法名 + 编码合并成一列，编码是十位随机码，单独占一列既宽又不好认
      title: '玩法',
      dataIndex: 'lotteryName',
      width: 200,
    },
    {
      title: '字符集：0-9, A-Z',
      dataIndex: 'numberCharset',
      ellipsis: true,
      showFlag: false,
    },
    {
      /*
       * 号码长度与发行上限原先是两个裸数字，谁也不会去心算 10 的 n 次方。
       * 合成一列并给出占用率 —— 那才是「还有多少扩容余地」的答案，
       * 而发过号之后这两个参数永久冻结，占用率顶满就再也加不上去了。
       */
      title: '号码空间占用',
      dataIndex: 'spaceUsage',
      width: 210,
    },
    {
      // 期号与发号实况：玩法配得再好，没有可领号的期号，用户照样领不到
      title: '期号 / 已发号',
      dataIndex: 'issueCount',
      width: 180,
    },
    {
      title: '参与 / 中奖',
      dataIndex: 'memberCount',
      width: 130,
    },
    {
      title: '体检',
      dataIndex: 'issue',
      width: 240,
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
      width: 190,
    },
  ]);

  // ---------------------------- 展示辅助 ----------------------------

  function num(value) {
    return value == null ? '-' : Number(value).toLocaleString();
  }

  function percent(rate) {
    if (rate == null) {
      return '-';
    }
    const v = Number(rate) * 100;
    // 占用率常见是整数（100%、50%），去掉无意义的尾零
    return `${Number(v.toFixed(2))}%`;
  }

  function shortIssue(message) {
    const cut = message.indexOf('：');
    return cut > 0 ? message.slice(0, cut) : message.slice(0, 14);
  }

  /*
   * 占用率分档：≥100% 是超发（永远发不完）、≥90% 是快顶满、其余正常。
   * 之所以在 90% 就变色：号码长度一旦发号就冻结，等到 100% 才提醒已经晚了。
   */
  function usageClass(record) {
    const v = Number(record.spaceUsage ?? 0);
    if (v > 1) {
      return 'text-dead';
    }
    return v >= 0.9 ? 'text-warn' : '';
  }

  const notSellableCount = computed(() => {
    if (boardResult.value.lotteryCount == null) {
      return 0;
    }
    return boardResult.value.lotteryCount - (boardResult.value.sellableCount ?? 0);
  });

  const sellableTone = computed(() => (notSellableCount.value > 0 ? 'tone-warning' : 'tone-success'));

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    activityCode: undefined, //活动编码
    lotteryCode: undefined, //彩票编码
    lotteryName: undefined, //彩票名称
    status: undefined, //状态：0-下线, 1-上线
    onlyIssue: false, //只看有体检告警的玩法
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
  // 概览（与明细同一次请求返回，卡片数字与列表必然一致）
  const boardResult = ref({});

  function toggleOnlyIssue() {
    queryForm.onlyIssue = !queryForm.onlyIssue;
    onSearch();
  }

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
      // 走 board 而不是 queryPage：多带号码空间占用、期号发号实况与体检结论
      let queryResult = await lotteryConfigApi.board(queryForm);
      boardResult.value = queryResult.data || {};
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
      // 状态变了的行留在选中列表里没意义，且容易让人对着旧状态再点一次批量禁用
      selectedRowKeyList.value = [];
      selectedRowList.value = [];
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  // 「创建时间」筛选已随查询区精简移除：玩法是配置数据、总共就几条，
  // 按创建时间筛没有实际用途，位置让给了「只看有告警的」

  onMounted(queryData);

  // ---------------------------- 跳转工作台 ----------------------------

  /*
   * 新建与编辑同一个出口。
   *
   * 不传 record = 新建：必须带 mode=new。工作台在「只有一个彩票活动」时会自动打开该活动的
   * 第一个玩法，不带这个标记从本页点「新建」会直接落在某个已有玩法上，像是进错了页面。
   *
   * 传了 record = 编辑：带 activityCode + lotteryCode 深链到那一个玩法。
   */
  function goWorkbench(record) {
    if (!record) {
      router.push({ path: LOTTERY_WORKBENCH_PATH, query: { mode: 'new' } });
      return;
    }
    router.push({
      path: LOTTERY_WORKBENCH_PATH,
      query: { activityCode: record.activityCode, lotteryCode: record.lotteryCode },
    });
  }

  // ---------------------------- 详情 ----------------------------

  const RULE_COLUMNS = [
    { title: '奖级', dataIndex: 'prizeLevel', width: 70 },
    { title: '匹配规则', dataIndex: 'matchRule', width: 180 },
    { title: '奖品编码', dataIndex: 'prizeCode', ellipsis: true },
    { title: '奖品价值', dataIndex: 'prizeValue', width: 100 },
  ];

  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detail = ref({});
  // 抽屉里的「去工作台编辑」需要原始行，detail 里没有列表行的字段
  const detailRecord = ref(null);

  /*
   * 复用工作台的聚合回显接口，不另开一个 detail：
   * 详情要看的（发行进度、结构锁、奖级规则）正是工作台加载的那一份，
   * 两个接口各查一次，迟早出现「详情说没锁、工作台里却改不了」。
   */
  async function showDetail(record) {
    detailRecord.value = record;
    detail.value = {};
    detailVisible.value = true;
    detailLoading.value = true;
    try {
      const res = await lotteryWorkbenchApi.detail(record.activityCode, record.lotteryCode);
      detail.value = res.data || {};
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      detailLoading.value = false;
    }
  }

  // ---------------------------- 启用 / 禁用 ----------------------------

  async function requestOnline(record) {
    SmartLoading.show();
    try {
      await lotteryConfigApi.online(record.lotteryCode);
      message.success('已启用，现在可以对外发号');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  async function requestOffline(record) {
    SmartLoading.show();
    try {
      await lotteryConfigApi.offline(record.lotteryCode);
      message.success('已禁用，已停止发号');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 批量禁用 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);
  // 行选中给的是 rowKey(id)，而上下线接口按 lotteryCode 定位，这里存一份映射
  const selectedRowList = ref([]);

  function onSelectChange(selectedRowKeys, selectedRows) {
    selectedRowKeyList.value = selectedRowKeys;
    selectedRowList.value = selectedRows;
  }

  function confirmBatchOffline() {
    Modal.confirm({
      title: '提示',
      content: `确定禁用选中的 ${selectedRowKeyList.value.length} 个玩法吗？禁用后立即停止发号，已发出的号码不受影响。`,
      okText: '确认禁用',
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
      SmartLoading.show();
      const codeList = selectedRowList.value.map((item) => item.lotteryCode);
      const res = await lotteryConfigApi.batchOffline(codeList);
      // 服务端回的是「已禁用 N 个，跳过 M 个…」的汇总，原样透出来 ——
      // 批量里混着已下线的玩法是常态，笼统提示一句「操作成功」会掩盖掉失败的那几个
      message.success(res.data || '操作成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
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
    color: #dc2626;
  }

  .text-warn {
    color: #d97706;
  }

  .text-ok {
    color: #10b981;
  }
</style>
