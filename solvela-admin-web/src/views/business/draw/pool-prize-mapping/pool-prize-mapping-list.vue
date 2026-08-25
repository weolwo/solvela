<!--
  * 奖池概率结构分析
  *
  * 定位：跨奖池的概率结构、赔付模型与配置体检，<b>只读</b>。
  *
  * 与「抽奖工作台」的分工：
  *   - 工作台 = 单个活动的奖池纵深编辑（拖坑位、调概率、绑资产）；
  *   - 本页   = 所有奖池横向看「谁的概率没闭环、谁抽一次要赔多少、库存两个口径对不对得上」。
  *
  * ⚠️ 本页不提供新建/编辑/删除，服务端也已下掉那四个接口。原因是这条写入路径能造成
  * 全模块最严重的后果：把任意一条概率改得让整池总和 ≠ 100%，DrawPoolSnapshot 构造
  * 就抛 IllegalArgumentException，而抽奖执行链路<b>没有捕获它</b> ——
  * 该奖池的每一次抽奖请求都会直接报错。一个下拉框就是一个线上开关。
  *
  * @Author:    weolwo
  * @Date:      2026-04-19 10:07:03
  * @Copyright  weolwo
-->
<template>
  <!---------- 概览 begin ----------->
  <a-row :gutter="12" class="overview-row">
    <a-col :span="6">
      <!-- 未闭环单独占一张卡：它不是「配置可疑」，是「这个池现在按下抽奖就报错」，
           严重程度和其他告警不是一个量级 -->
      <div class="overview-card tone-critical">
        <div class="overview-card-label">
          概率未闭环
          <a-tooltip title="概率总和不等于 100% 的奖池。抽奖时快照构造抛异常且执行链路未捕获，该奖池的每一次抽奖请求都会直接报错">
            <QuestionCircleOutlined class="overview-card-hint" />
          </a-tooltip>
        </div>
        <div class="overview-card-value">{{ result.brokenPoolCount ?? '-' }}</div>
        <div class="overview-card-foot">这些奖池现在抽奖必报错</div>
      </div>
    </a-col>
    <a-col :span="6">
      <div class="overview-card tone-danger" :class="{ active: queryForm.onlyIssue }" @click="toggleOnlyIssue">
        <div class="overview-card-label">体检告警</div>
        <div class="overview-card-value">
          {{ result.dangerCount ?? '-' }}
          <span v-if="result.warnCount > 0" class="overview-card-sub">+{{ result.warnCount }} 警告</span>
        </div>
        <div class="overview-card-foot">{{ queryForm.onlyIssue ? '已筛选 · 再点取消' : '点击只看有问题的奖池' }}</div>
      </div>
    </a-col>
    <a-col :span="6">
      <div class="overview-card tone-primary">
        <div class="overview-card-label">奖池</div>
        <div class="overview-card-value">{{ result.poolCount ?? '-' }}</div>
        <div class="overview-card-foot">共 {{ result.slotCount ?? '-' }} 个坑位</div>
      </div>
    </a-col>
    <a-col :span="6">
      <div class="overview-card tone-warning">
        <div class="overview-card-label">
          单抽期望赔付合计
          <a-tooltip title="每个奖池抽一次的期望赔付之和 = Σ(坑位概率 × 奖品单价)。跨池相加只用于横向比较量级">
            <QuestionCircleOutlined class="overview-card-hint" />
          </a-tooltip>
        </div>
        <div class="overview-card-value">￥{{ money(result.totalExpectedCostPerDraw) }}</div>
        <div class="overview-card-foot">各池单抽期望之和</div>
      </div>
    </a-col>
  </a-row>
  <!---------- 概览 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <!-- 先选活动再选奖池：奖池编码是十位随机码，脱离活动没人认得出是哪个池 -->
      <a-form-item label="活动" class="solvela-query-form-item">
        <a-select
          style="width: 260px"
          v-model:value="queryForm.activityCode"
          :options="activityOptions"
          :loading="activityLoading"
          placeholder="全部活动"
          allowClear
          show-search
          option-filter-prop="label"
          @change="onActivityChange"
        />
      </a-form-item>
      <a-form-item label="奖池" class="solvela-query-form-item">
        <a-select
          style="width: 260px"
          v-model:value="queryForm.poolCode"
          :options="poolOptions"
          :loading="poolLoading"
          :placeholder="queryForm.activityCode ? '本活动全部奖池' : '全部奖池'"
          allowClear
          show-search
          option-filter-prop="label"
          @change="onSearch"
        />
      </a-form-item>
      <a-form-item class="solvela-query-form-item">
        <a-checkbox v-model:checked="queryForm.onlyIssue" @change="onSearch">只看有体检告警的奖池</a-checkbox>
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

  <a-spin :spinning="loading">
    <a-alert type="info" show-icon class="mb-3">
      <template #message><span class="text-xs">坑位与概率只能在「抽奖工作台」修改，本页只做分析与体检</span></template>
      <template #description>
        <span class="text-xs">
          工作台按池整表重建坑位，所以任何其他写入路径都会被覆盖；而概率一旦没闭环，该奖池的抽奖请求会直接报错 —— 本页刻意不提供编辑入口。
        </span>
      </template>
    </a-alert>

    <a-empty v-if="!loading && list.length === 0" class="py-16">
      <template #description>
        <span class="text-sm text-slate-400">
          {{ queryForm.onlyIssue ? '当前筛选下没有体检告警 —— 奖池配置是干净的' : '还没有任何奖池配置坑位' }}
        </span>
      </template>
    </a-empty>

    <a-card
      v-for="pool in list"
      :key="pool.poolCode"
      size="small"
      :bordered="false"
      :hoverable="true"
      class="pool-card"
      :class="{ 'has-danger': pool.dangerCount > 0, 'is-broken': !pool.probabilityClosed }"
    >
      <div class="pool-head">
        <div class="pool-title">
          <span class="pool-name">{{ pool.poolName || '（奖池配置已缺失）' }}</span>
          <span class="pool-code">{{ pool.poolCode }}</span>
          <a-tag v-if="pool.activityStatus != null" :color="pool.activityStatus === 1 ? 'green' : 'default'">
            活动{{ pool.activityStatus === 1 ? '已上线' : '未上线' }}
          </a-tag>
          <a-tag v-if="pool.poolStatus != null" :color="poolStatusOf(pool.poolStatus).color"> 奖池{{ poolStatusOf(pool.poolStatus).desc }} </a-tag>
          <a-tag v-if="!pool.probabilityClosed" color="red">概率未闭环 · 抽奖必报错</a-tag>
          <a-tag v-else-if="pool.dangerCount > 0" color="red">{{ pool.dangerCount }} 项危险</a-tag>
          <a-tag v-else-if="pool.warnCount > 0" color="orange">{{ pool.warnCount }} 项警告</a-tag>
          <a-tag v-else color="green">体检通过</a-tag>
        </div>
        <a-button type="link" size="small" :disabled="!pool.activityCode" @click="gotoWorkbench(pool)"> 去工作台修改 → </a-button>
      </div>

      <div class="pool-metrics">
        <div class="metric">
          <div class="metric-label">坑位数</div>
          <div class="metric-value">{{ pool.slotCount }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">概率总和</div>
          <div class="metric-value" :class="pool.probabilityClosed ? '' : 'metric-broken'">{{ num(pool.probabilitySum) }}%</div>
        </div>
        <div class="metric">
          <div class="metric-label">兜底奖项</div>
          <div class="metric-value" :class="fallbackClass(pool)">
            {{ pool.fallbackCount }} 个
            <span v-if="pool.fallbackHasStock === false" class="metric-note">已抽空</span>
          </div>
        </div>
        <!-- 有货概率覆盖率：配置里的概率是静态的，库存却会被抽空。
             抽空的坑位仍占着概率区间，命中后只会降级或直接失败 ——
             所以这个数才是用户的实际体验，也是「为什么这么多人抽不到」的答案 -->
        <div class="metric">
          <div class="metric-label">
            有货概率
            <a-tooltip title="仍有库存的坑位概率之和。缺口部分命中后会降级到兜底；兜底也空了就是纯失败">
              <QuestionCircleOutlined class="metric-hint" />
            </a-tooltip>
          </div>
          <div class="metric-value" :class="availableClass(pool)">{{ num(pool.availableProbability) }}%</div>
        </div>
        <div class="metric metric-cost">
          <div class="metric-label">单抽期望赔付</div>
          <div class="metric-value">￥{{ money(pool.expectedCostPerDraw) }}</div>
        </div>
      </div>

      <!-- 概率区间彩带：把 100% 按坑位顺序切开。
           引擎判定就是「随机数落在哪一段」，这条带子是那个判定的原样可视化 ——
           哪个奖占了多大一块、有没有缺口，一眼就能看出来 -->
      <div v-if="pool.slotList.length > 0" class="prob-bar-wrap">
        <div class="prob-bar">
          <a-tooltip v-for="(slot, i) in pool.slotList" :key="slot.id" :title="slotTooltip(slot)">
            <!-- 抽空的坑位打上斜纹：它仍然占着概率区间（命中判定照旧），
                 但命中后只会降级或失败。彩带上一眼就能看出「有多少概率是空的」 -->
            <div class="prob-seg" :class="{ 'prob-seg-empty': segSoldOut(slot) }" :style="{ width: segWidth(slot), background: segColor(i, slot) }">
              <span v-if="segPercent(slot) >= 8" class="prob-seg-text">{{ num(slot.probability) }}%</span>
            </div>
          </a-tooltip>
          <!-- 未闭环时把缺口画出来：比只报一个数字直观得多 -->
          <div v-if="gapPercent(pool) > 0" class="prob-seg prob-gap" :style="{ width: gapPercent(pool) + '%' }">
            <span class="prob-seg-text">缺口 {{ num(100 - Number(pool.probabilitySum)) }}%</span>
          </div>
        </div>
        <div v-if="Number(pool.probabilitySum) > 100" class="prob-overflow">
          ⚠️ 概率总和超过 100%，超出部分的坑位永远不会被抽中（随机数最大只到 100）
        </div>
      </div>

      <a-alert v-for="(issue, idx) in pool.issueList" :key="idx" :type="issue.level === 'DANGER' ? 'error' : 'warning'" show-icon class="mb-2">
        <template #message
          ><span class="text-xs">{{ issue.message }}</span></template
        >
      </a-alert>

      <a-table
        size="small"
        :data-source="pool.slotList"
        :columns="slotColumns"
        row-key="id"
        bordered
        :pagination="false"
        :row-class-name="(record) => (hasDanger(record) ? 'row-danger' : '')"
      >
        <template #bodyCell="{ record, column }">
          <template v-if="column.dataIndex === 'sortWeight'">
            <div class="cell-stack">
              <span>#{{ record.sortWeight }}</span>
              <a-tag v-if="record.fallback" color="purple" class="cell-tag">兜底</a-tag>
            </div>
          </template>

          <!-- 命中区间是引擎的真实判定依据，表里没有这两个数，是按坑位顺序累加出来的 -->
          <template v-if="column.dataIndex === 'range'">
            <div class="cell-stack">
              <span class="font-mono">[{{ num(record.rangeMin) }}, {{ num(record.rangeMax) }})</span>
              <span class="cell-sub">概率 {{ num(record.probability) }}%</span>
            </div>
          </template>

          <template v-if="column.dataIndex === 'prizeCode'">
            <div class="cell-stack">
              <span v-if="record.prizeName">{{ prizeIcon(record.prizeType) }} {{ record.prizeName }}</span>
              <span v-else class="text-dead">奖品缺失</span>
              <span class="cell-sub font-mono">{{ record.prizeCode || `itemId=${record.prizeItemId}` }}</span>
            </div>
          </template>

          <template v-if="column.dataIndex === 'prizeValue'">
            <span class="cell-sub">{{ record.prizeValue == null ? '—' : `￥${money(record.prizeValue)}` }}</span>
          </template>

          <template v-if="column.dataIndex === 'expectedCostPerDraw'">
            <span class="font-bold">
              {{ record.expectedCostPerDraw == null ? '—' : `￥${money(record.expectedCostPerDraw)}` }}
            </span>
          </template>

          <!-- 库存双口径：运行态按 Redis 预扣，DB 只是兜底。
               两者不一致是真实事故，现有页面只显示 DB 口径，看到的是个安慰性数字 -->
          <template v-if="column.dataIndex === 'stock'">
            <div v-if="record.totalStock === -1" class="cell-sub">不限量</div>
            <div v-else class="cell-stack">
              <div>
                <span class="cell-label">Redis</span>
                <span :class="stockClass(record)">{{ record.remainStockCache ?? '未预热' }}</span>
              </div>
              <div class="cell-sub"><span class="cell-label">DB</span>{{ record.remainStockDb ?? '—' }} / 总 {{ record.totalStock ?? '—' }}</div>
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
    </a-card>

    <div v-if="list.length > 0" class="solvela-query-table-page">
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
        :show-total="(total) => `共${total}个奖池`"
      />
    </div>
  </a-spin>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { poolPrizeMappingApi } from '/src/api/business/draw/pool-prize-mapping-api';
  import { prizePoolConfigApi } from '/src/api/business/draw/prize-pool-config-api';
  import { activityConfigApi } from '/src/api/business/activity/activity-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { POOL_STATUS_ENUM } from '/src/constants/business/draw/prize-pool-config-const';
  import { prizeIcon } from '/@/constants/business/lottery/lottery-const';

  const router = useRouter();

  // 与抽奖工作台同源：活动下拉只取抽奖类活动
  const ACTIVITY_TYPE_DRAW = 'DRAW';

  /*
   * 列的排布按「从左往右读一遍就知道这个坑位有多大、发什么、还剩多少」来定。
   * 命中区间放在概率前面：区间才是引擎的判定依据，概率只是它的宽度。
   */
  const slotColumns = [
    { title: '坑位', dataIndex: 'sortWeight', width: 90 },
    { title: '命中区间', dataIndex: 'range', width: 170 },
    { title: '奖品', dataIndex: 'prizeCode', width: 200 },
    { title: '单价', dataIndex: 'prizeValue', width: 100 },
    { title: '单抽期望赔付', dataIndex: 'expectedCostPerDraw', width: 130 },
    { title: '剩余库存', dataIndex: 'stock', width: 160 },
    { title: '体检', dataIndex: 'issue', width: 200 },
  ];

  // ---------------------------- 展示辅助 ----------------------------

  function poolStatusOf(value) {
    return Object.values(POOL_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
  }

  function num(value) {
    if (value == null) {
      return '-';
    }
    // 概率配到万分位，但绝大多数是整数，去掉无意义的尾零
    return Number(value).toString();
  }

  function money(value) {
    if (value == null) {
      return '0.00';
    }
    return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function hasDanger(record) {
    return record.issueList?.some((i) => i.level === 'DANGER');
  }

  // 兜底抽空比没配兜底更糟：配了却接不住，运营还以为有降级
  function fallbackClass(pool) {
    if (pool.fallbackHasStock === false) {
      return 'metric-broken';
    }
    return pool.fallbackCount === 1 ? '' : 'metric-warn';
  }

  /*
   * 有货概率的分档按「用户体验掉到什么程度」来定，不是随便取的整数：
   *   = 100  全部奖项都有货，抽一次必得
   *   ≥ 90   偶尔降级，基本无感
   *   ≥ 60   降级已经常态化，运营该补货了
   *   < 60   多数请求靠兜底接着，兜底一空就是大面积失败
   */
  function availableClass(pool) {
    const value = Number(pool.availableProbability ?? 0);
    if (value >= 100) {
      return 'metric-ok';
    }
    if (value >= 90) {
      return '';
    }
    return value >= 60 ? 'metric-warn' : 'metric-broken';
  }

  function shortIssue(message) {
    const cut = message.indexOf('：');
    return cut > 0 ? message.slice(0, cut) : message.slice(0, 12);
  }

  function stockClass(record) {
    if (record.remainStockCache == null) {
      return 'cell-sub';
    }
    if (record.remainStockDb != null && record.remainStockCache !== record.remainStockDb) {
      return 'text-dead';
    }
    return record.remainStockCache <= 0 ? 'text-warn' : 'font-bold';
  }

  // ---------------------------- 概率彩带 ----------------------------

  function segPercent(slot) {
    return Math.max(0, Number(slot.probability || 0));
  }

  function segWidth(slot) {
    return `${segPercent(slot)}%`;
  }

  /*
   * 兜底奖项固定用紫色（与坑位标签一致），其余按顺序取色。
   * 概率为 0 的坑位画不出宽度，用条纹底色让它在表格里仍有对应关系。
   */
  const SEG_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16', '#f97316'];

  function segColor(index, slot) {
    if (slot.fallback) {
      return '#a855f7';
    }
    return SEG_COLORS[index % SEG_COLORS.length];
  }

  function segSoldOut(slot) {
    return slot.remainStockDb != null && slot.remainStockDb <= 0;
  }

  function gapPercent(pool) {
    const sum = Number(pool.probabilitySum || 0);
    return sum < 100 ? 100 - sum : 0;
  }

  function slotTooltip(slot) {
    const name = slot.prizeName || slot.prizeCode || `itemId=${slot.prizeItemId}`;
    return `${name} · 概率 ${num(slot.probability)}% · 命中区间 [${num(slot.rangeMin)}, ${num(slot.rangeMax)})`;
  }

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    activityCode: undefined,
    poolCode: undefined,
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
    // 重置回「全部活动」，奖池下拉也要跟着放开，否则会留着上一个活动的选项
    loadPoolOptions();
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
      const res = await poolPrizeMappingApi.analysis(queryForm);
      result.value = res.data || {};
      list.value = res.data?.list || [];
      total.value = res.data?.total || 0;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  onMounted(() => {
    queryData();
    loadActivityOptions();
    loadPoolOptions();
  });

  // ---------------------------- 活动下拉 ----------------------------

  const activityOptions = ref([]);
  const activityLoading = ref(false);

  /*
   * includeInactive 传 true：optionList 默认过滤掉已下线/已过期的活动，
   * 而本页恰恰要能查到这些活动下的奖池 —— 坏配置往往就躺在那些活动里，
   * 拿不到选项就等于永远看不见（彩票工作台的深链也踩过同一个坑）。
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
      solvelaSentry.captureError(e);
    } finally {
      activityLoading.value = false;
    }
  }

  /**
   * 切活动：奖池下拉跟着换一批，并默认选中第一个。
   *
   * 默认选中而不是留空，是因为「本活动全部奖池」在这个页面上意义不大 ——
   * 一个活动通常就两三个池，一次盯一个池的概率结构才看得清楚。
   * 清空活动时回到全量，此时才需要看跨活动的全景（也是发现孤儿映射的唯一入口）。
   */
  async function onActivityChange() {
    queryForm.poolCode = undefined;
    await loadPoolOptions();
    if (queryForm.activityCode && poolOptions.value.length > 0) {
      queryForm.poolCode = poolOptions.value[0].value;
    }
    onSearch();
  }

  // ---------------------------- 奖池下拉 ----------------------------

  const poolOptions = ref([]);
  const poolLoading = ref(false);

  // 奖池按当前活动收窄；未选活动时给全量
  async function loadPoolOptions() {
    poolLoading.value = true;
    try {
      const res = await prizePoolConfigApi.queryPage({
        pageNum: 1,
        pageSize: 200,
        activityCode: queryForm.activityCode || undefined,
      });
      poolOptions.value = (res.data?.list || []).map((item) => ({
        value: item.poolCode,
        label: `${item.poolName}（${item.poolCode}）`,
      }));
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      poolLoading.value = false;
    }
  }

  // ---------------------------- 跳转工作台 ----------------------------

  /**
   * 坑位与概率的唯一写入口是工作台，本页只负责把人准确送过去
   */
  function gotoWorkbench(pool) {
    router.push({
      path: '/business/draw/draw-workbench',
      query: { activityCode: pool.activityCode },
    });
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

  .pool-card {
    margin-bottom: 12px;
    border-left: 3px solid #e2e8f0;

    &.has-danger {
      border-left-color: #ef4444;
    }

    &.is-broken {
      border-left-color: #b91c1c;
      box-shadow: inset 0 0 0 1px #fecaca;
    }
  }

  .pool-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  .pool-title {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }

  .pool-name {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }

  .pool-code {
    font-family: monospace;
    font-size: 12px;
    color: #94a3b8;
  }

  .pool-metrics {
    display: flex;
    gap: 28px;
    padding: 10px 14px;
    margin-bottom: 10px;
    background: #f8fafc;
    border-radius: 6px;
  }

  .metric-label {
    font-size: 11px;
    color: #94a3b8;
  }

  .metric-value {
    font-size: 15px;
    font-weight: 600;
    color: #0f172a;
  }

  .metric-broken {
    color: #b91c1c;
  }

  .metric-warn {
    color: #d97706;
  }

  .metric-ok {
    color: #059669;
  }

  .metric-note {
    margin-left: 4px;
    font-size: 11px;
    font-weight: 500;
    color: #b91c1c;
  }

  .metric-hint {
    margin-left: 2px;
    color: #cbd5e1;
  }

  .metric-cost .metric-value {
    color: #d97706;
  }

  .prob-bar-wrap {
    margin-bottom: 12px;
  }

  .prob-bar {
    display: flex;
    height: 26px;
    overflow: hidden;
    background: #f1f5f9;
    border-radius: 4px;
  }

  .prob-seg {
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 2px;
    overflow: hidden;
    transition: opacity 0.15s;

    &:hover {
      opacity: 0.85;
    }
  }

  .prob-seg-text {
    font-size: 11px;
    font-weight: 600;
    color: #fff;
    white-space: nowrap;
  }

  // 抽空的坑位：颜色压暗 + 斜纹覆盖，仍占宽度（它确实还占着概率区间）
  .prob-seg-empty {
    position: relative;
    opacity: 0.45;

    &::after {
      position: absolute;
      inset: 0;
      content: '';
      background: repeating-linear-gradient(45deg, rgb(0 0 0 / 35%), rgb(0 0 0 / 35%) 4px, transparent 4px, transparent 8px);
    }
  }

  .prob-gap {
    background: repeating-linear-gradient(45deg, #fecaca, #fecaca 6px, #fee2e2 6px, #fee2e2 12px);

    .prob-seg-text {
      color: #b91c1c;
    }
  }

  .prob-overflow {
    margin-top: 4px;
    font-size: 11px;
    color: #b91c1c;
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
    font-size: 11px;
    color: #cbd5e1;
  }

  .cell-tag {
    margin: 0;
    align-self: flex-start;
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

  .text-ok {
    color: #10b981;
  }

  :deep(.row-danger) > td {
    background-color: #fef2f2;
  }
</style>
