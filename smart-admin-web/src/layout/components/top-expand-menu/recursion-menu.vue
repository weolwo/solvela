<!--
  * 左侧栏：当前一级菜单下的子菜单
  *
  * 一张 iOS 卡片，整栏同一个色系（颜色取自顶部选中的那个一级菜单）。
  * 展开/选中/跳转的状态逻辑走 side-menu/use-menu-state，与其它三种布局同一套。
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-06 20:29:12
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
-->
<template>
  <div
    class="h-full overflow-x-hidden overflow-y-auto bg-[#F2F2F7] dark:bg-black"
    :class="{ dark: darkFlag }"
    v-show="visibleFlag"
  >
    <!-- 顶部logo区域 -->
    <div
      class="sticky top-0 z-10 flex h-[46px] cursor-pointer items-center gap-[10px] bg-[#F2F2F7] select-none dark:bg-black"
      :class="collapsed ? 'w-[80px] justify-center' : 'px-[14px]'"
      @click="onGoHome"
    >
      <img class="h-[26px] w-[26px] shrink-0 rounded-[7px]" :src="logoImg" />
      <div v-if="!collapsed" class="truncate text-[15px] font-semibold tracking-[-0.24px] text-black dark:text-white">
        {{ websiteName }}
      </div>
    </div>

    <div v-if="!collapsed" class="mx-[10px] mb-[12px]">
      <MenuCard :nodes="visibleChildren" :color-key="topMenu.menuId" />
    </div>
  </div>
</template>

<script setup>
  import _ from 'lodash';
  import { computed, ref, watch } from 'vue';
  import { HOME_PAGE_NAME } from '/@/constants/system/home-const';
  import { router } from '/@/router';
  import menuEmitter from './top-expand-menu-mitt';
  import { useAppConfigStore } from '/@/store/modules/system/app-config';
  import logoImg from '/@/assets/images/logo/smart-admin-logo.png';
  import MenuCard from '../side-menu/menu-card.vue';
  import { useSideMenuTheme } from '../side-menu/use-side-menu-theme';
  import { useSmartMenuState } from '../side-menu/use-menu-state';

  const websiteName = computed(() => useAppConfigStore().websiteName);
  const { darkFlag } = useSideMenuTheme();

  const props = defineProps({
    collapsed: {
      type: Boolean,
      default: false,
    },
  });

  // 选中的顶级菜单
  const topMenu = ref({});
  const visibleChildren = computed(() => (topMenu.value.children || []).filter((e) => e.visibleFlag && !e.disabledFlag));
  const visibleFlag = computed(() => visibleChildren.value.length > 0);

  // 这一栏里可展开的是二级（一级在顶栏上），单开模式下二级之间互斥
  const accordionKeys = computed(() => visibleChildren.value.filter((e) => !_.isEmpty(e.children)).map((e) => e.menuId));
  const { openKeys, syncFromRoute } = useSmartMenuState({ accordionKeys });

  menuEmitter.on('selectTopMenu', onSelectTopMenu);

  //动态通知顶部菜单栏侧边栏状态
  watch(
    visibleFlag,
    (value) => {
      menuEmitter.emit('sideMenuChange', value);
    },
    { immediate: true }
  );

  // 监听选中顶级菜单事件：切换模块时默认全部展开，和原来的行为一致
  function onSelectTopMenu(selectedTopMenu) {
    topMenu.value = selectedTopMenu || {};
    openKeys.value = visibleChildren.value.map((e) => e.menuId);
  }

  function updateSelectKeyAndOpenKey(parentList, currentSelectKey) {
    if (!parentList) {
      return;
    }
    const needOpenKeys = syncFromRoute('none');
    openKeys.value = _.union(openKeys.value, needOpenKeys);
  }

  // 路由变化时（比如通过标签页跳转）也要跟着更新选中
  watch(
    () => topMenu.value.menuId,
    () => syncFromRoute('none')
  );

  function onGoHome() {
    router.push({ name: HOME_PAGE_NAME });
  }

  defineExpose({ updateSelectKeyAndOpenKey });
</script>
