/**
 * 旧版本的令牌本地存储 —— **现在只剩迁移用途**。
 *
 * 2026-09-26 起会话令牌改由服务端写进 HttpOnly cookie，前端不再保存、也读不到它。
 * 以前存在这里的问题很具体：混进页面的任何一段脚本都能 `localStorage.getItem` 把它读走，
 * 带回自己的机器用满 30 天。原理见 docs/知识库/Web鉴权-Cookie与浏览器安全边界.md。
 *
 * 升级后的第一次启动，stores/auth 会读出这里残留的旧令牌、交给服务端写进 cookie
 * （/auth/session/adopt，同一个令牌，用户不掉线），然后清掉本地这份。
 *
 * 🔴 等最后一批旧令牌过期（2026-10-26 之后），连同 adopt 接口一起删掉本文件。
 * **不要往回加 writeToken**：往 localStorage 写令牌，就是这次改造要消灭的东西。
 */

const TOKEN_KEY = 'solvela.app.token'
const EXPIRES_AT_KEY = 'solvela.app.token.expiresAt'

/** 提前多久就认为令牌不可用，避开临界点上正好过期 */
const EXPIRY_SKEW_MS = 60_000

export interface LegacyToken {
  token: string
  expiresAt: number
  /**
   * 旧令牌存在 localStorage（用户勾了「记住我」）还是 sessionStorage。
   * 迁移时据此决定下发持久 cookie 还是会话 cookie —— 让「关浏览器会不会掉线」在升级前后一致。
   */
  persisted: boolean
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

function stores(): Array<{ store: Storage; persisted: boolean }> {
  return safe(
    () => [
      { store: localStorage, persisted: true },
      { store: sessionStorage, persisted: false },
    ],
    [],
  )
}

/** 读出旧版本残留的令牌；没有或已过期返回 null（过期的顺手清掉） */
export function readToken(): LegacyToken | null {
  for (const { store, persisted } of stores()) {
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
    return { token, expiresAt, persisted }
  }
  return null
}

export function clearToken(): void {
  for (const { store } of stores()) {
    safe(() => store.removeItem(TOKEN_KEY), undefined)
    safe(() => store.removeItem(EXPIRES_AT_KEY), undefined)
  }
}
