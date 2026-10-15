<script setup lang="ts">
/**
 * 我的登录设备。
 *
 * <h3>这一页存在的理由</h3>
 * 用户要能自己回答两个问题：**现在有谁登着我的号**，以及**怎么把他踢掉**。
 * 在它之前，答案分别是「看不到」和「改密码」—— 而改密码会把自己也登出，
 * 代价高到大多数人宁可什么都不做。
 *
 * <h3>一台设备一张卡，不是一张长列表</h3>
 * 列表行适合「一屏十几项、快速扫读」的设置页；而这一页通常只有两三条，
 * 每一条都要读三行信息（设备、位置、时间）再做一个有后果的决定。
 * 独立的卡片把每一条围成一个可以单独判断的单元，行与行之间不会看串。
 *
 * <h3>🔴 「下线」和「这不是我」是两个不同的动作，刻意分开</h3>
 * 「下线」是「这台我不用了」—— 清理旧手机、公用电脑，很常见，点一下就执行。
 * 「这不是我」是「我的号被人登了」—— 它还会**冻结兑换、加地址、充话费**到改密为止，
 * 并撤销其余设备的信任。合成一个按钮的话，每次清理旧设备都会把自己的账号冻住。
 *
 * <p>所以后者要二次确认，而且确认之后要**明确告诉用户接下来会发生什么** ——
 * 不说清楚的话，他下一次兑换时会以为系统坏了。
 *
 * <h3>🔴 当前这一台必须一眼认出来</h3>
 * 不标的话，用户很容易把自己这台点下线，然后当场被踢出去 —— 而他会以为是页面出了 bug。
 * 所以当前这一条排最前、图标高亮、带「本机」标签、**没有下线按钮**
 *（要退出当前设备走「我的」页那一项）。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  fetchSessions,
  reportSession,
  revokeOtherSessions,
  revokeSession,
  type MemberSession,
} from '@/api/session'
import { ApiError } from '@/api/errors'
import type { IconName } from '@/ui/Icon.vue'

const router = useRouter()

const sessions = ref<MemberSession[]>([])
const loading = ref(true)
const errorMessage = ref('')
/** 正在下线的那一条，按钮转圈用 */
const revoking = ref<string | null>(null)
const revokingOthers = ref(false)
/** 「下线其它设备」的二次确认。这个操作会把用户的其它设备全部踢掉，不该点一下就执行 */
const confirmingOthers = ref(false)
/** 正在确认举报的那一条。null = 没有在确认 */
const reportingTarget = ref<MemberSession | null>(null)
const reporting = ref(false)
/** 举报成功后的收尾提示：必须让用户知道「现在该去重置密码了」 */
const reportedDone = ref(false)

const DEVICE_LABELS: Record<string, string> = {
  APP: '手机 App',
  H5: '手机浏览器',
  WECHAT: '微信',
  PC: '电脑',
}

/**
 * 图标按设备端分。
 *
 * 图标集里没有「电脑」，PC 借用 home（一个方框，读起来像屏幕）——
 * 比给所有端一个相同的手机图标强：那样这一列就完全没有信息量了。
 */
const DEVICE_ICONS: Record<string, IconName> = {
  APP: 'phone',
  H5: 'phone',
  WECHAT: 'phone',
  PC: 'home',
}

/** 老会话可能没有设备端。显示「未知设备」而不是留白 —— 留白像是页面坏了 */
function deviceLabel(session: MemberSession): string {
  if (session.deviceType === null) {
    return '未知设备'
  }
  return DEVICE_LABELS[session.deviceType] ?? session.deviceType
}

function deviceIcon(session: MemberSession): IconName {
  return (session.deviceType === null ? undefined : DEVICE_ICONS[session.deviceType]) ?? 'user'
}

/**
 * 登录时间。
 *
 * loginTime 为 0 表示这是一条 2026-09-10 之前签发的旧会话 —— 那时还没记时间。
 * 显示「时间未知」而不是 1970 年，后者会让用户以为数据错乱。
 */
