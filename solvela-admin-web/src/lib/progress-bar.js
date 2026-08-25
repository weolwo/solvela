/*
 * 顶部路由加载进度条 —— 替代 nprogress
 *
 * 为什么自己写：整个项目只用到 nprogress 的两个 API（start / done），
 * 为两个函数扛一个停更十年的依赖不划算；而且它 1.x 把 CSS 从
 * `nprogress/nprogress.css` 挪到了 `nprogress/css/nprogress.css`，
 * 升级时直接把 dev server 打挂过一次。
 *
 * 本实现<b>刻意不引入任何 CSS 文件</b>，样式全部由 JS 注入 ——
 * 这样就从根上消灭了「依赖改了 CSS 路径导致构建失败」这一整类问题。
 *
 * 颜色可通过 CSS 变量覆盖（不写死主题色，暗色主题下也能改）：
 *   :root { --solvela-progress-color: #1677ff; }
 *
 * @Author:    alaric
 * @Date:      2026-08-01
 */

/**
 * 延迟显示的时间。
 *
 * 🔴 这是相比 nprogress 的实质改进：SPA 里绝大多数路由切换都在几十毫秒内完成，
 * 立刻显示进度条会导致「闪一下」—— 那比不显示更糟，因为用户会以为页面抖了一下。
 * 所以先等一小会儿，若路由在这个窗口内就切完了，进度条<b>压根不出现</b>。
 */
const SHOW_DELAY_MS = 120;

/**
 * 递增间隔。真实进度不可知，只能模拟
 */
const TRICKLE_INTERVAL_MS = 240;

/**
 * 自行递增的上限：绝不自己走到 100%，
 * 100% 只能由 done() 给 —— 否则会出现「条子满了但页面还没好」的假象
 */
const MAX_BEFORE_DONE = 90;

/**
 * 冲到 100% 后停留多久再淡出，留一帧让用户看到「完成」
 */
const HOLD_BEFORE_FADE_MS = 80;

/**
 * 淡出时长，与 CSS transition 保持一致
 */
const FADE_MS = 240;

const TRANSITION = 'width 200ms ease, opacity 240ms ease';

let bar = null;
let showTimer = null;
let trickleTimer = null;
let fadeTimer = null;
let progress = 0;
let visible = false;

/**
 * 懒建 DOM：模块加载时 document.body 未必存在（main.js 里 import 很早就执行了）
 */
function ensureBar() {
  if (bar && bar.isConnected) {
    return bar;
  }
  bar = document.createElement('div');
  bar.className = 'solvela-progress-bar';
  bar.setAttribute('role', 'progressbar');
  bar.setAttribute('aria-hidden', 'true');
  Object.assign(bar.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    height: '2px',
    width: '0%',
    // 要盖住 antd 的 Modal(1000)/Message(1010)，但别高到挡住浏览器自身的 UI
    zIndex: '2000',
    background: 'var(--solvela-progress-color, #1677ff)',
    boxShadow: '0 0 8px var(--solvela-progress-color, #1677ff)',
    opacity: '0',
    // 进度条不该拦住任何点击
    pointerEvents: 'none',
    transition: TRANSITION,
  });
  document.body.appendChild(bar);
  return bar;
}

function paint(percent) {
  progress = Math.min(percent, 100);
  const el = ensureBar();
  el.style.opacity = '1';
  el.style.width = progress + '%';
}

function clearTimers() {
  clearTimeout(showTimer);
  clearInterval(trickleTimer);
  clearTimeout(fadeTimer);
  showTimer = null;
  trickleTimer = null;
  fadeTimer = null;
}

/**
 * 复位。
 *
 * ⚠️ 必须先关掉 transition 再把宽度归零，否则会看到进度条「倒着缩回左边」；
 * 归零后要强制一次重排再恢复 transition，不然下一次 start 会沿用 none、失去动画。
 */
function reset() {
  const el = ensureBar();
  el.style.transition = 'none';
  el.style.width = '0%';
  // 读一次布局属性即可强制重排，这一行不能删
  void el.offsetWidth;
  el.style.transition = TRANSITION;
  progress = 0;
  visible = false;
}

/**
 * 开始加载。重复调用是安全的（不会把已有进度打回从头）
 */
export function start() {
  if (visible || showTimer) {
    return;
  }
  clearTimers();
  progress = 0;
  showTimer = setTimeout(() => {
    showTimer = null;
    visible = true;
    paint(20);
    trickleTimer = setInterval(() => {
      // 越接近上限走得越慢：既像还在加载，又不会假装快好了
      const step = Math.max(1, (MAX_BEFORE_DONE - progress) * 0.15);
      paint(Math.min(progress + step, MAX_BEFORE_DONE));
    }, TRICKLE_INTERVAL_MS);
  }, SHOW_DELAY_MS);
}

/**
 * 加载完成。若整个过程都没超过延迟窗口，进度条不会出现过，此时直接收工
 */
export function done() {
  if (showTimer) {
    // 路由在「还没来得及显示」的窗口内就切完了 —— 这正是延迟显示要的效果
    clearTimers();
    progress = 0;
    return;
  }
  if (!visible) {
    return;
  }
  clearInterval(trickleTimer);
  trickleTimer = null;

  paint(100);
  fadeTimer = setTimeout(() => {
    const el = ensureBar();
    el.style.opacity = '0';
    fadeTimer = setTimeout(reset, FADE_MS);
  }, HOLD_BEFORE_FADE_MS);
}

/**
 * 默认导出保持 { start, done } 的形状，调用方写法与原来一致
 */
export default { start, done };
