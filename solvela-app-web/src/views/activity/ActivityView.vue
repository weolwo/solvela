<script setup lang="ts">
import { nextTick, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  drawActivity,
  fetchActivityDetail,
  landingIndex,
  MAX_DRAW_TIMES,
  prizeLabel,
  type DrawOutcome,
} from '@/api/activity'
import { ApiError } from '@/api/errors'
import { fetchPrizeRecords } from '@/api/records'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/datetime'

import Backdrop from './Backdrop.vue'
import Wheel from './Wheel.vue'

/**
 * 中秋抽奖专题页。
 *
 * <h3>为什么是匿名可访问</h3>
 * 路由标了 `anonymous` —— 对齐后端 {@code ActivityController.getActivity} 的 `@Anonymous`：
 * 活动页是分享出去的入口，要求先登录才能看一眼等于把分享链路掐断。
 * <b>抽奖那一步再要求登录</b>（{@link onDraw} 里判 `auth.isLoggedIn`）。
 *
 * <h3>页面的组成</h3>
 * 节日底（{@link Backdrop}）与转盘（{@link Wheel}）都在同目录下，<b>不在 ui/</b> ——
 * 它们认识「中秋」和「抽奖」，是这一页的私有零件。ui/ 下的组件对业务一无所知，
 * 由 unplugin-vue-components 自动注册；这里的两个必须显式 import，
 * 这个「多打一行 import」正是边界本身。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// noUncheckedIndexedAccess 下 route.params.code 是 string | string[] | undefined
const rawCode = route.params.code
const activityCode = (Array.isArray(rawCode) ? rawCode[0] : rawCode) ?? ''

const detail = useAsync(() => fetchActivityDetail(activityCode))

/*
 * 这个活动里我中过什么。
 *
 * 🔴 放在活动页而不是「我的」：奖励记录天然属于活动 ——
 * 用户抽完奖第一件想确认的是「刚才那个到账没有」，而他此刻就在这一页上。
 * 过滤由服务端按 activityCode 做，不是拿最近 20 条回来再筛：
 * 参与多个活动的用户会筛出空列表，而他明明在这个活动里中过奖。
 *
 * 未登录不查 —— 这一页是匿名可看的，而记录是「我的」东西。
 */
const records = useAsync(() =>
  auth.isLoggedIn ? fetchPrizeRecords(activityCode) : Promise.resolve([]),
)

/* ---- 转盘驱动：不调子组件方法，用「自增计数器 + 目标索引」驱动，停下 emit rest ---- */
const spinCounter = ref(0)
const spinTarget = ref(0)
let restResolve: (() => void) | null = null

function onWheelRest(): void {
  restResolve?.()
  restResolve = null
}

/** 转到某一格，Promise 在转盘停下时 resolve */
function spinWheelTo(index: number): Promise<void> {
  return new Promise((resolve) => {
    restResolve = resolve
    spinTarget.value = index
    spinCounter.value += 1
  })
}

/* ---- 抽奖 ---- */
/** 正在抽奖的那次的意愿次数（1 或 MAX_DRAW_TIMES），null 表示空闲。用来只给点中的那个按钮转圈 */
const pendingTimes = ref<number | null>(null)
const result = ref<DrawOutcome | null>(null)
const hintText = ref('')

async function onDraw(times: number): Promise<void> {
  if (pendingTimes.value !== null) {
    return
  }
  const data = detail.data.value
  if (data === null || data.wheel === null) {
    return
  }
  if (!data.joinable) {
    hintText.value = '活动不在进行中'
    return
  }
  if (!auth.isLoggedIn) {
    // 抽奖要登录。带 redirect 回来，登完就在这一页
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }

  pendingTimes.value = times
  hintText.value = ''
  result.value = null
  try {
    // 幂等键客户端生成，一次点击一个。网络超时不代表没抽
    const requestId = crypto.randomUUID()
    const outcome = await drawActivity(activityCode, times, requestId)
    await spinWheelTo(landingIndex(outcome, data.wheel.prizes))
    result.value = outcome
  } catch (error) {
    hintText.value = error instanceof ApiError ? error.message : '抽奖失败，请稍后再试'
  } finally {
    pendingTimes.value = null
  }
}

