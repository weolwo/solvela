package solvela.marketing.api;

/**
 * 活动里的一个奖品（C 端形状）。转盘的每一格对应一条。
 *
 * <h3>🔴 来源是奖池，不是运营手写的 JSON</h3>
 * 这些条目取自 {@code t_prize_pool_item}（关联 {@code t_prize_config}）——
 * <b>抽奖引擎真正抽的就是这张表</b>。
 *
 * <p>此前 C 端的转盘是从 {@code t_activity_display.extra_config} 里一段
 * 手写 JSON 解析的。那是<b>第二个源</b>，两个后果都发生过：
 * <ul>
 *   <li>没写 → {@code extra_config} 为 null → 转盘一格都没有，
 *       用户点进活动页什么都看不到（2026-09-05 的现场就是这个）；</li>
 *   <li>写了但和奖池对不上 → 转盘上转出一个奖池里根本没有的奖，
 *       或者能中的奖压根不在盘面上。</li>
 * </ul>
 * 展示的奖品必须和会发的奖品同源 —— 和「任务展示判据要跟发奖判据同源」是同一条。
 *
 * <h3>🔴 这些字段刻意<b>不</b>下发</h3>
 * <ul>
 *   <li>{@code total_stock} / {@code used_stock} —— 剩多少能反推中奖概率；</li>
 *   <li>{@code user_max_count} —— 同上，还会暴露风控口径；</li>
 *   <li>{@code white_list} —— <b>里面是会员号</b>，一条都不能出公网；</li>
 *   <li>{@code prize_value} / {@code promotion_config_id} / {@code approve_mode}
 *       —— 预算与审批是内部口径。奖品叫什么本身就够展示了。</li>
 * </ul>
 * 往这个 record 上加字段之前，先回答「用户看到它能做什么」。
 *
 * @param prizeType  对齐 {@code PrizeTypeEnum}。<b>{@code MARKER} 就是「谢谢参与」那一格</b> ——
 *                   它是正常的一格，不是「没有奖品」
 * @param prizeLevel 运营配的奖品等级，<b>0 表示未分级</b>。端上据此决定要不要把某一格
 *                   画得更显眼 —— 具体怎么画是端上的事，域不管样式
 */
public record ActivityPrizeView(
        String prizeCode,
        String prizeName,
        String prizeType,
        int prizeLevel) {
}
