package solvela.draw.poolitem.domain.query;

import solvela.base.domain.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖池奖品项分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PrizePoolItemQuery extends PageParam {

    /** 活动编码 */
    private String activityCode;

    /**
     * 只看有体检告警的奖项。库存口径漂移、已超发、快抽空都归在这里 ——
     * 都是要立刻处理、而看裸数字看不出来的情况。
     */
    private Boolean onlyIssue;

}
