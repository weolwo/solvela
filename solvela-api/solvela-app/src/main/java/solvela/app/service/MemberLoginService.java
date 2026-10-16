package solvela.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.app.auth.CurrentDevice;
import solvela.app.auth.MemberPrincipal;
import solvela.app.auth.MemberPrincipalLoader;
import solvela.auth.member.MemberAccessToken;
import solvela.auth.member.MemberSession;
import solvela.auth.member.MemberSessionContext;
import solvela.auth.member.MemberTokenStore;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.EmailBindRequest;
import solvela.app.domain.EmailCodeRequest;
import solvela.app.domain.SmsCodeRequest;
import solvela.member.api.SmsCodeSendCmd;
import solvela.member.api.SmsScene;
import solvela.member.api.MemberStepUpApi;
import solvela.member.api.LoginChallengeCmd;
import solvela.member.api.LoginChallengeCodeResult;
import solvela.member.api.StepUpCmd;
import solvela.member.api.SmsCodeSendResult;
import solvela.app.domain.MemberLoginRequest;
import solvela.app.domain.MemberRegisterRequest;
import solvela.app.domain.MemberResult;
import solvela.app.domain.PasswordResetRequest;
import solvela.app.domain.PhoneBindRequest;
import solvela.member.api.MemberPhoneBindCmd;
import solvela.member.api.MemberPhoneBindResult;
import solvela.app.domain.PasswordResetView;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.EmailCodeSendResult;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.MemberEmailBindCmd;
import solvela.member.api.MemberPasswordResetCmd;
import solvela.member.api.MemberPasswordResetResult;
import solvela.member.api.MemberEmailBindResult;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberContactView;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberLoginType;
import solvela.member.api.SecurityPromptAnswerCmd;
import solvela.member.api.SecurityPromptView;
import solvela.member.api.SessionReportCmd;
import solvela.member.api.MemberLogoutCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberPasswordPolicy;

