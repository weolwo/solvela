<script setup lang="ts">
import { computed, ref } from 'vue'

import GradeCardDeck from './GradeCardDeck.vue'

import { claimEntitlement, fetchMyEntitlements, type MemberEntitlement } from '@/api/entitlement'
import { ApiError } from '@/api/errors'
import { fetchGrowthLog, fetchMyGrade } from '@/api/grade'
import { useAsync } from '@/composables/useAsync'

/**
 * 会员中心。
 *
 * <h3>🔴 这一页最重要的不是「我是白金」，是「我快掉了」</h3>
 * 保级缓冲期在此之前对用户完全不可见：等级到期了、系统给了三个月宽限、
 * 这三个月成长值还是双倍的 —— 而他什么都不知道。
 * 一个用户感知不到的挽留机制等于没做，而这套等级体系要换的就是那个挽留。
 *
 * <p>所以缓冲期横幅放在最顶上，在卡片之前，并且写清三件事：
 * <b>保的是哪一档、还差多少、什么时候截止</b>。只说「你在保级期」等于没说。
 *
 * <h3>🔴 卡片式改版带进来一个新风险：用户会以为看到的都是自己的</h3>
 * 滑到钻石卡，下面整块权益就换成钻石的 —— 如果不说，那看起来就像
 * 「这些我都有」。一个让用户误以为自己有某项权益的页面，
 * 比一个朴素的列表差得多：他会去用，然后发现用不了。
 *
 * <p>所以权益区的标题<b>始终带着档名</b>（「钻石会员的权益」而不是「我的权益」），
 * 没解锁的那一档还要再说一次还差多少。看自己那一档时才说「你现在享有」。
 *
 * <h3>「在这一档」和「够得着这一档」仍然要分开</h3>
 * 缓冲期内用户<b>在</b>白金，但成长值<b>够不着</b>白金。卡片上是两个独立的标
 * （当前 / 未解锁），可以同时出现 —— 那正是他要被提醒的状态。
 *
 * <h3>明细一起拉，不做懒加载</h3>
 * `useAsync` 是「创建即加载」的。为了省一次 `LIMIT 20` 的主键范围查询而在页面里
 * 手搓一份异步状态，正是它那段注释要避免的事。而且「我这个数怎么来的」
 * 本来就是这一页的半个主题。
 */

const grade = useAsync(fetchMyGrade)
const logs = useAsync(() => fetchGrowthLog(20))
const entitlements = useAsync(fetchMyEntitlements)

/* ---- 领取 ---- */

/** 正在领哪一条。用 id 而不是布尔：一次只能点一个，但要知道是哪个在转圈 */
const claiming = ref<number | null>(null)
const claimHint = ref('')

/**
 * 领取一份。
 *
 * 🔴 成功之后**重新拉整块**，不本地改状态：
 * 「领没领到」的真相在服务端（发放那一步可能失败并写了原因），
 * 本地猜一个只会和服务端不一致，而用户会按那个去判断自己到底拿没拿到。
 */
async function onClaim(item: MemberEntitlement): Promise<void> {
  if (claiming.value !== null) {
    return
  }
  claiming.value = item.grantId
  claimHint.value = ''
  try {
    const got = await claimEntitlement(item.grantId)
    claimHint.value = `已领取：${got.assetName}`
    await entitlements.reload()
  } catch (error) {
    // 文案一律用服务端给的：「已经领过了」「已过期」都是它算出来的
    claimHint.value = error instanceof ApiError ? error.message : '领取失败，请稍后再试'
  } finally {
    claiming.value = null
  }
}

/** 状态文案。已过期的也要说清楚，否则用户只看到一条灰记录不知道发生了什么 */
function statusText(item: MemberEntitlement): string {
  if (item.status === 1) {
    return item.claimTime === null ? '已领取' : `已领取 · ${item.claimTime.slice(0, 10)}`
  }
  if (item.status === 2) {
    return '已过期'
  }
  return `${item.expireTime.slice(0, 10)} 前领取`
}

