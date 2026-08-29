package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.enums.BaseEnum;

import java.util.Arrays;

/**
 * 实物履约单状态，对齐 {@code t_physical_delivery.status}。
 *
 * <p>实物是三段式履约（见 {@code PhysicalAssetHandler} 的类注释）：
 * 中奖时只生成 {@link #PENDING} 的履约单，收件信息由用户后补，
 * 运营发货后才回填物流单号并推进到 {@link #DELIVERED}。
 * 所以「待发货」既包含「用户还没填地址」也包含「地址齐了等发货」，
 * 要区分这两种得看 receiver_address 是否为空，不是靠状态。
 *
 * <p>⚠️ 字段必须叫 {@code value} 且不要手写 getValue()，写法对齐 {@link IssueStatusEnum}。
 *
 * @Author alaric
 * @Date 2026-08-14
 */
@Getter
@AllArgsConstructor
public enum DeliveryStatusEnum implements BaseEnum {

    /**
     * 已取消：发货被取消，<b>终态</b>。
     *
     * <p>🔴 它一直存在于数据里，却长期不在本枚举、也不在 DDL 列注释里 ——
     * {@code PhysicalDeliveryStatDTO} 早就有对应的统计字段，
     * {@code LedgerStatTest#deliveryNumbersAddUp} 的注释里也写着「四个桶加起来比总数少 1」。
     * 统计层一直认它，只有类型层不认。2026-08-29 补齐。
     *
     * <p>统计层原先叫它「作废(discarded)」，与本枚举的「取消」不是一套说法，
     * 同日一并改成 {@code cancelledCount}（含前端展示文案），全链路只剩一种叫法。
     *
     * <p>用负数是刻意的：取消不是履约流程上的一环，不该排进 0..3 的推进序列。
     */
    CANCELLED(-1, "已取消"),

    /**
     * 待发货：履约单已建立，东西还没寄出
     */
    PENDING(0, "待发货"),

    /**
     * 已发货：物流单号已回填
     */
    DELIVERED(1, "已发货"),

    /**
     * 已签收：终态
     */
    SIGNED(2, "已签收"),

    /**
     * 异常退回：终态，需人工跟进
     */
    RETURNED(3, "异常退回"),
    ;

    private final Integer value;

    private final String desc;

    public static DeliveryStatusEnum resolve(Integer value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equals(value))
                .findFirst()
                .orElse(null);
    }
}