/**
 * 会员登录与退出的<b>接入层</b>：会话 + 措辞，没有别的。
 *
 * <p>「这个人是不是他声称的那个会员」由 {@link MemberAuthApi} 回答 ——
 * 那段逻辑在会员域里，短信验证码登录、第三方登录、内部工具都能复用同一份。
 * 本类只做三件网关该做的事：<b>签发/吊销令牌</b>、<b>把失败原因翻译成 HTTP 契约</b>、
 * <b>组装返回给客户端的形状</b>。
 *
 * <h3>失败一律抛 {@link ApiException}，不返回信封</h3>
 * 成功 = 2xx + 数据；失败 = 4xx/5xx + {@code ApiErrorResponse}。
 * 漏处理会一路冒到 {@code ApiExceptionHandler}，不会被静默吞掉。
 *
 * <h3>什么时候说真话，什么时候含糊其辞 —— 这几句措辞是反复权衡过的</h3>
 * 域只给一个 {@code AuthFailReason}，说多少是<b>这一层</b>的决定，改之前先读完：
 * <ul>
 *   <li>手机号<b>格式</b>不对 → 明说。一个非法的串本来就不可能是任何人的手机号，不泄露任何信息；</li>
 *   <li>查无此人 / 已注销 / 密码错 → 域已经合并成同一个原因，这里统一含糊成「手机号或密码错误」。
 *       分开说，等于免费送出一个「这个号注册过没有」的查询接口；</li>
 *   <li>账号被冻结 → <b>如实告知</b>。能走到这一步说明对方已经证明自己知道这个号是注册过的，
 *       再藏着只会让被误伤的用户一直重试然后打客服电话；</li>
 *   <li>没设过密码 → 明说并引导去验证码登录。这类账号确实存在
 *       （{@code t_member.password} 允许为空正是为验证码注册留的），
 *       含糊其辞会让用户以为自己记错了密码，一直重试直到被限制。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberLoginService {

    /**
     * 凭据错的措辞，<b>按登录方式分</b>。
     *
     * <p>🔴 按<b>登录方式</b>分，不按账号分 —— 用户输的是邮箱，却被告知「手机号或密码错误」，
     * 他会去找一个自己根本没输入过的东西。而这个区分不泄露任何信息：
     * 说的是「你刚才用什么登录的」，那是他自己选的。
     */
    private static final String BAD_CREDENTIALS_MSG = "手机号或密码错误";

    private static final String BAD_CREDENTIALS_EMAIL_MSG = "邮箱或密码错误";

    private static final String BAD_CREDENTIALS_EMAIL_CODE_MSG = "邮箱或验证码错误";

    /** 认不出登录方式时（凭票那条路）的兜底：两边都不提，免得指错方向 */
    private static final String BAD_CREDENTIALS_FALLBACK_MSG = "账号或密码错误";

    private static final String LOCKED_MSG = "连续登录失败次数过多，请 %d 分钟后重试；需要提前恢复请联系客服";

    private static final String REGISTER_LIMITED_MSG = "注册过于频繁，请 %d 分钟后重试";

    /**
     * 设备维度被限的措辞。<b>刻意不说「你的账号」</b> —— 被限的是这台设备，
     * 换个账号登录仍然会被限，说成账号问题只会让用户去做无效的事（找回密码）。
     */
    private static final String DEVICE_LIMITED_MSG = "当前设备操作过于频繁，请 %d 分钟后重试";

    /**
     * IP 维度被限。同样<b>不说「你的账号」</b>，而说「当前网络」—— 被限的是这个出口，
     * 公司、校园网里的真人被误伤时，这句话能让他明白换个网络（比如切到流量）就好，而不是去找回密码。
     */
    private static final String IP_LIMITED_MSG = "当前网络登录尝试过于频繁，请 %d 分钟后重试";

    /** 不传 deviceType 时的兜底，与 MemberAuthCmd 的约定一致 */
    private static final String DEFAULT_DEVICE_TYPE = "H5";

    private final MemberAuthApi memberAuthApi;
    private final MemberPrincipalLoader principalLoader;
    private final MemberTokenStore tokenStore;
    private final MemberStepUpApi stepUpApi;

    /** 「刚才是你吗」。单独一个契约，理由见 {@link solvela.member.api.MemberSecurityApi} */
    private final solvela.member.api.MemberSecurityApi securityApi;

    /**
     * 注册并<b>直接登录</b>。
     *
     * <p>不让用户注册完再登一次：那一步不产生任何信息，只多一次可能失败的调用。
     * 域返回 {@code MemberIdentity} 正是为了让这一层能直接签令牌。
     *
     * <h3>register_source 由这一层推导，不收客户端的</h3>
     * 它是运营分析渠道、风控识别批量注册的依据，可被任意伪造就没有价值。
     * 按 deviceType 推导：客户端仍能撒谎说自己是 APP，但至少只能在这几个已知取值里撒。
     */
    public MemberResult register(MemberRegisterRequest request, String ip) {
        String deviceType = request.deviceType() == null || request.deviceType().isBlank()
                ? DEFAULT_DEVICE_TYPE
                : request.deviceType();

        MemberRegisterResult result = memberAuthApi.register(new MemberRegisterCmd(
                request.typeOrDefault(), request.identity(), request.emailCode(), request.smsCode(),
                request.password(),
                deviceType, ip, deviceType,
                // 灰度期间可能为 null（老客户端还没带设备令牌），域里会直接放行
                CurrentDevice.deviceIdOrNull()));
        if (!result.success()) {
            throw translateRegister(result);
        }

        MemberPrincipal principal = MemberPrincipal.of(result.identity());
        MemberAccessToken token = tokenStore.issue(principal.memberId(), sessionContext(deviceType, ip));
        return new MemberResult(token.value(), token.expiresIn().toSeconds(), principal);
    }

    /**
     * @param captchaPassed 本次请求带的滑块通行票已经被核销（见 {@code CaptchaService.consumeIfPresent}）。
     *                      「要不要滑块」由域里的风控判，网关只负责核销
     */
    public MemberResult login(MemberLoginRequest request, String ip, boolean captchaPassed) {
        MemberAuthResult result = memberAuthApi.authenticate(new MemberAuthCmd(
                request.typeOrDefault(), request.identity(), request.credential(),
                request.verificationCode(), request.deviceType(), ip, CurrentDevice.deviceIdOrNull(),
                captchaPassed));
        if (!result.success()) {
            throw translate(result, request.typeOrDefault());
        }

        MemberPrincipal principal = MemberPrincipal.of(result.identity());
        MemberAccessToken token = tokenStore.issue(principal.memberId(),
                sessionContext(request.deviceType(), ip));
        // 资料可能在上次缓存之后被后台改过，登录是重建缓存最自然的时机
        principalLoader.evict(principal.memberId());

        return new MemberResult(token.value(), token.expiresIn().toSeconds(), principal);
    }

    /**
     * 退出登录。只吊销<b>当前这一个</b>令牌，其它设备不受影响 ——
     * 「退出所有设备」是另一个动作，用户得明确选择。
     */
    /**
     * 索取邮箱验证码。
     *
     * <p>🔴 <b>返回成功不代表真的寄了一封信。</b>邮箱与场景不匹配时（拿一个没注册过的
     * 邮箱要登录验证码、拿一个已注册的邮箱要注册验证码）域会静默成功 ——
     * 如实回答等于送出一个账号枚举接口。所以这里<b>不要</b>加任何
     * 「已发送到 xxx」之外的提示，更不要把域返回的成功与否解释成「这个邮箱存不存在」。
     */
    public void sendEmailCode(EmailCodeRequest request, String ip) {
        // 🔴 二次验证码只能走 /auth/step-up/code（码寄到【已绑定】的邮箱）。
        //    本接口的邮箱由客户端填，放行这个场景等于「给任意邮箱发一封安全验证信」
        if (request.scene() == EmailCodeScene.STEP_UP) {
            throw new ApiException(ApiErrors.INVALID_ARGUMENT, "不支持的验证码用途");
        }
        EmailCodeSendResult result = memberAuthApi.sendEmailCode(new EmailCodeSendCmd(
                request.scene(), request.email(), ip,
                // BIND 场景要知道「是谁在绑」，其余三个是匿名接口
                request.scene() == EmailCodeScene.BIND ? CurrentMember.memberIdOrNull() : null));
        if (!result.success()) {
            throw translateEmailCode(result);
        }
    }

    /** 发码失败原因 → HTTP 契约。同样用 switch 表达式，新增原因时编译不过。 */
    private ApiException translateEmailCode(EmailCodeSendResult result) {
        return switch (result.reason()) {
            case BAD_EMAIL_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "邮箱格式不正确");
            case TOO_FREQUENT -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    String.format("验证码已发送，请 %d 秒后再试", Math.max(1, result.retryAfterSeconds())));
            case DAILY_LIMIT_REACHED -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    "今日验证码发送次数已用完，请明天再试");
            // 这是【我们自己】的问题，如实说「稍后再试」而不是让用户以为自己填错了
            case SEND_FAILED -> new ApiException(ApiErrors.INTERNAL, "验证码发送失败，请稍后再试");
        };
    }

    /**
     * 索取短信验证码。
     *
     * <p>与 {@link #sendEmailCode} 的一个实质差别：这里<b>没有静默成功</b>那一层，
     * 因为短信目前只有注册在用。加手机号登录 / 重置时，域里要先补上静默，
     * 这一层的措辞才继续成立。
     */
    public void sendSmsCode(SmsCodeRequest request, String ip) {
        // 🔴 短信 LOGIN 场景只用于观察档二次验证，现在那条路凭票发码（/auth/login/challenge/code）。
        //    匿名放开它的话，任何人输一个手机号就能让我们给机主发一条要钱的短信
        if (request.scene() == SmsScene.LOGIN) {
            throw new ApiException(ApiErrors.INVALID_ARGUMENT, "不支持的验证码用途");
        }
        SmsCodeSendResult result = memberAuthApi.sendSmsCode(new SmsCodeSendCmd(
                request.scene(), request.phone(), ip,
                // BIND 场景要知道「是谁在绑」，其余三个是匿名接口。判据同邮箱那条
                request.scene() == SmsScene.BIND ? CurrentMember.memberIdOrNull() : null));
        if (!result.success()) {
            throw translateSmsCode(result);
        }
    }

    /** 发码失败原因 → HTTP 契约。 */
    private ApiException translateSmsCode(SmsCodeSendResult result) {
        return switch (result.reason()) {
            case BAD_PHONE_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "手机号格式不正确");
            case TOO_FREQUENT -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    String.format("验证码已发送，请 %d 秒后再试", Math.max(1, result.retryAfterSeconds())));
            case DAILY_LIMIT_REACHED -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    "今日验证码发送次数已用完，请明天再试");
            // 这是【我们自己】的问题（没接厂商、余额不足、签名被限），
            // 如实说「稍后再试」而不是让用户以为自己填错了号码
            case SEND_FAILED -> new ApiException(ApiErrors.INTERNAL, "验证码发送失败，请稍后再试");
        };
    }

    /**
     * 绑定 / 更换邮箱。<b>需要登录</b>。
     *
     * <p>会员号从 {@code CurrentMember} 取，不收客户端传的 —— 收了等于
     * 「说自己是谁就是谁」。这条与 {@code MemberLoginService.register} 里
     * 「register_source 由这一层推导，不收客户端的」是同一条规矩。
     */
    /** 我的联系方式，域里已经打过码。 */
    public MemberContactView contact(Long memberId) {
        return memberAuthApi.getContact(memberId);
    }

    public void bindEmail(EmailBindRequest request, String ip) {
        MemberEmailBindResult result = memberAuthApi.bindEmail(new MemberEmailBindCmd(
                CurrentMember.require().memberId(),
                request.email(), request.code(),
                request.currentPassword(), request.oldEmailCode(),
                ip, CurrentDevice.deviceIdOrNull()));
        if (!result.success()) {
            throw translateBind(result);
        }
    }

    /**
     * 绑定失败原因 → HTTP 契约。
     *
     * <p>{@code REBIND_VERIFICATION_REQUIRED} 用 <b>428</b> 语义最贴切，
     * 但本仓的 {@code ApiErrors} 里没有那一档，而为一个分支新增一个错误码
     * 会让客户端多一条分支。用 401 + 独立文案：客户端按 message 弹输入框即可，
     * 而 code 仍然落在它已经在处理的那几个里。
     */
    private ApiException translateBind(MemberEmailBindResult result) {
        return switch (result.reason()) {
            case BAD_EMAIL_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "邮箱格式不正确");
            case EMAIL_CODE_EXPIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码已失效，请重新获取");
            case EMAIL_CODE_MISMATCH -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
            case EMAIL_CODE_LOCKED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误次数过多，请重新获取");
            case EMAIL_TAKEN -> new ApiException(ApiErrors.CONFLICT, "该邮箱已被其他账号绑定");
            // 风控拦下（设备碰号数、黑名单、设备封禁合成同一句，不说是哪一维）
            case TOO_MANY_ATTEMPTS -> new ApiException(ApiErrors.OPERATION_LIMITED);
            case REBIND_VERIFICATION_REQUIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS,
                    "更换邮箱需要验证身份：请输入当前密码，或获取原邮箱的验证码");
            case REBIND_VERIFICATION_FAILED -> new ApiException(ApiErrors.BAD_CREDENTIALS,
                    "身份验证未通过，请检查密码或原邮箱验证码");
        };
    }

    /**
     * 绑定 / 更换手机号。<b>需要登录</b>。
     *
     * <p>会员号从 {@code CurrentMember} 取，不收客户端传的 —— 与绑定邮箱同一条规矩。
     */
    public void bindPhone(PhoneBindRequest request, String ip) {
        MemberPhoneBindResult result = memberAuthApi.bindPhone(new MemberPhoneBindCmd(
                CurrentMember.require().memberId(),
                request.phone(), request.code(),
                request.currentPassword(), request.oldPhoneCode(),
                ip, CurrentDevice.deviceIdOrNull()));
        if (!result.success()) {
            throw translatePhoneBind(result);
        }
    }

    /** 绑定手机号失败原因 → HTTP 契约。措辞与邮箱那条对称。 */
    private ApiException translatePhoneBind(MemberPhoneBindResult result) {
        return switch (result.reason()) {
            case BAD_PHONE_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "手机号格式不正确");
            case SMS_CODE_EXPIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码已失效，请重新获取");
            case SMS_CODE_MISMATCH -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
            case SMS_CODE_LOCKED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误次数过多，请重新获取");
            // 409 而不是 400：不是「你填错了」，是「服务端已有一个冲突的东西」
            case PHONE_TAKEN -> new ApiException(ApiErrors.CONFLICT, "该手机号已被其他账号绑定");
            // 措辞与邮箱那条对称
            case TOO_MANY_ATTEMPTS -> new ApiException(ApiErrors.OPERATION_LIMITED);
            case REBIND_VERIFICATION_REQUIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS,
                    "更换手机号需要验证身份：请输入当前密码，或获取原手机号的验证码");
            case REBIND_VERIFICATION_FAILED -> new ApiException(ApiErrors.BAD_CREDENTIALS,
                    "身份验证未通过，请检查密码或原手机号验证码");
        };
    }

    /**
     * 用邮箱验证码重置密码。<b>匿名</b> —— 用户正是因为进不去才走这条路。
     *
     * <p>返回被吊销的会话数，客户端要展示出来：「已在 3 台设备上退出登录」
     * 是用户判断「刚才是不是别人在动我账号」的依据。只回一句「修改成功」，
     * 这条信息就白丢了。
     */
    public PasswordResetView resetPassword(PasswordResetRequest request, String ip) {
        MemberPasswordResetResult result = memberAuthApi.resetPassword(new MemberPasswordResetCmd(
                request.typeOrDefault(), request.identity(), request.code(), request.newPassword(),
                ip, CurrentDevice.deviceIdOrNull()));
        if (!result.success()) {
            throw translateReset(result);
        }
        return new PasswordResetView(result.revokedSessions());
    }

    /** 重置密码失败原因 → HTTP 契约。 */
    private ApiException translateReset(MemberPasswordResetResult result) {
        return switch (result.reason()) {
            case BAD_EMAIL_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "邮箱格式不正确");
            case EMAIL_CODE_EXPIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码已失效，请重新获取");
            case EMAIL_CODE_MISMATCH -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
            case EMAIL_CODE_LOCKED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误次数过多，请重新获取");
            // 措辞与邮箱那三条一致：用户看到的是「验证码」，不需要知道它从哪条通道来
            case BAD_PHONE_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "手机号格式不正确");
            case SMS_CODE_EXPIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码已失效，请重新获取");
            case SMS_CODE_MISMATCH -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
            case SMS_CODE_LOCKED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误次数过多，请重新获取");
            // 文案从域里取，不在这里再写一遍规则 —— 两份措辞迟早对不上
            case WEAK_PASSWORD -> new ApiException(ApiErrors.INVALID_ARGUMENT, MemberPasswordPolicy.HINT);
            /*
             * 新绑的联系方式还在冷静期，不能用来找回密码。
             * 🔴 这里<b>不给 availableAt</b>：找回密码是未登录接口，而这个时间点属于账号内部状态 ——
             * 任何人输一个邮箱就能问出「这个号最近换过联系方式吗」。文案也只说「暂时不可用」。
             */
            case CONTACT_COOLING -> new ApiException(ApiErrors.CONTACT_COOLING,
                    "该联系方式暂时不能用于找回密码，请在你常用的设备上操作，或联系客服");
            // 走到这一档说明码猜对了但账号不存在。含糊成「验证码错误」即可 ——
            // 对真实用户这条路不可能出现（没账号就收不到码）
            case ACCOUNT_NOT_FOUND -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
            // 🔴 四条规则（IP / 设备限频、黑名单、设备封禁）合成同一句，并带上秒数：
            //    走到这一步的人已经登不进去了，「请稍后再试」对他等于「不知道还有没有希望」
            case TOO_MANY_ATTEMPTS -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    String.format("操作过于频繁，请 %d 分钟后重试", minutes(result.retryAfterSeconds())));
            case ACCOUNT_UNAVAILABLE -> new ApiException(ApiErrors.ACCOUNT_DISABLED,
                    "账号状态异常，无法自助重置密码，请联系客服");
        };
    }

    /**
     * 签发令牌时顺手记下的展示信息，供「我的登录设备」用。
     *
     * <p>{@code region} 传 null —— 解析 IP 归属地的工具在 solvela-base-core，
     * 而网关的 classpath 上没有它（见 {@link MemberSessionContext} 的类注释）。
     */
    private static MemberSessionContext sessionContext(String deviceType, String ip) {
        return new MemberSessionContext(
                deviceType == null || deviceType.isBlank() ? DEFAULT_DEVICE_TYPE : deviceType,
                CurrentDevice.deviceIdOrNull(), ip, null);
    }

    /** 这个会员当前活着的登录会话。 */
    public java.util.List<MemberSession> listSessions(Long memberId, String currentToken) {
        return tokenStore.listSessions(memberId, currentToken);
    }

    /**
     * 让某一个会话下线。
     *
     * <p>🔴 memberId 从令牌解析而来，<b>不收客户端传的</b> —— 收了就等于
     * 「说自己是谁就是谁」，任何人都能把别人的会话踢掉。
     */
    public boolean revokeSession(Long memberId, String sessionId) {
        return tokenStore.revokeSession(memberId, sessionId);
    }

    /**
     * 登录二次验证：凭票发码。返回码寄到了哪（打过码的）。
     *
     * <p>码发到哪由票决定（登录用的那个邮箱 / 手机号），客户端改不了 ——
     * 这正是它取代「客户端自己去匿名接口要码」的理由。
     */
    public String sendChallengeCode(String ticket, String ip) {
        LoginChallengeCodeResult result = memberAuthApi.sendChallengeCode(
                new LoginChallengeCmd(ticket, null, ip, CurrentDevice.deviceIdOrNull()));
        if (result.success()) {
            return result.maskedTarget();
        }
        throw switch (result.reason()) {
            case CHALLENGE_EXPIRED -> new ApiException(ApiErrors.CHALLENGE_EXPIRED);
            case TOO_FREQUENT -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    String.format("验证码已发送，请 %d 秒后再试", Math.max(1, result.retryAfterSeconds())));
            case DAILY_LIMIT_REACHED -> new ApiException(ApiErrors.OPERATION_LIMITED, "今日验证码发送次数已用完，请明天再试");
            case SEND_FAILED -> new ApiException(ApiErrors.INTERNAL, "验证码发送失败，请稍后再试");
        };
    }

    /**
     * 登录二次验证：凭票验码。通过即签发会话 —— 形状与 {@link #login} 完全一致，
     * 客户端走同一条「登录成功」的路。
     */
    public MemberResult verifyChallenge(String ticket, String code, String ip) {
        MemberAuthResult result = memberAuthApi.verifyChallenge(
                new LoginChallengeCmd(ticket, code, ip, CurrentDevice.deviceIdOrNull()));
        if (!result.success()) {
            throw translate(result);
        }
        MemberPrincipal principal = MemberPrincipal.of(result.identity());
        String deviceType = CurrentDevice.find().map(solvela.auth.device.DeviceIdentity::deviceType)
                .orElse(DEFAULT_DEVICE_TYPE);
        MemberAccessToken token = tokenStore.issue(principal.memberId(), sessionContext(deviceType, ip));
        principalLoader.evict(principal.memberId());
        return new MemberResult(token.value(), token.expiresIn().toSeconds(), principal);
    }

    /**
     * 下线除当前之外的所有会话，并撤销其余设备的信任。
     *
     * <p>只踢会话不撤信任的话，被踢下去的那台设备（很可能正是用户怀疑的那台）
     * 用密码重新登进来，仍然是「受信任设备」，加地址、充话费不用再验证。
     *
     * <p>先踢会话再撤信任：后一步是跨进程调用，失败时前一步已经生效 ——
     * 用户最急的那件事（别人立刻掉线）不该被它拖住。
     */
    public int revokeOtherSessions(Long memberId, String currentToken, String ip) {
        int revoked = tokenStore.revokeOthers(memberId, currentToken);
        stepUpApi.revokeOthers(StepUpCmd.of(memberId, CurrentDevice.deviceIdOrNull(), ip));
        return revoked;
    }

    /**
     * 「这不是我」：举报某一个会话。
     *
     * <h3>顺序：先踢人，再冻结</h3>
     * 🔴 两件事都要做，但<b>踢人在前</b> —— 用户点下去的那一刻最在意的是「他现在还在不在」。
     * 冻结要跨进程，万一下游抖了一下，至少对方已经掉线了。
     *
     * <h3>为什么顺手把【其余所有】会话也踢掉</h3>
     * 举报一个会话说明账号已经漏了。只踢被举报那一个，攻击者另开的第二个会话还在
     * —— 而用户以为自己已经把人赶出去了（方案 §8.1）。
     *
     * <p>⚠️ 当前这一台不踢：用户正拿着它操作，踢了他要重新登录，
     * 而「我刚举报就被踢出去了」看起来像是我们出了故障。
     *
     * @param loginEpochSecond 被举报那次登录的时间（秒），用来给决策流水打标；0 = 未知
     * @return 当前这台设备是否仍受信任
     */
    public boolean reportSession(Long memberId, String currentToken, String sessionId,
                                 String reportedDeviceId, long loginEpochSecond, String ip) {
        tokenStore.revokeSession(memberId, sessionId);
        int revoked = tokenStore.revokeOthers(memberId, currentToken);
        log.warn("【账号安全】「这不是我」, memberId: {}, 被举报会话: {}, 另外踢掉 {} 个会话",
                memberId, sessionId, revoked);
        return stepUpApi.reportNotMe(new SessionReportCmd(memberId, reportedDeviceId,
                loginEpochSecond, CurrentDevice.deviceIdOrNull(), ip));
    }

    /** 待确认的「刚才是你吗」。设备号从令牌解析，客户端说了不算 */
    public java.util.List<SecurityPromptView> listSecurityPrompts(Long memberId, String ip) {
        return securityApi.list(StepUpCmd.of(memberId, CurrentDevice.deviceIdOrNull(), ip));
    }

    /**
     * 回答一条。
     *
     * <p>🔴 「不是我」的后果与 {@link #reportSession} <b>完全一致</b>（域里走同一个方法），
     * 但这里还要多做一件网关才能做的事：把其余会话踢掉 —— 令牌库只在网关。
     */
    public void answerSecurityPrompt(Long memberId, String promptId, boolean isMe, String ip) {
        boolean accepted = securityApi.answer(new SecurityPromptAnswerCmd(
                memberId, promptId, isMe, CurrentDevice.deviceIdOrNull(), ip));
        if (accepted && !isMe) {
            // 当前这一台不踢：用户正拿着它回答，踢了他要重新登录
            int revoked = tokenStore.revokeOthers(memberId, null);
            log.warn("【账号安全】老设备上答「不是我」, memberId: {}, 踢掉 {} 个会话", memberId, revoked);
        }
    }

    public void logout(String tokenValue, Long memberId, String ip) {
        tokenStore.revoke(tokenValue);
        memberAuthApi.recordLogout(new MemberLogoutCmd(memberId, ip, CurrentDevice.deviceIdOrNull()));
    }

    /**
     * 失败原因 → HTTP 契约。
     *
     * <p>用 switch 表达式而不是 if 链：新增一个 {@code AuthFailReason} 时<b>编译不过</b>，
     * 而不是悄悄落进某个兜底分支返回「服务开小差了」。
     */
    private ApiException translate(MemberAuthResult result) {
        return translate(result, null);
    }

    /**
     * @param loginType 这一次用的登录方式；凭票那条路上认不出来，传 null。
     *                  只影响措辞和「还有哪些出路」，不影响码
     */
    private ApiException translate(MemberAuthResult result, MemberLoginType loginType) {
        return switch (result.reason()) {
            case BAD_PHONE_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "手机号格式不正确");
            case BAD_CREDENTIALS -> new ApiException(ApiErrors.BAD_CREDENTIALS,
                    badCredentialsMessage(loginType), lastChanceDetails(result));
            case ACCOUNT_FROZEN -> new ApiException(ApiErrors.ACCOUNT_DISABLED, "账号已被冻结，请联系客服");
            case NO_PASSWORD -> new ApiException(ApiErrors.BAD_CREDENTIALS, "该账号未设置密码，请使用短信验证码登录");
            case OPERATION_LIMITED -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    lockedMessage(result.lockedSeconds()), retryAfter(result.lockedSeconds()));
            /*
             * 密码冷却：唯一一个【用户此刻就有出路】的限制。
             * 所以除了倒计时，还要告诉客户端有哪几条出路 —— 它据此决定弹窗上摆哪几颗按钮。
             *
             * 🔴 details 里放的是 CODE_LOGIN / PASSWORD_RESET 这种【事实】，不是「弹什么窗」：
             *    App 和 H5 的页面结构不同，下发 UI 的话一个码就得为每个端写一份（方案 6.4 约定 2）。
             */
            case PASSWORD_COOLDOWN -> new ApiException(ApiErrors.PASSWORD_COOLDOWN,
                    ApiErrors.PASSWORD_COOLDOWN.defaultMessage(),
                    cooldownDetails(result.lockedSeconds(), loginType));
            /*
             * 要核身却没有通道。🔴 文案与 details 都【不提绑定】，理由见 ApiErrors.LOGIN_VERIFY_UNAVAILABLE：
             * 此刻对面只证明了「知道密码」，引导他去绑联系方式就是把账号接管铺成主路。
             */
            case LOGIN_VERIFY_UNAVAILABLE -> new ApiException(ApiErrors.LOGIN_VERIFY_UNAVAILABLE);
            // 邮箱格式：与手机号格式同一个判据，明说不泄露任何信息
            case BAD_EMAIL_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "邮箱格式不正确");
            // 验证码三态说得具体。它们说的是【码】不是【账号】，
            // 而且没有账号的邮箱也存了码（MailDelivery.SUPPRESS），所以不构成账号枚举
            case EMAIL_CODE_EXPIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码已失效，请重新获取");
            case EMAIL_CODE_MISMATCH -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
            case EMAIL_CODE_LOCKED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误次数过多，请重新获取");
            // 同为 429，但措辞完全不同：账号被限说「连续登录失败」，设备被限说「当前设备」——
            // 合并文案会让被设备维度限住的用户一直去找回密码，而那解决不了他的问题
            case DEVICE_LIMITED -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    String.format(DEVICE_LIMITED_MSG, minutes(result.lockedSeconds())),
                    retryAfter(result.lockedSeconds()));
            case IP_LIMITED -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    String.format(IP_LIMITED_MSG, minutes(result.lockedSeconds())),
                    retryAfter(result.lockedSeconds()));
            /*
             * 观察档的二次验证。两条分开，因为客户端要据此决定
             * 【把验证码框亮出来】还是【报错并让他重新获取】。
             *
             * 🔴 措辞都不提「你的设备被标记了」—— 那句话对真实用户毫无意义
             * （他做不了任何事），只会让人以为账号出了问题去找客服。
             */
            // 凭票放进 details：客户端之后的发码、验码都凭它。票只在这一个响应里出现一次
            // 还带上 channel：客户端要说「验证码已发到你的邮箱 / 手机」。说错了用户会去翻另一个
            // 收件箱，然后以为我们没发 —— 而换通道（手机号登录、码发邮箱）恰恰是常态
            case DEVICE_VERIFICATION_REQUIRED -> new ApiException(ApiErrors.DEVICE_VERIFICATION_REQUIRED,
                    ApiErrors.DEVICE_VERIFICATION_REQUIRED.defaultMessage(),
                    challengeDetails(result));
            /*
             * 风控判出这次登录要先过滑块。映射成客户端<b>早就认得</b>的 CAPTCHA_REQUIRED ——
             * 它那套「收到它 → 拖图 → 带票重试」一行都不用改。
             *
             * 🔴 不带 details、不说原因：说了「因为你换了网络」等于告诉对方该怎么绕。
             */
            case CAPTCHA_REQUIRED -> new ApiException(ApiErrors.CAPTCHA_REQUIRED);
            case CHALLENGE_EXPIRED -> new ApiException(ApiErrors.CHALLENGE_EXPIRED);
            case DEVICE_VERIFICATION_FAILED ->
                    new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误，请重新获取");
        };
    }

    /**
     * 注册失败原因 → HTTP 契约。同样用 switch 表达式，新增原因时编译不过。
     *
     * <h3>这里的措辞取舍与登录【正好相反】</h3>
     * 登录要含糊（不能让人拿登录接口枚举手机号），注册必须明说 ——
     * 用户得知道该去登录还是该换个号，含糊其辞只会让他一直点注册。
     * 这确实是一个手机号枚举口子，但它是注册这件事自带的、藏不掉，
     * 只能靠限频压速率。详见 {@code RegisterFailReason} 的类注释。
     */
    private ApiException translateRegister(MemberRegisterResult result) {
        return switch (result.reason()) {
            case BAD_PHONE_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "手机号格式不正确");
            // 409 而不是 400：这不是「你填错了」，是「服务端已有一个冲突的东西」。
            // 前端据此可以直接引导去登录页，而 400 是一堆参数问题的大杂烩
            case PHONE_TAKEN -> new ApiException(ApiErrors.CONFLICT, "该手机号已注册，请直接登录");
            // 提示文案从域里取，不在这里再写一遍规则 —— 两份措辞迟早对不上
            case WEAK_PASSWORD -> new ApiException(ApiErrors.INVALID_ARGUMENT, MemberPasswordPolicy.HINT);
            // 🔴 IP 与设备两维合成同一句：告诉对方「是设备被限」等于教他换一台
            case TOO_MANY_ATTEMPTS ->
                    new ApiException(ApiErrors.OPERATION_LIMITED, registerLimitedMessage(result.retryAfterSeconds()));
            case BAD_EMAIL_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "邮箱格式不正确");
            // 与 PHONE_TAKEN 同一个取舍：必须如实说，否则用户不知道该去登录还是换个邮箱。
            // 这条枚举口子在邮箱这边【贵得多】—— 验证码校验排在查重之前
            case EMAIL_TAKEN -> new ApiException(ApiErrors.CONFLICT, "该邮箱已注册，请直接登录");
            case EMAIL_CODE_EXPIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码已失效，请重新获取");
            case EMAIL_CODE_MISMATCH -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
            case EMAIL_CODE_LOCKED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误次数过多，请重新获取");
            // 措辞与邮箱那三条一致：用户看到的是「验证码」，不需要知道它从哪条通道来
            case SMS_CODE_EXPIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码已失效，请重新获取");
            case SMS_CODE_MISMATCH -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
            case SMS_CODE_LOCKED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误次数过多，请重新获取");
        };
    }

    private static String registerLimitedMessage(long retryAfterSeconds) {
        return String.format(REGISTER_LIMITED_MSG, minutes(retryAfterSeconds));
    }

    private static String badCredentialsMessage(MemberLoginType loginType) {
        if (loginType == null) {
            return BAD_CREDENTIALS_FALLBACK_MSG;
        }
        return switch (loginType) {
            case PHONE_PASSWORD -> BAD_CREDENTIALS_MSG;
            case EMAIL_PASSWORD -> BAD_CREDENTIALS_EMAIL_MSG;
            case EMAIL_CODE -> BAD_CREDENTIALS_EMAIL_CODE_MSG;
        };
    }

    /**
     * 「再错一次就要等了」。
     *
     * <h3>🔴 只在剩最后一次时给</h3>
     * 一直给等于把阶梯的形状完整地告诉对面，而它对正常用户的价值<b>只在最后那一下</b>：
     * 「再错 1 次就要等一分钟」能让人停下来想想，「还能错 4 次」只会让他继续猜。
     *
     * <p>它不构成账号枚举：阶梯按身份摘要计数，没有账号的身份算出同样的数，
     * 所以这个字段出现的时机对「有号 / 没号」完全一样（域那边有注释钉着）。
     */
    private static java.util.Map<String, Object> lastChanceDetails(MemberAuthResult result) {
        Integer attempts = result.attemptsBeforeCooldown();
        return attempts != null && attempts <= 1
                ? java.util.Map.of("attemptsBeforeCooldown", attempts)
                : null;
    }

    private static java.util.Map<String, Object> retryAfter(long seconds) {
        return java.util.Map.of("retryAfterSeconds", Math.max(1L, seconds));
    }

    private static java.util.Map<String, Object> challengeDetails(MemberAuthResult result) {
        return result.verifyChannel() == null
                ? java.util.Map.of("challengeTicket", result.challengeTicket())
                : java.util.Map.of("challengeTicket", result.challengeTicket(),
                        "channel", result.verifyChannel().name());
    }

    /**
     * 密码冷却时还有哪些出路。
     *
     * <h3>只取决于登录方式，不取决于账号</h3>
     * 按「这个号绑了什么」给的话，这个字段就成了一个账号画像接口 ——
     * 而冷却这一档对不存在的账号也会出现（按身份摘要计数）。
     *
     * <p>🔴 手机号登录今天拿到的是<b>空数组</b>：验证码登录只有邮箱那条，
     * 而手机号找回密码同样要短信 —— 短信厂商还没接，两条都走不通。
     * 硬塞一个 {@code PASSWORD_RESET} 进去，用户点过去会撞上一个发不出码的页面，
     * 那比没有按钮更糟。接上短信的那天把它加回来。
     */
    private static java.util.Map<String, Object> cooldownDetails(long lockedSeconds, MemberLoginType loginType) {
        java.util.List<String> alternatives = loginType == MemberLoginType.PHONE_PASSWORD || loginType == null
                ? java.util.List.of()
                : java.util.List.of("CODE_LOGIN", "PASSWORD_RESET");
        return java.util.Map.of("retryAfterSeconds", Math.max(1L, lockedSeconds),
                "alternatives", alternatives);
    }

    /**
     * 秒 → 分钟，向上取整且至少 1。
     *
     * <p>抽出来是因为现在有四处在用同一个换算 —— 散着写迟早有一处忘了取整，
     * 表现是「请 0 分钟后重试」，比不说还糟。
     */
    private static long minutes(long seconds) {
        return Math.max(1, (long) Math.ceil(seconds / 60.0));
    }

    /**
     * 把限制剩余时间拼成人话。
     *
     * <p>向上取整到分钟：剩 10 秒时说「请 0 分钟后重试」比不说还糟。
     */
    private static String lockedMessage(long lockedSeconds) {
        return String.format(LOCKED_MSG, minutes(lockedSeconds));
    }
}
