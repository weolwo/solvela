<!--
  改名前的页面路径，原样渲染 security-event-list.vue。

  🔴 菜单的组件路径存在 t_menu 里（menu_id 647），由改名的第二步脚本改成新路径；
  发版之后、脚本执行之前，菜单仍指向这个旧文件名 —— 没有它，「安全事件」页会打不开。

  TEMP(安全事件改名-收口): 移除条件 —— 改名的第二步脚本已在所有环境执行
  （t_menu 里没有 component = '/business/security/security-incident-list.vue' 的行）。
  见 docs/架构/安全事件改名-实施方案.md。
-->
<template>
  <SecurityEventList />
</template>

<script setup>
  import SecurityEventList from './security-event-list.vue';
</script>
