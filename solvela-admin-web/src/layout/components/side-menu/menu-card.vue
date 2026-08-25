<!--
  * 菜单卡片（四种布局共用）
  *
  * 一张 iOS 分组卡片：白底、10px 圆角、行间极细分割线（画在各行顶部，
  * 所以首行天然无线、末行天然无底线）。
  * 侧边菜单、顶栏下拉浮层、展开型布局的第二列，用的都是这一个组件。
-->
<template>
  <div class="overflow-hidden rounded-[10px] bg-white select-none dark:bg-[#1C1C1E]">
    <MenuNode
      v-for="(node, index) in nodes"
      :key="node.menuId"
      :node="node"
      :color-key="colorKey === null ? node.menuId : colorKey"
      :level="1"
      :show-divider="index > 0"
    />
  </div>
</template>

<script setup>
  import MenuNode from './menu-node.vue';

  defineProps({
    nodes: {
      type: Array,
      default: () => [],
    },
    // 传 null 表示每一项各自取色（一级菜单列表）；
    // 传具体 menuId 表示整张卡片同一个色系（某个模块的子菜单）
    colorKey: {
      type: [String, Number, null],
      default: null,
    },
  });
</script>
