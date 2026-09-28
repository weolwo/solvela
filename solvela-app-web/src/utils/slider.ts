/**
 * 滑块验证码的坐标换算。拆成纯函数是为了能单测 —— 换算错了的表现是「永远拖不对」，
 * 而用户只会以为是自己手笨。
 *
 * 两个坐标系：
 *   显示坐标：屏幕上的 px，图按容器宽度等比缩放；
 *   原图坐标：服务端出图的像素（300 宽），答案按它存。
 */

/** 把拖动的显示偏移限制在轨道内：最左 0，最右让滑块刚好贴着右边 */
export function clampOffset(offsetPx: number, trackWidth: number, handleWidth: number): number {
  const max = Math.max(0, trackWidth - handleWidth)
  return Math.min(Math.max(0, offsetPx), max)
}

/** 显示偏移 → 原图 x。提交给服务端的是这个 */
export function toImageX(offsetPx: number, displayWidth: number, imageWidth: number): number {
  if (displayWidth <= 0) {
    return 0
  }
  return (offsetPx * imageWidth) / displayWidth
}

/**
 * 拖动轨迹的一个点：[距按下的毫秒数, 原图 x, 相对按下时的纵向偏移 px]。
 * 服务端（TrackChecker）拿它判断这一下是不是人手拖的：有没有加减速、y 有没有抖、是不是一步瞬移到位。
 */
export type TrackPoint = [number, number, number]

/** 最多收这么多点。服务端上限 400，这里留足余量 */
export const TRACK_MAX_POINTS = 300

/** 两点间隔不足这么多毫秒就丢掉（抽稀）：高刷屏一秒能来 120 个 pointermove */
export const TRACK_MIN_GAP_MS = 16

/** 拖动中追加一个点：太密的丢掉，满了不再收 */
export function pushTrackPoint(track: TrackPoint[], point: TrackPoint): void {
  const last = track[track.length - 1]
  if (
    track.length >= TRACK_MAX_POINTS ||
    (last !== undefined && point[0] - last[0] < TRACK_MIN_GAP_MS)
  ) {
    return
  }
  track.push(point)
}

/**
 * 松手时补上终点：不受抽稀和上限限制 —— 服务端要核对「轨迹终点 = 提交的 x」，
 * 终点被抽掉的表现是拖对了也过不去。
 */
export function endTrack(track: TrackPoint[], point: TrackPoint): void {
  const last = track[track.length - 1]
  if (last !== undefined && last[0] === point[0]) {
    track[track.length - 1] = point
  } else {
    track.push(point)
  }
}
