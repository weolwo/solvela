<!--
  * 第二列菜单区域
  * 
  * @Author:    1024创新实验室-主任：卓大 
  * @Date:      2022-09-06 20:29:12 
  * @Wechat:    zhuda1024 
  * @Email:     lab1024@163.com 
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012 
-->
<template>
  <a-sub-menu :key="props.menuInfo.menuId.toString()">
    <template #icon>
      <component :is="$antIcons[props.menuInfo.icon]" />
    </template>
    <template #title>{{ props.menuInfo.menuName }}</template>
    <template v-for="item in props.menuInfo.children" :key="item.menuId">
      <template v-if="item.visibleFlag">
        <!-- 判空必须用 isEmpty：空数组是 truthy，用 !item.children 会渲染出空的 a-sub-menu，
             触发 antd 的 useInjectMenu 报错。与 recursion-menu.vue 的顶层判断同口径。 -->
        <template v-if="$lodash.isEmpty(item.children)">
          <a-menu-item :key="item.menuId.toString()" @click="turnToPage(item)">
            <template #icon>
              <component :is="$antIcons[item.icon]" />
            </template>
            {{ item.menuName }}
          </a-menu-item>
        </template>
        <template v-else>
          <SubMenu :menu-info="item" :key="item.menuId" @turnToPage="turnToPage" />
        </template>
      </template>
    </template>
  </a-sub-menu>
</template>
<script setup>
  let props = defineProps({
    menuInfo: Object,
  });
  const emits = defineEmits(['turnToPage']);
  const turnToPage = (route) => {
    emits('turnToPage', route);
  };
</script>
<style scoped lang="less">
  :deep(.ant-menu-item-selected) {
    border-right: 3px !important;
  }
</style>
