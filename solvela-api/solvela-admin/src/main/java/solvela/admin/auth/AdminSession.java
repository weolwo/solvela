package solvela.admin.auth;

/**
 * 一个令牌背后的会话。存在 Redis 里，每个请求反序列化一次，所以<b>只放最小必要信息</b>。
 *
 * <p>特别是：<b>不放员工姓名、部门、权限列表</b>。那些会变，而缓存里的副本不会跟着变——
 * 「改了角色要等 30 分钟才生效」这类问题的根因就是把可变数据塞进了会话。
 * 身份详情每次请求从 {@code LoginManager} 的缓存取，那份缓存有明确的失效点。
 *
 * @param employeeId 员工 id
 * @param superFlag  是否万能密码登录。<b>必须是会话上的一个字段</b>——
 *                   原先它编码在 sa-token 的 loginId 字符串里（{@code S:uuid:员工id}），
 *                   于是「怎么判断这是万能密码登录」变成了四处各自做字符串切割，
 *                   而 {@code getEmployeeIdByLoginId} 里那段 {@code substring(2)} 一旦
 *                   员工 id 位数变化就会静默取错人
 * @param device     登录设备，来自 {@code LoginDeviceEnum}，只用于展示与审计
 */
public record AdminSession(Long employeeId, boolean superFlag, String device) {
}
