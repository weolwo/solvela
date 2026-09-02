package solvela.draw.engine;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 抽奖命中引擎（纯函数，无状态、无 IO —— 概率计算与库存扣减彻底分离）。
 *
 * <p>判定优先级：
 * <ol>
 *   <li><b>白名单必中</b>：用户在某奖项白名单内且该奖项快照有库存 -> 直接命中（仍受库存约束）；</li>
 *   <li><b>概率区间命中</b>：随机数落入哪个 {@code [min, max)} 区间就命中哪个奖项；</li>
 *   <li><b>库存降级</b>：概率命中的奖项快照无库存 -> 降级到兜底奖项；兜底也不可用 -> NoStock。</li>
 * </ol>
 *
 * <p>本引擎的库存判断基于快照（快速失败），最终一致性由外层 Redis Lua 原子扣减 + DB 条件更新保证。
 *
 * <p>概率单位见 {@link Ppm}：引擎内部全整数，不存在小数与容差。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public final class DrawEngine {

    private DrawEngine() {
    }

    /** 执行一次抽奖判定（随机数由内部生成） */
    public static DrawResult draw(DrawPoolSnapshot pool, String memberName) {
        return draw(pool, memberName, ThreadLocalRandom.current().nextInt(Ppm.FULL));
    }

    /**
     * 执行一次抽奖判定（随机数外部注入，供测试与审计回放使用）。
     *
     * @param randPpm {@code [0, }{@link Ppm#FULL}{@code )} 区间的随机数
     */
    public static DrawResult draw(DrawPoolSnapshot pool, String memberName, int randPpm) {
        // 1. 白名单必中（按坑位顺序取第一个命中的白名单奖项；无库存的白名单奖项跳过）
        for (ProbabilityRange range : pool.ranges()) {
            DrawPrizeSnapshot prize = range.prize();
            if (prize.inWhiteList(memberName) && prize.hasStock()) {
                return new DrawResult.Hit(prize, DrawResult.HitSource.WHITE_LIST);
            }
        }

        // 2. 概率区间命中
        DrawPrizeSnapshot candidate = locate(pool, randPpm);
        if (candidate.hasStock()) {
            return new DrawResult.Hit(candidate, DrawResult.HitSource.PROBABILITY);
        }

        // 3. 命中奖项无库存 -> 降级兜底
        DrawPrizeSnapshot fallback = pool.fallbackPrize();
        if (fallback != null && fallback.prizeItemId() != candidate.prizeItemId() && fallback.hasStock()) {
            return new DrawResult.Hit(fallback, DrawResult.HitSource.FALLBACK_DEGRADE);
        }
        return new DrawResult.NoStock(candidate.prizeCode());
    }

    /**
     * 概率区间定位。
     *
     * <p>区间恰好铺满 {@code [0, FULL)}（快照构造器精确校验闭环），随机数取自
     * {@code nextInt(FULL)}，所以<b>必有命中</b>，落空是不可能的。
     *
     * <p>⚠️ 改造前这里在落空时静默回落到最后一个区间 —— 那是因为百分比小数下闭环只能容差判定，
     * 总和 99.9999 也算「闭环」，于是随机数真的可能落在所有区间之外。
     * 整数化之后这个分支不可达了，所以改成抛异常：它只可能由「有人绕过 of() 直接
     * 构造了非法快照」触发，静默回落会把配置错误变成一个悄悄偏斜的概率分布。
     */
    private static DrawPrizeSnapshot locate(DrawPoolSnapshot pool, int randPpm) {
        for (ProbabilityRange range : pool.ranges()) {
            if (range.contains(randPpm)) {
                return range.prize();
            }
        }
        throw new IllegalStateException("随机数落在所有概率区间之外，奖池快照非法: "
                + pool.poolCode() + " randPpm=" + randPpm);
    }
}
