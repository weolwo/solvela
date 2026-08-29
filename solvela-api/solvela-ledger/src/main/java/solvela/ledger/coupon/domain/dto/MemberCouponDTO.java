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
