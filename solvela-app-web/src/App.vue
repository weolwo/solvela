<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

const route = useRoute()

/**
 * 底部导航只在一级页出现。
 *
 * <p>用 `meta.tab` 而不是判断路径：路径会改，而「这是不是一级页」是路由自己的属性。
 * 判断路径的写法在加第四个 Tab 或改 path 时会静默漏掉一个页面。
 */
const showTabBar = computed(() => route.meta.tab === true)
</script>

<template>
  <!--
    底部空间由外壳统一留，不让每个页面自己写 padding-bottom ——
    那样改 TabBar 高度要改 N 处，而漏改的表现是内容被压在导航底下。
  -->
  <div class="shell" :class="{ 'shell--with-tabbar': showTabBar }">
    <RouterView v-slot="{ Component }">
      <component :is="Component" />
    </RouterView>
  </div>

  <SvTabBar v-if="showTabBar" />
</template>

<style scoped>
.shell {
  min-height: 100%;
}

.shell--with-tabbar {
  padding-bottom: calc(var(--sv-tabbar-height) + var(--sv-safe-bottom));
}
</style>
