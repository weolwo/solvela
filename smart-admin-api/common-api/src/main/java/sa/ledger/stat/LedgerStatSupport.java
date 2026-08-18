package sa.ledger.stat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 财务中心四个统计面板共用的取值与换算。
 *
 * <p>这几个方法本身都只有两三行，收在一处不是为了省代码量，而是为了<b>口径只有一份</b>：
 * 比率的小数位、金额的小数位、Map 取空值时兜成什么，四个页面必须一致 ——
 * 各写各的，早晚会出现「同一个 0.5 这页显示 50%、那页显示 50.00%」。
 *
 * <p>取数链路是 {@code SQL 别名 -> Map<String,Object> -> VO}，中间靠<b>字符串 key</b> 对接：
 * key 打错一个字母，{@link #toLong} 返回 0，页面上就是一个安安静静的「0 条异常」——
 * 编译不报错、SQL 不报错、接口 200。所以每个统计接口都配了 key 契约测试，
 * 见 {@code LedgerStatTest}。
 *
 * @Author alaric
 * @Date 2026-08-18
 */
public final class LedgerStatSupport {

    /**
     * 比率保留四位小数（页面上再乘 100 展示成百分比），与提案/任务/奖励三个漏斗一致
     */
    private static final int RATE_SCALE = 4;

    /**
     * 金额保留两位。DECIMAL(18,4) 直接透出会变成「5830.0000 积分」，多出来的两位没有意义
     */
    private static final int AMOUNT_SCALE = 2;

    private LedgerStatSupport() {
    }

    /**
     * 占比。分母为 0 时返回 0 而不是抛异常 —— 统计接口在空数据下必须还能正常返回。
     */
    public static BigDecimal rate(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    public static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    public static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
        return decimal.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 取字符串列。用 {@code String.valueOf} 会把 null 变成 "null" 四个字母显示到页面上，
     * 所以必须先判空。
     */
    public static String toStr(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
