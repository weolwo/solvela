<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <p class="not-found__text">页面不存在</p>
    <var-button type="primary" @click="router.replace('/')">回到首页</var-button>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: var(--sv-space-md);
}

.not-found__text {
  margin: 0;
  color: var(--sv-text-secondary);
}
</style>
