package solvela.consumer.handler;

import lombok.extern.slf4j.Slf4j;
import solvela.enums.ActivityTypeEnum;
import solvela.enums.ProposalSourceTypeEnum;
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
@Component
public class ProposalSourceResolver {

    /**
     * <b>纯函数，不查库。</b>
     *
     * <p>2026-08-30 之前它注入 {@code ActivityConfigService}，拿 activityCode 回头查活动表反推类型。
     * 四个服务拆开之后那条路走不通：<b>派发在会员服务，活动配置在营销服务</b>，不在一个进程里。
     * 让消费方反向依赖发送方的域，两个服务就又绑在一起了。
     *
     * <p>现在类型由发放方写进 {@code t_prize_log.activity_type}（发奖那一刻它本来就知道），
     * 这里只做翻译。事件驱动里的通则：<b>消息自带上下文，消费方不回头查发送方</b>。
     *
     * @param activityType 发奖流水上的玩法类型
     * @return 提案来源类型；为空或类型非法时降级为 {@link ProposalSourceTypeEnum#MANUAL}
     */
    public String resolve(String activityType) {
        ActivityTypeEnum type = ActivityTypeEnum.resolve(activityType);
        if (type == null) {
            // 不抛异常：来源类型只是归类维度，为它中断一次真实发奖不划算
            log.warn("【提案来源】玩法类型为空或非法，来源降级为 MANUAL。activityType={}", activityType);
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
