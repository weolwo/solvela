package solvela.member.stepup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PiiCipher;
import solvela.enums.LoginLogResultEnum;
import solvela.member.Member;
import solvela.member.api.DeviceTrust;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendResult;
import solvela.member.api.MemberStepUpApi;
import solvela.member.api.StepUpCmd;
import solvela.member.api.StepUpFailReason;
import solvela.member.api.StepUpResult;
import solvela.member.auth.MemberAuthDao;
import solvela.member.email.MemberEmailCodeService;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.member.util.MemberEmailUtil;

/**
 * 敏感操作二次验证。契约与取舍见 {@link MemberStepUpApi}。
 *
 * <h3>信任的两个来源</h3>
 * <ol>
 *   <li><b>验证过</b>：在这台设备上通过过一次二次验证（{@link DeviceTrustStore}）；</li>
 *   <li><b>老交情</b>：这台设备上该会员的成功登录早于门槛（{@code t_member_login_log}）。
 *       让存量老用户在常用设备上零打扰。</li>
 * </ol>
 * 两者都受「信任起算点」约束：重置密码、冻结、下线其他设备之后，此前的一切都不再算数。
 *
 * <h3>🔴 已知边界：设备令牌能被复制</h3>
 * 信任挂在设备号上，而设备令牌今天存在客户端的 localStorage 里，能被 XSS 或恶意软件读走
 * （{@code DeviceTokenCodec} 类注释：这是设计上接受的代价）。拿到一台老设备的令牌的人，
 * 在这里就是「老设备」。改成服务端下发的 HttpOnly cookie 能堵住 XSS 这一条，见方案文档 §3。
 *
 * @Date 2026-09-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberStepUpService implements MemberStepUpApi {

    private final DeviceTrustStore trustStore;

    private final MemberLoginLogDao loginLogDao;

    private final MemberAuthDao memberAuthDao;

    private final MemberEmailCodeService emailCodeService;

    private final PiiCipher piiCipher;

    private final MemberStepUpProperties properties;

    @Override
    public DeviceTrust check(StepUpCmd cmd) {
        if (SolvelaStringUtil.isBlank(cmd.deviceId())) {
            return DeviceTrust.NO_DEVICE;
        }
        if (trustStore.isVerified(cmd.memberId(), cmd.deviceId())) {
            return DeviceTrust.VERIFIED;
        }
        boolean history = loginLogDao.existsTrustedLogin(
                cmd.memberId(), cmd.deviceId(),
                trustStore.trustSince(cmd.memberId()),
                properties.trustAfter().toSeconds(),
                LoginLogResultEnum.LOGIN_SUCCESS.getValue());
        return history ? DeviceTrust.HISTORY : DeviceTrust.NEW_DEVICE;
    }

    @Override
    public StepUpResult sendCode(StepUpCmd cmd) {
        if (SolvelaStringUtil.isBlank(cmd.deviceId())) {
            // 发了码也没处记住「验过了」，下一次还要验 —— 不如一开始就说清楚
            return StepUpResult.fail(StepUpFailReason.NO_DEVICE);
        }
        String email = boundEmail(cmd.memberId());
        if (email == null) {
            return StepUpResult.fail(StepUpFailReason.NO_EMAIL);
        }
        EmailCodeSendResult sent = emailCodeService.send(EmailCodeScene.STEP_UP, email, cmd.clientIp());
        if (sent.success()) {
            return StepUpResult.sent(MemberEmailUtil.mask(email));
        }
        return switch (sent.reason()) {
            case TOO_FREQUENT -> StepUpResult.retryLater(StepUpFailReason.TOO_FREQUENT, sent.retryAfterSeconds());
            case DAILY_LIMIT_REACHED ->
                    StepUpResult.retryLater(StepUpFailReason.DAILY_LIMIT_REACHED, sent.retryAfterSeconds());
            case SEND_FAILED -> StepUpResult.fail(StepUpFailReason.SEND_FAILED);
            // 邮箱是库里取出来的，格式不对只可能是数据坏了。当成发不出去，并留下线索
            case BAD_EMAIL_FORMAT -> {
                log.error("【二次验证】会员绑定的邮箱格式非法，发不出码, memberId: {}", cmd.memberId());
                yield StepUpResult.fail(StepUpFailReason.SEND_FAILED);
            }
        };
    }

    @Override
    public StepUpResult verify(StepUpCmd cmd) {
        if (SolvelaStringUtil.isBlank(cmd.deviceId())) {
            return StepUpResult.fail(StepUpFailReason.NO_DEVICE);
        }
        String email = boundEmail(cmd.memberId());
        if (email == null) {
            return StepUpResult.fail(StepUpFailReason.NO_EMAIL);
        }
        StepUpFailReason problem = switch (emailCodeService.verify(EmailCodeScene.STEP_UP, email, cmd.code())) {
            case OK -> null;
            case NOT_FOUND -> StepUpFailReason.CODE_EXPIRED;
            case MISMATCH -> StepUpFailReason.CODE_MISMATCH;
            case TOO_MANY_ATTEMPTS -> StepUpFailReason.CODE_LOCKED;
        };
        if (problem != null) {
            return StepUpResult.fail(problem);
        }
        trustStore.markVerified(cmd.memberId(), cmd.deviceId());
        log.info("【二次验证】通过，设备记为受信任, memberId: {}, deviceId: {}", cmd.memberId(), cmd.deviceId());
        return StepUpResult.verified();
    }

    @Override
    public boolean revokeOthers(StepUpCmd cmd) {
        // 🔴 先判当前设备【本来】受不受信任，再撤销。顺序反过来的话，撤销之后它必然不受信任，
        //    而「当前设备无条件保留」又会让一台新设备借这个按钮把自己洗白
        boolean keepCurrent = check(cmd).trusted();
        trustStore.revoke(cmd.memberId(), keepCurrent ? cmd.deviceId() : null);
        log.info("【二次验证】会员下线其他设备，撤销其余设备信任, memberId: {}, 当前设备保留信任: {}",
                cmd.memberId(), keepCurrent);
        return keepCurrent;
    }

    /**
     * 撤销该会员的<b>全部</b>设备信任。重置密码、冻结时调用 —— 那两个时刻都意味着
     * 「此前登录过的设备里可能有不该在的」。
     */
    public void revokeAll(Long memberId) {
        trustStore.revoke(memberId, null);
    }

    /** 会员绑定的邮箱明文；没绑返回 null。 */
    private String boundEmail(Long memberId) {
        Member member = memberAuthDao.selectForEmailBind(memberId);
        if (member == null || SolvelaStringUtil.isEmpty(member.getEmail())) {
            return null;
        }
        return piiCipher.decrypt(member.getEmail());
    }
}
