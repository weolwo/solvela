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
