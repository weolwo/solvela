package solvela.base.stat;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 统计页面上的两种除法：<b>占比</b>与<b>人均</b>。
 *
 * <p>抽出来是因为分母为 0 这件事必须只处理一次。空活动、刚上线的奖池、没人参加的任务，
 * 分母全是 0 —— 统计接口在这些场景下必须还能正常返回，一个 500 会让整个看板白屏，
 * 而看板恰恰是运营用来确认「活动到底有没有跑起来」的地方。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
public final class Rate {

    /** 占比保留四位小数，页面上再乘 100 展示成百分比（即精确到 0.01%） */
    private static final int SHARE_SCALE = 4;

    /** 人均保留两位：「人均 3.47 次」已经足够，再多的位数没有解读价值 */
    private static final int AVERAGE_SCALE = 2;

    private Rate() {
    }

    /**
     * 占比。分母为 0 返回 0。
     *
     * <p>⚠️ 分母取什么是<b>业务口径问题，不是技术问题</b>：中奖率的分母该不该算上未开奖的、
     * 达标率该不该算上进行中的，各个漏斗的答案不一样，所以那个决定留在调用处并在那里写明理由。
     */
    public static BigDecimal share(long part, long whole) {
        if (whole <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), SHARE_SCALE, RoundingMode.HALF_UP);
    }

    /** 人均。人数为 0 返回 0 */
    public static BigDecimal average(long total, long population) {
        if (population <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(population), AVERAGE_SCALE, RoundingMode.HALF_UP);
    }
}
