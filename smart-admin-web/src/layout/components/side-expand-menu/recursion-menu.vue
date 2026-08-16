<!--
  * 第二列菜单：当前一级菜单下的子菜单
  *
  * 一张 iOS 卡片，整列同一个色系（颜色取自所属的一级菜单）。
  * 展开/选中/跳转的状态逻辑走 side-menu/use-menu-state，与其它三种布局同一套。
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-06 20:29:12
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
-->
<template>
  <div class="h-full overflow-x-hidden overflow-y-auto bg-[#F2F2F7] dark:bg-black" :class="{ dark: darkFlag }" v-show="visibleFlag">
    <!-- 当前一级菜单名称 -->
    <div
      class="sticky top-0 z-10 flex h-[40px] items-center bg-[#F2F2F7] px-[20px] text-[13px] tracking-[-0.08px] text-[#6D6D72] select-none dark:bg-black dark:text-[#8E8E93]"
    >
      <span class="truncate">{{ topMenu.menuName }}</span>
    </div>

    <div class="mx-[10px] mb-[12px]">
      <MenuCard :nodes="visibleChildren" :color-key="topMenu.menuId" />
    </div>
  </div>
</template>

<script setup>
  import _ from 'lodash';
  import { computed, ref, watch } from 'vue';
  import menuEmitter from './side-expand-menu-mitt';
  import MenuCard from '../side-menu/menu-card.vue';
  import { useSideMenuTheme } from '../side-menu/use-side-menu-theme';
  import { useSmartMenuState } from '../side-menu/use-menu-state';

  const { darkFlag } = useSideMenuTheme();

  // 选中的顶级菜单
  const topMenu = ref({});
  const visibleChildren = computed(() => (topMenu.value.children || []).filter((e) => e.visibleFlag && !e.disabledFlag));
  const visibleFlag = computed(() => visibleChildren.value.length > 0);

  // 这一列里可展开的是二级（一级在第一列上），单开模式下二级之间互斥
  const accordionKeys = computed(() => visibleChildren.value.filter((e) => !_.isEmpty(e.children)).map((e) => e.menuId));
  const { openKeys, syncFromRoute } = useSmartMenuState({ accordionKeys });

  menuEmitter.on('selectTopMenu', onSelectTopMenu);

  // 监听选中顶级菜单事件：切换模块时，默认把这一列全部展开，和原来的行为一致
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

  defineExpose({ updateSelectKeyAndOpenKey });
</script>
