package solvela.member.stepup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PiiCipher;
import solvela.enums.LoginLogResultEnum;
import solvela.enums.MemberVerifyChannelEnum;
import solvela.member.Member;
import solvela.member.api.DeviceTrust;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendResult;
import solvela.member.api.MemberStepUpApi;
import solvela.member.api.AssetOutRisk;
import solvela.member.api.AssetOutVerdict;
import solvela.member.api.SessionReportCmd;
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

    /** 资产出口的冻结记在这里（{@code t_member_operation_limit} 的 ASSET_OUT 类型） */
    private final solvela.member.operationlimit.service.MemberOperationLimitService operationLimitService;

    /** 「这不是我」要给那一次登录的决策流水打标（方案 §8.1） */
    private final solvela.member.risk.RiskLabelService riskLabelService;

    /** 新绑的邮箱 7 天内不能用来核身（方案 §6.4），见 {@code ContactCoolingStore} */
    private final solvela.member.contact.ContactCoolingStore coolingStore;

    /**
     * 资产出口那一道闸上的风控（2026-10-16，P3 ②）。
     *
     * <p>🔴 它回答的是 {@link #check} 回答不了的那个问题：
     * check 说「这台设备可信吗」，风控说「这一次操作像不像出事了」。
     * 一台「验证过」的老设备被偷了会话之后照样可信 —— 所以风控能要求可信设备也验一道。
     *
     * <h3>🔴 必须延迟取，不能直接注入</h3>
     * 直接注入是一个<b>真的循环依赖</b>，而且这条环说出了一件真事：
     * <pre>
     * 本服务 → 风控引擎 → 规则表（构造时把所有规则实例化出来做双向校验）
     *        → LoginMatrixRule → DeviceTrustProvider → 本服务
     * </pre>
     * 也就是说：<b>登录风控要用本服务算设备信任，而本服务要用风控判资产出口</b> ——
     * 两个方向都是对的，只是不能在构造器里同时成立。
     * 判据与 {@code LoginCounters} 里那个 {@code ObjectProvider} 一字不差。
     *
     * <p>用 {@code ObjectFactory} 而不是 {@code ObjectProvider}：前者是函数式接口，
     * 单测里一个 lambda 就能给（后者不是，试过，编译不过）。
     */
    private final org.springframework.beans.factory.ObjectFactory<solvela.member.risk.MemberRiskEngine> riskEngine;

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
        /*
         * 🔴 在不受信任的设备上新绑的邮箱，7 天内不能用来核身。
         *    不挡的话这条链是通的：登录 → 加地址要二次验证 → 没有邮箱 → 绑自己的邮箱
         *    → 用它通过二次验证 → 把东西搬走。而「去绑定」那颗按钮正是我们要给的。
         */
        long availableAt = coolingStore.availableAt(cmd.memberId(), MemberVerifyChannelEnum.EMAIL);
        if (availableAt > 0L) {
            return StepUpResult.retryLater(StepUpFailReason.CONTACT_COOLING,
                    availableAt - System.currentTimeMillis() / 1000L);
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
        /*
         * 验码这一步也要挡。🔴 只挡发码是不够的：码有 5 分钟有效期，
         * 一个在冷静期开始【之前】发出去的码，冷静期内照样能拿来验过。
         */
        long coolingUntil = coolingStore.availableAt(cmd.memberId(), MemberVerifyChannelEnum.EMAIL);
        if (coolingUntil > 0L) {
            return StepUpResult.retryLater(StepUpFailReason.CONTACT_COOLING,
                    coolingUntil - System.currentTimeMillis() / 1000L);
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
     * 资产出口这一道闸的完整回答。<b>顺序固定：先冻结，再信任</b>，理由见 {@link AssetOutVerdict}。
     *
     * <p>两个判断合成一次调用，是因为守这道闸的拦截器两件事都要知道 ——
     * 分成两个接口的话，每一次「加地址 / 充话费」都要跨进程两趟。
     */
    @Override
    public AssetOutVerdict assetOut(StepUpCmd cmd) {
        boolean frozen = operationLimitService.isFrozen(cmd.memberId(),
                solvela.enums.MemberOperationTypeEnum.ASSET_OUT);
        /*
         * 🔴 冻着就不必再算信任、也不必跑风控了，而且<b>刻意都不做</b>：
         * 两者都要查库，而它们的答案此刻没有任何用途 —— 冻结压倒一切。
         * 代价是被冻结账号上的那些尝试没有风控流水；它们记在
         * t_member_operation_limit 那一行上（谁冻的、什么时候、为什么）。
         */
        if (frozen) {
            return AssetOutVerdict.ofFrozen();
        }
        DeviceTrust trust = check(cmd);
        return assetOutRisk(cmd, trust);
    }

    /**
     * 跑一遍资产出口的风控，并把结论折成网关能执行的三种之一。
     *
     * <h3>🔴 风控出错 = 放行，但留痕</h3>
     * 与引擎其余部分一致（方案 §3.9）：风控挂了不能等于「所有人都取不出东西」。
     * 这一层额外兜一道 try，是因为这里<b>多了一次落库</b>（流水），
     * 而资产出口这条路上任何一次异常都会变成用户点不动那颗按钮。
     */
    private AssetOutVerdict assetOutRisk(StepUpCmd cmd, DeviceTrust trust) {
        solvela.member.risk.MemberRiskEngine engine = riskEngine.getObject();
        try (solvela.member.risk.RiskSession risk = engine.open(assetContext(cmd, trust))) {
            solvela.member.risk.RiskDecision decision = engine.evaluatePre(risk);
            if (decision.atLeast(solvela.enums.MemberRiskDecisionEnum.LIMIT)) {
                log.warn("【资产出口】风控硬拦, memberId: {}, deviceId: {}, 结论: {}",
                        cmd.memberId(), cmd.deviceId(), decision.decision());
                return risk.end(solvela.enums.MemberRiskOutcomeEnum.PRE_BLOCKED,
                        AssetOutVerdict.of(trust, AssetOutRisk.BLOCKED, decision.retryAfterSeconds()));
            }
            if (decision.needsOtp()) {
                /*
                 * 🔴 这一档会让【可信设备】也去验一道码 —— 整个改造的落点就在这里。
                 *
                 * 对客户端是 STEP_UP_REQUIRED，与「新设备要验码」同一个码、同一个弹窗，
                 * 所以前端一行都不用改；误判的代价也停在「多收一封信」。
                 */
                log.info("【资产出口】风控要求核身, memberId: {}, deviceId: {}, 设备信任: {}",
                        cmd.memberId(), cmd.deviceId(), trust);
                return risk.end(solvela.enums.MemberRiskOutcomeEnum.STEP_UP_REQUIRED,
                        AssetOutVerdict.of(trust, AssetOutRisk.STEP_UP, 0L));
            }
            return risk.end(solvela.enums.MemberRiskOutcomeEnum.SUCCESS, AssetOutVerdict.pass(trust));
        } catch (RuntimeException e) {
            log.error("【资产出口】风控执行失败，按放行处理, memberId: {}", cmd.memberId(), e);
            return AssetOutVerdict.pass(trust);
        }
    }

    /**
     * 资产出口的规则上下文。
     *
     * <h3>🔴 只有会员号，没有加载整行 Member</h3>
     * 这个场景的规则一条都不读账号字段（数的是「这台设备替几个人搬过东西」），
     * 而为了填一个 {@code Member} 去查一次库，是在<b>每一次加地址 / 充话费</b>上加一次查询。
     * 见 {@code RiskContext#memberIdOnly}。
     *
     * <h3>设备信任要放进上下文</h3>
     * 不放的话，引擎那条「可信设备跳过」的通路对本场景永远不生效 ——
     * 而本场景恰恰有一条规则刻意配成「可信也不跳过」（{@code ASSET_DEVICE_FANOUT}）。
     * 两件事要都能表达，前提是上下文里有这个值。
     */
    private solvela.member.risk.RiskContext assetContext(StepUpCmd cmd, DeviceTrust trust) {
        solvela.member.risk.RiskContext ctx = new solvela.member.risk.RiskContext(
                solvela.enums.MemberRiskEventTypeEnum.ASSET_OUT,
                cmd.memberId(), cmd.deviceId(), cmd.clientIp());
        ctx.deviceTrust(toRiskTrust(trust));
        return ctx;
    }

    /** 二次验证那套信任档 → 风控那套。两边刻意是同一个定义（方案 §3.2），这里只是换个载体 */
    private static solvela.member.risk.RiskDeviceTrust toRiskTrust(DeviceTrust trust) {
        return switch (trust) {
            case NO_DEVICE -> solvela.member.risk.RiskDeviceTrust.NO_DEVICE;
            case NEW_DEVICE -> solvela.member.risk.RiskDeviceTrust.NEW;
            case HISTORY -> solvela.member.risk.RiskDeviceTrust.HISTORY;
            case VERIFIED -> solvela.member.risk.RiskDeviceTrust.VERIFIED;
        };
    }

    /**
     * 「这不是我」。三件事，顺序即重要性：冻结 → 撤信任 → 打标。
     *
     * <h3>🔴 打标放在最后，而且不让它影响前两件</h3>
     * 标注是给分析用的，而且它可能压根找不到对应的流水（老会话没有设备号、
     * 流水已过期被 DROP PARTITION）。为了一条分析数据让「这不是我」失败，
     * 等于在用户最需要我们办成事的那一刻办不成。
     *
     * <h3>为什么冻结在最前面</h3>
     * 它是唯一能<b>立刻止损</b>的动作。撤信任只是让对方下次登录要多验一道，
     * 而他此刻手里那个会话可能正在加收货地址。
     */
    @Override
    public boolean reportNotMe(SessionReportCmd cmd) {
        if (cmd.memberId() == null) {
            return false;
        }
        operationLimitService.freeze(cmd.memberId(),
                solvela.enums.MemberOperationTypeEnum.ASSET_OUT,
                "用户举报「这不是我」",
                // 没有「到期」这个概念：正确的出口只有重置密码。那一列是 NOT NULL，所以给一个很远的日期
                java.time.LocalDateTime.now().plusYears(100));

        boolean keepCurrent = revokeOthers(StepUpCmd.of(
                cmd.memberId(), cmd.currentDeviceId(), cmd.clientIp()));

        riskLabelService.labelLogin(cmd.memberId(), cmd.reportedDeviceId(), cmd.loginEpochSecond(),
                solvela.enums.MemberRiskLabelEnum.STOLEN,
                solvela.enums.MemberRiskLabelSourceEnum.USER_REPORT);

        log.warn("【账号安全】用户举报「这不是我」, memberId: {}, 被举报设备: {}, 当前设备保留信任: {}",
                cmd.memberId(), cmd.reportedDeviceId(), keepCurrent);
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
