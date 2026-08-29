package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 优惠配置的审核层级，对齐 {@code t_promotion_config.review_level}。
 *
 * <p>决定一条发放提案要过几道人工关：
 * <ul>
 *   <li>{@link #NONE} 免审，提案直接落「待执行」；</li>
 *   <li>{@link #SINGLE} 一审通过即待执行；</li>
 *   <li>{@link #DOUBLE} 一审通过转二审，二审再通过才待执行。</li>
 * </ul>
 * 流转细节见 {@link ProposalStatusEnum}。
 *
 * <p>⚠️ 它是<b>有限取值</b>而不是数值等级 —— 名字里带 level 容易被当成后者。
 * 改造初期的自动分类就把它误判成「开放数值等级」而漏掉了一轮。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum ReviewLevelEnum implements BaseEnum {

    NONE(0, "无需审核"),

    SINGLE(1, "单层审批"),

    DOUBLE(2, "双层审批"),
    ;

    private final Integer value;

    private final String desc;
}
