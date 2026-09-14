<script setup lang="ts">
/**
 * 设置页。
 *
 * <h3>它终于有一项真实内容了</h3>
 * 之前是空壳（「我的」页上的「设置」必须指向一个真实存在的页面，否则用户会掉进
 * 404 —— 与本项目在别处坚持的「不给死链接」是同一条原则）。
 * 2026-09-10 加了「登录设备」，那是第一项。
 *
 * <p>这里刻意<b>没有</b>用 Section：它封装的是「加载/出错/空」三态，
 * 而这一页是一张写死的清单，不会加载、不会出错、不会为空。
 * 硬套的话要传四个恒定值的 prop，读的人还要停下来确认它们是不是真的恒定。
 */
</script>

<template>
  <div class="page">
    <NavBar title="设置" />

    <div class="page__body">
      <h2 class="page__group">账号安全</h2>
      <div class="page__card">
        <Cell icon="phone" title="手机号" :to="{ name: 'phone-bind' }" />
        <Cell icon="user" title="邮箱" :to="{ name: 'email-bind' }" />
        <Cell icon="lock" title="登录设备" :to="{ name: 'sessions' }" />
        <!--
          消息设置：只有交易和活动两个开关。
          🔴 没有「系统通知」那一档 —— 账号被冻结这种事不该能被静音，
          服务端的契约里压根没有那个字段，所以这里也画不出那个开关。
        -->
        <Cell icon="settings" title="消息设置" :to="{ name: 'notification-preference' }" />
      </div>
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
  padding: var(--sv-space-page);
}

.page__group {
  margin: 0 0 var(--sv-space-sm);
  padding-left: var(--sv-space-xs);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  font-weight: 500;
}

.page__card {
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  overflow: hidden;
}
</style>
