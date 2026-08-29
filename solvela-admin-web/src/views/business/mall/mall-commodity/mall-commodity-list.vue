<!--
  * 商城-商品列表
  *
  * 新建 / 编辑都跳整页编辑器（mall-commodity-editor.vue），不用弹窗：
  * 商品是主子表（主表 + N 个 SKU + 轮播图 + 富文本详情），弹窗装不下。
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:29:59
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="商品名称" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.commodityName" placeholder="支持模糊搜索" @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="商品编码" class="solvela-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.commodityCode" placeholder="10 位编码" @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="分类" class="solvela-query-form-item">
        <a-select style="width: 160px" v-model:value="queryForm.categoryId" :options="categoryOptions" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="类型" class="solvela-query-form-item">
        <a-select style="width: 140px" v-model:value="queryForm.commodityType" :options="COMMODITY_TYPE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="状态" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="queryForm.status" :options="COMMODITY_STATUS_OPTIONS" placeholder="全部" allow-clear />
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
        <a-button @click="goCreate" type="primary" size="small">
          <template #icon>
            <PlusOutlined />
          </template>
          新建商品
        </a-button>
        <a-button @click="confirmBatchDelete" type="primary" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <DeleteOutlined />
          </template>
          批量删除
        </a-button>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>
    <!---------- 表格操作行 end ----------->

    <!---------- 表格 begin ----------->
    <a-table
      size="small"
      :scroll="{ x: 1400 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'commodityName'">
          <div class="commodity-cell">
            <FileThumb :file-id="record.coverFileId" :height="48" />
            <div class="commodity-cell-text">
              <div class="commodity-name">{{ record.commodityName }}</div>
              <div class="commodity-code">{{ record.commodityCode }}</div>
            </div>
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'commodityType'">
          {{ typeDesc(text) }}
        </template>

        <template v-else-if="column.dataIndex === 'price'">
          <div>{{ record.pointsPrice }} 积分</div>
          <div v-if="record.payType === PAY_TYPE_ENUM.POINTS_CASH.value" class="cell-sub">+ ￥{{ record.cashPrice }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'stock'">
          <!-- 「投 100 / 售 37 / 余 61」：只显示总库存的话，运营解释不了为什么只剩这么多 -->
          <div>余 {{ record.availableStock }} / 共 {{ record.totalStock }}</div>
          <div class="cell-sub">{{ record.skuCount }} 个规格 · 已兑 {{ record.soldCount }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusMeta(text).color">{{ statusMeta(text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div class="solvela-table-operate">
            <a-button @click="goEdit(record)" type="link">编辑</a-button>
            <a-button
              v-if="record.status !== COMMODITY_STATUS_ENUM.ON.value"
              @click="onUpdateStatus(record, COMMODITY_STATUS_ENUM.ON.value)"
              type="link"
            >
              上架
            </a-button>
            <a-button v-else @click="onUpdateStatus(record, COMMODITY_STATUS_ENUM.OFF.value)" type="link">下架</a-button>
            <a-button @click="onDelete(record)" danger type="link">删除</a-button>
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
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { message, Modal } from 'ant-design-vue';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { mallCommodityApi } from '/@/api/business/mall/mall-commodity-api';
  import { mallCategoryApi } from '/@/api/business/mall/mall-category-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import {
    COMMODITY_STATUS_ENUM,
    COMMODITY_STATUS_OPTIONS,
    COMMODITY_TYPE_ENUM,
    COMMODITY_TYPE_OPTIONS,
    PAY_TYPE_ENUM,
  } from '/@/constants/business/mall/mall-commodity-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import FileThumb from '/@/components/support/file-thumb/index.vue';

  /** 编辑器的路由。它是一条隐藏菜单（visible_flag=0），列表是它唯一的入口 */
  const COMMODITY_EDITOR_PATH = '/mall/mall-commodity/editor';

  const route = useRoute();
  const router = useRouter();

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: '商品',
      dataIndex: 'commodityName',
      width: 280,
    },
    {
      title: '分类',
      dataIndex: 'categoryName',
      width: 120,
      ellipsis: true,
    },
    {
      title: '类型',
      dataIndex: 'commodityType',
      width: 90,
    },
    {
      title: '价格',
      dataIndex: 'price',
      width: 120,
    },
    {
      title: '库存',
      dataIndex: 'stock',
      width: 170,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
    },
    {
      title: '排序',
      dataIndex: 'sort',
      width: 70,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      ellipsis: true,
    },
    {
      title: '操作',
      dataIndex: 'action',
      fixed: 'right',
      width: 170,
    },
  ]);

  function statusMeta(status) {
    return Object.values(COMMODITY_STATUS_ENUM).find((item) => item.value === status) || COMMODITY_STATUS_ENUM.DRAFT;
  }

  function typeDesc(type) {
    const meta = Object.values(COMMODITY_TYPE_ENUM).find((item) => item.value === type);
    return meta ? meta.desc : type;
  }

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    commodityCode: undefined,
    commodityName: undefined,
    commodityType: undefined,
    categoryId: undefined,
    status: undefined,
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
  // 分类下拉
  const categoryOptions = ref([]);

  // 重置查询条件
  function resetQuery() {
    let pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    // 地址栏上的定位参数也要清掉，否则刷新一下又回到被过滤的状态
    if (route.query.commodityName) {
      router.replace({ path: route.path, query: {} });
    }
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
      let queryResult = await mallCommodityApi.queryPage(queryForm);
      tableData.value = queryResult.list;
      total.value = queryResult.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  async function loadCategory() {
    try {
      const res = await mallCategoryApi.enabledList();
      categoryOptions.value = (res || []).map((item) => ({ value: item.id, label: item.categoryName }));
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  onMounted(() => {
    loadCategory();
    // 编辑页发布成功后会带着商品名跳回来 —— 列表按 sort 排，刚发布的那个不一定在第一页，
    // 不回填搜索条件的话运营还得自己搜一遍才能确认到底存没存进去
    if (route.query.commodityName) {
      queryForm.commodityName = String(route.query.commodityName);
    }
    queryData();
  });

  // ---------------------------- 新建 / 编辑：跳整页编辑器 ----------------------------

  function goCreate() {
    router.push(COMMODITY_EDITOR_PATH);
  }

  function goEdit(record) {
    router.push({ path: COMMODITY_EDITOR_PATH, query: { id: record.id } });
  }

  // ---------------------------- 上架 / 下架 ----------------------------

  /**
   * 快捷开关走的是和保存同一组上架校验（没有启用规格、分类已停用都会被拒），
   * 所以这里只要把后端的提示原样显示出来即可。
   */
  async function onUpdateStatus(record, status) {
    SolvelaLoading.show();
    try {
      await mallCommodityApi.updateStatus(record.id, status);
      message.success(status === COMMODITY_STATUS_ENUM.ON.value ? '已上架' : '已下架');
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }

  // ---------------------------- 单个删除 ----------------------------
  //确认删除
  function onDelete(data) {
    Modal.confirm({
      title: '提示',
      content: `确定要删除商品「${data.commodityName}」吗？已产生兑换订单的商品不能删除，只能下架。`,
      okText: '删除',
      okType: 'danger',
      onOk() {
        requestDelete(data);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  //请求删除
  async function requestDelete(data) {
    SolvelaLoading.show();
    try {
      await mallCommodityApi.delete(data.id);
      message.success('删除成功');
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }

  // ---------------------------- 批量删除 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  // 批量删除
  function confirmBatchDelete() {
    Modal.confirm({
      title: '提示',
      content: '确定要批量删除这些商品吗？只要其中一个已产生兑换订单，整批都不会删除。',
      okText: '删除',
      okType: 'danger',
      onOk() {
        requestBatchDelete();
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  //请求批量删除
  async function requestBatchDelete() {
    try {
      SolvelaLoading.show();
      await mallCommodityApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      selectedRowKeyList.value = [];
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }
</script>

<style scoped lang="less">
  .commodity-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .commodity-cell-text {
    min-width: 0;
  }

  .commodity-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .commodity-code,
  .cell-sub {
    color: #bfbfbf;
    font-size: 12px;
  }
</style>
