<!--
  * 商品图片位（原型里的 .img-slot）
  *
  * 方块占位 + 悬停高亮 + 右上角 ✕ 删除。商品封面 / 轮播 / SKU 图 / 分类图标四处共用，
  * 靠 size 区分大小 —— 所以放在 mall/components 而不是 mall-commodity 下面。
  *
  * 【为什么不复用 components/support/image-field】
  * 那个组件是「上传 + 从素材库选」两个入口并排的一条横条（约 300px 宽）。
  * 商品图**刻意不走素材库选择器**（见 mall.sql「关于商品图片」一节：商品图复用率接近零，
  * 混进素材库会把运营的策展空间冲垮），所以那半边入口在这里是多余的；
  * 而 SKU 表里一格只有 44px，横条根本塞不下。
  *
  * 上传落 MALL_COMMODITY 分类，素材库列表固定排除该分类 —— 但 t_file_relation 照常登记，
  * 删除守卫、孤儿清理、存储介质切换全部照常生效。
  *
  * @Copyright  weolwo
-->
<template>
  <a-upload :show-upload-list="false" accept="image/*" :custom-request="customRequest" :disabled="disabled">
    <div class="img-slot" :class="{ filled: !!fileId, disabled }" :style="slotStyle">
      <a-spin v-if="uploading" size="small" />
      <template v-else-if="fileId">
        <!-- .stop：不加的话点 ✕ 会冒泡到 a-upload，删完立刻弹出选文件对话框 -->
        <span v-if="!disabled" class="img-del" @click.stop="onClear">✕</span>
        <FileThumb :file-id="fileId" :file-url="knownUrl" :height="thumbHeight" />
      </template>
      <template v-else>
        <span :style="{ fontSize: size === 'small' ? '16px' : '22px' }">+</span>
        <span v-if="size !== 'small' && placeholder" class="img-slot-text">{{ placeholder }}</span>
      </template>
    </div>
  </a-upload>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { fileApi } from '/@/api/support/file-api';
  import { FILE_CATEGORY_CODE } from '/@/constants/support/file-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import FileThumb from '/@/components/support/file-thumb/index.vue';

  const props = defineProps({
    fileId: { type: Number, default: null },
    /** normal = 104×104（封面/轮播），small = 44×44（SKU 表格内） */
    size: { type: String, default: 'normal' },
    placeholder: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
  });
  const emit = defineEmits(['update:fileId']);

  const uploading = ref(false);

  /**
   * 刚传上来那张图的 URL。连 fileId 一起记是必须的 —— 外部换了值之后上一张的 URL
   * 必须立刻失效，否则会出现「换了图但显示的还是上一张」。
   */
  const known = ref({ fileId: null, url: '' });
  const knownUrl = computed(() => (known.value.fileId === props.fileId ? known.value.url : ''));

  const px = computed(() => (props.size === 'small' ? 44 : 104));
  const slotStyle = computed(() => ({ width: `${px.value}px`, height: `${px.value}px` }));
  const thumbHeight = computed(() => px.value - 8);

  async function customRequest({ file, onSuccess, onError }) {
    uploading.value = true;
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await fileApi.uploadFileByCategory(formData, FILE_CATEGORY_CODE.MALL_COMMODITY);
      known.value = { fileId: res.fileId, url: res.fileUrl };
      emit('update:fileId', res.fileId);
      onSuccess(res, file);
    } catch (e) {
      solvelaSentry.captureError(e);
      onError(e);
    } finally {
      uploading.value = false;
    }
  }

  function onClear() {
    known.value = { fileId: null, url: '' };
    emit('update:fileId', null);
  }
</script>

<style scoped lang="less">
  .img-slot {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    color: #94a3b8;
    cursor: pointer;
    background: #fafcff;
    border: 1px dashed #cbd5e1;
    border-radius: 8px;
    transition: all 0.2s;
  }

  .img-slot:hover {
    color: #3b82f6;
    border-color: #3b82f6;
  }

  .img-slot.filled {
    background: #fff;
    border-color: #bfdbfe;
    border-style: solid;
  }

  .img-slot.disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }

  .img-del {
    position: absolute;
    top: 2px;
    right: 2px;
    z-index: 2;
    padding: 0 4px;
    font-size: 12px;
    line-height: 16px;
    color: #ef4444;
    cursor: pointer;
    background: rgb(255 255 255 / 90%);
    border-radius: 4px;
  }

  .img-slot-text {
    margin-top: 2px;
    font-size: 12px;
  }
</style>
