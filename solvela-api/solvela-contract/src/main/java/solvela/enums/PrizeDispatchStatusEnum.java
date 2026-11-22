package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发奖执行状态，对齐 {@code t_prize_log.status}。
 *
 * <p>⚠️ 与 {@link PrizeApproveStatusEnum} 是<b>两个维度</b>，同一行上并存：
 * 审批状态说的是「准不准发」，本状态说的是「发没发出去」。
 * 一条 {@code approve_status=已批准 + status=失败} 的记录是完全合法的，
 * 意思是运营批了但发放环节出错了 —— 把两者混成一列会丢掉这个区分。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum PrizeDispatchStatusEnum implements BaseEnum {

    /**
     * 等待执行：记录已落库，还没轮到它发
     */
    WAITING(0, "等待"),

    SUCCESS(1, "成功"),

    FAIL(2, "失败"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}。成功、失败都是终态。
     *
     * <p>🔴 闸门在 SQL 里：{@code PrizeLogMapper.xml} 的三条回写都带着「仍是等待」的条件，
     * 处理器与资产引擎的异步回写谁先到谁写，晚到的不覆盖。
     * {@code PrizeLogStatusSqlTest} 断言这些 SQL 的迁移都在这里。
     * 约定见 {@code docs/架构/领域实体轻充血-架构设计文档.md}。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(PrizeDispatchStatusEnum to) {
        return switch (this) {
            case WAITING -> to == SUCCESS || to == FAIL;
            case SUCCESS, FAIL -> false;
        };
    }
}
