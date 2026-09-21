<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchActivityDetail } from '@/api/activity'
import { ApiError } from '@/api/errors'
import { fetchLotteryBoard, obtainTicket } from '@/api/lottery'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'

/**
 * 彩票活动页。
 *
 * <h3>为什么不和 ActivityView 合成一页</h3>
 * 那一页是<b>抽奖专题页</b>：节日底 + 转盘 + 抽奖结果弹层，整段脚本都在驱动转盘。
 * 彩票的形状完全不同（期号、号码、开奖规则、往期结果），塞进同一个组件
 * 会得到一个一半 v-if 的文件，而两种玩法各自的改动都要先读懂另一种。
 *
 * <p>入口由列表按 `activityType` 分流；直接访问 `/activity/:code` 的分享链接
 * 由 ActivityView 判出类型后 replace 过来 —— 分享出去的老链接不能断。
 *
 * <h3>🔴 匿名可看，领号才要登录</h3>
 * 和抽奖活动页同一条：活动页是分享出去的入口，要求先登录才能看一眼
 * 等于把分享链路掐断。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const rawCode = route.params.code
const activityCode = (Array.isArray(rawCode) ? rawCode[0] : rawCode) ?? ''

/*
 * 两个请求：活动本身（名称/规则正文/时间窗）和彩票面板。
 *
 * 🔴 彩票那一块【已经在服务端聚合过】（期号+规则+往期+我的号码一次给完），
 *    所以这里是 2 个请求而不是 5 个。别看到「还能再合一个」就去合 ——
 *    活动是活动域的东西，彩票是玩法的东西，合并意味着活动接口要认识彩票。
 */
const detail = useAsync(() => fetchActivityDetail(activityCode))
const board = useAsync(() => fetchLotteryBoard(activityCode))

/* ---- 领号 ---- */

const obtaining = ref(false)
/** 刚领到的那个号码，用来做一次「亮出来」的展示。null = 没有 */
const justGot = ref<string | null>(null)
const hint = ref('')

/** 本期还在发号才画按钮。没有在售期时整块不画 —— 那是正常的运营空窗 */
const sellable = computed(() => {
  const issueNo = board.data.value?.issue?.issueNo
  return issueNo !== null && issueNo !== undefined
})

/**
 * 领一个号。
 *
 * <h3>🔴 被拒不是错误</h3>
 * 限购满了、售罄、停售 —— 后端返回 200 + `accepted: false` + 一句人话。
 * 丢进 catch 显示「领取失败」会把一个完全正常的结果说成故障。
 *
 * <h3>成功之后重拉整块，不本地 +1</h3>
 * 「我这期有几张」和号码列表都在服务端，本地猜一个只会和服务端不一致 ——
 * 而用户会按那个去判断自己还能不能再领。
 */
