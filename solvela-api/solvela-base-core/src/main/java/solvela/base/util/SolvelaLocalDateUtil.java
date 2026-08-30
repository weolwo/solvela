package solvela.base.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * java.time 日期时间工具类。
 * <p>
 * ⚠️ 格式化 / 解析一律走 {@link SolvelaDateFormatterEnum}，不要新增收 pattern 字符串的重载：
 * 枚举里的 DateTimeFormatter 是线程安全的常量，而 {@code DateTimeFormatter.ofPattern(pattern)}
 * 每次调用都会重新解析一遍 pattern，JsonConfig 在反序列化热路径上用着这里。
 * <p>
 * ⚠️ 所有时区换算统一用 {@link ZoneId#systemDefault()}，与数据源 URL 上的
 * {@code serverTimezone=Asia/Shanghai} 保持一致。不要再写死 {@code ZoneOffset.ofHours(8)}——
 * 那样容器时区一改就跟数据库对不上，且没有任何报错。
 *
 * @Author 1024创新实验室:胡克
 * @Date 2023/12/5 22:25:43
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * 1024创新实验室 （ https://1024lab.net ），2012-2023
 */
public class SolvelaLocalDateUtil {

    private SolvelaLocalDateUtil() {
        // 私有构造，防止实例化
    }

    // ==========================================
    // 格式化与解析
    // ==========================================

    /**
     * 格式化 LocalDateTime 返回对应格式字符串
     *
     * @param time
     * @param formatterEnum {@link SolvelaDateFormatterEnum}
     * @return
     */
    public static String format(LocalDateTime time, SolvelaDateFormatterEnum formatterEnum) {
        return time == null ? null : time.format(formatterEnum.getFormatter());
    }

    /**
     * 格式化 LocalDate返回对应格式字符串
     *
     * @param date
     * @param formatterEnum {@link SolvelaDateFormatterEnum}
     * @return
     */
    public static String format(LocalDate date, SolvelaDateFormatterEnum formatterEnum) {
        return date == null ? null : date.format(formatterEnum.getFormatter());
    }

    /**
     * 解析时间字符串 返回LocalDateTime
     *
     * @param time
     * @param formatterEnum {@link SolvelaDateFormatterEnum}
     * @return
     */
    public static LocalDateTime parse(String time, SolvelaDateFormatterEnum formatterEnum) {
        return LocalDateTime.parse(time, formatterEnum.getFormatter());
    }

    /**
     * 解析时间字符串 返回 LocalDate
     *
     * @param time
     * @param formatterEnum {@link SolvelaDateFormatterEnum}
     * @return
     */
    public static LocalDate parseDate(String time, SolvelaDateFormatterEnum formatterEnum) {
        return LocalDate.parse(time, formatterEnum.getFormatter());
    }

    // ==========================================
    // 时间戳
    // ==========================================

    /**
     * 获取当前时间戳(秒)
     *
     * @return
     */
    public static long nowSecond() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * LocalDateTime 转 秒时间戳
     */
    public static long toEpochSecond(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().getEpochSecond();
    }

    /**
     * LocalDateTime 转 毫秒时间戳
     */
    public static long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 秒时间戳 转 LocalDateTime
     */
    public static LocalDateTime ofEpochSecond(long timestamp) {
        return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 毫秒时间戳 转 LocalDateTime
     */
    public static LocalDateTime ofEpochMilli(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // ==========================================
    // 与 java.util.Date 互转（兼容老 API 与 JDBC 取出的字段）
    // ==========================================

    /**
     * Date 转 LocalDateTime
     * <p>
     * java.sql.Date 单独处理：它只有日期部分，走 toLocalDate().atStartOfDay() 语义更明确。
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().atStartOfDay();
        }
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * LocalDateTime 转 Date
     */
    public static Date toDate(LocalDateTime time) {
        return time == null ? null : Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ==========================================
    // 区间计算
    // ==========================================

    /**
     * 两个时间相差的秒数（常用于算 Redis TTL）
     */
    public static long betweenSeconds(LocalDateTime startTime, LocalDateTime endTime) {
        return Duration.between(startTime, endTime).getSeconds();
    }

    /**
     * 两个时间相差的天数
     */
    public static long betweenDays(LocalDateTime startTime, LocalDateTime endTime) {
        return ChronoUnit.DAYS.between(startTime, endTime);
    }

    /**
     * 当前时间是否落在 [startTime, endTime] 区间内（常用于判断期号 / 活动是否在售）
     */
    public static boolean isBetween(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startTime) && now.isBefore(endTime);
    }

    /**
     * 指定日期的开始时刻，例：2023-10-01 00:00:00
     */
    public static LocalDateTime getStartOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MIN);
    }

    /**
     * 指定日期的结束时刻，例：2023-10-01 23:59:59.999999999
     */
    public static LocalDateTime getEndOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MAX);
    }

}
