<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import type { GradeLadderItem } from '@/api/grade'

/**
 * 等级卡片组：左右滑动换档，下面的权益跟着换。
 *
 * <h3>🔴 为什么不复用 ui/Carousel</h3>
 * 那个组件有两条特性在这里是<b>反的</b>：
 * <ul>
 *   <li><b>自动轮播</b> —— 用户正低头读某一档的权益时，卡片会自己滑走，
 *       下面那一整块内容跟着换掉。焦点位轮播该自动播，等级卡片绝不能；</li>
 *   <li><b>环绕</b> —— 从钻石滑一下回到普通，等于告诉用户等级是个圈。
 *       等级是有底有顶的阶梯，到头就该滑不动。</li>
 * </ul>
 *
 * <h3>用原生 scroll-snap，不手搓 transform</h3>
 * Carousel 那边手搓是因为它<b>不需要惯性</b>（它的注释里写了这个判据）。
 * 而这里要露出左右两张的边（用户得看见「上面还有一档」才有往上够的理由），
 * 卡片又要跟手要有动量 —— 那是浏览器的活。scroll-snap 还顺带给了
 * 键盘滚动、无障碍焦点滚动、以及各家 WebView 自己的回弹曲线。
 *
 * <h3>⚠️ 初始要停在用户自己那一档</h3>
 * 不做这件事的话，每个人打开会员中心看到的都是「普通会员」——
 * 钻石用户要自己划四下才能看见自己。这是这类页面最容易漏、
 * 漏了之后又显得整页都没做对的一件事。
 */

const props = defineProps<{
  items: GradeLadderItem[]
  /** 本期成长值，用来算未解锁那一档「还差多少」 */
  currentValue: number
  /** 首次定位到第几张。之后由用户的手指说了算，这个值再变也不追 */
  initialIndex: number
}>()

/** 当前停在第几张。父级据此渲染下面的权益区 */
const emit = defineEmits<{ 'update:index': [index: number] }>()

const viewportEl = ref<HTMLElement | null>(null)
const cardEls = ref<HTMLElement[]>([])
const active = ref(props.initialIndex)

/*
 * ⚠️ matchMedia 要兜底：老浏览器和 jsdom 里没有这个 API，直接调会抛，
 * 而抛在 setup 里等于整张卡片组渲染不出来 —— 为了一个动画偏好把页面搞白屏。
 * 取不到就当「不需要减弱动画」，这和 stores/theme.ts 对同一个 API 的处理一致。
 */
const prefersReducedMotion =
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

/*
 * 🔴 程序化滚动期间必须屏蔽 scroll 回调。
 *
 * 不屏蔽的话：我们平滑滚向第 3 张，途中经过第 1、2 张，每经过一张
 * scroll 回调都会把 active 改成途经的那一张，下面的权益区就会在
 * 一次定位里连闪三次内容 —— 而最后停下来的又确实是第 3 张，
 * 所以看起来像是「页面自己抽了一下」，还不容易归因。
 */
let programmatic = false
let releaseTimer: ReturnType<typeof setTimeout> | null = null
let rafId: number | null = null

function releaseLater(ms: number): void {
  if (releaseTimer !== null) {
    clearTimeout(releaseTimer)
  }
  releaseTimer = setTimeout(() => {
    programmatic = false
    releaseTimer = null
  }, ms)
}

function scrollToIndex(index: number, smooth: boolean): void {
  const vp = viewportEl.value
  const el = cardEls.value[index]
  if (vp === undefined || vp === null || el === undefined) {
    return
  }
  programmatic = true
  // 让这张卡在视口里居中：左右各露出邻居的一条边
  const left = el.offsetLeft - (vp.clientWidth - el.offsetWidth) / 2
  const animate = smooth && !prefersReducedMotion
  /*
   * ⚠️ 带 options 的 scrollTo 不是处处都有（老 WebView、jsdom 都没有）。
   * 退回直接赋 scrollLeft：丢的只是那段平滑动画，位置照样对 ——
   * 而不兜底的话这里会抛，把整个定位（包括「一进页面停在自己那一档」）带掉。
   */
  if (typeof vp.scrollTo === 'function') {
    vp.scrollTo({ left, behavior: animate ? 'smooth' : 'auto' })
  } else {
    vp.scrollLeft = left
  }
  releaseLater(animate ? 450 : 50)
}