async function onObtain(): Promise<void> {
  if (obtaining.value) {
    return
  }
  if (!auth.isLoggedIn) {
    // 和抽奖页一样：看可以匿名，动手要登录。带上回跳，登录完回到这一页
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  obtaining.value = true
  hint.value = ''
  justGot.value = null
  try {
    const result = await obtainTicket(activityCode)
    // 文案一律用服务端给的：成功「号码已到账」，被拒是限购/售罄的原话
    hint.value = result.message
    if (result.accepted) {
      justGot.value = result.ticketNumber
      await board.reload()
    }
  } catch (error) {
    hint.value = error instanceof ApiError ? error.message : '领取失败，请稍后再试'
  } finally {
    obtaining.value = false
  }
}

/** 号码按位拆开：用户要拿它和开奖号逐位对 */
function digits(ticket: string): string[] {
  return [...ticket]
}

/**
 * 还没号码时画几个占位格。
 *
 * <p>按服务端给的 `numberLength` 画，<b>不从号码字符串长度去猜</b> ——
 * 一张号码都没有的时候也得画得出来，而那正是新用户第一次看到这一页的样子。
 */
const placeholders = computed(() => {
  const length = board.data.value?.numberLength ?? 0
  return length > 0 ? Array.from({ length }, () => '?') : []
})
</script>

<template>
  <div class="page">
    <NavBar :title="detail.data.value?.activityName ?? '彩票'" />

    <div class="page__body">
      <!--
        标题就用玩法名（还没拿到时先空着）—— Section 的 title 是必填的，
        而这一块本来就该有个头。拿「本期」当标题会和下面卡片里的玩法名打架。
      -->
      <!--
        ⚠️ 空态判据是 === null，这要求传输层把「空响应体」归一成 null ——
        axios 拿到的是空字符串，见 api/http.ts 里 request 的注释。
        2026-09-21 彩票活动页白屏就是这条判据没成立：走进了「有数据」分支，
        对着空字符串读属性，在 render 里抛 TypeError。
      -->
      <Section
        :title="board.data.value?.lotteryName ?? ''"
        :loading="board.loading.value"
        :error="board.error.value"
        :empty="board.data.value === null"
        empty-text="这个活动暂时没有可参与的彩票玩法"
        @retry="board.reload"
      >
        <template v-if="board.data.value !== null">
          <!-- ① 本期 -->
          <Card>
            <div class="issue">
              <!--
                🔴 判据写成两段显式判空，不用 `?.issueNo != null`：
                   后者 TS narrow 不进去，下面每一行读 issue 都会报「可能为 null」。
                   `sellable` 那个 computed 同理 —— 它只用来控按钮。
              -->
              <template
                v-if="board.data.value.issue !== null && board.data.value.issue.issueNo !== null"
              >
                <p class="issue__no">第 {{ board.data.value.issue.issueNo }} 期</p>
                <p class="issue__time">{{ board.data.value.issue.saleEndTime }} 截止发号</p>
                <p v-if="board.data.value.issue.planDrawTime !== null" class="issue__time">
                  预计 {{ board.data.value.issue.planDrawTime }} 开奖
                </p>
              </template>
              <!-- 空窗是正常的运营节奏，不是错误，所以不用错误样式 -->
              <p v-else class="issue__time">本期已结束，下一期敬请期待</p>

              <!-- 刚领到的那个号亮出来；没领过就画占位格，让人看出号码长什么样 -->
              <div class="nums">
                <span
                  v-for="(d, i) in justGot !== null ? digits(justGot) : placeholders"
                  :key="i"
                  class="nums__d"
                  :class="{ 'nums__d--got': justGot !== null, 'nums__d--empty': justGot === null }"
                >
                  {{ d }}
                </span>
              </div>

              <p v-if="hint !== ''" class="issue__hint" role="alert">{{ hint }}</p>

              <Button v-if="sellable" :loading="obtaining" @click="onObtain">领取号码</Button>

              <p v-if="board.data.value.issue !== null" class="issue__mine">
                我这期已有 {{ board.data.value.issue.myTicketCount }} 张
              </p>
            </div>
          </Card>

          <!-- ② 我这期的号码。全部号码在「我的彩票」那一页，这里只给当前期 -->
          <div v-if="board.data.value.myTickets.length > 0" class="block">
            <h2 class="block__title">我的号码</h2>
            <Card>
              <div v-for="t in board.data.value.myTickets" :key="t.ticketNumber" class="mine">
                <div class="nums nums--sm">
                  <span v-for="(d, i) in digits(t.ticketNumber)" :key="i" class="nums__d">
                    {{ d }}
                  </span>
                </div>
                <span class="mine__status">{{ t.statusText }}</span>
              </div>
            </Card>
          </div>

          <!-- ③ 中奖规则。文案由服务端拼 —— 端上做映射表就是第二份规则口径 -->
          <div v-if="board.data.value.rules.length > 0" class="block">
            <h2 class="block__title">中奖规则</h2>
            <Card>
              <div v-for="r in board.data.value.rules" :key="r.prizeLevel ?? 0" class="rule">
                <span class="rule__level">{{ r.prizeLevel }} 等奖</span>
                <span class="rule__text">{{ r.ruleText }}</span>
                <!-- 查不到奖品配置时不画这一行，而不是显示一个编码 -->
                <span v-if="r.prizeName !== null" class="rule__prize">{{ r.prizeName }}</span>
              </div>
            </Card>
          </div>

          <!-- ④ 往期开奖 -->
          <div v-if="board.data.value.recentIssues.length > 0" class="block">
            <h2 class="block__title">往期开奖</h2>
            <Card>
              <div v-for="p in board.data.value.recentIssues" :key="p.issueNo" class="past">
                <span class="past__no">第 {{ p.issueNo }} 期</span>
                <span class="past__num">{{ p.winningNumber }}</span>
                <span v-if="p.settleTime !== null" class="past__time">{{ p.settleTime }}</span>
              </div>
            </Card>
          </div>
        </template>
      </Section>

      <!-- 活动规则正文，放最后：想看的人会往下翻 -->
      <div v-if="detail.data.value?.ruleContent" class="block">
        <h2 class="block__title">活动规则</h2>
        <Card>
          <!--
            规则正文是运营在后台配的富文本 HTML，来自我们自己的后端
            （见 ActivityRuleView 的注释），按可信内容渲染。
            这里刻意不引 sanitize 库 —— 内容来源与「运营配的活动名」同级，
            口径与 ActivityView 那一处完全一致，别只改一边。
          -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div class="rich" v-html="detail.data.value.ruleContent"></div>
        </Card>
      </div>
    </div>
  </div>
</template>

<style scoped>
/*
  内层这三块不是独立的取数，所以不该用 ui/Section —— 那个组件的职责是
  「一次异步取数的四个状态」（加载/有数据/空/出错），它的 loading/error/empty
  三个必填 prop 就是这个职责的形状。当成「带标题的容器」用会把职责冲淡，
  下一个人会以为它只是个标题栏。这里要的就只是一个标题。
*/
.block {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
}

.block__title {
  margin: 0 0 0 var(--sv-space-xs);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  font-weight: 600;
}

.page__body {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md) var(--sv-space-page);
}

