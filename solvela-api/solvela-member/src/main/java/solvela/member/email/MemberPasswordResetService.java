package solvela.member.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.auth.member.MemberTokenStore;
import solvela.member.stepup.DeviceTrustStore;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberOperationUnlockTypeEnum;
import solvela.enums.MemberStatusEnum;
import solvela.enums.MemberVerifyChannelEnum;
import solvela.member.Member;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeVerifyResult;
import solvela.member.api.MemberPasswordResetCmd;
import solvela.member.api.MemberPasswordResetResult;
import solvela.member.api.MemberPasswordPolicy;
import solvela.member.api.PasswordResetFailReason;
import solvela.member.auth.MemberAuthDao;
import solvela.member.device.DeviceGuard;
import solvela.member.api.PasswordResetType;
import solvela.member.api.SmsScene;
import solvela.member.sms.MemberSmsCodeService;
import solvela.member.util.MemberEmailUtil;
import solvela.member.util.MemberPhoneUtil;

/**
 * 用邮箱验证码重置密码。
 *
 * <h3>🔴 成功之后必须吊销全部会话</h3>
 * 用户点「忘记密码」的<b>最常见原因之一</b>就是「我怀疑号被人动过」。
 * 只改密码不吊销会话，攻击者手里那个 30 天有效期的令牌<b>照样能用</b> ——
 * 而用户以为自己已经把人赶出去了。这比不改密码更糟：他不会再采取任何行动。
 *
 * <p>吊销放在<b>事务内</b>：失败就整个回滚，让用户重来。反过来（先提交再吊销）
 * 一旦吊销失败，就回到了「密码改了但旧会话还在」——正是这里要消灭的状态。
 * 判据与 {@code MemberService.updateStatus} 的冻结吊销完全一致。
 *
 * <h3>冻结的账号不许自助找回</h3>
 * 允许的话，风控封掉一个刷子账号之后，他改个密码就能继续用 ——
 * 而冻结的本意正是让他用不了。
 *
 * @Date 2026-09-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberPasswordResetService {

    private final MemberAuthDao memberAuthDao;

    private final MemberEmailCodeService emailCodeService;

    private final MemberSmsCodeService smsCodeService;

    private final MemberTokenStore tokenStore;

    private final DeviceTrustStore deviceTrustStore;

    private final DeviceGuard deviceGuard;

    private final PiiHasher piiHasher;

    /** 重置密码要清掉密码阶梯冷却 —— 它是被恶意冷却时的唯一自助出口（方案 §4.4） */
    private final solvela.member.risk.feature.PasswordCooldownStore cooldownStore;

    /** 也要解开「这不是我」造成的资产出口冻结（方案 §8.1） */
    private final solvela.member.operationlimit.service.MemberOperationLimitService operationLimitService;

    /** 新绑的联系方式 7 天内不能用来找回密码 —— 三处拦截里后果最重的一处 */
    private final solvela.member.contact.ContactCoolingStore coolingStore;

    /**
     * 重置密码。
     *
     * <p>顺序：邮箱格式 → 验证码 → 新密码强度 → 找人 → 账号状态 → 改密 → 吊销会话。
     *
     * <p>🔴 <b>验证码排在找人之前</b>，而且查无此人时也要走完验证码那一步 ——
     * 否则「有账号」和「没账号」输错码得到的回答就不一样了，发码那一步
     * 靠 {@code MailDelivery.SUPPRESS} 藏住的东西会在这里漏光。
     * 与 {@code MemberAuthService.authenticate} 的邮箱验证码登录同一个处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberPasswordResetResult reset(MemberPasswordResetCmd cmd) {
        PasswordResetType type = cmd.typeOrDefault();

        /*
         * 规范化 → 验码 → 强度 → 查人。四步的顺序对两条通道完全一样，
         * 只有「用哪个工具规范化」和「码走哪条通道」不同。
         * 用 switch 表达式：新增找回方式时【编译不过】，
         * 而不是悄悄落进某个兜底分支去按邮箱规范化一个手机号。
         */
        String identity = switch (type) {
            case EMAIL_CODE -> MemberEmailUtil.normalize(cmd.identity());
            case SMS_CODE -> MemberPhoneUtil.normalize(cmd.identity());
        };
        if (identity == null) {
            return MemberPasswordResetResult.fail(switch (type) {
                case EMAIL_CODE -> PasswordResetFailReason.BAD_EMAIL_FORMAT;
                case SMS_CODE -> PasswordResetFailReason.BAD_PHONE_FORMAT;
            });
        }

        PasswordResetFailReason codeProblem = verifyCode(type, identity, cmd.code());
        if (codeProblem != null) {
            // 验证码错也算一次设备失败：「一台机器在挨个试不同账号的重置码」
            // 是很强的信号，而它只有在这里记得下来
            deviceGuard.recordLoginFailure(cmd.deviceId());
            return MemberPasswordResetResult.fail(codeProblem);
        }

        // 强度校验排在验证码【之后】：它不查存储也不泄露信息，本可以更靠前，
        // 但放在这里能让「码对了才告诉你密码太弱」—— 而反过来的话，
        // 一个不知道码的人可以拿这个接口反复试探密码规则
        if (!MemberPasswordPolicy.isValid(cmd.newPassword())) {
            return MemberPasswordResetResult.fail(PasswordResetFailReason.WEAK_PASSWORD);
        }

        String hashHex = piiHasher.hash(identity);
        Member member = switch (type) {
            case EMAIL_CODE -> memberAuthDao.selectForLoginByEmail(hashHex);
            case SMS_CODE -> memberAuthDao.selectForLogin(hashHex);
        };
        if (member == null) {
            // 走到这里说明他猜中了一个六位数（百万分之一，只有 5 次机会）——
            // 不构成可用的枚举手段，见 PasswordResetFailReason.ACCOUNT_NOT_FOUND
            return MemberPasswordResetResult.fail(PasswordResetFailReason.ACCOUNT_NOT_FOUND);
        }
        if (member.getStatus() != MemberStatusEnum.NORMAL) {
            log.info("【重置密码】账号状态不允许自助找回, memberId: {}, status: {}",
                    member.getMemberId(), member.getStatus());
            return MemberPasswordResetResult.fail(PasswordResetFailReason.ACCOUNT_UNAVAILABLE);
        }

        /*
         * 🔴 冷静期：这一处漏了的后果是【永久接管】。
         *
         * 用新绑的邮箱重置密码之后，原主连验证码登录都进不来了 ——
         * 二次验证那一档漏了只是丢一次兑换，这一档漏了是丢整个账号。
         *
         * 排在验码【之后】：要先知道是哪个会员才查得到他的冷静期，
         * 而在那之前这个接口对「有号 / 没号」的回答必须保持一致。
         */
        MemberVerifyChannelEnum channel = switch (type) {
            case EMAIL_CODE -> MemberVerifyChannelEnum.EMAIL;
            case SMS_CODE -> MemberVerifyChannelEnum.SMS;
        };
        if (coolingStore.cooling(member.getMemberId(), channel)) {
            log.warn("【重置密码】联系方式还在冷静期内，拒绝, memberId: {}, 通道: {}",
                    member.getMemberId(), channel);
            return MemberPasswordResetResult.fail(PasswordResetFailReason.CONTACT_COOLING);
        }

        memberAuthDao.updatePassword(member.getMemberId(), PasswordCipher.encode(cmd.newPassword()));

        // 🔴 吊销全部会话，事务内。理由见类注释 —— 不吊销的话，攻击者手里那个
        //    30 天有效期的令牌照样能用，而用户以为自己已经把人赶出去了
        int revoked = tokenStore.revokeAll(member.getMemberId());
        // 设备信任一并撤销：会话踢掉了，但攻击者那台设备若已「验证过」或「登录满 7 天」，
        // 他改完密码重新登进来就直接是受信任设备，二次验证对他形同虚设
        deviceTrustStore.revoke(member.getMemberId(), null);

        /*
         * ---------- 重置密码是两个限制的【自助出口】（方案 §4.4、§8.1） ----------
         *
         * 🔴 这两行缺了的话，用户改完密码还是进不去 / 还是兑换不了 ——
         *    而他已经用完了所有能自己做的事，剩下的只有打客服电话。
         *    它们也正是密码阶梯敢用 24 小时那一档、资产出口敢「冻到改密为止」的前提。
         */
        // 密码阶梯冷却：两个桶都清（可信设备桶与共用桶）
        cooldownStore.onIdentityVerified(hashHex, cmd.deviceId());
        // 资产出口冻结：「这不是我」之后唯一的恢复方式就是这一步
        operationLimitService.unlock(member.getMemberId(), MemberOperationTypeEnum.ASSET_OUT,
                MemberOperationUnlockTypeEnum.RESET_PASSWORD, null, "重置密码");

        log.info("【重置密码】成功, memberId: {}, 通道: {}, 吊销会话 {} 个, 身份: {}",
                member.getMemberId(), type, revoked, mask(type, identity));
        return MemberPasswordResetResult.ok(revoked);
    }

    /** 验码。通过返回 null，失败返回对应原因。 */
    private PasswordResetFailReason verifyCode(PasswordResetType type, String identity, String code) {
        return switch (type) {
            case EMAIL_CODE -> switch (emailCodeService.verify(
                    EmailCodeScene.RESET_PASSWORD, identity, code)) {
                case OK -> null;
                case NOT_FOUND -> PasswordResetFailReason.EMAIL_CODE_EXPIRED;
                case MISMATCH -> PasswordResetFailReason.EMAIL_CODE_MISMATCH;
                case TOO_MANY_ATTEMPTS -> PasswordResetFailReason.EMAIL_CODE_LOCKED;
            };
            case SMS_CODE -> switch (smsCodeService.verify(
                    SmsScene.RESET_PASSWORD, identity, code)) {
                case OK -> null;
                case NOT_FOUND -> PasswordResetFailReason.SMS_CODE_EXPIRED;
                case MISMATCH -> PasswordResetFailReason.SMS_CODE_MISMATCH;
                case TOO_MANY_ATTEMPTS -> PasswordResetFailReason.SMS_CODE_LOCKED;
            };
        };
    }

    /** 日志里只放打过码的身份 —— 这条日志会长期留存，明文一次都不该出现在里面。 */
    private static String mask(PasswordResetType type, String identity) {
        return switch (type) {
            case EMAIL_CODE -> MemberEmailUtil.mask(identity);
            case SMS_CODE -> MemberPhoneUtil.mask(identity);
        };
    }
}
