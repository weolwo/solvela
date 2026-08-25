<!--
  * 顶部导航：一级菜单
  *
  * 横排磁贴 + 名称，点击切换模块（子菜单显示在左侧栏）。
  * 一级菜单多时横向放不下，放不下的收进「更多」浮层，
  * 量宽逻辑与 top 布局的 menu-bar 一致。
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-06 20:29:12
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
-->
<template>
  <!-- 高度必须写死 46px，与 top-expand-layout 里 .admin-layout-main 的 padding-top 对齐：
       顶栏就是最上面那条 46px 横带。之前误用 h-full（100vh）+ items-center，
       菜单项被垂直居中到屏幕中央，底色还铺满整个内容区，看起来就是「顶部消失了」。 -->
  <div
    class="top-menu-container relative flex h-[46px] items-center overflow-hidden bg-[#F9F9F9] px-[10px] dark:bg-[#1C1C1E]"
    :class="{ dark: darkFlag }"
  >
    <div
      ref="barRef"
      class="flex min-w-0 flex-1 items-center gap-[4px] overflow-hidden transition-opacity duration-150 select-none"
      :class="measured ? 'opacity-100' : 'opacity-0'"
    >
      <template v-for="(item, index) in menuTree" :key="item.menuId">
        <button
          v-if="item.visibleFlag && index < visibleCount"
          data-bar-item
          type="button"
          :class="barItemClass(item)"
          @click="onSelectMenu(item)"
        >
          <MenuIcon :icon="item.icon" :color-key="item.menuId" size="sm" />
          <span class="whitespace-nowrap">{{ item.menuName }}</span>
        </button>
      </template>

      <!-- 放不下的收进这里 -->
      <a-popover
        v-if="overflowGroups.length"
        placement="bottomRight"
        trigger="hover"
        :arrow="false"
        :overlay-inner-style="OVERLAY_INNER_STYLE"
      >
        <template #content>
          <div
            class="max-h-[70vh] w-[220px] overflow-x-hidden overflow-y-auto rounded-[10px] bg-white p-[6px] shadow-[0_8px_28px_rgba(0,0,0,0.14)] select-none dark:bg-[#1C1C1E]"
          >
            <button
              v-for="item in overflowGroups"
              :key="item.menuId"
              type="button"
              class="mb-[2px] flex w-full cursor-pointer items-center gap-[10px] rounded-[8px] border-0 px-[8px] py-[7px] font-[inherit] text-[14px] transition-colors last:mb-0"
              :class="
                selectedKey === item.menuId
                  ? 'bg-black/[0.06] font-medium text-black dark:bg-white/[0.12] dark:text-white'
                  : 'bg-transparent text-black hover:bg-black/[0.05] dark:text-[#E5E5EA] dark:hover:bg-white/[0.1]'
              "
              @click="onSelectMenu(item)"
            >
              <MenuIcon :icon="item.icon" :color-key="item.menuId" size="sm" />
              <span class="min-w-0 flex-1 truncate text-left">{{ item.menuName }}</span>
            </button>
          </div>
        </template>
        <button
          type="button"
          class="flex h-[34px] shrink-0 cursor-pointer items-center gap-[6px] rounded-[8px] border-0 bg-transparent px-[10px] font-[inherit] text-[14px] tracking-[-0.2px] whitespace-nowrap text-[#3C3C43] transition-colors hover:bg-black/[0.05] dark:text-[#EBEBF5] dark:hover:bg-white/[0.1]"
          :class="{ 'bg-black/[0.06] font-medium text-black dark:bg-white/[0.12] dark:text-white': overflowActiveFlag }"
        >
          更多
          <span class="text-[12px] text-[#8E8E93]">{{ overflowGroups.length }}</span>
        </button>
      </a-popover>
    </div>

    <!-- 底部极细分割线，与其它布局的顶栏一致 -->
    <span class="pointer-events-none absolute right-0 bottom-0 left-0 h-px bg-[#E5E5EA] dark:bg-[#38383A]"></span>
  </div>
</template>

