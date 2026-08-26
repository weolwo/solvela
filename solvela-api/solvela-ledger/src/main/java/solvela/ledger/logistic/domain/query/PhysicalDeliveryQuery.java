package solvela.ledger.logistic.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

/**
 * 实物履约单分页查询的<b>领域参数</b>。形状与管理端的 {@code PhysicalDeliveryQueryForm} 目前一致，
 * 但<b>变更的理由不同</b>：Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}（本域第一个改造的样板）。
 * 这里刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PhysicalDeliveryQuery extends PageParam {

    /**
     * 会员号（精确匹配，走 member_id 索引）。
     *
     * <p>🔴 这里<b>刻意不再收账号</b>：v3.71.0 之后 {@code member_name} 只是展示快照、
     * 身上没有任何索引，拿它当查询条件必是全表扫；而它又是可改的，
     * 用户改名之后按旧名字查等于查不到 —— 「不报错，只是查不到了」正是这次换键要消灭的。
     * 后台要按账号找人，先经 {@code MemberService.getMemberId} 换成会员号。
     */
    private Long memberId;

    /**
     * 来源单号：PROPOSAL 存提案ID / MALL 存订单号
     */
    private String sourceBizId;

    /**
     * 来源类型：PROPOSAL / MALL
     */
    private String sourceType;

    /**
     * 物流单号
     */
    private String logisticsNo;

    /**
     * 状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDate createTimeBegin;

    /**
     * 创建时间
     */
    private LocalDate createTimeEnd;

}
