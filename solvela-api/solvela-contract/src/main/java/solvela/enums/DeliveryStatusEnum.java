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
     * <p>统计层与前端也叫它「取消」（{@code cancelledCount}），全链路只有这一种叫法，别再起「作废」之类的别名。
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

    /**
     * 本状态能否迁到 {@code to}。待发货是唯一的初始状态；已签收、异常退回、已取消都是终态，不能改回。
     *
     * <p>待发货可以直接到已签收：回填时东西已经签收了，就一步到位，不用先回填一次「已发货」。
     *
     * <p>🔴 闸门在 SQL 里：{@code PhysicalDeliveryMapper.xml} 的每条改状态的 UPDATE 都带旧状态条件。
     * {@code PhysicalDeliveryStatusSqlTest} 断言那些 SQL 与这里一条不多、一条不少。
     * 回填导入在落库前先按本方法校验每一行，有一行不合法整批退回。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(DeliveryStatusEnum to) {
        return switch (this) {
            case PENDING -> to == DELIVERED || to == SIGNED || to == CANCELLED;
            case DELIVERED -> to == SIGNED || to == RETURNED;
            case SIGNED, RETURNED, CANCELLED -> false;
        };
    }

    public static DeliveryStatusEnum resolve(Integer value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equals(value))
                .findFirst()
                .orElse(null);
    }
}
