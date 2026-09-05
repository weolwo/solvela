<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

/**
 * 抽奖转盘。**内联 SVG，不引任何库。**
 *
 * <h3>为什么这个可以自己写</h3>
 * 项目原则是「要碰 teleport / 滚动锁定 / 焦点陷阱 / 手势惯性 的别自己写」。
 * 转盘一个都不沾：它<b>不接受任何手势</b>（转不转由下面的按钮决定），
 * 转一圈就是把 `<g>` 的 `transform: rotate()` 从当前角度<b>一次性</b>补间到算好的落点 ——
 * 没有速度估算、没有惯性衰减、没有回弹曲线。那些正是 Picker 真正难写的部分，这里没有。
 *
 * <h3>落点怎么算</h3>
 * 抽奖结果由父组件从后端拿到（中了哪个 prizeCode），再对到某一格的索引传进来。
 * 本组件只负责「转到第 targetIndex 格，让它停在顶部指针下」——
 * <b>不自己决定中奖结果</b>，那是服务端的事。
 *
 * <h3>父组件怎么驱动</h3>
 * 不用 ref 调方法（那样父组件要 import 本 .vue，在 eslint 视角下会变成 any 一串）。
 * 改成：`spin` 是一个自增计数器，每 +1 触发一次转动，转到当时的 `targetIndex`；
 * 停下时 emit `rest`。父组件把「等转盘停」写成一个 Promise 即可。
 */

/**
 * 转盘上的一格，<b>只有画一格需要的两个字段</b>。
 *
 * 刻意不复用 {@code api/activity} 的 WheelPrize：那个类型带着 prizeCode，
 * 而本组件从头到尾没碰过它。让展示组件认识后端契约，等于把「换个玩法就要改转盘」
 * 焊死进来 —— WheelPrize 结构上兼容这里，父组件直接传即可。
 */
export interface WheelSlice {
  /** 格子上显示的名字 */
  label: string
  /** 视觉分级：grand=大奖那一格（描金），normal=普通格 */
  tone: 'grand' | 'normal'
}

const props = defineProps<{
  /** 顺时针排列的格子，索引即扇区顺序 */
  prizes: WheelSlice[]
  /** 自增计数器：每 +1 转一次。初始 0 表示还没转过 */
  spin: number
  /** 这一次要停在哪一格 */
  targetIndex: number
}>()

const emit = defineEmits<{ rest: [] }>()

const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** 中心与半径，配合 viewBox 0 0 240 240。R 留出外圈金边的宽度 */
const CX = 120
const CY = 120
const R = 103
/** 外圈金边的中线半径 */
const RIM_R = R + 9

/*
 * ---- 配色：这是活动视觉，不是平台观感，所以是这一处写死的字面色，不走 --sv-* ----
 * 红 + 金交替，是中式抽奖盘最讨喜的一套。扇区数为偶数时交替不会在接缝处撞色（8 格没问题）。
 */
const RED = '#e5322b'
const RED_DEEP = '#cf2a20'
const GOLD_SLICE = '#ffd469'
const GOLD_SLICE_DEEP = '#f6c250'
const RIM_GOLD = '#f4c04a'
const RIM_GOLD_DEEP = '#c8871f'
const RIM_LINE = '#9c5c17'
const CAKE = '#ecb45c'
const CAKE_LINE = '#9c5c17'
const CREAM = '#fff6e6'
/** 金色扇区上的字色：深红，比纯黑更贴节日调 */
const INK = '#a3271a'

/** 累计旋转角度（度），只增不减，转动结束后归一化 */
const rotation = ref(0)
const spinning = ref(false)

const seg = computed(() => (props.prizes.length > 0 ? 360 / props.prizes.length : 360))

const mod360 = (x: number): number => ((x % 360) + 360) % 360

/** 0° 指正上方，角度顺时针增 */
function polar(radius: number, deg: number): [number, number] {
  const a = ((deg - 90) * Math.PI) / 180
  return [CX + radius * Math.cos(a), CY + radius * Math.sin(a)]
}

function sectorPath(i: number): string {
  const [x0, y0] = polar(R, i * seg.value)
  const [x1, y1] = polar(R, (i + 1) * seg.value)
  const large = seg.value > 180 ? 1 : 0
  return `M${CX} ${CY} L${x0.toFixed(2)} ${y0.toFixed(2)} A${R} ${R} 0 ${large} 1 ${x1.toFixed(2)} ${y1.toFixed(2)} Z`
}

/** 偶数格红、奇数格金。大奖格用略深一档的同色，不破坏交替、又能压出一点层次 */
function sliceFill(i: number, prize: WheelSlice): string {
  const gold = i % 2 !== 0
  if (gold) {
    return prize.tone === 'grand' ? GOLD_SLICE_DEEP : GOLD_SLICE
  }
  return prize.tone === 'grand' ? RED_DEEP : RED
}

