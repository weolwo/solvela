package solvela.base.partition;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 一个按月分区的分区名与边界之间的换算。<b>只有这一处定义这套命名。</b>
 *
 * <h3>约定</h3>
 * <pre>
 *   名字  p202610
 *   边界  VALUES LESS THAN ('2026-11-01')   ← 下个月 1 号，即「小于」它
 *   兜底  pmax  VALUES LESS THAN (MAXVALUE)
 * </pre>
 *
 * <h3>🔴 为什么边界是「下个月 1 号」而不是「本月最后一天」</h3>
 * {@code LESS THAN} 是开区间。写成本月最后一天的话，那一天<b>整天的数据</b>都会落进下一个分区 ——
 * 不报错，只是按月查的时候每个月都少一天、多一天。
 *
 * @Date 2026-10-16
 */
public final class MonthlyPartition {

    /** 兜底分区的名字。它永远是最后一个，而且<b>永远不能被删</b> */
    public static final String MAX_PARTITION = "pmax";

    private MonthlyPartition() {
    }

    public static String nameOf(YearMonth month) {
        return String.format("p%04d%02d", month.getYear(), month.getMonthValue());
    }

    /** 这个分区能装到哪一天（不含）。即下个月 1 号 */
    public static LocalDate upperBound(YearMonth month) {
        return month.plusMonths(1).atDay(1);
    }

    /** {@code p202610} → {@code 2026-10}；不是这个形状（含 pmax）返回 null */
    public static YearMonth monthOf(String partitionName) {
        if (partitionName == null || partitionName.length() != 7 || partitionName.charAt(0) != 'p') {
            return null;
        }
        try {
            return YearMonth.of(Integer.parseInt(partitionName.substring(1, 5)),
                    Integer.parseInt(partitionName.substring(5, 7)));
        } catch (NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 从 {@code information_schema} 读出来的 {@code partition_description} 里取出日期。
     *
     * <p>RANGE COLUMNS 的描述形如 {@code '2026-11-01'}（<b>带单引号</b>）；
     * 兜底分区是 {@code MAXVALUE}。取不出日期返回 null。
     */
    public static LocalDate boundaryOf(String partitionDescription) {
        if (partitionDescription == null) {
            return null;
        }
        String text = partitionDescription.trim().replace("'", "").replace("\"", "");
        if (text.isEmpty() || "MAXVALUE".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            // 可能带时间部分（'2026-11-01 00:00:00'），只取日期那一截
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
