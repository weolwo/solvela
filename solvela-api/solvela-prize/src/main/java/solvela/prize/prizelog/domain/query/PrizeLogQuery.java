package solvela.prize.prizelog.domain.query;

import solvela.base.domain.PageParam;

import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖品发放记录分页查询的<b>领域参数</b>。形状与管理端的 {@code PrizeLogQuery} 目前一致，
 * 但<b>变更的理由不同</b>：Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}（全项目第一个改造的样板）。
 * 这里刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PrizeLogQuery extends PageParam {

    /**
     * 会员号（精确匹配，走 member_id 索引）。
     *
     * <p>🔴 这里<b>刻意不再收账号</b>：v3.71.0 之后 {@code member_name} 只是展示快照、
     * 身上没有任何索引，拿它当查询条件必是全表扫；而它又是可改的，
     * 用户改名之后按旧名字查等于查不到 —— 「不报错，只是查不到了」正是这次换键要消灭的。
     * 后台要按账号找人，先经 {@code MemberService.getMemberId} 换成会员号。
     */
    private Long memberId;

    /** 奖品编码 */
    private String prizeCode;

    /** 活动编码 */
    private String activityCode;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

    /** 审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回 */
    private Integer approveStatus;

    /** 执行状态：0-等待, 1-成功, 2-失败 */
    private Integer status;

    /** 过期时间 */
    private LocalDate validUntilBegin;

    /** 过期时间 */
    private LocalDate validUntilEnd;

}
