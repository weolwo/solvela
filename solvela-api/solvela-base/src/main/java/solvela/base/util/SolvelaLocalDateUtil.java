package solvela.base.util;

import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;


/**
 * @Author 1024创新实验室:胡克
 * @Date 2023/12/5 22:25:43
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * 1024创新实验室 （ https://1024lab.net ），2012-2023
 */
public class SolvelaLocalDateUtil {


    /**
     * 格式化 LocalDateTime 返回对应格式字符串
     *
     * @param time
     * @param formatterEnum {@link SolvelaDateFormatterEnum}
     * @return
     */
    public static String format(LocalDateTime time, SolvelaDateFormatterEnum formatterEnum) {
        return time.format(formatterEnum.getFormatter());
    }

    /**
     * 格式化 LocalDate返回对应格式字符串
     *
     * @param date
     * @param formatterEnum {@link SolvelaDateFormatterEnum}
     * @return
     */
    public static String format(LocalDate date, SolvelaDateFormatterEnum formatterEnum) {
        return date.format(formatterEnum.getFormatter());
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

    /**
     * 获取指定日期时间戳
     *
     * @param time
     * @return
     */
    public static Long getTimestamp(LocalDateTime time) {
        return time.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
    }

    /**
     * 获取当前时间戳(秒)
     *
     * @return
     */
    public static long nowSecond() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 将时间格式化为 星期几，例：星期一 ... 星期日
     *
     * @param localDate
     * @return
     */
    public static String formatToChineseWeek(LocalDate localDate) {
        return localDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE);
    }

    /**
     * 将时间格式化为 周几，例：周一 ... 周日
     *
     * @param localDate
     * @return
     */
    public static String formatToChineseWeekZhou(LocalDate localDate) {
        return formatToChineseWeek(localDate).replace("星期", "周");
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 获取当天剩余时间 单位
     *
     * @param unit 时间单位
     * @return
     */
    public static Long getDayBalanceTime(ChronoUnit unit) {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.plusDays(1L).with(LocalTime.MIN)).get(unit);
    }

    public static void main(String[] args) {
        System.out.println(SolvelaLocalDateUtil.format(LocalDateTime.now(), SolvelaDateFormatterEnum.YMD_HMS));
        System.out.println(SolvelaLocalDateUtil.format(LocalDateTime.now(), SolvelaDateFormatterEnum.YMD_HM));
        System.out.println(SolvelaLocalDateUtil.parse("2021-10-15 10:10:00", SolvelaDateFormatterEnum.YMD_HMS));
    }

}
