package solvela.draw.poolconfig.domain.query;

import solvela.base.domain.PageParam;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖池配置分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PrizePoolConfigQuery extends PageParam {

    /** 活动编码 */
    private String activityCode;

    /** 奖池唯一编码 (如: VIPPOOL) */
    private String poolCode;

    /** 奖池名称 */
    private String poolName;

    /** 0关闭，1开启 */
    private Integer status;

    /**
     * 只看有体检告警的奖池。「配了却抽不了」是这页最该先处理的一类问题 ——
     * 概率没闭环、没配坑位、活动上线了池却关着，看裸字段一个都看不出来。
     */
    private Boolean onlyIssue;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

}
