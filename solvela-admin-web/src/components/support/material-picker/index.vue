<!--
  * 素材选择器
  *
  * 素材库真正的复用价值在这里：没有它，同一张主视觉在三个活动里用就要存三份字节，
  * 「素材库」退化成一堆各自为政的上传记录。
  *
  * 与素材库页面（views/support/file/file-list.vue）刻意不共用一套代码：
  * 那边是「管理」——多选打标签、看引用、删除、管分类；这边是「挑一张」——
  * 左边分类、右边网格、点一下就选中。硬凑成一个组件会让两边都长出一堆
  * v-if="pickerMode"，反而更难改。共用的是接口和取数口径，不是模板。
  *
  * @Copyright  1024创新实验室 （ https://1024lab.net ）
-->
<template>
  <a-modal :open="visible" :title="title" width="920px" :destroy-on-close="true" @cancel="close">
    <div class="picker">
      <!-- 左侧分类。不做成下拉是因为运营找图第一动作就是按分类扫，藏进下拉里等于多一次点击 -->
      <div class="picker-side">
        <div class="side-item" :class="{ active: currentCategoryId === undefined }" @click="selectCategory(undefined)">
          <span>全部素材</span>
        </div>
        <div
          v-for="item in categoryList"
          :key="item.categoryId"
          class="side-item"
          :class="{ active: currentCategoryId === item.categoryId }"
          @click="selectCategory(item.categoryId)"
        >
          <span class="side-name" :title="item.categoryName">{{ item.categoryName }}</span>
          <span class="side-count">{{ item.fileCount }}</span>
        </div>
      </div>

      <div class="picker-main">
        <div class="picker-toolbar">
          <a-input v-model:value="queryForm.originalName" placeholder="文件名" allow-clear style="width: 160px" @press-enter="onSearch" />
          <a-input v-model:value="queryForm.tag" placeholder="标签" allow-clear style="width: 120px" @press-enter="onSearch" />
          <a-button type="primary" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
          <div class="toolbar-right">
            <!--
              选图器里直接能传：运营手上没有合适的图时，让他先关掉弹窗、跳去素材库、上传、
              再回来重新选，中间会丢掉正在填的表单。传完自动选中，接着就能确定。
            -->
            <a-upload :show-upload-list="false" :accept="accept" :custom-request="customUpload">
              <a-button :loading="uploading">上传新素材</a-button>
            </a-upload>
          </div>
        </div>

        <a-spin :spinning="loading">
          <div class="file-grid">
            <div
              v-for="file in fileList"
              :key="file.fileId"
              class="file-card"
              :class="{ selected: isSelected(file.fileId) }"
              @click="toggleSelect(file)"
            >
              <div class="thumb-wrap">
                <FileThumb
                  :file-id="file.fileId"
                  :file-url="file.fileUrl"
                  :content-type="file.contentType"
                  :extension="file.extension"
                  :alt="file.originalName"
                  :height="96"
                />
                <div v-if="isSelected(file.fileId)" class="check-badge">✓</div>
              </div>
              <div class="file-name" :title="file.originalName">{{ file.originalName }}</div>
              <div class="file-sub">{{ readableSize(file.fileSize) }}</div>
            </div>
          </div>
          <a-empty v-if="!loading && !fileList.length" description="没有找到素材，可以直接在这里上传" />
        </a-spin>

        <div class="picker-page">
          <a-pagination
            size="small"
            show-less-items
            v-model:current="queryForm.pageNum"
            v-model:pageSize="queryForm.pageSize"
            :total="total"
            :show-size-changer="false"
            @change="queryData"
            :show-total="(t) => `共${t}条`"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <span class="footer-hint">{{ footerHint }}</span>
      <a-button @click="close">取消</a-button>
      <a-button type="primary" :disabled="!selectedList.length" @click="confirm">确定</a-button>
    </template>
  </a-modal>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { fileApi } from '/@/api/support/file-api';
  import { FILE_CATEGORY_CODE } from '/@/constants/support/file-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import FileThumb from '/@/components/support/file-thumb/index.vue';

  const props = defineProps({
    title: { type: String, default: '选择素材' },
    // 只列图片。给图片字段用时必须为 true，否则主视觉能选出一个 PDF
    imageOnly: { type: Boolean, default: true },
    multiple: { type: Boolean, default: false },
    // 多选上限，0 表示不限
    limit: { type: Number, default: 0 },
    accept: { type: String, default: 'image/*' },
    /**
     * 没在左侧选具体分类时，弹窗内上传落到哪个分类（分类编码）。
     * 默认活动素材：这个选择器目前的调用方都是活动配图，而活动图必须是公开可读的分类。
     */
    defaultCategoryCode: { type: String, default: FILE_CATEGORY_CODE.ACTIVITY },
  });

  const emit = defineEmits(['select']);

  const visible = ref(false);
  const loading = ref(false);
  const uploading = ref(false);

  const categoryList = ref([]);
  const currentCategoryId = ref(undefined);

  const fileList = ref([]);
  const total = ref(0);

  // 选中的是完整文件对象而不只是 id：调用方要 fileUrl 做回显，
  // 只回 id 会逼每个调用方再查一次详情
  const selectedList = ref([]);

  const queryFormState = {
    categoryId: undefined,
    originalName: undefined,
    tag: undefined,
    imageOnly: undefined,
    pageNum: 1,
    pageSize: 20,
  };
  const queryForm = reactive({ ...queryFormState });

  const footerHint = computed(() => {
    if (!props.multiple) {
      return selectedList.value.length ? `已选：${selectedList.value[0].originalName}` : '点击图片选择';
    }
    return props.limit ? `已选 ${selectedList.value.length} / ${props.limit}` : `已选 ${selectedList.value.length} 项`;
  });

  /**
   * 打开选择器。每次打开都从第一页重新取数：
   * 上一次打开到现在，别人可能传了新素材，留着旧列表会让人以为"传了没生效"。
   */
  function show() {
    visible.value = true;
    selectedList.value = [];
    Object.assign(queryForm, queryFormState);
    queryForm.imageOnly = props.imageOnly ? true : undefined;
    currentCategoryId.value = undefined;
    queryCategoryList();
    queryData();
  }

  function close() {
    visible.value = false;
  }

  async function queryCategoryList() {
    try {
      let res = await fileApi.getCategoryList();
      categoryList.value = res.data || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  function selectCategory(categoryId) {
    currentCategoryId.value = categoryId;
    queryForm.categoryId = categoryId;
    queryForm.pageNum = 1;
    queryData();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function resetQuery() {
    queryForm.originalName = undefined;
    queryForm.tag = undefined;
    queryForm.pageNum = 1;
    queryData();
  }

  async function queryData() {
    loading.value = true;
    try {
      let res = await fileApi.queryPage(queryForm);
      fileList.value = res.data.list || [];
      total.value = res.data.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  function isSelected(fileId) {
    return selectedList.value.some((e) => e.fileId === fileId);
  }

  function toggleSelect(file) {
    if (!props.multiple) {
      selectedList.value = isSelected(file.fileId) ? [] : [file];
      return;
    }
    const idx = selectedList.value.findIndex((e) => e.fileId === file.fileId);
    if (idx >= 0) {
      selectedList.value.splice(idx, 1);
      return;
    }
    if (props.limit && selectedList.value.length >= props.limit) {
      message.warning(`最多选择 ${props.limit} 项`);
      return;
    }
    selectedList.value.push(file);
  }

  /**
   * 传进来的文件落在左侧选中的那个分类下；停在「全部素材」时落到 defaultCategoryCode。
   *
   * <p>选中分类时按 ID 传没问题 —— 那个 ID 是刚从服务端列表里拿的，不是代码里写死的数字；
   * 而缺省分类必须按编码传，理由见 file-const 里的说明。
   */
  async function customUpload({ file, onSuccess, onError }) {
    uploading.value = true;
    try {
      const formData = new FormData();
      formData.append('file', file);
      let res = currentCategoryId.value
        ? await fileApi.uploadFile(formData, currentCategoryId.value)
        : await fileApi.uploadFileByCategory(formData, props.defaultCategoryCode);
      onSuccess(res, file);
      message.success('上传成功');
      // 刚传的图直接选中：上传的动机就是"我要用这张"，还要再点一下是多余的。
      // 但 upload 接口的返回是 FileUploadVO（只有 fileId/fileUrl/originalName 等），
      // 与列表的 FileVO 不同源，所以刷新列表后按 fileId 认领，拿到的才是同一套字段
      await refreshAndSelect(res.data.fileId);
    } catch (e) {
      solvelaSentry.captureError(e);
      onError(e);
    } finally {
      uploading.value = false;
    }
  }

  async function refreshAndSelect(fileId) {
    // 新素材按创建时间倒序必在第一页，回到第一页才看得见
    queryForm.pageNum = 1;
    await queryData();
    const uploaded = fileList.value.find((e) => e.fileId === fileId);
    if (uploaded && !isSelected(fileId)) {
      toggleSelect(uploaded);
    }
  }

  function confirm() {
    if (!selectedList.value.length) {
      return;
    }
    emit('select', props.multiple ? [...selectedList.value] : selectedList.value[0]);
    close();
  }

  function readableSize(size) {
    if (!size) return '';
    const num = 1024.0;
    if (size < num) return size + 'B';
    if (size < Math.pow(num, 2)) return (size / num).toFixed(2) + 'KB';
    if (size < Math.pow(num, 3)) return (size / Math.pow(num, 2)).toFixed(2) + 'MB';
    return (size / Math.pow(num, 3)).toFixed(2) + 'GB';
  }

  defineExpose({ show });
</script>

<style scoped lang="less">
  .picker {
    display: flex;
    gap: 16px;
    min-height: 420px;
  }

  .picker-side {
    width: 160px;
    flex: none;
    border-right: 1px solid #f0f0f0;
    padding-right: 8px;
    max-height: 460px;
    overflow-y: auto;
  }

  .side-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 6px 10px;
    border-radius: 4px;
    cursor: pointer;
    font-size: 13px;

    &:hover {
      background: #f5f5f5;
    }

    &.active {
      background: #e6f4ff;
      color: #1890ff;
      font-weight: 600;
    }
  }

  .side-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .side-count {
    color: #bfbfbf;
    font-size: 12px;
  }

  .picker-main {
    flex: 1;
    min-width: 0;
  }

  .picker-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  .toolbar-right {
    margin-left: auto;
  }

  .file-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
    gap: 12px;
    max-height: 380px;
    overflow-y: auto;
  }

  .file-card {
    border: 1px solid #f0f0f0;
    border-radius: 6px;
    padding: 6px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: #1890ff;
    }

    &.selected {
      border-color: #1890ff;
      background: #f6faff;
    }
  }

  .thumb-wrap {
    position: relative;
  }

  .check-badge {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 20px;
    height: 20px;
    line-height: 20px;
    text-align: center;
    border-radius: 50%;
    background: #1890ff;
    color: #fff;
    font-size: 12px;
  }

  .file-name {
    margin-top: 6px;
    font-size: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .file-sub {
    color: #bfbfbf;
    font-size: 12px;
  }

  .picker-page {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }

  .footer-hint {
    float: left;
    color: #999;
    font-size: 12px;
    line-height: 32px;
  }
</style>