.issue {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sv-space-xs);
  padding: var(--sv-space-lg) var(--sv-space-md);
  text-align: center;
}

.issue__no {
  margin: 0;
  color: var(--sv-color-primary);
  font-size: var(--sv-font-caption);
  font-weight: 600;
}

.issue__time {
  margin: 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
}

.issue__hint {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-color-primary);
  font-size: var(--sv-font-footnote);
}

.issue__mine {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}

.nums {
  display: flex;
  gap: 6px;
  margin: var(--sv-space-md) 0;
}

.nums--sm {
  margin: 0;
}

.nums__d {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 42px;
  border-radius: var(--sv-radius-sm);
  background: var(--sv-bg-fill);
  color: var(--sv-text-primary);
  font-size: 19px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.nums--sm .nums__d {
  width: 26px;
  height: 32px;
  font-size: 15px;
}

.nums__d--got {
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}

/* 占位格要看得出是空的，不能让人以为那是号码 */
.nums__d--empty {
  color: var(--sv-text-placeholder);
}

.mine {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
  padding: var(--sv-space-sm) var(--sv-space-md);
}

.mine + .mine {
  border-top: 1px solid var(--sv-border-color);
}

.mine__status {
  margin-left: auto;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}

.rule,
.past {
  display: flex;
  align-items: baseline;
  gap: var(--sv-space-sm);
  padding: var(--sv-space-sm) var(--sv-space-md);
  font-size: var(--sv-font-footnote);
}

.rule + .rule,
.past + .past {
  border-top: 1px solid var(--sv-border-color);
}

.rule__level {
  flex: none;
  width: 56px;
  color: var(--sv-color-primary);
  font-weight: 600;
}

.rule__text {
  flex: 1;
  min-width: 0;
  color: var(--sv-text-secondary);
}

.rule__prize {
  flex: none;
  color: var(--sv-text-primary);
}

.past__no {
  flex: none;
  width: 90px;
  color: var(--sv-text-secondary);
}

.past__num {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: 2px;
}

.past__time {
  flex: none;
  color: var(--sv-text-placeholder);
  font-size: 12px;
}

.rich {
  padding: var(--sv-space-md);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
  line-height: 1.7;
}

.rich :deep(img) {
  max-width: 100%;
}
</style>
