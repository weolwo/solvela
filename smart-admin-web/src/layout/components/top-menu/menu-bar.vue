<!--
  * 顶部菜单-横向菜单条
  *
  * 顶栏保持横条形态（顶部布局的意义就是把高度让给内容区），
  * 但下拉浮层换成 iOS 卡片：29px 彩色磁贴 + 44px 行 + 极细分割线，
  * 直接复用侧边菜单的 MenuNode / MenuIcon，两种布局共用同一套观感。
  *
  * 一级菜单多（本项目 15 个）横向放不下，所以自己做了溢出收纳：
  * 首次渲染时量出每一项的宽度缓存起来，按容器宽度算出能放几个，
  * 放不下的收进「更多」浮层 —— 那个浮层本身就是一张侧边菜单式的卡片。
  * 这样右侧用户区（含设置按钮）永远不会被菜单顶出屏幕。
-->
<template>
  <div
    ref="barRef"
    class="flex min-w-0 flex-1 items-center gap-[4px] overflow-hidden transition-opacity duration-150"
    :class="measured ? 'opacity-100' : 'opacity-0'"
  >
    <template v-for="(group, index) in menuTree" :key="group.menuId">
      <template v-if="index < visibleCount">
        <!-- 有子菜单：悬浮弹出 iOS 卡片 -->
        <a-popover
          v-if="hasChildren(group)"
          placement="bottomLeft"
          trigger="hover"
          :arrow="false"
          :overlay-inner-style="OVERLAY_INNER_STYLE"
        >
          <template #content>
            <div
              class="max-h-[70vh] w-[248px] overflow-x-hidden overflow-y-auto rounded-[10px] bg-white shadow-[0_8px_28px_rgba(0,0,0,0.14)] select-none dark:bg-[#1C1C1E]"
            >
              <MenuNode
                v-for="(child, i) in visibleChildren(group)"
                :key="child.menuId"
                :node="child"
                :color-key="group.menuId"
                :level="1"
                :show-divider="i > 0"
              />
            </div>
          </template>
          <button data-bar-item type="button" :class="barItemClass(group)">
            <MenuIcon :icon="group.icon" :color-key="group.menuId" size="sm" />
            <span class="whitespace-nowrap">{{ group.menuName }}</span>
          </button>
        </a-popover>

        <!-- 一级本身是叶子节点：直接跳转 -->
        <button v-else data-bar-item type="button" :class="barItemClass(group)" @click="onNavigate(group)">
          <MenuIcon :icon="group.icon" :color-key="group.menuId" size="sm" />
          <span class="whitespace-nowrap">{{ group.menuName }}</span>
        </button>
      </template>
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
          class="max-h-[70vh] w-[260px] overflow-x-hidden overflow-y-auto rounded-[10px] bg-white shadow-[0_8px_28px_rgba(0,0,0,0.14)] select-none dark:bg-[#1C1C1E]"
        >
          <MenuNode
            v-for="(group, i) in overflowGroups"
            :key="group.menuId"
            :node="group"
            :color-key="group.menuId"
            :level="1"
            :show-divider="i > 0"
          />
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
</template>

<script setup>
  import _ from 'lodash';
  import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import MenuNode from '../side-menu/menu-node.vue';
  import MenuIcon from '../side-menu/menu-icon.vue';
  import { router } from '/@/router/index';
  import { useAppConfigStore } from '/@/store/modules/system/app-config';
  import { useUserStore } from '/@/store/modules/system/user';

  // antd 浮层只负责定位，外观完全交给里面那张卡片
  const OVERLAY_INNER_STYLE = { padding: '0', background: 'transparent', boxShadow: 'none' };
  // 菜单项之间的 gap，量宽度时要算进去
  const ITEM_GAP = 4;
  // 「更多」按钮预留宽度
  const MORE_WIDTH = 76;

  const menuSingleExpandFlag = computed(() => useAppConfigStore().$state.menuSingleExpandFlag);
  const menuTree = computed(() => (useUserStore().getMenuTree || []).filter((e) => e.visibleFlag && !e.disabledFlag));

  function visibleChildren(node) {
    return (node.children || []).filter((e) => e.visibleFlag && !e.disabledFlag);
  }
  function hasChildren(node) {
    return !_.isEmpty(visibleChildren(node));
  }

  // ----------------------- 横向溢出收纳 -----------------------

  const barRef = ref();
  const measured = ref(false);
  const visibleCount = ref(Number.MAX_SAFE_INTEGER);
  // 每一项的宽度（含 gap），只在全部渲染时量一次，之后 resize 直接用缓存算
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
    // 放不下就得给「更多」腾位置，重算一遍
    if (count < itemWidths.length) {
      count = fit(available - MORE_WIDTH);
    }
    visibleCount.value = count;
  }

  /**
   * 先全部渲染出来量宽度，再收起。
   * 量完之前整条 bar 是 opacity-0，避免看到一帧撑破的布局。
   */
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

  // 菜单树变了（切换账号、权限变更）要重新量
  watch(menuTree, () => remeasure());

  // ----------------------- 展开与选中 -----------------------

  let currentRoute = useRoute();
  const selectedKey = ref(null);
  const openKeys = ref([]);
  // 当前页所属的一级菜单，用来高亮顶栏对应那一项
  const activeTopId = ref(null);

  const overflowActiveFlag = computed(() => overflowGroups.value.some((e) => e.menuId === activeTopId.value));

  const rootSubmenuKeys = computed(() =>
    menuTree.value.flatMap((group) => visibleChildren(group).filter((e) => hasChildren(e)).map((e) => e.menuId))
  );

  function isOpen(menuId) {
    return openKeys.value.includes(menuId);
  }
  function isSelected(menuId) {
    return selectedKey.value === menuId;
  }

  function onToggle(menuId) {
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

  function onNavigate(menu) {
    useUserStore().deleteKeepAliveIncludes(menu.menuId.toString());
    router.push({ path: menu.path });
  }

  provide('smartSideMenu', { isOpen, isSelected, onToggle, onNavigate });

  function barItemClass(group) {
    const base =
      'flex h-[34px] shrink-0 cursor-pointer items-center gap-[8px] rounded-[8px] border-0 px-[10px] font-[inherit] text-[14px] tracking-[-0.2px] transition-colors';
    const active = 'bg-black/[0.06] font-medium text-black dark:bg-white/[0.12] dark:text-white';
    const idle = 'bg-transparent text-[#3C3C43] hover:bg-black/[0.05] dark:text-[#EBEBF5] dark:hover:bg-white/[0.1]';
    return `${base} ${activeTopId.value === group.menuId ? active : idle}`;
  }

  /**
   * SmartAdmin中 router的name 就是 后端存储menu的id
   * 所以此处可以直接监听路由，根据路由更新菜单的选中和展开
   */
  function updateSelectKeys() {
    selectedKey.value = _.toNumber(currentRoute.name);

    let menuParentIdListMap = useUserStore().getMenuParentIdListMap;
    let parentList = menuParentIdListMap.get(currentRoute.name) || [];
    let needOpenKeys = _.map(parentList, 'name').map(Number);

    // 没有父级说明当前页本身就是一级菜单
    activeTopId.value = needOpenKeys.length ? needOpenKeys[0] : _.toNumber(currentRoute.name);
    openKeys.value = _.union(openKeys.value, needOpenKeys);
  }

  watch(currentRoute, () => updateSelectKeys(), { immediate: true });

  defineExpose({ updateSelectKeys });
</script>
