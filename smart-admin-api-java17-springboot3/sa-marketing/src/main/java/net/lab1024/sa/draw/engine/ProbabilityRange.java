package net.lab1024.sa.draw.engine;

import java.math.BigDecimal;

/**
 * 概率区间 [min, max)（百分比 0~100 轴上的一段）
 * 区间由 DrawPoolSnapshot 按坑位顺序累加构建，构造即校验合法性
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record ProbabilityRange(DrawPrizeSnapshot prize, BigDecimal min, BigDecimal max) {

    public ProbabilityRange {
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("概率区间非法: min=" + min + " > max=" + max);
        }
    }

    /**
     * 随机数是否落入本区间（左闭右开）
     */
    public boolean contains(BigDecimal randPercent) {
        return randPercent.compareTo(min) >= 0 && randPercent.compareTo(max) < 0;
    }
}
