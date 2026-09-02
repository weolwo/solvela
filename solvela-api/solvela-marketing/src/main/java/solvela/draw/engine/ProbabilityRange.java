package solvela.draw.engine;

/**
 * 概率区间 {@code [min, max)}，单位 {@link Ppm}（0 ~ 1000000 轴上的一段）。
 * 区间由 {@link DrawPoolSnapshot} 按坑位顺序累加得出，构造即校验合法性。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record ProbabilityRange(DrawPrizeSnapshot prize, int min, int max) {

    public ProbabilityRange {
        if (min > max) {
            throw new IllegalArgumentException("概率区间非法: min=" + min + " > max=" + max);
        }
    }

    /** 随机数是否落入本区间（左闭右开） */
    public boolean contains(int randPpm) {
        return randPpm >= min && randPpm < max;
    }
}
