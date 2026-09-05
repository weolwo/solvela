import { type Id, type Raw, toId } from '@/types/contract'

import { request } from './http'

/**
 * 任务中心。对应 <b>marketing 域的 task 玩法</b>，2026-09-05 已接通真实接口。
 *
 * <h3>🔴 没有「领取」这一步</h3>
 * 后端的 `TaskRecordStatusEnum` 是 RUNNING / COMPLETED / <b>DISPATCHED</b> / EXPIRED ——
 * 没有 CLAIMED。任务达标时由 `TaskPrizeDispatcher.dispatchReachedStages` <b>自动发奖</b>，
 * 用户不需要（也不能）点一下领。
 *
 * <p>所以<b>不要画「领取」按钮</b>：点了什么都不会发生，而奖励其实早就到账了，
 * 用户反而会以为自己没领到。第一版前端凭空造了这个按钮和 `claimTask()`，
 * 接通后端时才发现和设计对不上，已经删掉。
 *
 * <p>要改成「达标后手动领」是<b>产品决策</b>：得先在任务运行态加一个状态、
 * 加一个幂等的领取接口，不是前端加个按钮的事。
 *
 * <h3>任务挂在活动下，但 C 端不分活动</h3>
 * 任务配置属于某个活动（`t_task_config.activity_code`），而 C 端的任务中心是一个
 * 独立 tab —— 用户不关心「这个任务属于哪场活动」。所以后端在<b>进程内</b>把
 * 各活动的任务合成一份，前端只打一个 `/task`。
 */

/** 任务中心里的一条 */
/**
 * 任务的一个档位。
 *
 * 🔴 阶梯任务的价值全在这里：「签到 1 天得 188 积分、连签 5 天再得 8 元」是两件事。
 * 旧版把各档奖励名用「/」拼成一句 rewardText，用户看不出哪个奖对应哪一档，
 * 也看不出自己已经拿到了第一档。
 */
export interface TaskStage {
  /** 达标阈值。十进制字符串——金额型任务的阈值是小数 */
  target: string
  rewardText: string
  /** 本周期内已达标 */
  reached: boolean
}

export interface TaskItem {
  taskId: Id
  taskName: string
  /** 分组，运营配的。为空表示不分组 */
  taskGroup: string | null
  /** 目标值。**金额型任务是小数**，所以是字符串，走 Decimal 展示 */
  target: string
  /** 当前进度。没有记录（还没开始做）时是 '0' */
  current: string
  /**
   * 给用户看的一句话：未开始 / 进行中 / 已完成 / 已发奖 / 已过期。
   * <b>由后端给</b> —— 前端做映射表就是第二份状态机，域里加一个状态时它会静默变错。
   */
  statusText: string
  /** 是否已经拿到奖励。只用来选样式，不直接展示 */
  finished: boolean
  /**
   * 一行奖励摘要，如「+10 积分」。<b>只有单档任务才有</b>，多档为 null。
   *
   * 🔴 多档不再拼成「A / B」：那样用户看不出哪个奖对应哪一档，
   * 也看不出自己已经拿到了第一档。多档看 {@link TaskItem.stages}。
   */
  rewardText: string | null
  /** 档位，按阈值升序。单档任务也有一条 */
  stages: TaskStage[]
  /** 规则一句话，如「连续完成 5 次，中断即清零」。<b>由后端拼</b>，前端不认识 taskType */
  ruleText: string
  /** 周期：每日 / 每周 / 仅一次 / 不限 */
  periodText: string
  /** 截止提示，如「9月30日 23:59」。不限时为 null，不画那一行 */
  deadlineText: string | null
  /** 「去完成」跳哪。为空表示没有跳转入口（比如「每日登录」，用户已经在里面了） */
  actionUrl: string | null
}

/**
 * 我的全部任务：当前可见的任务型活动下的任务，<b>后端已经合成一份</b>。
 *
 * <p>聚合在营销服务进程内做，前端不按活动循环请求 —— 那是跨进程的 N+1。
 * 返回全部生效中的任务，没做过的也给：任务中心要回答的是「还有什么可做」。
 * 没有任务型活动时返回空数组，不是 404。
 */
/** 反序列化边界：Long 小值下发为数字，在这里归一成字符串。见 types/contract.ts 的 Raw */
function normalize(raw: Raw<TaskItem>): TaskItem {
  return { ...raw, taskId: toId(raw.taskId) }
}

export function fetchTasks(): Promise<TaskItem[]> {
  return request<Raw<TaskItem>[]>({ url: '/task' }).then((list) => list.map(normalize))
}
