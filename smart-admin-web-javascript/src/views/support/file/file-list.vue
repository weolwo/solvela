<!--
  * 素材库
  *
  * 交互三层：分类卡片 → 素材网格 → 右侧详情抽屉。
  *
  * 为什么网格不是表格：运营找图靠的是看，不是读文件名。原先的 a-table 对通用文件管理够用，
  * 对运营素材场景是错的 —— 一屏十几行文件名，谁也认不出哪张是双十一主视觉。
  *
  * 为什么保留「全部素材」入口：分类只是维度之一，运营找三个月前那张图时
  * 往往不记得它在哪个分类，强制先选分类会很难受。
  *
  * @Copyright  1024创新实验室 （ https://1024lab.net ）
-->
<template>
  <!---------- 分类卡片页 begin ----------->
  <a-card v-if="mode === 'category'" size="small" :bordered="false" :hoverable="true">
    <div class="page-header">
      <span class="page-title">素材库</span>
      <a-button type="primary" @click="showUploadModal">上传素材</a-button>
    </div>

    <a-spin :spinning="categoryLoading">
      <div class="category-grid">
        <!-- 「全部素材」放在第一张：不用先选分类也能搜 -->
        <div class="category-card all" @click="enterCategory(null)">
          <div class="category-name">全部素材</div>
          <div class="category-count">{{ totalFileCount }} 个文件</div>
        </div>

        <div v-for="item in categoryList" :key="item.categoryId" class="category-card" @click="enterCategory(item)">
          <div class="category-name">
            {{ item.categoryName }}
            <a-tag v-if="item.categoryTag" color="blue" size="small">{{ item.categoryTag }}</a-tag>
          </div>
          <div class="category-count">{{ item.fileCount }} 个文件</div>
          <div class="category-code">{{ item.categoryCode }}</div>
        </div>
      </div>
    </a-spin>
  </a-card>
  <!---------- 分类卡片页 end ----------->

  <!---------- 素材网格页 begin ----------->
  <a-card v-else size="small" :bordered="false" :hoverable="true">
    <div class="page-header">
      <a-breadcrumb>
        <a-breadcrumb-item><a @click="backToCategory">素材库</a></a-breadcrumb-item>
        <a-breadcrumb-item>{{ currentCategoryName }}</a-breadcrumb-item>
      </a-breadcrumb>
      <a-button type="primary" @click="showUploadModal">上传素材</a-button>
    </div>

    <a-form class="smart-query-form">
      <a-row class="smart-query-form-row">
        <a-form-item label="文件名" class="smart-query-form-item">
          <a-input style="width: 150px" v-model:value="queryForm.originalName" placeholder="文件名" allow-clear />
        </a-form-item>
        <a-form-item label="标签" class="smart-query-form-item">
          <a-input style="width: 130px" v-model:value="queryForm.tag" placeholder="标签" allow-clear />
        </a-form-item>
        <a-form-item label="扩展名" class="smart-query-form-item">
          <a-input style="width: 110px" v-model:value="queryForm.extension" placeholder="如 png" allow-clear />
        </a-form-item>
        <a-form-item label="上传人" class="smart-query-form-item">
          <a-input style="width: 120px" v-model:value="queryForm.createBy" placeholder="上传人" allow-clear />
        </a-form-item>
        <a-form-item label="上传时间" class="smart-query-form-item">
          <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 220px" @change="onChangeCreateTime" />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-button type="primary" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery" class="smart-margin-left10">重置</a-button>
        </a-form-item>
      </a-row>
    </a-form>

    <!-- 批量条：只在选中时出现。刻意没有「批量删除」—— 删除不可恢复，不该做成批量操作 -->
    <div v-if="selectedIds.length" class="batch-bar">
      <span>已选 {{ selectedIds.length }} 项</span>
      <a-select v-model:value="batchTags" mode="tags" style="width: 260px" placeholder="输入标签后回车" :token-separators="[',', '，']" />
      <a-button type="primary" size="small" :loading="batchSaving" @click="batchAddTags">批量打标签</a-button>
      <a-button size="small" @click="clearSelection">取消选择</a-button>
    </div>

    <a-spin :spinning="tableLoading">
      <div class="file-grid">
        <div v-for="file in fileList" :key="file.fileId" class="file-card" :class="{ selected: isSelected(file.fileId) }">
          <a-checkbox class="file-check" :checked="isSelected(file.fileId)" @change="toggleSelect(file.fileId)" />
          <div class="file-thumb" @click="openDetail(file)">
            <img v-if="isImage(file)" :src="file.fileUrl" :alt="file.originalName" loading="lazy" />
            <div v-else class="thumb-placeholder">{{ (file.extension || 'FILE').toUpperCase() }}</div>
          </div>
          <div class="file-name" :title="file.originalName" @click="openDetail(file)">{{ file.originalName }}</div>
          <div class="file-sub">{{ readableSize(file.fileSize) }}</div>
        </div>
      </div>
      <a-empty v-if="!tableLoading && !fileList.length" description="这个分类下还没有素材" />
    </a-spin>

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
        :show-total="(t) => `共${t}条`"
      />
    </div>
  </a-card>
  <!---------- 素材网格页 end ----------->

  <FileDetailDrawer ref="detailDrawerRef" @reload="onDetailChanged" />

  <a-modal v-model:open="uploadModalFlag" title="上传素材" @onCancel="hideUploadModal" @ok="hideUploadModal">
    <FileUpload
      list-type="text"
      :maxUploadSize="10"
      buttonText="点击上传"
      :defaultFileList="[]"
      :multiple="true"
      :folder="uploadFolder"
    />
  </a-modal>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { fileApi } from '/@/api/support/file-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { smartSentry } from '/@/lib/smart-sentry';
  import FileUpload from '/@/components/support/file-upload/index.vue';
  import FileDetailDrawer from './components/file-detail-drawer.vue';

  // ---------------------------- 视图切换 ----------------------------

  const mode = ref('category');
  const currentCategory = ref(null);

  const currentCategoryName = computed(() => (currentCategory.value ? currentCategory.value.categoryName : '全部素材'));

  // 上传落到哪个分类：在某个分类里就传那个分类，在「全部素材」里退回通用分类
  const COMMON_CATEGORY_ID = 1;
  const uploadFolder = computed(() => (currentCategory.value ? currentCategory.value.categoryId : COMMON_CATEGORY_ID));

  function enterCategory(category) {
    currentCategory.value = category;
    mode.value = 'grid';
    resetQuery();
  }

  function backToCategory() {
    mode.value = 'category';
    clearSelection();
    // 回到卡片页要重新拉计数：刚才可能传了或删了素材
    queryCategoryList();
  }

  // ---------------------------- 分类 ----------------------------

  const categoryList = ref([]);
  const categoryLoading = ref(false);

  const totalFileCount = computed(() => categoryList.value.reduce((sum, e) => sum + (e.fileCount || 0), 0));

  async function queryCategoryList() {
    categoryLoading.value = true;
    try {
      let res = await fileApi.getCategoryList();
      categoryList.value = res.data || [];
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      categoryLoading.value = false;
    }
  }

  // ---------------------------- 素材列表 ----------------------------

  const queryFormState = {
    categoryId: undefined,
    originalName: undefined,
    tag: undefined,
    extension: undefined,
    createBy: undefined,
    createTime: [],
    createTimeBegin: undefined,
    createTimeEnd: undefined,
    pageNum: 1,
    pageSize: 24,
  };
  const queryForm = reactive({ ...queryFormState });
  const fileList = ref([]);
  const total = ref(0);
  const tableLoading = ref(false);

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0];
    queryForm.createTimeEnd = dateStrings[1];
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function resetQuery() {
    Object.assign(queryForm, queryFormState);
    queryForm.createTime = [];
    // 分类是由外层视图决定的，不属于可重置的筛选条件
    queryForm.categoryId = currentCategory.value ? currentCategory.value.categoryId : undefined;
    clearSelection();
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      let res = await fileApi.queryPage(queryForm);
      fileList.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function isImage(file) {
    return (file.contentType || '').startsWith('image/');
  }

  // ---------------------------- 多选与批量打标签 ----------------------------

  const selectedIds = ref([]);
  const batchTags = ref([]);
  const batchSaving = ref(false);

  function isSelected(fileId) {
    return selectedIds.value.includes(fileId);
  }

  function toggleSelect(fileId) {
    const idx = selectedIds.value.indexOf(fileId);
    if (idx >= 0) {
      selectedIds.value.splice(idx, 1);
    } else {
      selectedIds.value.push(fileId);
    }
  }

  function clearSelection() {
    selectedIds.value = [];
    batchTags.value = [];
  }

  /**
   * 批量打标签是「追加」不是「覆盖」：一次活动传二十张图，逐个打标签会疯，
   * 但覆盖会把之前打好的标签抹掉，那不是运营想要的。
   */
  async function batchAddTags() {
    if (!batchTags.value.length) {
      message.warning('请先输入标签');
      return;
    }
    batchSaving.value = true;
    try {
      const selectedFiles = fileList.value.filter((f) => selectedIds.value.includes(f.fileId));
      for (const file of selectedFiles) {
        const merged = Array.from(new Set([...(file.tags || '').split(',').filter((t) => t), ...batchTags.value]));
        await fileApi.updateMeta({ fileId: file.fileId, tags: merged });
      }
      message.success(`已为 ${selectedFiles.length} 个素材打标签`);
      clearSelection();
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      batchSaving.value = false;
    }
  }

  // ---------------------------- 详情抽屉 ----------------------------

  const detailDrawerRef = ref();

  function openDetail(file) {
    detailDrawerRef.value.show(file.fileId);
  }

  function onDetailChanged() {
    queryData();
  }

  // ---------------------------- 上传 ----------------------------

  const uploadModalFlag = ref(false);

  function showUploadModal() {
    uploadModalFlag.value = true;
  }

  function hideUploadModal() {
    uploadModalFlag.value = false;
    if (mode.value === 'grid') {
      queryData();
    } else {
      queryCategoryList();
    }
  }

  function readableSize(size) {
    if (!size) return '';
    const num = 1024.0;
    if (size < num) return size + 'B';
    if (size < Math.pow(num, 2)) return (size / num).toFixed(2) + 'KB';
    if (size < Math.pow(num, 3)) return (size / Math.pow(num, 2)).toFixed(2) + 'MB';
    return (size / Math.pow(num, 3)).toFixed(2) + 'GB';
  }

  onMounted(queryCategoryList);
</script>

<style scoped lang="less">
  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }

  .page-title {
    font-size: 16px;
    font-weight: 600;
  }

  .category-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
  }

  .category-card {
    border: 1px solid #f0f0f0;
    border-radius: 6px;
    padding: 16px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: #1890ff;
      box-shadow: 0 2px 8px rgb(0 0 0 / 9%);
    }

    &.all {
      background: #f6faff;
      border-color: #d6e8ff;
    }
  }

  .category-name {
    font-size: 15px;
    font-weight: 600;
    margin-bottom: 6px;
  }

  .category-count {
    color: #888;
    font-size: 13px;
  }

  .category-code {
    color: #bfbfbf;
    font-size: 12px;
    margin-top: 4px;
  }

  .batch-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    background: #f6faff;
    border: 1px solid #d6e8ff;
    border-radius: 4px;
    margin-bottom: 12px;
  }

  .file-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 14px;
  }

  .file-card {
    position: relative;
    border: 1px solid #f0f0f0;
    border-radius: 6px;
    padding: 8px;
    transition: all 0.2s;

    &:hover {
      border-color: #1890ff;
    }

    &.selected {
      border-color: #1890ff;
      background: #f6faff;
    }
  }

  .file-check {
    position: absolute;
    top: 6px;
    left: 6px;
    z-index: 2;
  }

  .file-thumb {
    height: 110px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fafafa;
    border-radius: 4px;
    overflow: hidden;
    cursor: pointer;

    img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
    }
  }

  .thumb-placeholder {
    font-size: 18px;
    font-weight: 600;
    color: #bfbfbf;
    letter-spacing: 1px;
  }

  .file-name {
    margin-top: 6px;
    font-size: 13px;
    cursor: pointer;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .file-sub {
    color: #bfbfbf;
    font-size: 12px;
  }
</style>