/*
 * 结果浮层。里面只有<b>一个</b>可交互元素（关闭按钮），所以「焦点陷阱」在这里
 * 退化成两件小事：打开时把焦点放到按钮上、Tab 时不让它跑出去。
 * 这不是「要碰焦点陷阱的别自己写」那条原则拦的东西 —— 那条拦的是 Picker / Popup
 * 里多个可聚焦元素之间的环绕。点遮罩、按 Esc、点按钮都能关。
 */
const dismissButton = useTemplateRef<HTMLButtonElement>('dismissButton')

function closeResult(): void {
  result.value = null
  /*
   * 关掉结果弹窗时刷一次记录 —— 刚中的那个奖此刻才该出现在列表里。
   * 不刷的话用户会看到「中奖了」和一个不含它的记录列表，
   * 而那正是他会去问客服的场景。
   *
   * 放在关闭而不是开奖那一刻：开奖是异步发奖，那时记录可能还没落库。
   */
  if (auth.isLoggedIn) {
    void records.reload()
  }
}

function onOverlayKeydown(event: KeyboardEvent): void {
  if (result.value === null) {
    return
  }
  if (event.key === 'Escape') {
    closeResult()
  } else if (event.key === 'Tab') {
    // 浮层里无处可去，别让焦点溜到背后的页面
    event.preventDefault()
  }
}

watch(result, (value) => {
  if (value !== null) {
    void nextTick(() => dismissButton.value?.focus())
  }
})

function goBack(): void {
  router.back()
}
</script>

<template>
  <div class="page">
    <Backdrop />

    <div class="page__content">
      <button class="back" type="button" aria-label="返回" @click="goBack">
        <Icon name="chevron" :size="22" style="transform: scaleX(-1)" />
      </button>

      <!-- 加载 / 出错：专题页是深色，不能用 Section（那套是浅色的） -->
      <div v-if="detail.loading.value" class="state" aria-hidden="true">
        <span class="state__dot" />
      </div>

      <div v-else-if="detail.error.value !== null" class="state" role="alert">
        <p class="state__text">{{ detail.error.value }}</p>
        <button class="state__retry" type="button" @click="detail.reload">重试</button>
      </div>

      <template v-else-if="detail.data.value !== null">
        <header class="hero">
          <p class="hero__eyebrow">情满中秋 · 共赏婵娟</p>
          <h1 class="hero__title">{{ detail.data.value.activityName }}</h1>
          <p v-if="detail.data.value.subTitle !== null" class="hero__sub">
            {{ detail.data.value.subTitle }}
          </p>
        </header>

        <Wheel
          v-if="detail.data.value.wheel !== null"
          class="wheel"
          :prizes="detail.data.value.wheel.prizes"
          :spin="spinCounter"
          :target-index="spinTarget"
          @rest="onWheelRest"
        />
        <p v-else class="config-broken">活动配置异常，请稍后再来看看</p>

        <p v-if="hintText !== ''" class="hint" role="alert">{{ hintText }}</p>

        <div v-if="detail.data.value.wheel !== null" class="actions">
          <div class="actions__slot">
            <Button
              :disabled="pendingTimes !== null || !detail.data.value.joinable"
              :loading="pendingTimes === 1"
              @click="onDraw(1)"
            >
              抽一次
            </Button>
          </div>
          <div class="actions__slot">
            <Button
              class="actions__ten"
              :disabled="pendingTimes !== null || !detail.data.value.joinable"
              :loading="pendingTimes === MAX_DRAW_TIMES"
              @click="onDraw(MAX_DRAW_TIMES)"
            >
              抽 {{ MAX_DRAW_TIMES }} 次
            </Button>
          </div>
        </div>

        <p v-if="!detail.data.value.joinable" class="deadline">活动不在进行中</p>
        <p v-else class="deadline">
          活动截止 {{ formatDateTime(detail.data.value.endTime, 'MM月DD日 HH:mm') }}
        </p>

        <details v-if="detail.data.value.ruleContent !== null" class="rules">
          <summary class="rules__summary">活动规则</summary>
          <!--
            规则正文是运营在后台配的富文本 HTML，来自我们自己的后端（见 ActivityRuleView 的注释），
            按可信内容渲染。这里刻意不引 sanitize 库 —— 内容来源与「运营配的活动名」同级。
          -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div class="rules__body" v-html="detail.data.value.ruleContent"></div>
        </details>
      </template>
    </div>

    <!--
      我的奖励记录。登录后才有 —— 这一页匿名可看，而记录是「我的」东西。
      放在这里是因为用户抽完奖第一件想确认的就是「刚才那个到账没有」。
    -->
    <Section
      v-if="auth.isLoggedIn"
      title="我的中奖记录"
      :loading="records.loading.value"
      :error="records.error.value"
      :empty="(records.data.value ?? []).length === 0"
      empty-text="还没有中奖记录"
      @retry="records.reload"
    >
      <Card>
        <div v-for="item in records.data.value ?? []" :key="item.recordId" class="rec">
          <div class="rec__main">
            <p class="rec__title">{{ item.title }}</p>
            <p class="rec__time">{{ item.createTime }}</p>
          </div>
          <span class="rec__status" :class="`rec__status--${item.status.toLowerCase()}`">
            {{ item.statusText }}
          </span>
        </div>
      </Card>
    </Section>

    <!-- 抽奖结果 -->
    <Teleport to="body">
      <Transition name="prize">
        <div
          v-if="result !== null"
          class="prize"
          role="dialog"
          aria-modal="true"
          aria-labelledby="prize-title"
          @click.self="closeResult"
          @keydown="onOverlayKeydown"
        >
          <div class="prize__card">
            <p class="prize__emoji" aria-hidden="true">{{ result.hitCount > 0 ? '🎉' : '🌙' }}</p>
            <p id="prize-title" class="prize__title">{{ result.message }}</p>

            <ul v-if="result.records.length > 1" class="prize__list">
              <li
                v-for="(record, i) in result.records"
                :key="i"
                class="prize__item"
                :class="{ 'prize__item--hit': record.hit }"
              >
                {{ prizeLabel(record.prizeCode, detail.data.value?.wheel?.prizes ?? []) }}
              </li>
            </ul>
            <p v-else-if="result.records[0]?.hit === true" class="prize__single">
              {{ prizeLabel(result.records[0].prizeCode, detail.data.value?.wheel?.prizes ?? []) }}
            </p>

            <button ref="dismissButton" class="prize__btn" type="button" @click="closeResult">
              {{ result.hitCount > 0 ? '开心收下' : '知道了' }}
            </button>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.rec {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
}

