package solvela.member.api;

/**
 * 资产出口这一道闸的完整回答：<b>冻着没有</b> + <b>风控怎么说</b> + <b>这台设备可信吗</b>。
 *
 * <h3>为什么三个答案合成一次调用</h3>
 * 守资产出口的拦截器三件事都要知道，而它们都在会员域里。分开调的话，
 * 每一次「加地址 / 充话费」都要跨进程好几趟 —— 而那几趟中间账号状态还可能变。
 *
 * <h3>🔴 顺序是固定的：冻结 → 风控 → 信任</h3>
 * <ul>
 *   <li><b>冻结压倒一切</b>：用户点过「这不是我」，不管他在哪台设备上 ——
 *       包括那台受信任的老设备（举报的人很可能正拿着它）；</li>
 *   <li><b>风控压倒信任</b>（2026-10-16 加的那一档）：{@code MemberStepUpApi#check}
 *       回答的是「这台设备可信吗」，它不回答「这一次操作像不像出事了」。
 *       一台「验证过」的老设备被偷了会话之后照样是可信的 ——
 *       所以风控说要核身时，<b>可信设备也要验</b>；</li>
 *   <li>剩下的才看信任，与改造前完全一样。</li>
 * </ul>
 *
 * @param frozen            资产出口是否冻结中（用户点过「这不是我」，重置密码后恢复）
 * @param trust             这台设备对这个会员的信任档位
 * @param risk              风控的结论，见 {@link AssetOutRisk}
 * @param retryAfterSeconds 仅 {@link AssetOutRisk#BLOCKED} 时有意义，秒
 */
public record AssetOutVerdict(boolean frozen, DeviceTrust trust, AssetOutRisk risk, long retryAfterSeconds) {

    /**
     * 冻结那一档：此刻刻意不算信任、也不跑风控（它们的答案没有任何用途）。
     *
     * <p>⚠️ 叫 {@code ofFrozen} 而不是 {@code frozen}：后者与记录自带的取值器同名，编译不过。
     */
    public static AssetOutVerdict ofFrozen() {
        return new AssetOutVerdict(true, DeviceTrust.NEW_DEVICE, AssetOutRisk.PASS, 0L);
    }

    public static AssetOutVerdict of(DeviceTrust trust, AssetOutRisk risk, long retryAfterSeconds) {
        return new AssetOutVerdict(false, trust, risk, retryAfterSeconds);
    }

    /** 风控没有意见（绝大多数请求） */
    public static AssetOutVerdict pass(DeviceTrust trust) {
        return of(trust, AssetOutRisk.PASS, 0L);
    }
}
