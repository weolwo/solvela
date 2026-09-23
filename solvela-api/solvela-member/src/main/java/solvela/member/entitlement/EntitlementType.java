package solvela.member.entitlement;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 权益类型 —— 决定<b>多久发一次</b>，也就决定了幂等键里的周期怎么算。
 *
 * <h3>🔴 为什么不是枚举</h3>
 * 与 {@code GradeChangeType} 同一条：这几个值是<b>代码里写死的几条发放路径</b>，
 * 每一条都对应一段自己的扫描逻辑（生日要查生日、月度要扫全量）。
 * 做成枚举会让人以为「在管理端加一个值就能多一种周期」——而那需要写代码。
 *
 * @author alaric
 * @date 2026-09-22
 */
public final class EntitlementType {

    private EntitlementType() {
    }

    /** 生日礼：一年一次。周期键是 {@code yyyy} */
    public static final String BIRTHDAY = "BIRTHDAY";

    /** 月度券：一月一次。周期键是 {@code yyyyMM} */
    public static final String MONTHLY = "MONTHLY";

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 这一天属于哪个周期。
     *
     * <h3>⚠️ 两种类型的周期键<b>长度不同</b>，这是刻意的</h3>
     * 统一成 {@code yyyyMM} 的话，生日礼就变成<b>一年能领十二次</b> ——
     * 而这个错不会报任何异常：唯一键仍然生效，只是「一个周期」的含义悄悄变小了。
     *
     * @return 周期键；类型不认识时返回 {@code null}，由调用方决定怎么处理
     */
    public static String periodKeyOf(String type, LocalDate day) {
        if (day == null) {
            return null;
        }
        if (BIRTHDAY.equals(type)) {
            return day.format(YEAR);
        }
        if (MONTHLY.equals(type)) {
            return day.format(MONTH);
        }
        return null;
    }

    public static boolean isKnown(String type) {
        return BIRTHDAY.equals(type) || MONTHLY.equals(type);
    }
}