/**
 * 取「中心离视口中心最近」的那一张，而不是拿 scrollLeft 除以卡片宽度。
 *
 * ⚠️ 后者要求每张卡等宽且间距固定，而首尾两张的 padding 是不对称的
 * （靠 scroll-padding 让它们也能居中）—— 除法算出来的下标在首尾会偏一张。
 */
function nearestIndex(): number {
  const vp = viewportEl.value
  if (vp === null) {
    return active.value
  }
  const center = vp.scrollLeft + vp.clientWidth / 2
  let best = 0
  let bestDistance = Number.POSITIVE_INFINITY
  cardEls.value.forEach((el, i) => {
    const distance = Math.abs(el.offsetLeft + el.offsetWidth / 2 - center)
    if (distance < bestDistance) {
      bestDistance = distance
      best = i
    }
  })
  return best
}

function onScroll(): void {
  if (programmatic) {
    return
  }
  // scroll 事件在滑动中每帧都来好几发，合并到一帧里算一次就够
  if (rafId !== null) {
    return
  }
  rafId = requestAnimationFrame(() => {
    rafId = null
    const index = nearestIndex()
    if (index !== active.value) {
      active.value = index
      emit('update:index', index)
    }
  })
}

/*
 * 鼠标拖拽。
 *
 * ⚠️ 触摸设备不需要这段（浏览器原生就能滚），但<b>鼠标拖不动 overflow 容器</b> ——
 * 桌面上只有滚轮、触控板和滚动条能滚，而滚动条我们藏掉了。
 * 不补这一段，用鼠标的人会觉得这个卡片组是坏的（真的被这么反馈过）。
 */
let pointerDragging = false
let pointerStartX = 0
let pointerStartScroll = 0

function onPointerDown(e: PointerEvent): void {
  // 触摸交给浏览器：自己接管会把原生的动量和吸附一起弄丢
  if (e.pointerType === 'touch' || props.items.length <= 1) {
    return
  }
  const vp = viewportEl.value
  if (vp === null) {
    return
  }
  pointerDragging = true
  pointerStartX = e.clientX
  pointerStartScroll = vp.scrollLeft
  // 捕获指针：拖到卡片外面松手也要收得到 pointerup
  vp.setPointerCapture?.(e.pointerId)
}

function onPointerMove(e: PointerEvent): void {
  const vp = viewportEl.value
  if (!pointerDragging || vp === null) {
    return
  }
  vp.scrollLeft = pointerStartScroll - (e.clientX - pointerStartX)
}

function onPointerUp(): void {
  if (!pointerDragging) {
    return
  }
  pointerDragging = false
  /*
   * 松手要自己吸附到最近一张。scroll-snap 只在浏览器自己产生的滚动结束时生效，
   * 我们是直接改 scrollLeft，它不管 —— 不补这一下，卡片会停在两张中间。
   */
  scrollToIndex(nearestIndex(), true)
}

function goTo(index: number): void {
  if (index < 0 || index >= props.items.length || index === active.value) {
    return
  }
  active.value = index
  emit('update:index', index)
  scrollToIndex(index, true)
}

/*
 * 等卡片真正进 DOM 了再定位。items 是异步来的，挂载那一刻它还是空数组，
 * 这时 scrollTo 没有任何东西可滚 —— 页面就停在第一张上了。
 */
watch(
  () => props.items.length,
  async (length) => {
    if (length === 0) {
      return
    }
    const target = Math.min(Math.max(props.initialIndex, 0), length - 1)
    active.value = target
    emit('update:index', target)
    await nextTick()
    // 首次定位不要动画：一进页面就看见卡片自己滑过去，像是页面没加载完
    scrollToIndex(target, false)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (releaseTimer !== null) {
    clearTimeout(releaseTimer)
  }
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
  }
})

/** 未解锁那一档还差多少。已解锁的返回 0 */
function gapOf(item: GradeLadderItem): number {
  return Math.max(0, item.threshold - props.currentValue)
}

/**
 * 卡面配色按<b>档位序号</b>取，不按等级名。
 *
 * 等级是配置出来的，名字可以叫「青铜」也可以叫「Lv.1」，档数也可能是 3 档或 7 档 ——
 * 按名字匹配配色，运营改个名整张卡就变回兜底色，而且不报错。
 */
