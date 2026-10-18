/*
 * 构建产物换版之后的自愈：旧页面取不到 chunk 时，自动重载一次换成新的 index.html。
 *
 * 为什么需要它：路由组件是懒加载的（router/index.js 里 import.meta.glob），
 * 文件名带内容 hash。一次重新部署会把变过的 chunk 换成新 hash、删掉旧文件 ——
 * 而管理端的页面常常开着一整天不刷新。于是那张老页面点进一个还没访问过的菜单时，
 * 它请求的是【上一版】的文件名，服务端已经没有了。
 *
 * 线上长什么样（2026-10-18，风控那几个页面）：点进去转半天没反应，控制台只有一句
 *   Uncaught (in promise) TypeError: Failed to fetch dynamically imported module:
 *   https://admin.xxx/js/risk-rule-list-<旧 hash>.js
 * 很容易被当成「网络慢」或「接口挂了」，其实只差一次刷新。
 *
 * 🔴 这一层是【兜底】，不是全部。另两处同样重要，缺了这里也救不回来：
 *   1）deploy/nginx/nginx.conf：index.html 必须 no-store，否则刷新拿回来的还是旧入口，
 *      自愈就变成了死循环（下面的冷却窗口兜住了这种情况，但页面仍然打不开）；
 *   2）同一份配置里，缺失的 chunk 要回 404 而不是被 try_files 兜成 index.html。
 *
 * @Author:    alaric
 * @Date:      2026-10-18
 */

import { message } from 'ant-design-vue';
import progressBar from '/@/lib/progress-bar';
import { solvelaSentry } from '/@/lib/solvela-sentry';

/**
 * 上次自愈重载的时间戳存这里。
 *
 * 用 sessionStorage 而不是内存变量：重载会把内存清空，内存变量永远读不到「上次」，
 * 那就挡不住死循环。也不用 localStorage —— 跨标签页共享会让 A 页的重载
 * 把 B 页的冷却窗口也占掉。
 */
const RELOAD_AT_KEY = 'solvela-chunk-reload-at';

/**
 * 冷却窗口：这段时间内只自愈一次。
 *
 * 🔴 没有它就会死循环：如果刷新回来的 index.html 还是旧的（浏览器/CDN 缓存没放手），
 * 新页面会立刻再次取不到 chunk、再次重载 —— 一个停不下来的白屏。
 * 宁可停下来提示用户，也不要让页面自己转圈。
 */
const RELOAD_COOLDOWN_MS = 10 * 1000;

/**
 * 判断一个错误是不是「chunk 取不到」。
 *
 * ⚠️ 只能认文案，没有错误码可认：各浏览器措辞不同，所以这里是一组关键字 ——
 *   Chrome  Failed to fetch dynamically imported module
 *   Firefox error loading dynamically imported module
 *   Safari  Importing a module script failed
 * 另外 vite 预载 CSS 失败是 Unable to preload CSS，同一类问题，一并认。
 */
export function isChunkLoadError(error) {
  const text = typeof error === 'string' ? error : error?.message || '';
  if (!text) {
    return false;
  }
  return /dynamically imported module|importing a module script failed|unable to preload css|failed to fetch dynamically/i.test(text);
}

/**
 * 真正的自愈动作：冷却窗口内只做一次。
 */
function reloadOnce(error) {
  solvelaSentry.captureError(error);

  const lastAt = Number(sessionStorage.getItem(RELOAD_AT_KEY)) || 0;
  if (Date.now() - lastAt < RELOAD_COOLDOWN_MS) {
    // 刚刷过还是不行 —— 多半是哪一层还在发旧的 index.html，再刷也没用。
    // 路由已经失败，afterEach 不会再执行，进度条得自己收掉，否则顶上一直在转。
    progressBar.done();
    message.error('页面资源已更新但未能自动刷新，请手动强制刷新（Ctrl+Shift+R）');
    return;
  }
  sessionStorage.setItem(RELOAD_AT_KEY, String(Date.now()));

  // hash 路由的 #/xxx 不会丢，刷完还停在用户点的那个页面上。
  window.location.reload();
}

/**
 * 装上自愈。在 createApp 之前调用（main.js），晚于第一次路由跳转就来不及了。
 *
 * 三个入口都要挂，少一个就有漏网的：
 *   · vite:preloadError —— vite 预载 chunk 失败时在 window 上发；不 preventDefault
 *     的话它还会往外抛一次，控制台多一条没人能用的报错。
 *   · router.onError    —— 路由自己 await 组件时失败走这里。
 *   · 全局 unhandledrejection —— beforeEach 里那次「提前执行 component() 改组件名」
 *     是裸着的 Promise，失败只会变成一条 Uncaught (in promise)，上面两个都接不到。
 */
export function installChunkReload(router) {
  window.addEventListener('vite:preloadError', (event) => {
    event.preventDefault();
    reloadOnce(event.payload || event);
  });

  router.onError((error) => {
    if (isChunkLoadError(error)) {
      reloadOnce(error);
    }
  });

  window.addEventListener('unhandledrejection', (event) => {
    if (isChunkLoadError(event.reason)) {
      event.preventDefault();
      reloadOnce(event.reason);
    }
  });
}
