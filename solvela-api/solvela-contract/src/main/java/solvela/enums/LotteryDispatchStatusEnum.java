package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 中奖记录的派发状态，对齐 {@code t_lottery_record.dispatch_status}。
 *
 * <p>⚠️ 和 {@link PrizeDispatchStatusEnum} 取值形状一样（0/1/2）但<b>不是同一列</b>，
 * 也不该互相替代：那个说的是 {@code t_prize_log} 上「这次发奖执行成没成功」，
 * 这个说的是中奖记录「有没有被投递到发奖链路」。
 *
 * <p>{@link #WAITING} 把「待派发」和「无需派发」合成了一个取值 —— DDL 里就是这么定的。
 * 未中奖的记录天然不需要派发，也落 0，所以<b>不能拿 {@code dispatch_status = 0} 当
 * 「有奖待发」的判据</b>，必须同时带上 {@code win_status = 2}。
 * {@code LotteryRecordMapper} 里那几条待派发查询都是这么写的。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum LotteryDispatchStatusEnum implements BaseEnum {

    WAITING(0, "待派发/无需派发"),

    DISPATCHED(1, "已投递"),

    FAILED(2, "投递失败"),
    ;

    private final Integer value;

    private final String desc;
}
