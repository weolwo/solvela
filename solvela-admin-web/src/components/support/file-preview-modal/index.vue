<!--
  * 文件预览 弹窗
  *
  * @Author:    1024创新实验室：善逸
  * @Date:      2022-09-02 20:19:39
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
  *
-->
<template>
  <div class="container">
    <a-image
      class="img-prev"
      :style="{ display: 'none' }"
      :preview="{
        visible,
        onVisibleChange: setVisible,
      }"
      :src="previewUrl"
    />
  </div>
</template>

<script setup>
  import { ref } from 'vue';
  import { fileApi } from '/@/api/support/file-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';

  const imgFileType = ['jpg', 'jpeg', 'png', 'gif'];
  const previewUrl = ref();

  function showPreview(fileItem) {
    if (!fileItem.fileUrl) {
      (async () => {
        SolvelaLoading.show();
        try {
          let res = await fileApi.getUrl(fileItem.storageKey);
          fileItem.fileUrl = res;
          showFile(fileItem);
        } catch (e) {
          solvelaSentry.captureError(e);
        } finally {
          SolvelaLoading.hide();
        }
      })();
    } else {
      showFile(fileItem);
    }
  }

  const visible = ref(false);
  const setVisible = (value) => {
    visible.value = value;
  };

  function showFile(fileItem) {
    if (isImg(fileItem.fileType)) {
      previewUrl.value = fileItem.fileUrl;
      setVisible(true);
      return;
    }
    fileApi.downLoadFile(fileItem.storageKey);
  }

  // 判断图片类型
  function isImg(fileType) {
    return imgFileType.includes(fileType);
  }

  defineExpose({
    showPreview,
  });
</script>

<style lang="less" scoped>
  .container {
    display: flex;
    justify-content: center;
    align-items: center;
  }
</style>
