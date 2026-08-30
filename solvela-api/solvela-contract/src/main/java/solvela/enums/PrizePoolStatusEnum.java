package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 奖池开关状态，对齐 {@code t_prize_pool_config.status}。
 *
 * <p>⚠️ 措辞是「开启 / 关闭」而不是「启用 / 停用」，所以没有复用 {@link EnableStatusEnum} ——
 * desc 是要发给前端展示的，两套说法混用会让页面上出现两种叫法。
 *
 * <p>{@link #CLOSED} 是运行态的硬闸门：{@code DrawExecuteService} 判到它直接拒绝新的抽奖请求，
 * 比删奖池安全得多（删了之后在途请求会找不到配置）。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum PrizePoolStatusEnum implements BaseEnum {

    CLOSED(0, "关闭"),

    OPEN(1, "开启"),
    ;

    private final Integer value;

    private final String desc;
}
