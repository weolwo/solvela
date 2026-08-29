<!--
  * 抽奖工作台 · Tab2 奖池与概率配置 V2（多奖池，t_prize_pool_config + t_pool_prize_mapping）
  *
  * V2 核心：多奖池隔离
  * 1. poolList 持有多个奖池；右侧表格 / 兜底配平 / 沙盘模拟器严格只操作 activePool，池间互不干扰。
  * 2. 概率累加与配平全程 decimal.js，杜绝浮点误差导致「显示 100% 却保存不了」的死局。
  * 3. isProbabilityValid() 横扫所有奖池，任一池未闭环都不允许发布。
  * 4. 库存来自 Tab1 的 prizeItems（按 prizeCode 关联，只读），沙盘模拟器据此做超发预警。
  *
  * @Author:    alaric
  * @Date:      2026-07-20
-->
<template>
  <div>
    <a-alert
      v-if="isOnline"
      type="warning"
      show-icon
      class="mb-4"
      message="活动已上线：禁止新增/删除奖池，禁止从奖池增删奖项。概率仍可调整（属运营手段），如需管控建议接入审批流。"
    />

    <a-row :gutter="16">
      <!-- ==================== 左侧：分发奖池导航 ==================== -->
      <a-col :span="5">
        <div class="mb-2 flex items-center justify-between">
          <span class="font-semibold">分发奖池</span>
          <a-tag color="blue">{{ poolList.length }}</a-tag>
        </div>

        <a-menu :selected-keys="[activePoolKey]" mode="inline" class="rounded border-r-0 shadow-sm">
          <a-menu-item v-for="pool in poolList" :key="pool.poolCode" @click="activePoolKey = pool.poolCode">
            <div class="group flex items-center justify-between gap-2">
              <span class="truncate">{{ pool.poolName }}</span>
              <span class="flex shrink-0 items-center gap-1">
                <!-- 每个池独立的概率闭环状态，一眼看出哪个池没配好 -->
                <a-tooltip :title="poolStatusOf(pool) === 'ok' ? '概率已闭环' : '概率未闭环，无法发布'">
                  <a-badge :status="poolStatusOf(pool) === 'ok' ? 'success' : 'error'" />
                </a-tooltip>
                <a-popconfirm title="确认删除该奖池？池内概率配置将一并丢失" :disabled="isOnline || poolList.length <= 1" @confirm="removePool(pool)">
                  <a-button
                    type="text"
                    size="small"
                    danger
                    class="opacity-0 transition-opacity group-hover:opacity-100"
                    :disabled="isOnline || poolList.length <= 1"
                    @click.stop
                  >
                    <template #icon><DeleteOutlined /></template>
                  </a-button>
                </a-popconfirm>
              </span>
            </div>
          </a-menu-item>
        </a-menu>

        <a-tooltip :title="isOnline ? '活动已上线，禁止新增奖池' : ''">
          <a-button type="dashed" block class="mt-2" :disabled="isOnline" @click="openCreatePool">
            <template #icon><PlusOutlined /></template>
            新建奖池
          </a-button>
        </a-tooltip>
      </a-col>

      <!-- ==================== 右侧：当前奖池的概率映射 ==================== -->
      <a-col :span="19">
        <!-- 新活动尚未建池：右侧整体让位给引导，避免出现「0% 未闭环」这种无主体的报错态 -->
        <a-empty v-if="!activePool" class="py-16" description="本活动尚未创建奖池，请点击左侧「新建奖池」开始配置" />

        <template v-else>
          <div class="mb-2 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="font-semibold">转盘坑位映射</span>
              <a-input v-if="activePool" v-model:value="activePool.poolName" size="small" class="w-44" placeholder="奖池名称" />
              <span class="font-mono text-xs text-gray-400">{{ activePoolKey }}</span>
              <a-badge v-if="isDirty" status="warning" text="有未保存改动" />
            </div>
            <a-tooltip :title="isOnline ? '活动已上线，禁止增删奖项' : ''">
              <a-button type="dashed" size="small" :disabled="isOnline" @click="openDrawer">
                <template #icon><PlusOutlined /></template>
                从物资库引入奖项
              </a-button>
            </a-tooltip>
          </div>

          <a-table :data-source="activeMappings" :columns="columns" :pagination="false" row-key="prizeCode" size="small" bordered>
            <template #emptyText>
              <div class="py-6 text-gray-400">该奖池暂无奖项，请点击右上角「从物资库引入奖项」</div>
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'prizeName'">
                <span v-if="prizeMap[record.prizeCode]">{{ prizeMap[record.prizeCode].prizeName }}</span>
                <a-tooltip v-else title="该奖项已从物资库(Tab1)移除，请从奖池删除或回 Tab1 重新引入">
                  <span class="font-medium text-red-500">⚠ 物资库已移除</span>
                </a-tooltip>
              </template>

              <template v-else-if="column.dataIndex === 'prizeValue'"> ¥ {{ formatMoney(prizeMap[record.prizeCode]?.prizeValue) }} </template>

              <template v-else-if="column.dataIndex === 'remainStock'">
                <a-tag v-if="remainStockOf(record.prizeCode) === UNLIMITED" color="blue">∞ 不限量</a-tag>
                <span v-else>{{ remainStockOf(record.prizeCode) }}</span>
              </template>

              <template v-else-if="column.dataIndex === 'isFallback'">
                <a-tooltip :title="record.isFallback ? '兜底奖项：自动吃掉本池剩余概率' : '设为本池兜底奖项'">
                  <a-switch
                    size="small"
                    :checked="record.isFallback"
                    checked-children="兜底"
                    un-checked-children="固定"
                    @change="(checked) => setFallback(record.prizeCode, checked)"
                  />
                </a-tooltip>
              </template>

              <template v-else-if="column.dataIndex === 'probability'">
                <div class="flex items-center gap-2">
                  <a-input-number
                    v-model:value="record.probability"
                    :min="0"
                    :max="100"
                    :step="0.0001"
                    :precision="4"
                    :disabled="record.isFallback"
                    addon-after="%"
                    size="small"
                    class="w-36"
                  />
                  <span v-if="record.isFallback" class="whitespace-nowrap text-xs text-blue-500">(自动配平)</span>
                </div>
              </template>

              <template v-else-if="column.dataIndex === 'action'">
                <a-popconfirm title="确认从本奖池移除该奖项？" :disabled="isOnline" @confirm="removeMapping(record)">
                  <a-tooltip :title="isOnline ? '已上线，禁止移除' : ''">
                    <a-button type="link" danger size="small" :disabled="isOnline">移除</a-button>
                  </a-tooltip>
                </a-popconfirm>
              </template>
            </template>
          </a-table>

          <!-- 底部：当前池概率校验 + 模拟 + 保存 -->
          <div class="mt-4 flex items-center justify-between rounded border bg-white p-4 shadow-sm">
            <div class="mr-8 flex-1">
              <div class="mb-1 flex justify-between text-sm font-bold">
                <span>当前奖池总概率：{{ totalProbabilityText }}%</span>
                <span v-if="probabilityStatus === 'over'" class="text-red-500">❌ 概率超载，请调低固定奖项概率</span>
                <span v-else-if="probabilityStatus === 'under'" class="text-orange-500">⚠️ 概率不足，未达到 100%</span>
                <span v-else class="text-green-500">✅ 本池验证通过</span>
              </div>
              <a-progress
                :percent="progressPercent"
                :status="probabilityStatus === 'over' ? 'exception' : probabilityStatus === 'ok' ? 'success' : 'normal'"
                :stroke-width="12"
              />
              <!-- 多池全局提示：避免「当前池 OK」被误认为可以发布 -->
              <div v-if="invalidPoolNames.length > 0" class="mt-1 text-xs text-orange-500">
                另有 {{ invalidPoolNames.length }} 个奖池未闭环：{{ invalidPoolNames.join('、') }}
              </div>
            </div>
            <div class="flex gap-3">
              <a-button class="border-purple-300 bg-purple-50 text-purple-700" @click="openSimulator">🎲 沙盘模拟器</a-button>
            </div>
          </div>
        </template>
      </a-col>
    </a-row>

    <!-- 新建奖池：编码打开弹窗时已自动生成，也可手输或重新生成，规则与活动/奖品编码一致 -->
    <a-modal v-model:open="createModalOpen" title="新建奖池" ok-text="创建" cancel-text="取消" @ok="confirmCreatePool">
      <a-form layout="vertical">
        <a-form-item label="奖池名称" required>
          <a-input v-model:value="newPoolName" placeholder="如：新人专享池" :maxlength="32" @press-enter="confirmCreatePool" />
        </a-form-item>
        <a-form-item label="奖池编码" required>
          <a-input-group compact>
            <a-input
              v-model:value="newPoolCode"
              style="width: calc(100% - 96px)"
              :maxlength="10"
              placeholder="10位大写字母+数字"
              @change="onPoolCodeInput"
            />
            <a-tooltip title="重新随机生成一个未被占用的编码">
              <a-button style="width: 96px" :loading="codeGenerating" @click="generatePoolCode">重新生成</a-button>
            </a-tooltip>
          </a-input-group>
        </a-form-item>
        <a-alert type="info" show-icon message="奖池编码全局唯一且创建后不可修改；创建后请引入奖项并把总概率配满 100%。" />
      </a-form>
    </a-modal>

    <!-- 引入奖项抽屉（只影响当前奖池） -->
    <a-drawer v-model:open="drawerOpen" :title="`向「${activePool?.poolName || ''}」引入奖项`" :width="600" placement="right">
      <a-alert type="info" show-icon class="mb-3" message="仅可引入 Tab1 已配置库存的奖项；同一奖项可同时存在于不同奖池。" />
      <a-table
        :data-source="prizeItems"
        :columns="drawerColumns"
        :pagination="false"
        row-key="prizeCode"
        size="small"
        bordered
        :row-selection="rowSelection"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'prizeValue'">¥ {{ formatMoney(record.prizeValue) }}</template>
          <template v-else-if="column.dataIndex === 'remainStock'">
            <a-tag v-if="remainStockOf(record.prizeCode) === UNLIMITED" color="blue">∞ 不限量</a-tag>
            <span v-else>{{ remainStockOf(record.prizeCode) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'imported'">
            <a-tag v-if="activeCodes.has(record.prizeCode)" color="default">已在本池</a-tag>
            <span v-else class="text-gray-300">—</span>
          </template>
        </template>
      </a-table>
      <template #footer>
        <div class="flex items-center justify-between">
          <span class="text-sm text-gray-500">已选 {{ drawerSelectedKeys.length }} 项</span>
          <a-space>
            <a-button @click="drawerOpen = false">取消</a-button>
            <a-button type="primary" :disabled="drawerSelectedKeys.length === 0" @click="confirmImport">确认引入</a-button>
          </a-space>
        </div>
      </template>
    </a-drawer>

    <!-- 沙盘模拟器（只模拟当前奖池） -->
    <a-modal v-model:open="simulatorOpen" :title="`🎲 沙盘模拟器 · ${activePool?.poolName || ''}`" :width="640" :footer="null">
      <div class="mb-4 flex items-center justify-between">
        <div>
          <span>模拟抽奖次数：</span>
          <a-radio-group v-model:value="simulateTimes">
            <a-radio-button :value="1000">1千次</a-radio-button>
            <a-radio-button :value="10000">1万次</a-radio-button>
            <a-radio-button :value="100000">10万次</a-radio-button>
          </a-radio-group>
        </div>
        <a-button type="primary" @click="runSimulation">{{ simulationFinished ? '重新模拟' : '开始模拟' }}</a-button>
      </div>

      <div v-if="simulationFinished" class="rounded border bg-gray-50 p-4">
        <a-alert
          v-if="hasOverflow"
          type="error"
          show-icon
          class="mb-3"
          :message="`按此概率模拟 ${simulateTimes} 次，有 ${overflowCount} 个奖项会超发，实际投放将提前抽空、后续用户中奖却无货`"
        />
        <a-alert v-else type="success" show-icon class="mb-3" message="库存充足：当前概率下所有限量奖项的预计发放量均未超过剩余库存" />

        <h4 class="mb-3 font-bold">预计消耗物资结果：</h4>
        <div v-for="res in simulationResults" :key="res.prizeCode" class="mb-3">
          <div class="mb-1 flex justify-between text-sm">
            <span>
              {{ res.prizeName }}
              <a-tag v-if="res.overflow" color="red" class="ml-1">库存超发</a-tag>
            </span>
            <span :class="res.overflow ? 'font-bold text-red-500' : 'font-bold text-blue-600'"> {{ res.count }} 次 ({{ res.actualRatio }}%) </span>
          </div>
          <a-progress :percent="res.barPercent" :show-info="false" :stroke-color="res.overflow ? '#ef4444' : '#8b5cf6'" />
          <div v-if="res.remainStock !== UNLIMITED" class="mt-1 text-xs" :class="res.overflow ? 'text-red-500' : 'text-gray-400'">
            预计发出 {{ res.count }} / 剩余库存 {{ res.remainStock }}
            <span v-if="res.overflow">，缺口 {{ res.shortage }} 个</span>
          </div>
          <div v-else class="mt-1 text-xs text-gray-400">不限量，无超发风险</div>
        </div>
      </div>
      <div v-else class="py-8 text-center text-gray-400">点击上方「开始模拟」按钮，验证本奖池的概率模型与库存是否匹配。</div>
    </a-modal>
  </div>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { message } from 'ant-design-vue';
  import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';
  import { cloneDeep, isEqual } from 'lodash';
  import Decimal from 'decimal.js';
  import { drawWorkbenchApi } from '/@/api/business/draw/draw-workbench-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { regular } from '/@/constants/regular-const';

  // ---------------------------- Props / Emits ----------------------------

  const props = defineProps({
    isOnline: {
      type: Boolean,
      default: false,
    },
    // 物资库（来自 Tab1，按 prizeCode 关联，本组件只读）
    prizeItems: {
      type: Array,
      default: () => [],
    },
  });

  const emit = defineEmits(['change']);

  // ---------------------------- 常量 ----------------------------

  const UNLIMITED = -1;
  const HUNDRED = new Decimal(100);
  // 概率累加容差，防御浮点边界
  const PROBABILITY_EPSILON = new Decimal(0.0001);

  const columns = [
    { title: '奖项名称', dataIndex: 'prizeName' },
    { title: '价值', dataIndex: 'prizeValue', width: 100 },
    { title: '剩余库存', dataIndex: 'remainStock', width: 100 },
    { title: '兜底模式', dataIndex: 'isFallback', width: 100 },
    { title: '中奖概率', dataIndex: 'probability', width: 220 },
    { title: '操作', dataIndex: 'action', width: 70 },
  ];

  const drawerColumns = [
    { title: '奖品编码', dataIndex: 'prizeCode' },
    { title: '名称', dataIndex: 'prizeName' },
    { title: '价值', dataIndex: 'prizeValue', width: 90 },
    { title: '剩余库存', dataIndex: 'remainStock', width: 90 },
    { title: '状态', dataIndex: 'imported', width: 80 },
  ];

  // ---------------------------- 多奖池数据 ----------------------------

  const poolList = ref([]);

  const activePoolKey = ref('');
  const activePool = computed(() => poolList.value.find((pool) => pool.poolCode === activePoolKey.value));
  // 表格绑定的是 poolList 内部的源数组引用，编辑即改 poolList，触发 deep watch 配平
  const activeMappings = computed(() => activePool.value?.prizeMappingList || []);

  const savedSnapshot = ref([]);
  const isDirty = computed(() => !isEqual(poolList.value, savedSnapshot.value));

  watch(poolList, () => emit('change', { dirty: isDirty.value, poolCount: poolList.value.length }), { deep: true });

  // ---------------------------- 物资库关联（只读） ----------------------------

  const prizeMap = computed(() => {
    const map = {};
    props.prizeItems.forEach((item) => {
      map[item.prizeCode] = item;
    });
    return map;
  });

  const formatMoney = (value) => new Decimal(value || 0).toFixed(2);

  function remainStockOf(prizeCode) {
    const item = prizeMap.value[prizeCode];
    if (!item || item.totalStock === UNLIMITED) {
      return UNLIMITED;
    }
    return new Decimal(item.totalStock).minus(item.usedStock || 0).toNumber();
  }

  // ---------------------------- 概率计算（decimal.js，按池隔离） ----------------------------

  function poolTotalOf(pool) {
    return (pool?.prizeMappingList || []).reduce((sum, m) => sum.plus(new Decimal(m.probability || 0)), new Decimal(0));
  }

  function poolStatusOf(pool) {
    const diff = poolTotalOf(pool).minus(HUNDRED);
    if (diff.gt(PROBABILITY_EPSILON)) return 'over';
    if (diff.lt(PROBABILITY_EPSILON.negated())) return 'under';
    return 'ok';
  }

  // 当前池
  const totalProbability = computed(() => poolTotalOf(activePool.value));
  const totalProbabilityText = computed(() => totalProbability.value.toFixed(4));
  const probabilityStatus = computed(() => poolStatusOf(activePool.value));
  const progressPercent = computed(() => (probabilityStatus.value === 'over' ? 100 : Math.min(totalProbability.value.toNumber(), 100)));

  // 全局（所有池）
  const allPoolsValid = computed(() => poolList.value.every((pool) => poolStatusOf(pool) === 'ok'));
  const invalidPoolNames = computed(() =>
    poolList.value.filter((pool) => pool.poolCode !== activePoolKey.value && poolStatusOf(pool) !== 'ok').map((pool) => pool.poolName)
  );

  // ---------------------------- 兜底自动配平（每个池独立） ----------------------------

  function balancePool(pool) {
    let sumNormal = new Decimal(0);
    let fallback = null;
    pool.prizeMappingList.forEach((m) => {
      if (m.isFallback) {
        fallback = m;
      } else {
        sumNormal = sumNormal.plus(new Decimal(m.probability || 0));
      }
    });
    if (fallback) {
      const remain = HUNDRED.minus(sumNormal);
      const value = remain.gte(0) ? remain.toNumber() : 0;
      // 守卫：值未变则不写，避免 deep watch 死循环
      if (fallback.probability !== value) {
        fallback.probability = value;
      }
    }
  }

  watch(
    poolList,
    () => {
      // 对所有池执行配平（幂等），保证切池后各池数据始终正确
      poolList.value.forEach((pool) => balancePool(pool));
    },
    { deep: true }
  );

  // 兜底在「本池内」唯一
  function setFallback(prizeCode, checked) {
    if (!activePool.value) {
      return;
    }
    activePool.value.prizeMappingList.forEach((m) => {
      m.isFallback = m.prizeCode === prizeCode ? checked : false;
    });
  }

  function removeMapping(record) {
    if (!activePool.value) {
      return;
    }
    const list = activePool.value.prizeMappingList;
    const index = list.findIndex((m) => m.prizeCode === record.prizeCode);
    if (index > -1) {
      list.splice(index, 1);
    }
  }

  // ---------------------------- 奖池增删 ----------------------------

  const createModalOpen = ref(false);
  const newPoolName = ref('');
  const newPoolCode = ref('');
  const codeGenerating = ref(false);

  function openCreatePool() {
    newPoolName.value = '';
    newPoolCode.value = '';
    createModalOpen.value = true;
    // 打开即预填一个编码，运营不想操心编码时可以直接过
    generatePoolCode();
  }

  // 手输时统一转大写，省得运营因为小写被规则拦下来
  function onPoolCodeInput() {
    if (newPoolCode.value) {
      newPoolCode.value = newPoolCode.value.toUpperCase();
    }
  }

  async function generatePoolCode() {
    codeGenerating.value = true;
    try {
      const res = await drawWorkbenchApi.generatePoolCode();
      newPoolCode.value = res;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      codeGenerating.value = false;
    }
  }

  function confirmCreatePool() {
    const name = newPoolName.value.trim();
    if (!name) {
      message.error('请输入奖池名称');
      return;
    }
    const poolCode = (newPoolCode.value || '').trim();
    if (!regular.bizCode.test(poolCode)) {
      message.error(regular.bizCodeDesc);
      return;
    }
    // 本地重名拦截；跨活动的占用由服务端保存时判（uk_pool_code 是全局唯一）
    if (poolList.value.some((pool) => pool.poolCode === poolCode)) {
      message.error('该奖池编码在本活动内已存在');
      return;
    }
    poolList.value.push({ poolCode, poolName: name, prizeMappingList: [] });
    activePoolKey.value = poolCode;
    createModalOpen.value = false;
    message.success('奖池已创建，请引入奖项并把总概率配满 100%');
  }

  function removePool(pool) {
    if (poolList.value.length <= 1) {
      message.error('至少保留一个奖池');
      return;
    }
    const index = poolList.value.findIndex((item) => item.poolCode === pool.poolCode);
    if (index > -1) {
      poolList.value.splice(index, 1);
    }
    // 删掉的正是当前选中池时，回落到第一个
    if (activePoolKey.value === pool.poolCode) {
      activePoolKey.value = poolList.value[0].poolCode;
    }
    message.success('奖池已删除');
  }

  // ---------------------------- 引入奖项（只影响当前池） ----------------------------

  const drawerOpen = ref(false);
  const drawerSelectedKeys = ref([]);

  const activeCodes = computed(() => new Set(activeMappings.value.map((m) => m.prizeCode)));

  const rowSelection = computed(() => ({
    selectedRowKeys: drawerSelectedKeys.value,
    onChange: (keys) => {
      drawerSelectedKeys.value = keys;
    },
    getCheckboxProps: (record) => ({
      // 只在「本池」内去重：同一奖项允许出现在不同奖池
      disabled: activeCodes.value.has(record.prizeCode),
    }),
  }));

  function openDrawer() {
    drawerSelectedKeys.value = [];
    drawerOpen.value = true;
  }

  function confirmImport() {
    if (!activePool.value) {
      return;
    }
    const toAdd = drawerSelectedKeys.value.filter((code) => !activeCodes.value.has(code));
    toAdd.forEach((code) => activePool.value.prizeMappingList.push({ prizeCode: code, probability: 0, isFallback: false }));
    message.success(`已向「${activePool.value.poolName}」引入 ${toAdd.length} 个奖项`);
    drawerOpen.value = false;
    drawerSelectedKeys.value = [];
  }

  // ---------------------------- 沙盘模拟器（只模拟当前池） ----------------------------

  const simulatorOpen = ref(false);
  const simulateTimes = ref(100000);
  const simulationFinished = ref(false);
  const simulationResults = ref([]);

  const overflowCount = computed(() => simulationResults.value.filter((r) => r.overflow).length);
  const hasOverflow = computed(() => overflowCount.value > 0);

  function resetSimulation() {
    simulationFinished.value = false;
    simulationResults.value = [];
  }

  // 切换奖池必须清空上一个池的模拟结果，否则会张冠李戴
  watch(activePoolKey, resetSimulation);

  function openSimulator() {
    resetSimulation();
    simulatorOpen.value = true;
  }

  function runSimulation() {
    if (probabilityStatus.value !== 'ok') {
      message.error('当前奖池总概率必须等于 100% 才能模拟！');
      return;
    }

    // 概率区间用 decimal 累加保证边界精确，命中比较用 number
    let acc = new Decimal(0);
    const ranges = activeMappings.value.map((m) => {
      const min = acc.toNumber();
      acc = acc.plus(new Decimal(m.probability || 0));
      return { prizeCode: m.prizeCode, min, max: acc.toNumber() };
    });

    const counter = {};
    activeMappings.value.forEach((m) => (counter[m.prizeCode] = 0));

    for (let i = 0; i < simulateTimes.value; i++) {
      const rand = Math.random() * 100;
      const hit = ranges.find((r) => rand >= r.min && rand < r.max);
      if (hit) {
        counter[hit.prizeCode] += 1;
      }
    }

    let maxCount = 0;
    activeMappings.value.forEach((m) => {
      if (counter[m.prizeCode] > maxCount) {
        maxCount = counter[m.prizeCode];
      }
    });

    simulationResults.value = activeMappings.value.map((m) => {
      const count = counter[m.prizeCode];
      const remainStock = remainStockOf(m.prizeCode);
      const unlimited = remainStock === UNLIMITED;
      const overflow = !unlimited && count > remainStock;
      return {
        prizeCode: m.prizeCode,
        prizeName: prizeMap.value[m.prizeCode]?.prizeName || m.prizeCode,
        count,
        actualRatio: new Decimal(count).div(simulateTimes.value).mul(100).toFixed(4),
        barPercent: maxCount > 0 ? new Decimal(count).div(maxCount).mul(100).toNumber() : 0,
        remainStock,
        overflow,
        shortage: overflow ? new Decimal(count).minus(remainStock).toNumber() : 0,
      };
    });
    simulationFinished.value = true;
    message.success(`已完成 ${simulateTimes.value} 次抽奖模拟`);
  }

  // ---------------------------- 对外暴露 ----------------------------

  defineExpose({
    /**
     * 回显：填回后端 workbench/detail 的 poolList
     * 后端 BigDecimal 统一序列化为字符串（JsonConfig），此处归一化为 number，
     * 否则概率输入框拿到字符串、且首次自动配平会把配置误判成「有未保存改动」
     */
    setData: (list) => {
      poolList.value = (list || []).map((pool) => ({
        poolCode: pool.poolCode,
        poolName: pool.poolName,
        prizeMappingList: (pool.prizeMappingList || []).map((mapping) => ({
          prizeCode: mapping.prizeCode,
          probability: Number(mapping.probability ?? 0),
          isFallback: Boolean(mapping.isFallback),
        })),
      }));
      // 先配平再取基线：服务端数据本已闭环，配平是幂等的，但必须赶在快照之前跑完
      poolList.value.forEach((pool) => balancePool(pool));
      activePoolKey.value = poolList.value.length > 0 ? poolList.value[0].poolCode : '';
      savedSnapshot.value = cloneDeep(poolList.value);
      resetSimulation();
    },
    // 返回完整的多奖池数组
    getData: () => cloneDeep(poolList.value),
    isDirty: () => isDirty.value,
    // 所有奖池均需闭环才允许发布
    isProbabilityValid: () => allPoolsValid.value,
  });
</script>
