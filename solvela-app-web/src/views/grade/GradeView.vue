<script setup lang="ts">
import { computed } from 'vue'

import { fetchGrowthLog, fetchMyGrade, type GradeLadderItem } from '@/api/grade'
import { useAsync } from '@/composables/useAsync'

/**
 * 我的会员等级。
 *
 * <h3>🔴 这一页最重要的不是「我是白金」，是「我快掉了」</h3>
 * 保级缓冲期在此之前对用户完全不可见：等级到期了、系统给了三个月宽限、
 * 这三个月成长值还是双倍的 —— 而他什么都不知道。
 * 一个用户感知不到的挽留机制等于没做，而这套等级体系要换的就是那个挽留。
 *
 * <p>所以缓冲期横幅放在最顶上，并且写清三件事：
 * <b>保的是哪一档、还差多少、什么时候截止</b>。只说「你在保级期」等于没说，
 * 用户不知道要做什么。
 *
 * <h3>「在这一档」和「够得着这一档」要分开渲染</h3>
 * 缓冲期内用户<b>在</b>白金，但成长值<b>够不着</b>白金 —— 这正是他要被提醒的状态。
 * 把 `current` 和 `reached` 混成一个高亮，那句话就说不出来了。
 *
 * <h3>明细一起拉，不做懒加载</h3>
 * `useAsync` 是「创建即加载」的（它的注释写着：三个 ref 加 try-catch 写到第三遍
 * 就会开始不一致）。为了省一次 `LIMIT 20` 的主键范围查询而在页面里手搓一份
 * 异步状态，正是那段注释要避免的事。而且「我这个数怎么来的」本来就是这一页的
 * 半个主题，藏在按钮后面反而更差。
 */

const grade = useAsync(fetchMyGrade)
const logs = useAsync(() => fetchGrowthLog(20))

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

function ladderClass(item: GradeLadderItem) {
  return {
    'ladder__item--current': item.current,
    'ladder__item--reached': item.reached && !item.current,
  }
}
</script>

<template>
  <div class="page">
    <NavBar title="我的等级" />

    <div class="page__body">
      <Section
        title="当前等级"
        :loading="grade.loading.value"
        :error="grade.error.value"
        :empty="false"
        empty-text=""
        @retry="grade.reload"
      >
        <template v-if="grade.data.value !== null">
          <!--
            🔴 缓冲期横幅：这一页真正的主角。
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

          <Card>
            <p class="hero__name">{{ grade.data.value.gradeName }}</p>
            <p class="hero__value">
              本期成长值 <b>{{ grade.data.value.currentValue }}</b>
              <span class="hero__total">· 累计 {{ grade.data.value.totalValue }}</span>
            </p>

            <div class="bar" role="presentation">
              <div class="bar__fill" :style="{ width: progress + '%' }"></div>
            </div>

            <p v-if="grade.data.value.gapToNext !== null" class="hero__next">
              距 {{ grade.data.value.nextGradeName }} 还差 <b>{{ grade.data.value.gapToNext }}</b>
            </p>
            <p v-else class="hero__next">已是最高等级</p>

            <!-- 没参与过的人没有周期，不要显示一个空的「截止」 -->
            <p v-if="grade.data.value.periodEnd !== null" class="hero__period">
              本期截止 {{ grade.data.value.periodEnd }}
            </p>
            <p v-else class="hero__period">完成第一个任务就开始计算你的会员年度</p>
          </Card>
        </template>
      </Section>

      <!-- 阶梯：看得见够不着，才有往上够的理由 -->
      <Section
        title="等级与权益"
        :loading="grade.loading.value"
        :error="null"
        :empty="(grade.data.value?.ladder ?? []).length === 0"
        empty-text="还没有配置等级"
        @retry="grade.reload"
      >
        <Card>
          <div
            v-for="item in grade.data.value?.ladder ?? []"
            :key="item.gradeCode"
            class="ladder__item"
            :class="ladderClass(item)"
          >
            <div class="ladder__head">
              <span class="ladder__name">{{ item.gradeName }}</span>
              <span class="ladder__mark">
                <template v-if="item.current">当前</template>
                <template v-else-if="item.reached">已达成</template>
                <template v-else>{{ item.threshold }} 成长值</template>
              </span>
            </div>
            <ul v-if="item.privileges.length > 0" class="ladder__privileges">
              <li v-for="p in item.privileges" :key="p.privilegeCode">
                {{ p.privilegeName }}
                <span v-if="p.description !== null" class="ladder__desc">{{ p.description }}</span>
              </li>
            </ul>
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
.protect {
  margin: 0 0 12px;
  padding: 12px;
  border-radius: 8px;
  background: var(--sv-color-warning-soft);
  color: var(--sv-color-warning);
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
}

.hero__name {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.hero__value {
  margin: 4px 0 12px;
  font-size: var(--sv-font-footnote);
}

.hero__total {
  margin-left: 6px;
  opacity: 0.6;
}

.bar {
  height: 6px;
  border-radius: 3px;
  background: var(--sv-bg-fill);
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  background: var(--sv-color-primary);
}

.hero__next {
  margin: 10px 0 0;
  font-size: var(--sv-font-footnote);
}

.hero__period {
  margin: 4px 0 0;
  font-size: var(--sv-font-caption);
  opacity: 0.6;
}

.ladder__item {
  padding: 12px 0;
  border-bottom: 1px solid var(--sv-border-color);
}

.ladder__item:last-child {
  border-bottom: none;
}

.ladder__item--reached .ladder__name {
  color: var(--sv-color-primary);
}

.ladder__item--current {
  background: var(--sv-color-primary-soft);
  margin: 0 -12px;
  padding: 12px;
  border-radius: 8px;
}

.ladder__head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.ladder__name {
  font-weight: 600;
}

.ladder__mark {
  font-size: var(--sv-font-caption);
  opacity: 0.7;
}

.ladder__privileges {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: var(--sv-font-caption);
  opacity: 0.8;
}

.ladder__desc {
  margin-left: 6px;
  opacity: 0.7;
}

.log {
  padding: 10px 0;
  border-bottom: 1px solid var(--sv-border-color);
}

.log:last-child {
  border-bottom: none;
}

.log__main {
  display: flex;
  justify-content: space-between;
}

.log__delta {
  font-weight: 600;
  color: var(--sv-color-success);
}

.log__meta {
  margin: 2px 0 0;
  font-size: var(--sv-font-caption);
  opacity: 0.6;
}

.log__boost {
  margin-left: 8px;
  color: var(--sv-color-warning);
}
</style>