function loginLabel(session: MemberSession): string {
  if (session.loginTime <= 0) {
    return '登录时间未知'
  }
  const d = new Date(session.loginTime)
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function locationLabel(session: MemberSession): string {
  // region 目前服务端永远给 null（见 api/session.ts），所以实际显示的是 IP
  return [session.region, session.ip].filter(Boolean).join(' · ') || '位置未知'
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    sessions.value = await fetchSessions()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '加载失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

async function revoke(session: MemberSession): Promise<void> {
  if (revoking.value !== null) {
    return
  }
  revoking.value = session.sessionId
  errorMessage.value = ''
  try {
    await revokeSession(session.sessionId)
    // 重新拉一次而不是本地删掉那一行：期间可能有别的设备登进来，
    // 而这个页面上「列表是不是真的」比「响应快一点」重要
    await load()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '下线失败，请稍后再试'
  } finally {
    revoking.value = null
  }
}

async function revokeOthers(): Promise<void> {
  revokingOthers.value = true
  errorMessage.value = ''
  try {
    await revokeOtherSessions()
    confirmingOthers.value = false
    await load()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '操作失败，请稍后再试'
  } finally {
    revokingOthers.value = false
  }
}

/**
 * 举报确认。
 *
 * 🔴 成功之后**不是**悄悄刷新列表了事：资产出口已经被冻住，用户必须去重置密码才能恢复。
 * 不说这一句的话，他下一次兑换时会撞上一个看不懂的「账号处于保护状态」。
 */
async function confirmReport(): Promise<void> {
  const target = reportingTarget.value
  if (target === null || reporting.value) {
    return
  }
  reporting.value = true
  errorMessage.value = ''
  try {
    await reportSession(target)
    reportingTarget.value = null
    reportedDone.value = true
    await load()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '操作失败，请稍后再试'
  } finally {
    reporting.value = false
  }
}

async function goPasswordReset(): Promise<void> {
  reportedDone.value = false
  await router.push({ name: 'password-reset' })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <NavBar title="登录设备" />

    <div class="page__body">
      <p class="page__intro">这些设备当前登录着你的账号。不认识的，直接让它下线。</p>

      <!-- 骨架屏而不是转圈：它把「马上会出现什么形状」提前告诉了用户 -->
      <div v-if="loading" class="cards" aria-hidden="true">
        <div v-for="i in 2" :key="i" class="card card--skeleton" />
      </div>

      <div v-else-if="errorMessage !== ''" class="state">
        <p class="state__error" role="alert">{{ errorMessage }}</p>
        <Button variant="text" @click="load">重试</Button>
      </div>

      <p v-else-if="sessions.length === 0" class="state__empty">当前没有登录中的设备</p>

      <template v-else>
        <ul class="cards">
          <li
            v-for="session in sessions"
            :key="session.sessionId"
            class="card"
            :class="{ 'card--current': session.current }"
          >
            <span class="card__icon" :class="{ 'card__icon--current': session.current }">
              <Icon :name="deviceIcon(session)" :size="20" />
            </span>

            <div class="card__main">
              <div class="card__title">
                {{ deviceLabel(session) }}
                <span v-if="session.current" class="card__badge">本机</span>
              </div>
              <div class="card__meta">{{ locationLabel(session) }}</div>
              <div class="card__meta">{{ loginLabel(session) }}</div>
            </div>

            <!--
              当前这一台【没有】下线按钮。想退出当前设备走「我的」页那一项 ——
              在这里给一个，用户点下去会当场被踢出这个页面，而他多半只是想踢别人
            -->
            <div v-if="!session.current" class="card__ops">
              <Button
                variant="danger"
                :block="false"
                :loading="revoking === session.sessionId"
                @click="revoke(session)"
              >
                下线
              </Button>
              <!--
                「这不是我」比「下线」后果重得多，所以用文字按钮而不是第二颗醒目的按钮：
                两颗一样显眼的红按钮会让人随手点错，而点错的那一次会把自己的兑换冻住
              -->
              <Button variant="text" :block="false" @click="reportingTarget = session">
                这不是我
              </Button>
            </div>
          </li>
        </ul>

        <!--
          只有真的存在其它设备时才出现。只有一台时给一个「下线其它设备」，
          点下去什么都不会发生 —— 那种按钮会让人以为功能坏了
        -->
        <div v-if="sessions.length > 1" class="actions">
          <Button
            v-if="!confirmingOthers"
            variant="danger"
            :block="false"
            @click="confirmingOthers = true"
          >
            下线其它所有设备
          </Button>
          <template v-else>
            <p class="actions__confirm">
              除本机外的 {{ sessions.length - 1 }} 台设备都会被登出，你自己不受影响。
            </p>
            <div class="actions__row">
              <Button
                variant="danger"
                :block="false"
                :loading="revokingOthers"
                @click="revokeOthers"
              >
                确认下线
              </Button>
              <Button variant="text" :block="false" @click="confirmingOthers = false">取消</Button>
            </div>
          </template>
        </div>
      </template>
    </div>

    <!--
      举报的二次确认。🔴 必须把后果说全：下线、冻结、要去改密。
      只写一句「确定举报吗」的话，用户点完才发现兑换不了了。
    -->
    <div
      v-if="reportingTarget !== null"
      class="mask"
      role="alertdialog"
      aria-modal="true"
      aria-label="举报这次登录"
    >
      <div class="dialog">
        <h2 class="dialog__title">这不是我的登录？</h2>
        <p class="dialog__message">我们会立刻让这台设备下线，并把你其它设备也一起登出。</p>
        <p class="dialog__message">
          为了保护你的东西，<b>兑换、加收货地址、充话费会被暂时停用</b>，直到你重置密码。
        </p>
        <div class="dialog__actions">
          <Button variant="danger" :loading="reporting" @click="confirmReport">
            确认，这不是我
          </Button>
          <Button variant="text" @click="reportingTarget = null">取消</Button>
        </div>
      </div>
    </div>

    <!-- 举报成功后的收尾：把「接下来该做什么」一次说清，并给一条路 -->
    <div v-if="reportedDone" class="mask" role="alertdialog" aria-modal="true" aria-label="已处理">
      <div class="dialog">
        <h2 class="dialog__title">已处理</h2>
        <p class="dialog__message">
          那台设备已下线，你的其它设备也已登出。现在请重置密码 ——
          <b>这也是恢复兑换和充话费的唯一方式</b>。
        </p>
        <div class="dialog__actions">
          <Button @click="goPasswordReset">去重置密码</Button>
          <Button variant="text" @click="reportedDone = false">稍后再说</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 两颗操作竖排：横排时「这不是我」四个字会把卡片挤变形 */
.card__ops {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sv-space-xs);
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sv-space-page);
  background: rgb(0 0 0 / 45%);
}

.dialog {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  width: 100%;
  max-width: 340px;
  padding: var(--sv-space-lg);
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  box-shadow: var(--sv-shadow-overlay);
}

.dialog__title {
  margin: 0;
  font-size: var(--sv-font-heading);
  line-height: 1.4;
  color: var(--sv-text-primary);
}

.dialog__message {
  margin: 0;
  font-size: var(--sv-font-body);
  line-height: 1.7;
  color: var(--sv-text-secondary);
}

.dialog__actions {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
  margin-top: var(--sv-space-sm);
}

.page {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.page__body {
  flex: 1;
  padding: var(--sv-space-md) var(--sv-space-page) calc(var(--sv-safe-bottom) + var(--sv-space-lg));
}

.page__intro {
  margin: 0 0 var(--sv-space-md);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.5;
}

.cards {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  margin: 0;
  padding: 0;
  list-style: none;
}

/*
 * 一台设备一张卡。卡之间留空隙而不是用分隔线 ——
 * 每一条都要单独读、单独判断，挤在一起容易看串行。
 */
.card {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md);
  border: 1px solid transparent;
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

/* 本机那张描一圈主色：用户第一眼要找的就是它 */
.card--current {
  border-color: var(--sv-color-primary);
}

.card--skeleton {
  height: 72px;
  opacity: 0.6;
}

.card__icon {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--sv-bg-fill);
  color: var(--sv-text-secondary);
}

.card__icon--current {
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}

.card__main {
  flex: 1;
  min-width: 0;
}

.card__title {
  display: flex;
  align-items: center;
  gap: var(--sv-space-xs);
  font-size: var(--sv-font-body);
  font-weight: 500;
}

/* 「本机」标签要一眼看见 —— 它是用户敢不敢点下线的全部依据 */
.card__badge {
  padding: 1px 6px;
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
  font-size: var(--sv-font-footnote);
  font-weight: 500;
  line-height: 1.6;
}

/*
 * 🔴 用 secondary 而不是 placeholder。
 * 这两行装的是【真实信息】（登录地点、登录时间），不是占位提示 ——
 * placeholder 那一档在浅色下与白卡只有 2.41:1 的对比度，小字远低于 AA 的 4.5。
 * 2026-09-12 做深色时量出来的，浅色那边一直也不达标，只是没人量过。
 */
.card__meta {
  margin-top: 3px;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-sm);
  padding: var(--sv-space-xl) 0;
}

.state__error {
  margin: 0;
  color: var(--sv-color-danger);
  font-size: var(--sv-font-caption);
}

.state__empty {
  margin: 0;
  padding: var(--sv-space-xl) 0;
  text-align: center;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-caption);
}

.actions {
  margin-top: var(--sv-space-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-sm);
}

.actions__confirm {
  margin: 0;
  text-align: center;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.5;
}

.actions__row {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
}
</style>
