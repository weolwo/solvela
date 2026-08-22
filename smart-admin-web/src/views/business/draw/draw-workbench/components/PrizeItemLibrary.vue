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
    <a-alert v-if="isOnline" type="warning" show-icon class="mb-4" message="活动已上线：仅允许追加库存，禁止缩减库存、切换不限量或移除奖项。" />

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
      <template #emptyText>
        <div class="py-6 text-gray-400">本活动尚未配置奖项，请点击右上角「从资产大库引入奖项」</div>
      </template>

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
              <a-input-number v-model:value="record.totalStock" :min="stockMin(record)" :precision="0" size="small" class="w-24" />
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

        <!-- 高级策略：白名单（对齐 t_prize_pool_item.white_list） -->
        <template v-else-if="column.dataIndex === 'whiteList'">
          <a-button
            type="link"
            size="small"
            :class="record.whiteList?.length ? 'font-medium text-purple-600!' : 'text-gray-400!'"
            @click="openWhiteList(record)"
          >
            配置白名单<span v-if="record.whiteList?.length"> ({{ record.whiteList.length }})</span>
          </a-button>
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

    <!-- 底部：预算汇总。落库统一走顶部「保存并发布」，此处不做半截保存，避免主子表配置被拆成两次事务 -->
    <div class="mt-4 flex items-center justify-between rounded border bg-gray-50 px-4 py-3">
      <div class="text-sm text-gray-600">
        限量奖项预计最大投入：
        <span class="text-base font-bold text-blue-600">¥ {{ budgetSummary.total }}</span>
        <span v-if="budgetSummary.unlimitedCount > 0" class="ml-2 text-xs text-gray-400">
          （另有 {{ budgetSummary.unlimitedCount }} 个不限量奖项未计入）
        </span>
      </div>
      <span class="text-xs text-gray-400">物资与奖池概率一起提交，请点击右上角「保存并发布抽奖活动」</span>
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
        :loading="assetLoading"
        :row-selection="rowSelection"
      >
        <template #emptyText>
          <div class="py-6 text-gray-400">本活动的资产大库为空，请先到「奖品配置」为该活动创建启用中的奖品</div>
        </template>

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
            <a-button type="primary" :disabled="drawerSelectedKeys.length === 0" @click="confirmImport"> 确认引入 </a-button>
          </a-space>
        </div>
      </template>
    </a-drawer>

    <!-- 白名单配置 -->
    <a-modal
      v-model:open="whiteListModalOpen"
      :title="`🛡️ 白名单配置 - ${currentRecord?.prizeName || ''}`"
      :width="560"
      ok-text="保存名单"
      cancel-text="取消"
      @ok="confirmWhiteList"
    >
      <a-alert
        type="info"
        show-icon
        class="mb-3"
        message="名单内的用户在抽奖时，只要该奖项仍有库存，将无视概率 100% 必中此奖。多个 UserID 请用逗号或换行隔开。"
      />
      <a-textarea
        v-model:value="whiteListText"
        :rows="8"
        placeholder="每行一个 UserID，也可用逗号分隔，例如：&#10;vip_10086&#10;test_user_01, test_user_02"
      />
      <div class="mt-2 text-xs text-gray-400">保存时会自动按换行/中英文逗号切分、去空格、去重。</div>
    </a-modal>
  </div>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { message } from 'ant-design-vue';
  import { PlusOutlined } from '@ant-design/icons-vue';
  import { cloneDeep, isEqual } from 'lodash';
  import Decimal from 'decimal.js';
  import { prizeConfigApi } from '/src/api/business/prize/prize-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';

  // ---------------------------- Props / Emits ----------------------------

  const props = defineProps({
    // 运行期防篡改锁：活动是否已上线
    isOnline: {
      type: Boolean,
      default: false,
    },
    // 当前活动编码：资产大库按活动维度隔离（t_prize_config.activity_code）
    activityCode: {
      type: String,
      default: '',
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
    { title: '高级策略', dataIndex: 'whiteList', width: 140 },
    { title: '操作', dataIndex: 'action', width: 80 },
  ];

  // ---------------------------- 主表数据（本地可写） ----------------------------
  // 只读字段(prizeName/prizeType/prizeValue)是引入时从资产库带过来的快照，本组件不提供编辑

  const mainList = ref([]);

  // 已保存基线：用于 isDirty 比对 + isOnline 时的库存下限锁定
  const savedSnapshot = ref([]);
  // 上线时的原始库存映射（追加 only 的下限来源）
  const originalStockMap = computed(() => {
    const map = {};
    savedSnapshot.value.forEach((row) => {
      map[row.prizeCode] = row.totalStock;
    });
    return map;
  });

  const isDirty = computed(() => !isEqual(mainList.value, savedSnapshot.value));

  watch(mainList, () => emit('change', { dirty: isDirty.value, count: mainList.value.length }), { deep: true });

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

  // ---------------------------- 资产大库抽屉 ----------------------------

  const drawerOpen = ref(false);
  const drawerSelectedKeys = ref([]);
  const assetLoading = ref(false);

  // 资产大库（t_prize_config），按当前活动加载
  const assetLibrary = ref([]);

  async function loadAssetLibrary() {
    if (!props.activityCode) {
      assetLibrary.value = [];
      return;
    }
    assetLoading.value = true;
    try {
      const res = await prizeConfigApi.optionList(props.activityCode);
      assetLibrary.value = res.data || [];
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      assetLoading.value = false;
    }
  }

  // 切活动即换资产大库
  watch(() => props.activityCode, loadAssetLibrary, { immediate: true });

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
    loadAssetLibrary();
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
      prizeValue: Number(asset.prizeValue ?? 0),
      totalStock: DEFAULT_STOCK,
      userMaxCount: DEFAULT_USER_MAX,
      usedStock: 0,
      // 新引入奖项默认无白名单
      whiteList: [],
    }));
    mainList.value.push(...rows);
    message.success(`已引入 ${rows.length} 个奖项`);
    drawerOpen.value = false;
    drawerSelectedKeys.value = [];
  }

  // ---------------------------- 白名单配置（t_prize_pool_item.white_list） ----------------------------
  // 白名单是「必中」策略，属运营手段，上线后仍允许调整（与概率同理），故不受 isOnline 结构锁限制

  const whiteListModalOpen = ref(false);
  const whiteListText = ref('');
  // 指向 mainList 中的行对象引用，保存时直接改其 whiteList 即可触发响应式与 isDirty
  const currentRecord = ref(null);

  function openWhiteList(record) {
    currentRecord.value = record;
    whiteListText.value = (record.whiteList || []).join('\n');
    whiteListModalOpen.value = true;
  }

  function confirmWhiteList() {
    // 清洗：按换行 / 英文逗号 / 中文逗号切分 -> 去首尾空格 -> 去空项 -> Set 去重
    const idList = (whiteListText.value || '')
      .split(/[\r\n,，]+/)
      .map((item) => item.trim())
      .filter((item) => item !== '');
    const uniqueIdList = [...new Set(idList)];

    if (currentRecord.value) {
      currentRecord.value.whiteList = uniqueIdList;
    }
    whiteListModalOpen.value = false;
    message.success(`已解析并保存 ${uniqueIdList.length} 个不重复的 UserID`);
  }

  // ---------------------------- 对外暴露：供工作台父组件统一取数/保存 ----------------------------

  defineExpose({
    /**
     * 回显：把后端 workbench/detail 的 prizeItemList 填回表格，并把基线重置为服务端状态
     * usedStock 是服务端权威值，本组件只读展示，提交时也不回传
     * prizeValue 后端是 BigDecimal、统一序列化为字符串（JsonConfig），此处归一化为 number
     */
    setData: (itemList) => {
      mainList.value = (itemList || []).map((item) => ({
        key: item.prizeCode,
        prizeCode: item.prizeCode,
        prizeName: item.prizeName,
        prizeType: item.prizeType,
        prizeValue: Number(item.prizeValue ?? 0),
        totalStock: item.totalStock,
        userMaxCount: item.userMaxCount,
        usedStock: item.usedStock ?? 0,
        whiteList: item.whiteList || [],
      }));
      savedSnapshot.value = cloneDeep(mainList.value);
    },
    getData: () => cloneDeep(mainList.value),
    isDirty: () => isDirty.value,
  });
</script>
