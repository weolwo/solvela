package solvela.biz.server;

import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.member.api.SmsCodeSendCmd;
import solvela.member.api.SmsScene;
import solvela.member.auth.MemberAuthService;
import solvela.member.util.MemberPhoneUtil;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试里拿一个<b>真实发出来的</b>短信验证码。
 *
 * <h3>为什么不直接往 Redis 里塞一个码</h3>
 * 塞进去的话，「发码」那一半就没被跑过 —— 而手机号注册的前置条件恰恰是它。
 * 走真链路的代价只是多一次调用，换来的是：发码的规范化与验码的规范化<b>必须一致</b>
 * 这条性质，在每一个用到手机号注册的用例里都被顺带验了一遍。
 *
 * <p>test profile 的 {@code sms-transport: LOG} 让这条链路不需要任何厂商就能跑通 ——
 * 码只是被打进日志，而这里从 Redis 把它读回来。
 *
 * <p>抽成共享类而不是每个测试类抄一份：这段代码知道<b>验证码在 Redis 里长什么样</b>
 * （key 的拼法、值的分隔符），那是 {@code VerificationCodeStore} 的内部约定。
 * 抄三份的话，改一次存储格式要改三处，而漏掉的那处会红得莫名其妙。
 *
 * @Date 2026-09-10
 */
final class TestSmsCode {

    private TestSmsCode() {
    }

    /** 发一条注册验证码并把它读回来。 */
    static String issue(MemberAuthService authService, RedisService redisService,
                        PiiHasher piiHasher, String phone, String clientIp) {
        assertTrue(authService.sendSmsCode(
                        new SmsCodeSendCmd(SmsScene.REGISTER, phone, clientIp, null)).success(),
                "前提不成立：短信验证码没发出去");
        String key = redisService.generateRedisKey("mbr:code:",
                "sms:" + SmsScene.REGISTER.name() + ":" + piiHasher.hash(MemberPhoneUtil.normalize(phone)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split(java.util.regex.Pattern.quote("|"))[0];
    }
}
