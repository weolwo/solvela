package solvela.member.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 敏感操作二次验证（step-up）契约。
 *
 * <h3>它守的是「资产出口」，不是登录</h3>
 * 本站盗号 / 买号的变现路径是：<b>加一个自己的收货地址再兑换</b>，或<b>充话费到自己的手机号</b>。
 * 用已有地址兑换拿不走任何东西（东西寄回受害者家里），所以真正要守的只有「指定新的收件方」这一步。
 * 在一台<b>不受信任的设备</b>上做这一步，先证明自己是邮箱的主人。
 * 方案与取舍见 {@code docs/业务/会员/账号安全-方案评估与Web端Cookie改造.md}。
 *
 * <h3>为什么验的是邮箱码，不接受密码</h3>
 * 撞库拿下账号的人<b>本来就知道密码</b>。接受密码等于这道验证对主要威胁无效，
 * 只剩「会话被盗但密码没丢」那一种情形能拦住。
 *
 * <h3>为什么不挂在 MemberAuthApi 上</h3>
 * 认证回答「你是谁」，这里回答「你此刻在这台设备上够不够可信」—— 调用时机、调用方、
 * 失败语义都不一样。挂在一起的话，认证契约会长出一堆只有资产出口才用得到的方法。
 *
 * <p>路径前缀 {@code /internal} 同 {@link DeviceApi}：入口层必须把它整体挡在外面。
 *
 * @Date 2026-09-26
 */
@HttpExchange("/internal/member/step-up")
public interface MemberStepUpApi {

    /**
     * 这台设备对这个会员受不受信任。<b>只读，不产生任何副作用。</b>
     */
    @PostExchange("/check")
    DeviceTrust check(@RequestBody StepUpCmd cmd);

    /**
     * 发一封二次验证码，寄到该会员<b>已绑定</b>的邮箱。
     *
     * <p>🔴 收码地址由域按会员号取，契约里刻意没有 email 字段 ——
     * 让客户端填的话，偷到会话的人填自己的邮箱，就把这道验证变成了自己给自己发码。
     */
    @PostExchange("/code")
    StepUpResult sendCode(@RequestBody StepUpCmd cmd);

    /**
     * 校验验证码；通过后把这台设备记为该会员的受信任设备。
     *
     * <p>验证码一经通过即被消费，同一个码不能用第二次。
     */
    @PostExchange("/verify")
    StepUpResult verify(@RequestBody StepUpCmd cmd);

    /**
     * 撤销该会员<b>除当前设备之外</b>的全部信任。用户点「下线其他设备」时调用。
     *
     * <p>🔴 当前设备<b>只有本来就受信任时</b>才保留。否则攻击者在一台新设备上点一下
     * 「下线其他设备」，就把自己变成了唯一受信任的那台 —— 顺带把真正的主人挤成了新设备。
     *
     * @return 当前设备是否仍受信任
     */
    @PostExchange("/revoke-others")
    boolean revokeOthers(@RequestBody StepUpCmd cmd);

    /**
     * 资产出口这一道闸要的全部判断：<b>冻着没有</b> + <b>这台设备可信吗</b>。
     *
     * <p>和 {@link #check} 并存而不是取代它：进程内还有调用方只要信任那一个答案
     * （登录风控的设备信任特征），让它们多查一次冻结没有意义。
     *
     * <p>🔴 守资产出口的拦截器必须用<b>这个</b>，不是 {@link #check} ——
     * 只看信任的话，一台受信任的老设备上的盗号者（会话被偷、设备验证过）照样搬得走东西，
     * 而那恰恰是用户点「这不是我」时最担心的情形。
     */
    @PostExchange("/asset-out")
    AssetOutVerdict assetOut(@RequestBody StepUpCmd cmd);

    /**
     * 「这不是我」：用户举报了某一次登录。
     *
     * <h3>做三件事，顺序即重要性</h3>
     * <ol>
     *   <li><b>冻结资产出口</b>到改密为止 —— 这是唯一能立刻止损的动作；</li>
     *   <li><b>撤销其余设备的信任</b> —— 不撤的话，被踢下线的那台用密码重新登进来
     *       仍然是「受信任设备」，加地址、充话费不用再验证；</li>
     *   <li>给那一次登录的决策流水打上「确认被盗」—— 给分析用，<b>失败不影响前两件</b>。</li>
     * </ol>
     *
     * <p>⚠️ <b>下线会话不在这里</b>：令牌库住在网关（{@code solvela-auth}），
     * 由入口层在调用本方法前后自己踢。域不认识令牌，见 {@code MemberAuthService} 的类注释。
     *
     * @return 当前这台设备是否仍受信任（同 {@link #revokeOthers}）
     */
    @PostExchange("/report-not-me")
    boolean reportNotMe(@RequestBody SessionReportCmd cmd);
}
