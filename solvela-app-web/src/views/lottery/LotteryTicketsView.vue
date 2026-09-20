<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { fetchLotteryIssue, fetchMyTickets, type LotteryIssue } from '@/api/lottery'
import { useAsync } from '@/composables/useAsync'

/**
 * 我的彩票。
 *
 * <h3>🔴 这一页存在的理由</h3>
 * 彩票玩法后端全建成了 —— FPE 算号、期号、号码池、中奖规则、开奖与核销 ——
 * 而会员侧一个入口都没有：拿不到号，也看不到自己有什么。这一页补的是后半句。
 *
 * <h3>没有「购买」按钮，这是后端设计</h3>
 * 号码只能从奖品派发拿到。要做「花积分买一张」是产品决策 ——
 * 画一个点了什么都不会发生的按钮，比不画更糟。
 *
 * <h3>中奖的排在最前，这个顺序由服务端定</h3>
 * 前端<b>不再排一次</b>：排序口径（奖级升序、同级最新在前）在 SQL 里，
 * 端上照抄一份就是第二份口径，改一边就会不一致。
 */

const tickets = useAsync(fetchMyTickets)

/**
 * 最近那张票所属玩法的「当前一期」。
 *
 * <h3>为什么只查一个玩法，而不是每个都查</h3>
 * 这块要回答的是「我现在还能不能再拿一张」。用户手里的票绝大多数属于同一个玩法，
 * 为了覆盖那少数几个而按玩法数发 N 个请求，是拿一次跨进程往返换一行文案 ——
 * 不划算。取<b>列表里第一条</b>所属的玩法（服务端已按中奖优先排过，
 * 那也正是用户此刻在看的那一个）。
 *
 * <p>拿不到就整块不画：这是锦上添花的信息，失败了不该影响主列表。
 */
const currentIssue = ref<LotteryIssue | null>(null)

watch(
  () => tickets.data.value,
  async (list) => {
    const first = (list ?? [])[0]
    if (first === undefined) {
      currentIssue.value = null
      return
    }
    try {
      currentIssue.value = await fetchLotteryIssue(first.lotteryCode)
    } catch {
      // 静默：主列表已经出来了，为一条提示语弹错误提示只会打扰人
      currentIssue.value = null
    }
  },
  { immediate: true },
)

/** 还在发号的那一期才提示。没有在售期（issueNo 为 null）时这块不画 */
const sellingNow = computed(
  () => currentIssue.value !== null && currentIssue.value.issueNo !== null,
)

/** 中奖的张数 —— 有的话顶上说一句，别让用户自己一条条找 */
const wonCount = computed(() => (tickets.data.value ?? []).filter((t) => t.winStatus === 2).length)

/**
 * 号码按位拆开显示。
 *
 * <p>一串 `48213` 挤在一起读起来费劲，而用户会拿它和开奖号逐位对 ——
 * 拆开之后「第几位对上了」一眼能看出来。
 */
function digits(ticket: string): string[] {
  return [...ticket]
}
</script>

<template>
  <div class="page">
    <NavBar title="我的彩票" />

    <div class="page__body">
      <p v-if="wonCount > 0" class="notice">
        恭喜，有 <b>{{ wonCount }}</b> 张中奖了
      </p>

      <!--
        「这一期还在发号」只是提示，不是入口：号码只能从奖品派发拿到，
        这里画一个「去领号」按钮会通向一个不存在的动作。
      -->
      <p v-else-if="sellingNow" class="notice notice--calm">
        {{ currentIssue?.lotteryName }} 第 {{ currentIssue?.issueNo }} 期发号中， 你已有
        {{ currentIssue?.myTicketCount }} 张
      </p>

      <Section
        title="全部号码"
        :loading="tickets.loading.value"
        :error="tickets.error.value"
        :empty="(tickets.data.value ?? []).length === 0"
        empty-text="还没有彩票号码，参与活动中奖就能拿到"
        @retry="tickets.reload"
      >
        <Card>
          <div
            v-for="t in tickets.data.value ?? []"
            :key="`${t.lotteryCode}-${t.issueNo}-${t.ticketNumber}`"
            class="ticket"
            :class="{ 'ticket--won': t.winStatus === 2 }"
          >
            <div class="ticket__head">
              <span class="ticket__name">{{ t.lotteryName }} · 第 {{ t.issueNo }} 期</span>
              <span class="ticket__status" :class="{ 'ticket__status--won': t.winStatus === 2 }">
                {{ t.statusText }}
              </span>
            </div>

            <!-- 号码按位拆开：用户要拿它和开奖号逐位对 -->
            <div class="nums">
              <span v-for="(d, i) in digits(t.ticketNumber)" :key="i" class="nums__d">{{ d }}</span>
            </div>

            <!-- 开奖了才有开奖号。没开奖就说什么时候开 —— 那是用户此刻唯一想知道的 -->
            <p v-if="t.winningNumber !== null" class="ticket__meta">
              开奖号码 <b>{{ t.winningNumber }}</b>
            </p>
            <p v-else-if="t.planDrawTime !== null" class="ticket__meta">
              预计 {{ t.planDrawTime }} 开奖
            </p>

            <p class="ticket__meta ticket__meta--dim">领取于 {{ t.obtainTime }}</p>
          </div>
        </Card>
      </Section>

      <!--
        说清楚号码从哪来。不说的话，看到空列表的用户第一反应是「在哪买」，
        而这个平台没有购买入口 —— 让他自己去找是更糟的体验。
      -->
      <p class="foot">彩票号码通过参与活动中奖获得，开奖后中奖奖品会自动发放到你的账户。</p>
    </div>
  </div>
</template>

<style scoped>
.page__body {
  padding: var(--sv-space-md) var(--sv-space-page);
}

.notice {
  margin: 0 0 var(--sv-space-md);
  padding: 10px 12px;
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
  color: var(--sv-color-primary);
  background: var(--sv-color-primary-soft);
  border-radius: var(--sv-radius-sm);
}

.notice--calm {
  color: var(--sv-text-secondary);
  background: var(--sv-bg-fill);
}

.ticket {
  padding: var(--sv-space-md);
}

.ticket + .ticket {
  border-top: 1px solid var(--sv-border-color);
}

.ticket__head {
  display: flex;
  align-items: baseline;
  gap: var(--sv-space-sm);
}

.ticket__name {
  flex: 1;
  min-width: 0;
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ticket__status {
  flex: none;
  font-size: var(--sv-font-footnote);
  color: var(--sv-text-placeholder);
}

.ticket__status--won {
  color: var(--sv-color-primary);
  font-weight: 600;
}

.nums {
  display: flex;
  gap: 6px;
  margin-top: var(--sv-space-sm);
}

.nums__d {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 36px;
  border-radius: var(--sv-radius-sm);
  background: var(--sv-bg-fill);
  color: var(--sv-text-primary);
  font-size: 17px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* 中奖那张要一眼看得出来，否则用户得逐条读状态文字 */
.ticket--won .nums__d {
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}

.ticket__meta {
  margin: var(--sv-space-sm) 0 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
  font-variant-numeric: tabular-nums;
}

.ticket__meta--dim {
  margin-top: var(--sv-space-xs);
  color: var(--sv-text-placeholder);
}

.foot {
  margin: var(--sv-space-md) 0 0;
  padding: 10px 12px;
  color: var(--sv-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  background: var(--sv-bg-fill);
  border-radius: var(--sv-radius-sm);
}
</style>
