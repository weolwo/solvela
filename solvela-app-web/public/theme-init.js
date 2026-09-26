/*
 * 在 Vue 加载【之前】把主题打到 <html> 上，避免深色模式用户每次打开先看到一下白屏。
 *
 * 由 index.html 在 <head> 里【同步】加载（没有 defer / async / type=module）：
 * 同步脚本会阻塞渲染，所以它跑完之前浏览器不会画出任何东西 —— 那一闪就没了。
 *
 * 🔴 为什么是外部文件而不是内联在 index.html 里：
 * nginx 下发的 CSP 是 `script-src 'self'`，不允许内联脚本 —— 那正是它挡 XSS 的方式
 * （混进页面的 <script> 与 onerror= 一律不执行）。为这一段开 'unsafe-inline' 等于把整道防线拆掉；
 * 写哈希又会在这段代码每次改动时静默失效。放成同源文件，'self' 就放行了。
 *
 * 它和 stores/theme.ts 读的是同两个 key，那边是唯一真源，这里只是把结果提前几百毫秒。
 * 不经过 Vite 打包（public/ 原样拷贝），所以只能写 ES5、不能 import。
 */
;(function () {
  try {
    var appearance = localStorage.getItem('solvela.app.appearance') || 'system'
    var skin = localStorage.getItem('solvela.app.skin') || 'default'
    if (appearance === 'system') {
      appearance = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    }
    var root = document.documentElement
    root.dataset.appearance = appearance
    root.dataset.skin = skin
    root.style.colorScheme = appearance
  } catch (e) {
    // 隐私模式下 localStorage 会抛。什么都不做 —— CSS 的默认值就是浅色
  }
})()
