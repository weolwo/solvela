package solvela.draw.prizemapping.domain.query;

import solvela.base.domain.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖池奖品映射分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PoolPrizeMappingQuery extends PageParam {

    /**
     * 活动编码。页面先按活动收窄，再在活动内选具体奖池 ——
     * 奖池编码是十位随机码，脱离活动没人认得出是哪个池。
     */
    private String activityCode;

    /** 奖池编码 */
    private String poolCode;

    /**
     * 只看有体检告警的奖池。这是本页最主要的巡检入口 ——
     * 概率未闭环的奖池不是「配置可疑」，而是按下抽奖就报错，必须能一眼筛出来。
     */
    private Boolean onlyIssue;
}
