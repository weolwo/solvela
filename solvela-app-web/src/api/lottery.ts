import { request } from './http'

/**
 * 彩票。
 *
 * <h3>🔴 这条链路此前整个不存在</h3>
 * 后端的 FPE 算号引擎、期号、号码池、中奖规则、管理端配置页、开奖与核销全都建成了，
 * 而 C 端一个接口都没有 —— 会员既拿不到号，也看不到自己有什么。
 * 整个玩法只有运营那一半。
 *
 * <h3>没有「买一张」，这是后端设计</h3>
 * 号码**只能从奖品派发拿到**：中奖 → `LotteryPrizeHandler` → 发一个号。
 * 要做「花积分买一张」得先回答一串产品问题（多少分一张、单人限购、卖不完怎么办），
 * 而领号引擎那一侧刻意没有资产扣减。
 *
 * <p>所以**不要画「购买」按钮** —— 那是产品决策，不是前端加个按钮的事。
 * 与任务中心没有「领取」按钮是同一条规矩。
 */

/** 我的一张号码 */
export interface LotteryTicket {
  lotteryCode: string
  /** 玩法名。玩法被删了后端会给「彩票」这个兜底 —— 票不该因此从页面上消失 */
  lotteryName: string
  issueNo: string
  ticketNumber: string
  obtainTime: string
  /** 0 未开奖 / 1 未中奖 / 2 已中奖 */
  winStatus: number | null
  /**
   * 给用户看的那句话（待开奖 / 未中奖 / 中 N 等奖）。
   * **由后端给** —— 前端做映射表就是第二份状态机。
   */
  statusText: string
  /**
   * 中奖奖级，1 最大。未中 / 未开奖是 `null`。
   *
   * 🔴 后端已经把库里那个 `99`（「未中奖」的哨兵值）滤掉了。
   * 别自己去判 `=== 99` —— 那个数字不该出现在前端。
   */
  prizeLevel: number | null
  /** 开奖号码。未开奖是 null */
  winningNumber: string | null
  /** 计划开奖时间。未开奖时用它告诉用户什么时候来看 */
  planDrawTime: string | null
}

/** 一个玩法的当前一期 */
export interface LotteryIssue {
  lotteryCode: string
  lotteryName: string
  /**
   * 当前在售期号。
   *
   * 🔴 **可能是 null，那不是错误**：上一期开完奖、下一期还没开售，
   * 中间本来就有空窗。这时候该说「下一期敬请期待」，不是转圈也不是报错。
   */
  issueNo: string | null
  saleEndTime: string | null
  planDrawTime: string | null
  /** 我在这一期已经有几张 */
  myTicketCount: number
}

/**
 * 我的彩票号码，跨玩法跨期一起给。
 *
 * <p>排序是**中奖的在最前**，不是最新的在最前 —— 用户点进来最想知道的是
 * 「我中了没有」。这个口径由服务端的 SQL 保证，前端不要再排一次。
 */
export function fetchMyTickets(): Promise<LotteryTicket[]> {
  return request<LotteryTicket[]>({ url: '/lottery/ticket/mine' })
}

/**
 * 某个玩法的当前一期。**匿名可看** —— 「几点截止、几点开奖」是活动的公开面。
 *
 * <p>玩法不存在或已下线时后端返回 `null`。
 */
export function fetchLotteryIssue(lotteryCode: string): Promise<LotteryIssue | null> {
  return request<LotteryIssue | null>({ url: `/lottery/${lotteryCode}/issue` })
}

/** 一条中奖规则，给用户看的那一版 */
export interface LotteryPrizeRule {
  prizeLevel: number | null
  /** 怎么算中奖，**由服务端拼成人话** —— 端上做 EXACT/TAIL/HEAD 映射表就是第二份规则口径 */
  ruleText: string
  /** 中了给什么。查不到奖品配置时为 null，就不画那一行 */
  prizeName: string | null
}

/** 往期的一期开奖结果 */
export interface LotteryIssueResult {
  issueNo: string
  winningNumber: string
  /** **实际**开奖时刻，不是计划开奖时刻 */
  settleTime: string | null
}

/**
 * 彩票活动页要的全部数据，**一次拿完**。
 *
 * 🔴 本期 / 中奖规则 / 往期开奖 / 我这期的号码是四块同时出现的内容 ——
 * 分四个请求会让首屏等最慢的那个，中途还会出现「规则出来了但期号还在转圈」的半成品。
 * 服务端那一侧它们是四次本地查询。
 */
export interface LotteryBoard {
  lotteryCode: string
  lotteryName: string
  /** 号码位数。**按它画号码框**，不要从号码字符串长度去猜 —— 还没号码时也得画得出来 */
  numberLength: number | null
  /** 当前一期。**可能为 null**：上一期开完、下一期没开售的空窗 */
  issue: LotteryIssue | null
  rules: LotteryPrizeRule[]
  recentIssues: LotteryIssueResult[]
  /** 我在**当前这一期**的号码。全部号码在「我的彩票」那一页 */
  myTickets: LotteryTicket[]
}

/** 领号结果。`accepted: false` 不是故障 —— 多半是限购满了或者售罄 */
export interface LotteryObtainResult {
  accepted: boolean
  /** 给用户看的一句话，**成功时也有**。直接显示，不要自己再拼一句 */
  message: string
  lotteryCode: string | null
  issueNo: string | null
  ticketNumber: string | null
}

/**
 * 彩票活动页数据。**匿名可看** —— 活动页是分享出去的入口。
 *
 * <p>活动不存在、不是彩票型、或没挂上线的彩票玩法时后端返回 `null`。
 */
export function fetchLotteryBoard(activityCode: string): Promise<LotteryBoard | null> {
  return request<LotteryBoard | null>({ url: `/lottery/board/${activityCode}` })
}

/**
 * 参与，领一个号码。
 *
 * 🔴 不传任何参数：会员号由网关从登录态取，幂等键也由网关生成。
 * 让客户端传幂等键的话，一个固定值就能让这个人永远领不到号。
 */
export function obtainTicket(activityCode: string): Promise<LotteryObtainResult> {
  return request<LotteryObtainResult>({
    url: `/lottery/${activityCode}/obtain`,
    method: 'POST',
  })
}
