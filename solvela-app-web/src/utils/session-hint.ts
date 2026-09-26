/**
 * 「这个浏览器上次是登着的」—— 一个**提示**，不是凭证。
 *
 * <h3>为什么需要它</h3>
 * 会话令牌在 HttpOnly cookie 里，页面脚本读不到，也就没法同步地回答「现在登着吗」。
 * 唯一可靠的办法是问服务端（`/auth/me`）。但每次启动都问的话，
 * **每一个匿名访客**打开页面都会先吃一个 401 —— 白白一次请求，控制台一行红字。
 *
 * 所以登录时记一笔「可能登着」，启动时只有看到它才去问；问回来 401 就把它擦掉。
 *
 * <h3>🔴 它不能被当成「已登录」</h3>
 * 它只决定「要不要去问」，不决定「是不是登着」。它可能是过期的（cookie 过期了、
 * 在别处被下线了、会话 cookie 随浏览器关闭没了），也可能被人手动写进去 ——
 * 这些情况下问一次服务端就纠正了。它里面没有任何秘密，被读走、被伪造都无所谓。
 */

const HINT_KEY = 'solvela.app.session.hint'

function safe<T>(fn: () => T, fallback: T): T {
  try {
    return fn()
  } catch {
    return fallback
  }
}

export function hasSessionHint(): boolean {
  return safe(() => localStorage.getItem(HINT_KEY) === '1', false)
}

export function setSessionHint(): void {
  safe(() => localStorage.setItem(HINT_KEY, '1'), undefined)
}

export function clearSessionHint(): void {
  safe(() => localStorage.removeItem(HINT_KEY), undefined)
}
