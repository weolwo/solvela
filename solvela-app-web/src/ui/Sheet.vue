<script setup lang="ts">
import { nextTick, useTemplateRef, watch } from 'vue'

/**
 * 底部弹层。列表项的详情用它，不用单开一个路由。
 *
 * <h3>为什么详情不做成独立页面</h3>
 * 详情要显示的东西<b>列表已经全带在手上了</b>（档位、规则、周期、截止时间）。
 * 单开一个路由意味着再加一个「按 id 查一条」的接口，于是同一份数据有两个源，
 * 而它们迟早会不一致 —— 列表说 3/5、详情说 2/5，没人知道哪个对。
 *
 * <p>任务也不是分享入口（活动才是），没有 deep link 的需求。
 * 弹层还省掉了「直接访问详情 URL 时列表还没加载」这类边界。
 *
 * <h3>可访问性不是可选项</h3>
 * 弹层最容易漏的三件事，这里都做了：
 * <ul>
 *   <li><b>Esc 关闭</b> —— 键盘用户没有别的出路；</li>
 *   <li><b>打开时焦点进弹层</b> —— 否则读屏用户还停在背后的列表上，
 *       完全不知道弹出了东西；</li>
 *   <li><b>点遮罩关闭</b>，且只在<b>点遮罩本身</b>时关（{@code .self}）——
 *       否则在弹层里选一段文字、鼠标松开时飘到遮罩上，弹层就没了。</li>
 * </ul>
 */

const open = defineModel<boolean>({ required: true })

defineProps<{ title: string }>()

const panel = useTemplateRef<HTMLElement>('panel')

watch(open, async (value) => {
  if (!value) {
    return
  }
  // 等 DOM 出来再移焦点，否则 focus() 落在一个还不存在的元素上
  await nextTick()
  panel.value?.focus()
})

function close(): void {
  open.value = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    close()
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="sheet">
      <div
        v-if="open"
        class="sheet"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        @click.self="close"
        @keydown="onKeydown"
      >
        <!-- tabindex=-1 让面板本身可以接收焦点，但不进 Tab 顺序 -->
        <div ref="panel" class="sheet__panel" tabindex="-1">
          <div class="sheet__head">
            <h2 class="sheet__title">{{ title }}</h2>
            <button class="sheet__close" type="button" aria-label="关闭" @click="close">
              <Icon name="close" :size="20" />
            </button>
          </div>
          <div class="sheet__body">
            <slot />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.sheet {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgb(0 0 0 / 45%);
}

.sheet__panel {
  width: 100%;
  max-width: 640px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  background: var(--sv-bg-surface);
  border-radius: 20px 20px 0 0;
  outline: none;
}

.sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 8px;
}

.sheet__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--sv-text-primary);
  text-wrap: balance;
}

.sheet__close {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--sv-text-tertiary);
  background: none;
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.sheet__close:hover {
  background: var(--sv-bg-fill);
}

.sheet__body {
  /* 内容比屏幕长时在面板里滚，页面本身不动 */
  overflow-y: auto;
  padding: 0 16px calc(16px + env(safe-area-inset-bottom));
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.2s ease;
}

.sheet-enter-active .sheet__panel,
.sheet-leave-active .sheet__panel {
  transition: transform 0.24s cubic-bezier(0.32, 0.72, 0, 1);
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .sheet__panel,
.sheet-leave-to .sheet__panel {
  transform: translateY(100%);
}

/* 关掉动画的人不该被硬推一下 —— 直接出现即可 */
@media (prefers-reduced-motion: reduce) {
  .sheet-enter-active,
  .sheet-leave-active,
  .sheet-enter-active .sheet__panel,
  .sheet-leave-active .sheet__panel {
    transition: none;
  }
}
</style>
