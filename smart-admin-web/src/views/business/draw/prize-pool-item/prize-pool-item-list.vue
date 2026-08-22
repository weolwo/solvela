<!--
  * 奖项库存看板
  *
  * 定位：跨活动的奖项库存监控，<b>只读</b>。
  *
  * ⚠️ 本页不提供新建/编辑/删除，服务端也已下掉那四个接口。最要命的一条：
  * 原先的表单直接开放 usedStock —— 跨奖池累计已出数量、库存对账的基准。
  * 工作台落库处明写「used_stock/version 永不接受前端值」，那条路径却照单全收。
  * 手改一个数，DB 账目当场错乱，而运行态真正用来预扣的 Redis 剩余量根本不会跟着变。
  *
  * 本页最核心的一列是「两个口径的剩余库存」：
  * 运行态扣减是 Redis Lua 预扣 + DB 条件更新兜底，决定「还能不能抽到」的是 Redis 那个数。
  * 两者漂移意味着可能超发或少发，是要立刻处理的事故 ——
  * 而原页面只显示 DB 口径，看到的是个跟运行态无关的安慰性数字。
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 09:52:45
  * @Copyright  weolwo
-->
<template>
  <!---------- 概览 begin ----------->
  <a-row :gutter="12" class="overview-row">
    <a-col :span="6">
      <div class="overview-card tone-critical">
        <div class="overview-card-label">
          库存口径漂移
          <a-tooltip title="Redis 剩余与 DB 剩余对不上的奖项数。运行态按 Redis 预扣，DB 只是兜底对账 —— 漂移意味着可能超发或少发">
            <QuestionCircleOutlined class="overview-card-hint" />
          </a-tooltip>
        </div>
        <div class="overview-card-value">{{ result.driftCount ?? '-' }}</div>
        <div class="overview-card-foot">Redis 与 DB 剩余对不上</div>
      </div>
    </a-col>
    <a-col :span="6">
      <div class="overview-card tone-danger" :class="{ active: queryForm.onlyIssue }" @click="toggleOnlyIssue">
        <div class="overview-card-label">体检告警</div>
        <div class="overview-card-value">
          {{ result.dangerCount ?? '-' }}
          <span v-if="result.warnCount > 0" class="overview-card-sub">+{{ result.warnCount }} 警告</span>
        </div>
        <div class="overview-card-foot">{{ queryForm.onlyIssue ? '已筛选 · 再点取消' : '点击只看有问题的奖项' }}</div>
      </div>
    </a-col>
    <a-col :span="6">
      <div class="overview-card tone-warning">
        <div class="overview-card-label">已抽空</div>
        <div class="overview-card-value">{{ result.soldOutCount ?? '-' }}</div>
        <div class="overview-card-foot">共 {{ result.itemCount ?? '-' }} 个奖项</div>
      </div>
    </a-col>
    <a-col :span="6">
      <div class="overview-card tone-primary">
        <div class="overview-card-label">
          已发出 / 剩余敞口
          <a-tooltip title="已发出 = 已出数量 × 单价；剩余敞口 = DB剩余 × 单价，即库存全部抽完最坏还要赔多少">
            <QuestionCircleOutlined class="overview-card-hint" />
          </a-tooltip>
        </div>
        <div class="overview-card-value overview-card-dual">
          <span>￥{{ money(result.totalIssuedValue) }}</span>
          <span class="overview-card-slash">/</span>
          <span class="overview-card-exposure">￥{{ money(result.totalRemainValue) }}</span>
        </div>
        <div class="overview-card-foot">已赔付 / 最坏还要赔</div>
      </div>
    </a-col>
  </a-row>
  <!---------- 概览 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <!-- 活动编码是十位随机码，手输等于必须先去别处抄一遍，而且 mapper 里是精确匹配，
           少打一个字符就是空列表还看不出是自己打错了 —— 改成下拉 -->
      <a-form-item label="活动" class="smart-query-form-item">
        <a-select
          style="width: 280px"
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
      <a-form-item class="smart-query-form-item">
        <a-checkbox v-model:checked="queryForm.onlyIssue" @change="onSearch">只看有体检告警的奖项</a-checkbox>
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
    <a-alert type="info" show-icon class="mb-3">
      <template #message><span class="text-xs">奖项与库存只能在「抽奖工作台」修改，本页只做监控与体检</span></template>
      <template #description>
        <span class="text-xs">
          已出数量（used_stock）是库存对账的基准，永远不该由人手填 —— 本页刻意不提供编辑入口。
          库存要补货请到工作台追加总库存（活动已上线时只允许追加，不允许缩减）。
        </span>
      </template>
    </a-alert>

    <a-table
      size="small"
      :scroll="{ x: 1400, y: 800 }"
      :dataSource="list"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="loading"
      :pagination="false"
      :row-class-name="(record) => (hasDanger(record) ? 'row-danger' : '')"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'prizeCode'">
          <div class="cell-stack">
            <span v-if="record.prizeName">{{ prizeIcon(record.prizeType) }} {{ record.prizeName }}</span>
            <span v-else class="text-dead">奖品缺失</span>
            <span class="cell-sub font-mono">{{ record.prizeCode }}</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'activityCode'">
          <div class="cell-stack">
            <span class="font-mono text-xs">{{ record.activityCode }}</span>
            <a-tag
              v-if="record.activityStatus != null"
              :color="record.activityStatus === 1 ? 'green' : 'default'"
              class="cell-tag"
            >
              {{ record.activityStatus === 1 ? '已上线' : '未上线' }}
            </a-tag>
          </div>
        </template>

        <!-- 库存双口径：Redis 是运行态真相，DB 是兜底对账。并排放才看得出漂移 -->
        <template v-if="column.dataIndex === 'stock'">
          <div v-if="record.totalStock === -1" class="cell-sub">不限量</div>
          <div v-else class="cell-stack">
            <div>
              <span class="cell-label">Redis</span>
              <span :class="cacheClass(record)">{{ record.remainStockCache ?? '未预热' }}</span>
              <a-tag v-if="record.stockDrift" color="red" class="cell-tag ml-1">
                漂移 {{ record.stockDrift > 0 ? '+' : '' }}{{ record.stockDrift }}
              </a-tag>
            </div>
            <div class="cell-sub"><span class="cell-label">DB</span>{{ record.remainStockDb }} / 总 {{ record.totalStock }}</div>
          </div>
        </template>

        <!-- 消耗率带进度条：光看「已出 315」判断不了快不快，要有分母和视觉长度 -->
        <template v-if="column.dataIndex === 'usedRate'">
          <div v-if="record.usedRate == null" class="cell-sub">—</div>
          <div v-else class="cell-stack">
            <a-progress
              :percent="ratePercent(record.usedRate)"
              :status="ratePercent(record.usedRate) >= 100 ? 'exception' : 'normal'"
              size="small"
              :show-info="false"
            />
            <span class="cell-sub">已出 {{ record.usedStock }} · {{ ratePercent(record.usedRate) }}%</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'value'">
          <div class="cell-stack">
            <span class="cell-sub">已发 ￥{{ money(record.issuedValue) }}</span>
            <span :class="Number(record.remainValue || 0) > 0 ? 'text-exposure' : 'cell-sub'">
              敞口 ￥{{ money(record.remainValue) }}
            </span>
          </div>
        </template>

        <!-- 库存跨奖池共享：A 池抽空了 B 池同一个奖也没了，这是那个问题的答案 -->
        <template v-if="column.dataIndex === 'poolCodeList'">
          <div v-if="record.poolCodeList.length === 0" class="text-warn text-xs">无奖池引用</div>
          <div v-else class="cell-stack">
            <a-tag v-for="code in record.poolCodeList" :key="code" class="cell-tag font-mono">{{ code }}</a-tag>
            <span v-if="record.poolCodeList.length > 1" class="cell-sub">库存在这 {{ record.poolCodeList.length }} 个池间共享</span>
          </div>
        </template>

        <template v-if="column.dataIndex === 'limit'">
          <div class="cell-stack">
            <span>{{ record.userMaxCount === -1 ? '不限' : `每人 ${record.userMaxCount} 次` }}</span>
            <span v-if="record.whiteListCount === null" class="text-dead text-xs">白名单解析失败</span>
            <span v-else-if="record.whiteListCount > 0" class="cell-sub">白名单 {{ record.whiteListCount }} 人必中</span>
          </div>
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
      </template>
    </a-table>

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
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { prizePoolItemApi } from '/src/api/business/draw/prize-pool-item-api';
  import { activityConfigApi } from '/src/api/business/activity/activity-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { prizeIcon } from '/@/constants/business/lottery/lottery-const';

  // 与抽奖工作台同源：活动下拉只取抽奖类活动
  const ACTIVITY_TYPE_DRAW = 'DRAW';

  /*
   * 列的排布按「从左往右读一遍就知道这个奖还剩多少、烧得快不快、要赔多少」来定。
   * 剩余库存放在消耗率前面：先回答「还有没有」，再回答「烧得多快」。
   * id / 租户id / 创建人 等审计列不再单列 —— 排查时走原始行接口。
   */
  const columns = [
    { title: '奖品', dataIndex: 'prizeCode', width: 220 },
    { title: '所属活动', dataIndex: 'activityCode', width: 140 },
    { title: '剩余库存', dataIndex: 'stock', width: 190 },
    { title: '消耗率', dataIndex: 'usedRate', width: 150 },
    { title: '价值', dataIndex: 'value', width: 150 },
    { title: '被哪些奖池引用', dataIndex: 'poolCodeList', width: 180 },
    { title: '限领 / 白名单', dataIndex: 'limit', width: 140 },
    { title: '体检', dataIndex: 'issue', width: 220 },
  ];

  // ---------------------------- 展示辅助 ----------------------------

  function money(value) {
    if (value == null) {
      return '0.00';
    }
    return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function ratePercent(rate) {
    return Math.min(100, Math.round(Number(rate || 0) * 100));
  }

  function hasDanger(record) {
    return record.issueList?.some((i) => i.level === 'DANGER');
  }

  function shortIssue(message) {
    const cut = message.indexOf('：');
    return cut > 0 ? message.slice(0, cut) : message.slice(0, 12);
  }

  function cacheClass(record) {
    if (record.remainStockCache == null) {
      return 'cell-sub';
    }
    if (record.stockDrift) {
      return 'text-dead';
    }
    return record.remainStockCache <= 0 ? 'text-warn' : 'font-bold';
  }

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    activityCode: undefined,
    onlyIssue: false,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const loading = ref(false);
  const result = ref({});
  const list = ref([]);
  const total = ref(0);

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
    loading.value = true;
    try {
      // 概览与明细同一次请求返回，卡片数字与列表必然一致
      const res = await prizePoolItemApi.stockBoard(queryForm);
      result.value = res.data || {};
      list.value = res.data?.list || [];
      total.value = res.data?.total || 0;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      loading.value = false;
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
   * 而本页恰恰要能查到这些活动下的奖项 —— 库存对不上、奖品被停用这类问题
   * 往往就躺在那些活动里，拿不到选项就等于永远看不见。
   */
  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList(ACTIVITY_TYPE_DRAW, true);
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

  .tone-critical {
    background: #fef2f2;
    border-left-color: #b91c1c;

    .overview-card-value {
      color: #b91c1c;
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

  .tone-warning {
    border-left-color: #f59e0b;

    .overview-card-value {
      color: #d97706;
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

  .overview-card-dual {
    font-size: 18px;
  }

  .overview-card-slash {
    margin: 0 6px;
    color: #cbd5e1;
  }

  .overview-card-exposure {
    color: #d97706;
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

  .cell-label {
    margin-right: 4px;
    font-size: 11px;
    color: #cbd5e1;
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
    font-weight: 600;
    color: #d97706;
  }

  .text-exposure {
    font-size: 11px;
    font-weight: 600;
    color: #d97706;
  }

  .text-ok {
    color: #10b981;
  }

  :deep(.row-danger) > td {
    background-color: #fef2f2;
  }
</style>
