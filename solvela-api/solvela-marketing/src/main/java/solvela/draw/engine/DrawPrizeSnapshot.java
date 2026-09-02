package solvela.draw.engine;

import java.util.Set;

/**
 * 奖项快照（引擎输入的不可变值对象）。<b>纯配置，不含库存。</b>
 *
 * <p>库存是批内会变的量，住在 {@link LocalInventory} 里 —— 它原本是这里的一个
 * {@code remainStock} 字段，导致「批内扣掉一份」只能重建整棵结构。
 *
 * @param prizeItemId 奖项ID（t_prize_pool_item.id）
 * @param prizeCode   奖品编码
 * @param fallback    是否兜底奖项（命中奖项无库存时降级到它）
 * @param whiteList   白名单：名单内用户库存充足时必中本奖项
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawPrizeSnapshot(long prizeItemId, String prizeCode, boolean fallback, Set<String> whiteList) {

    public DrawPrizeSnapshot {
        // 防御 null，保证消费端可直接 contains 判定
        whiteList = whiteList == null ? Set.of() : Set.copyOf(whiteList);
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
