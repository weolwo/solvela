<script setup lang="ts">
import { computed } from 'vue'

import { fetchTasks, type TaskItem, type TaskStage } from '@/api/task'
import { useAsync } from '@/composables/useAsync'
import { compare, format, money } from '@/utils/money'

/**
 * 任务中心。首页四个 tab 里的第三个。
 *
 * <h3>🔴 这一页<b>不提交任何东西</b></h3>
 * 后端的任务状态是 RUNNING / COMPLETED / <b>DISPATCHED</b> / EXPIRED，没有 CLAIMED：
 * 任务达标时自动发奖，用户不需要（也不能）点一下领。
 *
 * <p>第一版这里有个「领取」按钮和 `claimTask()`，是照着我想当然的模型写的 ——
 * 接通后端才发现和设计对不上。<b>画一个点了什么都不会发生的按钮比不画更糟</b>：
 * 奖励其实早就到账了，用户反而会以为自己没领到。
 *
 * <h3>状态文案由后端给</h3>
 * 前端做「状态码 → 中文」的映射表就是第二份状态机，域里加一个状态时它会静默变错。
 * 这里只用 `finished` 选个样式。
 */

const tasks = useAsync(fetchTasks)

/** 进度条只对多次任务有意义：目标为 1 的任务画一根 0% 或 100% 的条纯属噪音 */
function showsProgress(task: TaskItem): boolean {
  return compare(money(task.target), money('1')) > 0 && !task.finished
}

function progressPercent(task: TaskItem): number {
  const target = Number(task.target)
  const current = Number(task.current)
  if (!Number.isFinite(target) || target <= 0) {
    return 0
  }
  return Math.min(100, Math.round((current / target) * 100))
}

/**
 * 进度数字。金额型任务的目标是小数，走 money 工具展示；
 * 次数型是整数，多两位 .00 只是噪音，所以按目标有没有小数位来定。
 */
function progressText(task: TaskItem): string {
  const decimals = task.target.includes('.') ? 2 : 0
  return `${format(money(task.current), decimals)}/${format(money(task.target), decimals)}`
}

/**
 * 档位的阈值文案。
 *
 * 小数位跟着<b>任务的目标</b>而不是这一档的阈值走 —— 否则同一个任务里
 * 会出现「1 次」和「5.00 次」两种写法。理由同 progressText。
 */
function stageLabel(task: TaskItem, stage: TaskStage): string {
  const decimals = task.target.includes('.') ? 2 : 0
  return format(money(stage.target), decimals)
}

/** 有分组的任务排在一起。没有分组的归到「其他」，但只在真的存在分组时才分区 */
const grouped = computed(() => {
  const list = tasks.data.value ?? []
  const hasGroup = list.some((t) => t.taskGroup !== null && t.taskGroup !== '')
  if (!hasGroup) {
    return [{ name: '', items: list }]
  }
  const groups = new Map<string, TaskItem[]>()
  for (const task of list) {
    const name = task.taskGroup === null || task.taskGroup === '' ? '其他' : task.taskGroup
    const bucket = groups.get(name)
    if (bucket === undefined) {
      groups.set(name, [task])
    } else {
      bucket.push(task)
    }
  }
  return [...groups].map(([name, items]) => ({ name, items }))
})
</script>

