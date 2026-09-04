package solvela.base.stat;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collection;

/**
 * 一条配置体检告警。
 *
 * <p>五个体检页面（奖池概率、奖项库存、奖池配置、彩票配置、奖级规则）原本各有一个
 * 字段完全一样的 {@code XxxIssueDTO}，连 {@code danger} / {@code warn} 两个工厂方法
 * 都是一字不差的抄写。抄五份的代价不在字数，在于「DANGER / WARN 这两个字符串
 * 到底有几处定义」这件事没人说得清 —— 而各处又都在用
 * {@code "DANGER".equals(i.getLevel())} 做统计，写错一个字母就是永远数出 0 条严重告警，
 * 页面看起来一片绿。
 *
 * <h3>只说问题，不给按钮</h3>
 * 体检结论只描述「发现了什么、会导致什么后果」，不带修复动作。
 * 概率与库存是资损敏感配置，一键改错的代价远大于让人多点两下。
 *
 * <h3>级别为什么留成 String</h3>
 * 它要原样出现在接口 JSON 里给前端分色。工程里的枚举走 {@code @EnumSerialize}
 * 输出的是 value 而不是名字，换成枚举等于悄悄改了接口契约。
 * 判定一律用 {@link #isDanger()} / {@link #isWarn()}，不要在外面再写字符串比较。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
@Data
@AllArgsConstructor
public class HealthIssue {

    /** 会导致功能报错、发不出奖或资损 */
    public static final String DANGER = "DANGER";

    /** 配置可疑但还能跑 */
    public static final String WARN = "WARN";

    /** 告警编码，供前端分组，不直接展示 */
    private String code;

    /** 严重级别：{@link #DANGER} / {@link #WARN} */
    private String level;

    /** 人话说明：发现了什么，以及会导致什么后果 */
    private String message;

    public static HealthIssue danger(String code, String message) {
        return new HealthIssue(code, DANGER, message);
    }

    public static HealthIssue warn(String code, String message) {
        return new HealthIssue(code, WARN, message);
    }

    public boolean isDanger() {
        return DANGER.equals(level);
    }

    public boolean isWarn() {
        return WARN.equals(level);
    }

    /**
     * 数严重告警。
     *
     * <p>页面按「严重条数」排序与置顶，所以这个数就是运营看到的优先级。
     * 各体检页自己写 {@code filter(i -> "DANGER".equals(i.getLevel())).count()} 时，
     * 拼错的那一处不会报错，只会安静地永远返回 0。
     */
    public static int countDanger(Collection<HealthIssue> issues) {
        return count(issues, DANGER);
    }

    public static int countWarn(Collection<HealthIssue> issues) {
        return count(issues, WARN);
    }

    private static int count(Collection<HealthIssue> issues, String level) {
        if (issues == null) {
            return 0;
        }
        return (int) issues.stream().filter(issue -> level.equals(issue.getLevel())).count();
    }
}