const PALETTE = [
  'linear-gradient(135deg, #8d9aa8, #5b6673)',
  'linear-gradient(135deg, #b9c2cc, #7d8896)',
  'linear-gradient(135deg, #e0b063, #b8863a)',
  'linear-gradient(135deg, #5a6478, #2f3542)',
  'linear-gradient(135deg, #6f7bd8, #3b3f8f)',
]

function cardStyle(index: number): Record<string, string> {
  return { background: PALETTE[Math.min(index, PALETTE.length - 1)]! }
}

const total = computed(() => props.items.length)
</script>

<template>
  <div v-if="items.length > 0" class="deck">
    <div
      ref="viewportEl"
      class="deck__viewport"
      role="group"
      aria-label="等级卡片，左右滑动查看各档权益"
      @scroll.passive="onScroll"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <!--
        卡面去饱和看的是「这一档是不是他的」，不是「成长值够不够」——
        缓冲期里白金仍然是他正在用的那张卡，灰掉就等于说它不属于他。
      -->
      <article
        v-for="(item, index) in items"
        :key="item.gradeCode"
        ref="cardEls"
        class="deck__card"
        :class="{
          'deck__card--locked': !item.reached && !item.current,
          'deck__card--active': index === active,
        }"
        :aria-current="index === active ? 'true' : undefined"
      >
        <div class="deck__face" :style="cardStyle(index)"></div>

        <div class="deck__content">
          <header class="deck__head">
            <span class="deck__name">{{ item.gradeName }}</span>
            <!--
              🔴 「当前」和「未解锁」是两个独立的标，必须能同时出现 —— 所以这里
              是两个 v-if，不是 if/else-if/else 链。

              保级缓冲期里用户【在】白金但成长值【够不着】白金：写成链的话
              current 一命中就短路，「未解锁」永远出不来，而那恰恰是这一页
              最该说出口的一句 —— 他正挂在一档自己撑不住的等级上。
              （这个 bug 真出现过：注释先写对了，代码却写成了链。）
            -->
            <span v-if="item.current" class="deck__badge deck__badge--current">当前</span>
            <span v-else-if="item.reached" class="deck__badge">已达成</span>
            <span v-if="!item.reached" class="deck__badge deck__badge--locked">未解锁</span>
          </header>

          <p class="deck__threshold">
            <template v-if="item.threshold === 0">入门档，注册即拥有</template>
            <template v-else>{{ item.threshold }} 成长值</template>
          </p>

          <p class="deck__gap">
            <template v-if="item.reached">已达成这一档的门槛</template>
            <!--
              ⚠️ 「在这一档但成长值不够」不要写死成「保级还差」：
              人工调级也会造出同样的状态，而那时说保级是错的。
              说成长值没达标在两种情况下都成立。
            -->
            <template v-else-if="item.current">成长值暂未达标，还差 {{ gapOf(item) }}</template>
            <template v-else>还差 {{ gapOf(item) }} 成长值</template>
          </p>
        </div>
      </article>
    </div>

    <!-- 圆点：既是进度指示，也是唯一能用键盘换档的入口 -->
    <div v-if="total > 1" class="deck__dots">
      <button
        v-for="(item, index) in items"
        :key="item.gradeCode"
        type="button"
        class="deck__dot"
        :class="{ 'deck__dot--active': index === active }"
        :aria-current="index === active"
        :aria-label="`${item.gradeName}，第 ${index + 1} 档，共 ${total} 档`"
        @click="goTo(index)"
      />
    </div>
  </div>
</template>

<style scoped>
.deck {
  /*
   * 一屏一张：卡片宽度正好等于页面内容宽，左右与下面的卡片对齐。
   *
   * 🔴 之前露出邻居的一条边（peek），本意是「让用户看见上面还有一档」。
   * 真机上那半张卡看着像是没排好版，而且把卡片挤窄了 ——
   * 窄到「白金会员」加两个标签就得折行。
   * 「还有更多」这件事交给下面的圆点说，它说得更清楚，也不占卡面。
   */
  --deck-gutter: var(--sv-space-page);
}

