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
    <!--
      这一页刻意没有全局的「上传素材」按钮：文件必须落进某个分类，
      而在分类卡片页按下它时「哪个分类」是没有答案的 —— 原先默认丢进「通用」，
      运营看到的是"传完了但不在我以为的地方"。上传入口做到每张卡片上，分类就是自明的。
    -->
    <div class="page-header">
      <span class="page-title">素材库</span>
      <a-button @click="openCategoryModal(null)">新建分类</a-button>
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

          <!-- 卡片操作。@click.stop 不能少，否则点编辑会连带触发卡片的进入分类 -->
          <div class="category-actions" @click.stop>
            <!-- 上传直接挂在卡片上：点哪张卡就传进哪个分类，不需要再问一次 -->
            <a-upload :show-upload-list="false" :multiple="true" :custom-request="(opt) => customUpload(opt, item.categoryId)">
              <a>上传</a>
            </a-upload>
            <a @click="openCategoryModal(item)">编辑</a>
            <a-tooltip v-if="item.systemFlag" title="内置分类被代码引用，不能删除">
              <span class="disabled-link">删除</span>
            </a-tooltip>
            <a-tooltip v-else-if="item.fileCount > 0" title="分类下还有文件，不能删除">
              <span class="disabled-link">删除</span>
            </a-tooltip>
            <a-popconfirm v-else title="确定删除该分类？" @confirm="removeCategory(item)">
              <a class="danger-link">删除</a>
            </a-popconfirm>
          </div>
        </div>
      </div>
    </a-spin>
  </a-card>
  <!---------- 分类卡片页 end ----------->

  <!---------- 素材网格页 begin ----------->
  <a-card v-else size="small" :bordered="false" :hoverable="true">
    <div class="page-header">
      <!--
        返回上一层必须有一个显式的按钮：面包屑那条「素材库」链接在视觉上只是导航文字，
        运营没把它当成可点的东西，结果是进了分类就出不来、只能关页面重点菜单。
        按钮留着面包屑一起 —— 面包屑负责说明「我在哪」，按钮负责「怎么回去」。
      -->
      <a-space>
        <a-button size="small" @click="backToCategory">
          <template #icon><LeftOutlined /></template>
          返回分类
        </a-button>
        <a-breadcrumb>
          <a-breadcrumb-item><a @click="backToCategory">素材库</a></a-breadcrumb-item>
          <a-breadcrumb-item>{{ currentCategoryName }}</a-breadcrumb-item>
        </a-breadcrumb>
      </a-space>
      <!--
        点按钮直接弹系统文件框，选完才出待传列表 —— 原先是先开一个弹窗、
        弹窗里再点一次「点击上传」才弹文件框，中间那一步什么都没问，纯属多一次点击。
        「全部素材」里没有分类可言，退回通用分类，并在按钮上写明白传到哪。
      -->
      <a-upload :show-upload-list="false" :multiple="true" :custom-request="(opt) => customUpload(opt, uploadCategoryId)">
        <a-button type="primary">上传到「{{ uploadCategoryName }}」</a-button>
      </a-upload>
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
          <div class="thumb-wrap" @click="openDetail(file)">
            <FileThumb :file-id="file.fileId" :content-type="file.contentType" :extension="file.extension" :alt="file.originalName" />
          </div>
          <div class="file-name" :title="file.originalName" @click="openDetail(file)">{{ file.originalName }}</div>

          <!--
            卡片上直接给「标签 / 上传时间 / 引用情况」：找图靠扫，扫到一半还要点开详情
            才知道这张在不在用、是不是上个月那批，等于把网格退化成一个缩略图列表。
          -->
          <div class="file-tags">
            <a-tag v-for="tag in splitTags(file.tags)" :key="tag" color="blue">{{ tag }}</a-tag>
            <span v-if="!splitTags(file.tags).length" class="file-muted">未打标签</span>
          </div>

          <div class="file-sub">
            <span>{{ readableSize(file.fileSize) }}</span>
            <span class="file-muted">{{ shortDate(file.createTime) }}</span>
          </div>

          <!-- 引用数是删除守卫的依据：有引用删不掉，先在卡片上说清楚，别等点了删除才报错 -->
          <div class="file-ref">
            <a-tag v-if="file.referenceCount > 0" color="green">引用 {{ file.referenceCount }}</a-tag>
            <a-tag v-else>未被引用</a-tag>
          </div>
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

  <a-modal v-model:open="categoryModalFlag" :title="categoryForm.categoryId ? '编辑分类' : '新建分类'" @ok="saveCategory" :confirm-loading="categorySaving">
    <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }" class="mt-4">
      <a-form-item label="分类编码" required>
        <a-input
          v-model:value="categoryForm.categoryCode"
          placeholder="字母数字下划线短横线"
          :disabled="categoryForm.systemFlag"
          :maxlength="50"
        />
        <!-- code 会成为 storageKey 的第一段前缀，所以字符集必须收窄；
             内置分类的 code 被代码引用着，改了等于把引用全部指空 -->
        <div class="form-tip">
          {{ categoryForm.systemFlag ? '内置分类的编码被代码引用，不可修改' : '会作为存储路径前缀，只允许字母、数字、下划线、短横线' }}
        </div>
      </a-form-item>
      <a-form-item label="分类名称" required>
        <a-input v-model:value="categoryForm.categoryName" placeholder="展示用名称，可随时改" :maxlength="50" />
      </a-form-item>
      <a-form-item label="标签">
        <a-input v-model:value="categoryForm.categoryTag" placeholder="选填，用于分组展示" :maxlength="32" />
      </a-form-item>
      <a-form-item label="排序">
        <a-input-number v-model:value="categoryForm.sort" :min="0" style="width: 120px" />
        <div class="form-tip">数字小的排前面</div>
      </a-form-item>
    </a-form>
  </a-modal>

  <!--
    待传列表在选完文件之后才出现，全成功就自己关掉 —— 没出错的时候不需要人来点一下确认。
    有失败的就留着，逐个说明哪一个为什么没传上去（类型不允许、超限都是后端逐条给的话）。
  -->
  <a-modal v-model:open="uploadPanelFlag" title="上传素材" :footer="null" :mask-closable="!uploading" @cancel="closeUploadPanel">
    <div v-for="task in uploadQueue" :key="task.uid" class="upload-task">
      <span class="upload-task-name" :title="task.name">{{ task.name }}</span>
      <span v-if="task.status === 'uploading'" class="upload-task-status"><a-spin size="small" /> 上传中</span>
      <span v-else-if="task.status === 'done'" class="upload-task-status success">已上传</span>
      <a-tooltip v-else :title="task.error">
        <span class="upload-task-status error">失败</span>
      </a-tooltip>
    </div>
    <div class="upload-panel-footer">
      <a-button size="small" :disabled="uploading" @click="closeUploadPanel">关闭</a-button>
    </div>
  </a-modal>