function textFill(i: number): string {
  return i % 2 !== 0 ? INK : CREAM
}

/** 文字锚点：中线方向、离圆心 86 处 */
const LABEL_Y = 34
/** 月饼纹样：中线方向、离圆心 60 处（在文字和轮毂之间） */
const CAKE_Y = 60

/** 把某一格的内容（文字 / 纹样）摆到该格中线上的旋转；不翻正 */
function placeOnSlice(i: number): string {
  const mid = i * seg.value + seg.value / 2
  return `rotate(${mid.toFixed(2)} ${CX} ${CY})`
}

/**
 * 文字沿切向排布：外层 `rotate(mid)` 把文字摆到该格中线上；
 * 中线落在下半圈（90°~270°）时，再<b>绕文字自身锚点</b>补一个 180°，只把字翻正，位置不动
 *（净字形旋转回到 (-90°, 90°) 区间就是正的）。两个 rotate 的支点不同，别合并成一个。
 */
function labelTransform(i: number): string {
  const mid = i * seg.value + seg.value / 2
  const place = placeOnSlice(i)
  return mid > 90 && mid < 270 ? `${place} rotate(180 ${CX} ${LABEL_Y})` : place
}

const ariaLabel = computed(
  () => `抽奖转盘，共 ${props.prizes.length} 格：${props.prizes.map((p) => p.label).join('、')}`,
)

const rotorStyle = computed(() => ({
  transform: `rotate(${rotation.value}deg)`,
  transformOrigin: `${CX}px ${CY}px`,
  transition: spinning.value
    ? `transform ${prefersReducedMotion ? '0.28s ease' : '4s cubic-bezier(0.16, 1, 0.3, 1)'}`
    : 'none',
}))

let fallbackTimer: ReturnType<typeof setTimeout> | null = null

function clearFallback(): void {
  if (fallbackTimer !== null) {
    clearTimeout(fallbackTimer)
    fallbackTimer = null
  }
}

/**
 * 收尾。幂等 —— transitionend 与兜底定时器都会调它，谁先到算谁。
 * 兜底定时器是必须的：`prefers-reduced-motion` 下过渡可能短到 transitionend 不稳定触发。
 */
function finish(): void {
  if (!spinning.value) {
    return
  }
  spinning.value = false
  clearFallback()
  // 归一化：玩很多把之后 rotation 会累加到很大，视觉无差别，纯粹是卫生
  rotation.value = mod360(rotation.value)
  emit('rest')
}

async function runSpin(index: number): Promise<void> {
  const n = props.prizes.length
  if (spinning.value || n === 0) {
    return
  }
  const s = 360 / n
  // 目标扇区中心（从正上方顺时针量）。要让它停到指针（正上方）下，转过的角度 R 需满足 mid + R ≡ 0
  const mid = index * s + s / 2
  const targetMod = mod360(-mid)
  let delta = targetMod - mod360(rotation.value)
  if (delta <= 0) {
    delta += 360
  }
  // 落点在扇区内随机偏一点，别每次都卡正中；±0.25 格，稳在指针覆盖范围内
  const jitter = (Math.random() - 0.5) * s * 0.5
  const turns = prefersReducedMotion ? 0 : 5

  spinning.value = true
  // 先让带 transition 的样式渲染出去，再改角度，过渡才会触发
  await nextTick()
  rotation.value = rotation.value + turns * 360 + delta + jitter
  clearFallback()
  fallbackTimer = setTimeout(finish, prefersReducedMotion ? 450 : 4300)
}

watch(
  () => props.spin,
  (n) => {
    if (n > 0) {
      void runSpin(props.targetIndex)
    }
  },
)

onBeforeUnmount(clearFallback)
</script>

