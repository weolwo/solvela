<!--
  * 素材缩略图
  *
  * 存在的理由只有一个：下载接口要登录态，`<img src>` 发不出 Authorization 头。
  * 所以每处要显示图的地方都得「带 token 取字节 → object URL」，
  * 与其在素材库、选图器、展示配置里各写一遍，不如收在这里一处（见 lib/file-preview.js）。
  *
  * 非图片不去取字节 —— 一个 20MB 的 zip 拿回来只为渲染一个「ZIP」占位，纯浪费。
  *
  * @Copyright  1024创新实验室 （ https://1024lab.net ）
-->
<template>
  <div class="file-thumb" :style="{ height: height + 'px' }">
    <a-spin v-if="loading" size="small" />
    <img v-else-if="src" :src="src" :alt="alt" />
    <div v-else class="thumb-placeholder">{{ placeholderText }}</div>
  </div>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { getFilePreviewUrl } from '/@/lib/file-preview';

  const props = defineProps({
    fileId: { type: Number, default: null },
    // 用来判断要不要真去取字节；拿不到就按图片试一次
    contentType: { type: String, default: 'image/' },
    extension: { type: String, default: '' },
    alt: { type: String, default: '' },
    height: { type: Number, default: 110 },
  });

  const src = ref('');
  const loading = ref(false);

  const isImage = computed(() => (props.contentType || '').startsWith('image/'));

  const placeholderText = computed(() => (props.extension || (isImage.value ? 'IMG' : 'FILE')).toUpperCase());

  async function load() {
    src.value = '';
    if (!props.fileId || !isImage.value) {
      return;
    }
    loading.value = true;
    const requested = props.fileId;
    try {
      const url = await getFilePreviewUrl(requested);
      // 取字节期间 fileId 可能已经被换掉（翻页、换图），晚到的结果不能覆盖当前这张
      if (requested === props.fileId) {
        src.value = url;
      }
    } catch (e) {
      // 图挂了就退回占位，不弹错 —— 一屏二十张图挂三张会刷出三条报错
      src.value = '';
    } finally {
      loading.value = false;
    }
  }

  watch(() => props.fileId, load, { immediate: true });
</script>

<style scoped lang="less">
  .file-thumb {
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fafafa;
    border-radius: 4px;
    overflow: hidden;

    img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
    }
  }

  .thumb-placeholder {
    font-size: 16px;
    font-weight: 600;
    color: #bfbfbf;
    letter-spacing: 1px;
  }
</style>
