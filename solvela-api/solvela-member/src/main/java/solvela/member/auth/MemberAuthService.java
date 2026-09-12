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
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberStatusEnum;
import solvela.member.Member;
import solvela.member.MemberLoginLog;
import solvela.member.MemberOperationLimit;
import solvela.member.api.AuthFailReason;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
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
import solvela.member.operationlimit.service.MemberOperationLimitService;
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
    private final MemberOperationLimitService operationLimitService;
    private final PiiHasher piiHasher;

    private final PiiCipher piiCipher;
    private final DeviceGuard deviceGuard;
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
            return MemberAuthResult.fail(loginType == MemberLoginType.PHONE_PASSWORD
                    ? AuthFailReason.BAD_PHONE_FORMAT
                    : AuthFailReason.BAD_EMAIL_FORMAT);
        }

        // ---------- 设备闸 ----------
        // 🔴 排在【查会员之前】：被限的设备不该还能拿登录接口去试探「这个号注册过没有」——
        //    那正是 BAD_CREDENTIALS 合并三种失败原因要堵的口子。
        //    deviceId 为 null（老客户端）时 checkLogin 直接放行，见 DeviceGuard 类注释。
        DeviceGuardVerdict deviceVerdict = deviceGuard.checkLogin(cmd.deviceId());
        if (!deviceVerdict.allowed()) {
            return MemberAuthResult.deviceLimited(deviceVerdict.retryAfterSeconds());
        }

        // ---------- 按摘要找人 ----------
        Member member = findMember(loginType, identity);
        if (member == null) {
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
                    return MemberAuthResult.fail(codeProblem);
                }
            }
            // 这里刻意不写登录日志：t_member_login_log.member_id 是 NOT NULL，
            // 没有会员就没有可写的行。「不存在的账号被反复尝试」属于风控范畴，
            // 要防的话得另建一张按 IP/身份聚合的表，不是往会员日志里塞假 member_id。
            return MemberAuthResult.fail(AuthFailReason.BAD_CREDENTIALS);
        }

        /*
         * 三道闸，顺序即安全，返回非 null 即被挡下：
         *   状态 -> 限制 -> 密码
         * 每一道都必须排在密码比对之前，理由分别写在各自的方法上。
         */
        MemberAuthResult statusProblem = checkStatus(member, cmd);
        if (statusProblem != null) {
            return statusProblem;
        }
        MemberAuthResult limited = checkOperationLimit(member, cmd);
        if (limited != null) {
            return limited;
        }
        MemberAuthResult credentialProblem = verifyCredential(loginType, identity, member, cmd);
        if (credentialProblem != null) {
            return credentialProblem;
        }

        // ---------- 设备观察档：二次验证 ----------
        // 🔴 排在密码校验【之后】：排在之前的话，任何人拿一个手机号就能让我们
        //    给机主发一条短信 —— 而短信是花钱的，那就成了免费的轰炸接口。
        MemberAuthResult deviceChallenge = checkDeviceChallenge(loginType, identity, member, cmd);
        if (deviceChallenge != null) {
            return deviceChallenge;
        }

        operationLimitService.clearFail(member.getMemberId(), MemberOperationTypeEnum.LOGIN);

        // 一机多号只能在这里判 —— 在此之前拿不到 memberId。
        // 关联关系无论放不放行都要记下：dry-run 期间要的正是这份数据
        DeviceGuardVerdict fanout = deviceGuard.checkMemberFanout(cmd.deviceId(), member.getMemberId());
        if (!fanout.allowed()) {
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "同一设备关联账号过多");
            return MemberAuthResult.deviceLimited(fanout.retryAfterSeconds());
        }

        saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_SUCCESS, null);
        return MemberAuthResult.ok(toIdentity(member));
    }

    /**
     * 观察档设备的二次验证。正常设备返回 null（什么都不做）。
     *
     * <h3>为什么是「多验一道」而不是「直接拒」</h3>
     * 方案里那句「优先降级，不优先拒绝」落在这里。误伤的代价不对称：
     * 拦错一个正常用户，他不会来报障，只会不再打开；而多要一道验证码，
     * 正常用户只是多花十秒，刷子却要为<b>每一台设备</b>付出一条短信的成本。
     *
     * <h3>用哪条通道，跟着登录身份走</h3>
     * 用 switch 表达式：新增登录方式时<b>编译不过</b>，
     * 而不是悄悄落进某个兜底分支，让观察档对那条新通道形同虚设。
     */
    private MemberAuthResult checkDeviceChallenge(MemberLoginType loginType, String identity,
                                                  Member member, MemberAuthCmd cmd) {
        if (dispositionService.currentStatus(cmd.deviceId()) != DeviceStatusEnum.OBSERVE) {
            return null;
        }
        /*
         * 🔴 邮箱验证码登录【本来就是一道验证码】，不再要第二道。
         * 再要一道的话，用户会在同一个邮箱里收到两封信，
         * 而第二封证明不了第一封证明不了的任何事情。
         */
        if (loginType == MemberLoginType.EMAIL_CODE) {
            return null;
        }
        if (SolvelaStringUtil.isBlank(cmd.verificationCode())) {
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "设备观察档：需要二次验证");
            return MemberAuthResult.fail(AuthFailReason.DEVICE_VERIFICATION_REQUIRED);
        }
        // 用哪条通道跟着登录身份走。switch 表达式：新增登录方式时【编译不过】，
        // 而不是悄悄落进兜底分支，让观察档对那条新通道形同虚设
        boolean passed = switch (loginType) {
            // 手机号+密码：发一条短信到本人号码
            case PHONE_PASSWORD ->
                    smsCodeService.verify(SmsScene.LOGIN, identity, cmd.verificationCode())
                            == SmsCodeVerifyResult.OK;
            // 邮箱+密码：邮箱一定有（就是用它登的），发到那个邮箱
            case EMAIL_PASSWORD ->
                    emailCodeService.verify(EmailCodeScene.LOGIN, identity, cmd.verificationCode())
                            == EmailCodeVerifyResult.OK;
            case EMAIL_CODE -> throw new IllegalStateException("不可能走到：EMAIL_CODE 已在上面返回");
        };
        if (passed) {
            return null;
        }
        saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "设备观察档：二次验证码不正确");
        return MemberAuthResult.fail(AuthFailReason.DEVICE_VERIFICATION_FAILED);
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
     * 连续失败限制。<b>同样必须排在验密码之前</b> —— 放到后面的话，被限期间每一次尝试
     * 仍然会走一遍密码比对，限制就只剩一句提示语，拦不住任何东西。
     *
     * <p>会员端自成一套（{@code t_member_operation_limit} + Redis 计数），不走员工端的
     * 三级等保：那套锁的是账号，而会员的手机号是可猜、可泄露的 ——
     * 等于给了别人一个把你挡在门外的开关。
     */
    private MemberAuthResult checkOperationLimit(Member member, MemberAuthCmd cmd) {
        MemberOperationLimit activeLimit =
                operationLimitService.getActiveLimit(member.getMemberId(), MemberOperationTypeEnum.LOGIN);
        if (activeLimit == null) {
            return null;
        }
        saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "登录已被限制");
        return MemberAuthResult.limited(remainingSeconds(activeLimit));
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
    private MemberAuthResult verifyCredential(MemberLoginType loginType, String identity,
                                              Member member, MemberAuthCmd cmd) {
        return switch (loginType) {
            case PHONE_PASSWORD, EMAIL_PASSWORD -> verifyPassword(member, cmd);
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
        // 验证码错也算一次登录失败：账号锁挡「这个号被爆破」，设备计数挡
        // 「这台机器在挨个试不同的号」，两者都该看到这一次
        operationLimitService.recordFail(member.getMemberId(), MemberOperationTypeEnum.LOGIN, "邮箱验证码错误");
        deviceGuard.recordLoginFailure(cmd.deviceId());
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
    private MemberAuthResult verifyPassword(Member member, MemberAuthCmd cmd) {
        if (SolvelaStringUtil.isEmpty(member.getPassword())) {
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "未设置登录密码");
            return MemberAuthResult.fail(AuthFailReason.NO_PASSWORD);
        }
        if (PasswordCipher.matches(cmd.credential(), member.getPassword())) {
            return null;
        }
        MemberOperationLimit triggered = operationLimitService.recordFail(
                member.getMemberId(), MemberOperationTypeEnum.LOGIN, "连续登录失败");
        // 设备维度也记一次。两者互不替代：账号锁挡的是「这个号被爆破」，
        // 设备计数挡的是「这台机器在挨个试不同的号」—— 后者账号锁完全看不见
        deviceGuard.recordLoginFailure(cmd.deviceId());
        saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "手机号或密码错误");
        // triggered 非空表示这一次失败刚好把人限制住了，直接返回「还要等多久」，
        // 而不是让他再点一次才发现被限 —— 后者是投诉的主要来源
        return triggered != null
                ? MemberAuthResult.limited(remainingSeconds(triggered))
                : MemberAuthResult.fail(AuthFailReason.BAD_CREDENTIALS);
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

    /** 限制剩余秒数。已过期时按 0 算，不返回负数 —— 负数会被调用方格式化成「请 -1 分钟后重试」。 */
    private static long remainingSeconds(MemberOperationLimit limit) {
        return Math.max(0L, Duration.between(LocalDateTime.now(), limit.getExpireTime()).toSeconds());
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
