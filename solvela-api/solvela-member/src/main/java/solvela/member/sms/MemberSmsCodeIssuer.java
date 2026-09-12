package solvela.member.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.crypto.PiiHasher;
import solvela.member.api.SmsCodeFailReason;
import solvela.member.api.SmsCodeSendResult;
import solvela.member.api.SmsDelivery;
import solvela.member.api.SmsScene;
import solvela.member.register.MemberRegisterDao;
import solvela.member.util.MemberPhoneUtil;

/**
 * 决定这条短信<b>到底发不发</b>。与 {@code MemberEmailCodeIssuer} 逐条对称。
 *
 * <h3>🔴 它补的是我自己留下的一条待办</h3>
 * {@code MemberSmsCodeService} 的类注释上原本写着：
 * 「将来加手机号登录 / 重置时，要照邮箱那边补上 SUPPRESS」。
 * 2026-09-10 手机号找回密码与绑定手机号一起落地，这条到期了。
 *
 * <h3>为什么短信更需要这一层</h3>
 * 邮箱那边 SUPPRESS 主要防的是「给没有账号的地址寄信」。短信这边多两层代价：
 * <ul>
 *   <li><b>钱</b>。给一个根本没注册过的号码发重置码是纯浪费，
 *       而攻击者可以拿一份号码库把它刷成实打实的账单；</li>
 *   <li><b>骚扰</b>。我们的短信会真的响在陌生人手机上。
 *       注册场景挡不住（要能注册新号码，这是它自带的），
 *       但登录和重置<b>完全没必要</b>放行。</li>
 * </ul>
 *
 * <p>⚠️ 无论发不发，<b>返回给调用方的结果都一样</b>，而且配额照常消耗 ——
 * 差别只在「有没有真的发出去」。回答不一样的话，这个接口就成了账号枚举器。
 *
 * @Date 2026-09-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSmsCodeIssuer {

    private final MemberSmsCodeService smsCodeService;

    private final MemberRegisterDao memberRegisterDao;

    private final PiiHasher piiHasher;

    public SmsCodeSendResult issue(SmsScene scene, String rawPhone, String clientIp,
                                   Long currentMemberId) {
        String phone = MemberPhoneUtil.normalize(rawPhone);
        if (phone == null) {
            // 格式不对可以明说：一个非法的串本来就不可能是任何人的号码，不泄露任何信息
            return SmsCodeSendResult.fail(SmsCodeFailReason.BAD_PHONE_FORMAT);
        }
        return smsCodeService.send(scene, phone, clientIp, decideDelivery(scene, phone, currentMemberId));
    }

    private SmsDelivery decideDelivery(SmsScene scene, String phone, Long currentMemberId) {
        String hash = piiHasher.hash(phone);
        boolean exists = memberRegisterDao.countByPhoneHash(hash) > 0;
        SmsDelivery delivery = switch (scene) {
            // 已经注册过的号码不需要注册码。它照样能收到「该号已注册」——
            // 那句话藏不掉（唯一约束自带），但没必要再花一条短信去说
            case REGISTER -> exists ? SmsDelivery.SUPPRESS : SmsDelivery.DELIVER;
            // 登录和重置：没有账号就没有可登录/可重置的东西，不发
            case LOGIN, RESET_PASSWORD -> exists ? SmsDelivery.DELIVER : SmsDelivery.SUPPRESS;
            /*
             * 🔴 绑定看的是「被【别人】占了没有」，不是「有没有人占」。
             *    按 exists 判的话，会员想给【自己的旧号码】发码
             *    （换绑流程里那一步）就永远收不到 —— 而那条路正是
             *    没设过密码的会员唯一能换绑手机号的方式。
             */
            case BIND -> memberRegisterDao.countByPhoneHashExcludingMember(hash, currentMemberId) > 0
                    ? SmsDelivery.SUPPRESS
                    : SmsDelivery.DELIVER;
        };
        if (delivery == SmsDelivery.SUPPRESS) {
            // 只打日志，不改返回值。这行日志是排查「用户说没收到码」时的唯一线索 ——
            // 而那种工单里，多数情况恰恰是他记错了自己用哪个号注册的
            log.info("【短信验证码】场景与账号状态不匹配，静默不发, scene: {}, exists: {}, phone: {}",
                    scene, exists, MemberPhoneUtil.mask(phone));
        }
        return delivery;
    }
}
