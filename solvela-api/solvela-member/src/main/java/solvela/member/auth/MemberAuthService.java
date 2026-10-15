package solvela.member.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.trace.Trace;
import solvela.base.util.SolvelaIpUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.LoginLogResultEnum;
import solvela.enums.MemberStatusEnum;
import solvela.member.Member;
import solvela.member.MemberLoginLog;
import solvela.member.api.AuthFailReason;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.LoginChallengeCmd;
import solvela.member.api.LoginChallengeCodeResult;
import solvela.member.api.MemberIdentity;
import solvela.member.api.MemberLogoutCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberContactView;
import solvela.member.api.MemberPhoneBindCmd;
import solvela.member.api.MemberPhoneBindResult;
import solvela.member.api.MemberRegisterResult;
import solvela.enums.DeviceStatusEnum;
import solvela.member.api.SmsCodeVerifyResult;
import solvela.member.api.SmsScene;
import solvela.member.api.SmsCodeSendCmd;
import solvela.member.api.SmsCodeSendResult;
import solvela.member.sms.MemberSmsCodeService;
import solvela.member.register.MemberRegisterService;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.member.device.DeviceGuard;
import solvela.member.device.DeviceGuardVerdict;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeVerifyResult;
import solvela.member.api.MemberLoginType;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.EmailCodeSendResult;
import solvela.member.api.MemberEmailBindCmd;
import solvela.member.api.MemberEmailBindResult;
import solvela.member.api.MemberPasswordResetCmd;
import solvela.member.api.MemberPasswordResetResult;
import solvela.member.email.MemberEmailBindService;
import solvela.member.email.MemberPasswordResetService;
import solvela.member.email.MemberEmailCodeIssuer;
import solvela.member.email.MemberEmailCodeService;
import solvela.member.util.MemberEmailUtil;
import solvela.member.util.MemberPhoneUtil;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskOutcomeEnum;
import solvela.enums.MemberVerifyChannelEnum;
import solvela.member.risk.MemberRiskEngine;
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskDecision;
import solvela.member.risk.RiskEventRef;
import solvela.member.risk.RiskSession;
import solvela.member.risk.VerifyRouter;
import solvela.member.risk.feature.AccountFailTracker;
import solvela.member.risk.feature.LoginProfileProvider;
import solvela.member.risk.feature.PasswordCooldownStore;