<script setup>
  import _ from 'lodash';
  import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
  import { MENU_TYPE_ENUM } from '/@/constants/system/menu-const';
  import { router } from '/@/router';
  import { useUserStore } from '/@/store/modules/system/user';
  import menuEmitter from './top-expand-menu-mitt';
  import MenuIcon from '../side-menu/menu-icon.vue';
  import { useSideMenuTheme } from '../side-menu/use-side-menu-theme';

  const OVERLAY_INNER_STYLE = { padding: '0', background: 'transparent', boxShadow: 'none' };
  const ITEM_GAP = 4;
  const MORE_WIDTH = 76;

  const menuTree = computed(() => (useUserStore().getMenuTree || []).filter((e) => e.visibleFlag));
  const { darkFlag } = useSideMenuTheme();

  const props = defineProps({
    collapsed: {
      type: Boolean,
      default: false,
    },
  });

  // ----------------------- 横向溢出收纳 -----------------------

  const barRef = ref();
  const measured = ref(false);
  const visibleCount = ref(Number.MAX_SAFE_INTEGER);
  let itemWidths = [];

  const overflowGroups = computed(() => menuTree.value.slice(visibleCount.value));

  function computeVisibleCount() {
    const bar = barRef.value;
    if (!bar || !itemWidths.length) {
      return;
    }
    const available = bar.clientWidth;
    const fit = (limit) => {
      let total = 0;
      let count = 0;
      for (const w of itemWidths) {
        if (total + w > limit) {
          break;
        }
        total += w;
        count++;
      }
      return count;
    };
    let count = fit(available);
    if (count < itemWidths.length) {
      count = fit(available - MORE_WIDTH);
    }
    visibleCount.value = count;
  }

  /** 先全部渲染出来量宽度，再收起；量完之前整条 bar 是 opacity-0，避免看到一帧撑破的布局 */
  async function remeasure() {
    measured.value = false;
    visibleCount.value = Number.MAX_SAFE_INTEGER;
    await nextTick();
    const bar = barRef.value;
    if (!bar) {
      return;
    }
    itemWidths = [...bar.querySelectorAll('[data-bar-item]')].map((el) => el.getBoundingClientRect().width + ITEM_GAP);
    computeVisibleCount();
    measured.value = true;
  }

  let resizeObserver = null;

  onMounted(() => {
    remeasure();
    resizeObserver = new ResizeObserver(() => computeVisibleCount());
    if (barRef.value) {
      resizeObserver.observe(barRef.value);
    }
  });

  onBeforeUnmount(() => {
    resizeObserver?.disconnect();
    resizeObserver = null;
  });

  watch(menuTree, () => remeasure());
  // 左侧栏收起/展开会改变本条的可用宽度
  watch(() => props.collapsed, computeVisibleCount);

  // ----------------------- 选中 -----------------------

  const selectedKey = ref(null);
  const overflowActiveFlag = computed(() => overflowGroups.value.some((e) => e.menuId === selectedKey.value));

  function barItemClass(item) {
    const base =
      'flex h-[34px] shrink-0 cursor-pointer items-center gap-[8px] rounded-[8px] border-0 px-[10px] font-[inherit] text-[14px] tracking-[-0.2px] transition-colors';
    const active = 'bg-black/[0.06] font-medium text-black dark:bg-white/[0.12] dark:text-white';
    const idle = 'bg-transparent text-[#3C3C43] hover:bg-black/[0.05] dark:text-[#EBEBF5] dark:hover:bg-white/[0.1]';
    return `${base} ${selectedKey.value === item.menuId ? active : idle}`;
  }

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

  defineExpose({ updateSelectKey });

  // 动态计算当前导航宽度
  let hasSideMenu = ref(false);
  menuEmitter.on('sideMenuChange', (data) => {
    hasSideMenu.value = data;
  });

  const menuInfo = computed(() => {
    let width = '100vw';
    let right = '-100vw';
    if (hasSideMenu.value) {
      if (props.collapsed) {
        width = 'calc(100vw - 80px)';
        right = 'calc(-100vw + 80px)';
      } else {
        width = 'calc(100vw - 180px)';
        right = 'calc(-100vw + 180px)';
      }
    }
    return {
      width,
      right,
    };
  });
</script>
<style scoped lang="less">
  .top-menu {
    position: absolute;
    transition:
      all 0.2s,
      background 0s;
    top: 0;
    right: v-bind('menuInfo.right');
    width: v-bind('menuInfo.width');
    flex-shrink: 0;
  }
</style>
