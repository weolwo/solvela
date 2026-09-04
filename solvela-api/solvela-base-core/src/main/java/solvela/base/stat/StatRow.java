package solvela.base.stat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 一行统计结果。
 *
 * <p>后台所有漏斗/看板的取数链路都长这样：{@code SQL 别名 -> Map<String,Object> -> VO}。
 * 中间那个 Map 是 JDBC 给的原始形态，取一个数要写
 * {@code value == null ? 0L : ((Number) value).longValue()} —— 于是七个 Service 各写了一遍
 * {@code toLong}，四个各写了一遍 {@code toDecimal}，取字符串列则干脆散在几十处
 * 三元表达式里。这个类把那些强转收进来，让漏斗代码只剩下业务本身：
 *
 * <pre>
 * 改造前：long total = toLong(row.get("totalCount"));
 * 改造后：long total = row.count("totalCount");
 * </pre>
 *
 * <h3>为什么值得单独建一个类型</h3>
 * 不是为了省那几行，是为了<b>口径只有一份</b>：金额保留几位、空值兜成什么、
 * 分组键为 null 时给前端 null 还是 "null"，十来个统计页面必须一致 ——
 * 各写各的，早晚出现「同一个 0.5 这页显示 50%、那页显示 50.00%」。
 *
 * <h3>⚠️ 字符串 key 是这条链路上唯一没有编译期保护的地方</h3>
 * key 打错一个字母，{@link #count} 安静地返回 0，页面上就是一个「0 条异常」——
 * 编译不报错、SQL 不报错、接口 200。所以每个统计接口都应配一个 key 契约测试，
 * 见 {@code LedgerStatTest}。
 *
 * @param raw JDBC 原样返回的一行，列名 -> 值
 * @Author alaric
 * @Date 2026-09-04
 */
public record StatRow(Map<String, Object> raw) {

    /** 金额保留两位。DECIMAL(18,4) 直接透出会变成「5830.0000 积分」，多出来的两位没有意义 */
    private static final int AMOUNT_SCALE = 2;

    public static StatRow of(Map<String, Object> raw) {
        return new StatRow(raw == null ? Map.of() : raw);
    }

    /** 一次包装一整个结果集，省掉调用方循环里的那层 {@code StatRow.of} */
    public static List<StatRow> of(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows.stream().map(StatRow::of).toList();
    }

    /**
     * 计数列。<b>没这一列也返回 0</b> —— {@code COUNT} 类的 SQL 在空数据下会给 NULL，
     * 而「一条都没有」在业务上就是 0，不是异常。
     */
    public long count(String key) {
        Object value = raw.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }

    /**
     * 金额列，统一两位小数。空值同样兜成 0：{@code SUM} 在没有行时返回 NULL。
     */
    public BigDecimal amount(String key) {
        Object value = raw.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
        return decimal.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 文本列，<b>可能为 null</b>：分组键本身就可能是空的（比如写入侧没填 discard_code）。
     *
     * <p>不用 {@code String.valueOf}：它会把 null 变成 "null" 四个字母显示到页面上，
     * 那比空白更难看懂 —— 运营会以为真有个叫 null 的奖品。
     */
    public String text(String key) {
        Object value = raw.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 主键列，<b>可能为 null</b>。与 {@link #count} 的区别是它不兜 0：
     * id 为 0 是一个具体的（不存在的）行，而 null 表示这一组压根没有归属对象。
     */
    public Long id(String key) {
        Object value = raw.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    /** 小整数列（奖级、档位这类），<b>可能为 null</b>，理由同 {@link #id} */
    public Integer intValue(String key) {
        Object value = raw.get(key);
        return value == null ? null : ((Number) value).intValue();
    }
}
