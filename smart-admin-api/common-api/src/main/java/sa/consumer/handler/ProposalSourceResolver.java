package sa.consumer.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sa.activity.domain.entity.ActivityConfig;
import sa.activity.service.ActivityConfigService;
import sa.enums.ActivityTypeEnum;
import sa.enums.ProposalSourceTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 把「奖来自哪种玩法」翻译成提案来源类型。
 *
 * <p>四个 {@code @PrizeStrategy} handler 共用一份，避免同一段映射抄四遍 ——
 * 之前那句硬编码的 {@code EventTypeEnum.LOTTERY_DRAW} 正是抄了四遍，
 * 所以四处都错、且改一处不会被发现漏改另外三处。
 *
 * <p><b>为什么从活动类型推导，而不是给 UserPrizeEvent / t_prize_log 加字段传递</b>：
 * 「这个奖来自哪种玩法」本来就等于 {@code t_activity_config.activity_type}，
 * 是已经存在的事实而不是需要新增的信息。加字段意味着改跨域事件契约 + 加 DDL 列 +
 * 存量数据回填，而这三样都只是为了搬运一个查一次就能得到的值。
 * 代价是每次派发多一次活动查询 —— 与已有的奖品配置查询同一量级，可接受。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProposalSourceResolver {

    private final ActivityConfigService activityConfigService;

    /**
     * @param activityCode 发奖流水上的活动编码
     * @return 提案来源类型；活动查不到或类型非法时降级为 {@link ProposalSourceTypeEnum#MANUAL}
     */
    public String resolve(String activityCode) {
        if (activityCode == null || activityCode.isBlank()) {
            return ProposalSourceTypeEnum.MANUAL.getValue();
        }
        ActivityConfig activity = activityConfigService.getByActivityCode(activityCode);
        if (activity == null) {
            // 不抛异常：来源类型只是归类维度，为它中断一次真实发奖不划算
            log.warn("【提案来源】活动不存在，来源降级为 MANUAL。activityCode={}", activityCode);
            return ProposalSourceTypeEnum.MANUAL.getValue();
        }
        ActivityTypeEnum type = ActivityTypeEnum.resolve(activity.getActivityType());
        if (type == null) {
            log.warn("【提案来源】活动类型非法，来源降级为 MANUAL。activityCode={}, activityType={}",
                    activityCode, activity.getActivityType());
            return ProposalSourceTypeEnum.MANUAL.getValue();
        }
        return switch (type) {
            case DRAW -> ProposalSourceTypeEnum.DRAW.getValue();
            case TASK -> ProposalSourceTypeEnum.TASK.getValue();
            case LOTTERY -> ProposalSourceTypeEnum.LOTTERY.getValue();
            // BASIC 不挂玩法引擎，它下面的奖只可能是人工发放
            case BASIC -> ProposalSourceTypeEnum.MANUAL.getValue();
        };
    }
}
