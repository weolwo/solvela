package solvela.member.api;

/**
 * 一条「刚才是你吗」。
 *
 * <h3>🔴 刻意不带设备号，也不带 IP</h3>
 * 客户端答题只需要单号；设备号和 IP 对用户没有任何意义，而它们一旦出现在响应里，
 * 就成了「偷到一个会话的人能读到的账号信息」。回答时的设备判断在服务端做，不信客户端。
 *
 * @param promptId   答题凭它。一次性，答完即失效
 * @param deviceType APP / H5 / PC …；拿不到时为 null，客户端显示「未知设备」
 * @param location   IP 归属地（人话，不是 IP）。拿不到时为 null
 * @param loginTime  那次登录的时间（毫秒）。与「我的登录设备」同一个口径
 */
public record SecurityPromptView(String promptId, String deviceType, String location, long loginTime) {
}
