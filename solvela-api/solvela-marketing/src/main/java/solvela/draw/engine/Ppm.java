package solvela.draw.engine;

import java.math.BigDecimal;

/**
 * 抽奖概率的内部单位：<b>百万分之一</b>（parts per million）。整个引擎只认这一个单位。
 *
 * <h3>为什么不是 BigDecimal 百分比</h3>
 * 概率本来就是个整数量 —— {@code t_pool_prize_mapping.probability} 是 {@code decimal(8,4)}
 * 的百分比，最小步长 0.0001%，正好是百万分之一。写成小数之后，BigDecimal 的三个卖点全部落空：
 * <ul>
 *   <li><b>精度</b>没有额外收益（本来就没有小数）；</li>
 *   <li><b>精确比较</b>反而做不到 —— 闭环只能写成
 *       {@code |total - 100| <= 0.0001} 的容差判定，而整数下就是 {@code total == FULL}；</li>
 *   <li><b>可读性</b>是负的：{@code min.compareTo(max) > 0} 要在脑子里翻译回 {@code min > max}。</li>
 * </ul>
 * 代价则是实打实的：每次抽奖生成随机数要 double -> BigDecimal -> 乘 -> 截断四步、
 * 每个坑位两次 {@code compareTo}，全在事务内的热路径上，连抽再乘 N。
 *
 * <h3>单位为什么进类型名</h3>
 * 改造前 {@code BigDecimal probability} 这个签名不携带单位，于是单位只能靠注释传递，
 * 而注释会漂 —— 事实上已经漂了：建表语句与实体注释都写着「万分位」，
 * 但闭环校验对着 100 做、报错文案写「必须等于100%」、前端渲染 {@code {{ probability }}%}，
 * <b>真实单位是百分比，那两处注释是错的</b>。
 * 叫 {@code ppm} 之后这种漂移写不出来。
 *
 * <h3>边界在哪</h3>
 * DB 与后台仍用百分比（运营看的是「10.95%」，不是「109500」）。
 * 百分比 -> ppm 的转换<b>只有 {@link #fromPercent} 一处</b>，就在组装快照时。
 * 越过这条边界之后，引擎内部不存在小数。
 */
public final class Ppm {

    /** 100%。奖池所有坑位的 ppm 之和必须恰好等于它 */
    public static final int FULL = 1_000_000;

    /** 百分比小数点后 4 位 -> ppm，即 ×10^4 */
    private static final int PERCENT_TO_PPM_SHIFT = 4;

    private Ppm() {
    }

    /**
     * 百分比 -> ppm。{@code 10.95} -> {@code 109500}。
     *
     * <p>用 {@code intValueExact} 而不是 {@code intValue()}：小数位超过 4 位时<b>抛异常而不是静默舍入</b>。
     * 库里的列是 {@code decimal(8,4)}，取出来的值不可能超 4 位，所以这条路上抛不出来；
     * 真抛了说明有人绕过 DB 直接构造了配置，那正是该炸的时候 —— 静默舍入会让某个坑位的
     * 概率悄悄变化，而所有校验依然通过。
     */
    public static int fromPercent(BigDecimal percent) {
        if (percent == null) {
            // 列是 NOT NULL，走到这里说明快照组装的数据源不对
            throw new IllegalArgumentException("概率不能为空");
        }
        return percent.movePointRight(PERCENT_TO_PPM_SHIFT).intValueExact();
    }

    /**
     * 一组百分比之和是否<b>恰好</b>闭环到 100%。
     *
     * <h3>为什么必须与引擎同一份实现</h3>
     * 判定闭环的地方有四处：运行态的 {@link DrawPoolSnapshot} 构造器、后台保存前校验、
     * 奖池看板、奖池分析页。改造前它们各写了一遍
     * {@code |total - 100| <= 0.0001}，注释还互相叮嘱「两边必须同时改」——
     * 而<b>只要口径漂一点，后台保存得下去的配置就会在抽奖时构造快照失败</b>，
     * 表现是这个奖池的每一次抽奖请求都直接报错。
     *
     * <p>所以口径收到这里一处：{@link DrawPoolSnapshot} 用 {@code == FULL}，
     * 其余三处调本方法，两者是同一个判断。
     *
     * @param totalPercent 各坑位百分比之和；{@code null} 视为不闭环
     */
    public static boolean isClosedPercent(BigDecimal totalPercent) {
        if (totalPercent == null) {
            return false;
        }
        try {
            return fromPercent(totalPercent) == FULL;
        } catch (ArithmeticException e) {
            // 小数位超过 4 位，列本身就存不下，不可能是一份闭环配置
            return false;
        }
    }

    /** ppm -> 给人看的百分比文本，只用于报错与日志 */
    public static String toPercentText(int ppm) {
        return BigDecimal.valueOf(ppm, PERCENT_TO_PPM_SHIFT).toPlainString() + "%";
    }
}
