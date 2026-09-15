package solvela.ledger.coupon.domain.dto;

import solvela.enums.CouponStatusEnum;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会员券列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>{@code createBy} / {@code updateBy} 是<b>后台运营人员</b>的账号，
 * C 端接口一个都不该看到 —— DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，
 * 装配在端上做。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class MemberCouponDTO {


    private Long id;

    /**
     * 会员号
     */
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    private String memberName;

    /**
     * 券模编码
     */
    private String couponCode;

    /**
     * 券类型
     */
    private String couponType;

    /**
     * 券名称
     */
    private String couponName;

    /**
     * 状态：0-未使用, 1-已使用, 2-已过期, 3-已作废
     */
    private CouponStatusEnum status;

    /**
     * 来源：DRAW, TASK, MANUAL_SEND
     */
    private String sourceType;

    /**
     * 关联单号
     */
    private String sourceBizId;

    /**
     * 有效期开始
     */
    private LocalDateTime validStartTime;

    /**
     * 有效期结束
     */
    private LocalDateTime validEndTime;

    /**
     * 核销时间
     */
    private LocalDateTime usedTime;

    // ------------------------------------------------------------------
    // 规则快照与锁定状态。2026-09-15 补 ——
    //
    // 🔴 这几列在阶段 1 就加到表上了，但列表一直没带出来。后果是：
    //    C 端券包能看到「满 100 减 20」，而【管理端看不到】。
    //    客服拿着用户的截图问「这张券到底减多少」，后台答不上来，
    //    只能去翻券模板 —— 而模板是会改版的，翻到的可能根本不是这张券当时那一版。
    //
    //    这正是当初做快照要解决的问题，只是读出口漏了一半。
    // ------------------------------------------------------------------

    /** 发券时的模板版本。null = 发券时没有模板，这张券没有规则 */
    private Integer templateVersion;

    /** 规则快照：FIXED-固定金额 / PERCENT-百分比。null = 没有规则 */
    private solvela.enums.CouponDiscountTypeEnum discountType;

    /** 规则快照：FIXED 时是抵扣额，PERCENT 时是折扣率 */
    private java.math.BigDecimal discountValue;

    /** 规则快照：最低消费门槛。0 = 无门槛，null = 没有规则 */
    private java.math.BigDecimal minAmount;

    /** 规则快照：最高抵扣。PERCENT 时必填 */
    private java.math.BigDecimal maxDiscount;

    /** 规则快照：CASH-抵现金 / SCORE-抵积分 */
    private solvela.enums.CouponDeductTargetEnum deductTarget;

    /** 规则快照：适用范围 */
    private solvela.enums.CouponScopeTypeEnum scopeType;

    /**
     * 锁定它的单据号。
     *
     * <p>⚠️ 客服排查「用户说券不见了」时最有用的一列：券在 4-锁定中 时
     * 用户点不动它，而这一列直接回答「是被哪一笔占着」。
     */
    private String lockedBizId;

    /** 锁定时间。兜底任务按它判超时 */
    private LocalDateTime lockedTime;

    /**
     * 本次实际抵扣额。
     *
     * <p>⚠️ 冗余列，<b>权威在 {@code t_coupon_write_off}</b>。两者不一致时以流水为准。
     */
    private java.math.BigDecimal discountAmount;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
