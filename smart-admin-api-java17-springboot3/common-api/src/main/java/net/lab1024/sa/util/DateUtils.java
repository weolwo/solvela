package net.lab1024.sa.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/**
 * Java 8 日期时间高级工具类
 * 专为高并发业务设计：所有使用到的 DateTimeFormatter 均为线程安全
 */
public class DateUtils {

    // ==========================================
    // 1. 常用格式化定义 (线程安全)
    // ==========================================
    public static final String NORM_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String NORM_DATE_PATTERN = "yyyy-MM-dd";
    public static final String PURE_DATETIME_PATTERN = "yyyyMMddHHmmss";
    public static final String PURE_DATE_PATTERN = "yyyyMMdd";

    public static final DateTimeFormatter NORM_DATETIME_FORMATTER = DateTimeFormatter.ofPattern(NORM_DATETIME_PATTERN);
    public static final DateTimeFormatter NORM_DATE_FORMATTER = DateTimeFormatter.ofPattern(NORM_DATE_PATTERN);
    public static final DateTimeFormatter PURE_DATETIME_FORMATTER = DateTimeFormatter.ofPattern(PURE_DATETIME_PATTERN);

    private DateUtils() {
        // 私有构造，防止实例化
    }

    // ==========================================
    // 2. 格式化与解析 (String <-> LocalDateTime)
    // ==========================================

    /**
     * 将 LocalDateTime 格式化为字符串 (yyyy-MM-dd HH:mm:ss)
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(NORM_DATETIME_FORMATTER) : null;
    }

    /**
     * 将 LocalDateTime 格式化为指定格式字符串
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ofPattern(pattern)) : null;
    }

    /**
     * 将字符串解析为 LocalDateTime (yyyy-MM-dd HH:mm:ss)
     */
    public static LocalDateTime parse(String dateTimeStr) {
        return LocalDateTime.parse(dateTimeStr, NORM_DATETIME_FORMATTER);
    }

    // ==========================================
    // 3. 业务时间计算 (计算差值、判断区间) -> 彩票业务核心
    // ==========================================

    /**
     * 【重要】计算两个时间的相差秒数 (常用于计算 Redis 的 TTL)
     */
    public static long betweenSeconds(LocalDateTime startTime, LocalDateTime endTime) {
        return Duration.between(startTime, endTime).getSeconds();
    }

    /**
     * 计算两个时间的相差天数
     */
    public static long betweenDays(LocalDateTime startTime, LocalDateTime endTime) {
        return ChronoUnit.DAYS.between(startTime, endTime);
    }

    /**
     * 【重要】判断当前时间是否在指定的时间区间内 (常用于判断活动/期号是否在售卖中)
     */
    public static boolean isBetween(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startTime) && now.isBefore(endTime);
    }

    // ==========================================
    // 4. 获取一天的开始和结束 (常用于按天查询数据库记录)
    // ==========================================

    /**
     * 获取指定日期的开始时间 (例如：2023-10-01 00:00:00)
     */
    public static LocalDateTime getStartOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MIN);
    }

    /**
     * 获取指定日期的结束时间 (例如：2023-10-01 23:59:59.999999999)
     */
    public static LocalDateTime getEndOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MAX);
    }

    /**
     * 获取当月的最后一天最后时刻
     */
    public static LocalDateTime getEndOfMonth() {
        return LocalDateTime.now().with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
    }

    // ==========================================
    // 5. 时间戳转换 (毫秒/秒 <-> LocalDateTime)
    // ==========================================
    public static long getNowSeconds() {
        return  LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().getEpochSecond();
    }
    /**
     * LocalDateTime 转 秒时间戳
     */
    public static long toEpochSeconds(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().getEpochSecond();
    }

    public static LocalDateTime ofSeconds(long timestamp) {
        return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
    /**
     * LocalDateTime 转 毫秒时间戳
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 毫秒时间戳 转 LocalDateTime
     */
    public static LocalDateTime ofEpochMilli(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // ==========================================
    // 6. 兼容老旧 API (Date <-> LocalDateTime)
    // ==========================================

    /**
     * Date 转 LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate().atStartOfDay();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * LocalDateTime 转 Date
     */
    public static Date toDate(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}