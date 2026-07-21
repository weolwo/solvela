<!--
  * 抽奖工作台 · Tab1 奖项物资库（t_prize_pool_item）
  *
  * 设计要点：
  * 1. 严格 SKU 化 + 读写分离：名称/类型/价值来自资产大库(t_prize_config)，本组件只读展示；
  *    运营仅能配置抽奖专有属性 totalStock(总库存,-1不限量) 与 userMaxCount(单人限领,-1不限)。
  * 2. 资产引入：从资产大库抽屉勾选，转为本地行推入主表，不允许在此新建奖品。
  * 3. 运行期防篡改锁 isOnline：已上线只允许追加库存，禁止缩减/切不限量/移除。
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
      message="活动已上线：仅允许追加库存，禁止缩减库存、切换不限量或移除奖项。"
    />

    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span class="text-base font-semibold">奖项物资库</span>
        <a-tag color="blue">{{ mainList.length }} 个奖项</a-tag>
        <a-badge v-if="isDirty" status="warning" text="有未保存改动" />
      </div>
      <a-tooltip :title="isOnline ? '活动已上线，禁止引入新奖项' : ''">
        <a-button type="primary" :disabled="isOnline" @click="openDrawer">
          <template #icon><PlusOutlined /></template>
          从资产大库引入奖项
        </a-button>
      </a-tooltip>
    </div>

    <a-table :data-source="mainList" :columns="columns" :pagination="false" row-key="key" size="middle" bordered>
      <template #bodyCell="{ column, record }">
        <!-- 只读：奖品编码 -->
        <template v-if="column.dataIndex === 'prizeCode'">
          <span class="font-mono text-xs text-gray-500">{{ record.prizeCode }}</span>
        </template>

        <!-- 只读：类型 -->
        <template v-else-if="column.dataIndex === 'prizeType'">
          <a-tag :color="PRIZE_TYPE_MAP[record.prizeType]?.color">{{ prizeTypeLabel(record.prizeType) }}</a-tag>
        </template>

        <!-- 只读：价值（decimal.js 保证展示精度） -->
        <template v-else-if="column.dataIndex === 'prizeValue'">
          <span class="text-gray-700">¥ {{ formatMoney(record.prizeValue) }}</span>
        </template>

        <!-- 可写：总库存 -->
        <template v-else-if="column.dataIndex === 'totalStock'">
          <div class="flex items-center gap-3">
            <a-tooltip :title="isOnline ? '已上线，禁止切换不限量' : '开启后本奖项不限库存'">
              <a-switch
                size="small"
                :checked="record.totalStock === UNLIMITED"
                :disabled="isOnline"
                checked-children="不限"
                un-checked-children="限量"
                @change="(checked) => toggleUnlimitedStock(record, checked)"
              />
            </a-tooltip>

            <a-tag v-if="record.totalStock === UNLIMITED" color="blue">∞ 不限量</a-tag>
            <div v-else class="flex items-center gap-1 text-sm">
              <span class="text-gray-400">已发 {{ record.usedStock }} /</span>
              <a-input-number
                v-model:value="record.totalStock"
                :min="stockMin(record)"
                :precision="0"
                size="small"
                class="w-24"
              />
            </div>
          </div>
        </template>

        <!-- 可写：单人限领 -->
        <template v-else-if="column.dataIndex === 'userMaxCount'">
          <div class="flex items-center gap-3">
            <a-switch
              size="small"
              :checked="record.userMaxCount === UNLIMITED"
              checked-children="不限"
              un-checked-children="限次"
              @change="(checked) => toggleUnlimitedLimit(record, checked)"
            />
            <a-tag v-if="record.userMaxCount === UNLIMITED" color="blue">不限</a-tag>
            <a-input-number v-else v-model:value="record.userMaxCount" :min="1" :precision="0" size="small" class="w-20" />
          </div>
        </template>

        <!-- 操作 -->
        <template v-else-if="column.dataIndex === 'action'">
          <a-popconfirm title="确认从本奖池移除该奖项？" :disabled="isOnline" @confirm="removeRow(record)">
            <a-tooltip :title="isOnline ? '已上线，禁止撤项' : ''">
              <a-button type="link" danger size="small" :disabled="isOnline">移除</a-button>
            </a-tooltip>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- 底部：预算汇总 + 保存 -->
    <div class="mt-4 flex items-center justify-between rounded border bg-gray-50 px-4 py-3">
      <div class="text-sm text-gray-600">
        限量奖项预计最大投入：
        <span class="text-base font-bold text-blue-600">¥ {{ budgetSummary.total }}</span>
        <span v-if="budgetSummary.unlimitedCount > 0" class="ml-2 text-xs text-gray-400">
          （另有 {{ budgetSummary.unlimitedCount }} 个不限量奖项未计入）
        </span>
      </div>
      <a-button type="primary" :disabled="!isDirty" @click="save">保存物资配置</a-button>
    </div>

    <!-- 资产选择器抽屉 -->
    <a-drawer v-model:open="drawerOpen" title="从资产大库引入奖项" :width="640" placement="right">
      <a-alert type="info" show-icon class="mb-3" message="仅展示资产大库(t_prize_config)中的奖品，已引入的不可重复选择。" />
      <a-table
        :data-source="assetLibrary"
        :columns="assetColumns"
        :pagination="false"
        row-key="prizeCode"
        size="small"
        bordered
        :row-selection="rowSelection"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'prizeCode'">
            <span class="font-mono text-xs text-gray-500">{{ record.prizeCode }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'prizeType'">
            <a-tag :color="PRIZE_TYPE_MAP[record.prizeType]?.color">{{ prizeTypeLabel(record.prizeType) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'prizeValue'">¥ {{ formatMoney(record.prizeValue) }}</template>
          <template v-else-if="column.dataIndex === 'imported'">
            <a-tag v-if="importedCodes.has(record.prizeCode)" color="default">已引入</a-tag>
            <span v-else class="text-gray-300">—</span>
          </template>
        </template>
      </a-table>

      <template #footer>
        <div class="flex items-center justify-between">
          <span class="text-sm text-gray-500">已选 {{ drawerSelectedKeys.length }} 项</span>
          <a-space>
            <a-button @click="drawerOpen = false">取消</a-button>
            <a-button type="primary" :disabled="drawerSelectedKeys.length === 0" @click="confirmImport">
              确认引入
            </a-button>
          </a-space>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { message } from 'ant-design-vue';
  import { PlusOutlined } from '@ant-design/icons-vue';
  import { cloneDeep, isEqual } from 'lodash';
  import Decimal from 'decimal.js';

  // ---------------------------- Props / Emits ----------------------------

  const props = defineProps({
    // 运行期防篡改锁：活动是否已上线
    isOnline: {
      type: Boolean,
      default: false,
    },
  });

  const emit = defineEmits(['change']);

  // ---------------------------- 常量 ----------------------------

  // 库存 / 限领的「不限量」哨兵值，取值对齐后端 t_prize_pool_item
  const UNLIMITED = -1;
  // 引入新奖项时的默认值
  const DEFAULT_STOCK = 100;
  const DEFAULT_USER_MAX = 1;

  // 资产类型（对齐 PrizeTypeEnum）
  const PRIZE_TYPE_MAP = {
    SCORE: { label: '积分', color: 'gold' },
    BALANCE: { label: '现金', color: 'green' },
    COUPON: { label: '优惠券', color: 'blue' },
    PHYSICAL: { label: '实物', color: 'purple' },
  };
  const prizeTypeLabel = (type) => PRIZE_TYPE_MAP[type]?.label || type;

  const columns = [
    { title: '奖品编码', dataIndex: 'prizeCode', width: 160 },
    { title: '奖品名称', dataIndex: 'prizeName' },
    { title: '类型', dataIndex: 'prizeType', width: 90 },
    { title: '价值', dataIndex: 'prizeValue', width: 110 },
    { title: '总库存（已发/总量）', dataIndex: 'totalStock', width: 230 },
    { title: '单人限领', dataIndex: 'userMaxCount', width: 180 },
    { title: '操作', dataIndex: 'action', width: 80 },
  ];

  // ---------------------------- 主表数据（本地可写） ----------------------------
  // 只读字段(prizeName/prizeType/prizeValue)是引入时从资产库带过来的快照，本组件不提供编辑

  const mainList = ref([
    { key: 'PRIZE_IPHONE15', prizeCode: 'PRIZE_IPHONE15', prizeName: 'iPhone 15 Pro', prizeType: 'PHYSICAL', prizeValue: 7999, totalStock: 5, userMaxCount: 1, usedStock: 2 },
    { key: 'PRIZE_SCORE_100', prizeCode: 'PRIZE_SCORE_100', prizeName: '100 积分', prizeType: 'SCORE', prizeValue: 1, totalStock: 10000, userMaxCount: 5, usedStock: 4500 },
    { key: 'PRIZE_THANKS', prizeCode: 'PRIZE_THANKS', prizeName: '谢谢参与', prizeType: 'SCORE', prizeValue: 0, totalStock: UNLIMITED, userMaxCount: UNLIMITED, usedStock: 0 },
  ]);

  // 已保存基线：用于 isDirty 比对 + isOnline 时的库存下限锁定
  const savedSnapshot = ref(cloneDeep(mainList.value));
  // 上线时的原始库存映射（追加 only 的下限来源）
  const originalStockMap = computed(() => {
    const map = {};
    savedSnapshot.value.forEach((row) => {
      map[row.prizeCode] = row.totalStock;
    });
    return map;
  });

  const isDirty = computed(() => !isEqual(mainList.value, savedSnapshot.value));

  watch(
    mainList,
    () => emit('change', { dirty: isDirty.value, count: mainList.value.length }),
    { deep: true }
  );

  // ---------------------------- 金额（decimal.js，杜绝浮点精度丢失） ----------------------------

  const formatMoney = (value) => new Decimal(value || 0).toFixed(2);

  // 限量奖项预计最大投入 = Σ(价值 × 总库存)，不限量项无法估算单独计数
  const budgetSummary = computed(() => {
    let total = new Decimal(0);
    let unlimitedCount = 0;
    mainList.value.forEach((row) => {
      if (row.totalStock === UNLIMITED) {
        unlimitedCount += 1;
        return;
      }
      total = total.plus(new Decimal(row.prizeValue || 0).mul(row.totalStock || 0));
    });
    return { total: total.toFixed(2), unlimitedCount };
  });

  // ---------------------------- 库存 / 限领 编辑 ----------------------------

  // isOnline 时已保存的有限量奖项只能追加：下限锁定为原始库存；其余情况下限为 0
  function stockMin(record) {
    if (!props.isOnline) {
      return 0;
    }
    const original = originalStockMap.value[record.prizeCode];
    return original != null && original > 0 ? original : 0;
  }

  function toggleUnlimitedStock(record, checked) {
    record.totalStock = checked ? UNLIMITED : DEFAULT_STOCK;
  }

  function toggleUnlimitedLimit(record, checked) {
    record.userMaxCount = checked ? UNLIMITED : DEFAULT_USER_MAX;
  }

  function removeRow(record) {
    const index = mainList.value.findIndex((row) => row.key === record.key);
    if (index > -1) {
      mainList.value.splice(index, 1);
    }
  }

  function save() {
    savedSnapshot.value = cloneDeep(mainList.value);
    message.success('物资配置已保存');
  }

  // ---------------------------- 资产大库抽屉 ----------------------------

  const drawerOpen = ref(false);
  const drawerSelectedKeys = ref([]);

  // Mock 资产大库（t_prize_config）
  const assetLibrary = ref([
    { prizeCode: 'PRIZE_IPHONE15', prizeName: 'iPhone 15 Pro', prizeType: 'PHYSICAL', prizeValue: 7999 },
    { prizeCode: 'PRIZE_SCORE_100', prizeName: '100 积分', prizeType: 'SCORE', prizeValue: 1 },
    { prizeCode: 'PRIZE_THANKS', prizeName: '谢谢参与', prizeType: 'SCORE', prizeValue: 0 },
    { prizeCode: 'PRIZE_CASH_5', prizeName: '5 元现金红包', prizeType: 'BALANCE', prizeValue: 5 },
    { prizeCode: 'PRIZE_COUPON_20', prizeName: '满100减20优惠券', prizeType: 'COUPON', prizeValue: 20 },
    { prizeCode: 'PRIZE_AIRPODS', prizeName: 'AirPods Pro 2', prizeType: 'PHYSICAL', prizeValue: 1899 },
    { prizeCode: 'PRIZE_SCORE_500', prizeName: '500 积分', prizeType: 'SCORE', prizeValue: 5 },
    { prizeCode: 'PRIZE_CASH_88', prizeName: '88 元现金红包', prizeType: 'BALANCE', prizeValue: 88 },
  ]);

  const assetColumns = [
    { title: '奖品编码', dataIndex: 'prizeCode' },
    { title: '名称', dataIndex: 'prizeName' },
    { title: '类型', dataIndex: 'prizeType', width: 80 },
    { title: '价值', dataIndex: 'prizeValue', width: 90 },
    { title: '状态', dataIndex: 'imported', width: 70 },
  ];

  // 已引入的编码集合：抽屉里禁止重复勾选
  const importedCodes = computed(() => new Set(mainList.value.map((row) => row.prizeCode)));

  const rowSelection = computed(() => ({
    selectedRowKeys: drawerSelectedKeys.value,
    onChange: (keys) => {
      drawerSelectedKeys.value = keys;
    },
    getCheckboxProps: (record) => ({
      disabled: importedCodes.value.has(record.prizeCode),
    }),
  }));

  function openDrawer() {
    drawerSelectedKeys.value = [];
    drawerOpen.value = true;
  }

  function confirmImport() {
    const toAdd = assetLibrary.value.filter(
      (asset) => drawerSelectedKeys.value.includes(asset.prizeCode) && !importedCodes.value.has(asset.prizeCode)
    );
    // cloneDeep 切断与资产库的引用，转为本地可写行结构
    const rows = cloneDeep(toAdd).map((asset) => ({
      key: asset.prizeCode,
      prizeCode: asset.prizeCode,
      prizeName: asset.prizeName,
      prizeType: asset.prizeType,
      prizeValue: asset.prizeValue,
      totalStock: DEFAULT_STOCK,
      userMaxCount: DEFAULT_USER_MAX,
      usedStock: 0,
    }));
    mainList.value.push(...rows);
    message.success(`已引入 ${rows.length} 个奖项`);
    drawerOpen.value = false;
    drawerSelectedKeys.value = [];
  }

  // ---------------------------- 对外暴露：供工作台父组件统一取数/保存 ----------------------------

  defineExpose({
    getData: () => cloneDeep(mainList.value),
    isDirty: () => isDirty.value,
  });
</script>
