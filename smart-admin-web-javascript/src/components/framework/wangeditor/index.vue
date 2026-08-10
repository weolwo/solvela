<!--
  * 编辑器
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-12 15:34:33
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
  *
-->
<template>
  <div style="border: 1px solid #ccc">
    <Toolbar style="border-bottom: 1px solid #ccc" :editor="editorRef" />
    <Editor
      style="overflow-y: hidden"
      :style="{ height: `${height}px` }"
      v-model="editorHtml"
      :defaultConfig="editorConfig"
      @onCreated="handleCreated"
      @onChange="handleChange"
    />
  </div>
</template>
<script setup>
  import { shallowRef, onBeforeUnmount, watch, ref } from 'vue';
  import { FILE_CATEGORY_CODE } from '/@/constants/support/file-const';
  import { fileApi } from '/@/api/support/file-api';
  import '@wangeditor-next/editor/dist/css/style.css';
  import { Editor, Toolbar } from '@wangeditor-next/editor-for-vue';
  import { smartSentry } from '/@/lib/smart-sentry';

  //菜单
  const editorConfig = { MENU_CONF: {} };

  /**
   * 上传。正文配图统一落到 CONTENT 分类。
   *
   * <p>为什么单独一个分类：正文图是编辑器里随手插的，量大且零散，
   * 和运营专门挑选的活动素材混在一起，会让选图器变得很难用。
   *
   * <p>⚠️ 插进正文的是 URL 而不是 fileId —— 富文本就是这么工作的。所以这些图
   * <b>必须能被 `<img>` 直接加载</b>（浏览器渲染时带不了 token）。
   * 这也是本模块不做私有文件的原因之一，见 v3.56.0。
   */
  let customUpload = {
    async customUpload(file, insertFn) {
      try {
        const formData = new FormData();
        formData.append('file', file);
        let res = await fileApi.uploadFileByCategory(formData, FILE_CATEGORY_CODE.CONTENT);
        let data = res.data;
        insertFn(data.fileUrl);
      } catch (error) {
        smartSentry.captureError(error);
      }
    },
  };
  editorConfig.MENU_CONF['uploadImage'] = customUpload;
  editorConfig.MENU_CONF['uploadVideo'] = customUpload;

  // ----------------------- 以下是公用变量 emits props ----------------
  const editorHtml = ref();
  let props = defineProps({
    modelValue: String,
    height: {
      type: Number,
      default: 500,
    },
  });
  watch(
    () => props.modelValue,
    (nVal) => {
      editorHtml.value = nVal;
    },
    {
      immediate: true,
      deep: true,
    }
  );

  // 获取编辑器实例html
  const emit = defineEmits(['update:modelValue']);
  const editorRef = shallowRef();
  const handleCreated = (editor) => {
    editorRef.value = editor;
  };
  const handleChange = (editor) => {
    emit('update:modelValue', editorHtml.value);
  };

  function getHtml() {
    const htmlContent = editorRef.value.getHtml();
    return htmlContent === '<p><br></p>' ? '' : htmlContent;
  }
  function getText() {
    return editorRef.value.getText();
  }

  // 组件销毁时，也及时销毁编辑器
  onBeforeUnmount(() => {
    const editor = editorRef.value;
    if (editor == null) return;
    editor.destroy();
  });

  defineExpose({
    editorRef,
    getHtml,
    getText,
  });
</script>

<style scoped>
  .w-e-full-screen-container {
    z-index: 9999 !important;
  }
</style>
<!-- 解决弹窗高度警告信息显示 -->
<style>
  ::v-deep .w-e-text-container {
    height: 420px !important;
  }
  .w-e-text-container .w-e-scroll {
    height: 500px !important;
    -webkit-overflow-scrolling: touch;
  }
</style>
