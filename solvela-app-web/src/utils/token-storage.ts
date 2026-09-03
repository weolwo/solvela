/**
 * 令牌本地存储。
 *
 * solvela-app 的令牌有效期是 30 天（solvela.app.auth.token-ttl）。
 *
 * 同时存过期时间戳：后端在 LoginResult 里给了 expiresIn 就是为了让客户端**提前**处理，
 * 而不是等某次请求 401 了才反应过来。
 *
 * <h3>「记住我」落在这里，而且是真的</h3>
 * 勾选 = localStorage（关掉浏览器还在，下次直接进）；
 * 不勾 = sessionStorage（标签页一关就没了，共用设备上不留痕）。
 *
 * 🔴 一个不改变任何行为的「记住我」勾选框比没有更糟 —— 用户以为自己在共用设备上
 * 关掉了持久登录，实际没有。所以要么按这个语义实现，要么把勾选框拿掉。
 */

const TOKEN_KEY = 'solvela.app.token'
const EXPIRES_AT_KEY = 'solvela.app.token.expiresAt'

/** 提前多久就认为令牌不可用，避开临界点上正好过期 */
const EXPIRY_SKEW_MS = 60_000

export interface StoredToken {
  token: string
  expiresAt: number
}

/**
 * 隐私模式 / 禁用站点数据时，访问 storage 会直接抛（不是返回 null）。
 * 不能让它掀翻整个应用，所以每一次读写都包起来。
 */
function safe<T>(fn: () => T, fallback: T): T {
  try {
    return fn()
  } catch {
    return fallback
  }
}

/** 两个 storage 都要查：用户上次可能勾了、也可能没勾 */
function stores(): Storage[] {
  return safe(() => [localStorage, sessionStorage], [])
}

export function readToken(): StoredToken | null {
  for (const store of stores()) {
    const token = safe(() => store.getItem(TOKEN_KEY), null)
    const expiresAtRaw = safe(() => store.getItem(EXPIRES_AT_KEY), null)
    if (token === null || token === '' || expiresAtRaw === null) {
      continue
    }
    const expiresAt = Number(expiresAtRaw)
    if (!Number.isFinite(expiresAt) || expiresAt - EXPIRY_SKEW_MS <= Date.now()) {
      clearToken()
      return null
    }
    return { token, expiresAt }
  }
  return null
}

/**
 * @param persist true=记住我（localStorage）；false=只在本次会话（sessionStorage）
 */
export function writeToken(token: string, expiresInSeconds: number, persist: boolean): StoredToken {
  const expiresAt = Date.now() + expiresInSeconds * 1000
  // 🔴 先清两边再写：从「记住」改成「不记住」时，localStorage 里那份必须消失，
  //    否则用户取消了勾选，持久令牌却还留着 —— 正是这个开关要防的事
  clearToken()
  const store = persist ? safe(() => localStorage, null) : safe(() => sessionStorage, null)
  if (store !== null) {
    safe(() => store.setItem(TOKEN_KEY, token), undefined)
    safe(() => store.setItem(EXPIRES_AT_KEY, String(expiresAt)), undefined)
  }
  return { token, expiresAt }
}

export function clearToken(): void {
  for (const store of stores()) {
    safe(() => store.removeItem(TOKEN_KEY), undefined)
    safe(() => store.removeItem(EXPIRES_AT_KEY), undefined)
  }
}
