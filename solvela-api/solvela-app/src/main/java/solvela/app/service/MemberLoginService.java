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

    private static final String BAD_CREDENTIALS_MSG = "手机号或密码错误";

    private static final String LOCKED_MSG = "连续登录失败次数过多，请 %d 分钟后重试；需要提前恢复请联系客服";

    private static final String REGISTER_LIMITED_MSG = "注册过于频繁，请 %d 分钟后重试";

    /**
     * 设备维度被限的措辞。<b>刻意不说「你的账号」</b> —— 被限的是这台设备，
     * 换个账号登录仍然会被限，说成账号问题只会让用户去做无效的事（找回密码）。
     */
    private static final String DEVICE_LIMITED_MSG = "当前设备操作过于频繁，请 %d 分钟后重试";

    /** 不传 deviceType 时的兜底，与 MemberAuthCmd 的约定一致 */
    private static final String DEFAULT_DEVICE_TYPE = "H5";

    private final MemberAuthApi memberAuthApi;
    private final MemberPrincipalLoader principalLoader;
    private final MemberTokenStore tokenStore;

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

    public MemberResult login(MemberLoginRequest request, String ip) {
        MemberAuthResult result = memberAuthApi.authenticate(new MemberAuthCmd(
                request.typeOrDefault(), request.identity(), request.credential(),
                request.verificationCode(), request.deviceType(), ip, CurrentDevice.deviceIdOrNull()));
        if (!result.success()) {
            throw translate(result);
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
            // 走到这一档说明码猜对了但账号不存在。含糊成「验证码错误」即可 ——
            // 对真实用户这条路不可能出现（没账号就收不到码）
            case ACCOUNT_NOT_FOUND -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
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

    /** 下线除当前之外的所有会话。 */
    public int revokeOtherSessions(Long memberId, String currentToken) {
        return tokenStore.revokeOthers(memberId, currentToken);
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
        return switch (result.reason()) {
            case BAD_PHONE_FORMAT -> new ApiException(ApiErrors.INVALID_ARGUMENT, "手机号格式不正确");
            case BAD_CREDENTIALS -> new ApiException(ApiErrors.BAD_CREDENTIALS, BAD_CREDENTIALS_MSG);
            case ACCOUNT_FROZEN -> new ApiException(ApiErrors.ACCOUNT_DISABLED, "账号已被冻结，请联系客服");
            case NO_PASSWORD -> new ApiException(ApiErrors.BAD_CREDENTIALS, "该账号未设置密码，请使用短信验证码登录");
            case OPERATION_LIMITED -> new ApiException(ApiErrors.OPERATION_LIMITED, lockedMessage(result.lockedSeconds()));
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
                    String.format(DEVICE_LIMITED_MSG, minutes(result.lockedSeconds())));
            /*
             * 观察档的二次验证。两条分开，因为客户端要据此决定
             * 【把验证码框亮出来】还是【报错并让他重新获取】。
             *
             * 🔴 措辞都不提「你的设备被标记了」—— 那句话对真实用户毫无意义
             * （他做不了任何事），只会让人以为账号出了问题去找客服。
             */
            case DEVICE_VERIFICATION_REQUIRED -> new ApiException(ApiErrors.DEVICE_VERIFICATION_REQUIRED);
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
            case TOO_MANY_ATTEMPTS ->
                    new ApiException(ApiErrors.OPERATION_LIMITED, registerLimitedMessage(result.retryAfterSeconds()));
            case DEVICE_LIMITED -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    String.format(DEVICE_LIMITED_MSG, minutes(result.retryAfterSeconds())));
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