/** 用户自己那一档在阶梯里的下标。卡片组首次就停在这里 */
const currentIndex = computed(() => {
  const ladder = grade.data.value?.ladder ?? []
  const hit = ladder.findIndex((i) => i.current)
  return hit < 0 ? 0 : hit
})

/*
 * 正在看第几张。null = 用户还没滑过，跟着 currentIndex 走。
 *
 * ⚠️ 不要初始化成 0：数据回来之前就渲染出「普通会员的权益」，
 * 卡片定位完再闪一下换成用户自己那一档 —— 那一闪看起来像是降级了。
 */
const viewIndex = ref<number | null>(null)
const shownIndex = computed(() => viewIndex.value ?? currentIndex.value)

const shown = computed(() => grade.data.value?.ladder[shownIndex.value] ?? null)

/**
 * 正在看的这一档<b>是不是他的</b>。
 *
 * 🔴 不能只看 `reached`。保级缓冲期里他【在】白金但成长值【够不着】白金 ——
 * 此刻这些权益他是<b>真的在享有</b>的，压暗成「未解锁」就是骗他，
 * 而页面同一行还写着「这是你现在享有的权益」，自己跟自己打架。
 */
const shownIsMine = computed(() => {
  const item = shown.value
  return item !== null && (item.current || item.reached)
})

/** 正在看的这一档还差多少解锁。已解锁的是 0 */
const shownGap = computed(() => {
  const g = grade.data.value
  const item = shown.value
  if (g === null || item === null || item.reached) {
    return 0
  }
  return Math.max(0, item.threshold - g.currentValue)
})

/**
 * 进度条：<b>已达成的最高一档</b> → 下一档 之间走到哪了。
 *
 * 🔴 起点取 `reached` 的最高档，不能取 `gradeCode` 那一档的门槛。
 * 缓冲期内两者是分开的：他挂着白金（门槛 20000），成长值却只有 100 ——
 * 拿 20000 当起点，`span` 会算成负数，进度条直接满格，
 * 而页面上同时写着「距银卡还差 900」。自相矛盾，还偏偏是最该说清楚的那一屏。
 */
const progress = computed(() => {
  const g = grade.data.value
  if (g === null || g.nextThreshold === null) {
    return 100
  }
  const base = g.ladder.filter((i) => i.reached).reduce((max, i) => Math.max(max, i.threshold), 0)
  const span = g.nextThreshold - base
  if (span <= 0) {
    return 100
  }
  return Math.min(100, Math.max(0, Math.round(((g.currentValue - base) / span) * 100)))
})
</script>

