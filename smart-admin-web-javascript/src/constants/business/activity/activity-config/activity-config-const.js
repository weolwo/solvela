/**
 * 活动配置 枚举
 *
 * @Author:    weolwo
 * @Date:      2026-04-18 19:31:49
 * @Copyright  weolwo
 */

/**
 * 活动类型：与后端 net.lab1024.sa.enums.ActivityTypeEnum 一一对应。
 *
 * ⚠️ 路由、组件、图标、文案全部收在这一处（铁律 3：消除魔法值）。
 * 原型里曾把它散在 getRoutePath / getTypeIcon / getTypeName / getComponentName 四个函数里，
 * 结果其中 LOTTERY 的路由与组件名两个都写错了还没人发现 —— 散开就是错一个都不知道的根源。
 *
 * BASIC 的 component 为 null，这就是「向导没有第二步」的唯一判据。
 * 不要在向导里另写 if (type === 'BASIC') 的散落判断。
 */
export const ACTIVITY_TYPE_ENUM = {
  BASIC: {
    value: 'BASIC',
    desc: '基础活动',
    icon: '📋',
    color: 'default',
    hint: '仅活动外壳，不挂玩法',
    // 无玩法引擎：向导跳过第二步，完备度恒为「无需配置」
    component: null,
    route: null,
  },
  DRAW: {
    value: 'DRAW',
    desc: '奖池抽奖',
    icon: '🎡',
    color: 'orange',
    hint: '转盘/九宫格/盲盒',
    component: 'DrawWorkbench',
    route: '/business/draw/draw-workbench',
  },
  TASK: {
    value: 'TASK',
    desc: '任务驱动',
    icon: '🎯',
    color: 'purple',
    hint: '签到/浏览/分享',
    component: 'TaskWizard',
    route: '/business/task/task-wizard',
  },
  LOTTERY: {
    value: 'LOTTERY',
    desc: 'FPE彩票',
    icon: '🎟️',
    color: 'cyan',
    hint: '加密发号/周期开奖',
    component: 'LotteryWorkbench',
    route: '/business/lottery/lottery-workbench',
  },
};

/** 全部类型（用于卡片选择、筛选下拉） */
export const ACTIVITY_TYPE_LIST = Object.values(ACTIVITY_TYPE_ENUM);

/** 挂玩法的类型（用于「升级玩法」候选项），由 component 推导而非另写一份 */
export const PLAYABLE_TYPE_LIST = ACTIVITY_TYPE_LIST.filter((t) => t.component !== null);

/** 查询下拉用 */
export const ACTIVITY_TYPE_OPTIONS = ACTIVITY_TYPE_LIST.map((t) => ({ value: t.value, label: t.desc }));

/** 该类型是否需要走向导第二步 */
export function hasGameplay(type) {
  return Boolean(ACTIVITY_TYPE_ENUM[type]?.component);
}

/** 取类型元信息；未知类型给兜底，避免一行脏数据把整页渲染炸掉 */
export function activityTypeMeta(type) {
  return (
    ACTIVITY_TYPE_ENUM[type] || {
      value: type,
      desc: type || '未知',
      icon: '❓',
      color: 'default',
      hint: '',
      component: null,
      route: null,
    }
  );
}

/**
 * 活动状态：对齐 t_activity_config.status。
 *
 * ⚠️ **管理端只有「启用 / 禁用」两态**。
 * 库里还存在历史值 0（原「未开始」），但「活动有没有开始」是<b>业务层按起止时间实时算的</b>，
 * 不是运营在后台拨的开关 —— 管理端再保留一个 0 态，等于凭空多出第三种管理状态，
 * 还会和业务层算出来的结果打架。
 * 故 0 一律按「未启用」展示（开关关闭），运营打开即变 1，此后只在 1↔2 之间切换。
 */
export const ACTIVITY_STATUS_ENUM = {
  ENABLED: { value: 1, desc: '启用', badge: 'processing' },
  DISABLED: { value: 2, desc: '禁用', badge: 'default' },
};

export const ACTIVITY_STATUS_LIST = Object.values(ACTIVITY_STATUS_ENUM);

/** 查询下拉用。只给 1/2 两档 —— 0 不是运营可选的状态，见上面的说明 */
export const ACTIVITY_STATUS_OPTIONS = ACTIVITY_STATUS_LIST.map((i) => ({ value: i.value, label: i.desc }));

/** 是否处于启用态。只有 1 算启用；0（历史未开始）与 2（禁用）都算未启用 */
export function isActivityEnabled(status) {
  return status === ACTIVITY_STATUS_ENUM.ENABLED.value;
}

export function activityStatusMeta(status) {
  return isActivityEnabled(status) ? ACTIVITY_STATUS_ENUM.ENABLED : ACTIVITY_STATUS_ENUM.DISABLED;
}

export default {
  ACTIVITY_TYPE_ENUM,
  ACTIVITY_STATUS_ENUM,
};
