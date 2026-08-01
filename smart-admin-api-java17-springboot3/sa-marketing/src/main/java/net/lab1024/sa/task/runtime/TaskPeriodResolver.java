package net.lab1024.sa.task.runtime;

import net.lab1024.sa.task.constant.TaskConst;
import net.lab1024.sa.task.constant.TaskTypeEnum;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;

/**
 * 周期键与幂等键的<b>唯一真源</b>（方案 §4.6）。
 *
 * <p>🔴 全项目只此一处，禁止在别处散写 {@code format("yyyyMMdd")} —— 理由不是洁癖：
 * 一旦 period_key 在 A 处算、过期扫描在 B 处用 {@code now()} 算，就又是两个时钟源，
 * 而交接文档「时间戳错乱排查纪实」已经为同一类问题付过一次代价
 * （497 行 update_time 早于 create_time，查了两轮才定位）。
 *
 * <p>三条约束：
 * <ol>
 *   <li>周期一律<b>在 Java 侧</b>按 {@code ctx.eventTime()} 计算 ——
 *       用事件<b>实际发生</b>的时间而不是 {@code now()}，
 *       迟到的事件应归属它发生的那一天，而不是被处理的那一天；</li>
 *   <li>前提是 JVM 与 MySQL 会话时区已由铁律 10 的连接串对齐
 *       （{@code connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true}），
 *       前提不成立时整个周期会偏 8 小时，且症状是「昨天的签到算到今天」这种极难联想的形状；</li>
 *   <li>STREAK 恒返回 NONE，并在 switch 里<b>显式写出来</b>，不靠 default 落下去。</li>
 * </ol>
 *
 * @author alaric
 * @date 2026-08-01
 */
public final class TaskPeriodResolver {

    private TaskPeriodResolver() {
    }

    public static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String LIMIT_ONCE = TaskConst.LIMIT_ONCE;
    private static final String LIMIT_DAILY = TaskConst.LIMIT_DAILY;
    private static final String LIMIT_WEEKLY = TaskConst.LIMIT_WEEKLY;
    private static final String LIMIT_UNLIMITED = TaskConst.LIMIT_UNLIMITED;

    /**
     * 无天然单号的事件，幂等键按事件日兜底的前缀
     */
    private static final String DAY_BIZ_ID_PREFIX = "D";

    /**
     * 解析周期键：决定 t_task_record 按什么粒度分片（配合 uk_t_tsk_rec_mbr_cfg_prd 做周期幂等）。
     *
     * <p>🔴 <b>STREAK 必须先判，且恒为 NONE</b>：连续型要把连续数累加在同一条记录上，
     * 按天分片会让 current_metric 永远是 1。这一条与 limit_type 无关，
     * 即便运营把连续签到配成 {@code limit_type=DAILY} 也一样 —— 类型的语义优先于频次配置。
     */
    public static String resolvePeriodKey(TaskTypeEnum taskType, String limitType, LocalDateTime eventTime) {
        if (taskType == TaskTypeEnum.STREAK) {
            return TaskConst.PERIOD_NONE;
        }
        if (limitType == null) {
            return TaskConst.PERIOD_NONE;
        }
        return switch (limitType) {
            case LIMIT_DAILY -> eventTime.format(DAY_FORMAT);
            // ISO-8601 周：跨年周的归属按 ISO 标准（12月31日可能属于次年第1周），
            // 带上 ISO 周所属年份，否则「2026年第1周」与「2027年第1周」会撞成同一个 key
            case LIMIT_WEEKLY -> eventTime.get(IsoFields.WEEK_BASED_YEAR)
                    + "W" + String.format("%02d", eventTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case LIMIT_ONCE, LIMIT_UNLIMITED -> TaskConst.PERIOD_NONE;
            default -> TaskConst.PERIOD_NONE;
        };
    }

    /**
     * 解析事件幂等键（落进 {@code t_task_record_flow.event_biz_id}）。
     *
     * <p>上游带了天然单号（订单号）就用它；没带则按<b>事件的自然日</b>兜底。
     *
     * <p>🔴 <b>兜底刻意不用 period_key</b>，尽管那看起来更"统一"：
     * STREAK 的 period_key 恒为 NONE，若拿它当幂等键，
     * 同一个人对同一个任务<b>一辈子只有一个幂等键</b> ——
     * 第一次签到之后所有事件都会被唯一索引挡掉，「连续签到」直接失效。
     * 而无单号的事件（签到、浏览）其幂等语义天然就是「一天一次」，按自然日兜底才是对的。
     *
     * <p>对 {@code limit_type=DAILY} 的 COUNT 任务，两者恰好等价；差别只在 STREAK 上显现 ——
     * 这正是「看起来能统一的两个概念其实不是一回事」的例子。
     */
    public static String resolveEventBizId(String upstreamBizId, LocalDateTime eventTime) {
        if (upstreamBizId != null && !upstreamBizId.isBlank()) {
            return upstreamBizId.trim();
        }
        return DAY_BIZ_ID_PREFIX + formatDay(eventTime);
    }

    /**
     * yyyyMMdd
     */
    public static String formatDay(LocalDateTime time) {
        return time.format(DAY_FORMAT);
    }

    // ==================== 轮次（limit_count） ====================

    /**
     * 给周期键追加轮次：第 1 轮返回裸键，第 2 轮起返回 {@code 基础键#N}。
     *
     * <p>🔴 第 1 轮不带后缀是为了<b>与存量记录兼容</b>：库里已有的记录全是裸键，
     * 若第 1 轮也加 {@code #1}，那些记录会与新逻辑算出的键对不上，
     * 表现是「老用户的进度突然从头开始」。
     */
    public static String withRound(String basePeriodKey, int round) {
        return round <= 1 ? basePeriodKey : basePeriodKey + TaskConst.PERIOD_ROUND_SEPARATOR + round;
    }

    /**
     * 从周期键里解出轮次：裸键是第 1 轮，{@code 20260801#3} 是第 3 轮。
     *
     * <p>解析不出来时返回 1（当作第一轮）—— 脏数据不该让用户卡在「轮次未知」而完全推不动。
     */
    public static int parseRound(String periodKey) {
        if (periodKey == null) {
            return 1;
        }
        int idx = periodKey.lastIndexOf(TaskConst.PERIOD_ROUND_SEPARATOR);
        if (idx < 0) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(periodKey.substring(idx + 1)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * 该频次类型是否受 {@code limit_count} 轮次限制。
     *
     * <p>只有 DAILY / WEEKLY 受限：
     * <ul>
     *   <li>{@code ONCE} 语义就是终身一轮，界面上也不让填次数；</li>
     *   <li>{@code UNLIMITED} 是「轮次不限」，若让 limit_count 生效，
     *       「无限制 + 限制 1 次」会退化成「终身一次」，与它自己的名字矛盾。</li>
     * </ul>
     */
    public static boolean supportsRoundLimit(String limitType) {
        return LIMIT_DAILY.equals(limitType) || LIMIT_WEEKLY.equals(limitType);
    }
}
