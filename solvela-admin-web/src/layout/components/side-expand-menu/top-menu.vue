<!--
  * 第一列菜单：一级导航
  *
  * 竖排磁贴 + 两字缩写，选中项高亮。点击后通过 mitt 通知第二列换内容。
  * 磁贴配色与其它三种布局同源（按 menuId 哈希取 iOS 系统色）。
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-06 20:29:12
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
-->
<template>
  <div class="flex h-full flex-col bg-[#F2F2F7] dark:bg-black" :class="{ dark: darkFlag }">
    <!-- 顶部logo区域 -->
    <div class="flex h-[40px] shrink-0 cursor-pointer items-center justify-center select-none" @click="onGoHome">
      <img class="h-[26px] w-[26px] rounded-[7px]" :src="logoImg" />
    </div>

    <!-- 一级菜单展示 -->
    <div class="flex-1 overflow-x-hidden overflow-y-auto pb-[12px] select-none">
      <div class="flex flex-col items-center gap-[2px]">
        <template v-for="item in menuTree" :key="item.menuId">
          <a-tooltip v-if="item.visibleFlag" placement="right" :title="item.menuName">
            <button
              type="button"
              class="flex w-[72px] cursor-pointer flex-col items-center gap-[4px] rounded-[10px] border-0 py-[8px] font-[inherit] transition-colors"
              :class="
                selectedKey === item.menuId
                  ? 'bg-black/[0.07] dark:bg-white/[0.14]'
                  : 'bg-transparent hover:bg-black/[0.04] dark:hover:bg-white/[0.08]'
              "
              @click="onSelectMenu(item)"
            >
              <MenuIcon :icon="item.icon" :color-key="item.menuId" />
              <span
                class="max-w-full truncate text-[12px] tracking-[-0.08px]"
                :class="selectedKey === item.menuId ? 'font-medium text-black dark:text-white' : 'text-[#6D6D72] dark:text-[#8E8E93]'"
              >
                {{ menuNameAdapter(item.menuName) }}
              </span>
            </button>
          </a-tooltip>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
  import _ from 'lodash';
  import { computed, ref } from 'vue';
  import { HOME_PAGE_NAME } from '/@/constants/system/home-const';
  import { MENU_TYPE_ENUM } from '/@/constants/system/menu-const';
  import { router } from '/@/router';
  import { useUserStore } from '/@/store/modules/system/user';
  import logoImg from '/@/assets/images/logo/solvela-admin-logo.png';
  import menuEmitter from './side-expand-menu-mitt';
  import MenuIcon from '../side-menu/menu-icon.vue';
  import { useSideMenuTheme } from '../side-menu/use-side-menu-theme';

  const menuTree = computed(() => useUserStore().getMenuTree || []);
  const { darkFlag } = useSideMenuTheme();

  // 展开菜单的顶级目录名字适配，只展示两个字为好
  function menuNameAdapter(name) {
    return (name || '').substr(0, 2);
  }

  // 选中的顶级菜单
  const selectedKey = ref(null);

  // 选中菜单，页面跳转
  function onSelectMenu(menuItem) {
    selectedKey.value = menuItem.menuId;
    if (menuItem.menuType === MENU_TYPE_ENUM.MENU.value && (_.isEmpty(menuItem.children) || menuItem.children.every((e) => !e.visibleFlag))) {
      useUserStore().deleteKeepAliveIncludes(menuItem.menuId.toString());
      router.push({ name: menuItem.menuId.toString() });
    }
    menuEmitter.emit('selectTopMenu', menuItem);
  }

  // 更新选中的菜单
  function updateSelectKey(key) {
    selectedKey.value = Number(key);
    let selectMenu = _.find(menuTree.value, { menuId: Number(key) });
    if (selectMenu) {
      menuEmitter.emit('selectTopMenu', selectMenu);
    }
  }

  //点击logo回到首页
  function onGoHome() {
    router.push({ name: HOME_PAGE_NAME });
  }

  defineExpose({ updateSelectKey });
</script>
