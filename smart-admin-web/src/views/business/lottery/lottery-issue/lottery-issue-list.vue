<!--
  * 期号巡检
  *
  * 定位：跨玩法的期号总览与异常巡检。
  *
  * 与「彩票配置工作台」Tab2 的分工是刻意的，不要再往这里搬开奖能力：
  *   - 工作台 = 单个玩法的纵深操作（创设、改时间、摇号、开奖、派奖、核销进度）；
  *   - 本页   = 所有玩法横向巡检（谁逾期没开、谁快售罄、今天有几期要开），
  *             发现问题后一键深链回工作台去处理。
  * 之所以要有这一页：工作台一次只盯一个玩法，看不到「所有玩法里哪几期到点没人开奖」——
  * 而那正是要赔付的那几期。
  *
  * ⚠️ 页面里没有任何 new Date()。售卖态、是否逾期，全部由服务端用数据库时钟算好后返回
  * （铁律 9/10：全系统只认数据库一个时钟）。浏览器再算一遍就是第二个时钟源，
  * 客户端时钟偏一点，页面显示的「在售」就会和运行态发号的判定对不上。
  *
  * @Author:    weolwo
  * @Date:      2026-05-09 16:54:51
  * @Copyright  weolwo
-->
<template>
  <!---------- 巡检概览 begin ----------->
  <!--
    四张卡片既是概览也是筛选入口：点一下即按该口径筛列表，再点一下取消。
    数字与列表口径同源（服务端共用同一份 SQL 表达式），所以点进去的行数必然等于卡片上的数字。
  -->
  <a-row :gutter="12" class="overview-row">
    <a-col v-for="card in overviewCards" :key="card.key" :span="6">
      <div class="overview-card" :class="[card.tone, { active: activeCard === card.key }]" @click="toggleCard(card)">
        <div class="overview-card-label">
          {{ card.label }}
          <a-tooltip :title="card.hint"><QuestionCircleOutlined class="overview-card-hint" /></a-tooltip>
        </div>
        <div class="overview-card-value">{{ card.value ?? '-' }}</div>
        <div class="overview-card-foot">{{ activeCard === card.key ? '已筛选 · 再点取消' : '点击筛选' }}</div>
      </div>
    </a-col>
  </a-row>
  <!---------- 巡检概览 end ----------->

  <!---------- 查询表单form begin ----------->
  <!--
    去掉了原先的「租户id」输入框：租户维度已于 v3.73.0 整体删除
    （新建表单里根本没有这个字段），筛它永远只有两种结果 —— 全部或全空。
    「创建时间」换成「计划开奖时间」：期号的创建顺序对运营没有意义，先建的未必先开奖。
  -->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="彩票玩法" class="smart-query-form-item">
        <a-select
          style="width: 200px"
          v-model:value="queryForm.lotteryCode"
          :options="lotteryOptions"
          :loading="lotteryLoading"
          placeholder="全部玩法"
          allowClear
          show-search
          option-filter-prop="label"
          @change="onSearch"
        />
      </a-form-item>
      <a-form-item label="期号" class="smart-query-form-item">
        <a-input style="width: 180px" v-model:value="queryForm.issueNo" placeholder="支持模糊搜索" @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="售卖态" class="smart-query-form-item">
        <a-select style="width: 150px" v-model:value="queryForm.saleState" :options="SALE_STATE_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="开奖状态" class="smart-query-form-item">
        <a-select style="width: 130px" v-model:value="queryForm.status" :options="ISSUE_STATUS_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="计划开奖" class="smart-query-form-item">
        <a-range-picker
          v-model:value="queryForm.planDrawTime"
          :presets="defaultTimeRanges"
          style="width: 230px"
          @change="onChangePlanDrawTime"
        />
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
      :scroll="{ x: 1200, y: 800 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
      :row-class-name="(record) => (record.overdue ? 'row-overdue' : '')"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
    >
      <template #bodyCell="{ text, record, column }">
        <!-- 期号 + 玩法名：期号单看是一串编码，不带玩法名根本认不出是哪一个活动的 -->
        <template v-if="column.dataIndex === 'issueNo'">
          <div class="cell-stack">
            <span class="font-mono font-bold">{{ record.issueNo }}</span>
            <span class="cell-sub">{{ record.lotteryName || record.lotteryCode }}</span>
          </div>
        </template>

        <!-- 售卖窗口：起止时间 + 服务端算好的售卖态。
             这个 tag 才是「现在还能不能领号」的答案，开奖状态那一列回答不了 -->
        <template v-if="column.dataIndex === 'saleWindow'">
          <div class="cell-stack">
            <a-tag :color="saleStateOf(record.saleState).color" class="cell-tag">{{ saleStateOf(record.saleState).desc }}</a-tag>
            <span class="cell-sub">{{ fmt(record.saleStartTime) }} ~ {{ fmt(record.saleEndTime) }}</span>
          </div>
        </template>

        <!-- 发售进度：只给分子（原先那样）看不出任何信息，
             要的是「离发完还有多远」，所以分母 total_count 一起 join 回来了 -->
        <template v-if="column.dataIndex === 'soldCount'">
          <div class="cell-stack">
            <a-progress
              :percent="soldPercent(record)"
              :status="soldPercent(record) >= 100 ? 'exception' : 'normal'"
              size="small"
              :show-info="false"
            />
            <span class="cell-sub">
              {{ (record.soldCount || 0).toLocaleString() }} / {{ record.totalCount == null ? '—' : record.totalCount.toLocaleString() }}
            </span>
          </div>
        </template>

        <!-- 计划开奖 vs 实际开奖。逾期由服务端判定（plan_draw_time 已过且未开完），
             这里只负责标红 —— 前端不参与任何时间比较 -->
        <template v-if="column.dataIndex === 'drawTime'">
          <div class="cell-stack">
            <span :class="record.overdue ? 'text-overdue' : ''">
              <span class="cell-label">计划</span>{{ fmt(record.planDrawTime) || '未设置' }}
              <a-tag v-if="record.overdue" color="red" class="cell-tag ml-1">逾期未开奖</a-tag>
            </span>
            <span class="cell-sub"><span class="cell-label">实际</span>{{ fmt(record.settleTime) || '—' }}</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'winningNumber'">
          <span v-if="record.winningNumber" class="font-mono font-bold text-winning">{{ record.winningNumber }}</span>
          <span v-else class="cell-sub">—</span>
        </template>

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
            <!-- 开奖不在本页做：本页是巡检，纵深操作一律回工作台，
                 两处各实现一遍开奖迟早会漂移 -->
            <a-button type="link" @click="gotoWorkbench(record)">去开奖</a-button>
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

    <LotteryIssueForm ref="formRef" @reloadList="reload" />
  </a-card>
