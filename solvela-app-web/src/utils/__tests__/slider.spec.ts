import { describe, expect, it } from 'vitest'

import { clampOffset, toImageX } from '../slider'

/** 换算错了的表现是「永远拖不对」，而用户只会以为是自己手笨 */
describe('滑块坐标换算', () => {
  it('偏移限制在轨道内：不能拖出左边，也不能让滑块越过右边', () => {
    expect(clampOffset(-20, 300, 52)).toBe(0)
    expect(clampOffset(120, 300, 52)).toBe(120)
    expect(clampOffset(999, 300, 52)).toBe(248)
  })

  it('显示偏移按缩放比换算回原图坐标 —— 手机上图缩小了，答案没变', () => {
    // 原图 300 宽，显示成 240 宽：显示上拖了 80px，原图里是 100
    expect(toImageX(80, 240, 300)).toBe(100)
    expect(toImageX(80, 300, 300)).toBe(80)
  })

  it('还没量到宽度时不除以 0', () => {
    expect(toImageX(80, 0, 300)).toBe(0)
  })
})
