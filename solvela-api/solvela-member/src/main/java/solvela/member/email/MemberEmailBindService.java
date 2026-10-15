package solvela.member.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.MemberVerifyChannelEnum;
import solvela.member.Member;
import solvela.member.api.EmailBindFailReason;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeVerifyResult;
import solvela.member.api.MemberEmailBindCmd;
import solvela.member.api.MemberEmailBindResult;
import solvela.member.auth.MemberAuthDao;
import solvela.member.util.MemberEmailUtil;

/**
 * 绑定 / 更换邮箱。
 *
 * <h3>🔴 换绑要多验一道，因为有一条完整的权限提升链</h3>
 * <pre>
 *   会话被盗 → 换绑成攻击者自己的邮箱 → 用「忘记密码」重置 → 永久接管账号
 * </pre>
 * 每一步单看都合法。而 C 端令牌有 30 天有效期，token 泄露的机会比密码泄露多得多 ——
 * 只验新邮箱的话，「偷到一个 token」就等于「拿走这个账号」。
 *
 * <p>所以换绑必须额外证明「你是原主」，二选一：当前密码，或发到<b>旧邮箱</b>的验证码。
 * 后者是给没设过密码的会员留的（邮箱验证码注册出来的那批，password 是 NULL）——
 * 没有它，那批人就永远换不了邮箱。
 *
 * <p><b>首次绑定不要求这一步</b>：那时账号上还没有任何邮箱，多问一道堵不住上面那条链
 * （链条的起点就是「已有邮箱可换」），只是白提高门槛。
 *
 * @Date 2026-09-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberEmailBindService {

    private final MemberAuthDao memberAuthDao;

    private final MemberEmailCodeService emailCodeService;

    private final PiiHasher piiHasher;

    private final PiiCipher piiCipher;

    /**
     * 新绑的联系方式要不要进冷静期，看<b>绑定时那台设备可信不可信</b>。
     *
     * <p>🔴 用 {@code ObjectProvider} 延迟取：{@code MemberStepUpService} 发二次验证码要用
     * {@code MemberEmailCodeService}，而本类也要它 —— 直接注入会在某些装配顺序下成环。
     */
    private final org.springframework.beans.factory.ObjectProvider<solvela.member.stepup.MemberStepUpService>
            stepUpServiceProvider;

    private final solvela.member.contact.ContactCoolingStore coolingStore;

    private final solvela.notification.service.NotificationService notificationService;

    /**
     * 绑定或更换邮箱。
     *
     * <p>顺序：新邮箱格式 → 新邮箱验证码 → （换绑时）证明你是原主 → 查重 → 落库。
     *
     * <p>🔴 <b>新邮箱验证码排在「证明原主」之前</b>是有意的：它证明的是
     * 「这个邮箱归你」，而那件事与账号无关，先验掉可以让后面的分支都不必再考虑
     * 「这个邮箱是不是他瞎填的」。反过来先验原主，一个填错邮箱的用户会先被问密码，
     * 而那与他的错误毫无关系。
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberEmailBindResult bind(MemberEmailBindCmd cmd) {
        String newEmail = MemberEmailUtil.normalize(cmd.newEmail());
        if (newEmail == null) {
            return MemberEmailBindResult.fail(EmailBindFailReason.BAD_EMAIL_FORMAT);
        }

        EmailCodeVerifyResult codeResult =
                emailCodeService.verify(EmailCodeScene.BIND, newEmail, cmd.newEmailCode());
        if (codeResult != EmailCodeVerifyResult.OK) {
            return MemberEmailBindResult.fail(toBindReason(codeResult));
        }

        Member member = memberAuthDao.selectForEmailBind(cmd.memberId());
        if (member == null) {
            // 令牌解析出的会员号在库里不存在。走不到，除非会员被硬删了 ——
            // 而注销是改 status 不是删行。当成没有原主证明处理，不泄露更多
            log.warn("【邮箱绑定】令牌里的会员号查不到人, memberId: {}", cmd.memberId());
            return MemberEmailBindResult.fail(EmailBindFailReason.REBIND_VERIFICATION_FAILED);
        }

        String oldEmail = SolvelaStringUtil.isEmpty(member.getEmail())
                ? null
                : piiCipher.decrypt(member.getEmail());
        if (oldEmail != null) {
            MemberEmailBindResult rebindProblem = requireOwnership(member, oldEmail, cmd);
            if (rebindProblem != null) {
                return rebindProblem;
            }
        }

        return doUpdate(cmd.memberId(), newEmail, oldEmail, cmd.deviceId(), cmd.clientIp());
    }

    /**
     * 换绑时证明「你是原主」。通过返回 null。
     *
     * <p>两条路<b>各自独立</b>，给一条就行：
     * <ul>
     *   <li>当前密码 —— 会员设过密码时可用；</li>
     *   <li>旧邮箱验证码 —— 没设过密码的会员只有这一条。</li>
     * </ul>
     *
     * <p>⚠️ 两条都没给时返回 {@link EmailBindFailReason#REBIND_VERIFICATION_REQUIRED}
     * 而不是 FAILED：前者是「还需要一步」，客户端据此弹出输入框；
     * 后者是「你给的不对」。合并成一个的话，客户端没法区分该弹框还是该报错。
     */
    private MemberEmailBindResult requireOwnership(Member member, String oldEmail, MemberEmailBindCmd cmd) {
        boolean hasPassword = !SolvelaStringUtil.isEmpty(member.getPassword());
        boolean triedPassword = hasPassword && !SolvelaStringUtil.isEmpty(cmd.currentPassword());
        boolean triedOldCode = !SolvelaStringUtil.isEmpty(cmd.oldEmailCode());

        if (!triedPassword && !triedOldCode) {
            return MemberEmailBindResult.fail(EmailBindFailReason.REBIND_VERIFICATION_REQUIRED);
        }
        if (triedPassword && PasswordCipher.matches(cmd.currentPassword(), member.getPassword())) {
            return null;
        }
        if (triedOldCode && emailCodeService.verify(EmailCodeScene.BIND, oldEmail, cmd.oldEmailCode())
                == EmailCodeVerifyResult.OK) {
            return null;
        }
        // 🔴 不区分「密码错」和「旧邮箱验证码错」：区分等于告诉调用方
        //    「这个账号有没有设过密码」，而那是账号内部状态
        log.info("【邮箱绑定】换绑时原主证明未通过, memberId: {}", member.getMemberId());
        return MemberEmailBindResult.fail(EmailBindFailReason.REBIND_VERIFICATION_FAILED);
    }

    /**
     * 落库。
     *
     * <p>查重<b>只是提前给一句人话</b>，不是并发防线 —— 查完到更新之间有窗口。
     * 真正的防线是 {@code uk_mbr_email_hash}，撞了就是「已被别人绑走」，
     * 对用户是同一件事，别让它变成 500。判据同 {@code MemberRegisterService}。
     */
    private MemberEmailBindResult doUpdate(Long memberId, String newEmail, String oldEmail,
                                           String deviceId, String clientIp) {
        String hashHex = piiHasher.hash(newEmail);
        try {
            // 密文与摘要必须来自【同一个】规范化后的字符串，
            // 否则「解密出来的邮箱」和「能登录的邮箱」会是两个东西
            memberAuthDao.updateEmail(memberId, piiCipher.encrypt(newEmail), hashHex);
        } catch (DuplicateKeyException e) {
            log.info("【邮箱绑定】该邮箱已被其它账号绑定, memberId: {}", memberId);
            return MemberEmailBindResult.fail(EmailBindFailReason.EMAIL_TAKEN);
        }

        log.info("【邮箱绑定】成功, memberId: {}, 换绑: {}, 新邮箱: {}",
                memberId, oldEmail != null, MemberEmailUtil.mask(newEmail));
        applyCooling(memberId, deviceId, clientIp);
        notifyBound(memberId, newEmail);
        return MemberEmailBindResult.ok();
    }

    /**
     * 不受信任的设备上绑的 → 进 7 天冷静期；受信任的设备上绑的 → 解除（主人回自己老手机上重绑了）。
     *
     * <p>🔴 判据是<b>绑定那一刻那台设备</b>，不是「这个账号可不可信」。
     * 攻击者手里的设备恰恰是不受信任的那台，而主人在自己的老手机上补绑邮箱最正常 ——
     * 这个判据正好把两者分开（见 {@code ContactCoolingStore}）。
     *
     * <p>整段包在 try 里：冷静期记不上只是「这次没有冷静期」，不该让一次成功的绑定回滚。
     */
    private void applyCooling(Long memberId, String deviceId, String clientIp) {
        try {
            boolean trusted = stepUpServiceProvider.getObject()
                    .check(solvela.member.api.StepUpCmd.of(memberId, deviceId, clientIp)).trusted();
            if (trusted) {
                coolingStore.clear(memberId, MemberVerifyChannelEnum.EMAIL);
            } else {
                coolingStore.start(memberId, MemberVerifyChannelEnum.EMAIL);
            }
        } catch (RuntimeException e) {
            log.error("【邮箱绑定】判断冷静期失败，本次不设, memberId: {}", memberId, e);
        }
    }

    /**
     * 告诉主人一声。
     *
     * <h3>🔴 这条通知比冷静期更早起作用</h3>
     * 冷静期挡住的是「用新邮箱核身」，而主人要先<b>知道</b>有人动了他的账号才会去改密码。
     * 站内信登进来才看得到，但盗号者改不了它的存在 ——
     * 他能标已读，而主人下一次进来仍然会在消息列表里看到它。
     *
     * <p>⚠️ 还没做「通知旧邮箱」。换绑之后旧邮箱就失去了这个账号的登录能力，
     * 而它的主人不会收到任何消息 —— 要补的话是再加一个邮件模板，不影响这里。
     */
    private void notifyBound(Long memberId, String newEmail) {
        notificationService.send(solvela.notification.domain.NotifyRequest
                .of(solvela.enums.NotificationTemplateEnum.CONTACT_BOUND, memberId)
                .param("contactType", "邮箱")
                // 🔴 打过码：这条通知会长期留在消息列表里，而列表不该成为一个读明文邮箱的地方
                .param("maskedContact", MemberEmailUtil.mask(newEmail))
                .build());
    }

    /**
     * 验码结果 → 绑定失败原因。
     *
     * <p>与 {@code MemberAuthService.toAuthFailReason} 是两张表而不是一张：
     * 两条链路的失败取值集合不同（绑定没有 BAD_CREDENTIALS），
     * 硬合成一个枚举会让每一处都要面对一堆自己用不到的取值。
     */
    private static EmailBindFailReason toBindReason(EmailCodeVerifyResult result) {
        return switch (result) {
            case NOT_FOUND -> EmailBindFailReason.EMAIL_CODE_EXPIRED;
            case MISMATCH -> EmailBindFailReason.EMAIL_CODE_MISMATCH;
            case TOO_MANY_ATTEMPTS -> EmailBindFailReason.EMAIL_CODE_LOCKED;
            case OK -> throw new IllegalStateException("不可能走到：OK 已在调用方判掉");
        };
    }
}
