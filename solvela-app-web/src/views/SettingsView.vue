<script setup lang="ts">
/**
 * 设置页。
 *
 * <h3>🔴 目前是空壳，是刻意的</h3>
 * 「我的」页面上「设置」这一项必须指向一个真实存在的页面 ——
 * 指向一个不存在的路由，用户点了会掉进 404，这跟本项目在别处刻意坚持的
 * 「不给死链接」（忘记密码置灰、优惠页未开活动不做链接）是同一条原则的反面。
 *
 * <p>具体设置项（消息通知、隐私、账号安全……）等真实需求出现时再加，
 * 不在这次"先把框架搭出来"的范围内。
 */
</script>

<template>
  <div class="page">
    <NavBar title="设置" />

    <div class="page__body">
      <p class="page__empty">设置项正在建设中，敬请期待</p>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.page__body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sv-space-page);
}

.page__empty {
  margin: 0;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
}
</style>
