import { describe, expect, it } from 'vitest'

import {
  clampOffset,
  endTrack,
  pushTrackPoint,
  toImageX,
  TRACK_MAX_POINTS,
  type TrackPoint,
} from '../slider'

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

describe('拖动轨迹', () => {
  it('太密的点抽掉：高刷屏一秒上百个 pointermove，没必要全交', () => {
    const track: TrackPoint[] = [[0, 0, 0]]
    pushTrackPoint(track, [5, 2, 0])
    pushTrackPoint(track, [16, 4, 1])
    expect(track).toEqual([
      [0, 0, 0],
      [16, 4, 1],
    ])
  })

  it('满了不再收 —— 服务端对点数有上限', () => {
    const track: TrackPoint[] = []
    for (let i = 0; i < TRACK_MAX_POINTS + 50; i++) {
      pushTrackPoint(track, [i * 20, i, 0])
    }
    expect(track).toHaveLength(TRACK_MAX_POINTS)
  })

  it('🔴 终点一定在：不受抽稀和上限影响，否则服务端核对终点时拖对了也过不去', () => {
    const track: TrackPoint[] = [[0, 0, 0]]
    endTrack(track, [3, 137, 1])
    expect(track[track.length - 1]).toEqual([3, 137, 1])

    const same: TrackPoint[] = [[100, 120, 0]]
    endTrack(same, [100, 137, 1])
    expect(same).toEqual([[100, 137, 1]])
  })
})
