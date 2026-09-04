package solvela.base.stat;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据体检清单：把「这个数不为零就说明出事了」这一族判断写成一张<b>能一眼读完的清单</b>。
 *
 * <p>每个漏斗末尾都有一段体检，改造前长这样，一条 4 行、一个页面七八条：
 *
 * <pre>
 * long staleRunning = toLong(row.get("staleRunningCount"));
 * if (staleRunning &gt; 0) {
 *     issues.add("有 " + staleRunning + " 条记录已过有效期却仍是「进行中」：……");
 * }
 * </pre>
 *
 * 三行 if/取值/拼串是<b>每一条都一模一样</b>的仪式，真正的内容只有那句话。收成一行之后，
 * 体检段落读起来就是一张检查项列表，加一条、删一条、核对漏没漏，都不用再看控制流：
 *
 * <pre>
 * checkup.countIf(row.count("staleRunningCount"), "有 {} 条记录已过有效期却仍是「进行中」：……");
 * </pre>
 *
 * <h3>为什么占位符用 {@code {}} 而不是 {@code %d}</h3>
 * 体检文案是写给运营看的中文，里面出现「等于 100%」「占比 5%」是常事，
 * 而 {@code String.format} 见到一个孤立的 {@code %} 会当场抛
 * {@code UnknownFormatConversionException} —— 一个纯文案改动炸掉整个统计接口，
 * 且只在这条体检真的命中时才炸。{@code {}} 没有这个雷，顺带与日志里的写法一致。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
public final class Checkup {

    private final List<String> issues = new ArrayList<>();

    /**
     * 计数型检查：{@code count} 大于 0 才记一条，并把它填进第一个 {@code {}}。
     *
     * @param count    异常条数，通常直接来自 {@link StatRow#count}
     * @param template 说明文案，依次用 {@code {}} 占位
     * @param extra    第一个之后的占位值
     */
    public Checkup countIf(long count, String template, Object... extra) {
        if (count <= 0) {
            return this;
        }
        Object[] args = new Object[extra.length + 1];
        args[0] = count;
        System.arraycopy(extra, 0, args, 1, extra.length);
        issues.add(fill(template, args));
        return this;
    }

    /**
     * 条件型检查：给「不是简单地数大于零」的那几条用，
     * 比如「有人在排队 <b>且</b> 最久的那个已经等过一天」。
     */
    public Checkup when(boolean broken, String template, Object... args) {
        if (broken) {
            issues.add(fill(template, args));
        }
        return this;
    }

    /** 体检结论。没查出问题就是空列表，前端据此显示「一切正常」 */
    public List<String> issues() {
        return issues;
    }

    private static String fill(String template, Object... args) {
        StringBuilder text = new StringBuilder(template);
        for (Object arg : args) {
            int slot = text.indexOf("{}");
            if (slot < 0) {
                break;
            }
            text.replace(slot, slot + 2, String.valueOf(arg));
        }
        return text.toString();
    }
}