<template>
  <div class="tasks">
    <Section
      title="我的任务"
      :loading="tasks.loading.value"
      :error="tasks.error.value"
      :empty="(tasks.data.value ?? []).length === 0"
      empty-text="当前没有可做的任务，去活动中心看看"
      @retry="tasks.reload"
    >
      <div class="groups">
        <div v-for="group in grouped" :key="group.name" class="group">
          <h3 v-if="group.name !== ''" class="group__title">{{ group.name }}</h3>

          <Card>
            <div v-for="task in group.items" :key="task.taskId" class="task">
              <span class="task__icon" :class="{ 'task__icon--done': task.finished }">
                <Icon :name="task.finished ? 'check' : 'task'" :size="20" />
              </span>

              <div class="task__main">
                <p class="task__title">{{ task.taskName }}</p>

                <!--
                  阶梯任务：每一档一行，已达标的打勾。
                  单档任务不画这块 —— 它的奖励在右侧那行摘要里，画阶梯是多余的。
                -->
                <ul v-if="task.stages.length > 1" class="ladder">
                  <li
                    v-for="stage in task.stages"
                    :key="stage.target"
                    class="ladder__item"
                    :class="{ 'ladder__item--reached': stage.reached }"
                  >
                    <Icon
                      class="ladder__mark"
                      :name="stage.reached ? 'check' : 'star'"
                      :size="14"
                    />
                    <span class="ladder__target">{{ stageLabel(task, stage) }}</span>
                    <span class="ladder__reward">{{ stage.rewardText }}</span>
                  </li>
                </ul>

                <div v-if="showsProgress(task)" class="task__progress">
                  <!--
                    进度条用 role="progressbar" 而不是干画一个 div：读屏用户听到的
                    是「进度 1，最小 0，最大 3」，不是一句什么都没有的空元素。
                  -->
                  <div
                    class="task__track"
                    role="progressbar"
                    :aria-valuenow="Number(task.current)"
                    aria-valuemin="0"
                    :aria-valuemax="Number(task.target)"
                  >
                    <span class="task__fill" :style="{ width: `${progressPercent(task)}%` }" />
                  </div>
                  <span class="task__count">{{ progressText(task) }}</span>
                </div>
                <p v-else class="task__status">{{ task.statusText }}</p>
              </div>

              <div class="task__side">
                <span v-if="task.rewardText !== null" class="task__reward">
                  {{ task.rewardText }}
                </span>
                <!--
                  没有「领取」按钮：达标即自动发奖，见文件头注释。
                  有 actionUrl 的任务给一个「去完成」，没有的（比如每日登录）就不画 ——
                  一个点了没去处的按钮比没有更糟。
                -->
                <a
                  v-if="!task.finished && task.actionUrl !== null && task.actionUrl !== ''"
                  class="task__go"
                  :href="task.actionUrl"
                >
                  去完成
                </a>
                <span v-else-if="task.finished" class="task__done">{{ task.statusText }}</span>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </Section>
  </div>
</template>

<style scoped>
.ladder {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
}

.ladder__item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--sv-text-tertiary);
}

/* 已拿到的那档要看得出来 —— 否则用户签到 1 天拿了奖，界面上毫无变化 */
.ladder__item--reached {
  color: var(--sv-color-success);
}

.ladder__mark {
  flex: none;
}

.ladder__target {
  flex: none;
  font-variant-numeric: tabular-nums;
}

.ladder__reward {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tasks {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md) var(--sv-space-page) 0;
}

.groups {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-lg);
}

.group__title {
  margin: 0 0 var(--sv-space-sm) var(--sv-space-xs);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  font-weight: 600;
}

.task {
  display: flex;
  align-items: flex-start;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md);
}

.task__icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--sv-radius-md);
  background: var(--sv-bg-fill);
  color: var(--sv-text-secondary);
}

.task__icon--done {
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
}

.task__main {
  flex: 1;
  min-width: 0;
}

.task__title {
  margin: 0;
  font-size: var(--sv-font-caption);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task__status {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
}

.task__progress {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
  margin-top: var(--sv-space-sm);
}

.task__track {
  flex: 1;
  height: 4px;
  border-radius: var(--sv-radius-pill);
  background: var(--sv-bg-fill);
  overflow: hidden;
}

.task__fill {
  display: block;
  height: 100%;
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary);
  transition: width 0.3s ease;
}

@media (prefers-reduced-motion: reduce) {
  .task__fill {
    transition: none;
  }
}

.task__count {
  flex: none;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  font-variant-numeric: tabular-nums;
}

.task__side {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sv-space-xs);
}

.task__reward {
  color: var(--sv-color-primary);
  font-size: var(--sv-font-footnote);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.task__done {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}

.task__go {
  padding: 3px var(--sv-space-md);
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary);
  color: var(--sv-text-on-primary);
  font-size: var(--sv-font-footnote);
  font-weight: 600;
  text-decoration: none;
}

.task__go:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}
</style>
