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
    /**
     * 后端下发的 fileUrl。**公开文件的 URL 可以直接用**（走 /support/file/public/ 或 CDN，
     * 免登录），这时不必带着 token 把字节拉进内存 —— 交给浏览器缓存比我们自己缓存好得多。
     * 只有指向登录态下载接口的 URL 才需要走取字节那条路。
     */
    fileUrl: { type: String, default: '' },
    // 用来判断要不要真去取字节；拿不到就按图片试一次
    contentType: { type: String, default: 'image/' },
    extension: { type: String, default: '' },
    alt: { type: String, default: '' },
    height: { type: Number, default: 110 },
  });

  /** 要登录态的那条路径。判它而不是判"是不是 http 开头"：公开前缀可能是相对路径 */
  const AUTH_DOWNLOAD_PATH = '/file/download/';

  const src = ref('');
  const loading = ref(false);

  const isImage = computed(() => (props.contentType || '').startsWith('image/'));

  const placeholderText = computed(() => (props.extension || (isImage.value ? 'IMG' : 'FILE')).toUpperCase());

  async function load() {
    src.value = '';
    if (!props.fileId || !isImage.value) {
      return;
    }
    if (props.fileUrl && !props.fileUrl.includes(AUTH_DOWNLOAD_PATH)) {
      // 公开图：直接交给 <img>，省一次 XHR，也让浏览器/CDN 的缓存真正起作用
      src.value = props.fileUrl;
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

  watch(() => [props.fileId, props.fileUrl], load, { immediate: true });
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
