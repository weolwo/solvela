/**
 * 任务域-任务记录表 枚举
 *
 * 取值对齐后端 t_task_record 的列注释与 TaskConst.RECORD_STATUS_*。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:03:53
 * @Copyright  weolwo
 */

/**
 * 任务记录状态：对齐 t_task_record.status
 *
 * ⚠️「进行中」含一种容易误读的情况：阶梯任务里**低档已发奖、最高档还没达标**，
 * 记录仍然停在 0。看到 0 不等于「一点进展都没有」。
 * 「已发奖」是终态，此后不再接受事件。
 */
export const RECORD_STATUS_ENUM = {
  RUNNING: { value: 0, desc: '进行中', color: 'processing' },
  COMPLETED: { value: 1, desc: '已完成', color: 'blue' },
  DISPATCHED: { value: 2, desc: '已发奖', color: 'green' },
  EXPIRED: { value: 3, desc: '已过期', color: 'default' },
};

export const RECORD_STATUS_OPTIONS = Object.values(RECORD_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

export function recordStatusOf(value) {
  return Object.values(RECORD_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export default {
  RECORD_STATUS_ENUM,
  RECORD_STATUS_OPTIONS,
  recordStatusOf,
};
