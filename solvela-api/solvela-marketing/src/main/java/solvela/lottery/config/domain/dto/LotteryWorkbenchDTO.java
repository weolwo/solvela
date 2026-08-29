package solvela.lottery.config.domain.dto;

import solvela.enums.LotteryConfigStatusEnum;
import java.util.List;

/**
 * 彩票工作台聚合回显VO：与聚合保存 LotteryWorkbenchSaveCommand 同构，前端拿到即可直接填回表单。
 *
 * 未配置过的活动返回一个「空壳」（lotteryCode 为 null、规则空列表）而不是报错，
 * 前端据此进入「从零配置」态 —— 与抽奖工作台的处理一致。
 *
 * @param activityCode  活动编码
 * @param activityName  活动名称
 * @param lotteryCode   彩票编码；未配置时返回一个预生成的可用编码，运营可直接用也可重新生成
 * @param lotteryName   彩票名称
 * @param numberLength  号码长度
 * @param totalCount    单期发售上限
 * @param status        彩票状态：0-未上线, 1-售卖中；未配置时为 null
 * @param configured    是否已配置过：false 表示这个活动下还没有彩票，前端走「从零配置」
 * @param structureLocked 结构是否已冻结：true 时禁改 numberLength / totalCount（服务端保存会再算一遍，UI 只是防呆）
 * @param lockReason    冻结原因的人话说明，直接给前端当提示文案用；未冻结为 null
 * @param issueCount    已创建的期号数
 * @param soldTotal     累计已发号数
 * @param prizeRuleList 奖级规则
 *
 * @Author alaric
 * @Date 2026-07-27
 */
public record LotteryWorkbenchDTO(String activityCode,
                                 String activityName,
                                 String lotteryCode,
                                 String lotteryName,
                                 Integer numberLength,
                                 Integer totalCount,
                                 LotteryConfigStatusEnum status,
                                 boolean configured,
                                 boolean structureLocked,
                                 String lockReason,
                                 long issueCount,
                                 long soldTotal,
                                 List<LotteryWorkbenchRuleDTO> prizeRuleList) {
}
