package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发奖审批状态，对齐 {@code t_prize_log.approve_status}。
 *
 * <p>取代 {@code PrizeDispatchHandler} 里那三个私有 int 常量
 * （{@code APPROVE_PENDING/PASSED/REJECTED}）—— 它们只覆盖了 1/2/3，
 * 而 0 是靠一句 {@code setApproveStatus(config.getApproveMode() == 1 ? 1 : 0)}
 * 的行尾注释表达的，翻译过来是「不需要审批」。
 *
 * <p>{@link #NOT_REQUIRED} 与 {@link #PASSED} 的区别别丢：前者是这个奖品压根不走审批
 * （{@code approve_mode = AUTO}），后者是走了审批并且过了。运营对账时这两者不是一回事。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum PrizeApproveStatusEnum implements BaseEnum {

    /**
     * 无需审批：奖品配置的 approve_mode 是自动免审
     */
    NOT_REQUIRED(0, "无需审批"),

    /**
     * 待审批：等运营点头，这是 approve_mode=人工 的奖品唯一的入口状态
     */
    PENDING(1, "待审批"),

    /**
     * 已批准
     */
    PASSED(2, "已批准"),

    /**
     * 已驳回：终态
     */
    REJECTED(3, "已驳回"),
    ;

    private final Integer value;

    private final String desc;
}
