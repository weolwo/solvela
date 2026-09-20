package solvela.marketing.api;

import java.util.List;

/**
 * 彩票活动页要的<b>全部数据，一次给完</b>。
 *
 * <h3>为什么合成一个，而不是让端上打三次</h3>
 * 本期、中奖规则、往期开奖 —— 这三块在页面上是同时出现的，分三个接口的话
 * 首屏得等最慢的那个回来，中间还会出现「规则出来了但期号还在转圈」的半成品状态。
 * 而它们在服务端是三次本地查询，合起来一次跨进程往返。
 *
 * <p>这和任务中心「聚合发生在营销服务进程内，网关不去循环调」是同一条。
 *
 * @param lotteryCode  玩法编码。<b>活动没挂彩票玩法时整个返回为 null</b>，不是这里为 null
 * @param lotteryName  玩法名
 * @param numberLength 号码位数。端上按它画号码框，不要从号码字符串长度去猜 ——
 *                     还没有号码的时候也得画得出来
 * @param issue        当前一期。<b>可能为 null</b>：上一期开完、下一期没开售的空窗
 * @param rules        中奖规则，奖级从大到小。文案由服务端拼
 * @param recentIssues 往期开奖结果，新的在前
 * @param myTickets    我在<b>当前这一期</b>的号码。未登录是空数组
 * @author alaric
 * @date 2026-09-18
 */
public record LotteryBoardView(
        String lotteryCode,
        String lotteryName,
        Integer numberLength,
        LotteryIssueView issue,
        List<LotteryPrizeRuleView> rules,
        List<LotteryIssueResultView> recentIssues,
        List<LotteryTicketView> myTickets) {
}
