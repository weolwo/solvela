<!--
  * 商城-库存总览
  *
  * 【这个页面回答的问题】哪些规格快卖光了 —— 一个在别处答不了的问题：
  * 商品列表只有商品级的库存合计（某一个规格断货看不出来），
  * 商品编辑页一次只能看一个商品。
  *
  * 【它不负责改库存】改库存在商品编辑页，那里有批量设置，而且库存改动会连同价格、
  * 状态一起走同一个聚合保存事务。两个入口写同一批数据，迟早对不上。
  * 所以这里每行只给一个「去编辑」的跳转，不做行内编辑。
  *
  * 【默认按可用库存升序】打开就是为了看该补哪些货；按创建时间排的话，
  * 最该看的那几行会淹在几百行中间。
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:37:50
  * @Copyright  weolwo
-->
<template>
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="商品名称" class="solvela-query-form-item">
        <a-input style="width: 170px" v-model:value="queryForm.commodityName" placeholder="支持模糊搜索" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="SKU编码" class="solvela-query-form-item">
        <a-input style="width: 150px" v-model:value="queryForm.skuCode" placeholder="精确匹配" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="分类" class="solvela-query-form-item">
        <a-select style="width: 150px" v-model:value="queryForm.categoryId" :options="categoryOptions" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="商品状态" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="queryForm.commodityStatus" :options="COMMODITY_STATUS_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="规格状态" class="solvela-query-form-item">
        <a-select style="width: 110px" v-model:value="queryForm.skuStatus" :options="SKU_STATUS_OPTIONS" placeholder="全部" allow-clear />
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

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block">
        <!-- 这个页面的主用法：打开就想知道今天该补哪些货 -->
        <a-button size="small" :type="onlyLowStock ? 'primary' : 'default'" @click="toggleLowStock">
          只看告警（可用 ≤ {{ LOW_STOCK_THRESHOLD }}）
        </a-button>
        <span class="toolbar-tip">改库存请到商品编辑页，那里可以批量设置</span>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      :scroll="{ x: 1300 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
      :row-class-name="rowClassName"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'commodityName'">
          <div class="commodity-cell">
            <FileThumb v-if="record.coverFileId" :file-id="record.coverFileId" :height="36" />
            <div>
              <div>{{ record.commodityName }}</div>
              <div class="cell-sub">{{ record.categoryName }} · {{ record.commodityCode }}</div>
            </div>
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'skuAttrs'">
          <div>{{ skuAttrsText(record.skuAttrs) }}</div>
          <div class="cell-sub">{{ record.skuCode }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'stock'">
          <!-- 「投 100 / 售 37 / 锁 2 / 余 61」：只显示可用的话，运营解释不了库存去哪了 -->
          <span class="stock-detail">投 {{ record.totalStock }} · 售 {{ record.soldCount }} · 锁 {{ record.lockedStock }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'availableStock'">
          <span class="available" :class="availableClass(record)">{{ record.availableStock }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'price'">
          <!-- SKU 没覆盖价时实际卖的是商品基准价，这里要显示那个值，否则会看到一片空白 -->
          <span>{{ record.skuPointsPrice === null || record.skuPointsPrice === undefined ? record.basePointsPrice : record.skuPointsPrice }}</span>
          <span class="cell-sub"> 积分</span>
          <div v-if="record.skuPointsPrice === null || record.skuPointsPrice === undefined" class="cell-sub">继承商品基准价</div>
        </template>

        <template v-else-if="column.dataIndex === 'skuStatus'">
          <a-tag :color="text === SKU_STATUS_ENUM.ENABLED.value ? 'green' : 'default'">
            {{ text === SKU_STATUS_ENUM.ENABLED.value ? '启用' : '停用' }}
          </a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'commodityStatus'">
          <a-tag :color="commodityStatusMeta(text).color">{{ commodityStatusMeta(text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" @click="goEdit(record)">去编辑</a-button>
        </template>
      </template>
    </a-table>

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
  </a-card>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { mallSkuApi } from '/@/api/business/mall/mall-sku-api';
  import { mallCategoryApi } from '/@/api/business/mall/mall-category-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { COMMODITY_STATUS_ENUM, COMMODITY_STATUS_OPTIONS, SKU_STATUS_ENUM } from '/@/constants/business/mall/mall-commodity-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import FileThumb from '/@/components/support/file-thumb/index.vue';

  const COMMODITY_EDITOR_PATH = '/mall/mall-commodity/editor';

  /** 告警阈值。不做成可配置项：它只是个默认视角，运营真要别的口径可以自己筛 */
  const LOW_STOCK_THRESHOLD = 10;

  const SKU_STATUS_OPTIONS = Object.values(SKU_STATUS_ENUM).map((item) => ({ value: item.value, label: item.desc }));

  const router = useRouter();

  const columns = ref([
    { title: '商品', dataIndex: 'commodityName', width: 260 },
    { title: '规格 / SKU编码', dataIndex: 'skuAttrs', width: 200 },
    { title: '可用', dataIndex: 'availableStock', width: 90 },
    { title: '投/售/锁', dataIndex: 'stock', width: 170 },
    { title: '价格', dataIndex: 'price', width: 120 },
    { title: '规格状态', dataIndex: 'skuStatus', width: 100 },
    { title: '商品状态', dataIndex: 'commodityStatus', width: 100 },
    { title: '操作', dataIndex: 'action', width: 90, fixed: 'right' },
  ]);

  function commodityStatusMeta(value) {
    return Object.values(COMMODITY_STATUS_ENUM).find((item) => item.value === value) || { desc: value, color: 'default' };
  }

  function skuAttrsText(raw) {
    if (!raw) {
      return '无规格';
    }
    try {
      const attrs = typeof raw === 'string' ? JSON.parse(raw) : raw;
      const parts = Object.entries(attrs).map(([k, v]) => `${k}: ${v}`);
      return parts.length ? parts.join(' / ') : '无规格';
    } catch (e) {
      // 脏数据不该让整行渲染不出来
      return '无规格';
    }
  }

  /** 停用的规格不参与告警配色：它本来就不卖，缺不缺货不重要 */
  function availableClass(record) {
    if (record.skuStatus !== SKU_STATUS_ENUM.ENABLED.value) {
      return 'is-muted';
    }
    if (record.availableStock <= 0) {
      return 'is-empty';
    }
    return record.availableStock <= LOW_STOCK_THRESHOLD ? 'is-low' : 'is-ok';
  }

  function rowClassName(record) {
    return record.skuStatus === SKU_STATUS_ENUM.ENABLED.value && record.availableStock <= 0 ? 'row-empty' : '';
  }

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    commodityName: undefined,
    commodityCode: undefined,
    skuCode: undefined,
    categoryId: undefined,
    skuStatus: undefined,
    commodityStatus: undefined,
    stockBelow: undefined,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);
  const categoryOptions = ref([]);

  const onlyLowStock = computed(() => queryForm.stockBelow === LOW_STOCK_THRESHOLD);

  function toggleLowStock() {
    queryForm.stockBelow = onlyLowStock.value ? undefined : LOW_STOCK_THRESHOLD;
    onSearch();
  }

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

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await mallSkuApi.queryPage(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  async function loadCategory() {
    try {
      const res = await mallCategoryApi.enabledList();
      categoryOptions.value = (res.data || []).map((item) => ({ value: item.id, label: item.categoryName }));
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  onMounted(() => {
    loadCategory();
    queryData();
  });

  function goEdit(record) {
    router.push({ path: COMMODITY_EDITOR_PATH, query: { id: record.commodityId } });
  }
</script>

<style scoped lang="less">
  .toolbar-tip,
  .cell-sub {
    font-size: 12px;
    color: #bfbfbf;
  }

  .toolbar-tip {
    margin-left: 10px;
  }

  .commodity-cell {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .stock-detail {
    font-size: 12px;
    color: #64748b;
  }

  .available {
    font-size: 16px;
    font-weight: 600;
  }

  .available.is-ok {
    color: #059669;
  }

  .available.is-low {
    color: #d97706;
  }

  .available.is-empty {
    color: #ef4444;
  }

  .available.is-muted {
    color: #cbd5e1;
  }

  :deep(.row-empty) {
    background: #fff7f7;
  }
</style>
