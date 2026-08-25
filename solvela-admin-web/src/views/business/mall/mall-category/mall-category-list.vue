<!--
  * 商城-商品分类
  *
  * 树表而不是平铺列表：分类是两级结构，平铺的话「手机配件」和「数码3C」并排出现，
  * 看不出谁挂在谁下面 —— 而 C 端宫格导航正是按这个层级渲染的。
  *
  * 不分页：两级、量级在几十，分页会把「父在第 2 页、子在第 1 页」这种拼不出树的情况带进来。
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:28:16
  * @Copyright  weolwo
-->
<template>
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="分类名称" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="keyword" placeholder="支持模糊搜索" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="状态" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="statusFilter" :options="CATEGORY_STATUS_OPTIONS" placeholder="全部" allow-clear />
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
        <a-button type="primary" size="small" @click="showBatchForm()">
          <template #icon>
            <PlusOutlined />
          </template>
          新建分类
        </a-button>
        <a-button size="small" @click="expandAll">{{ expandedRowKeys.length ? '全部收起' : '全部展开' }}</a-button>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      row-key="id"
      bordered
      :data-source="treeData"
      :columns="columns"
      :loading="tableLoading"
      :pagination="false"
      :expanded-row-keys="expandedRowKeys"
      @expand="onExpand"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'categoryName'">
          <div class="category-cell">
            <FileThumb v-if="record.iconFileId" :file-id="record.iconFileId" :height="28" />
            <span v-else class="icon-placeholder">—</span>
            <span>{{ record.categoryName }}</span>
            <a-tag v-if="!record.parentId" color="blue">一级</a-tag>
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'commodityCount'">
          <!-- 直接显示出来，运营点删除之前就知道会不会被拦 -->
          <span :class="record.commodityCount > 0 ? '' : 'cell-muted'">{{ record.commodityCount || 0 }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-switch
            :checked="text === CATEGORY_STATUS_ENUM.ENABLED.value"
            checked-children="启用"
            un-checked-children="停用"
            @change="(checked) => onToggleStatus(record, checked)"
          />
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div class="solvela-table-operate">
            <!-- 二级分类下面不能再挂分类，所以只有一级才有「加子分类」。
                 走批量表单：一个一级分类下往往要一次加好几个子分类 -->
            <a-button v-if="!record.parentId" type="link" @click="showBatchForm(record.id)">加子分类</a-button>
            <a-button type="link" @click="showForm(record)">编辑</a-button>
            <a-button danger type="link" @click="onDelete(record)">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>
  </a-card>

  <MallCategoryForm ref="formRef" @reloadList="queryData" />
</template>

<script setup>
  import { computed, onMounted, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { mallCategoryApi } from '/@/api/business/mall/mall-category-api';
  import { CATEGORY_STATUS_ENUM, CATEGORY_STATUS_OPTIONS } from '/@/constants/business/mall/mall-category-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import FileThumb from '/@/components/support/file-thumb/index.vue';
  import MallCategoryForm from './mall-category-form.vue';

  const columns = ref([
    { title: '分类名称', dataIndex: 'categoryName', width: 300 },
    { title: '商品数', dataIndex: 'commodityCount', width: 90 },
    { title: '排序', dataIndex: 'sort', width: 80 },
    { title: '状态', dataIndex: 'status', width: 100 },
    { title: '创建人', dataIndex: 'createBy', width: 120, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', width: 170, ellipsis: true },
    { title: '操作', dataIndex: 'action', width: 200, fixed: 'right' },
  ]);

  const keyword = ref(undefined);
  const statusFilter = ref(undefined);
  const tableLoading = ref(false);
  const flatList = ref([]);
  const expandedRowKeys = ref([]);

  /**
   * 拼两级树。只认一层 children，不做递归 —— 递归反而会把「运营手滑建出的三级」渲染出来，
   * 让一个本该被服务端拦下的错误看起来是正常的。
   */
  const treeData = computed(() => {
    const kw = (keyword.value || '').trim();
    const matched = (c) =>
      (!kw || (c.categoryName || '').includes(kw)) &&
      (statusFilter.value === undefined || statusFilter.value === null || c.status === statusFilter.value);

    const roots = flatList.value.filter((c) => !c.parentId);
    const childrenOf = (id) => flatList.value.filter((c) => c.parentId === id);

    const result = [];
    roots.forEach((root) => {
      const children = childrenOf(root.id).filter(matched);
      // 父级自己不匹配但有匹配的子级时，父级仍要出现 —— 否则那些子级就没有挂载点了
      if (matched(root) || children.length) {
        result.push(children.length ? { ...root, children } : { ...root });
      }
    });
    return result;
  });

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await mallCategoryApi.queryAll();
      flatList.value = res.data || [];
      // 默认全展开：两级、几十条，收起来反而要多点一次才看得到子分类
      expandedRowKeys.value = flatList.value.filter((c) => !c.parentId).map((c) => c.id);
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  function onSearch() {
    // 树是前端按 flatList 现算的，搜索不用回服务端
    expandedRowKeys.value = flatList.value.filter((c) => !c.parentId).map((c) => c.id);
  }

  function resetQuery() {
    keyword.value = undefined;
    statusFilter.value = undefined;
    onSearch();
  }

  function onExpand(expanded, record) {
    if (expanded) {
      expandedRowKeys.value = [...expandedRowKeys.value, record.id];
    } else {
      expandedRowKeys.value = expandedRowKeys.value.filter((k) => k !== record.id);
    }
  }

  function expandAll() {
    expandedRowKeys.value = expandedRowKeys.value.length ? [] : flatList.value.filter((c) => !c.parentId).map((c) => c.id);
  }

  // ---------------------------- 新建 / 编辑 ----------------------------

  const formRef = ref();

  /**
   * 新建与编辑<b>共用同一个弹窗</b>：编辑态就是「只有一行、且那一行锁定行数」的同一张表。
   * 拆成两个组件的话，运营点「编辑」会跳出一个和刚才新建时完全不一样的界面。
   *
   * 两处都把全量分类传过去 —— 上级下拉、重名判断、「有没有子分类」都从它算，
   * 弹窗不必再查一次。
   */
  function showForm(record) {
    formRef.value.showEdit(record, flatList.value);
  }

  /**
   * 新建是批量的：分类结构基本是「建一次就不怎么动」的东西，而那一次往往要一口气
   * 把整棵树搭出来。只填一行的话它就等价于单条新建，不多花任何操作。
   */
  function showBatchForm(presetParentId) {
    formRef.value.show(flatList.value, presetParentId);
  }

  // ---------------------------- 启用 / 停用 ----------------------------

  async function onToggleStatus(record, checked) {
    const status = checked ? CATEGORY_STATUS_ENUM.ENABLED.value : CATEGORY_STATUS_ENUM.DISABLED.value;
    const childCount = flatList.value.filter((c) => c.parentId === record.id).length;
    // 停用一级分类会连带让它的子分类在 C 端不可见，点之前该知道波及多大
    if (!checked && childCount > 0) {
      Modal.confirm({
        title: '确认停用？',
        content: `停用「${record.categoryName}」后，它下面 ${childCount} 个子分类在 C 端也会一并不可见。`,
        okText: '停用',
        okType: 'danger',
        cancelText: '取消',
        onOk: () => requestStatus(record, status),
      });
      return;
    }
    await requestStatus(record, status);
  }

  async function requestStatus(record, status) {
    SolvelaLoading.show();
    try {
      await mallCategoryApi.updateStatus(record.id, status);
      message.success(status === CATEGORY_STATUS_ENUM.ENABLED.value ? '已启用' : '已停用');
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
      // 服务端拒绝时（如上级已停用）要把开关拨回去 —— 重查即可
      queryData();
    } finally {
      SolvelaLoading.hide();
    }
  }

  // ---------------------------- 删除 ----------------------------

  function onDelete(record) {
    const childCount = flatList.value.filter((c) => c.parentId === record.id).length;
    let hint = '';
    if (childCount > 0) {
      hint = `它下面还有 ${childCount} 个子分类，需要先处理它们。`;
    } else if (record.commodityCount > 0) {
      hint = `它下面还有 ${record.commodityCount} 个商品，需要先把商品移到别的分类。`;
    }
    Modal.confirm({
      title: '提示',
      content: `确定删除分类「${record.categoryName}」吗？${hint}不想用了也可以改为停用。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => requestDelete(record),
    });
  }

  async function requestDelete(record) {
    SolvelaLoading.show();
    try {
      await mallCategoryApi.delete(record.id);
      message.success('删除成功');
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }
</script>

<style scoped lang="less">
  .category-cell {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .icon-placeholder,
  .cell-muted {
    color: #cbd5e1;
  }
</style>
