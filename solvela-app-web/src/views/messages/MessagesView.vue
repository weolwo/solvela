<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import {
  type AnnouncementItem,
  type NotificationItem,
  fetchAnnouncements,
  fetchNotifications,
  readAllAnnouncements,
  readAllNotifications,
  readAnnouncement,
} from '@/api/notification'
import { useAsync } from '@/composables/useAsync'

/**
 * 消息中心。
 *
 * <h3>🔴 两个 tab，不是一个混合列表</h3>
 * 通知（写给你一个人的）和公告（发给所有人的）在服务端就是两套存储模型，
 * 已读语义也不同：
 *
 * <ul>
 *   <li><b>通知</b>逐条已读 —— 点开第 5 条，前 4 条仍是未读；</li>
 *   <li><b>公告</b>是一个游标 —— 点开第 5 条，<b>前面的全部算已读</b>。</li>
 * </ul>
 *
 * 混在一个列表里的话，用户点一条通知红点掉 1、点一条公告红点掉一片，
 * 同一个列表两种规则，看着就是 bug。分成两个 tab 之后这个矛盾消失了，
 * 服务端那边也因此省掉了整套「两路归并分页」的复杂度。
 *
 * <p>⚠️ 哪天有人提「把两个 tab 合并」，要连带回去看服务端的游标设计 ——
 * 合并之后公告就必须支持跳读，那需要重新引入一个会不断变宽的例外集合。
 *
 * <h3>为什么公告用「加载更多」而通知用页码</h3>
 * 公告会持续新增，页码分页在第 2 页会重复看到被挤下来的那一条；
 * 通知是按 id 倒序的个人列表，新增只发生在最前面，页码是安全的。
 */

type Tab = 'notification' | 'announcement'

const TABS = [
  { value: 'notification' as const, label: '通知' },
  { value: 'announcement' as const, label: '公告' },
]

const tab = ref<Tab>('notification')

// ---------------------------------------------------------------- 通知

const notifications = useAsync(() => fetchNotifications({ pageSize: 30 }))

/** 本地维护一份，因为「点开即已读」要就地改 readFlag，而不是整页重拉 */
const notificationList = ref<NotificationItem[]>([])
const notificationUnread = ref(0)

watch(notifications.data, (page) => {
  notificationList.value = page?.list ?? []
  notificationUnread.value = page?.unreadCount ?? 0
})

async function markAllNotificationsRead() {
  await readAllNotifications()
  // 就地改而不是重拉：重拉会让列表闪一下，而这里的结果是确定的
  notificationList.value = notificationList.value.map((item) => ({ ...item, readFlag: 1 }))
  notificationUnread.value = 0
}

// ---------------------------------------------------------------- 公告

const announcements = useAsync(() => fetchAnnouncements(undefined, 20))

const announcementList = ref<AnnouncementItem[]>([])
const announcementNoMore = ref(false)
const loadingMore = ref(false)

watch(announcements.data, (list) => {
  announcementList.value = list ?? []
  announcementNoMore.value = (list ?? []).length < 20
})

const announcementUnread = computed(
  () => announcementList.value.filter((item) => item.unread).length,
)

async function loadMoreAnnouncements() {
  const last = announcementList.value.at(-1)
  if (last === undefined || loadingMore.value) {
    return
  }
  loadingMore.value = true
  try {
    const more = await fetchAnnouncements(last.id, 20)
    announcementList.value = [...announcementList.value, ...more]
    announcementNoMore.value = more.length < 20
  } finally {
    loadingMore.value = false
  }
}

/**
 * 展开一条公告。
 *
 * 🔴 顺手把它<b>以及它之前的全部</b>标成已读 —— 那是服务端游标的语义，
 * 端上必须如实反映，否则用户会看到「我明明点了，上面那几条怎么还是未读」。
 */
const expandedId = ref<string | null>(null)

async function toggleAnnouncement(item: AnnouncementItem) {
  expandedId.value = expandedId.value === item.id ? null : item.id

  if (!item.unread) {
    return
  }
  await readAnnouncement(item.id)
  // 游标语义：这一条【及其之前】的全部变已读。列表按 id 倒序，
  // 所以是「它自己和它后面的（更旧的）」
  const index = announcementList.value.findIndex((row) => row.id === item.id)
  announcementList.value = announcementList.value.map((row, i) =>
    i >= index ? { ...row, unread: false } : row,
  )
}

async function markAllAnnouncementsRead() {
  await readAllAnnouncements()
  announcementList.value = announcementList.value.map((item) => ({ ...item, unread: false }))
}

// ---------------------------------------------------------------- 首屏
// useAsync 自己会 void reload()，这里不用再触发一次 —— 多调一次就是多一个请求

const categoryLabel: Record<string, string> = {
  SYSTEM: '系统',
  TRADE: '交易',
  MARKETING: '活动',
}
</script>

