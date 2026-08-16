<!--
  * 传统菜单-菜单树
  *
  * 形态对齐 iOS 设置「首页」：所有一级菜单同处一张纯白卡片，每行 29px 彩色磁贴 +
  * 文字 + chevron，行间极细分割线；点一级项，子菜单在其下方手风琴式平滑展开。
  *
  * 菜单模块多（本项目 15 个一级），靠三件事减少滚动：
  *   1、一级默认全部收起，只在当前路由所在的那条分支上展开；
  *   2、搜索框 sticky 常驻顶部，列表再长也不用滚回去；
  *   3、搜索时命中的分支全部自动展开。
  * 「设置-菜单展开」选单个时，一级之间互斥（真手风琴）。
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-06 20:29:12
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
-->
<template>
  <!-- ================= 展开态 ================= -->
  <div v-if="!collapsed">
    <!-- 搜索：sticky 贴在 logo 下方，菜单再长也够得着。
         底色必须和侧栏一致，否则内容会从下面透出来 -->
    <div class="sticky top-[40px] z-20 bg-[#F2F2F7] px-[10px] pt-[6px] pb-[10px] dark:bg-black">
      <div class="relative">
        <svg
          class="pointer-events-none absolute top-1/2 left-[10px] h-[14px] w-[14px] -translate-y-1/2 text-[#8E8E93]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        </svg>
        <input
          v-model="searchValue"
          type="text"
          :placeholder="$t('menu.search.placeholder')"
          class="h-[32px] w-full rounded-[10px] border-0 bg-[rgba(118,118,128,0.12)] pr-[28px] pl-[32px] font-[inherit] text-[14px] text-black outline-none placeholder:text-[#8E8E93] dark:bg-[rgba(118,118,128,0.24)] dark:text-white"
        />
        <button
          v-if="searchValue"
          type="button"
          class="absolute top-1/2 right-[6px] flex h-[20px] w-[20px] -translate-y-1/2 cursor-pointer items-center justify-center rounded-full border-0 bg-transparent text-[#8E8E93]"
          @click="searchValue = ''"
        >
          <svg class="h-[12px] w-[12px]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <path d="M18 6 6 18M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 所有一级菜单同处一张白卡片，绝不让文字裸露在灰底上 -->
    <div
      v-if="displayMenuTree.length"
      class="mx-[10px] overflow-hidden rounded-[10px] bg-white select-none dark:bg-[#1C1C1E]"
    >
      <MenuNode
        v-for="(group, index) in displayMenuTree"
        :key="group.menuId"
        :node="group"
        :color-key="group.menuId"
        :level="1"
        :show-divider="index > 0"
      />
    </div>

    <div v-else-if="searchValue" class="py-[20px] text-center text-[14px] text-[#8E8E93]">
      {{ $t('menu.search.empty') }}
    </div>
  </div>

  <!-- ================= 侧栏收起态 ================= -->
  <!-- 只剩一列磁贴，有子菜单的悬浮弹出，没有的直接跳转 -->
  <div v-else class="flex flex-col items-center gap-[4px] py-[8px] select-none">
    <template v-for="group in menuTree" :key="group.menuId">
      <a-popover v-if="!$lodash.isEmpty(group.children)" placement="rightTop" trigger="hover">
        <template #content>
          <div class="-mx-[8px] -my-[4px] w-[220px] overflow-hidden rounded-[8px] select-none">
            <MenuNode :node="group" :color-key="group.menuId" :level="1" :show-divider="false" />
          </div>
        </template>
        <button
          type="button"
          class="flex h-[40px] w-[40px] cursor-pointer items-center justify-center rounded-[10px] border-0 bg-transparent transition-colors hover:bg-black/5 dark:hover:bg-white/10"
        >
          <MenuIcon :icon="group.icon" :color-key="group.menuId" />
        </button>
      </a-popover>

      <a-tooltip v-else placement="right" :title="group.menuName">
        <button
          type="button"
          class="flex h-[40px] w-[40px] cursor-pointer items-center justify-center rounded-[10px] border-0 bg-transparent transition-colors hover:bg-black/5 dark:hover:bg-white/10"
          @click="onNavigate(group)"
        >
          <MenuIcon :icon="group.icon" :color-key="group.menuId" />
        </button>
      </a-tooltip>
    </template>
  </div>
</template>