<template>
  <div class="page">
    <NavBar title="会员中心" />

    <div class="page__body">
      <Section
        class="hero"
        title=""
        :loading="grade.loading.value"
        :error="grade.error.value"
        :empty="false"
        empty-text=""
        @retry="grade.reload"
      >
        <template v-if="grade.data.value !== null">
          <!--
            🔴 缓冲期横幅在卡片之前。
            三件事缺一不可 —— 保的是哪一档、还差多少、什么时候截止。
          -->
          <p v-if="grade.data.value.inProtect" class="protect">
            <b>{{ grade.data.value.gradeName }}</b> 正在保级中 ——
            <template v-if="(grade.data.value.protectGap ?? 0) > 0">
              还差 <b>{{ grade.data.value.protectGap }}</b> 成长值
            </template>
            <template v-else>已达标，等结算就保住了</template>
            ，截止 {{ grade.data.value.protectUntil }}。 这段时间成长值
            <b>×{{ grade.data.value.boostMultiplier }}</b>
          </p>

          <!--
            🔴 卡片组要破出页面内边距，贴到屏幕两边 —— 那是这类会员卡的观感来源，
            左右邻居被屏幕边裁掉，而不是被一条看不见的内边距裁掉。
            负边距写在这里而不是组件里：组件不该知道调用它的页面留了多少白。
          -->
          <div class="deck-bleed">
            <GradeCardDeck
              :items="grade.data.value.ladder"
              :current-value="grade.data.value.currentValue"
              :initial-index="currentIndex"
              @update:index="viewIndex = $event"
            />
          </div>

          <!--
            进度条讲的始终是【用户自己】的进度，所以它在卡片外面，不随滑动变。
            放进卡片里的话，滑到钻石卡时那条进度条会被读成「我离钻石还有这么近」。
          -->
          <Card>
            <div class="mine">
              <p class="mine__value">
                本期成长值 <b>{{ grade.data.value.currentValue }}</b>
                <span class="mine__total">· 累计 {{ grade.data.value.totalValue }}</span>
              </p>

              <div class="bar" role="presentation">
                <div class="bar__fill" :style="{ width: progress + '%' }"></div>
              </div>

              <p v-if="grade.data.value.gapToNext !== null" class="mine__next">
                距 {{ grade.data.value.nextGradeName }} 还差 <b>{{ grade.data.value.gapToNext }}</b>
              </p>
              <p v-else class="mine__next">已是最高等级</p>

              <!-- 没参与过的人没有周期，不要显示一个空的「截止」 -->
              <p v-if="grade.data.value.periodEnd !== null" class="mine__period">
                本期截止 {{ grade.data.value.periodEnd }}
              </p>
              <p v-else class="mine__period">完成第一个任务就开始计算你的会员年度</p>
            </div>
          </Card>
        </template>
      </Section>

      <!--
        🔴 「我的权益」放在卡片之后、阶梯权益之前。
        上面那块是「这一档有什么」（展示），这一块是「我现在有什么能领」（真东西）——
        两者挨着，用户才看得出保级换来的是什么。
      -->
      <Section
        v-if="(entitlements.data.value ?? []).length > 0"
        title="我的权益"
        :loading="entitlements.loading.value"
        :error="entitlements.error.value"
        :empty="false"
        empty-text=""
        @retry="entitlements.reload"
      >
        <Card>
          <p v-if="claimHint !== ''" class="claim-hint" role="status">{{ claimHint }}</p>
          <div
            v-for="item in entitlements.data.value ?? []"
            :key="item.grantId"
            class="ent"
            :class="{ 'ent--done': item.status !== 0 }"
          >
            <div class="ent__main">
              <!--
                权益名与资产名分开说：前者是「为什么给你」，后者是「给你什么」。
                合并成一个的话只能二选一，而用户两件都想知道。
              -->
              <p class="ent__name">{{ item.entitlementName }}</p>
              <p class="ent__asset">{{ item.assetName }}</p>
              <p class="ent__meta">{{ statusText(item) }}</p>
            </div>
            <!--
              🔴 :block="false" 不能省。Button 默认 block（width:100%），
              放在 flex 行里会把同排的文字列挤成 0 宽，那一列就变成「每行一个字」竖着排 ——
              组件注释里已经记过这个坑（2026-09-10 登录设备页），我还是踩了一次。
            -->
            <Button
              v-if="item.status === 0"
              :block="false"
              :loading="claiming === item.grantId"
              :disabled="claiming !== null && claiming !== item.grantId"
              @click="onClaim(item)"
            >
              领取
            </Button>
          </div>
        </Card>
      </Section>

      <!--
        🔴 标题始终带档名。「我的权益」在滑到别档时就是一句假话，
        而用户会照着它去用一个自己还没有的权益。
      -->
      <Section
        v-if="shown !== null"
        :title="`${shown.gradeName}的权益`"
        :loading="false"
        :error="null"
        :empty="shown.privileges.length === 0"
        :empty-text="`${shown.gradeName}这一档还没有配置权益`"
      >
        <Card>
          <!--
            ⚠️ 状态和数字分成两件东西：胶囊只放一个短状态，数字另起一行小字。
            把整句话塞进胶囊会折成两行，看起来像一块报错提示 —— 第一版就是这样。
          -->
          <div class="own">
            <span class="own__tag" :class="{ 'own__tag--locked': !shownIsMine }">
              <template v-if="shown.current">你现在享有</template>
              <template v-else-if="shown.reached">已达成</template>
              <template v-else>未解锁</template>
            </span>
            <!--
              🔴 当前档即使成长值不够，这些权益他也确实在享有（缓冲期就是干这个的），
              但「暂未达标」必须说出来 —— 只说「你现在享有」会让他以为一切正常，
              而他其实正要掉下去。
            -->
            <span v-if="shown.current && !shown.reached" class="own__note">
              成长值暂未达标，还差 {{ shownGap }}
            </span>
            <span v-else-if="!shownIsMine" class="own__note">还差 {{ shownGap }} 成长值</span>
          </div>

          <div
            v-for="p in shown.privileges"
            :key="p.privilegeCode"
            class="priv"
            :class="{ 'priv--locked': !shownIsMine }"
          >
            <p class="priv__name">{{ p.privilegeName }}</p>
            <p v-if="p.description !== null" class="priv__desc">{{ p.description }}</p>
          </div>
        </Card>
      </Section>

      <Section
        title="成长值明细"
        :loading="logs.loading.value"
        :error="logs.error.value"
        :empty="(logs.data.value ?? []).length === 0"
        empty-text="还没有成长值记录，做个任务就有了"
        @retry="logs.reload"
      >
        <Card>
          <div v-for="(row, i) in logs.data.value ?? []" :key="i" class="log">
            <div class="log__main">
              <span>{{ row.remark ?? '成长值入账' }}</span>
              <span class="log__delta">+{{ row.delta }}</span>
            </div>
            <p class="log__meta">
              {{ row.createTime }}
              <!--
                🔴 倍率 ≠ 1 时必须把算式摆出来。缓冲期看到「+200」而自己只做了一件
                值 100 的事，用户第一反应是系统算错了 —— 而那恰恰是加速生效的时刻。
              -->
              <span v-if="row.multiplier !== 1" class="log__boost">
                {{ row.baseValue }} × {{ row.multiplier }} 倍加速
              </span>
            </p>
          </div>
        </Card>
      </Section>
    </div>
  </div>