.rec + .rec {
  border-top: 1px solid var(--sv-border-subtle);
}

.rec__main {
  min-width: 0;
}

.rec__title {
  margin: 0;
  font-size: 15px;
  color: var(--sv-text-primary);
}

.rec__time {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--sv-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.rec__status {
  flex: none;
  font-size: 12px;
}

.rec__status--pending {
  color: var(--sv-color-warning);
}

.rec__status--done {
  color: var(--sv-color-success);
}

.rec__status--failed {
  color: var(--sv-color-danger);
}

/*
 * 专题页是活动视觉：深色夜空一套自成体系的颜色，不走 --sv-* 那套浅色平台变量。
 * 间距 / 圆角 / 字号仍然用 token。
 */
.page {
  position: relative;
  min-height: 100dvh;
  overflow: hidden;
  color: #fff3e0;
}

.page__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-lg);
  padding: calc(var(--sv-safe-top) + 56px) var(--sv-space-page)
    calc(var(--sv-safe-bottom) + var(--sv-space-xl));
}

.back {
  position: absolute;
  top: calc(var(--sv-safe-top) + var(--sv-space-sm));
  left: var(--sv-space-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: rgb(255 255 255 / 12%);
  color: #fff3e0;
  cursor: pointer;
}

.back:active {
  background: rgb(255 255 255 / 22%);
}

.back:focus-visible {
  outline: 2px solid #f2ce7e;
  outline-offset: 2px;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-md);
  margin-top: 30vh;
}

.state__dot {
  width: 28px;
  height: 28px;
  border: 3px solid rgb(255 255 255 / 25%);
  border-top-color: #f2ce7e;
  border-radius: 50%;
  animation: prize-spin 0.8s linear infinite;
}

@keyframes prize-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .state__dot {
    animation-duration: 2.4s;
  }
}

.state__text {
  margin: 0;
  font-size: var(--sv-font-caption);
}

.state__retry {
  border: 1px solid currentcolor;
  padding: var(--sv-space-xs) var(--sv-space-md);
  border-radius: var(--sv-radius-pill);
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: var(--sv-font-caption);
  cursor: pointer;
}

.hero {
  text-align: center;
}

.hero__eyebrow {
  margin: 0;
  color: #f2ce7e;
  font-size: var(--sv-font-caption);
  letter-spacing: 0.16em;
}

