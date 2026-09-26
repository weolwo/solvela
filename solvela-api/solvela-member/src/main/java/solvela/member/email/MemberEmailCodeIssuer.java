package solvela.member.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.crypto.PiiHasher;
import solvela.member.api.EmailCodeFailReason;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendResult;
import solvela.member.api.MailDelivery;
import solvela.member.register.MemberRegisterDao;
import solvela.member.util.MemberEmailUtil;

/**
 * 决定<b>这封验证码信到底寄不寄</b>，然后交给 {@link MemberEmailCodeService} 去发。
 *
 * <h3>为什么单独一层</h3>
 * {@code MemberEmailCodeService} 的类注释写着「只管码本身，不管业务」——
 * 它不该知道 {@code t_member} 的存在。而「这个邮箱有没有会员」恰恰是业务判断，
 * 且四个场景的判据<b>正好相反</b>：
 * <pre>
 *   REGISTER        已被注册   -> 不寄（他该去登录，而不是被告知「已注册」）
 *   LOGIN           查无此人   -> 不寄
 *   RESET_PASSWORD  查无此人   -> 不寄
 *   BIND            已被别人占 -> 不寄
 * </pre>
 *
 * <h3>🔴 「不寄」不等于「报错」</h3>
 * 四种情况一律<b>返回成功</b>。如实回答等于送出一个账号枚举接口：
 * 输入一个邮箱，看回的是「已注册」还是「未注册」，就知道这人是不是本站用户。
 *
 * <p>而且不寄的时候<b>码照样存、限频照样计</b>（{@link MailDelivery#SUPPRESS}）——
 * 否则漏洞会转移到校验那一步：随便输个错码，有账号回「验证码错误」、
 * 没账号回「验证码已失效」。见 {@code MailDelivery} 的类注释。
 *
 * @Date 2026-09-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberEmailCodeIssuer {

    private final MemberEmailCodeService emailCodeService;

    private final MemberRegisterDao memberRegisterDao;

    private final PiiHasher piiHasher;

    /**
     * 发一封验证码信。
     *
     * @param currentMemberId 当前登录会员，仅 {@link EmailCodeScene#BIND} 用得到；
     *                        其余场景传 null（那几条本来就是匿名接口）
     */
    public EmailCodeSendResult issue(EmailCodeScene scene, String rawEmail,
                                     String clientIp, Long currentMemberId) {
        String email = MemberEmailUtil.normalize(rawEmail);
        if (email == null) {
            // 格式不对可以明说：一个非法的串本来就不可能是任何人的邮箱，不泄露任何信息
            return EmailCodeSendResult.fail(EmailCodeFailReason.BAD_EMAIL_FORMAT);
        }

        MailDelivery delivery = decideDelivery(scene, email, currentMemberId);
        return emailCodeService.send(scene, email, clientIp, delivery);
    }

    /**
     * 这封信寄不寄。
     *
     * <p>用 switch 表达式：新增场景时<b>编译不过</b>，而不是悄悄落进某个默认行为 ——
     * 而这里的默认行为无论选哪个都是错的（默认寄 = 给陌生邮箱发垃圾，
     * 默认不寄 = 新功能上线后没人收得到码）。
     */
    private MailDelivery decideDelivery(EmailCodeScene scene, String email, Long currentMemberId) {
        String hash = piiHasher.hash(email);
        boolean exists = memberRegisterDao.countByEmailHash(hash) > 0;
        MailDelivery delivery = switch (scene) {
            case REGISTER -> exists ? MailDelivery.SUPPRESS : MailDelivery.DELIVER;
            case LOGIN, RESET_PASSWORD -> exists ? MailDelivery.DELIVER : MailDelivery.SUPPRESS;
            // 🔴 绑定看的是「被【别人】占了没有」，不是「有没有人占」。
            //    按 exists 判的话，会员想重发一次绑到自己名下那个邮箱的码
            //    （换绑流程里要给【旧邮箱】发码）就永远收不到 —— 而那条路
            //    正是没设过密码的会员唯一能换绑的方式
            case BIND -> memberRegisterDao.countByEmailHashExcludingMember(hash, currentMemberId) > 0
                    ? MailDelivery.SUPPRESS
                    : MailDelivery.DELIVER;
            // 🔴 二次验证码只能由 MemberStepUpService 发到会员【已绑定】的邮箱，不走本类。
            //    走到这里说明有人从公开发码接口（邮箱由客户端填）传了这个场景 ——
            //    真寄出去就成了「给任意邮箱发一封看起来像安全验证的信」。
            //    网关已经拒掉这个场景，这里是第二道：照常计限频，但不寄
            case STEP_UP -> MailDelivery.SUPPRESS;
        };
        if (delivery == MailDelivery.SUPPRESS) {
            // 只打日志，不改返回值。这行日志是排查「用户说没收到码」时的唯一线索 ——
            // 而那种工单里，多数情况恰恰是他记错了自己用哪个邮箱注册的
            log.info("【邮箱验证码】场景与账号状态不匹配，静默不寄, scene: {}, exists: {}, email: {}",
                    scene, exists, MemberEmailUtil.mask(email));
        }
        return delivery;
    }
}
