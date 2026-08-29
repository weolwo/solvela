import { describe, expect, it } from 'vitest'

import { toApiError } from '@/api/errors'

describe('toApiError', () => {
  it('识别后端的 ApiErrorResponse 并保留 traceId', () => {
    const error = toApiError(
      401,
      { code: 'BAD_CREDENTIALS', message: '手机号或密码错误', traceId: 'c4e01b18' },
      '兜底',
    )
    expect(error.code).toBe('BAD_CREDENTIALS')
    expect(error.message).toBe('手机号或密码错误')
    expect(error.traceId).toBe('c4e01b18')
    expect(error.status).toBe(401)
  })

  it('BAD_CREDENTIALS 虽然是 401，但不算掉登录态', () => {
    const error = toApiError(401, { code: 'BAD_CREDENTIALS', message: 'x', traceId: 't' }, '兜底')
    expect(error.isLoginRequired).toBe(false)
  })

  it('LOGIN_REQUIRED 才算掉登录态', () => {
    const error = toApiError(
      401,
      { code: 'LOGIN_REQUIRED', message: '请先登录', traceId: 't' },
      '兜底',
    )
    expect(error.isLoginRequired).toBe(true)
  })

  it('未知错误码落到 INTERNAL，但保留后端原始 message', () => {
    const error = toApiError(
      400,
      { code: 'SOME_NEW_CODE', message: '新加的错误', traceId: 't' },
      '兜底',
    )
    expect(error.code).toBe('INTERNAL')
    expect(error.message).toBe('新加的错误')
  })

  it('没有响应体时按网络错误处理', () => {
    const error = toApiError(null, undefined, '网络连接失败')
    expect(error.code).toBe('NETWORK')
    expect(error.message).toBe('网络连接失败')
  })
})
