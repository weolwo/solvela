package solvela.marketing.api;

/**
 * 一条中奖规则，给用户看的那一版。
 *
 * <h3>🔴 不下发 {@code prize_code}</h3>
 * 那是奖品编码，对用户没有意义，而且它是<b>核销时要快照的东西</b> ——
 * 摆在页面上只会让人拿去猜别的奖品编码。用户要知道的是
 * 「几等奖、怎么算中、中了给什么」，前两个在这里，第三个是 {@code prizeName}。
 *
 * @param prizeLevel  奖级，1 最大
 * @param ruleText    怎么算中奖，<b>由服务端拼成人话</b>
 *                    （「号码完全一致」/「后 3 位一致」）——
 *                    端上做 EXACT/TAIL/HEAD 的映射表就是第二份规则口径
 * @param prizeName   中了给什么。查不到奖品配置时为 null，端上就不画那一行
 * @author alaric
 * @date 2026-09-18
 */
public record LotteryPrizeRuleView(
        Integer prizeLevel,
        String ruleText,
        String prizeName) {
}
