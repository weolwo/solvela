<!--
  * 传统菜单-菜单行（递归）
  *
  * 形态对齐 iOS 设置「首页」：一级菜单本身就是白卡片里的一行，
  * 左侧 29px 高饱和实色磁贴 + 纯白图案，右侧极细 chevron。
  * 点击有子菜单的一级项，子菜单在它下方平滑展开。
  * 整张卡片从上到下是连贯的纯白，子菜单不加任何底色，层级只靠缩进表达。
  *
  * 层级与缩进：
  *   level 1  图标 + 文字，左内缩 16，文字落在 16 + 29 + 15 = 60
  *   level 2  无图标，缩进对齐到一级的文字（60）
  *   level 3+ 每级再进 14
  * 细线永远和本行文字左缘同一坐标，避开图标；本行画在自己顶部，
  * 所以卡片首行不画、末行天然没有底线。
  *
  * 本项目两个坑（整个 side-menu 目录都遵守）：
  *   1、theme/index.less 把 html 的 font-size 设成 14px，tailwind 的 rem 间距会
  *      整体缩到 0.875 倍，所以尺寸一律写 [Npx]；
  *   2、tailwind.css 没引 preflight，border 系工具类不可靠（border-b 不渲染，
  *      补 border-solid 又会把四条边一起打开），所以细线用绝对定位的 1px 元素；
  *      同理 button 的浏览器默认样式还在，需要显式重置 border / bg / font。
  *   另外背景色不能一半写在基础类、一半写在条件类里 —— tailwind 按工具类种类
  *   排序生成，基础类里的 bg-transparent 会盖掉条件类里的选中色。
-->
<template>
  <div class="relative">
    <button
      type="button"
      class="relative flex h-[44px] w-full cursor-pointer items-center border-0 pr-[16px] font-[inherit] text-[15px] tracking-[-0.24px] transition-colors duration-150"
      :class="
        selected
          ? 'bg-[#D1D1D6] font-medium text-black dark:bg-[#3A3A3C] dark:text-white'
          : 'bg-transparent text-black hover:bg-[#E5E5EA] active:bg-[#D1D1D6] dark:text-[#E5E5EA] dark:hover:bg-[#2C2C2E] dark:active:bg-[#3A3A3C]'
      "
      :style="{ paddingLeft: `${indent}px` }"
      @click="onClick"
    >
      <MenuIcon v-if="showIcon" :icon="node.icon" :color-key="colorKey" />
      <span class="min-w-0 flex-1 truncate text-left" :class="{ 'ml-[15px]': showIcon }">{{ node.menuName }}</span>

      <!-- 右侧箭头：SF Symbols chevron 比例，1.5 极细笔画；可展开的展开后转向下 -->
      <svg
        class="ml-[8px] h-[13px] w-[8px] shrink-0 text-[#C7C7CC] transition-transform duration-200 dark:text-[#5B5B60]"
        :class="{ 'rotate-90': hasChildren && open }"
        viewBox="0 0 8 13"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M1.5 1.5 6.5 6.5 1.5 11.5" />
      </svg>

      <!-- 行间细线：从本行文字左缘起画，图标正下方不画。
           线一律画在各行顶部，所以卡片首行不画、末行天然没有底线；
           父级展开时首个子行也不画，父子连成一体。 -->
      <span
        v-if="showDivider"
        class="pointer-events-none absolute top-0 right-0 h-px bg-[#E5E5EA] dark:bg-[#38383A]"
        :style="{ left: `${labelOffset}px` }"
      ></span>
    </button>

    <!-- 子菜单平滑展开：用 grid-template-rows 0fr→1fr 做高度过渡，
         不需要测量 scrollHeight，也不需要 JS 钩子 -->
    <div v-if="hasChildren" class="grid transition-[grid-template-rows] duration-300 ease-out" :class="open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'">
      <!-- 子菜单不给底色：整张卡片从上到下必须是连贯的纯白，层级只靠 60px 缩进表达。
           首个子行不画细线，这样父行和子菜单之间没有断口，视觉上融为一体。 -->
      <div class="overflow-hidden">
        <MenuNode
          v-for="(child, index) in visibleChildren"
          :key="child.menuId"
          :node="child"
          :color-key="colorKey"
          :level="level + 1"
          :show-divider="index > 0"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
  import { computed, inject } from 'vue';
  import _ from 'lodash';
  import MenuIcon from './menu-icon.vue';

  // 一级行的排布常量：左内缩 + 磁贴 + 间距 = 文字左缘，细线也对齐到这里
  const ROW_INSET = 16;
  const ICON_SIZE = 29;
  const ICON_GAP = 15;
  const TEXT_OFFSET = ROW_INSET + ICON_SIZE + ICON_GAP;

  const props = defineProps({
    node: {
      type: Object,
      required: true,
    },
    // 一级菜单的 menuId，一路透传下去，保证整棵子树是同一个色系
    colorKey: {
      type: [String, Number],
      default: 0,
    },
    // 卡片里的一级行是 1，往下递增
    level: {
      type: Number,
      default: 1,
    },
    showDivider: {
      type: Boolean,
      default: false,
    },
  });

  const menu = inject('solvelaSideMenu');

  const visibleChildren = computed(() => (props.node.children || []).filter((e) => e.visibleFlag && !e.disabledFlag));
  const hasChildren = computed(() => !_.isEmpty(visibleChildren.value));
  const open = computed(() => menu.isOpen(props.node.menuId));
  const selected = computed(() => !hasChildren.value && menu.isSelected(props.node.menuId));

  // 只有一级带磁贴，和 iOS 设置首页一致；再往下靠缩进表达层级
  const showIcon = computed(() => props.level === 1);
  const indent = computed(() => (props.level === 1 ? ROW_INSET : TEXT_OFFSET + (props.level - 2) * 14));
  const labelOffset = computed(() => (props.level === 1 ? TEXT_OFFSET : indent.value));

  function onClick() {
    if (hasChildren.value) {
      menu.onToggle(props.node.menuId);
    } else {
      menu.onNavigate(props.node);
    }
  }
</script>
