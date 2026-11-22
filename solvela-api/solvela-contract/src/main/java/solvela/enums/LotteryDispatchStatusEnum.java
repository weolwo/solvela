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

    /**
     * 本状态能否迁到 {@code to}。已投递、投递失败都是终态。
     *
     * <p>⚠️ 投递失败(2) 至今没有出边：「运营补回奖品配置后人工重投」还没实现。
     * 实现时先在这里加边，{@code LotteryRecordStatusSqlTest} 会逼着 DAO 补上对应的闸门 SQL。
     *
     * <p>🔴 闸门在 SQL 里：{@code LotteryRecordMapper.xml} 的标记投递与标记失败都带 {@code dispatch_status = 待派发}。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(LotteryDispatchStatusEnum to) {
        return switch (this) {
            case WAITING -> to == DISPATCHED || to == FAILED;
            case DISPATCHED, FAILED -> false;
        };
    }
}
