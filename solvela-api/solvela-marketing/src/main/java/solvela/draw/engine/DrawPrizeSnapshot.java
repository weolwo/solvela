package solvela.draw.engine;

import java.util.Set;

/**
 * 奖项快照（引擎输入的不可变值对象）
 * 由调用方在抽奖前从缓存/DB 组装；remainStock 为快照参考值，最终扣减以 Redis Lua 原子操作为准
 *
 * @param prizeItemId 奖项ID（t_prize_pool_item.id）
 * @param prizeCode   奖品编码
 * @param fallback    是否兜底奖项（命中奖项无库存时降级到它）
 * @param remainStock 剩余库存快照：-1 表示不限量
 * @param whiteList   白名单：名单内用户库存充足时必中本奖项
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawPrizeSnapshot(long prizeItemId, String prizeCode, boolean fallback, int remainStock, Set<String> whiteList) {

    public static final int UNLIMITED = -1;

    public DrawPrizeSnapshot {
        // 防御 null，保证消费端可直接 contains 判定
        whiteList = whiteList == null ? Set.of() : Set.copyOf(whiteList);
    }

    /**
     * 快照层面是否还有可发库存
     */
    public boolean hasStock() {
        return remainStock == UNLIMITED || remainStock > 0;
    }

    /**
     * ⚠️ 白名单按<b>账号</b>匹配，不是会员号 —— {@code t_prize_pool_item.white_list}
     * 里存的是运营手填的一串账号。这意味着<b>白名单用户改名之后名单会静默失效</b>：
     * 抽奖照常进行，只是这个人不再必中，没有任何报错。
     *
     * <p>v3.71.0 换关联键时刻意没顺手改这里：白名单是<b>运营配置</b>，
     * 改成会员号要连配置页、存量数据一起迁，属于会员域收口后的独立一步（交接文档 §13.7）。
     */
    public boolean inWhiteList(String memberName) {
        return memberName != null && whiteList.contains(memberName);
    }
}