</template>

<style scoped>
/*
  🔴 左右内边距。改版前这一页就漏了它 —— 所有文字一直贴着屏幕边，
  而另外十几个页面都留了白。卡片挪到最上面之后这条缝更显眼，顺手补上。

  ⚠️ 段与段之间也要有间距：Section 只管自己内部的 gap，
  兄弟 Section 之间是 0 —— 不给的话标题会紧贴上一块的卡片底边。
*/
.page__body {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-lg);
  padding: var(--sv-space-md) var(--sv-space-page) calc(var(--sv-safe-bottom) + var(--sv-space-lg));
}

/*
  顶部这一块借 Section 拿的是它的「加载 / 出错 / 重试」四态，不是它的标题 ——
  会员卡上面再压一行小标题，跟 NavBar 的「会员中心」重复。
  所以标题传空串并在这里把那一行藏掉；藏的是 head，不是整个组件。
*/
.hero {
  gap: var(--sv-space-md);
}

.hero :deep(.sv-section__head) {
  display: none;
}

.deck-bleed {
  margin-inline: calc(var(--sv-space-page) * -1);
}

.protect {
  margin: 0;
  padding: var(--sv-space-md);
  border-radius: var(--sv-radius-md);
  background: var(--sv-color-warning-soft);
  color: var(--sv-color-warning);
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
}

