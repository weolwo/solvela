<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

/**
 * 首页顶部的运营焦点位：自动轮播 + 触摸滑动 + 圆点。
 *
 * <h3>为什么这个可以自己写，"要碰手势惯性的别自己写"那条原则不适用</h3>
 * 拖拽跟手用的是<b>线性 1:1 跟随</b>（手指移多少，画布跟多少），松手只做一次
 * "过阈值就翻页、没过就弹回"的判定——没有速度估算、没有惯性衰减、没有橡皮筋
 * 回弹曲线。这类物理模拟正是 Picker/PullRefresh 那种组件真正难写的部分；
 * 一个焦点位轮播不需要它，做到这个程度足够顺手，再往上加不该我们自己啃。
 *
 * <h3>为什么卡片是纯色/渐变，不是图片</h3>
 * 后端目前没有暴露文件下载接口（{@code MineView} 的头像也卡在同一个原因），
 * 运营也还没有"轮播图配置"这张表——现在能拿到的字段就是活动的标题、副标题、
 * 主题色。用假图片占位只会看起来像图裂了；用真实字段渲染卡片，
 * 字段接上真数据的那天，这个组件不用跟着改。
 */

export interface CarouselItem {
  id: string
  title: string
  subtitle: string | null
  /** 卡片背景色（十六进制）。为空则用品牌渐变兜底 */
  themeColor: string | null
}

const props = withDefaults(
  defineProps<{
    items: CarouselItem[]
    /** 自动切换间隔（毫秒） */
    interval?: number
  }>(),
  { interval: 4500 },
)

const active = ref(0)
const viewportEl = ref<HTMLElement | null>(null)

/*
 * 🔴 dragging / dragDeltaX 必须是 ref，不能是普通变量——这不是风格选择。
 *
 * trackStyle 是 computed，Vue 的响应式只追踪 ref/reactive 依赖；改一个普通变量
 * 不会让任何 computed 重新求值。第一版就是拿普通变量写的（当时的想法是"只在
 * 事件回调间传递，包成响应式纯属浪费"），结果是拖拽时卡片完全不跟手——
 * onTouchMove 里改了 dragDeltaX，但 trackStyle 那次重算根本没有被触发，
 * 用户手指划了，画面纹丝不动，直到下一次 active 变化才顺带用上最新值。
 * 这个 bug 不报错、编译过、类型对，只有拖一下才看得出来。
 *
 * dragStartX / trackWidth 不需要是 ref：它们只在 onTouchEnd 里被【同步读一次】
 * 参与计算，不驱动任何模板/computed 的展示更新。
 */
const dragging = ref(false)
const dragDeltaX = ref(0)
let dragStartX = 0
let trackWidth = 0

const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** 自动轮播定时器。用户正在交互（触摸中/键盘聚焦）时必须暂停，见 pauseAutoplay */
let timer: ReturnType<typeof setInterval> | null = null

function startAutoplay(): void {
  stopAutoplay()
  if (props.items.length <= 1 || prefersReducedMotion) {
    return
  }
  timer = setInterval(() => {
    goTo(active.value + 1)
  }, props.interval)
}