</template>
<script setup>
  import { computed, reactive, ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { message, Modal } from 'ant-design-vue';
  import { StopOutlined, QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { lotteryIssueApi } from '/src/api/business/lottery/lottery-issue-api';
  import { lotteryConfigApi } from '/src/api/business/lottery/lottery-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import LotteryIssueForm from './lottery-issue-form.vue';
  import {
    ISSUE_STATUS_ENUM,
    ISSUE_STATUS_OPTIONS,
    issueStatusOf,
    SALE_STATE_ENUM,
    SALE_STATE_OPTIONS,
    saleStateOf,
  } from '/@/constants/business/lottery/lottery-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';

  const router = useRouter();

  // ---------------------------- 表格列 ----------------------------

  /*
   * 列的排布按「运营从左往右读一遍就能判断这期要不要管」来定：
   *   是哪一期 → 现在能不能买 → 卖了多少 → 该开奖了没 → 开了什么 → 走到哪一步。
   *
   * id / 创建时间 标了 showFlag: false，租户id、创建人、更新人、更新时间
   * 命中全局 DEFAULT_HIDDEN_COLUMNS —— 都只是默认不显示，列设置里随时能勾回来。
   * 原先这些审计字段占了 15 列里的 6 列，把真正要看的业务字段挤出了屏幕。
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
      title: '期号 / 玩法',
      dataIndex: 'issueNo',
      width: 200,
    },
    {
      title: '售卖窗口',
      dataIndex: 'saleWindow',
      width: 220,
    },
    {
      title: '发售进度',
      dataIndex: 'soldCount',
      width: 150,
    },
    {
      title: '开奖时间（计划/实际）',
      dataIndex: 'drawTime',
      width: 250,
    },
    {
      title: '开奖号码',
      dataIndex: 'winningNumber',
      width: 120,
    },
    {
      title: '开奖状态',
      dataIndex: 'status',
      width: 100,
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      width: 120,
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      ellipsis: true,
      showFlag: false,
    },
    {
      title: '更新人',
      dataIndex: 'updateBy',
      width: 120,
      ellipsis: true,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
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

  // 后端返回 ISO 形式（2026-07-28T18:20:13），截到分钟即可 —— 秒对运营没有意义，反而占宽
  function fmt(value) {
    return value ? String(value).replace('T', ' ').slice(0, 16) : '';
  }

  function soldPercent(record) {
    if (!record.totalCount) {
      return 0;
    }
    return Math.min(100, Math.round(((record.soldCount || 0) / record.totalCount) * 100));
  }

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    lotteryCode: undefined, //彩票编码
    issueNo: undefined, //期号，模糊匹配
    status: undefined, //开奖状态: 0-待开奖, 1-核销中, 2-已开奖
    saleState: undefined, //售卖态，服务端按数据库时钟判定
    overdueOnly: undefined, //只看逾期未开奖
    todayDrawOnly: undefined, //只看今日计划开奖，服务端用 CURDATE() 判定
    planDrawTime: [], //计划开奖时间
    planDrawTimeBegin: undefined,
    planDrawTimeEnd: undefined,
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
    activeCard.value = null;
    reload();
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

  function onChangePlanDrawTime(dates, dateStrings) {
    // 结束日期补到当天 23:59:59：选「今天~今天」时若原样传 00:00:00，
    // 计划开奖时间在今天白天的期一条都筛不出来
    queryForm.planDrawTimeBegin = dateStrings[0] ? `${dateStrings[0]} 00:00:00` : undefined;
    queryForm.planDrawTimeEnd = dateStrings[1] ? `${dateStrings[1]} 23:59:59` : undefined;
  }

  // 列表与概览一起刷：停售、编辑都会改变卡片上的数字，只刷一个会让两者对不上
  async function reload() {
    await Promise.all([queryData(), loadOverview()]);
  }

  onMounted(() => {
    reload();
    loadLotteryOptions();
  });

  // ---------------------------- 巡检概览 ----------------------------

  const overview = ref(null);
  // 当前被点亮的卡片，null 表示没按卡片筛
  const activeCard = ref(null);

  /*
   * 卡片顺序 = 紧急程度。逾期未开奖排第一：到点没开奖是要赔付的，
   * 比「有多少期在卖」重要得多，它也是这一页存在的理由。
   */
  const overviewCards = computed(() => [
    {
      key: 'overdue',
      label: '逾期未开奖',
      tone: 'tone-danger',
      value: overview.value?.overdueCount,
      hint: '计划开奖时间已过、却还没开完奖的期。含卡在「核销中」的 —— 开奖跑一半中断比压根没开更需要盯。',
      apply: (f) => (f.overdueOnly = true),
    },
    {
      key: 'onSale',
      label: '售卖中',
      tone: 'tone-primary',
      value: overview.value?.onSaleCount,
      hint: '此刻真的能领到号的期：玩法已上线、期号待开奖、落在售卖窗口内且未售罄。判定与运行态发号逐条一致。',
      apply: (f) => (f.saleState = SALE_STATE_ENUM.ON_SALE.value),
    },
    {
      key: 'soldOut',
      label: '已售罄',
      tone: 'tone-warning',
      value: overview.value?.soldOutCount,
      hint: '已发数达到发行上限，窗口还没到点但已经发不出号了。要么加量要么提前开奖。',
      apply: (f) => (f.saleState = SALE_STATE_ENUM.SOLD_OUT.value),
    },
    {
      key: 'todayDraw',
      label: '今日计划开奖',
      tone: 'tone-success',
      value: overview.value?.todayDrawCount,
      hint: '计划开奖时间落在今天、且尚未开完奖的期 —— 今天的活儿。',
      // 不在这里算「今天是哪天」：那也是一次时间判断，交给浏览器就又多了一个时钟源，
      // 客户端时区偏一格筛出来的就是昨天的活儿。传个布尔量，让数据库用 CURDATE() 判
      apply: (f) => (f.todayDrawOnly = true),
    },
  ]);

  async function loadOverview() {
    try {
      // 概览只吃玩法维度：服务端刻意忽略 status / saleState / overdueOnly，
      // 否则点了「逾期」之后其余三张卡会全部归零，没法互相对照
      const res = await lotteryIssueApi.overview({ lotteryCode: queryForm.lotteryCode, pageNum: 1, pageSize: 1 });
      overview.value = res.data;
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  /**
   * 点卡片即按该口径筛，再点一下取消。
   *
   * 切卡片前先清掉所有卡片可能设过的条件 —— 否则「逾期」叠上「售卖中」
   * 会筛出一个既逾期又在售的空集，运营只会看到一片空白，不知道是自己叠错了条件。
   */
  function toggleCard(card) {
    const turningOff = activeCard.value === card.key;
    queryForm.overdueOnly = undefined;
    queryForm.todayDrawOnly = undefined;
    queryForm.saleState = undefined;

    if (turningOff) {
      activeCard.value = null;
    } else {
      card.apply(queryForm);
      activeCard.value = card.key;
    }
    onSearch();
  }

  // ---------------------------- 玩法下拉 ----------------------------

  const lotteryOptions = ref([]);
  const lotteryLoading = ref(false);

  /*
   * 原先「彩票编码」是个手输入框，而 mapper 里是 lottery_code = #{} 精确匹配 ——
   * 少打一个字符就是空列表，还看不出是自己打错了。改成下拉，从彩票配置现拉。
   * 配置表本身就是几十条量级，一次拉回即可。
   */
  async function loadLotteryOptions() {
    lotteryLoading.value = true;
    try {
      const res = await lotteryConfigApi.queryPage({ pageNum: 1, pageSize: 200 });
      lotteryOptions.value = (res.data?.list || []).map((item) => ({
        value: item.lotteryCode,
        label: `${item.lotteryName}（${item.lotteryCode}）`,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      lotteryLoading.value = false;
    }
  }

  // ---------------------------- 跳转工作台 ----------------------------

  /**
   * 开奖、派奖、核销进度都在工作台，本页只负责把人准确送过去。
   * 带 activityCode + lotteryCode 深链，落地即定位到这个玩法的期号 Tab，
   * 不用运营到了工作台再自己找一遍活动和玩法。
   */
  function gotoWorkbench(record) {
    router.push({
      path: '/business/lottery/lottery-workbench',
      query: {
        activityCode: record.activityCode,
        lotteryCode: record.lotteryCode,
        tab: 'issue',
      },
    });
  }

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
      reload();
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
      reload();
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
    cursor: pointer;
    background: #fff;
    border: 1px solid #e2e8f0;
    border-left: 4px solid #cbd5e1;
    border-radius: 6px;
    transition: all 0.2s;

    &:hover {
      box-shadow: 0 2px 10px rgb(15 23 42 / 8%);
    }

    &.active {
      background: #f8fafc;
      box-shadow: 0 2px 10px rgb(15 23 42 / 10%);
    }
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

  .overview-card-foot {
    margin-top: 2px;
    font-size: 11px;
    color: #cbd5e1;
  }

  .tone-danger {
    border-left-color: #ef4444;

    .overview-card-value {
      color: #dc2626;
    }
  }
  .tone-primary {
    border-left-color: #3b82f6;
  }
  .tone-warning {
    border-left-color: #f59e0b;
  }
  .tone-success {
    border-left-color: #10b981;
  }

  .cell-stack {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .cell-sub {
    font-size: 11px;
    color: #94a3b8;
  }

  .cell-label {
    margin-right: 4px;
    color: #cbd5e1;
  }

  .cell-tag {
    margin: 0;
    align-self: flex-start;
  }

  .text-overdue {
    font-weight: 600;
    color: #dc2626;
  }

  .text-winning {
    font-size: 14px;
    color: #dc2626;
    letter-spacing: 2px;
  }

  // 逾期行整行浅红：卡片给的是数量，进了列表还得一眼认出是哪几行
  :deep(.row-overdue) > td {
    background-color: #fef2f2;
  }
</style>
