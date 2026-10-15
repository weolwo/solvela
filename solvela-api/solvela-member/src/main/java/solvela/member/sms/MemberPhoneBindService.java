package solvela.member.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.member.Member;
import solvela.member.api.MemberPhoneBindCmd;
import solvela.member.api.MemberPhoneBindResult;
import solvela.member.api.PhoneBindFailReason;
import solvela.member.api.SmsCodeVerifyResult;
import solvela.member.api.SmsScene;
import solvela.member.auth.MemberAuthDao;
import solvela.member.util.MemberPhoneUtil;

/**
 * 绑定 / 更换手机号。
 *
 * <h3>它不是「改个资料」，是换掉一条登录身份</h3>
 * 手机号在这个系统里比邮箱重一档：{@code uk_mbr_phone_hash} 是唯一约束，
 * 而手机号是<b>注册的默认身份</b>。换绑意味着「原来那个号从此登不了、也注册不了
 * 这个账号」—— 那不是资料变更，是身份转移。
 *
 * <h3>🔴 换绑必须多验一道「你是原主」，首次绑定不用</h3>
 * 拦的是这条链：
 * <pre>会话被盗 → 换绑成攻击者的手机号 → 用短信验证码登录 → 永久接管</pre>
 * 每一步单看都合法。而 C 端令牌有 30 天有效期，token 泄露的机会比密码泄露多得多。
 * 首次绑定不需要 —— 那时这条链的起点还不存在。
 *
 * <h3>与 {@code MemberEmailBindService} 逐段对称，这是刻意的</h3>
 * 两条通道的规则本来就该一样。差异只应该来自它们真正不同的地方
 *（格式校验用哪个工具、码发到哪），而不是「当时谁写的」。
 * 唯一实质不同的一点：<b>短信要花钱</b>，所以发码那一侧的 IP 日限紧得多
 *（见 {@code VerificationCodeProperties}）。
 *
 * @Date 2026-09-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberPhoneBindService {

    private final MemberAuthDao memberAuthDao;

    private final MemberSmsCodeService smsCodeService;

    private final PiiHasher piiHasher;

    private final PiiCipher piiCipher;

    /**
     * 判断「绑定时那台设备可信吗」。
     *
     * <p>🔴 延迟取：{@code MemberStepUpService} 的依赖里有验证码服务，与本类成环的风险同邮箱那边。
     */
    private final org.springframework.beans.factory.ObjectProvider<solvela.member.stepup.MemberStepUpService>
            stepUpServiceProvider;

    private final solvela.member.contact.ContactCoolingStore coolingStore;

    private final solvela.notification.service.NotificationService notificationService;

    @Transactional(rollbackFor = Exception.class)
    public MemberPhoneBindResult bind(MemberPhoneBindCmd cmd) {
        String newPhone = MemberPhoneUtil.normalize(cmd.newPhone());
        if (newPhone == null) {
            return MemberPhoneBindResult.fail(PhoneBindFailReason.BAD_PHONE_FORMAT);
        }

        /*
         * 先验新号码的码，再查会员。顺序与邮箱那边一致：
         * 没有码的人不该能拿这个接口去问「这个号被谁绑了」——
         * 而 PHONE_TAKEN 是必须如实回答的（藏了用户就不知道该换号还是找回账号）。
         */
        SmsCodeVerifyResult codeResult =
                smsCodeService.verify(SmsScene.BIND, newPhone, cmd.newPhoneCode());
        if (codeResult != SmsCodeVerifyResult.OK) {
            return MemberPhoneBindResult.fail(toBindReason(codeResult));
        }

        Member member = memberAuthDao.selectForPhoneBind(cmd.memberId());
        if (member == null) {
            // 令牌解析出的会员号在库里不存在。走不到，除非会员被硬删了 ——
            // 而注销是改 status 不是删行。当成没有原主证明处理，不泄露更多
            log.warn("【手机绑定】令牌里的会员号查不到人, memberId: {}", cmd.memberId());
            return MemberPhoneBindResult.fail(PhoneBindFailReason.REBIND_VERIFICATION_FAILED);
        }

        String oldPhone = SolvelaStringUtil.isEmpty(member.getPhone())
                ? null
                : piiCipher.decrypt(member.getPhone());
        if (oldPhone != null) {
            MemberPhoneBindResult rebindProblem = requireOwnership(member, oldPhone, cmd);
            if (rebindProblem != null) {
                return rebindProblem;
            }
        }
        return doUpdate(cmd.memberId(), newPhone, oldPhone, cmd.deviceId(), cmd.clientIp());
    }

    /**
     * 换绑时的原主证明：当前密码，或旧手机号收到的验证码。
     *
     * <p>没设过密码的会员（邮箱注册那批）只有后一条路 —— 所以两条都要留着。
     */
    private MemberPhoneBindResult requireOwnership(Member member, String oldPhone,
                                                   MemberPhoneBindCmd cmd) {
        boolean hasPassword = !SolvelaStringUtil.isEmpty(member.getPassword());
        boolean triedPassword = hasPassword && !SolvelaStringUtil.isEmpty(cmd.currentPassword());
        boolean triedOldCode = !SolvelaStringUtil.isEmpty(cmd.oldPhoneCode());

        if (!triedPassword && !triedOldCode) {
            return MemberPhoneBindResult.fail(PhoneBindFailReason.REBIND_VERIFICATION_REQUIRED);
        }
        if (triedPassword && PasswordCipher.matches(cmd.currentPassword(), member.getPassword())) {
            return null;
        }
        if (triedOldCode
                && smsCodeService.verify(SmsScene.BIND, oldPhone, cmd.oldPhoneCode())
                == SmsCodeVerifyResult.OK) {
            return null;
        }
        /*
         * 🔴 不区分「密码错」和「旧手机验证码错」：区分等于告诉调用方
         *    「这个账号有没有设过密码」，而那是账号内部状态。
         *    判据与邮箱那条一字不差。
         */
        log.info("【手机绑定】换绑时原主证明未通过, memberId: {}", member.getMemberId());
        return MemberPhoneBindResult.fail(PhoneBindFailReason.REBIND_VERIFICATION_FAILED);
    }

    private MemberPhoneBindResult doUpdate(Long memberId, String newPhone, String oldPhone,
                                           String deviceId, String clientIp) {
        String hashHex = piiHasher.hash(newPhone);
        try {
            // 密文与摘要必须来自【同一个】规范化后的字符串，
            // 否则「解密出来的号」和「能登录的号」会是两个东西
            memberAuthDao.updatePhone(memberId, piiCipher.encrypt(newPhone), hashHex);
        } catch (DuplicateKeyException e) {
            log.info("【手机绑定】该手机号已被其它账号绑定, memberId: {}", memberId);
            return MemberPhoneBindResult.fail(PhoneBindFailReason.PHONE_TAKEN);
        }
        log.info("【手机绑定】成功, memberId: {}, 换绑: {}, 新号码: {}",
                memberId, oldPhone != null, MemberPhoneUtil.mask(newPhone));
        applyCooling(memberId, deviceId, clientIp);
        notifyBound(memberId, newPhone);
        /*
         * ⚠️ 还没做「通知旧手机号」。换绑之后旧号码就失去了这个账号的登录能力，
         *    而它的主人收不到短信（站内信他看得到，但那要登进来）。
         *    邮箱那边留着同一条待办。要补的话是一次发送，不影响这里的主流程。
         */
        return MemberPhoneBindResult.ok();
    }

    /**
     * 不受信任的设备上绑的 → 进 7 天冷静期；受信任的设备上绑的 → 解除。
     * 判据与理由与邮箱那边一字不差，见 {@code ContactCoolingStore}。
     */
    private void applyCooling(Long memberId, String deviceId, String clientIp) {
        try {
            boolean trusted = stepUpServiceProvider.getObject()
                    .check(solvela.member.api.StepUpCmd.of(memberId, deviceId, clientIp)).trusted();
            if (trusted) {
                coolingStore.clear(memberId, solvela.enums.MemberVerifyChannelEnum.SMS);
            } else {
                coolingStore.start(memberId, solvela.enums.MemberVerifyChannelEnum.SMS);
            }
        } catch (RuntimeException e) {
            log.error("【手机绑定】判断冷静期失败，本次不设, memberId: {}", memberId, e);
        }
    }

    /** 告诉主人一声。号码打过码 —— 这条通知会长期留在消息列表里 */
    private void notifyBound(Long memberId, String newPhone) {
        notificationService.send(solvela.notification.domain.NotifyRequest
                .of(solvela.enums.NotificationTemplateEnum.CONTACT_BOUND, memberId)
                .param("contactType", "手机号")
                .param("maskedContact", MemberPhoneUtil.mask(newPhone))
                .build());
    }

    private static PhoneBindFailReason toBindReason(SmsCodeVerifyResult result) {
        return switch (result) {
            case NOT_FOUND -> PhoneBindFailReason.SMS_CODE_EXPIRED;
            case MISMATCH -> PhoneBindFailReason.SMS_CODE_MISMATCH;
            case TOO_MANY_ATTEMPTS -> PhoneBindFailReason.SMS_CODE_LOCKED;
            case OK -> throw new IllegalStateException("不可能走到：OK 已在调用方判掉");
        };
    }
}
