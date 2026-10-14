package solvela.member.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.DeviceStatusEnum;
import solvela.member.risk.feature.LoginCounters;

/**
 * 设备维度的闸门：登录频次、失败次数、一机多号、批量注册。
 *
 * <h3>它拦的是「量」，不是「人」</h3>
 * 这四条规则<b>都不判断谁是坏人</b>，只判断某个量级不正常。所以处置也只该是
 * 「降级 / 打标」而不是封禁 —— 见 {@link DeviceGuardProperties} 的类注释。
 *
 * <h3>为什么全部在 Redis，不进 MySQL</h3>
 * 这些计数高频、可自然过期、丢了也不要紧。{@code t_member_operation_limit} 那张表
 * 管的是<b>会员</b>的功能级冻结（带人工解冻、要审计），语义和成本都不一样：
 * 把设备计数写进去只会让它变成一张热点表。
 *
 * <h3>🔴 deviceId 为空时一律放行，而且判的是 isBlank 不是 isEmpty</h3>
 * 判成 {@code isEmpty} 的话，一个全是空白的设备号会被当成真设备去计数 ——
 * 于是<b>所有这样的请求共用同一个计数器</b>，很快集体撞上限，
 * 而日志里那个 deviceId 看起来只是「空的」。
 * 生产上走不到（{@code DeviceContract.sanitize} 只放 32 位 hex 过），
 * 但这类「兜底判空判得不够」的写法值得一次就写对。
 *
 * <p>而「为空」本身是<b>常态</b>：灰度期间老客户端还没带设备令牌。把它当成可疑，
 * 等于在 enforce 之前就把老版本用户全挡在外面 —— 那正是三档灰度要避免的事故。
 *
 * @Date 2026-09-09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceGuard {

    /**
     * 🔴 计数全部走它，本类不再自己拼 Redis 键。
     *
     * <p>每个计数器都有两侧：写的一侧在这里（失败时 +1），读的一侧在风控规则里（超没超）。
     * 键名或窗口在两侧各写一遍，漂移的第一天不会有任何报错 —— 表现是「这条限流再也不触发」。
     * 所以键只在 {@link LoginCounters} 里出现一次。
     */
    private final LoginCounters counters;

    private final DeviceGuardProperties properties;

    private final DeviceDispositionService dispositionService;

    /**
     * 登录前的闸：这台设备今天登得太多了吗、最近失败得太多了吗。
     *
     * <p>放在<b>查会员之前</b>调用：被限的设备不该还能拿登录接口去试探
     * 「这个手机号注册过没有」——那正是 {@code BAD_CREDENTIALS} 刻意合并三种原因要堵的口子。
     */
    public DeviceGuardVerdict checkLogin(String deviceId) {
        if (SolvelaStringUtil.isBlank(deviceId)) {
            return DeviceGuardVerdict.pass();
        }
        // 封禁排在所有计数之前：一台已被人工封掉的设备不该再消耗任何计数配额，
        // 也不该因为「今天还没登够 30 次」而被放行
        if (banned(deviceId)) {
            return DeviceGuardVerdict.hit(DeviceGuardRule.BANNED, 0L, false);
        }
        // 🔴 失败计数【只读不加】：它由 recordLoginFailure 在真的失败时才 +1。
        //    在这里 incr 会把「一次正常登录」也算成失败，阈值当场失去意义
        long fails = counters.read(LoginCounters.Counter.DEVICE_FAIL, deviceId);
        if (fails > properties.getMaxFailPerHour()) {
            return verdict(DeviceGuardRule.FAIL_TOO_MANY, deviceId, LoginCounters.Counter.DEVICE_FAIL);
        }

        long logins = counters.bump(LoginCounters.Counter.DEVICE_LOGIN, deviceId);
        if (logins > properties.getMaxLoginPerDay()) {
            return verdict(DeviceGuardRule.LOGIN_TOO_MANY, deviceId, LoginCounters.Counter.DEVICE_LOGIN);
        }
        return DeviceGuardVerdict.pass();
    }

    /**
     * 记一次登录失败。密码错、账号冻结这类<b>能定位到人</b>的失败才算。
     *
     * <p>手机号格式不对不算 —— 那是客户端 bug 或用户手滑，算进去只会让阈值失真。
     */
    public void recordLoginFailure(String deviceId) {
        if (SolvelaStringUtil.isBlank(deviceId)) {
            return;
        }
        counters.bump(LoginCounters.Counter.DEVICE_FAIL, deviceId);
    }

    /**
     * 登录成功之后：把这台设备与这个会员关联起来，并判断一机多号。
     *
     * <p>🔴 只能放在<b>认证通过之后</b>——在那之前拿不到 memberId。
     * 代价是「密码验过了才告诉你设备被限」，看起来别扭，但这个场景里
     * 账号本来就是攻击者自己的，不构成泄露。
     */
    public DeviceGuardVerdict checkMemberFanout(String deviceId, Long memberId) {
        if (SolvelaStringUtil.isBlank(deviceId) || memberId == null) {
            return DeviceGuardVerdict.pass();
        }
        // 集合每次 add 都续期（见 LoginCounters.Counter.DEVICE_MEMBER 的注释：
        // 只在首次设 TTL 的话，一台持续活跃的设备会在 24 小时后计数归零 —— 那正是养号要的效果）
        long distinct = counters.addToSet(LoginCounters.Counter.DEVICE_MEMBER, deviceId, String.valueOf(memberId));
        if (distinct > properties.getMaxMembersPerDay()) {
            return verdict(DeviceGuardRule.MEMBER_FANOUT, deviceId, LoginCounters.Counter.DEVICE_MEMBER);
        }
        return DeviceGuardVerdict.pass();
    }

    /**
     * 注册前的闸：这台设备今天建了几个号。
     *
     * <p>与 {@code MemberRegisterProperties} 的 IP 限频是<b>两个维度</b>，都要过：
     * IP 走代理池就换，设备号得先过一次签发限频才拿得到。
     */
    public DeviceGuardVerdict checkRegister(String deviceId) {
        if (SolvelaStringUtil.isBlank(deviceId)) {
            return DeviceGuardVerdict.pass();
        }
        if (banned(deviceId)) {
            return DeviceGuardVerdict.hit(DeviceGuardRule.BANNED, 0L, false);
        }
        long count = counters.bump(LoginCounters.Counter.DEVICE_REGISTER, deviceId);
        if (count > properties.getMaxRegisterPerDay()) {
            return verdict(DeviceGuardRule.REGISTER_TOO_MANY, deviceId, LoginCounters.Counter.DEVICE_REGISTER);
        }
        return DeviceGuardVerdict.pass();
    }

    /**
     * 组装判定并打日志。
     *
     * <p>dry-run 期间<b>照常放行</b>，只把「本来会被拦」记下来 —— 这条日志就是
     * 后面校准阈值的全部依据，所以打的是 WARN 而不是 DEBUG：它必须能被捞出来。
     * 上线初期它会很吵，那正是要看的东西。
     */
    private DeviceGuardVerdict verdict(DeviceGuardRule rule, String deviceId, LoginCounters.Counter counter) {
        boolean dryRun = properties.isDryRun();
        long retryAfter = counters.retryAfterSeconds(counter, deviceId);
        log.warn("【设备闸门】{} deviceId: {}, 规则: {}({}), 还需等待 {} 秒",
                dryRun ? "命中但放行[dry-run]" : "拦截", deviceId, rule.getValue(), rule.getDesc(), retryAfter);
        /*
         * 🔴 降档也归 dryRun 管。
         *
         * 「进观察档」不是一条日志，它对用户是有感的（下次登录要多验一道短信码）——
         * 所以它和拦截是同一类动作，dry-run 期间两者都不该发生。
         * 只把拦截关掉、留着降档，等于「说好了不拦人，实际在拦」。
         *
         * 代价是 dry-run 期间整个处置闭环也是静止的。那正是方案本来的顺序：
         * 四个阈值全是拍出来的，先拿真实流量校准，再收口。
         */
        if (!dryRun) {
            dispositionService.observe(deviceId, rule.getDesc());
        }
        return DeviceGuardVerdict.hit(rule, retryAfter, dryRun);
    }

    /**
     * 是否已被人工封禁。
     *
     * <p>🔴 <b>不受 dryRun 影响</b>。dry-run 说的是「那四个拍出来的阈值先别拦人」，
     * 而封禁是<b>有人看过之后签字做的决定</b> —— 把它也关掉，后台那个按钮就是假的。
     */
    private boolean banned(String deviceId) {
        return dispositionService.currentStatus(deviceId) == DeviceStatusEnum.BANNED;
    }
}
