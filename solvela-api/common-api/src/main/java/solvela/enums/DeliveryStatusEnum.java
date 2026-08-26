package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.enumeration.BaseEnum;

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
