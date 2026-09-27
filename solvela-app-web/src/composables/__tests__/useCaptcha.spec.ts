import { describe, expect, it } from 'vitest'

import { finishCaptcha, requestCaptcha, useCaptchaState } from '../useCaptcha'

describe('useCaptcha', () => {
  it('唤起即打开，结束即关闭，并把通行票交给等待者', async () => {
    const { open } = useCaptchaState()
    const pending = requestCaptcha()
    expect(open.value).toBe(true)

    finishCaptcha('pass-1')
    expect(open.value).toBe(false)
    await expect(pending).resolves.toBe('pass-1')
  })

  it('取消 → 等待者拿到 null', async () => {
    const pending = requestCaptcha()
    finishCaptcha(null)
    await expect(pending).resolves.toBeNull()
  })

  it('🔴 新的等待者进来时，旧的按「放弃」结束 —— 不能让它永远悬着', async () => {
    const first = requestCaptcha()
    const second = requestCaptcha()
    await expect(first).resolves.toBeNull()
    finishCaptcha('pass-2')
    await expect(second).resolves.toBe('pass-2')
  })
})
