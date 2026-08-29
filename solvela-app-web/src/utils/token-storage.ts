/**
 * 令牌本地存储。
 *
 * solvela-app 的令牌有效期是 30 天（solvela.app.auth.token-ttl），
 * 所以用 localStorage 而不是 sessionStorage —— C 端用户不接受每次开页面都登录。
 *
 * 同时存过期时间戳：后端在 LoginResult 里给了 expiresIn 就是为了让客户端**提前**处理，
 * 而不是等某次请求 401 了才反应过来。
 */

const TOKEN_KEY = 'solvela.app.token'
const EXPIRES_AT_KEY = 'solvela.app.token.expiresAt'

/** 提前多久就认为令牌不可用，避开临界点上正好过期 */
const EXPIRY_SKEW_MS = 60_000

export interface StoredToken {
  token: string
  expiresAt: number
}

function safeGet(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    // 隐私模式 / 禁用站点数据时 localStorage 会直接抛，不能让它掀翻整个应用
    return null
  }
}

function safeSet(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 存不下就只在内存里活一次会话，不影响本次使用
  }
}

function safeRemove(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // 同上
  }
}

export function readToken(): StoredToken | null {
  const token = safeGet(TOKEN_KEY)
  const expiresAtRaw = safeGet(EXPIRES_AT_KEY)
  if (token === null || token === '' || expiresAtRaw === null) {
    return null
  }
  const expiresAt = Number(expiresAtRaw)
  if (!Number.isFinite(expiresAt) || expiresAt - EXPIRY_SKEW_MS <= Date.now()) {
    clearToken()
    return null
  }
  return { token, expiresAt }
}

export function writeToken(token: string, expiresInSeconds: number): StoredToken {
  const expiresAt = Date.now() + expiresInSeconds * 1000
  safeSet(TOKEN_KEY, token)
  safeSet(EXPIRES_AT_KEY, String(expiresAt))
  return { token, expiresAt }
}

export function clearToken(): void {
  safeRemove(TOKEN_KEY)
  safeRemove(EXPIRES_AT_KEY)
}
