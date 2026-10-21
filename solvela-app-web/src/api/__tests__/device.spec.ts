import type { AxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ensureDevice, registerDevice, resetDeviceForTest } from '../device'

/**
 * 设备身份（HttpOnly cookie 版）。它坏掉的时候**不会有任何报错**。
 *
 * <p>设备号是整套防刷里最便宜、回报最高的一笔，也是资产出口二次验证判「受信任设备」的依据。
 * 它退化的方式全是静默的：页面照常打开，只是 `t_device` 里凭空多出几行、
 * 或者令牌又回到了脚本读得到的地方。
 *
 * <p>这里钉住：**令牌不落本地**、**一次页面生命周期只领一次**。
 */

const request = vi.hoisted(() => vi.fn())

vi.mock('../http', () => ({
  request,
  requestVoid: vi.fn(() => Promise.resolve()),
  DEVICE_REGISTER_URL: '/device/register',
}))

/** cookie 模式下服务端不回令牌原文 */
const REPLY = { deviceToken: null, deviceId: '0123456789abcdef0123456789abcdef' }

function sentConfig(i = 0): AxiosRequestConfig {
  const config = request.mock.calls[i]?.[0] as AxiosRequestConfig | undefined
  expect(config, '压根没发出请求').toBeDefined()
  return config as AxiosRequestConfig
}

beforeEach(() => {
  localStorage.clear()
  resetDeviceForTest()
  request.mockReset()
  request.mockResolvedValue(REPLY)
})

afterEach(() => {
  localStorage.clear()
})

describe('领设备身份', () => {
  it('🔴 要求 cookie 下发，且令牌不落 localStorage —— 落了就回到了脚本读得到的地方', async () => {
    await registerDevice({ deviceType: 'H5' })

    expect((sentConfig().data as Record<string, unknown>).useCookie).toBe(true)
    expect(localStorage.length).toBe(0)
  })

  it('🔴 H5 不上报 model —— 浏览器里那串 UA 不是型号', async () => {
    await registerDevice({ deviceType: 'H5' })

    const body = (sentConfig().data ?? {}) as Record<string, unknown>
    expect(body.deviceType).toBe('H5')
    expect(body.model).toBeUndefined()
  })

  it('不带设备头 —— 凭证只走 cookie', async () => {
    await registerDevice({ deviceType: 'H5' })

    expect(sentConfig().headers).toBeUndefined()
  })
})

describe('ensureDevice', () => {
  it('🔴 一次页面生命周期只领一次 —— 每个请求前都会 await 它', async () => {
    await ensureDevice()
    await ensureDevice()
    await ensureDevice()

    expect(request).toHaveBeenCalledTimes(1)
  })

  it('🔴 并发调用只发一次 —— 第一个 cookie 落地之前，其余请求也不带 cookie，会各新建一台', async () => {
    const [a, b, c] = await Promise.all([ensureDevice(), ensureDevice(), ensureDevice()])

    expect([a, b, c]).toEqual([true, true, true])
    expect(request).toHaveBeenCalledTimes(1)
  })

  it('领不到时返回 false，不抛 —— 设备身份是增强，不是登录的前置条件', async () => {
    request.mockRejectedValue(new Error('network'))

    await expect(ensureDevice()).resolves.toBe(false)
  })

  it('这一次失败不该把后面的也卡死：下一次会再试', async () => {
    request.mockRejectedValueOnce(new Error('network'))
    await ensureDevice()

    request.mockResolvedValueOnce(REPLY)
    await expect(ensureDevice()).resolves.toBe(true)
    expect(request).toHaveBeenCalledTimes(2)
  })
})
