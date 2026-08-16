package sa.draw.engine;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抽奖命中引擎（纯函数，无状态、无 IO——概率计算与库存扣减彻底分离）
 *
 * 判定优先级：
 * 1. 白名单必中：用户在某奖项白名单内且该奖项快照有库存 -> 直接命中（仍受库存约束，与产品语义一致）
 * 2. 概率区间命中：随机数落入哪个 [min,max) 区间命中哪个奖项
 * 3. 库存降级：概率命中的奖项快照无库存 -> 降级到兜底奖项；兜底也不可用 -> NoStock
 *
 * 注意：本引擎的库存判断基于快照（快速失败），最终一致性由外层 Redis Lua 原子扣减 + DB 乐观锁保证
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public final class DrawEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private DrawEngine() {
    }

    /**
     * 执行一次抽奖判定（随机数由内部生成）
     */
    public static DrawResult draw(DrawPoolSnapshot pool, String memberName) {
        return draw(pool, memberName, randomPercent());
    }

    /**
     * 执行一次抽奖判定（随机数外部注入，供测试与审计回放使用）
     *
     * @param randPercent [0, 100) 区间的随机数
     */
    public static DrawResult draw(DrawPoolSnapshot pool, String memberName, BigDecimal randPercent) {
        // 1. 白名单必中（按坑位顺序取第一个命中的白名单奖项；无库存的白名单奖项跳过）
        for (ProbabilityRange range : pool.ranges()) {
            DrawPrizeSnapshot prize = range.prize();
            if (prize.inWhiteList(memberName) && prize.hasStock()) {
                return new DrawResult.Hit(prize, DrawResult.HitSource.WHITE_LIST);
            }
        }

        // 2. 概率区间命中
        DrawPrizeSnapshot candidate = locate(pool, randPercent);
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
     * 概率区间定位。概率闭环由快照构造校验保证，理论上必有命中；
     * 浮点边界导致 randPercent 恰好等于累加上限时，回落到最后一个区间
     */
    private static DrawPrizeSnapshot locate(DrawPoolSnapshot pool, BigDecimal randPercent) {
        for (ProbabilityRange range : pool.ranges()) {
            if (range.contains(randPercent)) {
                return range.prize();
            }
        }
        return pool.ranges().get(pool.ranges().size() - 1).prize();
    }

    /**
     * 生成 [0, 100) 随机数，4 位小数精度与概率配置精度对齐
     */
    private static BigDecimal randomPercent() {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())
                .multiply(HUNDRED)
                .setScale(4, java.math.RoundingMode.DOWN);
    }
}
