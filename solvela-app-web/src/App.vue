<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import ForceAckDialog from '@/components/ForceAckDialog.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

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

  <TabBar v-if="showTabBar" />

  <!--
    强制确认公告的弹窗。挂在外壳上而不是某个页面里 —— 它要在**任何**页面都能弹出来，
    否则用户停在商品详情页就永远躲过了那条必须确认的公告。

    只在登录后挂：它按会员查待确认列表，未登录时那次请求注定 401。

    ⚠️ 它自己不做轮询，只在挂载时拉一次。没确认的话下次进 App 会再弹 ——
    服务端那边「没确认就一直返回」，机制自带催办，不需要端上守着。
  -->
  <ForceAckDialog v-if="auth.isLoggedIn" />
</template>

<style scoped>
.shell {
  min-height: 100%;
}

.shell--with-tabbar {
  padding-bottom: calc(var(--sv-tabbar-height) + var(--sv-safe-bottom));
}
</style>
