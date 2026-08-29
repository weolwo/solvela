<!--
  * 素材详情抽屉
  *
  * 抽屉用来装「单个对象的详情与编辑」，这是它最擅长的场景。
  * 文件列表不放这里 —— 一个分类下几百张素材需要网格 + 分页 + 筛选 + 多选，
  * 塞进抽屉会很挤，抽屉里再套分页也很别扭。
  *
  * @Copyright  1024创新实验室 （ https://1024lab.net ）
-->
<template>
  <a-drawer :open="visible" title="素材详情" width="520" @close="close" :destroy-on-close="true">
    <a-spin :spinning="loading">
      <div v-if="detail.file">
        <!-- 预览区：图片给大图，其他给扩展名占位 -->
        <div class="preview">
          <!-- 用 a-image 是为了点击放大看细节；src 必须是带 token 取回来的 object URL，
               直接用后端下发的 fileUrl 只会得到一张打不开的图（下载接口要登录态） -->
          <a-image v-if="isImage && previewSrc" :src="previewSrc" :alt="detail.file.originalName" />
          <div v-else class="preview-placeholder">
            <div class="ext">{{ (detail.file.extension || 'FILE').toUpperCase() }}</div>
          </div>
        </div>

        <a-descriptions :column="1" size="small" bordered class="meta">
          <a-descriptions-item label="文件名">
            <a-input v-if="editing" v-model:value="editForm.originalName" placeholder="文件名" />
            <span v-else>{{ detail.file.originalName }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="标签">
            <!-- 编辑态用 mode=tags 的 select：运营既能选已有标签也能新建，
                 而已有标签来自当前列表，比让他凭记忆敲要靠谱 -->
            <a-select
              v-if="editing"
              v-model:value="editForm.tags"
              mode="tags"
              style="width: 100%"
              placeholder="回车新增标签"
              :token-separators="[',', '，']"
            />
            <template v-else>
              <a-tag v-for="tag in tagList" :key="tag" color="blue">{{ tag }}</a-tag>
              <span v-if="!tagList.length" class="empty">未打标签</span>
            </template>
          </a-descriptions-item>
          <a-descriptions-item label="尺寸" v-if="detail.file.imageWidth">
            {{ detail.file.imageWidth }} × {{ detail.file.imageHeight }}
          </a-descriptions-item>
          <a-descriptions-item label="大小">{{ readableSize(detail.file.fileSize) }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ detail.file.contentType }}</a-descriptions-item>
          <a-descriptions-item label="上传人">{{ detail.file.createBy || '-' }}</a-descriptions-item>
          <a-descriptions-item label="上传时间">{{ detail.file.createTime }}</a-descriptions-item>
          <a-descriptions-item label="存储键">
            <span class="storage-key">{{ detail.file.storageKey }}</span>
          </a-descriptions-item>
        </a-descriptions>

        <!--
          引用列表：这是本抽屉最有价值的一块。
          运营删图前看一眼「这张图正在 3 个活动里用」，比删完了才发现活动页变叉强得多。
        -->
        <div class="references">
          <div class="references-title">
            引用情况
            <a-tag :color="detail.references && detail.references.length ? 'orange' : 'green'">
              {{ detail.references ? detail.references.length : 0 }} 处
            </a-tag>
          </div>
          <a-empty v-if="!detail.references || !detail.references.length" description="没有业务在引用，可以安全删除" :image="simpleImage" />
          <a-list v-else size="small" :data-source="detail.references">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-tag>{{ bizTypeName(item.bizType) }}</a-tag>
                <span>ID：{{ item.bizId }}</span>
              </a-list-item>
            </template>
          </a-list>
        </div>
      </div>
    </a-spin>

    <template #footer>
      <div class="footer">
        <template v-if="editing">
          <a-button @click="cancelEdit">取消</a-button>
          <a-button type="primary" @click="saveMeta" :loading="saving">保存</a-button>
        </template>
        <template v-else>
          <a-popconfirm title="删除后不可恢复，确定删除？" ok-text="删除" cancel-text="取消" @confirm="remove" :disabled="hasReferences">
            <a-button danger :disabled="hasReferences">
              {{ hasReferences ? '被引用中，不能删除' : '删除' }}
            </a-button>
          </a-popconfirm>
          <a-button @click="download">下载</a-button>
          <a-button type="primary" @click="startEdit">编辑</a-button>
        </template>
      </div>
    </template>
  </a-drawer>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { Empty, message } from 'ant-design-vue';
  import { fileApi } from '/@/api/support/file-api';
  import { getFilePreviewUrl } from '/@/lib/file-preview';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const emit = defineEmits(['reload']);

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;

  const visible = ref(false);
  const loading = ref(false);
  const saving = ref(false);
  const editing = ref(false);
  const detail = reactive({ file: null, references: [] });
  const editForm = reactive({ originalName: '', tags: [] });
  const previewSrc = ref('');

  const isImage = computed(() => !!detail.file && (detail.file.contentType || '').startsWith('image/'));
  const hasReferences = computed(() => !!detail.references && detail.references.length > 0);

  // tags 后端存成 ,a,b, 的形式（前后带逗号是为了让检索能精确匹配），展示时拆开
  const tagList = computed(() => splitTags(detail.file && detail.file.tags));

  function splitTags(tags) {
    if (!tags) return [];
    return tags.split(',').filter((t) => t);
  }

  async function show(fileId) {
    visible.value = true;
    editing.value = false;
    loading.value = true;
    detail.file = null;
    detail.references = [];
    previewSrc.value = '';
    try {
      let res = await fileApi.detail(fileId);
      detail.file = res.file;
      detail.references = res.references || [];
      if (isImage.value) {
        // 取不到就留空，抽屉的其余信息（尤其引用列表）照样要能看
        previewSrc.value = await getFilePreviewUrl(fileId).catch(() => '');
      }
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  function close() {
    visible.value = false;
  }

  function startEdit() {
    editForm.originalName = detail.file.originalName;
    editForm.tags = splitTags(detail.file.tags);
    editing.value = true;
  }

  function cancelEdit() {
    editing.value = false;
  }

  async function saveMeta() {
    saving.value = true;
    try {
      await fileApi.updateMeta({
        fileId: detail.file.fileId,
        originalName: editForm.originalName,
        tags: editForm.tags,
      });
      message.success('已保存');
      editing.value = false;
      await show(detail.file.fileId);
      emit('reload');
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  async function remove() {
    try {
      await fileApi.delete(detail.file.fileId);
      message.success('已删除');
      close();
      emit('reload');
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  async function download() {
    try {
      await fileApi.downLoadFile(detail.file.storageKey);
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  /**
   * bizType 是开放字符串，后端刻意不去 join 业务对象的标题
   * （今天是活动、明天是商品，穷举不了所有业务表），中文名在前端映射。
   */
  const BIZ_TYPE_NAME = {
    ACTIVITY_DISPLAY: '活动展示',
    NOTICE: '公告',
    HELP_DOC: '帮助文档',
    FEEDBACK: '意见反馈',
  };

  function bizTypeName(bizType) {
    return BIZ_TYPE_NAME[bizType] || bizType;
  }

  function readableSize(size) {
    if (!size) return '-';
    const num = 1024.0;
    if (size < num) return size + 'B';
    if (size < Math.pow(num, 2)) return (size / num).toFixed(2) + 'KB';
    if (size < Math.pow(num, 3)) return (size / Math.pow(num, 2)).toFixed(2) + 'MB';
    return (size / Math.pow(num, 3)).toFixed(2) + 'GB';
  }

  defineExpose({ show });
</script>

<style scoped lang="less">
  .preview {
    text-align: center;
    margin-bottom: 16px;
    background: #fafafa;
    padding: 12px;
    border-radius: 4px;

    :deep(img) {
      max-height: 260px;
      object-fit: contain;
    }
  }

  .preview-placeholder {
    height: 160px;
    display: flex;
    align-items: center;
    justify-content: center;

    .ext {
      font-size: 28px;
      font-weight: 600;
      color: #bfbfbf;
      letter-spacing: 2px;
    }
  }

  .meta {
    margin-bottom: 16px;
  }

  .storage-key {
    word-break: break-all;
    font-size: 12px;
    color: #888;
  }

  .empty {
    color: #bfbfbf;
  }

  .references-title {
    font-weight: 600;
    margin-bottom: 8px;
  }

  .footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
</style>
