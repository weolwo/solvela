package solvela.marketing.api;

/**
 * 商品列表的查询条件。
 *
 * <p>字段对齐既有的 {@code MallCommodityQuery} 与 {@code idx_mall_cmd_cat_status_sort}，
 * 不另发明 —— 索引就是照这个查询建的。
 *
 * @param categoryId 分类 id，null 表示全部
 * @param keyword    商品名关键字，null 或空表示不筛
 * @param sortBy     排序：{@code SORT}(运营权重，默认) / {@code SOLD}(热销) /
 *                   {@code POINTS_ASC}(积分从低到高)。
 *                   <p>🔴 「热门」按 {@code sold_count} 算，<b>不是</b>一个手工标记 ——
 *                   DDL 里那段解释了为什么不加 is_hot：手工标的热门会和真实数据打架，
 *                   而且没有任何机制提醒运营去撤掉它
 * @param memberId   会员号，用来标记「我收藏过哪些」。<b>未登录传 null</b>
 * @param pageNum    从 1 开始
 * @param pageSize   每页条数，服务端会封顶
 */
public record MallCommodityPageCmd(
        Long categoryId,
        String keyword,
        String sortBy,
        Long memberId,
        Integer pageNum,
        Integer pageSize) {
}
