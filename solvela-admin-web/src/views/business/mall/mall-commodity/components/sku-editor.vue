<!--
  * 定价与规格（原型 docs/mall.html 第 2 段）
  *
  * 结构：规格维度（规格名 + 规格值标签）→ 生成/刷新 SKU 表 → 批量设置 → SKU 表。
  *
  * 【为什么规格组合是"生成"出来的，不能手工加行】
  * 规格组合必须两两不同。DDL 里写明了：JSON 列做不了唯一索引，去重只能靠 Service 比对 attrs。
  * 手工加行的话运营很容易录出两个「星空灰 / XL」，提交时才被后端打回来 —— 那时表单已经填了十分钟。
  *
  * 【无规格商品】不加任何规格维度即可：仍然生成一行 skuAttrs = {} 的 SKU。
  * 原型注释专门记了这个坑：上一版直接 `skuList = []`，导致「10元话费券」这种无规格商品
  * 根本填不了价格和库存，提交出去 skuList 是空数组，后端拿不到任何定价。
  *
  * 【刷新会保留已填过的价格与库存】按规格组合匹配旧行，不是整表重建。
  *
  * @Copyright  weolwo
-->
<template>
  <div>
    <!---------- 规格维度 ----------->
    <div class="mb-4 pl-3 spec-section">
      <div class="flex items-center justify-between mb-3">
        <span class="text-sm font-medium spec-section-title">规格维度</span>
        <span class="text-xs form-tip">不加任何规格 = 单规格商品，下方仍会生成一行 SKU</span>
      </div>

      <div v-for="(spec, index) in specList" :key="index" class="spec-box">
        <div class="flex items-center justify-between mb-3">
          <div class="flex items-center gap-2">
            <span class="text-sm spec-label">规格名</span>
            <a-input v-model:value="spec.name" placeholder="如 颜色 / 容量" style="width: 180px" size="small" :maxlength="20" />
          </div>
          <a-button type="text" danger size="small" @click="removeSpec(index)">移除</a-button>
        </div>
        <div class="flex items-center flex-wrap gap-2">
          <span class="text-sm spec-label shrink-0">规格值</span>
          <a-tag v-for="(val, vIndex) in spec.values" :key="vIndex" closable color="processing" @close="removeSpecValue(index, vIndex)">
            {{ val }}
          </a-tag>
          <a-input v-model:value="spec.tempValue" placeholder="输入后回车" size="small" style="width: 130px" @press-enter="addSpecValue(index)" />
        </div>
      </div>

      <div class="flex gap-3 mt-2">
        <a-button type="dashed" size="small" :disabled="specList.length >= MAX_SPEC_COUNT" @click="addSpec">+ 添加规格</a-button>
        <a-button type="primary" size="small" :loading="generating" @click="generateSkuTable">⬇ 生成/刷新 SKU 表</a-button>
        <span class="text-xs form-tip self-center">刷新会保留已填过的价格与库存</span>
      </div>
    </div>

    <!---------- 批量设置：SKU 一多，逐格填是最耗时的一步 ----------->
    <div class="batch-bar">
      <span class="text-sm font-medium">批量设置</span>
      <a-input-number v-model:value="batch.points" :min="0" :precision="0" placeholder="积分" style="width: 110px" size="small" />
      <a-input-number
        v-model:value="batch.cash"
        :min="0"
        :precision="2"
        placeholder="现金"
        style="width: 110px"
        size="small"
        :disabled="pointsOnly"
      />
      <a-input-number v-model:value="batch.stock" :min="0" :precision="0" placeholder="库存" style="width: 110px" size="small" />
      <a-button type="primary" ghost size="small" @click="applyBatch">
        应用到{{ selectedRowKeys.length ? `选中 ${selectedRowKeys.length} 项` : '全部' }}
      </a-button>
      <span class="text-xs form-tip">不勾选任何行 = 应用到全部；留空的项不覆盖</span>
    </div>

    <!---------- SKU 表 ----------->
    <a-table
      size="small"
      bordered
      row-key="rowKey"
      :data-source="rows"
      :columns="skuColumns"
      :pagination="false"
      :row-selection="{ selectedRowKeys, onChange: (keys) => (selectedRowKeys = keys) }"
      :scroll="{ x: scrollX }"
    >
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.dataIndex === 'attrs'">
          <span v-if="isSingleSku" class="form-tip">单规格</span>
          <template v-else>
            <a-tag v-for="(v, k) in record.skuAttrs" :key="k" color="blue">{{ k }}: {{ v }}</a-tag>
          </template>
        </template>

        <template v-else-if="column.dataIndex === 'skuCode'">
          <!-- 创建后不可改：编码被履约链路引用着，改一次等于把那些引用指向空。后端同样会忽略修改 -->
          <a-input
            v-model:value="record.skuCode"
            size="small"
            :disabled="!!record.id"
            :status="BIZ_CODE_REGEX.test(record.skuCode || '') ? '' : 'error'"
            @change="record.skuCode = (record.skuCode || '').toUpperCase()"
          />
        </template>

        <template v-else-if="column.dataIndex === 'skuCoverFileId'">
          <ImageSlot v-model:fileId="record.skuCoverFileId" size="small" />
        </template>

        <template v-else-if="column.dataIndex === 'skuPointsPrice'">
          <!-- 占位符是基准价：留空 = 继承主表，不是 0。0 是「免费兑换」的合法取值 -->
          <a-input-number
            v-model:value="record.skuPointsPrice"
            :min="0"
            :precision="0"
            size="small"
            style="width: 100%"
            :placeholder="`继承 ${pointsPrice || 0}`"
          />
        </template>

        <template v-else-if="column.dataIndex === 'skuCashPrice'">
          <a-input-number
            v-model:value="record.skuCashPrice"
            :min="0"
            :precision="2"
            size="small"
            style="width: 100%"
            :placeholder="`继承 ${cashPrice || 0}`"
          />
        </template>

        <template v-else-if="column.dataIndex === 'totalStock'">
          <a-input-number v-model:value="record.totalStock" :min="occupiedOf(record)" :precision="0" size="small" style="width: 100%" />
        </template>

        <template v-else-if="column.dataIndex === 'lockedStock'">
          <span class="text-locked">{{ record.lockedStock || 0 }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'soldCount'">
          <span class="form-tip">{{ record.soldCount || 0 }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'available'">
          <span :class="availableOf(record) > 0 ? 'text-available' : 'text-empty'">{{ availableOf(record) }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'skuStatus'">
          <a-switch
            :checked="record.skuStatus === SKU_STATUS_ENUM.ENABLED.value"
            size="small"
            @change="(c) => (record.skuStatus = c ? SKU_STATUS_ENUM.ENABLED.value : SKU_STATUS_ENUM.DISABLED.value)"
          />
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a-tooltip :title="removeDisabledReason(record)">
            <span>
              <a-button type="link" danger size="small" :disabled="!canRemove(record)" @click="removeRow(index)">删除</a-button>
            </span>
          </a-tooltip>
        </template>
      </template>
    </a-table>

    <div class="text-xs form-tip mt-2">
      可用库存 = 总库存 − 锁定 − 已售，由数据库虚拟列算，不落地写入。扣减靠单行条件 UPDATE 防超卖，<b>不需要乐观锁版本号</b>。
    </div>
  </div>
</template>

<script setup>
  import { computed, reactive, ref, watch } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { mallCommodityApi } from '/@/api/business/mall/mall-commodity-api';
  import { PAY_TYPE_ENUM, SKU_STATUS_ENUM } from '/@/constants/business/mall/mall-commodity-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import ImageSlot from '../../components/image-slot.vue';

  /** 铁律 8 的唯一真源在后端 SolvelaCodeUtil，这里是同源常量 */
  const BIZ_CODE_REGEX = /^[A-Z0-9]{10}$/;

  /** 三个维度已经是 10×10×10 的组合上限了，再多表格本身就没法用 */
  const MAX_SPEC_COUNT = 3;

  const MAX_SKU_COUNT = 100;

  const props = defineProps({
    // v-model:value —— SKU 行数组，与后端 MallCommoditySaveForm.skuList 同构
    value: { type: Array, default: () => [] },
    payType: { type: Number, default: PAY_TYPE_ENUM.POINTS.value },
    pointsPrice: { type: Number, default: null },
    cashPrice: { type: Number, default: null },
  });
  const emit = defineEmits(['update:value']);

  const pointsOnly = computed(() => props.payType === PAY_TYPE_ENUM.POINTS.value);

  // ---------------------------- 行数据 ----------------------------

  /**
   * rowKey 是前端自己发的，不能用 id（新增行还没有），也不能用 index
   * （删掉中间一行之后所有输入框的内容会跟着串位，因为 Vue 复用了 DOM）。
   */
  let rowSeed = 0;
  const rows = ref([]);
  const selectedRowKeys = ref([]);
  const generating = ref(false);
  const batch = reactive({ points: null, cash: null, stock: null });

  /**
   * 🔴 必须声明在下面那个 immediate watch 之前。
   * 那个 watch 在 setup 的同步阶段就会执行并调到 deriveSpecList()，函数声明会提升、
   * `const` 不会 —— 声明写在 watch 之后的话这里是暂时性死区，整个编辑页白屏。
   */
  const specList = ref([]);

  function withRowKey(sku) {
    return { rowKey: `sku-${rowSeed++}`, ...sku };
  }

  /**
   * ⚠️ 这个 watch 是 immediate 的，**跑在 setup 的同步阶段**，所以回调里只能碰
   * 声明在它上面的东西。别在这里调 generateSkuTable() 之类的函数 ——
   * 函数声明会提升，但函数体里用到的 computed（validSpecs 等）声明在下面，
   * 一碰就是暂时性死区。首次播种交给父页面在 onMounted 之后调 seedIfEmpty()。
   */
  watch(
    () => props.value,
    (list) => {
      // 外部（打开编辑页、切换商品）灌入时重建；本组件内部的修改不走这里
      if (list === rows.value) {
        return;
      }
      rows.value = (list || []).map((sku) => withRowKey({ ...sku, skuAttrs: { ...(sku.skuAttrs || {}) } }));
      deriveSpecList();
      selectedRowKeys.value = [];
    },
    { immediate: true }
  );

  watch(rows, (list) => emit('update:value', list), { deep: true });

  // ---------------------------- 规格维度 ----------------------------

  /**
   * 从已有 SKU 反推规格维度。
   *
   * 规格维度并没有单独存在库里 —— 它只存在于每个 SKU 的 attrs 里。不反推的话，
   * 运营打开一个已有商品会看到「规格维度」是空的，一点「生成」就把现有 SKU 全冲掉。
   */
  function deriveSpecList() {
    const groups = [];
    const indexByName = new Map();
    for (const row of rows.value) {
      for (const [name, value] of Object.entries(row.skuAttrs || {})) {
        if (!indexByName.has(name)) {
          indexByName.set(name, groups.length);
          groups.push({ name, values: [], tempValue: '' });
        }
        const group = groups[indexByName.get(name)];
        if (value !== '' && value !== null && value !== undefined && !group.values.includes(value)) {
          group.values.push(value);
        }
      }
    }
    specList.value = groups;
  }

  const validSpecs = computed(() =>
    specList.value
      .map((s) => ({ name: (s.name || '').trim(), values: (s.values || []).map((v) => String(v).trim()).filter(Boolean) }))
      .filter((s) => s.name && s.values.length)
  );

  const isSingleSku = computed(() => validSpecs.value.length === 0);

  function addSpec() {
    if (specList.value.length >= MAX_SPEC_COUNT) {
      return;
    }
    specList.value.push({ name: '', values: [], tempValue: '' });
  }

  function removeSpec(index) {
    specList.value.splice(index, 1);
  }

  function addSpecValue(index) {
    const spec = specList.value[index];
    const value = (spec.tempValue || '').trim();
    if (value && !spec.values.includes(value)) {
      spec.values.push(value);
    }
    spec.tempValue = '';
  }

  function removeSpecValue(specIndex, valueIndex) {
    specList.value[specIndex].values.splice(valueIndex, 1);
  }

  // ---------------------------- 生成 SKU 表 ----------------------------

  /** 与后端 normalizedAttrsKey 同口径：按属性名排序后拼接，顺序不同不算不同规格 */
  function attrsKey(attrs) {
    return Object.entries(attrs || {})
      .map(([k, v]) => [String(k).trim(), String(v).trim()])
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([k, v]) => `${k}:${v}`)
      .join('/');
  }

  /**
   * 按规格维度的笛卡尔积重建 SKU 表。三条规则，缺一条都会让运营丢数据：
   *   ① 组合还在 → **复用原来那一行**（id / 编码 / 库存 / 已售 / 价格全部保留）
   *   ② 组合是新的 → 加一行，编码问后端要
   *   ③ 组合没了但**有流水** → 留着并停用，不能删（后端也会拒绝删除，早点在这里说清楚）
   */
  async function generateSkuTable() {
    const emptyValueSpec = specList.value.find((s) => (s.name || '').trim() && !(s.values || []).length);
    if (emptyValueSpec) {
      message.warning(`规格「${emptyValueSpec.name}」还没有填任何规格值`);
      return;
    }

    const specs = validSpecs.value;
    const combos = specs.length === 0 ? [{}] : cartesian(specs);
    if (combos.length > MAX_SKU_COUNT) {
      message.warning(`会生成 ${combos.length} 个规格，超过单商品 ${MAX_SKU_COUNT} 个的上限`);
      return;
    }

    const oldByKey = new Map(rows.value.map((row) => [attrsKey(row.skuAttrs), row]));
    const reused = combos.map((attrs) => oldByKey.get(attrsKey(attrs)) || null);
    const newCount = reused.filter((r) => r === null).length;

    // 编码一次问后端要够：铁律 8 的唯一真源是 SolvelaCodeUtil，前端自造迟早和后端漂开
    let freshCodes = [];
    if (newCount > 0) {
      generating.value = true;
      try {
        const res = await mallCommodityApi.generateSkuCodes(newCount);
        freshCodes = res || [];
      } catch (e) {
        solvelaSentry.captureError(e);
        return;
      } finally {
        generating.value = false;
      }
    }

    let codeIndex = 0;
    const nextRows = combos.map((skuAttrs, i) => {
      const matched = reused[i];
      if (matched) {
        oldByKey.delete(attrsKey(skuAttrs));
        return { ...matched, skuAttrs };
      }
      return withRowKey({
        id: null,
        skuCode: freshCodes[codeIndex++] || '',
        skuAttrs,
        skuCoverFileId: null,
        // 留空 = 继承主表基准价；库存留 0，等运营用批量设置一次填完
        skuPointsPrice: null,
        skuCashPrice: null,
        totalStock: 0,
        lockedStock: 0,
        soldCount: 0,
        skuStatus: SKU_STATUS_ENUM.ENABLED.value,
        sort: 0,
      });
    });

    // 被生成规则淘汰、但已经有交易流水的行：保留 + 停用
    const orphansWithHistory = [...oldByKey.values()].filter((row) => occupiedOf(row) > 0);
    orphansWithHistory.forEach((row) => nextRows.push({ ...row, skuStatus: SKU_STATUS_ENUM.DISABLED.value }));

    rows.value = nextRows.map((row, index) => ({ ...row, sort: index }));
    selectedRowKeys.value = [];

    if (orphansWithHistory.length > 0) {
      Modal.info({
        title: '有规格已产生兑换记录',
        content: `${orphansWithHistory.length} 个旧规格已经卖出或被锁定，不能删除，已为你保留并置为停用。`,
      });
    }
  }

  function cartesian(specs) {
    return specs.reduce((acc, spec) => acc.flatMap((combo) => spec.values.map((v) => ({ ...combo, [spec.name]: v }))), [{}]);
  }

  // ---------------------------- 批量设置 ----------------------------

  function applyBatch() {
    const targets = selectedRowKeys.value.length ? rows.value.filter((r) => selectedRowKeys.value.includes(r.rowKey)) : rows.value;
    if (!targets.length) {
      return;
    }
    let stockSkipped = 0;
    targets.forEach((row) => {
      if (batch.points !== null && batch.points !== undefined) {
        row.skuPointsPrice = batch.points;
      }
      if (batch.cash !== null && batch.cash !== undefined && !pointsOnly.value) {
        row.skuCashPrice = batch.cash;
      }
      if (batch.stock !== null && batch.stock !== undefined) {
        // 已锁定+已售的部分改不回去，批量设置也不例外 —— 否则一次批量就把
        // 「投100/售37」改成「投10」，虚拟列 available_stock 直接变负数
        if (batch.stock < occupiedOf(row)) {
          stockSkipped++;
        } else {
          row.totalStock = batch.stock;
        }
      }
    });
    message.success(`已应用到 ${targets.length} 个 SKU`);
    if (stockSkipped > 0) {
      message.warning(`其中 ${stockSkipped} 个规格的库存低于已锁定+已售，未修改`);
    }
  }

  // ---------------------------- 行操作 ----------------------------

  function occupiedOf(record) {
    return (record.lockedStock || 0) + (record.soldCount || 0);
  }

  function availableOf(record) {
    return (record.totalStock || 0) - occupiedOf(record);
  }

  function canRemove(record) {
    return occupiedOf(record) === 0 && rows.value.length > 1;
  }

  /**
   * 禁用原因有两种，必须分清楚：有交易流水 vs 只剩最后一行。
   * 都写成「有流水」的话，新建商品那唯一一行也会显示「有流水」—— 一个一件都没卖过的规格。
   * 禁用的 button 不派发鼠标事件，所以 tooltip 挂在外层 span 上。
   */
  function removeDisabledReason(record) {
    if (occupiedOf(record) > 0) {
      return '该规格已产生兑换记录，不能删除；请改为停用';
    }
    if (rows.value.length <= 1) {
      return '商品至少要保留一个规格';
    }
    return '';
  }

  function removeRow(index) {
    rows.value.splice(index, 1);
  }

  // ---------------------------- 表格列 ----------------------------

  /** 有任意一行是库里来的，就算编辑态 —— 新建商品不显示锁定/已售/可用 */
  const hasPersisted = computed(() => rows.value.some((r) => !!r.id));

  /**
   * 列是动态的，两处按需出现：
   *   · 现金列只在「积分+现金」时出现 —— 纯积分商品那一列永远是 0，白占宽度
   *   · 锁定/已售/可用只在编辑态出现 —— 新建商品这三列恒为 0，是纯噪音
   */
  const skuColumns = computed(() => {
    const cols = [
      { title: '规格', dataIndex: 'attrs', width: 190 },
      { title: 'SKU编码', dataIndex: 'skuCode', width: 140 },
      { title: '图', dataIndex: 'skuCoverFileId', width: 60, align: 'center' },
      { title: '所需积分', dataIndex: 'skuPointsPrice', width: 120 },
    ];
    if (!pointsOnly.value) {
      cols.push({ title: '所需现金', dataIndex: 'skuCashPrice', width: 120 });
    }
    cols.push({ title: '总库存', dataIndex: 'totalStock', width: 110 });
    if (hasPersisted.value) {
      cols.push({ title: '锁定', dataIndex: 'lockedStock', width: 66, align: 'center' });
      cols.push({ title: '已售', dataIndex: 'soldCount', width: 66, align: 'center' });
      cols.push({ title: '可用', dataIndex: 'available', width: 66, align: 'center' });
    }
    cols.push({ title: '启用', dataIndex: 'skuStatus', width: 70, align: 'center' });
    cols.push({ title: '操作', dataIndex: 'action', width: 70, align: 'center', fixed: 'right' });
    return cols;
  });

  const scrollX = computed(() => skuColumns.value.reduce((sum, c) => sum + (c.width || 100), 0) + 60);

  const totalStock = computed(() => rows.value.reduce((sum, r) => sum + (r.totalStock || 0), 0));

  defineExpose({
    totalStock,
    /**
     * 新建商品时由父页面在挂载后调用：直接摆出那一行单规格 SKU。
     * 留一张「暂无数据」的空表运营会愣住 —— 界面上没有「加一行」按钮，
     * 行是按规格维度生成的，而不加规格维度时那个动作看起来什么都不会发生。
     */
    seedIfEmpty() {
      if (rows.value.length === 0) {
        return generateSkuTable();
      }
      return Promise.resolve();
    },
    /** 保存前由父页面调用：把「一个规格都没有」这种状态兜回合法形态 */
    ensureNotEmpty() {
      if (rows.value.length === 0) {
        return generateSkuTable();
      }
      return Promise.resolve();
    },
    /** 供父页面做校验汇总 */
    validate() {
      const errors = [];
      if (!rows.value.length) {
        errors.push('还没有生成 SKU');
        return errors;
      }
      const codes = new Set();
      rows.value.forEach((row) => {
        if (!BIZ_CODE_REGEX.test(row.skuCode || '')) {
          errors.push('有 SKU 编码格式不对（10位大写字母+数字）');
        } else if (codes.has(row.skuCode)) {
          errors.push(`SKU 编码重复：${row.skuCode}`);
        } else {
          codes.add(row.skuCode);
        }
      });
      // 去重：同一类问题重复十行没有意义，运营看的是「有哪几类问题」
      return [...new Set(errors)];
    },
  });
</script>

<style scoped lang="less">
  .spec-section {
    border-left: 2px solid #e2e8f0;
  }

  .spec-section-title {
    color: #334155;
  }

  .spec-label {
    color: #64748b;
  }

  .spec-box {
    padding: 14px 16px;
    margin-bottom: 12px;
    background: #f8fafc;
    border: 1px dashed #cbd5e1;
    border-radius: 8px;
    transition: all 0.2s;
  }

  .spec-box:hover {
    background: #f8fbff;
    border-color: #3b82f6;
  }

  .batch-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
    padding: 10px 14px;
    margin-bottom: 12px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
  }

  .form-tip {
    color: #94a3b8;
  }

  .text-locked {
    color: #d97706;
  }

  .text-available {
    font-weight: 500;
    color: #059669;
  }

  .text-empty {
    font-weight: 500;
    color: #ef4444;
  }
</style>
