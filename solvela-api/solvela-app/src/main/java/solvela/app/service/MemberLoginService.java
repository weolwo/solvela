package solvela.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.app.auth.MemberPrincipal;
import solvela.app.auth.MemberPrincipalLoader;
import solvela.member.session.MemberAccessToken;
import solvela.member.session.MemberTokenStore;
import solvela.app.domain.MemberLoginRequest;
import solvela.app.domain.MemberResult;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberLogoutCmd;

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

    private final MemberAuthApi memberAuthApi;
    private final MemberPrincipalLoader principalLoader;
    private final MemberTokenStore tokenStore;

    public MemberResult login(MemberLoginRequest request, String ip) {
        MemberAuthResult result = memberAuthApi.authenticate(new MemberAuthCmd(
                request.phone(), request.password(), request.deviceType(), ip));
        if (!result.success()) {
            throw translate(result);
        }

        MemberPrincipal principal = MemberPrincipal.of(result.identity());
        MemberAccessToken token = tokenStore.issue(principal.memberId());
        // 资料可能在上次缓存之后被后台改过，登录是重建缓存最自然的时机
        principalLoader.evict(principal.memberId());

        return new MemberResult(token.value(), token.expiresIn().toSeconds(), principal);
    }

    /**
     * 退出登录。只吊销<b>当前这一个</b>令牌，其它设备不受影响 ——
     * 「退出所有设备」是另一个动作，用户得明确选择。
     */
    public void logout(String tokenValue, Long memberId, String ip) {
        tokenStore.revoke(tokenValue);
        memberAuthApi.recordLogout(new MemberLogoutCmd(memberId, ip));
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
        };
    }

    /**
     * 把限制剩余时间拼成人话。
     *
     * <p>向上取整到分钟：剩 10 秒时说「请 0 分钟后重试」比不说还糟。
     */
    private static String lockedMessage(long lockedSeconds) {
        return String.format(LOCKED_MSG, Math.max(1, (long) Math.ceil(lockedSeconds / 60.0)));
    }
}
