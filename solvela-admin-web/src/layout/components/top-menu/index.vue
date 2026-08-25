<!--
  * 顶部菜单
  *
  * 一整行三段：logo / 横向菜单条 / 用户操作区。
  * 三段都用 flex：logo 与用户操作区 shrink-0，中间菜单条 min-w-0 + overflow-hidden，
  * 这样菜单再多也只会收进「更多」，绝不会把右侧的设置按钮顶出屏幕。
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-06 20:29:12
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
-->
<template>
  <div
    class="header-main relative flex h-[48px] w-full items-center bg-[#F9F9F9] pr-[10px] pl-[16px] text-black dark:bg-[#1C1C1E] dark:text-white"
    :class="{ dark: darkFlag }"
  >
    <!-- 1、logo区域 -->
    <div class="mr-[16px] flex shrink-0 cursor-pointer items-center gap-[10px] select-none" @click="onGoHome">
      <img class="h-[28px] w-[28px] shrink-0 rounded-[7px]" :src="logoImg" />
      <div class="solvela-logo truncate text-[15px] font-semibold tracking-[-0.24px]">{{ websiteName }}</div>
    </div>

    <!-- 2、菜单区域 -->
    <MenuBar ref="menuRef" />

    <!-- 3、用户操作区域 -->
    <div class="ml-[12px] flex shrink-0 items-center gap-[4px]">
      <!---消息通知--->
      <!---设置--->
      <a-button type="text" @click="showSetting">
        <template #icon><setting-outlined /></template>
      </a-button>
      <!---头像信息--->
      <div class="ml-[6px]">
        <HeaderAvatar />
      </div>
      <HeaderSetting ref="headerSetting" />
    </div>

    <!-- 底部极细分割线 -->
    <span class="pointer-events-none absolute right-0 bottom-0 left-0 h-px bg-[#E5E5EA] dark:bg-[#38383A]"></span>
  </div>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import MenuBar from './menu-bar.vue';
  import logoImg from '/@/assets/images/logo/solvela-admin-logo.png';
  import { HOME_PAGE_NAME } from '/@/constants/system/home-const';
  import { useAppConfigStore } from '/@/store/modules/system/app-config';
  import HeaderAvatar from '../header-user-space/header-avatar.vue';
  import HeaderSetting from '../header-user-space/header-setting.vue';
  import { useSideMenuTheme } from '../side-menu/use-side-menu-theme';

  // 设置
  const headerSetting = ref();
  function showSetting() {
    headerSetting.value.show();
  }

  //消息通知
  const headerMessage = ref();

  const websiteName = computed(() => useAppConfigStore().websiteName);
  const { darkFlag } = useSideMenuTheme();

  const menuRef = ref();

  const router = useRouter();
  function onGoHome() {
    router.push({ name: HOME_PAGE_NAME });
  }
</script>

<style lang="less" scoped>
  // antd 给按钮和徽标写死了颜色，这里让它们跟随外层的明暗文字色
  .header-main {
    :deep(.ant-btn-text),
    :deep(.ant-badge) {
      color: inherit;
    }
  }
</style>