.deck__viewport {
  display: flex;
  /*
    🔴 间距必须 >= 左右 gutter，否则邻居会在屏幕边缘漏出一条细边。
    算一下就清楚：某张卡居中时 scrollLeft = 卡宽 + gap，
    而上一张的右边缘在 gutter + 卡宽 —— gap 小于 gutter 时前者更小，
    那几像素就露在屏幕最左边，看着像渲染没对齐。
  */
  gap: calc(var(--deck-gutter) * 2);
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  /*
    左右留白等于页面内边距：卡片就和下面那几张卡左右对齐。
    加上 snap-align:center，首尾两张也停得到正中间。
  */
  padding: 0 var(--deck-gutter);
  scrollbar-width: none;
  /* 鼠标拖拽时别选中卡片上的文字 */
  user-select: none;
  cursor: grab;
}

/*
 * 🔴 这里【不能】写 touch-action: pan-y。
 *
 * 第一版照着 ui/Carousel 抄了这一行，结果整个卡片组在触摸设备上划不动 ——
 * pan-y 的意思是「本元素只允许浏览器处理纵向平移，横向手势交给 JS」。
 * Carousel 需要它，因为它自己用 touch 事件实现横向；而这里横向靠的正是
 * 浏览器原生滚动，这一行等于把要用的东西关掉了。
 *
 * 留空（默认 auto）让浏览器自己判方向：横划滚卡片，竖划滚页面。
 */

.deck__viewport:active {
  cursor: grabbing;
}

.deck__viewport::-webkit-scrollbar {
  display: none;
}

.deck__card {
  position: relative;
  flex: none;
  /* 100% = 视口的内容宽（已扣掉左右 gutter），所以正好一屏一张 */
  width: 100%;
  min-height: 150px;
  padding: var(--sv-space-lg);
  border-radius: var(--sv-radius-lg);
  overflow: hidden;
  scroll-snap-align: center;
  color: #ffffff;
}

/*
  ⚠️ 一屏一张之后，非当前张【不再】缩放变淡。
  那套效果是给 peek 版做的（用来区分「正在看的」和露边的邻居）；
  现在屏幕上同时只有一张，缩放只会在滑动过程中让卡片抖一下。
*/

/*
  卡面单独一层，是为了只给背景去饱和。
  给整张卡加 filter 的话文字也会一起灰掉 —— 未解锁不等于看不清。
*/
.deck__face {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.deck__card--locked .deck__face {
  filter: saturate(0.25) brightness(0.8);
}

.deck__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

/*
  ⚠️ 要能换行。一张卡最多会同时挂「当前」+「未解锁」两个标（缓冲期），
  窄屏上挤不下时，宁可让标签整体掉到第二行 ——
  不换行的后果是等级名被从中间断开，「白金会 / 员」。
*/
.deck__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sv-space-sm);
}

.deck__name {
  font-size: var(--sv-font-heading);
  font-weight: 700;
  letter-spacing: -0.01em;
  /* 名字本身不断行：断开的等级名比掉一行难看得多 */
  white-space: nowrap;
}

.deck__badge {
  padding: 2px 8px;
  border-radius: var(--sv-radius-pill);
  background: rgb(255 255 255 / 22%);
  font-size: var(--sv-font-caption);
  font-weight: 600;
  white-space: nowrap;
}

.deck__badge--current {
  background: #ffffff;
  color: #1f2430;
}

.deck__badge--locked {
  background: rgb(0 0 0 / 28%);
}

.deck__threshold {
  margin: var(--sv-space-sm) 0 0;
  font-size: var(--sv-font-footnote);
  opacity: 0.9;
}

.deck__gap {
  margin: auto 0 0;
  padding-top: var(--sv-space-md);
  font-size: var(--sv-font-caption);
  opacity: 0.85;
}

.deck__dots {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin-top: var(--sv-space-md);
}

.deck__dot {
  width: 6px;
  height: 6px;
  border: 0;
  border-radius: 999px;
  padding: 0;
  background: var(--sv-border-strong);
  cursor: pointer;
  transition:
    width 0.25s ease,
    background-color 0.25s ease;
}

.deck__dot--active {
  width: 16px;
  background: var(--sv-color-primary);
}

.deck__dot:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .deck__card {
    transition: none;
  }
}
</style>