<template>
  <div class="page">
    <NavBar title="消息" />

    <div class="page__body">
      <div class="tabs">
        <Segmented v-model="tab" :options="TABS" />
      </div>

      <!-- ---------------------------------------------------------- 通知 -->
      <Section
        v-if="tab === 'notification'"
        title="通知"
        :loading="notifications.loading.value"
        :error="notifications.error.value"
        :empty="notificationList.length === 0"
        empty-text="还没有通知"
        @retry="notifications.reload"
      >
        <template #action>
          <button
            v-if="notificationUnread > 0"
            type="button"
            class="page__link"
            @click="markAllNotificationsRead"
          >
            全部已读（{{ notificationUnread }}）
          </button>
        </template>

        <ul class="cards">
          <li v-for="item in notificationList" :key="item.id" class="card">
            <RouterLink
              class="card__link"
              :to="{ name: 'message-detail', params: { id: item.id } }"
            >
              <span class="card__dot" :class="{ 'card__dot--on': item.readFlag === 0 }" />

              <div class="card__main">
                <p class="card__summary" :class="{ 'card__summary--read': item.readFlag === 1 }">
                  {{ item.summary }}
                </p>
                <p class="card__meta">
                  <span class="card__tag">{{ categoryLabel[item.category] ?? item.category }}</span>
                  <span>{{ item.createTime }}</span>
                </p>
              </div>

              <Icon name="chevron" :size="16" class="card__arrow" />
            </RouterLink>
          </li>
        </ul>
      </Section>

      <!-- ---------------------------------------------------------- 公告 -->
      <Section
        v-else
        title="公告"
        :loading="announcements.loading.value"
        :error="announcements.error.value"
        :empty="announcementList.length === 0"
        empty-text="暂无公告"
        @retry="announcements.reload"
      >
        <template #action>
          <button
            v-if="announcementUnread > 0"
            type="button"
            class="page__link"
            @click="markAllAnnouncementsRead"
          >
            全部已读（{{ announcementUnread }}）
          </button>
        </template>

        <ul class="cards">
          <li v-for="item in announcementList" :key="item.id" class="card card--block">
            <!--
              公告直接在列表里展开，不跳详情页：正文是运营手写的一整段，
              没有模板要渲染、也没有更多字段要拉，跳一次页面纯属多余。
            -->
            <button type="button" class="card__head" @click="toggleAnnouncement(item)">
              <span class="card__dot" :class="{ 'card__dot--on': item.unread }" />
              <div class="card__main">
                <p class="card__title">{{ item.title }}</p>
                <p class="card__meta">
                  <span class="card__tag">{{ categoryLabel[item.category] ?? item.category }}</span>
                  <span>{{ item.publishTime }}</span>
                </p>
              </div>
              <!-- 图标集里只有一个朝右的 chevron，方向靠旋转表达 -->
              <Icon
                name="chevron"
                :size="16"
                class="card__arrow"
                :class="expandedId === item.id ? 'card__arrow--up' : 'card__arrow--down'"
              />
            </button>

            <p v-if="expandedId === item.id" class="card__content">{{ item.content }}</p>
          </li>
        </ul>

        <button
          v-if="!announcementNoMore"
          type="button"
          class="more"
          :disabled="loadingMore"
          @click="loadMoreAnnouncements"
        >
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </button>
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

.tabs {
  margin-bottom: 12px;
}

.page__link {
  border: 0;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--sv-color-primary);
  cursor: pointer;
}

.cards {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card {
  background: var(--sv-bg-surface);
  border-radius: 12px;
  overflow: hidden;
}

.card__link,
.card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  /* Card 不自带 padding 是本项目的约定，由子元素给 —— 不给的话文字会贴着边框 */
  padding: 14px 14px;
  border: 0;
  background: none;
  text-align: left;
  color: inherit;
  text-decoration: none;
  cursor: pointer;
}

.card__main {
  flex: 1;
  min-width: 0;
}

/* 未读圆点。已读时保留占位，否则整行文字会左右跳动 */
.card__dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: transparent;
}

.card__dot--on {
  background: var(--sv-color-danger);
}

.card__summary,
.card__title {
  margin: 0 0 4px;
  font-size: 15px;
  line-height: 1.45;
  color: var(--sv-text-primary);
}

.card__summary {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 已读的压一档，但不要压到看不清 —— 它仍然是用户要能读的内容 */
.card__summary--read {
  color: var(--sv-text-secondary);
}

.card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 12px;
  color: var(--sv-text-secondary);
}

.card__tag {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--sv-bg-page);
  color: var(--sv-text-secondary);
}

.card__content {
  margin: 0;
  padding: 0 14px 14px 32px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--sv-text-secondary);
  white-space: pre-wrap;
}

.more {
  width: 100%;
  margin-top: 12px;
  padding: 10px;
  border: 0;
  border-radius: 10px;
  background: var(--sv-bg-surface);
  font-size: 14px;
  color: var(--sv-text-secondary);
  cursor: pointer;
}

.more:disabled {
  opacity: 0.6;
  cursor: default;
}

/* 图标集里只有一个朝右的 chevron（见 ui/Icon.vue），方向全靠旋转 */
.card__arrow {
  flex: none;
  color: var(--sv-text-placeholder);
}

.card__arrow--down {
  transform: rotate(90deg);
}

.card__arrow--up {
  transform: rotate(-90deg);
}
</style>
