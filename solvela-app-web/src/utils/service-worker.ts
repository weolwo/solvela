/**
 * 注册 service worker（`public/sw.js`）。它做什么、为什么不缓存 index.html，见那个文件的文件头。
 *
 * <p>只在生产构建里注册：开发时 Vite 的模块是按请求现编的，被 SW 缓存住就是「改了代码页面不变」。
 * 本地要试 SW 用 `npm run build-only && npm run preview`（localhost 不要求 HTTPS）。
 *
 * <p>等到 load 之后再注册：注册会去下载并启动 sw.js，不跟首屏抢带宽。
 * 注册失败（隐私模式、浏览器禁用）只是少一层加速，页面照常工作，所以不报错。
 */
export function registerServiceWorker(): void {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) {
    return
  }
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => undefined)
  })
}