.hero__title {
  margin: var(--sv-space-sm) 0 0;
  font-size: var(--sv-font-title);
  font-weight: 700;
  letter-spacing: 0.02em;
  /* 月亮的光晕可能扫到标题右侧，双层阴影保证字在亮底上也读得清 */
  text-shadow:
    0 2px 10px rgb(6 14 30 / 70%),
    0 0 3px rgb(6 14 30 / 60%);
}

.hero__sub {
  margin: var(--sv-space-sm) 0 0;
  color: rgb(255 243 224 / 82%);
  font-size: var(--sv-font-caption);
}

.wheel {
  margin: var(--sv-space-sm) 0;
}

.config-broken {
  margin: 15vh 0;
  color: rgb(255 243 224 / 80%);
  font-size: var(--sv-font-caption);
}

.hint {
  margin: 0;
  color: #ffd7cf;
  font-size: var(--sv-font-caption);
}

.actions {
  display: flex;
  gap: var(--sv-space-md);
  width: 100%;
  max-width: 320px;
}

.actions__slot {
  flex: 1;
}

/* 十连抽用描金实底，和"大奖"那一格同一套语言 */
.actions__ten {
  background: linear-gradient(150deg, #f6c453, #e0a23a);
  color: #5a1329;
  font-weight: 700;
}

.deadline {
  margin: 0;
  color: rgb(255 243 224 / 62%);
  font-size: var(--sv-font-footnote);
  font-variant-numeric: tabular-nums;
}

.rules {
  width: 100%;
  max-width: 360px;
  border-radius: var(--sv-radius-lg);
  background: rgb(0 0 0 / 22%);
}

.rules__summary {
  padding: var(--sv-space-md);
  color: #f2ce7e;
  font-size: var(--sv-font-caption);
  cursor: pointer;
  list-style: none;
}

.rules__summary::-webkit-details-marker {
  display: none;
}

.rules__body {
  padding: 0 var(--sv-space-md) var(--sv-space-md);
  color: rgb(255 243 224 / 80%);
  font-size: var(--sv-font-footnote);
  line-height: 1.7;
}

.rules__body :deep(p) {
  margin: 0 0 var(--sv-space-sm);
}

/* ---- 结果浮层 ---- */
.prize {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sv-space-page);
  background: rgb(8 16 32 / 62%);
  backdrop-filter: blur(3px);
}

.prize__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-md);
  width: 100%;
  max-width: 300px;
  padding: var(--sv-space-xl) var(--sv-space-lg);
  border: 1px solid rgb(242 206 126 / 45%);
  border-radius: var(--sv-radius-lg);
  background: linear-gradient(160deg, #1f4372, #122a4c);
  color: #fff3e0;
  text-align: center;
}

.prize__emoji {
  margin: 0;
  font-size: 40px;
  line-height: 1;
}

.prize__title {
  margin: 0;
  font-size: var(--sv-font-heading);
  font-weight: 700;
}

.prize__single {
  margin: 0;
  color: #f2ce7e;
  font-size: var(--sv-font-body);
  font-weight: 600;
}

.prize__list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sv-space-xs);
  width: 100%;
  margin: 0;
  padding: 0;
  list-style: none;
}

.prize__item {
  padding: var(--sv-space-xs) var(--sv-space-sm);
  border-radius: var(--sv-radius-sm);
  background: rgb(255 255 255 / 8%);
  font-size: var(--sv-font-footnote);
}

.prize__item--hit {
  background: rgb(242 206 126 / 22%);
  color: #f7dfa8;
  font-weight: 600;
}

.prize__btn {
  width: 100%;
  height: var(--sv-control-height);
  border: 0;
  border-radius: var(--sv-radius-pill);
  background: linear-gradient(150deg, #f6c453, #e0a23a);
  color: #5a1329;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.prize__btn:focus-visible {
  outline: 2px solid #fff3e0;
  outline-offset: 2px;
}

.prize-enter-active,
.prize-leave-active {
  transition: opacity 0.2s ease;
}

.prize-enter-active .prize__card {
  transition: transform 0.24s cubic-bezier(0.2, 1.2, 0.4, 1);
}

.prize-enter-from,
.prize-leave-to {
  opacity: 0;
}

.prize-enter-from .prize__card {
  transform: scale(0.9);
}

@media (prefers-reduced-motion: reduce) {
  .prize-enter-active .prize__card {
    transition: none;
  }
}
</style>
