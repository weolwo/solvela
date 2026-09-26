import { describe, expect, it } from 'vitest'

import { finishStepUp, requestStepUp, useStepUpState } from '../useStepUp'

describe('useStepUp', () => {
  it('唤起即打开，结束即关闭，并把结果交给等待者', async () => {
    const { open } = useStepUpState()
    const pending = requestStepUp()
    expect(open.value).toBe(true)

    finishStepUp(true)
    expect(open.value).toBe(false)
    await expect(pending).resolves.toBe(true)
  })

  it('🔴 新的等待者进来时，旧的按「放弃」结束 —— 不能让它永远悬着', async () => {
    const first = requestStepUp()
    const second = requestStepUp()

    await expect(first).resolves.toBe(false)
    finishStepUp(true)
    await expect(second).resolves.toBe(true)
  })

  it('没有等待者时结束也不报错（退出登录时会无条件调一次）', () => {
    expect(() => finishStepUp(false)).not.toThrow()
  })
})