import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 会员认证：验明身份，仅此而已。{@link MemberAuthApi} 的实现。
 *
 * <h3>职责边界：这里没有令牌</h3>
 * 本服务回答的是「这个人是不是他声称的那个会员，现在能不能用」。
 * <b>令牌怎么签、存多久、放哪、怎么吊销，一概不在这里</b> —— 那是接入层的决定，
 * 而且各端可以不同（C 端用不透明令牌进 Redis，内部工具可能压根不需要令牌）。
 * 把发令牌焊进认证，等于让所有调用方都得接受同一套会话模型。
 *
 * <h3>为什么从 solvela-app 搬下来</h3>
 * 这段逻辑原先整个长在 C 端网关里（{@code solvela.app.service.MemberLoginService}）。
 * 结果是：短信验证码登录、第三方登录、内部工具想复用同一套「查人 → 判状态 → 验密码 →
 * 记失败」，一行都用不上，只能各写一遍 —— 而三份实现漂移的第一天不会有任何报错。
 * 现在网关只剩「翻译成 HTTP」这一件事。
 *
 * <h3>失败一律用返回值，不抛异常</h3>
 * 见 {@link AuthFailReason} 的类注释。域只说原因，措辞由调用方定。
 *
 * @Date 2026-08-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAuthService implements MemberAuthApi {

    private static final String DEFAULT_DEVICE_TYPE = "H5";

    /**
     * 查无此人时拿来「验」一次的假哈希，让两条路径的耗时落在同一个量级。
     *
     * <h3>🔴 必须是一个真的 Argon2 串，而且参数与线上一致</h3>
     * 随便塞一个字符串的话 {@code matches} 会在解析阶段就返回 false，几乎不花时间 ——
     * 那就白做了。这一串是用 {@code PasswordCipher.encode} 生成的，所以它的 m/t/p
     * 与线上密码同一档，验一次的耗时也就同一档。
     *
     * <p>内容是什么不重要（没人知道原文，也不需要知道）：它只被用来消耗与真实校验相同的时间。
     */
    private static final String DUMMY_PASSWORD_HASH = PasswordCipher.encode("risk-timing-equalizer");

    /**
     * 失败来源记多久。
     *
     * <p>⚠️ 这里是<b>写</b>的一侧，{@code ACCOUNT_FAIL_BURST} 规则是读的一侧 ——
     * 两边都从规则的 params 取窗口才真正一致，而写的这一刻拿不到规则配置（它在失败路径上，
     * 不该再去读一次规则表）。所以写的窗口给得比规则的默认值<b>宽</b>：
     * 记多了只是多留几条过期数据，记少了会让规则读不到本该看见的失败。
     */
    private static final java.time.Duration FAIL_BURST_WINDOW = java.time.Duration.ofHours(6);

    private static final int TRACE_ID_MAX_LENGTH = 64;

    private static final int REMARK_MAX_LENGTH = 128;

    private final MemberAuthDao memberAuthDao;
    /**
     * 注册委托给它。本类是<b>读</b>（验身份），注册是<b>写</b>（建号 + 事务），
     * 塞进同一个类只会让「会员认证：验明身份，仅此而已」这句话变成假话。
     * 契约共用 {@link MemberAuthApi} 的理由见那个接口上 register 方法的注释。
     */
    private final MemberRegisterService memberRegisterService;
    private final MemberLoginLogDao memberLoginLogDao;
    private final PiiHasher piiHasher;

    /** 风控引擎。前置、后置两个决策点，以及每次尝试一行流水 */
    private final MemberRiskEngine riskEngine;

    /** 密码阶梯冷却。取代了改造前的账号锁（{@code MemberOperationLimitService} 的 LOGIN 类型） */
    private final PasswordCooldownStore cooldownStore;

    /**
     * 这个账号最近的失败来自哪些 IP。
     *
     * <p>🔴 单独记一份，不从登录日志数 —— 那里的 {@code LOGIN_FAIL} 混着
     * 「签了二次验证凭票」和「被冷却挡下」，照它数会把正常登录的人算成失败。
     */
    private final AccountFailTracker failTracker;

    /** 登录成功后要让画像缓存失效，否则用户在新网络上验过一次码，下次来还是「陌生网络」 */
    private final LoginProfileProvider profileProvider;

    /** 核身路由：结论只说强度，码发到哪条通道由它选（方案 3.7） */
    private final VerifyRouter verifyRouter;

    private final PiiCipher piiCipher;
    private final DeviceGuard deviceGuard;
    private final LoginIpGuard loginIpGuard;
    private final LoginChallengeStore challengeStore;
    private final NewDeviceLoginNotifier newDeviceLoginNotifier;
    private final MemberEmailCodeService emailCodeService;
    private final MemberEmailCodeIssuer emailCodeIssuer;

    private final MemberSmsCodeService smsCodeService;

    private final solvela.member.sms.MemberSmsCodeIssuer smsCodeIssuer;

    private final solvela.member.device.DeviceDispositionService dispositionService;
    private final MemberEmailBindService emailBindService;

    private final solvela.member.sms.MemberPhoneBindService phoneBindService;
    private final MemberPasswordResetService passwordResetService;

    /**
     * 手机号 + 密码注册。逻辑全在 {@link MemberRegisterService}，本方法只是契约的落点。
     */
    @Override
    public MemberRegisterResult register(MemberRegisterCmd cmd) {
        return memberRegisterService.register(cmd);
    }

    /**
     * 认证。三种登录方式走<b>同一条主干</b>，只有「怎么找人」和「怎么验凭据」按类型分派。
     *
     * <p>分支顺序是<b>有讲究的</b>，别调换：账号状态在验凭据之前（被冻结的账号不该还能
     * 用来试探密码对不对），限制检查也在验凭据之前（被限制期间连试的机会都没有，
     * 否则限制形同虚设）。
     *
     * <h3>为什么不是三个并行方法</h3>
     * 设备闸、三道闸、登录日志、一机多号判定<b>三种方式完全一样</b>。
     * 各写一遍的话，那些逻辑就有了三份，而它们漂移的第一天不会有任何报错。
     *
     * <p>类型由调用方<b>显式传</b>，不从输入格式猜 —— 见 {@link MemberLoginType} 的类注释。
     */
    @Override
    public MemberAuthResult authenticate(MemberAuthCmd cmd) {

        // 老调用点可能没传（本字段 2026-09-09 才加），按最早的那条通道兜底
        MemberLoginType loginType = cmd.loginType() == null
                ? MemberLoginType.PHONE_PASSWORD
                : cmd.loginType();

        // ---------- 规范化身份 ----------
        // 必须先规范化再算摘要：手机号 "138 0000 0000" 与 "13800000000"、
        // 邮箱 "A@Example.com" 与 "A@example.com" 都会算出不同的 hash
        String identity = normalizeIdentity(loginType, cmd.identity());
        if (identity == null) {
            // 格式都不对，谈不上是对哪个账号的尝试：开一个没有身份摘要的会话，只为留下一行流水
            try (RiskSession risk = riskEngine.open(riskContext(loginType, null, null, null, cmd))) {
                return risk.end(MemberRiskOutcomeEnum.BAD_FORMAT,
                        MemberAuthResult.fail(loginType == MemberLoginType.PHONE_PASSWORD
                                ? AuthFailReason.BAD_PHONE_FORMAT
                                : AuthFailReason.BAD_EMAIL_FORMAT));
            }
        }

        /*
         * ---------- 按摘要找人 ----------
         * 🔴 挪到了风控【之前】。改造前它在设备闸、IP 闸之后，理由是「被限的设备不该还能试探
         *    这个号注册过没有」。那个顾虑现在由另一条更强的约束守着：
         *    前置决策【可以】看账号，但账号信息【只能把结论往松里调】（方案 §3.1）——
         *    于是「有号」和「没号」得到的回答一模一样，查不查得到人都不会漏出去。
         *
         *    换来的是密码冷却能按「可信设备」分桶：没有 member 就算不出信任，
         *    而算不出信任就只能让所有人共用一个桶 —— 那等于任何知道你邮箱的人都能把你锁在门外。
         */
        Member member = findMember(loginType, identity);
        String identityHashHex = piiHasher.hash(identity);

        try (RiskSession risk = riskEngine.open(riskContext(loginType, identity, identityHashHex, member, cmd))) {
            MemberAuthResult blocked = runPreDecision(risk, cmd);
            if (blocked != null) {
                return blocked;
            }
            return afterPreDecision(risk, loginType, identity, member, cmd);
        }
    }

    /**
     * 前置决策：被挡下就返回要给客户端的东西，放行返回 null。
     *
     * <p>动作（降设备档这类有副作用的事）在这里执行 —— 只执行<b>生效</b>结论带的那些，
     * 陪跑规则请求的动作已经被引擎丢掉了。
     */
    private MemberAuthResult runPreDecision(RiskSession risk, MemberAuthCmd cmd) {
        RiskDecision pre = riskEngine.evaluatePre(risk);
        applyActions(pre, cmd.deviceId());
        if (pre.is(MemberRiskDecisionEnum.CAPTCHA)) {
            // 票由接入层核销，域只看结论。已经过了就继续往下走
            return risk.context().isCaptchaPassed()
                    ? null
                    : risk.end(MemberRiskOutcomeEnum.CAPTCHA_REQUIRED, MemberAuthResult.captchaRequired());
        }
        if (pre.is(MemberRiskDecisionEnum.COOLDOWN)) {
            /*
             * 🔴 单独一个原因，不并进 OPERATION_LIMITED：被冷却的人此刻还有两条路
             * （验证码登录、找回密码），而「操作过于频繁，请稍后再试」除了等什么也说不了。
             * 见 AuthFailReason.PASSWORD_COOLDOWN。
             */
            return risk.end(MemberRiskOutcomeEnum.COOLDOWN,
                    MemberAuthResult.passwordCooldown(pre.retryAfterSeconds()));
        }
        if (pre.atLeast(MemberRiskDecisionEnum.LIMIT)) {
            // 🔴 限流与拒绝对外是<b>同一句</b>：告诉对方「你被拉黑了」等于给他一个精确的调试反馈
            return risk.end(MemberRiskOutcomeEnum.PRE_BLOCKED, MemberAuthResult.ipLimited(pre.retryAfterSeconds()));
        }
        return null;
    }

    /** 前置放行之后的主干：找人、状态、验凭据、后置决策、收尾 */
    private MemberAuthResult afterPreDecision(RiskSession risk, MemberLoginType loginType, String identity,
                                              Member member, MemberAuthCmd cmd) {
        if (member == null) {
            Integer attemptsBeforeCooldown = null;
            // 🔴 验证码登录时，即使查无此人也要照常验一次码，【而且要返回验码本身的结果】。
            //    发码那一步对没有会员的邮箱也存了码（MailDelivery.SUPPRESS），
            //    这里如果一律回 BAD_CREDENTIALS，两条路径的回答又不一样了 ——
            //    随便输个错码，有账号回「验证码错误」、没账号回「邮箱或密码错误」，
            //    发码那一步藏住的东西在这里漏光。这条有用例钉着（输错码的回答一致）。
            //
            //    码【对】了才落到下面的 BAD_CREDENTIALS。那需要猜中一个六位数
            //    （百万分之一，且只有 5 次机会），不构成可用的枚举手段。
            if (loginType == MemberLoginType.EMAIL_CODE) {
                AuthFailReason codeProblem = toAuthFailReason(
                        emailCodeService.verify(EmailCodeScene.LOGIN, identity, cmd.credential()));
                if (codeProblem != null) {
                    loginIpGuard.recordUnknownAccount(cmd.clientIp(), maskIdentity(loginType, identity));
                    return risk.end(MemberRiskOutcomeEnum.UNKNOWN_ACCOUNT, MemberAuthResult.fail(codeProblem));
                }
            } else {
                /*
                 * 🔴 查无此人也要跑一次密码哈希，而且要算一次【真的】Argon2。
                 *
                 * 不跑的话这个分支几十毫秒就返回，而账号存在时要验一次 Argon2 —— 响应时间本身
                 * 就在回答「这个号存不存在」，前面所有「把三种失败合并成一句话」的努力全都白做。
                 * 这个缝在改造前一直开着（2026-10-15 对着代码核对时发现）。
                 *
                 * 同理也要记一次冷却计数：否则「错几次会不会被冷却」又成了一个账号枚举接口。
                 */
                PasswordCipher.matches(cmd.credential(), DUMMY_PASSWORD_HASH);
                /*
                 * 🔴 连「还剩几次」也要照样算出来并返回。
                 *
                 * 只在账号存在时给这个数的话，「错两次看看有没有提示」就是一个精确的账号枚举接口 ——
                 * 而阶梯本来就按身份摘要计数，没有账号的身份算出的数一模一样，返回它不泄露任何东西。
                 */
                attemptsBeforeCooldown = cooldownStore.onPasswordFailure(risk.context());
            }
            // 🔴 查无此人以前一行记录都不留（登录日志的 member_id 是 NOT NULL）——
            //    现在 IP 维度数得到它，而且风控流水里有完整的一行（按身份摘要聚合）
            loginIpGuard.recordUnknownAccount(cmd.clientIp(), maskIdentity(loginType, identity));
            return risk.end(MemberRiskOutcomeEnum.UNKNOWN_ACCOUNT,
                    MemberAuthResult.badCredentials(attemptsBeforeCooldown));
        }

        /*
         * 三道闸，顺序即安全，返回非 null 即被挡下：
         *   状态 -> 限制 -> 密码
         * 每一道都必须排在密码比对之前，理由分别写在各自的方法上。
         */
        MemberAuthResult statusProblem = checkStatus(member, cmd);
        if (statusProblem != null) {
            loginIpGuard.recordFailure(cmd.clientIp());
            return risk.end(MemberRiskOutcomeEnum.FROZEN, statusProblem);
        }
        MemberAuthResult credentialProblem = verifyCredential(risk, loginType, identity, member, cmd);
        if (credentialProblem != null) {
            loginIpGuard.recordFailure(cmd.clientIp());
            return risk.end(MemberRiskOutcomeEnum.BAD_CREDENTIALS, credentialProblem);
        }

        /*
         * ---------- 后置决策 ----------
         * 🔴 排在密码校验【之后】：不知道密码就拿不到票，拿不到票就发不了码 ——
         *    那条码是花钱的（短信），也是轰炸的原料。
         */
        RiskDecision post = riskEngine.evaluatePost(risk);
        applyActions(post, cmd.deviceId());
        if (post.needsOtp()) {
            return issueChallenge(risk, loginType, identity, member, cmd, post.decision());
        }
        if (post.atLeast(MemberRiskDecisionEnum.LIMIT)) {
            return risk.end(MemberRiskOutcomeEnum.POST_BLOCKED,
                    MemberAuthResult.deviceLimited(post.retryAfterSeconds()));
        }

        return risk.end(MemberRiskOutcomeEnum.SUCCESS, completeLogin(member, cmd, risk.context().getIdentityHashHex()));
    }

    /**
     * 签一张二次验证凭票。
     *
     * <p>🔴 <b>先落流水再签票</b>：票里要带这一行流水的引用（id + 时间），
     * 验码那一行才能指回来 —— 不然「签票」和「验码」在流水里是两条互不相干的记录。
     */
    private MemberAuthResult issueChallenge(RiskSession risk, MemberLoginType loginType, String identity,
                                            Member member, MemberAuthCmd cmd,
                                            MemberRiskDecisionEnum post) {
        if (loginType == MemberLoginType.EMAIL_CODE) {
            /*
             * 邮箱验证码登录【本身就是一道验证码】，不再要第二道：
             * 再要一道的话，用户会在同一个邮箱里收到两封信，而第二封证明不了第一封证明不了的任何事情。
             * 规则那边也拦了一道（DeviceObserveRule），这里是兜底 —— 将来新增一条后置规则时，
             * 它不必自己记得这个例外。
             */
            return risk.end(MemberRiskOutcomeEnum.SUCCESS, completeLogin(member, cmd, risk.context().getIdentityHashHex()));
        }
        /*
         * ---------- 选通道，而且要在签票【之前】 ----------
         * 🔴 签了票却发不出码是最糟的一种失败：用户停在一个「获取验证码」永远失败的界面上，
         *    服务端日志里只剩一串 SEND_FAILED —— 看不出根因是这个账号压根没有通道。
         *    所以先问路由，一条都没有就当场给出 LOGIN_VERIFY_UNAVAILABLE。
         */
        VerifyRouter.Route route = verifyRouter.route(loginType, member.getMemberId(), post);
        if (route == null) {
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL,
                    "风控要求二次验证：无可用核身通道");
            return risk.end(MemberRiskOutcomeEnum.POST_BLOCKED,
                    MemberAuthResult.fail(AuthFailReason.LOGIN_VERIFY_UNAVAILABLE));
        }
        // 落库：事后要能数出「有多少次本该换通道却退回了」（接上短信那天这个数应该归零）
        risk.verifyChannel(route.channel().getValue());
        RiskEventRef ref = risk.end(MemberRiskOutcomeEnum.CHALLENGED);
        String ticket = challengeStore.issue(new LoginChallengeStore.Challenge(
                member.getMemberId(), loginType, cmd.deviceId(), cmd.deviceType(), ref,
                route.channel(), identity));
        saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "风控要求二次验证：已签发凭票");
        return MemberAuthResult.challenge(ticket, route.channel());
    }

    /**
     * 组装规则看得到的东西。
     *
     * <p>只放请求自带、零 IO 就有的 —— 画像、设备信任这类要查库的由规则按需去取，
     * 而且同一次尝试里只算一次（见 {@code RiskContext#feature}）。
     */
    private RiskContext riskContext(MemberLoginType loginType, String identity, String identityHashHex,
                                    Member member, MemberAuthCmd cmd) {
        return new RiskContext(MemberRiskEventTypeEnum.LOGIN, loginType, identity, identityHashHex, member,
                cmd.deviceId(), cmd.deviceType(), cmd.clientIp(),
                // null（老调用方没传）当作「没过滑块」—— 往严的方向偏
                Boolean.TRUE.equals(cmd.captchaPassed()));
    }

    /** 执行生效结论带的动作。陪跑规则请求的动作已经被引擎丢掉了，这里拿到的都该执行 */
    private void applyActions(RiskDecision decision, String deviceId) {
        if (decision.has(RiskAction.OBSERVE_DEVICE) && deviceId != null) {
            dispositionService.observe(deviceId, "风控命中：量级异常");
        }
        // RECORD_LIMIT（把长冷却写进 t_member_operation_limit 让后台可见可解除）与
        // NOTIFY_OWNER（通知主人）都还没接，见方案 §9：它们跟后台页面一起在 P1 做。
        // 🔴 刻意不在这里「先写一半」：一个解除了却不生效的后台按钮比没有按钮更糟
    }

    /**
     * 登录的收尾：清失败计数、判一机多号、写登录日志。
     *
     * <p>正常登录与「观察档凭票验码成功」共用这一段 —— 两条路最后必须走同一套收尾，
     * 否则凭票那条会悄悄漏掉一机多号的记录，而 dry-run 期间要的正是这份数据。
     */
    private MemberAuthResult completeLogin(Member member, MemberAuthCmd cmd, String identityHashHex) {
        // 清掉密码冷却的计数：登录成功证明了身份，之前攒的连续失败不该再算（两个桶都清，
        // 否则换台新手机就会一脚踩进攻击者在共用桶里攒的计数）
        cooldownStore.onIdentityVerified(identityHashHex, cmd.deviceId());
        /*
         * 画像变了：这一次的网段、国家要立刻算进「常用」。
         * 🔴 不失效的话，用户在新网络上验过一次码、下一次来还是「陌生网络」——
         * 于是他每次登录都要验一道，而他已经证明过自己了。
         */
        profileProvider.evict(member.getMemberId());
        // 失败来源清零：登录成功说明这个号没被攻破，之前那几次失败不该再算
        failTracker.clear(member.getMemberId());

        // 🔴 必须在写本次成功日志【之前】判断：写了之后「这台设备登过没有」永远是「登过」
        newDeviceLoginNotifier.onLoginSucceeded(member.getMemberId(), cmd.deviceId(), cmd.deviceType(), cmd.clientIp());

        saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_SUCCESS, null);
        return MemberAuthResult.ok(toIdentity(member));
    }

    @Override
    public LoginChallengeCodeResult sendChallengeCode(LoginChallengeCmd cmd) {
        LoginChallengeStore.Challenge challenge = validChallenge(cmd);
        if (challenge == null) {
            return LoginChallengeCodeResult.fail(LoginChallengeCodeResult.Reason.CHALLENGE_EXPIRED);
        }
        /*
         * 码发到哪【只看票上的通道】，不再按登录方式算一遍 ——
         * 换通道（手机号登录、码发邮箱）和退回（短信没接、只能走邮箱）都只有路由知道。
         * 这里重算一次就是把那套判断复制了一份，而两份漂移的表现是
         * 「告诉用户发到了邮箱，码却发去了短信」。
         *
         * 🔴 发到【哪个】邮箱 / 手机号也不看票上的 identity 了：换通道时那两个根本不是一个东西。
         *    按会员号取当前绑定的那一个。
         */
        Member contacts = memberAuthDao.selectContacts(challenge.memberId());
        if (contacts == null) {
            return LoginChallengeCodeResult.fail(LoginChallengeCodeResult.Reason.CHALLENGE_EXPIRED);
        }
        return switch (challenge.channel()) {
            case SMS -> {
                String phone = piiCipher.decrypt(contacts.getPhone());
                if (SolvelaStringUtil.isBlank(phone)) {
                    yield LoginChallengeCodeResult.fail(LoginChallengeCodeResult.Reason.SEND_FAILED);
                }
                SmsCodeSendResult sent = smsCodeService.send(SmsScene.LOGIN, phone, cmd.clientIp());
                yield sent.success()
                        ? LoginChallengeCodeResult.sent(MemberPhoneUtil.mask(phone))
                        : toChallengeFailure(sent.reason().name(), sent.retryAfterSeconds());
            }
            case EMAIL -> {
                String email = piiCipher.decrypt(contacts.getEmail());
                if (SolvelaStringUtil.isBlank(email)) {
                    yield LoginChallengeCodeResult.fail(LoginChallengeCodeResult.Reason.SEND_FAILED);
                }
                EmailCodeSendResult sent = emailCodeService.send(EmailCodeScene.LOGIN, email, cmd.clientIp());
                yield sent.success()
                        ? LoginChallengeCodeResult.sent(MemberEmailUtil.mask(email))
                        : toChallengeFailure(sent.reason().name(), sent.retryAfterSeconds());
            }
        };
    }

    /**
     * 两条通道的发码失败原因同名（TOO_FREQUENT / DAILY_LIMIT_REACHED / SEND_FAILED / 格式错）。
     * 格式错只可能是库里的数据坏了，当成发不出去。
     */
    private static LoginChallengeCodeResult toChallengeFailure(String reason, long retryAfterSeconds) {
        return switch (reason) {
            case "TOO_FREQUENT" -> LoginChallengeCodeResult.retryLater(
                    LoginChallengeCodeResult.Reason.TOO_FREQUENT, retryAfterSeconds);
            case "DAILY_LIMIT_REACHED" -> LoginChallengeCodeResult.retryLater(
                    LoginChallengeCodeResult.Reason.DAILY_LIMIT_REACHED, retryAfterSeconds);
            default -> LoginChallengeCodeResult.fail(LoginChallengeCodeResult.Reason.SEND_FAILED);
        };
    }

    @Override
    public MemberAuthResult verifyChallenge(LoginChallengeCmd cmd) {
        LoginChallengeStore.Challenge challenge = validChallenge(cmd);
        if (challenge == null) {
            return MemberAuthResult.fail(AuthFailReason.CHALLENGE_EXPIRED);
        }
        // 登录日志、一机多号记在【签票时】那台设备上 —— 票上记着，不信请求当时带的
        MemberAuthCmd authCmd = new MemberAuthCmd(challenge.loginType(), challenge.identity(), null, null,
                challenge.deviceType(), cmd.clientIp(), challenge.deviceId(), false);
        String identity = challenge.identity();
        String identityHashHex = piiHasher.hash(identity);
        // 签票到验码之间账号可能被冻结了 —— 状态以此刻为准
        Member member = memberAuthDao.selectForAuth(challenge.memberId());

        /*
         * 验码也是一次尝试，也要有一行流水 —— 而且它靠 ref_event_id 指回签票那一行，
         * 「要了码的人后来验过没有」才查得出来（方案 §8.2 的打扰率就是这么算的）。
         * 这里<b>不</b>再跑一遍规则：风控在签票那一步已经判过了，这一步只回答「码对不对」。
         */
        try (RiskSession risk = riskEngine.open(
                new RiskContext(MemberRiskEventTypeEnum.LOGIN_VERIFY, challenge.loginType(), identity,
                        identityHashHex, member, challenge.deviceId(), challenge.deviceType(),
                        cmd.clientIp(), false),
                challenge.eventRef())) {

            long ipWait = loginIpGuard.check(cmd.clientIp());
            if (ipWait > 0) {
                return risk.end(MemberRiskOutcomeEnum.PRE_BLOCKED, MemberAuthResult.ipLimited(ipWait));
            }

            /*
             * 🔴 验的是【发码时那条通道、那个目标】，所以同样只看票上的 channel，
             *    而且目标按会员号现取 —— 与 sendChallengeCode 必须是同一套判断。
             *
             * 两边不一致的代价很具体：换通道时码发去了邮箱，这里却拿登录用的手机号去验，
             * 结果是用户收到了码、输对了码，却永远被告知「验证码不正确」。
             */
            Member contacts = member == null ? null : memberAuthDao.selectContacts(challenge.memberId());
            String verifyTarget = contacts == null ? null
                    : piiCipher.decrypt(challenge.channel() == MemberVerifyChannelEnum.SMS
                            ? contacts.getPhone() : contacts.getEmail());
            if (SolvelaStringUtil.isBlank(verifyTarget)) {
                // 签票之后联系方式被解绑了。不能当成「码错了」—— 那会让用户一直重发一个永远验不过的码
                saveLoginLog(challenge.memberId(), authCmd, LoginLogResultEnum.LOGIN_FAIL, "二次验证通道已失效");
                return risk.end(MemberRiskOutcomeEnum.VERIFY_FAILED,
                        MemberAuthResult.fail(AuthFailReason.CHALLENGE_EXPIRED));
            }
            String outcome = switch (challenge.channel()) {
                case SMS -> smsCodeService.verify(SmsScene.LOGIN, verifyTarget, cmd.code()).name();
                case EMAIL -> emailCodeService.verify(EmailCodeScene.LOGIN, verifyTarget, cmd.code()).name();
            };
            if ("TOO_MANY_ATTEMPTS".equals(outcome)) {
                // 码被错到作废：票也作废，让他从输密码重新来 —— 否则同一张票可以无限次「重新发码再猜」
                challengeStore.discard(cmd.ticket());
                loginIpGuard.recordFailure(cmd.clientIp());
                saveLoginLog(challenge.memberId(), authCmd, LoginLogResultEnum.LOGIN_FAIL, "二次验证码错误次数过多");
                return risk.end(MemberRiskOutcomeEnum.VERIFY_FAILED,
                        MemberAuthResult.fail(AuthFailReason.CHALLENGE_EXPIRED));
            }
            if (!"OK".equals(outcome)) {
                loginIpGuard.recordFailure(cmd.clientIp());
                saveLoginLog(challenge.memberId(), authCmd, LoginLogResultEnum.LOGIN_FAIL, "二次验证码不正确");
                return risk.end(MemberRiskOutcomeEnum.VERIFY_FAILED,
                        MemberAuthResult.fail(AuthFailReason.DEVICE_VERIFICATION_FAILED));
            }

            challengeStore.discard(cmd.ticket());
            if (member == null) {
                return risk.end(MemberRiskOutcomeEnum.UNKNOWN_ACCOUNT,
                        MemberAuthResult.fail(AuthFailReason.BAD_CREDENTIALS));
            }
            MemberAuthResult statusProblem = checkStatus(member, authCmd);
            if (statusProblem != null) {
                return risk.end(MemberRiskOutcomeEnum.FROZEN, statusProblem);
            }
            return risk.end(MemberRiskOutcomeEnum.VERIFY_PASSED,
                    completeLogin(member, authCmd, identityHashHex));
        }
    }

    /**
     * 找票，并确认它是在<b>这台设备</b>上签的。无效一律返回 null。
     *
     * <p>🔴 设备必须一致：票是在「这台可疑设备上、密码已验对」这个前提下签的，
     * 拿到别的机器上用，那个前提就不成立了。
     */
    private LoginChallengeStore.Challenge validChallenge(LoginChallengeCmd cmd) {
        LoginChallengeStore.Challenge challenge = challengeStore.find(cmd.ticket());
        if (challenge == null || !java.util.Objects.equals(challenge.deviceId(), cmd.deviceId())) {
            return null;
        }
        return challenge;
    }

    /**
     * 账号状态。<b>必须排在验密码之前</b> —— 被冻结的账号不该还能拿来试探密码对不对：
     * 一个已被判定为高风险的账号，不该再提供任何「密码猜对了没有」的信号。
     */
    private MemberAuthResult checkStatus(Member member, MemberAuthCmd cmd) {
        if (member.getStatus() == MemberStatusEnum.CANCELLED) {
            // 正常走不到：注销会把 phone_hash 置 NULL，查人那一步就查不到了。
            // 留着是因为「查不到」依赖的是另一处代码写对，而这一行只值三行代价。
            return MemberAuthResult.fail(AuthFailReason.BAD_CREDENTIALS);
        }
        if (member.getStatus() == MemberStatusEnum.FROZEN) {
            // 冻结账号被反复尝试，同样是设备侧的异常信号：一台机器挨个试一批
            // 已被封的号，账号锁那一侧完全看不见（每个号各锁各的）
            deviceGuard.recordLoginFailure(cmd.deviceId());
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "账号已冻结");
            return MemberAuthResult.fail(AuthFailReason.ACCOUNT_FROZEN);
        }
        return null;
    }

    /**
     * 发一封邮箱验证码。逻辑全在 {@link MemberEmailCodeIssuer}，本方法只是契约的落点 ——
     * 与 {@link #register} 委托给 {@code MemberRegisterService} 同一个做法。
     */
    @Override
    public EmailCodeSendResult sendEmailCode(EmailCodeSendCmd cmd) {
        return emailCodeIssuer.issue(cmd.scene(), cmd.email(), cmd.clientIp(), cmd.currentMemberId());
    }

    /**
     * 发一条短信验证码。逻辑全在 {@link MemberSmsCodeService}，本方法只是契约的落点。
     *
     * <p>2026-09-10 起走 {@link solvela.member.sms.MemberSmsCodeIssuer}，
     * 与邮箱那条对称 —— 手机号找回密码和绑定手机号落地之后，
     * 「该不该静默」这个决定才真的有了内容。
     */
    @Override
    public SmsCodeSendResult sendSmsCode(SmsCodeSendCmd cmd) {
        return smsCodeIssuer.issue(cmd.scene(), cmd.phone(), cmd.clientIp(), cmd.currentMemberId());
    }

    /**
     * 当前会员的联系方式，<b>脱敏之后</b>再出域。
     *
     * <p>🔴 解密只发生在这一个方法里，而且解出来的明文<b>立刻被打码</b>，
     * 不进返回值、不进日志。整套 PiiCipher 的意义就在于此 ——
     * 页面上要显示的本来就只是 {@code 138****8000}。
     */
    @Override
    public MemberContactView getContact(Long memberId) {
        Member member = memberAuthDao.selectContact(memberId);
        if (member == null) {
            return new MemberContactView(null, null, false);
        }
        return new MemberContactView(
                maskCipher(member.getPhone(), MemberPhoneUtil::mask),
                maskCipher(member.getEmail(), MemberEmailUtil::mask),
                !SolvelaStringUtil.isEmpty(member.getPassword()));
    }

    /** 密文 → 明文 → 打码。解不出来时返回 null，不抛：一个展示接口不该因此 500。 */
    private String maskCipher(String cipher, java.util.function.UnaryOperator<String> mask) {
        if (SolvelaStringUtil.isEmpty(cipher)) {
            return null;
        }
        try {
            return mask.apply(piiCipher.decrypt(cipher));
        } catch (Exception e) {
            log.warn("【联系方式】解密失败, memberId 已省略", e);
            return null;
        }
    }

    /**
     * 绑定 / 更换邮箱。逻辑全在 {@link MemberEmailBindService}，本方法只是契约的落点。
     */
    @Override
    public MemberEmailBindResult bindEmail(MemberEmailBindCmd cmd) {
        return emailBindService.bind(cmd);
    }

    /**
     * 绑定 / 更换手机号。逻辑全在 {@link MemberPhoneBindService}，本方法只是契约的落点。
     */
    @Override
    public MemberPhoneBindResult bindPhone(MemberPhoneBindCmd cmd) {
        return phoneBindService.bind(cmd);
    }

    /**
     * 重置密码。逻辑全在 {@link MemberPasswordResetService}，本方法只是契约的落点。
     */
    @Override
    public MemberPasswordResetResult resetPassword(MemberPasswordResetCmd cmd) {
        return passwordResetService.reset(cmd);
    }

    /**
     * 按登录方式规范化身份；非法返回 null。
     *
     * <p>用 switch 表达式：新增一种登录方式时<b>编译不过</b>，
     * 而不是悄悄落进某个兜底分支去按手机号规范化一个邮箱。
     */
    /** 身份打码后进日志：邮箱 a***@x.com，手机号 138****0000 */
    private static String maskIdentity(MemberLoginType loginType, String identity) {
        return loginType == MemberLoginType.PHONE_PASSWORD
                ? MemberPhoneUtil.mask(identity)
                : MemberEmailUtil.mask(identity);
    }

    private static String normalizeIdentity(MemberLoginType loginType, String rawIdentity) {
        return switch (loginType) {
            case PHONE_PASSWORD -> MemberPhoneUtil.normalize(rawIdentity);
            case EMAIL_PASSWORD, EMAIL_CODE -> MemberEmailUtil.normalize(rawIdentity);
        };
    }

    /**
     * 按登录方式找人；查无此人返回 null。
     *
     * <p>两个查询各查各的：邮箱注册出来的会员<b>没有手机号</b>（{@code phone_hash} 为 NULL），
     * 反过来也一样。不要指望其中一个能兜住另一个。
     */
    private Member findMember(MemberLoginType loginType, String identity) {
        String hash = piiHasher.hash(identity);
        return switch (loginType) {
            case PHONE_PASSWORD -> memberAuthDao.selectForLogin(hash);
            case EMAIL_PASSWORD, EMAIL_CODE -> memberAuthDao.selectForLoginByEmail(hash);
        };
    }

    /**
     * 验凭据：密码或邮箱验证码。返回 null 表示通过。
     */
    private MemberAuthResult verifyCredential(RiskSession risk, MemberLoginType loginType, String identity,
                                              Member member, MemberAuthCmd cmd) {
        return switch (loginType) {
            case PHONE_PASSWORD, EMAIL_PASSWORD -> verifyPassword(risk, member, cmd);
            case EMAIL_CODE -> verifyEmailCode(identity, member, cmd);
        };
    }

    /**
     * 验邮箱验证码。返回 null 表示通过。
     *
     * <h3>失败原因可以说得具体，而且不泄露账号是否存在</h3>
     * 「验证码错误」「验证码已失效」「错太多次了」三者对用户的意义完全不同 ——
     * 含糊成一句会让他反复重试一件必然失败的事。
     *
     * <p>之所以敢说具体，是因为<b>没有会员的邮箱也存了码</b>
     * （见 {@code MailDelivery.SUPPRESS}），两条路径给出的这三种回答分布一致。
     *
     * <p>⚠️ 仍有一个<b>无法消除</b>的差别：攻击者用<b>自己拥有的</b>邮箱发码时，
     * 收不收得到信本身就说明了有没有账号。但那个邮箱他本来就能拿去注册一次，
     * 而注册接口必须如实回答「已被注册」（藏了用户就没法用，见 {@code RegisterFailReason}）——
     * 也就是说这条信息对他控制的邮箱本来就是可得的。SUPPRESS 要堵的是
     * <b>他不控制的</b>那些邮箱，那里没有任何信号漏出去。
     */
    private MemberAuthResult verifyEmailCode(String email, Member member, MemberAuthCmd cmd) {
        EmailCodeVerifyResult result = emailCodeService.verify(EmailCodeScene.LOGIN, email, cmd.credential());
        if (result == EmailCodeVerifyResult.OK) {
            return null;
        }
        /*
         * 🔴 邮箱验证码错<b>不进密码冷却的阶梯</b>：验证码自己已经有「错 5 次作废、必须重发」，
         * 不需要第二道。更要紧的是 —— 验证码登录正是被恶意冷却时剩下的那条出口，
         * 把它也算进阶梯，那条出口就跟着被堵上了。
         *
         * 设备计数照记：它挡的是「这台机器在挨个试不同的号」，与账号维度无关。
         */
        deviceGuard.recordLoginFailure(cmd.deviceId());
        failTracker.record(member.getMemberId(), cmd.clientIp(), FAIL_BURST_WINDOW);
        saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "邮箱验证码" + result.name());

        return MemberAuthResult.fail(toAuthFailReason(result));
    }

    /**
     * 验码结果 → 失败原因；通过时返回 null。
     *
     * <p>抽出来是因为它有<b>两个调用点</b>：查到会员时走一次，查无此人时也走一次 ——
     * 而两处必须给出<b>完全一样</b>的映射，否则「有账号 / 没账号」就能被区分出来。
     * 两份 switch 迟早漂移，而漂移的表现是一个悄悄打开的账号枚举接口。
     */
    private static AuthFailReason toAuthFailReason(EmailCodeVerifyResult result) {
        return switch (result) {
            case OK -> null;
            case NOT_FOUND -> AuthFailReason.EMAIL_CODE_EXPIRED;
            case MISMATCH -> AuthFailReason.EMAIL_CODE_MISMATCH;
            case TOO_MANY_ATTEMPTS -> AuthFailReason.EMAIL_CODE_LOCKED;
        };
    }

    /**
     * 验密码。返回 null 表示通过。
     *
     * <p>「没设过密码」单独一个原因，不混进「密码错误」—— 混了的话用户会一直重试
     * 一个他从来没设过的密码。
     */
    private MemberAuthResult verifyPassword(RiskSession risk, Member member, MemberAuthCmd cmd) {
        if (SolvelaStringUtil.isEmpty(member.getPassword())) {
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "未设置登录密码");
            return MemberAuthResult.fail(AuthFailReason.NO_PASSWORD);
        }
        if (PasswordCipher.matches(cmd.credential(), member.getPassword())) {
            return null;
        }
        /*
         * 阶梯冷却记一次。它取代了改造前的账号锁（30 分钟错 5 次、锁 30 分钟），两处关键的不同：
         *   1. 按【身份摘要】计数，所以不存在的账号也照样冷却 —— 否则「会不会被冷却」就是账号枚举接口；
         *   2. 可信设备【单独一个桶】—— 否则任何知道你邮箱的人连错几次就能把你锁在门外。
         * 见 PasswordCooldownStore 的类注释。
         */
        Integer attemptsBeforeCooldown = cooldownStore.onPasswordFailure(risk.context());
        // 设备维度也记一次。两者互不替代：冷却挡的是「这个号被爆破」，
        // 设备计数挡的是「这台机器在挨个试不同的号」—— 后者冷却完全看不见
        deviceGuard.recordLoginFailure(cmd.deviceId());
        // 第三个维度：这一次失败是从哪个 IP 来的。冷却只数次数，数不到「多个来源同时围攻一个号」
        failTracker.record(member.getMemberId(), cmd.clientIp(), FAIL_BURST_WINDOW);
        saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "手机号或密码错误");
        /*
         * 🔴 这里<b>不</b>返回「还要等多久」，哪怕这一次失败刚好触发了冷却。
         *
         * 改造前会立刻把剩余时间告诉用户（理由是「别让他再点一次才发现」）。而冷却现在按身份摘要计数，
         * 对不存在的账号同样会触发 —— 这一刻把它说出来，就等于回答了「这个号存不存在」。
         * 代价是用户要再点一次才看到倒计时，那一次的回答对有号、没号完全一样。
         *
         * 「再错几次会冷却」则照样给：它对有号、没号算出的数一模一样（同一个摘要计数器），
         * 而它能让一个真在回忆密码的人停下来 —— 给不给用户看是接入层的决定（方案 6.4）。
         */
        return MemberAuthResult.badCredentials(attemptsBeforeCooldown);
    }

    /**
     * 按会员号取<b>可用身份</b>；会员不存在或状态不正常返回 null。
     *
     * <p>🔴 状态判断收在这里，而不是把 status 交给调用方自己判：
     * 「什么算一个可用身份」只该有一个定义。多一处判断就多一次判漏的机会，
     * 而判漏的表现是「被冻结的人还能正常用」—— 不会有任何报错。
     */
    @Override
    public MemberIdentity getAuthIdentity(Long memberId) {
        if (memberId == null) {
            return null;
        }
        Member member = memberAuthDao.selectForAuth(memberId);
        if (member == null || member.getStatus() != MemberStatusEnum.NORMAL) {
            return null;
        }
        return toIdentity(member);
    }

    /**
     * 记一次退出登录。
     *
     * <p>吊销令牌是接入层的事（会话模型归它），本方法只负责留痕 ——
     * 于是「退出」和登录成功/失败在同一张表、同一套字段里，查一个人的登录轨迹不用 join。
     */
    @Override
    public void recordLogout(MemberLogoutCmd cmd) {
        saveLoginLog(cmd.memberId(), cmd.clientIp(), null, cmd.deviceId(), LoginLogResultEnum.LOGIN_OUT, null);
    }

    private static MemberIdentity toIdentity(Member member) {
        return new MemberIdentity(
                member.getMemberId(),
                member.getMemberName(),
                member.getNickname(),
                member.getAvatarFileId(),
                member.getGender());
    }

    private void saveLoginLog(Long memberId, MemberAuthCmd cmd, LoginLogResultEnum status, String remark) {
        saveLoginLog(memberId, cmd.clientIp(), cmd.deviceType(), cmd.deviceId(), status, remark);
    }

    /**
     * 写登录日志。
     *
     * <p>traceId 从 MDC 取（{@link Trace#id()}），不从参数传：它对每个接口都一样，
     * 进签名是噪音，而且总有调用点会忘了填 —— 忘了的表现只是这一列悄悄变空。
     * 拆成独立服务后由服务端 Filter 把请求头放进 MDC，这一行代码不用改。
     *
     * <p>取值与管理端的 {@code t_login_log.login_result} <b>完全一致</b>，共用
     * {@link LoginLogResultEnum}。这两张表曾经 0/1 相反（本表 1 是成功），
     * 2026-08-29 趁本表还是零行统一了口径 —— 别再往回改。
     *
     * <p>⚠️ 日志失败绝不能影响认证本身 —— 「登不上去是因为日志表满了」这种事排查极其费劲，
     * 而登录日志的价值再高也高不过登录本身。
     */
    private void saveLoginLog(Long memberId, String clientIp, String deviceType, String deviceId,
                              LoginLogResultEnum status, String remark) {
        try {
            MemberLoginLog loginLog = new MemberLoginLog();
            loginLog.setMemberId(memberId);
            loginLog.setClientIp(clientIp);
            loginLog.setIpRegion(SolvelaIpUtil.getRegion(clientIp));
            loginLog.setDeviceType(SolvelaStringUtil.isEmpty(deviceType) ? DEFAULT_DEVICE_TYPE : deviceType);
            // 🔴 允许为 null，而且【为空是正常的】：灰度期间老客户端还没带设备令牌。
            // 查询侧要认这一点，别把 NULL 当异常 —— 它的语义是「设备身份上线前的登录」
            loginLog.setDeviceId(deviceId);
            // os_name / browser_name 暂不填：后端没有 UA 解析库，
            // 与其用几个 indexOf 猜出一堆不可信的值，不如留空 —— 空值至少不会被拿去做统计。
            loginLog.setStatus(status);
            loginLog.setRemark(truncate(remark, REMARK_MAX_LENGTH));
            loginLog.setTraceId(truncate(Trace.id(), TRACE_ID_MAX_LENGTH));
            loginLog.setCreateTime(LocalDateTime.now());
            memberLoginLogDao.insert(loginLog);
        } catch (Exception e) {
            log.error("会员登录日志写入失败, memberId: {}", memberId, e);
        }
    }

    /**
     * 截断到列宽。MySQL 非严格模式下超长是<b>静默截断</b>，严格模式下直接报错 ——
     * 前者让数据悄悄变形，后者让登录失败，两个都不能接受，所以入库前自己截。
     */
    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
