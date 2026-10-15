package solvela.trace;

/**
 * 网关 → 内部服务之间传<b>当前会员号</b>的约定。与 {@link DeviceContract} 是同一套做法、同一个理由。
 *
 * <h3>它解决的是什么</h3>
 * 「这个会员最近还在不在用」只能在<b>有业务请求</b>时记下来，而会员号只有网关知道
 * （它才验会话令牌）。而内部服务的那些 DTO 里绝大多数根本不带 memberId ——
 * 为了一个活跃时间给每层 DTO 加字段，等于开一串「忘了填就静默失效」的口子。
 *
 * <h3>🔴 它是一个被信任的头，所以内院绝不能对外暴露端口</h3>
 * 下游<b>无条件信任</b>这个头里的会员号 —— 和 {@link DeviceContract} 完全一样
 * （那边传的也是「网关验签通过之后的设备号」）。成立的前提只有一个：
 * app-biz 不发布端口，只有网关能从容器网络里访问它。
 *
 * <p>⚠️ 所以<b>不要</b>拿这个头去做任何授权判断。它的用途被刻意限制在
 * 「记一笔活跃时间」这种<b>写了也不影响任何人的事</b>上：假如有一天内院真的被直连了，
 * 伪造它最多只能把别人的活跃时间刷新一下，拿不到任何数据、也改不了任何资产。
 * 真正的身份判断一律走命令里的 memberId，那是网关从会话令牌解出来、显式传下来的。
 *
 * <p>⚠️ 改这里等于改线上协议，三个进程必须同时发版。
 *
 * @Date 2026-10-15
 */
public final class MemberContract {

    /**
     * 请求头名。
     *
     * <p>刻意<b>不叫</b> {@code X-Member-Token}：那个是客户端发给网关的会话令牌，
     * 这个是网关验过之后解出来的会员号。两者一个是凭证一个是身份，
     * 用同一个名字迟早有人把令牌原样透传下去 —— 那等于把凭证散给所有内部服务。
     */
    public static final String HEADER = "X-Member-Id";

    /** MDC 的键。业务代码取值走 {@code MemberTrace.id()}，不要直接 {@code MDC.get} */
    public static final String MDC_KEY = "memberId";

    /** 会员号是 10 位数字（{@code 1000000000 ~ 9999999999}），见 {@code MemberIdCodec} */
    private static final long MIN = 1_000_000_000L;

    private static final long MAX = 9_999_999_999L;

    private MemberContract() {
    }

    /**
     * 把请求头里的候选值收敛成一个合法会员号；<b>不合法返回 null</b>。
     *
     * <p>形状校验不是安全措施（见类注释：这个头本身就是被信任的），
     * 它只是不让脏值进 MDC —— 否则日志里会出现一堆看不懂的 memberId，
     * 而按会员号 grep 日志是排查问题最常用的一步。
     */
    public static Long sanitize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(candidate.trim());
            return value >= MIN && value <= MAX ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
