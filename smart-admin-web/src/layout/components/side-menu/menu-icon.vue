<!--
  * 传统菜单-图标磁贴
  *
  * 图标本身仍然是 @ant-design/icons-vue（数据库 t_menu.icon 里存的是组件名），
  * 这里把它放进 iOS 设置那种实色圆角磁贴：始终实色底 + 纯白图标，不做浅底。
  * 明暗两套色值挂成 CSS 变量，由 dark: 变体切换，组件本身不需要知道当前主题。
-->
<template>
  <span
    class="flex h-[29px] w-[29px] shrink-0 items-center justify-center rounded-[7px] bg-[var(--tile-light)] dark:bg-[var(--tile-dark)]"
    :style="tileVars"
  >
    <component :is="iconComponent" v-if="iconComponent" class="text-[15px] text-white" />
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
