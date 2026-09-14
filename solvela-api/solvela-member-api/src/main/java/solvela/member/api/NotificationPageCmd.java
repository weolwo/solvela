package solvela.member.api;

/**
 * 收件箱分页参数。
 *
 * <p>🔴 <b>memberId 由网关从登录态填</b>，不是客户端传的。
 * 契约里有它是因为这是服务间调用 —— 客户端那一层的
 * {@code NotificationController} 压根不收这个字段。
 *
 * @param category   分类过滤，null = 全部
 * @param unreadOnly 只看未读，null = 不限
 */
public record NotificationPageCmd(
        Long memberId,
        String category,
        Boolean unreadOnly,
        Integer pageNum,
        Integer pageSize) {
}
