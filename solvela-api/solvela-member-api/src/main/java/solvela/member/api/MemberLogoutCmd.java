package solvela.member.api;

/**
 * 退出登录的留痕入参。
 *
 * <p>这里没有令牌 —— <b>吊销令牌是接入层的事</b>。会话模型（不透明令牌？JWT？多久过期？）
 * 由各端自己定，会员域只负责把「这个人退出了」记进登录日志。
 *
 * @param memberId 会员号
 * @param clientIp 客户端 IP，允许为 null
 */
public record MemberLogoutCmd(Long memberId, String clientIp) {
}
