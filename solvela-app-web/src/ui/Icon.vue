<script setup lang="ts">
/**
 * 图标。**内联 SVG，不引图标库。**
 *
 * <p>整个 C 端用到的图标是个位数，为它装一个图标库要么全量打包（几百 KB），
 * 要么配一套按需引入的构建插件 —— 两种成本都比这个文件高。
 * 需要新图标时往下面的 map 里加一条 path，24×24 视图框、线性、`currentColor`。
 *
 * <p>用 `stroke` 而不是 `fill`：线性图标在小尺寸下比面性清晰，
 * 而且改粗细只要改一个 `stroke-width`，不用重画。
 */

const props = withDefaults(
  defineProps<{
    name: IconName
    size?: number
    /** 无障碍名。不传则视为纯装饰，对读屏隐藏 */
    label?: string
  }>(),
  { size: 24 },
)

export type IconName = keyof typeof PATHS

const PATHS = {
  phone: 'M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Zm4 15h2',
  lock: 'M6 11h12v9H6zM9 11V7a3 3 0 0 1 6 0v4',
  eye: 'M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6Zm10 2.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
  'eye-off':
    'M3 3l18 18M10.6 6.2A9.9 9.9 0 0 1 12 6c6.5 0 10 6 10 6a17 17 0 0 1-3.2 3.8M6.5 8.3A17 17 0 0 0 2 12s3.5 6 10 6a9.8 9.8 0 0 0 3.3-.6M9.9 9.9a3 3 0 0 0 4.2 4.2',
  home: 'M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z',
  gift: 'M3 11h18v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1zM3 7h18v4H3zM12 7v14M12 7S10.5 3 8.5 3a2 2 0 1 0 0 4M12 7s1.5-4 3.5-4a2 2 0 1 1 0 4',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM4 21a8 8 0 0 1 16 0',
  settings:
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm8.4-3a8.4 8.4 0 0 0-.15-1.5l2-1.5-2-3.4-2.3 1a8.3 8.3 0 0 0-2.6-1.5L15 2.5H9l-.35 2.6a8.3 8.3 0 0 0-2.6 1.5l-2.3-1-2 3.4 2 1.5a8.5 8.5 0 0 0 0 3l-2 1.5 2 3.4 2.3-1a8.3 8.3 0 0 0 2.6 1.5L9 21.5h6l.35-2.6a8.3 8.3 0 0 0 2.6-1.5l2.3 1 2-3.4-2-1.5c.1-.5.15-1 .15-1.5Z',
  palette:
    'M12 21a9 9 0 1 1 0-18c4.5 0 9 3 9 7 0 2.2-1.8 3.5-4 3.5h-1.5a1.75 1.75 0 0 0-1.2 3c.35.4.45.9.2 1.4-.3.65-1.2 1.1-2.5 1.1ZM7.5 10.5h.01M11 7.5h.01M15.5 9h.01',
  chevron: 'm9 5 7 7-7 7',
  check: 'm5 13 4.5 4.5L19 7',
  wallet:
    'M3 8a2 2 0 0 1 2-2h13a1 1 0 0 1 1 1v1M3 8v10a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-2M3 8h16a2 2 0 0 1 2 2v2h-4a2 2 0 1 0 0 4h4',
  receipt: 'M6 3h12v18l-2.5-1.5L13 21l-2.5-1.5L8 21l-2-1.5zM9 8h6M9 12h6',
  logout:
    'M15 17v2a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v2M9 12h11m0 0-3-3m3 3-3 3',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm5.8-2.2L21 21',
  close: 'M6 6l12 12M18 6 6 18',
  heart:
    'M12 20.3C9.8 18.7 3 14.2 3 10.2A4.2 4.2 0 0 1 7.2 6c2 0 3.6 1.2 4.8 3 1.2-1.8 2.8-3 4.8-3A4.2 4.2 0 0 1 21 10.2c0 4-6.8 8.5-9 10.1Z',
  star: 'm12 3.5 2.6 5.3 5.9.9-4.25 4.15 1 5.85L12 16.9l-5.25 2.8 1-5.85L3.5 9.7l5.9-.9L12 3.5Z',
  cart: 'M3 4h2.2l2.3 11.2a1 1 0 0 0 1 .8h8.6a1 1 0 0 0 1-.8L20 7H6.4M9 20h.01M17 20h.01',
  task: 'M9 4h6a1 1 0 0 1 1 1v1H8V5a1 1 0 0 1 1-1ZM8 6H6a1 1 0 0 0-1 1v13a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V7a1 1 0 0 0-1-1h-2m-7 7 2 2 4-4',
  // ---- 商城分类：只用于 Mall 的分类条，加新分类时往这里加一条 ----
  shirt: 'M8.5 3 12 5.5 15.5 3 21 6.2l-2.6 4.2L17 9.4V21H7V9.4l-1.4 1L3 6.2 8.5 3Z',
  bag: 'M4 8h16l-1.1 12.1a1 1 0 0 1-1 .9H6.1a1 1 0 0 1-1-.9L4 8Zm4 0V6a4 4 0 0 1 8 0v2',
  shoe: 'M3 18v-7h3.2l2.8 2.4h3.6l4.6 1.6a3 3 0 0 1 2 2.8V19H4a1 1 0 0 1-1-1ZM6.2 11V8.2',
  watch:
    'M12 18a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm-3.2-9.4L9.2 3h5.6l.4 5.6M8.8 17.4l.4 3.6h5.6l.4-3.6M12 10.5V13l1.6 1',
} as const
</script>

<template>
  <svg
    class="sv-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    :role="label === undefined ? 'presentation' : 'img'"
    :aria-hidden="label === undefined ? 'true' : undefined"
    :aria-label="label"
  >
    <path :d="PATHS[props.name]" />
  </svg>
</template>

<style scoped>
.sv-icon {
  flex: none;
  display: block;
}
</style>