function stopAutoplay(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

/** 环绕取模：JS 的 % 对负数不归一，-1 % 3 是 -1 不是 2，手写一遍 */
function wrap(index: number): number {
  const n = props.items.length
  return ((index % n) + n) % n
}

function goTo(index: number): void {
  active.value = wrap(index)
}

const trackStyle = computed(() => {
  const base = -active.value * 100
  const dragPercent = trackWidth > 0 ? (dragDeltaX.value / trackWidth) * 100 : 0
  return {
    transform: `translateX(${base + dragPercent}%)`,
    transition:
      dragging.value || prefersReducedMotion
        ? 'none'
        : 'transform 0.32s cubic-bezier(0.2, 0.7, 0.3, 1)',
  }
})

/** 过多少比例判定为"用户想翻页"，而不是弹回原地 */
const SWIPE_THRESHOLD_RATIO = 0.18

function onTouchStart(e: TouchEvent): void {
  if (props.items.length <= 1) {
    return
  }
  dragging.value = true
  dragStartX = e.touches[0]!.clientX
  dragDeltaX.value = 0
  // 🔴 宽度取 viewport（可视窗口，一张卡片的宽度），不是 track（flex 容器，
  // 内容撑开后是"卡片数 × 单张宽度"）——用错分母，算出来的拖拽比例会偏小，
  // 划到底也可能凑不够翻页阈值
  trackWidth = viewportEl.value?.clientWidth ?? 1
  stopAutoplay()
}

function onTouchMove(e: TouchEvent): void {
  if (!dragging.value) {
    return
  }
  dragDeltaX.value = e.touches[0]!.clientX - dragStartX
}

function onTouchEnd(): void {
  if (!dragging.value) {
    return
  }
  dragging.value = false
  const ratio = dragDeltaX.value / trackWidth
  /*
   * 🔴 三条分支都要清零 dragDeltaX，不能只有"没过阈值"那条有。
   *
   * trackStyle 的 transform 是 base（active 决定）+ dragPercent（dragDeltaX 决定）
   * 两者相加。过阈值翻页时只调了 goTo() 改 active，若不清零 dragDeltaX，
   * 新的 base 会叠上这一次拖拽已经产生的偏移——表现是翻页那一下"飞过头"：
   * 划到 -50% 松手翻页，目标位置变成 -100% + (-50%) = -150%，
   * 卡片多划出半张的量才停住，而且不会自己纠正回来（真实复现过）。
   */
  if (ratio <= -SWIPE_THRESHOLD_RATIO) {
    goTo(active.value + 1)
  } else if (ratio >= SWIPE_THRESHOLD_RATIO) {
    goTo(active.value - 1)
  }
  dragDeltaX.value = 0
  startAutoplay()
}

watch(
  () => props.items.length,
  () => {
    active.value = 0
    startAutoplay()
  },
)

startAutoplay()
onBeforeUnmount(stopAutoplay)
</script>

<template>
  <div
    v-if="items.length > 0"
    class="sv-carousel"
    role="region"
    aria-roledescription="carousel"
    aria-label="运营推荐"
    @focusin="stopAutoplay"
    @focusout="startAutoplay"
    @mouseenter="stopAutoplay"
    @mouseleave="startAutoplay"
  >
    <div
      ref="viewportEl"
      class="sv-carousel__viewport"
      @touchstart.passive="onTouchStart"
      @touchmove.passive="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <div class="sv-carousel__track" :style="trackStyle">
        <div
          v-for="item in items"
          :key="item.id"
          class="sv-carousel__slide"
          :style="item.themeColor === null ? undefined : { background: item.themeColor }"
          :aria-hidden="items[active]?.id !== item.id"
        >
          <p class="sv-carousel__title">{{ item.title }}</p>
          <p v-if="item.subtitle !== null" class="sv-carousel__subtitle">{{ item.subtitle }}</p>
        </div>
      </div>
    </div>

    <div v-if="items.length > 1" class="sv-carousel__dots">
      <button
        v-for="(item, index) in items"
        :key="item.id"
        class="sv-carousel__dot"
        type="button"
        :class="{ 'sv-carousel__dot--active': index === active }"
        :aria-current="index === active"
        :aria-label="`第 ${index + 1} 张，共 ${items.length} 张`"
        @click="goTo(index)"
      />
    </div>
  </div>
</template>

<style scoped>
.sv-carousel {
  position: relative;
}

.sv-carousel__viewport {
  overflow: hidden;
  border-radius: var(--sv-radius-lg);
  /* 手指纵向滑动仍要能触发页面滚动，只有横向手势才归轮播管 */
  touch-action: pan-y;
}

.sv-carousel__track {
  display: flex;
  will-change: transform;
}

.sv-carousel__slide {
  flex: none;
  width: 100%;
  min-height: 132px;
  padding: var(--sv-space-lg);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  background: linear-gradient(150deg, #ff6a4d, var(--sv-color-primary) 55%, #d92f1f);
  color: var(--sv-text-on-primary);
}

.sv-carousel__title {
  margin: 0;
  font-size: var(--sv-font-heading);
  font-weight: 700;
  letter-spacing: -0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sv-carousel__subtitle {
  margin: var(--sv-space-xs) 0 0;
  font-size: var(--sv-font-caption);
  opacity: 0.88;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sv-carousel__dots {
  position: absolute;
  right: var(--sv-space-md);
  bottom: var(--sv-space-sm);
  display: flex;
  gap: 6px;
}

.sv-carousel__dot {
  width: 6px;
  height: 6px;
  border: 0;
  border-radius: 999px;
  padding: 0;
  background: rgb(255 255 255 / 50%);
  cursor: pointer;
  transition:
    width 0.25s ease,
    background-color 0.25s ease;
}

.sv-carousel__dot--active {
  width: 16px;
  background: #ffffff;
}

.sv-carousel__dot:focus-visible {
  outline: 2px solid #ffffff;
  outline-offset: 2px;
}
</style>
