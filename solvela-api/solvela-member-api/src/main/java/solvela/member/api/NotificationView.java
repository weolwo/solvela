package solvela.member.api;

/**
 * 收件箱列表的一行（通知 tab）。
 *
 * <h3>🔴 这里没有 content，也没有 title</h3>
 * 列表页只需要摘要 + 时间 + 已读态。正文和标题都要查模板、要渲染，
 * 而列表一页几十条 —— 那就是几十次模板查询加几十次渲染，全是白干的。
 *
 * <p>{@code summary} 是<b>发送时就渲染好、直接存在库里</b>的那 128 字，
 * 本身已经是可读的一句话。正文在详情页按需渲染。
 *
 * @param category 分类：SYSTEM / TRADE / MARKETING。端上按它分二级筛选
 * @param readFlag 0-未读 1-已读
 */
public record NotificationView(
        Long id,
        String templateCode,
        String category,
        String summary,
        Integer readFlag,
        String createTime) {
}
