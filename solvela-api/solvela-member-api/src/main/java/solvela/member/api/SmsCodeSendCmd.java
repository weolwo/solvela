package solvela.member.api;

/**
 * 发送短信验证码的入参。
 *
 * <p>2026-09-10 加上了 {@code currentMemberId}：绑定手机号落地之后，
 * {@link SmsScene#BIND} 要知道「这个号是不是本人已有的」才能决定发不发 ——
 * 判据与 {@link EmailCodeSendCmd} 完全一样。在那之前它确实没有意义，所以之前没有。
 *
 * @param scene           用途。<b>必须由调用方显式指定</b> —— 场景进 Redis key，
 *                        共用的话一个为注册发的码就能拿去重置密码
 * @param phone           收件手机号，任意格式，域内会规范化
 * @param clientIp        客户端 IP，允许为 null（拿不到时放行并打警告）
 * @param currentMemberId 当前登录会员，仅 {@link SmsScene#BIND} 用得到，其余场景为 null
 */
public record SmsCodeSendCmd(SmsScene scene, String phone, String clientIp, Long currentMemberId) {
}
