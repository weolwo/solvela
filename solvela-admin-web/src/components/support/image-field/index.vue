<!--
  * 图片字段：上传 or 从素材库选，表单里只存 fileId
  *
  * 从 activity-display-form 里的内联渲染函数抽出来的。抽出来有两个理由：
  * 一是加了「从素材库选」之后它已经不是一个"顺手写的小东西"了；
  * 二是渲染函数里写 `h('a-button')` 这类错误构建期完全发现不了（本模块踩过），
  * 而 SFC 模板里的未知组件 Vue 会在控制台直接警告。
  *
  * <p>只存 fileId 不存 URL：URL 里带着存储域名，存进业务表意味着将来换 CDN 要洗全表。
  *
  * @Copyright  1024创新实验室 （ https://1024lab.net ）
-->
<template>
  <div class="image-field">
    <FileThumb v-if="fileId" class="image-field-preview" :file-id="fileId" :file-url="knownUrl" :alt="tip" :height="64" />

    <a-space>
      <a-button @click="openPicker">{{ fileId ? '更换' : '从素材库选择' }}</a-button>
      <a-upload :show-upload-list="false" :accept="accept" :custom-request="customRequest">
        <a-button :loading="uploading">上传新图</a-button>
      </a-upload>
      <a-button v-if="fileId" danger type="link" @click="clear">移除</a-button>
    </a-space>

    <span v-if="tip" class="image-field-tip">{{ tip }}</span>

    <MaterialPicker ref="pickerRef" :title="pickerTitle" :default-category-code="categoryCode" @select="onPicked" />
  </div>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { fileApi } from '/@/api/support/file-api';
  import { FILE_CATEGORY_CODE } from '/@/constants/support/file-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import MaterialPicker from '/@/components/support/material-picker/index.vue';
  import FileThumb from '/@/components/support/file-thumb/index.vue';

  const props = defineProps({
    fileId: { type: Number, default: null },
    tip: { type: String, default: '' },
    pickerTitle: { type: String, default: '选择图片' },
    accept: { type: String, default: 'image/*' },
    /**
     * 上传落到哪个素材分类（分类编码）。默认活动素材 —— 这个组件的场景就是运营给活动配图，
     * 而活动图必须公开可读（C 端匿名用户取不到私有文件）。可见性由分类决定，不由这里决定。
     */
    categoryCode: { type: String, default: FILE_CATEGORY_CODE.ACTIVITY },
  });

  const emit = defineEmits(['update:fileId']);

  const uploading = ref(false);
  const pickerRef = ref();

  /**
   * 刚选/刚传的那张图的 URL。带着它是为了公开图能直接进 `<img>`，省一次取字节；
   * 连 fileId 一起记是必须的 —— 表单从外部换了值（切活动、重新加载）之后，
   * 上一张的 URL 必须立刻失效，否则会出现"换了图但显示的还是上一张"。
   */
  const known = ref({ fileId: null, url: '' });
  const knownUrl = computed(() => (known.value.fileId === props.fileId ? known.value.url : ''));

  function openPicker() {
    pickerRef.value.show();
  }

  function onPicked(file) {
    known.value = { fileId: file.fileId, url: file.fileUrl };
    emit('update:fileId', file.fileId);
  }

  async function customRequest({ file, onSuccess, onError }) {
    uploading.value = true;
    try {
      const formData = new FormData();
      formData.append('file', file);
      let res = await fileApi.uploadFileByCategory(formData, props.categoryCode);
      known.value = { fileId: res.data.fileId, url: res.data.fileUrl };
      emit('update:fileId', res.data.fileId);
      onSuccess(res, file);
    } catch (e) {
      solvelaSentry.captureError(e);
      onError(e);
    } finally {
      uploading.value = false;
    }
  }

  function clear() {
    known.value = { fileId: null, url: '' };
    emit('update:fileId', null);
  }
</script>

<style scoped lang="less">
  .image-field {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .image-field-preview {
    width: 120px;
    border: 1px solid #f0f0f0;
    border-radius: 4px;
  }

  .image-field-tip {
    color: #bfbfbf;
    font-size: 12px;
  }
</style>
