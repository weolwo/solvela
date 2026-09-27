package solvela.member.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 会员认证契约。
 *
 * <h3>同一个接口，两种形态</h3>
 * <ul>
 *   <li><b>今天</b>：{@code solvela.member.auth.MemberAuthService} 实现它，网关直接注入，
 *       同 JVM 一次方法调用，{@code @HttpExchange} 这些注解运行期完全不起作用；</li>
 *   <li><b>拆成 app-member 服务后</b>：服务端套一个 {@code @RestController implements MemberAuthApi}
 *       的薄壳（Spring MVC 认得接口上的 {@code @HttpExchange}），网关侧把注入源换成
 *       {@code HttpServiceProxyFactory} 生成的代理 —— <b>调用方代码一行不改</b>。</li>
 * </ul>
 * 这就是本模块存在的全部理由。
 *
 * <h3>路径前缀 /internal 是有意的</h3>
 * 这些端点服务于服务间调用，<b>永远不该暴露到公网</b>：{@code verify} 收的是明文密码，
 * {@code identity} 用会员号直接换身份。将来网关/入口层要按前缀把 {@code /internal/**} 挡在外面。
 *
 * <h3>失败不抛异常</h3>
 * 认证失败是最常见的正常情况，用 {@link MemberAuthResult} 的 reason 表达。
 * 抛异常留给意外（库挂了、代码 bug）—— 那些跨进程后就是 5xx，本来也该是 5xx。
 */
@HttpExchange("/internal/member/auth")
public interface MemberAuthApi {

    /**
     * 手机号 + 密码注册。<b>只建会员，不发令牌</b> —— 与 {@link #authenticate} 同一个理由。
     *
     * <p>成功返回 {@link MemberIdentity}，网关据此直接签令牌让用户进去，不必再走一次登录。
     *
     * <p>放在<b>认证契约</b>里而不是另建一个 {@code MemberRegisterApi}：多一个接口就多一个
     * 「服务端薄壳建了没有」的失误面 —— 那个坑踩过一次（{@code MemberProposalApi} 的壳漏了，
     * 所有进程内测试都发现不了，一直到第一次真实发奖才炸）。挂在已有壳上，编译器替你记着。
     *
     * <p>实现委托给 {@code MemberRegisterService}：认证是读、注册是写，事务语义不同，
     * 不该塞进同一个类。
     */
    @PostExchange("/register")
    MemberRegisterResult register(@RequestBody MemberRegisterCmd cmd);

    /**
     * 手机号 + 密码认证。<b>只验身份，不发令牌。</b>
     *
     * <p>成功返回带 {@link MemberIdentity} 的结果；失败带 {@link AuthFailReason}，
     * 「告诉用户多少」由调用方决定（同一个 BAD_CREDENTIALS，C 端要含糊、客服后台要具体）。
     */
    @PostExchange("/verify")
    MemberAuthResult authenticate(@RequestBody MemberAuthCmd cmd);

    /**
     * 登录二次验证：凭票发码。码发到<b>登录用的那个身份</b>上（邮箱登录发邮件，手机号登录发短信），
     * 收码地址由票决定，客户端改不了。
     *
     * <p>🔴 不知道密码就拿不到票，拿不到票就发不了码 —— 这正是它取代「匿名发码」的理由。
     */
    @PostExchange("/challenge/code")
    LoginChallengeCodeResult sendChallengeCode(@RequestBody LoginChallengeCmd cmd);

    /**
     * 登录二次验证：凭票验码。通过即完成登录（返回身份），票随即作废。
     *
     * <p>码错时返回 {@link AuthFailReason#DEVICE_VERIFICATION_FAILED}，票还能再试；
     * 码被错到作废、票过期、换了设备，一律 {@link AuthFailReason#CHALLENGE_EXPIRED}，要重新登录。
     */
    @PostExchange("/challenge/verify")
    MemberAuthResult verifyChallenge(@RequestBody LoginChallengeCmd cmd);

    /**
     * 发一封邮箱验证码。
     *
     * <p>挂在<b>认证契约</b>上而不是另建一个 Api，理由同 {@link #register} 的注释：
     * 多一个接口就多一个「服务端薄壳建了没有」的失误面，而那个坑踩过一次。
     *
     * <p>🔴 <b>返回成功不代表真的寄了一封信。</b>邮箱与场景不匹配时（比如拿一个
     * 没注册过的邮箱要登录验证码）会静默成功 —— 如实回答等于送出一个账号枚举接口。
     * 详见 {@code MemberEmailCodeIssuer} 的类注释。
     */
    @PostExchange("/email-code")
    EmailCodeSendResult sendEmailCode(@RequestBody EmailCodeSendCmd cmd);

    /**
     * 发一条短信验证码。
     *
     * <p>⚠️ <b>短信是要花钱的</b>，这是它与邮箱最实质的区别：邮件被刷只是难看，
     * 短信被刷是账单。所以 IP 日限比邮箱紧得多（见 {@code VerificationCodeProperties}），
     * 而且这条路由和 {@code /register} 一样<b>不需要任何身份</b> ——
     * {@code /internal/**} 必须整体挡在公网之外。
     *
     * <p>🔴 与 {@link #sendEmailCode} 不同，这里<b>没有静默成功那一档</b>：
     * 短信目前只有注册在用，而注册的「这个号已被占用」本来就藏不掉。
     * 等手机号登录 / 重置上线，得照邮箱那边补上，否则这个接口会变成账号枚举器。
     */
    @PostExchange("/sms-code")
    SmsCodeSendResult sendSmsCode(@RequestBody SmsCodeSendCmd cmd);

    /**
     * 绑定 / 更换邮箱。
     *
     * <p>放在<b>认证契约</b>里而不是某个「会员资料」契约：绑定邮箱不是改昵称，
     * 它<b>新增了一条登录身份</b> —— 绑完之后这个邮箱就能用来登录、能用来重置密码。
     * 那是认证的事。
     *
     * <p>🔴 {@code memberId} 由网关从令牌解析后填入，<b>不接受客户端传</b>。
     * 收客户端的 memberId 等于「说自己是谁就是谁」。
     */
    @PostExchange("/email/bind")
    MemberEmailBindResult bindEmail(@RequestBody MemberEmailBindCmd cmd);

    /**
     * 绑定 / 更换手机号。
     *
     * <p>与 {@link #bindEmail} 同一个判断：手机号不是资料，是<b>登录身份</b>，
     * 而且比邮箱重一档 —— {@code uk_mbr_phone_hash} 是唯一约束，
     * 换绑意味着原来那个号从此登不了、也注册不了这个账号。
     *
     * <p>🔴 {@code memberId} 由网关从令牌解析后填入，<b>不接受客户端传</b>。
     */
    @PostExchange("/phone/bind")
    MemberPhoneBindResult bindPhone(@RequestBody MemberPhoneBindCmd cmd);

    /**
     * 当前会员的联系方式，<b>全部脱敏</b>。
     *
     * <p>刻意不合并进 {@link #getAuthIdentity}：那个结果会进网关的缓存和日志，
     * 而手机号邮箱是 PII。分开一次调用，代价是多一个往返，
     * 换的是「明文一次都不出域」。
     */
    @GetExchange("/contact/{memberId}")
    MemberContactView getContact(@PathVariable Long memberId);

    /**
     * 用邮箱验证码重置密码。<b>匿名</b> —— 用户正是因为进不去才走这条路。
     *
     * <p>⚠️ 这条链路的验证码威力最大：拿到它就能改密码，等于账号易主。
     * 成功之后域里会吊销该会员的<b>全部会话</b>。
     */
    @PostExchange("/password/reset")
    MemberPasswordResetResult resetPassword(@RequestBody MemberPasswordResetCmd cmd);

    /**
     * 按会员号取<b>可用身份</b>；会员不存在或状态不正常返回 null。
     *
     * <p>网关每个请求都会（经缓存）走一次这里，把令牌解析出的会员号还原成身份。
     * 调用方务必带缓存 —— 这是全站最热的一次调用。
     */
    @GetExchange("/identity/{memberId}")
    MemberIdentity getAuthIdentity(@PathVariable Long memberId);

    /**
     * 记一次退出登录。吊销令牌是调用方的事，这里只留痕。
     */
    @PostExchange("/logout-log")
    void recordLogout(@RequestBody MemberLogoutCmd cmd);
}
