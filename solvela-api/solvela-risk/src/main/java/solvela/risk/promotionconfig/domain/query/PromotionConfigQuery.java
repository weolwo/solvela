package solvela.risk.promotionconfig.domain.query;

import solvela.enums.EnableStatusEnum;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 营销配置分页查询的<b>领域参数</b>。形状与管理端的 {@code PromotionConfigQuery} 目前一致，
 * 但<b>变更的理由不同</b>：Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}（全项目第一个改造的样板）。
 * 这里刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PromotionConfigQuery extends PageParam {

    /** 优惠配置名称 */
    private String promoName;

    /** 资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物) */
    private String prizeType;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

    /** 同周期内，单会员ID最多领取次数 (-1为不限) */
    private Integer identifyLimit;

    /** 同周期内，单手机号最多领取次数 (-1为不限) */
    private Integer phoneLimit;

    /** 同周期内，单IP地址最多领取次数 (-1为不限) */
    private Integer ipLimit;

    /** 状态：0-停用, 1-启用 */
    private EnableStatusEnum status;

}
