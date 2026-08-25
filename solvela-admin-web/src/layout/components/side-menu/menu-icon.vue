<!--
  * 菜单图标磁贴（侧边菜单 / 顶部菜单共用）
  *
  * 图标本身仍然是 @ant-design/icons-vue（数据库 t_menu.icon 里存的是组件名），
  * 这里把它放进 iOS 设置那种实色圆角磁贴：始终实色底 + 纯白图标，不做浅底。
  * 明暗两套色值挂成 CSS 变量，由 dark: 变体切换，组件本身不需要知道当前主题。
  *
  * size: md = 29px（iOS 设置列表的真实尺寸，卡片行用）
  *       sm = 22px（顶栏只有 48px 高，用小一号）
-->
<template>
  <span
    class="flex shrink-0 items-center justify-center bg-[var(--tile-light)] dark:bg-[var(--tile-dark)]"
    :class="size === 'sm' ? 'h-[22px] w-[22px] rounded-[6px] text-[12px]' : 'h-[29px] w-[29px] rounded-[7px] text-[15px]'"
    :style="tileVars"
  >
    <component :is="iconComponent" v-if="iconComponent" class="text-white" />
    <span v-else class="h-[6px] w-[6px] rounded-full bg-white"></span>
  </span>
</template>

<script setup>
  import * as antIcons from '@ant-design/icons-vue';
  import { computed } from 'vue';
  import { getMenuIconColor } from './menu-icon-color';

  const props = defineProps({
    // 数据库里存的 ant 图标组件名，可能为空
    icon: {
      type: String,
      default: '',
    },
    // 一级菜单的 menuId，整棵子树共用它来取色
    colorKey: {
      type: [String, Number],
      default: 0,
    },
    size: {
      type: String,
      default: 'md',
    },
  });

  const iconComponent = computed(() => (props.icon ? antIcons[props.icon] : null));

  const tileVars = computed(() => {
    const color = getMenuIconColor(props.colorKey);
    return {
      '--tile-light': color.light,
      '--tile-dark': color.dark,
    };
  });
</script>
