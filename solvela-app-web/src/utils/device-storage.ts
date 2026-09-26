/**
 * 旧版本的设备令牌本地存储 —— **现在只剩迁移用途**。
 *
 * 2026-09-26 起设备令牌改由服务端写进 HttpOnly cookie（见 api/device.ts）。
 * 存在 localStorage 的问题：混进页面的脚本能把它读走，冒充一台「老设备」——
 * 而资产出口二次验证的「受信任设备」正是按它判的。
 *
 * 升级后第一次启动，api/device.ts 会读出这里残留的旧令牌、放进请求头交给服务端，
 * 服务端把**同一个**令牌写进 cookie（设备号不断档），然后清掉本地这份。
 *
 * 🔴 **不要往回加 writeDevice**。等存量客户端都升级过（残留读不到了）之后删掉本文件。
 *
 * <h3>迁移之后，这几条规矩由服务端的 cookie 继续保证</h3>
 * <ul>
 *   <li>永远持久化 —— cookie 带 Max-Age（400 天，每次启动续期）；</li>
 *   <li>退出登录不清 —— 退出只清会话 cookie，设备 cookie 不动。
 *       「换个号登录 = 换一台机器」正是刷子最想要的效果；</li>
 *   <li>客户端不判过期 —— 令牌里的时间戳由服务端解释。</li>
 * </ul>
 */

const DEVICE_TOKEN_KEY = 'solvela.app.device.token'
const DEVICE_ID_KEY = 'solvela.app.device.id'

export interface StoredDevice {
  /** 旧版本放进 X-Device-Token 头的那个串，形如 dv_1.xxx.yyy */
  token: string
  /** 32 位 hex */
  deviceId: string
}

/**
 * 隐私模式 / 禁用站点数据时，访问 storage 会直接抛（不是返回 null）。
 * 不能让它掀翻整个应用。
 */
function safe<T>(fn: () => T, fallback: T): T {
  try {
    return fn()
  } catch {
    return fallback
  }
}

/** 读出旧版本残留的设备令牌；没有返回 null */
export function readDevice(): StoredDevice | null {
  const token = safe(() => localStorage.getItem(DEVICE_TOKEN_KEY), null)
  const deviceId = safe(() => localStorage.getItem(DEVICE_ID_KEY), null)
  if (token === null || token === '' || deviceId === null || deviceId === '') {
    return null
  }
  return { token, deviceId }
}

/** 迁移成功后清掉本地残留 */
export function clearDevice(): void {
  safe(() => localStorage.removeItem(DEVICE_TOKEN_KEY), undefined)
  safe(() => localStorage.removeItem(DEVICE_ID_KEY), undefined)
}
