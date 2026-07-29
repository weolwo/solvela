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
 * 活动状态：对齐 t_activity_config.status
 */
export const ACTIVITY_STATUS_ENUM = {
  NOT_START: { value: 0, desc: '未开始', badge: 'default' },
  ONLINE: { value: 1, desc: '已上线', badge: 'processing' },
  OFFLINE: { value: 2, desc: '已下线', badge: 'default' },
};

export const ACTIVITY_STATUS_LIST = Object.values(ACTIVITY_STATUS_ENUM);

export function activityStatusMeta(status) {
  return ACTIVITY_STATUS_LIST.find((s) => s.value === status) || { value: status, desc: '未知', badge: 'default' };
}

export default {
  ACTIVITY_TYPE_ENUM,
  ACTIVITY_STATUS_ENUM,
};
