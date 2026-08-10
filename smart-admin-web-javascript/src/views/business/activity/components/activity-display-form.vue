<!--
  * 活动 C 端展示配置
  *
  * 同一个组件给两个入口用：创建向导的最后一步、活动编辑页。
  * 能共用是因为后端 save 按 activityCode 做 upsert —— 它不关心活动是新建还是编辑。
  *
  * 这一步应当可跳过：所有字段都可空，运营常常在等设计出图，不该被它挡住「完成」。
  * 而且有些活动本来就不需要 C 端展示。
  *
  * @Copyright  1024创新实验室 （ https://1024lab.net ）
-->
<template>
  <a-spin :spinning="loading">
    <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 18 }">
      <a-form-item label="主视觉">
        <ImageField v-model:fileId="form.mainImageId" v-model:url="preview.main" tip="活动页顶部大图" />
      </a-form-item>
      <a-form-item label="背景图">
        <ImageField v-model:fileId="form.bgImageId" v-model:url="preview.bg" tip="可不传，不传则用主题色" />
      </a-form-item>
      <a-form-item label="分享图">
        <ImageField v-model:fileId="form.shareImageId" v-model:url="preview.share" tip="微信分享卡片缩略图" />
      </a-form-item>

      <a-form-item label="副标题">
        <a-input v-model:value="form.subTitle" placeholder="活动页副标题" :maxlength="128" show-count />
      </a-form-item>
      <a-form-item label="主题色">
        <a-input v-model:value="form.themeColor" placeholder="#FF6A00" :maxlength="16" style="width: 160px" />
      </a-form-item>
      <a-form-item label="分享标题">
        <a-input v-model:value="form.shareTitle" placeholder="分享出去的标题" :maxlength="64" show-count />
      </a-form-item>
      <a-form-item label="分享描述">
        <a-input v-model:value="form.shareDesc" placeholder="分享卡片的一句话描述" :maxlength="128" show-count />
      </a-form-item>

      <a-form-item label="活动规则">
        <!--
          富文本里插的图会走 fileApi.uploadFile，插入的是 /file/download/{id} 形态的 URL，
          保存时后端能反查回 fileId 并登记引用 —— 不登记的话孤儿清理任务会把这些图删掉。
          编辑器里禁掉了 base64，后端也会再拦一道（前端配置是会被人改回来的）。
        -->
        <Wangeditor v-model="form.ruleContent" :height="320" />
      </a-form-item>
    </a-form>

    <div class="footer">
      <a-button v-if="skippable" @click="$emit('skip')">稍后再配</a-button>
      <a-button type="primary" :loading="saving" @click="save">保存展示配置</a-button>
    </div>
  </a-spin>
</template>

<script setup>
  import { h, onMounted, reactive, ref, watch } from 'vue';
  import { Button, message, Upload } from 'ant-design-vue';
  import { activityDisplayApi } from '/@/api/business/activity/activity-display-api';
  import { fileApi } from '/@/api/support/file-api';
  import { FILE_FOLDER_TYPE_ENUM } from '/@/constants/support/file-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import Wangeditor from '/@/components/framework/wangeditor/index.vue';

  const props = defineProps({
    activityCode: { type: String, required: true },
    // 向导里可跳过；活动编辑页里没有「稍后再配」这个动作
    skippable: { type: Boolean, default: false },
  });
  const emit = defineEmits(['saved', 'skip']);

  const loading = ref(false);
  const saving = ref(false);

  const form = reactive({
    mainImageId: null,
    bgImageId: null,
    shareImageId: null,
    subTitle: '',
    themeColor: '',
    shareTitle: '',
    shareDesc: '',
    ruleContent: '',
  });
  // 图片只在 form 里存 fileId，URL 单独放：存 URL 的话换 CDN 域名要洗全表
  const preview = reactive({ main: '', bg: '', share: '' });

  async function load() {
    if (!props.activityCode) return;
    loading.value = true;
    try {
      let res = await activityDisplayApi.get(props.activityCode);
      // 没配过时后端返回 null，按空表单处理
      if (res.data) {
        Object.assign(form, res.data);
      }
      await loadPreviews();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  /** 三个图片字段存的是 fileId，展示时才换算成 URL */
  async function loadPreviews() {
    const pairs = [
      ['main', form.mainImageId],
      ['bg', form.bgImageId],
      ['share', form.shareImageId],
    ];
    for (const [key, fileId] of pairs) {
      if (!fileId) {
        preview[key] = '';
        continue;
      }
      try {
        let res = await fileApi.detail(fileId);
        preview[key] = res.data.file.fileUrl;
      } catch (e) {
        // 图被删了也不该让整个表单打不开，留空即可
        preview[key] = '';
      }
    }
  }

  async function save() {
    saving.value = true;
    try {
      await activityDisplayApi.save(props.activityCode, { ...form });
      message.success('展示配置已保存');
      emit('saved');
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  watch(() => props.activityCode, load);
  onMounted(load);

  // ---------------------------- 内联的图片字段组件 ----------------------------

  /**
   * 上传即用：拿到 fileId 和 fileUrl，form 里只留 fileId。
   *
   * <p>暂时没有「从素材库选图」的入口 —— 那需要把素材网格抽成可复用的选择器弹窗，
   * 是独立的一块工作。现在的形态是可用的：上传的图同样进素材库，只是复用要重新传一次。
   */
  const ImageField = {
    props: {
      fileId: { type: Number, default: null },
      url: { type: String, default: '' },
      tip: { type: String, default: '' },
    },
    emits: ['update:fileId', 'update:url'],
    setup(fieldProps, { emit: fieldEmit }) {
      const uploading = ref(false);

      async function customRequest({ file, onSuccess, onError }) {
        uploading.value = true;
        try {
          const formData = new FormData();
          formData.append('file', file);
          let res = await fileApi.uploadFile(formData, FILE_FOLDER_TYPE_ENUM.COMMON.value);
          fieldEmit('update:fileId', res.data.fileId);
          fieldEmit('update:url', res.data.fileUrl);
          onSuccess(res, file);
        } catch (e) {
          smartSentry.captureError(e);
          onError(e);
        } finally {
          uploading.value = false;
        }
      }

      function clear() {
        fieldEmit('update:fileId', null);
        fieldEmit('update:url', '');
      }

      return () =>
        h('div', { class: 'image-field' }, [
          fieldProps.url
            ? h('div', { class: 'image-field-preview' }, [
                h('img', { src: fieldProps.url }),
                h('a', { class: 'image-field-clear', onClick: clear }, '移除'),
              ])
            : null,
          h(
            Upload,
            {
              showUploadList: false,
              accept: 'image/*',
              customRequest,
            },
            // 渲染函数里必须传组件对象而不是 'a-button' 字符串 —— 字符串会被当成原生标签，
            // 浏览器不认识它，按钮会变成一个没样式的裸元素，而构建期完全发现不了
            () => [h(Button, { loading: uploading.value }, () => (fieldProps.url ? '更换图片' : '上传图片'))]
          ),
          fieldProps.tip ? h('span', { class: 'image-field-tip' }, fieldProps.tip) : null,
        ]);
    },
  };
</script>

<style scoped lang="less">
  .footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding-top: 12px;
  }

  :deep(.image-field) {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  :deep(.image-field-preview) {
    display: flex;
    align-items: center;
    gap: 8px;

    img {
      max-height: 64px;
      max-width: 160px;
      object-fit: contain;
      border: 1px solid #f0f0f0;
      border-radius: 4px;
    }
  }

  :deep(.image-field-tip) {
    color: #bfbfbf;
    font-size: 12px;
  }
</style>
