<script setup lang="ts">
import { useRoute } from 'vue-router'

import { fetchNotificationDetail } from '@/api/notification'
import { toId } from '@/types/contract'
import { useAsync } from '@/composables/useAsync'

/**
 * 通知详情。
 *
 * <h3>正文是服务端现渲染的，而且用的是「发送当时那一版」模板</h3>
 * 库里存的不是正文，是 `模板编码 + 版本号 + 参数`。这么设计有两个理由：
 *
 * <ul>
 *   <li><b>省</b>：注册成功那种正文几百字节、每个用户都一样，存参数只要几十字节。
 *       而那张表的行数是「业务事件数 × 时间」，会长到亿级；</li>
 *   <li><b>不篡改历史</b>：锁着版本号，所以运营改一次模板，
 *       你 1 月收到的那条消息措辞不会跟着变。</li>
 * </ul>
 *
 * <h3>⚠️ 打开即已读，不用再调一次接口</h3>
 * 服务端在返回详情时就把它标记了。端上再补一次调用是多余的，而<b>漏掉</b>
 * 那次调用的表现是「点开了红点还在」—— 把它放在服务端就没有漏的可能。
 */

const route = useRoute()
// 路由参数是裸 string，Id 是 branded type —— 经 toId 归一是唯一的入口，
// 这正是 branded type 想拦住的那种「随手当 string 用」
const detail = useAsync(() => fetchNotificationDetail(toId(String(route.params.id))))

const categoryLabel: Record<string, string> = {
  SYSTEM: '系统',
  TRADE: '交易',
  MARKETING: '活动',
}
</script>

<template>
  <div class="page">
    <NavBar title="消息详情" />

    <div class="page__body">
      <Section
        title="内容"
        :loading="detail.loading.value"
        :error="detail.error.value"
        :empty="false"
        empty-text=""
        @retry="detail.reload"
      >
        <article v-if="detail.data.value !== null" class="detail">
          <h1 class="detail__title">{{ detail.data.value.title }}</h1>

          <p class="detail__meta">
            <span class="detail__tag">{{
              categoryLabel[detail.data.value.category] ?? detail.data.value.category
            }}</span>
            <span>{{ detail.data.value.createTime }}</span>
          </p>

          <!-- white-space: pre-wrap —— 模板正文里的换行是运营排版的一部分 -->
          <p class="detail__content">{{ detail.data.value.content }}</p>
        </article>
      </Section>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: var(--sv-bg-page);
}

.page__body {
  padding: 12px 16px 24px;
}

.detail {
  padding: 16px 14px;
  background: var(--sv-bg-surface);
  border-radius: var(--sv-radius-lg);
}

.detail__title {
  margin: 0 0 8px;
  font-size: var(--sv-font-title);
  line-height: 1.4;
  color: var(--sv-text-primary);
}

.detail__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  font-size: var(--sv-font-caption);
  color: var(--sv-text-secondary);
}

.detail__tag {
  padding: 1px 6px;
  border-radius: var(--sv-radius-sm);
  background: var(--sv-bg-page);
}

.detail__content {
  margin: 0;
  font-size: var(--sv-font-body);
  line-height: 1.7;
  color: var(--sv-text-primary);
  white-space: pre-wrap;
}
</style>
