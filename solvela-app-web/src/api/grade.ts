import { request } from './http'

/**
 * 我的会员等级。
 *
 * <h3>这一页存在的理由</h3>
 * 保级缓冲期这套机制在此之前对用户是**完全不可见**的：他的等级到期了、
 * 系统给了他三个月宽限、这三个月里成长值还是双倍的 —— 而他什么都不知道。
 * 一个用户感知不到的挽留机制等于没做。
 *
 * <h3>🔴 全是只读</h3>
 * 等级是**派生状态**，跟着成长值走，而成长值只从业务动作来。
 * 契约里一个写方法都没有，是刻意的 —— 给 C 端开写口等于给刷等级开门。
 */

/** 一条权益。**纯展示** —— 真正的权益靠任务人群、脚本、商城价格实现 */
export interface GradePrivilege {
  privilegeCode: string
  privilegeName: string
  description: string | null
  iconFileId: number | null
  /** 为空表示纯展示、不可点 */
  actionUrl: string | null
}

/** 阶梯上的一档 */
export interface GradeLadderItem {
  gradeCode: number
  gradeName: string
  threshold: number
  /** 成长值够不够这一档 */
  reached: boolean
  /**
   * 是不是用户此刻**所在**的那一档。
   *
   * 🔴 与 `reached` 不是一回事：保级缓冲期内他**在**白金，但成长值**够不着**白金。
   * 页面上这两个状态要分别渲染，混成一个就说不清「你正在保的是什么」。
   */
  current: boolean
  privileges: GradePrivilege[]
}

/** 等级页要的全部 */
export interface MyGrade {
  gradeCode: number
  gradeName: string
  /** 没参与过的会员为 null */
  gradeSince: string | null
  currentValue: number
  totalValue: number
  /** 没参与过的会员为 null —— 他还没有周期 */
  periodEnd: string | null
  /** 已是最高档为 null */
  nextGradeCode: number | null
  nextGradeName: string | null
  nextThreshold: number | null
  gapToNext: number | null
  /** 正在保级缓冲期 */
  inProtect: boolean
  protectUntil: string | null
  protectGrade: number | null
  /** 保住还差多少；已达标是 0 */
  protectGap: number | null
  /** 当前成长值倍率：缓冲期是 2，平时是 1 */
  boostMultiplier: number
  ladder: GradeLadderItem[]
}

/** 一条成长值明细 */
export interface GrowthLogItem {
  /** 已乘倍率 */
  delta: number
  /** 倍率之前的基数 */
  baseValue: number
  multiplier: number
  remark: string | null
  createTime: string
}

/**
 * 我的等级页。
 *
 * ⚠️ 没参与过的会员**也有返回**（0 级 + 完整阶梯），不是 404 ——
 * 「你还没开始」和「查不到」对用户是两件事。
 */
export function fetchMyGrade(): Promise<MyGrade> {
  return request<MyGrade>({ url: '/grade' })
}

/**
 * 我的成长值明细。
 *
 * 倍率与基数一起给：缓冲期看到「+200」而自己只做了一件值 100 的事时，
 * 用户第一反应是系统算错了。把「100 × 2」摆出来，那条加速规则才真的被感知到。
 */
export function fetchGrowthLog(limit = 20): Promise<GrowthLogItem[]> {
  return request<GrowthLogItem[]>({ url: '/grade/growthLog', params: { limit } })
}
