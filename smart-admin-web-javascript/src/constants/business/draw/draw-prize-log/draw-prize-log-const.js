/**
 * 抽奖域-抽奖流水记录表 枚举
 *
 * 取值对齐后端 t_draw_prize_log 的列注释。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 18:36:32
 * @Copyright  weolwo
 */

/**
 * 抽奖结果状态：对齐 t_draw_prize_log.status
 *
 * ⚠️「未中奖」是正常业务结果（抽中了「谢谢参与」兜底），不是失败；
 * 真正需要排查的是「库存不足」与「异常」两档。
 */
export const DRAW_STATUS_ENUM = {
  MISS: { value: 0, desc: '未中奖', color: 'default' },
  WIN: { value: 1, desc: '已中奖', color: 'green' },
  OUT_OF_STOCK: { value: 2, desc: '库存不足', color: 'orange' },
  ERROR: { value: 3, desc: '异常', color: 'red' },
};

export const DRAW_STATUS_OPTIONS = Object.values(DRAW_STATUS_ENUM).map((i) => ({ value: i.value, label: i.desc }));

export function drawStatusOf(value) {
  return Object.values(DRAW_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export default {
  DRAW_STATUS_ENUM,
  DRAW_STATUS_OPTIONS,
  drawStatusOf,
};