/*
  ⚠️ Card 自己【没有】内边距（见 ui/Card.vue：只有圆角、底色、overflow），
  内容得自己给。漏了这一层的后果是每一行都贴着卡片边 —— 第一版就是这样，
  真机上一眼就看出来不对。
*/
.mine {
  padding: var(--sv-space-md);
}

.mine__value {
  margin: 0 0 var(--sv-space-md);
  font-size: var(--sv-font-footnote);
}

.mine__total {
  margin-left: 6px;
  color: var(--sv-text-secondary);
}

.bar {
  height: 8px;
  border-radius: 999px;
  background: var(--sv-bg-fill);
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  border-radius: 999px;
  background: var(--sv-color-primary);
  transition: width 0.3s ease;
}

.mine__next {
  margin: var(--sv-space-md) 0 0;
  font-size: var(--sv-font-footnote);
}

.mine__period {
  margin: var(--sv-space-xs) 0 0;
  font-size: var(--sv-font-caption);
  color: var(--sv-text-secondary);
}

/*
  ⚠️ 这一行是状态说明，不是报错。
  第一版拿品牌色直接染整行字，而品牌色是红的 —— 一行红字顶在权益列表上面，
  看起来像是加载失败。做成浅底小胶囊，它才读得出是「标签」而不是「警告」。
*/
.claim-hint {
  margin: 0;
  padding: var(--sv-space-md) var(--sv-space-md) 0;
  font-size: var(--sv-font-caption);
  color: var(--sv-color-primary);
}

.ent {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md);
}

.ent + .ent {
  border-top: 1px solid var(--sv-border-color);
}

/* 已领取/已过期压暗，但不藏 —— 藏了用户会以为「刚才那个东西没了」 */
.ent--done {
  opacity: 0.55;
}

.ent__main {
  flex: 1;
  min-width: 0;
}

.ent__name {
  margin: 0;
  font-weight: 600;
}

.ent__asset {
  margin: 2px 0 0;
  font-size: var(--sv-font-footnote);
  color: var(--sv-color-primary);
}

.ent__meta {
  margin: 2px 0 0;
  font-size: var(--sv-font-caption);
  color: var(--sv-text-secondary);
}

.own {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sv-space-sm);
  padding: var(--sv-space-md) var(--sv-space-md) 0;
}

/*
  ⚠️ 这是状态标签，不是报错。
  第一版拿品牌色染了整行字，而品牌色是红的 —— 一行红字顶在权益列表上面，
  看起来像加载失败。做成浅底小胶囊，才读得出是「标签」。
*/
.own__tag {
  padding: 3px 10px;
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
  font-size: var(--sv-font-caption);
  font-weight: 600;
  white-space: nowrap;
}

.own__tag--locked {
  background: var(--sv-bg-fill);
  color: var(--sv-text-secondary);
}

.own__note {
  font-size: var(--sv-font-caption);
  color: var(--sv-text-secondary);
}

.priv {
  padding: var(--sv-space-md);
}

.priv + .priv {
  border-top: 1px solid var(--sv-border-color);
}

/* 未解锁那一档整体压暗：看得见，但一眼看出不是自己的 */
.priv--locked {
  opacity: 0.55;
}

.priv__name {
  margin: 0;
  font-weight: 600;
}

.priv__desc {
  margin: var(--sv-space-xs) 0 0;
  font-size: var(--sv-font-caption);
  color: var(--sv-text-secondary);
}

.log {
  padding: var(--sv-space-md);
}

.log + .log {
  border-top: 1px solid var(--sv-border-color);
}

.log__main {
  display: flex;
  justify-content: space-between;
  gap: var(--sv-space-sm);
}

.log__delta {
  font-weight: 600;
  color: var(--sv-color-success);
  white-space: nowrap;
}

.log__meta {
  margin: var(--sv-space-xs) 0 0;
  font-size: var(--sv-font-caption);
  color: var(--sv-text-secondary);
}

.log__boost {
  margin-left: 8px;
  color: var(--sv-color-warning);
}
</style>
