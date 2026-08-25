package solvela.draw.runtime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;

/**
 * 奖池重置周期解析：把 {@code t_prize_pool_config.reset_period} 翻译成
 * 「单人限领计数应该记在哪个桶里、这个桶多久之后可以回收」。
 *
 * <h3>为什么需要它</h3>
 * {@code t_prize_pool_item.user_max_count} 是「单人限领次数」，而运行态的计数 key 是
 * {@code draw:user:{活动}:{奖项}:{会员}} —— 没有周期维度、也没有 TTL。
 * 结果是限领恒为「整个活动期间累计 N 次」，奖池上配的 {@code reset_period}
 * （每天/每周/每月）<b>完全不生效</b>：运营选了「每天」，用户抽完一次就再也抽不到了。
 * 本类补上那个缺失的维度。
 *
 * <h3>约定与任务模块保持一致</h3>
 * 格式、ISO 周的处理方式都对齐 {@link solvela.task.runtime.TaskPeriodResolver} ——
 * 两个模块都在算「周期键」，形状不一样只会让后来者以为其中一个有特殊考量。
 * 尤其是<b>周</b>：必须带 ISO week-based-year，否则「2026 年第 1 周」与
 * 「2027 年第 1 周」会撞成同一个桶（12 月 31 日可能属于次年第 1 周）。
 *
 * <h3>⚠️ 时区前提</h3>
 * 周期在 Java 侧按传入的时间计算，而那个时间必须来自<b>数据库时钟</b>（铁律 9/10）。
 * 前提是 JVM 与 MySQL 会话时区已由连接串对齐
 * （{@code connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true}）。
 * 前提不成立时整个周期会偏 8 小时，症状是「昨天的抽奖次数算到今天」这种极难联想的形状。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
public final class DrawPeriodResolver {

    private DrawPeriodResolver() {
    }

    /** 重置周期取值，对齐 t_prize_pool_config.reset_period 的列注释 */
    public static final String PERIOD_DAY = "DAY";
    public static final String PERIOD_WEEK = "WEEK";
    public static final String PERIOD_MONTH = "MONTH";
    public static final String PERIOD_ACTIVITY = "ACTIVITY";

    /**
     * 不分周期时的桶名：整个活动期间共用一个计数器，与本次改动之前的行为完全一致。
     * 取一个不会与日期串混淆的字面量（日期桶是 8 位或 6 位数字、周桶含 W）。
     */
    public static final String BUCKET_ALL = "ALL";

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 计数桶的 TTL。
     *
     * <p>TTL 只负责<b>回收</b>，不负责判定周期 —— 周期由桶名决定。
     * 所以宽松一点没有副作用，而留足余量能吸收时钟偏差与迟到的回滚补偿；
     * 反过来若 TTL 卡得比周期还短，会出现「周期没到但计数被清空」的超发。
     */
    private static final long TTL_DAY = 2L * 24 * 3600;
    private static final long TTL_WEEK = 9L * 24 * 3600;
    private static final long TTL_MONTH = 40L * 24 * 3600;

    /** 不过期 */
    public static final long TTL_NONE = 0L;

    /**
     * 一个周期桶：计数 key 里的周期段 + 该 key 的存活时间。
     *
     * @param bucket     周期桶名，进 Redis key
     * @param ttlSeconds 该 key 的 TTL，0 表示不过期
     */
    public record Period(String bucket, long ttlSeconds) {
    }

    /**
     * 解析奖池的当前周期桶。
     *
     * <p>⚠️ 取值无法识别时<b>退回「不重置」而不是按天重置</b>。
     * 方向是刻意选的：多重置一次意味着用户能比配置多抽，是资损；
     * 少重置一次只是比配置严格，用户会来反馈。脏数据面前宁可严格。
     *
     * @param resetPeriod 奖池的 reset_period，可为 null
     * @param now         当前时间，<b>必须来自数据库时钟</b>
     */
    public static Period resolve(String resetPeriod, LocalDateTime now) {
        if (resetPeriod == null || now == null) {
            return new Period(BUCKET_ALL, TTL_NONE);
        }
        return switch (resetPeriod.trim().toUpperCase()) {
            case PERIOD_DAY -> new Period(now.format(DAY_FORMAT), TTL_DAY);
            // ISO-8601 周：带上 week-based-year，跨年周才不会撞桶
            case PERIOD_WEEK -> new Period(now.get(IsoFields.WEEK_BASED_YEAR)
                    + "W" + String.format("%02d", now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)), TTL_WEEK);
            case PERIOD_MONTH -> new Period(now.format(MONTH_FORMAT), TTL_MONTH);
            case PERIOD_ACTIVITY -> new Period(BUCKET_ALL, TTL_NONE);
            default -> new Period(BUCKET_ALL, TTL_NONE);
        };
    }

    /**
     * 这个周期配置是否需要知道「现在几点」。
     *
     * <p>抽奖是热路径，而取数据库时钟是一次额外往返。ACTIVITY / 空值 / 脏值
     * 都固定落到 {@link #BUCKET_ALL}，跟时间无关 —— 这种情况下就别去问数据库了。
     */
    public static boolean needsClock(String resetPeriod) {
        if (resetPeriod == null) {
            return false;
        }
        return switch (resetPeriod.trim().toUpperCase()) {
            case PERIOD_DAY, PERIOD_WEEK, PERIOD_MONTH -> true;
            default -> false;
        };
    }
}