<script setup>
  import _ from 'lodash';
  import { computed, provide, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import MenuNode from './menu-node.vue';
  import MenuIcon from './menu-icon.vue';
  import { router } from '/@/router/index';
  import { useAppConfigStore } from '/@/store/modules/system/app-config';
  import { useUserStore } from '/@/store/modules/system/user';

  const menuSingleExpandFlag = computed(() => useAppConfigStore().$state.menuSingleExpandFlag);

  const props = defineProps({
    collapsed: {
      type: Boolean,
      default: false,
    },
  });

  const menuTree = computed(() => (useUserStore().getMenuTree || []).filter((e) => e.visibleFlag && !e.disabledFlag));

  // ----------------------- 菜单搜索 -----------------------

  const searchValue = ref('');
  const searchingFlag = computed(() => !!searchValue.value.trim());

  /**
   * 按菜单名过滤：节点自己命中，或者子孙里有命中的，就保留。
   * 父节点命中时整棵子树都留下，方便直接看到这个模块下有什么。
   */
  function filterMenuTree(list, keyword) {
    const result = [];
    for (const item of list) {
      if (!item.visibleFlag || item.disabledFlag) {
        continue;
      }
      if ((item.menuName || '').toLowerCase().includes(keyword)) {
        result.push(item);
        continue;
      }
      const children = filterMenuTree(item.children || [], keyword);
      if (children.length) {
        result.push({ ...item, children });
      }
    }
    return result;
  }

  const displayMenuTree = computed(() => {
    const keyword = searchValue.value.trim().toLowerCase();
    if (!keyword) {
      return menuTree.value;
    }
    return filterMenuTree(menuTree.value, keyword);
  });

  // ----------------------- 展开与选中 -----------------------

  let currentRoute = useRoute();
  const selectedKey = ref(null);
  const openKeys = ref([]);

  // 一级现在是可展开的行，单开模式下一级之间互斥
  const rootSubmenuKeys = computed(() => menuTree.value.filter((e) => !_.isEmpty(e.children)).map((e) => e.menuId));

  function isOpen(menuId) {
    // 搜索时无条件全开，否则命中的结果会藏在收起的分支里
    if (searchingFlag.value) {
      return true;
    }
    return openKeys.value.includes(menuId);
  }

  function isSelected(menuId) {
    return selectedKey.value === menuId;
  }

  function onToggle(menuId) {
    // 搜索态下展开由 isOpen 接管，手动折叠不落库
    if (searchingFlag.value) {
      return;
    }
    if (openKeys.value.includes(menuId)) {
      openKeys.value = openKeys.value.filter((e) => e !== menuId);
      return;
    }
    if (menuSingleExpandFlag.value && rootSubmenuKeys.value.includes(menuId)) {
      openKeys.value = [menuId];
    } else {
      openKeys.value = [...openKeys.value, menuId];
    }
  }

  // 页面跳转
  function onNavigate(menu) {
    useUserStore().deleteKeepAliveIncludes(menu.menuId.toString());
    router.push({ path: menu.path });
  }

  provide('smartSideMenu', { isOpen, isSelected, onToggle, onNavigate });

  /**
   * SmartAdmin中 router的name 就是 后端存储menu的id
   * 所以此处可以直接监听路由，根据路由更新菜单的选中和展开
   */
  function updateOpenKeysAndSelectKeys() {
    // 更新选中
    selectedKey.value = _.toNumber(currentRoute.name);

    // 如果是折叠菜单的话，则不需要设置openkey
    if (props.collapsed) {
      return;
    }

    //获取需要展开的menu key集合（含一级，保证当前页所在的卡片行是展开的）
    let menuParentIdListMap = useUserStore().getMenuParentIdListMap;
    let parentList = menuParentIdListMap.get(currentRoute.name) || [];
    let needOpenKeys = _.map(parentList, 'name').map(Number);

    if (menuSingleExpandFlag.value) {
      openKeys.value = [...needOpenKeys];
    } else {
      // 使用lodash的union函数，进行 去重合并两个数组
      openKeys.value = _.union(openKeys.value, needOpenKeys);
    }
  }

  watch(
    currentRoute,
    () => {
      updateOpenKeysAndSelectKeys();
    },
    {
      immediate: true,
    }
  );

  defineExpose({
    updateOpenKeysAndSelectKeys,
  });
</script>
