package solvela.member.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券包里的一张券。
 *
 * <h3>🔴 带着 {@code ruleText}，不让端上自己拼</h3>
 * 「满 100 可用，最高减 50」这句话是由 {@code discountType} / {@code discountValue} /
 * {@code minAmount} / {@code maxDiscount} 四个字段组合出来的，而组合规则会变
 *（加一种折扣类型就多一条分支）。让每个端自己拼，就是让同一段易错逻辑
 * 在 C 端、管理端、将来的小程序各存一份 —— 而它们一定会不一致。
 *
 * <p>原始字段也一起给，端上要做别的展示（比如进度条）时用得上。
 *
 * @param couponId       会员券 id。下单选券时传它
 * @param couponName     券名
 * @param ruleText       规则的人话版本，直接显示
 * @param discountType   {@code FIXED} / {@code PERCENT}；没有规则时为 null
 * @param discountValue  抵扣额或折扣率；没有规则时为 null
 * @param minAmount      使用门槛，0 = 无门槛
 * @param maxDiscount    最高抵扣，仅 {@code PERCENT} 有意义
 * @param deductTarget   抵什么：{@code CASH} / {@code SCORE}
 * @param status         券状态码，对齐 {@code CouponStatusEnum} 的 value
 * @param statusDesc     状态的人话版本
 * @param validEndTime   失效时间
 * @param usedTime       核销时间，没用过时为 null
 * @param discountAmount 实际抵扣了多少，没用过时为 null。
 *                       ⚠️ 冗余字段，权威在核销流水 —— 这里是为了券包列表零 join
 */
public record MemberCouponView(Long couponId,
                               String couponName,
                               String ruleText,
                               String discountType,
                               BigDecimal discountValue,
                               BigDecimal minAmount,
                               BigDecimal maxDiscount,
                               String deductTarget,
                               Integer status,
                               String statusDesc,
                               LocalDateTime validEndTime,
                               LocalDateTime usedTime,
                               BigDecimal discountAmount) {
}
