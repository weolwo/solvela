package solvela.draw.engine;

/**
 * 抽奖命中结果（sealed：结果类型有限且互斥，调用方 switch 模式匹配可获编译期穷尽性检查——
 * 将来新增结果类型时，所有消费端漏改会直接编译报错，而不是运行时漏判）
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public sealed interface DrawResult permits DrawResult.Hit, DrawResult.NoStock {

    /**
     * 命中来源
     */
    enum HitSource {
        /** 概率区间命中 */
        PROBABILITY,
        /** 白名单必中 */
        WHITE_LIST,
        /** 概率命中奖项无库存，降级到兜底奖项 */
        FALLBACK_DEGRADE
    }

    /**
     * 命中某奖项（库存扣减由外层 Lua 原子执行，本结果仅为候选判定）
     */
    record Hit(DrawPrizeSnapshot prize, HitSource source) implements DrawResult {
    }

    /**
     * 无可发奖项：命中奖项无库存且兜底也不可用（或不存在兜底）
     */
    record NoStock(String candidatePrizeCode) implements DrawResult {
    }
}