<template>
  <div class="sv-wheel" role="img" :aria-label="ariaLabel">
    <svg class="sv-wheel__svg" viewBox="0 0 240 240" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <radialGradient id="sv-wheel-hub" cx="38%" cy="34%" r="75%">
          <stop offset="0%" stop-color="#fff2cf" />
          <stop offset="60%" stop-color="#f7d488" />
          <stop offset="100%" stop-color="#e6ad48" />
        </radialGradient>
      </defs>

      <!-- 转动的部分：底盘 + 扇区 + 文字 + 月饼纹样 -->
      <g class="sv-wheel__rotor" :style="rotorStyle" @transitionend="finish">
        <circle :cx="CX" :cy="CY" :r="RIM_R" :fill="RIM_GOLD_DEEP" />
        <path
          v-for="(prize, i) in prizes"
          :key="`s-${i}`"
          :d="sectorPath(i)"
          :fill="sliceFill(i, prize)"
          :stroke="RIM_LINE"
          stroke-width="0.75"
        />

        <g v-for="(prize, i) in prizes" :key="`d-${i}`" :transform="placeOnSlice(i)">
          <g :transform="`translate(${CX} ${CAKE_Y})`">
            <circle r="8" :fill="CAKE" />
            <circle
              r="8"
              fill="none"
              :stroke="CAKE_LINE"
              stroke-width="1"
              stroke-dasharray="1.6 1.4"
            />
            <rect
              x="-4.3"
              y="-4.3"
              width="8.6"
              height="8.6"
              rx="2"
              fill="none"
              :stroke="CAKE_LINE"
              stroke-width="0.9"
            />
            <circle r="1.5" :fill="CAKE_LINE" />
          </g>
          <!-- 大奖格多一颗小星 -->
          <path
            v-if="prize.tone === 'grand'"
            :transform="`translate(${CX} ${CAKE_Y - 13})`"
            d="M0-4 1-1 4 0 1 1 0 4-1 1-4 0-1-1Z"
            :fill="CREAM"
          />
        </g>

        <text
          v-for="(prize, i) in prizes"
          :key="`t-${i}`"
          :transform="labelTransform(i)"
          :x="CX"
          :y="LABEL_Y"
          text-anchor="middle"
          dominant-baseline="middle"
          :fill="textFill(i)"
          :font-weight="prize.tone === 'grand' ? 700 : 600"
          font-size="8.5"
        >
          {{ prize.label }}
        </text>
      </g>

      <!-- 不转的部分：外圈金边 + 灯珠 + 轮毂 + 指针 -->
      <circle :cx="CX" :cy="CY" :r="RIM_R" fill="none" :stroke="RIM_GOLD_DEEP" stroke-width="13" />
      <circle :cx="CX" :cy="CY" :r="RIM_R" fill="none" :stroke="RIM_GOLD" stroke-width="6.5" />
      <circle :cx="CX" :cy="CY" :r="RIM_R + 6" fill="none" :stroke="RIM_LINE" stroke-width="0.8" />
      <circle :cx="CX" :cy="CY" :r="RIM_R - 6" fill="none" :stroke="RIM_LINE" stroke-width="0.8" />
      <circle
        v-for="b in 16"
        :key="`b-${b}`"
        :cx="polar(RIM_R, (b - 1) * (360 / 16))[0]"
        :cy="polar(RIM_R, (b - 1) * (360 / 16))[1]"
        r="2.2"
        :fill="CREAM"
      />

      <circle :cx="CX" :cy="CY" r="30" :fill="RIM_GOLD_DEEP" />
      <circle :cx="CX" :cy="CY" r="26.5" fill="url(#sv-wheel-hub)" />
      <circle :cx="CX" :cy="CY" r="26.5" fill="none" :stroke="RIM_GOLD_DEEP" stroke-width="1.4" />
      <!-- 轮毂里的玉兔 -->
      <g :fill="RIM_LINE">
        <ellipse cx="116.6" cy="112.5" rx="2.3" ry="6.4" transform="rotate(-14 116.6 112.5)" />
        <ellipse cx="123.4" cy="112.5" rx="2.3" ry="6.4" transform="rotate(14 123.4 112.5)" />
        <circle cx="120" cy="119" r="5.6" />
        <ellipse cx="120" cy="128" rx="8.4" ry="6.4" />
        <circle cx="128" cy="129.5" r="2.4" />
      </g>

      <!-- 顶部水滴指针，指向 12 点方向的扇区 -->
      <path
        d="M120 8c-7 0-11 7-11 13 0 8 6 13 11 19 5-6 11-11 11-19 0-6-4-13-11-13Z"
        :fill="RIM_GOLD"
        :stroke="RIM_GOLD_DEEP"
        stroke-width="1.6"
      />
      <circle cx="120" cy="20" r="4" :fill="CREAM" />
    </svg>
  </div>
</template>

<style scoped>
.sv-wheel {
  position: relative;
  width: 100%;
  max-width: 320px;
  margin: 0 auto;
  aspect-ratio: 1;
  /* 金色光晕，让转盘从深色背景里浮起来 */
  filter: drop-shadow(0 8px 24px rgb(0 0 0 / 35%));
}

.sv-wheel__svg {
  display: block;
  width: 100%;
  height: 100%;
  /* 指针尖端超出 viewBox 顶边一点，别被裁掉 */
  overflow: visible;
  font-family: inherit;
}

.sv-wheel__rotor {
  will-change: transform;
}
</style>
