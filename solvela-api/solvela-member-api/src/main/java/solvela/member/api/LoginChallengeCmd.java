package solvela.member.api;

/**
 * 登录二次验证（观察档设备）的发码 / 验码入参。
 *
 * <h3>为什么凭票，而不是再交一次手机号密码</h3>
 * 以前观察档的做法是：客户端带着「手机号 + 密码 + 验证码」重新调一次登录，验证码则由客户端
 * 自己去匿名的发码接口要。两个问题：
 * <ul>
 *   <li>客户端得把密码一直留在内存里，等用户收码时再交一遍；</li>
 *   <li>🔴 发码接口是匿名的，号码存在就真发 —— 「验码排在密码之后、防短信轰炸」并不成立，
 *       轰炸者根本不走登录接口。</li>
 * </ul>
 * 现在密码验对之后才发一张 5 分钟的一次性凭票，<b>发码与验码都只认它</b>：
 * 不知道密码就拿不到票，拿不到票就发不了码。
 *
 * @param ticket   凭票原文
 * @param code     用户输入的验证码，只有验码那一步用得到
 * @param clientIp 客户端 IP
 * @param deviceId 验签通过的设备号。🔴 必须与签票时的设备一致 —— 票不能拿到别的机器上用
 *
 * @Date 2026-09-27
 */
public record LoginChallengeCmd(String ticket, String code, String clientIp, String deviceId) {
}
