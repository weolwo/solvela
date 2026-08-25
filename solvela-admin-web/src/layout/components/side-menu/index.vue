<!--
  * 传统菜单
  *
  * 根节点带 .dark 类来驱动 tailwind 的 dark: 变体（见 theme/tailwind.css 里的 @custom-variant），
  * 明暗切换不依赖系统设置，只看「设置-菜单主题 / 夜间模式」。
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-06 20:29:12
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
-->
<template>
  <!--左侧菜单分为两部分：1、顶部logo区域，包含 logo和名称;2、下方菜单区域-->
  <!-- 底色与 use-side-menu-theme.js 里的 SIDE_MENU_BG_* 保持一致 -->
  <div class="min-h-full bg-[#F2F2F7] dark:bg-black" :class="{ dark: darkFlag }">
    <!-- 1、顶部logo区域：sticky 而不是 fixed —— 留在文档流里，下面的搜索框
         才能跟着 sticky 到它正下方（top-[40px]），两者一起常驻顶部。
         底色必须和侧栏一致，否则滚动的内容会从下面透出来 -->
    <div
      class="sticky top-0 z-30 flex h-[40px] cursor-pointer items-center gap-[10px] bg-[#F2F2F7] px-[14px] select-none dark:bg-black"
      :class="{ 'justify-center !px-0': collapsed }"
      @click="onGoHome"
    >
      <img class="h-[26px] w-[26px] shrink-0 rounded-[7px]" :src="logoImg" />
      <div v-if="!collapsed" class="solvela-logo truncate text-[15px] font-semibold tracking-[-0.24px] text-black dark:text-white">
        {{ websiteName }}
      </div>
    </div>

    <!-- 2、下方菜单区域 -->
    <div class="pb-[16px]">
      <MenuTree :collapsed="collapsed" ref="menuRef" />
    </div>
  </div>
</template>

<script setup>
  import { computed, nextTick, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import MenuTree from './menu-tree.vue';
  import logoImg from '/@/assets/images/logo/solvela-admin-logo.png';
  import { HOME_PAGE_NAME } from '/@/constants/system/home-const';
  import { useAppConfigStore } from '/@/store/modules/system/app-config';
  import { useSideMenuTheme } from './use-side-menu-theme';

  const websiteName = computed(() => useAppConfigStore().websiteName);
  const { darkFlag } = useSideMenuTheme();

  const props = defineProps({
    collapsed: {
      type: Boolean,
      required: false,
      default: false,
    },
  });

  const menuRef = ref();

  watch(
    () => props.collapsed,
    (newValue, oldValue) => {
      // 如果是展开菜单的话，重新获取更新菜单的展开项: openkeys和selectKeys
      if (!newValue) {
        nextTick(() => menuRef.value.updateOpenKeysAndSelectKeys());
      }
    }
  );

  const router = useRouter();
  function onGoHome() {
    router.push({ name: HOME_PAGE_NAME });
  }
</script>
