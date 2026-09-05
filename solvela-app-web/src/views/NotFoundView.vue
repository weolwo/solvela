<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="page">
    <p class="page__code">404</p>
    <p class="page__text">页面不存在</p>
    <Button class="page__action" :block="false" @click="router.replace('/')">回到首页</Button>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: var(--sv-space-page);
  gap: var(--sv-space-sm);
}

.page__code {
  margin: 0;
  color: var(--sv-text-placeholder);
  font-size: 56px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.page__text {
  margin: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
}

.page__action {
  margin-top: var(--sv-space-md);
}
</style>