</template>

<script setup>
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { message } from 'ant-design-vue';
  import { LeftOutlined } from '@ant-design/icons-vue';
  import { fileApi } from '/@/api/support/file-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { FILE_FOLDER_TYPE_ENUM } from '/@/constants/support/file-const';
  import FileThumb from '/@/components/support/file-thumb/index.vue';
  import FileDetailDrawer from './components/file-detail-drawer.vue';

  // ---------------------------- 视图切换 ----------------------------

  const mode = ref('category');
  const currentCategory = ref(null);

  const currentCategoryName = computed(() => (currentCategory.value ? currentCategory.value.categoryName : '全部素材'));

  // 上传落到哪个分类：在某个分类里就传那个分类，在「全部素材」里退回通用分类。
  // 按钮上把分类名写出来，运营按下之前就知道会传到哪
  const COMMON_CATEGORY_ID = FILE_FOLDER_TYPE_ENUM.COMMON.value;
  const uploadCategoryId = computed(() => (currentCategory.value ? currentCategory.value.categoryId : COMMON_CATEGORY_ID));
  const uploadCategoryName = computed(() => {
    if (currentCategory.value) {
      return currentCategory.value.categoryName;
    }
    const common = categoryList.value.find((e) => e.categoryId === COMMON_CATEGORY_ID);
    return common ? common.categoryName : '通用';
  });

  /**
   * 「在哪一层」写进 URL 而不是只放在组件状态里。
   *
   * <p>只放在状态里的代价：**浏览器后退没有任何东西可退**（路由压根没变），
   * 刷新一下也会掉回分类页 —— 运营的表现是「进了分类就出不来，只能关掉重点菜单」。
   * 写进 query 之后后退键、刷新、以及把链接发给同事都成立了。
   *
   * <p>`categoryId=all` 是「全部素材」那一档：它同样是网格页，但没有具体分类，
   * 用缺省值表达不了（缺省已经被「分类卡片页」占了）。
   */
  const route = useRoute();
  const router = useRouter();

  function enterCategory(category) {
    router.push({ query: { categoryId: category ? category.categoryId : 'all' } });
  }

  function backToCategory() {
    router.push({ query: {} });
  }

  /**
   * URL → 视图。onMounted 与 watch(query) 共用一份，
   * 否则从别处带 query 跳回来时 path 没变、组件不重挂载，会停在上一次的分类里。
   */
  function initFromQuery() {
    const raw = route.query.categoryId;
    if (!raw) {
      const backFromGrid = mode.value === 'grid';
      mode.value = 'category';
      currentCategory.value = null;
      clearSelection();
      if (backFromGrid) {
        // 从网格页回来要重新拉计数：刚才可能传了或删了素材。
        // 首次挂载不用重拉 —— onMounted 里已经拉过一次了
        queryCategoryList();
      }
      return;
    }
    if (raw === 'all') {
      currentCategory.value = null;
    } else {
      const hit = categoryList.value.find((e) => String(e.categoryId) === String(raw));
      if (!hit) {
        // 分类被删了或 URL 是手敲的，退回卡片页，别把人晾在一个空网格里
        router.replace({ query: {} });
        return;
      }
      currentCategory.value = hit;
    }
    mode.value = 'grid';
    clearSelection();
    resetQuery();
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

  // ---------------------------- 分类增删改 ----------------------------

  const categoryModalFlag = ref(false);
  const categorySaving = ref(false);
  const categoryForm = reactive({ categoryId: null, categoryCode: '', categoryName: '', categoryTag: '', sort: 0, systemFlag: false });

  function openCategoryModal(item) {
    Object.assign(categoryForm, {
      categoryId: item ? item.categoryId : null,
      categoryCode: item ? item.categoryCode : '',
      categoryName: item ? item.categoryName : '',
      categoryTag: item ? item.categoryTag : '',
      sort: item ? item.sort : 0,
      systemFlag: item ? !!item.systemFlag : false,
    });
    categoryModalFlag.value = true;
  }

  async function saveCategory() {
    if (!categoryForm.categoryCode || !categoryForm.categoryName) {
      message.warning('分类编码与名称都不能为空');
      return;
    }
    categorySaving.value = true;
    try {
      const param = {
        categoryId: categoryForm.categoryId,
        categoryCode: categoryForm.categoryCode,
        categoryName: categoryForm.categoryName,
        categoryTag: categoryForm.categoryTag,
        sort: categoryForm.sort,
      };
      if (categoryForm.categoryId) {
        await fileApi.updateCategory(param);
      } else {
        await fileApi.addCategory(param);
      }
      message.success('已保存');
      categoryModalFlag.value = false;
      queryCategoryList();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      categorySaving.value = false;
    }
  }

  async function removeCategory(item) {
    try {
      await fileApi.deleteCategory(item.categoryId);
      message.success('已删除');
      queryCategoryList();
    } catch (e) {
      smartSentry.captureError(e);
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

  /**
   * 上传队列。a-upload 多选时会对每个文件各调一次 customRequest，
   * 所以进度是逐个文件维护的，不是一个全局的 loading。
   */
  const uploadQueue = ref([]);
  const uploadPanelFlag = ref(false);

  const uploading = computed(() => uploadQueue.value.some((e) => e.status === 'uploading'));

  async function customUpload({ file, onSuccess, onError }, categoryId) {
    // 选完文件才开面板：没选之前弹一个空框，就是原先那多出来的一次点击
    uploadPanelFlag.value = true;
    const task = reactive({ uid: file.uid || `${file.name}_${Date.now()}`, name: file.name, status: 'uploading', error: '' });
    uploadQueue.value.push(task);
    try {
      const formData = new FormData();
      formData.append('file', file);
      let res = await fileApi.uploadFile(formData, categoryId);
      task.status = 'done';
      onSuccess(res, file);
    } catch (e) {
      task.status = 'error';
      // 后端对类型/大小是逐条给人话的，原样带出来才知道这一个为什么没传上去
      task.error = e?.msg || e?.data?.msg || '上传失败';
      onError(e);
    } finally {
      afterOneUploaded();
    }
  }

  /** 一批全部结束后：刷新数据；全成功就自己关掉，有失败则留着让人看清是哪几个 */
  function afterOneUploaded() {
    if (uploading.value) {
      return;
    }
    refreshCurrentView();
    if (uploadQueue.value.every((e) => e.status === 'done')) {
      message.success(`已上传 ${uploadQueue.value.length} 个素材`);
      closeUploadPanel();
    }
  }

  function closeUploadPanel() {
    if (uploading.value) {
      return;
    }
    uploadPanelFlag.value = false;
    uploadQueue.value = [];
  }

  function refreshCurrentView() {
    if (mode.value === 'grid') {
      queryData();
    }
    // 分类卡片上的计数在两个视图下都要刷：网格页返回时不再重拉也能看到新数字
    queryCategoryList();
  }

  /** tags 后端存成 ,a,b, 的形式（前后带逗号是为了检索能精确匹配），展示时拆开 */
  function splitTags(tags) {
    if (!tags) return [];
    return tags.split(',').filter((t) => t);
  }

  /** 卡片位置有限，只留「几月几号」；完整时间在详情抽屉里 */
  function shortDate(createTime) {
    if (!createTime) return '';
    return createTime.substring(0, 10);
  }

  function readableSize(size) {
    if (!size) return '';
    const num = 1024.0;
    if (size < num) return size + 'B';
    if (size < Math.pow(num, 2)) return (size / num).toFixed(2) + 'KB';
    if (size < Math.pow(num, 3)) return (size / Math.pow(num, 2)).toFixed(2) + 'MB';
    return (size / Math.pow(num, 3)).toFixed(2) + 'GB';
  }

  watch(() => route.query.categoryId, initFromQuery);

  onMounted(async () => {
    // 必须先有分类列表再解析 URL：进的是哪个分类要靠 id 在列表里认领
    await queryCategoryList();
    initFromQuery();
  });
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

  .category-actions {
    margin-top: 10px;
    display: flex;
    gap: 12px;
    font-size: 12px;
  }

  .disabled-link {
    color: #d9d9d9;
    cursor: not-allowed;
  }

  .danger-link {
    color: #ff4d4f;
  }

  .form-tip {
    color: #bfbfbf;
    font-size: 12px;
    line-height: 18px;
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

  .thumb-wrap {
    cursor: pointer;
  }

  .upload-task {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 6px 0;
    border-bottom: 1px solid #f5f5f5;
  }

  .upload-task-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .upload-task-status {
    flex: none;
    color: #999;
    font-size: 12px;

    &.success {
      color: #52c41a;
    }

    &.error {
      color: #ff4d4f;
      cursor: help;
    }
  }

  .upload-panel-footer {
    display: flex;
    justify-content: flex-end;
    padding-top: 12px;
  }

  .file-name {
    margin-top: 6px;
    font-size: 13px;
    cursor: pointer;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .file-tags {
    margin-top: 4px;
    height: 22px;
    overflow: hidden;

    :deep(.ant-tag) {
      margin-inline-end: 4px;
      font-size: 11px;
      line-height: 18px;
    }
  }

  .file-sub {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    font-size: 12px;
    color: #999;
  }

  .file-muted {
    color: #bfbfbf;
  }

  .file-ref {
    margin-top: 4px;

    :deep(.ant-tag) {
      font-size: 11px;
      line-height: 18px;
    }
  }
</style>
