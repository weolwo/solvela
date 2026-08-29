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
        <ImageField v-model:fileId="form.mainImageId" tip="活动页顶部大图" />
      </a-form-item>
      <a-form-item label="背景图">
        <ImageField v-model:fileId="form.bgImageId" tip="可不传，不传则用主题色" />
      </a-form-item>
      <a-form-item label="分享图">
        <ImageField v-model:fileId="form.shareImageId" tip="微信分享卡片缩略图" />
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
  import { onMounted, reactive, ref, watch } from 'vue';
  import { message } from 'ant-design-vue';
  import { activityDisplayApi } from '/@/api/business/activity/activity-display-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import Wangeditor from '/@/components/framework/wangeditor/index.vue';
  import ImageField from '/@/components/support/image-field/index.vue';

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
  // 表单里只存 fileId，不存 URL：存 URL 的话换 CDN 域名要洗全表。
  // 预览由 ImageField 自己按 fileId 取，这里不再维护第二份 URL 状态

  async function load() {
    if (!props.activityCode) return;
    loading.value = true;
    try {
      let res = await activityDisplayApi.get(props.activityCode);
      // 没配过时后端返回 null，按空表单处理
      if (res) {
        Object.assign(form, res);
      }
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  async function save() {
    saving.value = true;
    try {
      await activityDisplayApi.save(props.activityCode, { ...form });
      message.success('展示配置已保存');
      emit('saved');
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  watch(() => props.activityCode, load);
  onMounted(load);
</script>

<style scoped lang="less">
  .footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding-top: 12px;
  }
</style>
