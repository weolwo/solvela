import { clearDevice, readDevice } from '@/utils/device-storage'

import { type DeviceType } from './auth'
import { DEVICE_REGISTER_URL, request } from './http'

/**
 * 设备身份。**每次启动领一次，不是首次登录**。
 *
 * <p>匿名接口（注册、登录、活动页）也要有设备身份 —— 而防刷要防的恰恰是它们。
 * 等到登录才领，等于把最需要保护的那几条路留在外面。
 *
 * <h3>令牌在 HttpOnly cookie 里，这里拿不到、也不需要拿</h3>
 * 服务端把设备令牌写进 HttpOnly cookie，之后每个同源请求由浏览器自动带上。
 * 以前它存在 localStorage：混进页面的脚本能把它读走，冒充一台「老设备」——
 * 而二次验证的「受信任设备」正是按它判的。
 *
 * <h3>每次启动都调，而不是「本地没有才调」</h3>
 * 页面脚本读不到 HttpOnly cookie，没法知道「有没有」。好在服务端现在是幂等的：
 * 请求带着有效的设备 cookie 就复用那一台、只续期，没有才新建。
 * 顺带解决了以前的老问题 —— 多标签页并发时一个人会凭空多出几台设备。
 */

export interface DeviceRegisterPayload {
  deviceType: DeviceType
  /**
   * 品牌型号，如 "iPhone 15 Pro"。
   *
   * 🔴 **H5 不要填**。浏览器里拿得到的只有 userAgent，那不是型号 ——
   * 把一串 UA 塞进 `t_device.model`，那一列就再也没法用来分组统计了
   * （「这批号是不是同一款机器注册的」正是它的用途）。
   * 等原生壳把这个页面包起来，由壳传真值。
   */
  model?: string
  osVersion?: string
  appVersion?: string
}

interface RawDeviceRegisterView {
  /** cookie 模式下服务端不回令牌原文（它已经在 HttpOnly cookie 里了） */
  deviceToken: string | null
  deviceId: string
}

/**
 * 旧版本存在 localStorage 里的设备令牌走这个头交给服务端，服务端把**同一个**令牌写进 cookie。
 * 对齐网关的 `solvela.app.device.header`。
 */
const LEGACY_DEVICE_HEADER = 'X-Device-Token'

/**
 * 领（或续期）设备身份，返回设备号。
 *
 * <h3>迁移：旧令牌只交出去一次</h3>
 * localStorage 里还躺着旧版本领的令牌时，放进请求头交给服务端 —— 服务端认出它、
 * 把同一个令牌写进 cookie，**设备号不断档**（「这台设备碰过哪些号」的历史还在）。
 * 交出去成功之后删掉本地那份：留着它就留着一份脚本读得到的凭证。
 */
export async function registerDevice(payload: DeviceRegisterPayload): Promise<string> {
  const legacy = readDevice()
  const raw = await request<RawDeviceRegisterView>({
    url: DEVICE_REGISTER_URL,
    method: 'POST',
    data: { ...payload, useCookie: true },
    ...(legacy === null ? {} : { headers: { [LEGACY_DEVICE_HEADER]: legacy.token } }),
  })
  if (legacy !== null) {
    clearDevice()
  }
  return raw.deviceId
}

/**
 * 本次页面生命周期里那一次领设备身份。**成功之后不再发请求**。
 *
 * 🔴 没有它的话，冷启动那一瞬间并发的几个请求会各领一次 —— 服务端虽然幂等了，
 * 但第一个请求的 cookie 落地之前，另外几个请求也不带 cookie，照样会各新建一台。
 */
let ensured: Promise<boolean> | null = null

/**
 * 确保这个浏览器有设备身份（cookie 已落地）。
 *
 * <p>**失败返回 false，不抛**。设备身份是一层增强，不是前置条件：
 * 领不到的时候用户照样该能登录、能抽奖。失败也不缓存 —— 下一个请求会再试一次。
 */
export function ensureDevice(deviceType: DeviceType = 'H5'): Promise<boolean> {
  if (ensured === null) {
    ensured = registerDevice({ deviceType })
      .then(() => true)
      .catch(() => {
        ensured = null
        return false
      })
  }
  return ensured
}

/** 仅供测试：清掉「本次已领过」的缓存 */
export function resetDeviceForTest(): void {
  ensured = null
}
