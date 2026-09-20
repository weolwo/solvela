package solvela.member.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.member.Member;
import solvela.member.grade.service.MemberGrowthService;
import solvela.member.manager.MemberManager;
import solvela.exception.BusinessException;
import solvela.scriptengine.annotation.ScriptFunction;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptDomain;
import solvela.scriptengine.spi.ScriptFunctionHandler;
import solvela.scriptengine.spi.ScriptIdentity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 暴露给脚本的会员查询函数。
 *
 * <pre>
 *   m = member_info();
 *   if (m.registerDays &lt;= 7 &amp;&amp; m.registerSource == 'INVITE') {
 *       return draw_executeDrawByScript('POOL_NEWCOMER');
 *   }
 * </pre>
 *
 * <h3>🔴 为什么返回 Map 而不是 Member 实体</h3>
 * 两个理由，第二个是硬的：
 * <ol>
 *   <li><b>脱敏</b>：{@code t_member} 上有手机号密文、邮箱密文、Argon2 口令串。
 *       把实体丢给脚本等于把它们一并交出去，而脚本是运营在后台写的；</li>
 *   <li><b>实体在脚本里根本读不出字段</b>：引擎跑在 {@code isolation} 策略下，
 *       {@code ReflectLoader.loadField} 对非 Map 的对象直接返回 null ——
 *       {@code m.nickname} 拿到的会是 null，而且<b>不报错</b>。
 *       所以「查询类函数一律返回 Map」不是风格问题，是唯一能用的形态。</li>
 * </ol>
 *
 * <h3>会员号从内部通道取，不从参数取</h3>
 * 没有 {@code member_infoOf(memberId)} 这样的重载，是刻意的：那等于让脚本
 * 查任意会员的资料。身份走 {@link ScriptIdentity}，脚本看不见也改不掉。
 *
 * <h3>一次执行只查一次库</h3>
 * 结果缓存在<b>内部通道</b>里（{@link #CACHE_KEY}）。脚本里
 * {@code member_info()} 写三遍是常态 —— 运营不会去想「这行会不会打一次数据库」，
 * 也不该让他去想。缓存的生命周期就是这一次执行，跟着 {@code EngineContext} 一起消失，
 * 所以不存在跨请求读到旧数据的问题。
 */
@Component
@RequiredArgsConstructor
public class MemberScriptFunctions implements ScriptFunctionHandler {

    /**
     * 本次执行的会员资料快照在内部通道里的键。脚本看不见，改不掉
     */
    static final String CACHE_KEY = "__memberInfo";

    private final MemberManager memberManager;

    /**
     * 等级住在 {@code t_member_growth}，不在 {@code t_member} 上 —— 它是派生状态，
     * 跟着成长值走。所以取一次 {@code member_info()} 是<b>两次主键点查</b>。
     *
     * <p>⚠️ 刻意不做成「用到 level 才查」：那会让 {@code member_info()} 返回的 map
     * <b>时有 level 时没有</b>（取决于同一次执行里谁先被调用），
     * 而脚本里 {@code m.grade} 读不到字段时是静默的 null —— 运营看到的是
     * 「这个判据有时灵有时不灵」，那比多一次点查糟得多。
     */
    private final MemberGrowthService memberGrowthService;

    @Override
    public ScriptDomain domain() {
        return ScriptDomain.MEMBER;
    }

    /**
     * 当前会员的资料。字段清单见 {@link #project}。
     */
    @ScriptFunction(name = "info",
            description = "当前会员的资料，返回 map：memberId/memberName/nickname/gender/status/"
                    + "registerSource/registerTime/registerDays/birthday/inviteId/invited/grade。"
                    + "一次执行只查一次库，写几遍都行。不含手机号等敏感字段")
    public Map<String, Object> info(EngineContext context) {
        return load(context, "member_info");
    }

    /**
     * 当前会员的等级。
     *
     * <p>等价于 {@code member_info().level}，单独给一个是因为「等级专享」是
     * 写脚本时最常用的一个判据，而 {@code member_info().level} 要多写一层。
     */
    @ScriptFunction(name = "grade",
            description = "当前会员的等级（0 起，数字越大越高）。等价于 member_info().grade")
    public Long grade(EngineContext context) {
        return (Long) load(context, "member_grade").get("grade");
    }

    /**
     * 等级是否达到某一档。
     *
     * <p>这是<b>会员等级的第一版权益</b>在脚本侧的入口：专享奖池、专享活动都靠它。
     * <pre>
     *   if (member_gradeAtLeast(3)) {
     *       return draw_executeDrawByScript('POOL_PLATINUM');
     *   }
     * </pre>
     *
     * <p>🔴 门槛由脚本传，与 {@code member_isNewMember(days)} 同一个取向：
     * 「几级算高等级」是<b>这个活动</b>的判据，不是会员域的属性 ——
     * 中秋活动可能给银卡以上，年度大促可能只给白金以上。
     */
    @ScriptFunction(name = "gradeAtLeast",
            description = "会员等级是否达到某一档，如 member_gradeAtLeast(3)。"
                    + "门槛由脚本给：几级算高等级是活动的判据，不是会员的属性")
    public Boolean gradeAtLeast(EngineContext context, int grade) {
        if (grade < 0) {
            throw new BusinessException("member_gradeAtLeast 的等级是 " + grade
                    + "。等级从 0 起，这样写谁都满足，多半是参数写错了");
        }
        return (Long) load(context, "member_gradeAtLeast").get("grade") >= grade;
    }

    @ScriptFunction(name = "registerDays",
            description = "会员注册至今多少天。等价于 member_info().registerDays，单独给一个是因为它最常用")
    public Long registerDays(EngineContext context) {
        return (Long) load(context, "member_registerDays").get("registerDays");
    }

    /**
     * N 天内注册的算新人。
     *
     * <p>阈值由脚本传，不写死也不读配置：「几天算新人」是<b>这个活动</b>的判据，
     * 不是会员域的属性 —— 拉新活动可能算 3 天，唤醒活动可能算 90 天。
     */
    @ScriptFunction(name = "isNewMember",
            description = "是否 N 天内注册的新会员，如 member_isNewMember(7)。"
                    + "阈值由脚本给：几天算新人是活动的判据，不是会员的属性")
    public Boolean isNewMember(EngineContext context, int days) {
        if (days <= 0) {
            throw new BusinessException("member_isNewMember 的天数是 " + days
                    + "。这样写谁都不是新人，多半是参数写错了");
        }
        return (Long) load(context, "member_isNewMember").get("registerDays") <= days;
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(EngineContext context, String functionName) {
        Map<String, Object> cached = context.getInternal(CACHE_KEY, Map.class);
        if (cached != null) {
            return cached;
        }
        Long memberId = ScriptIdentity.requireMemberId(context, functionName);
        Member member = memberManager.getById(memberId);
        if (member == null) {
            // 上下文里的会员号是系统绑的，查不到说明数据出了问题，不是业务分支。
            // 兜底成空 map 的话，脚本里所有判据都会静默走 else
            throw new BusinessException("脚本函数 [" + functionName + "] 查不到会员 " + memberId
                    + "。会员号来自系统上下文，查不到属于数据异常");
        }
        Map<String, Object> info = project(member);
        info.put("grade", gradeOf(memberId));
        context.bindInternal(CACHE_KEY, info);
        return info;
    }

    /**
     * 挑出能给脚本看的字段。
     *
     * <p>🔴 白名单而不是黑名单：{@code t_member} 以后加了什么列，默认<b>不会</b>流到脚本里。
     * 手机号/邮箱（密文）、口令串、各种 hash 都不在这里，也不要加进来 ——
     * 脚本能把拿到的东西写进日志、写进缓存。
     */
    private Map<String, Object> project(Member member) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("memberId", member.getMemberId());
        info.put("memberName", member.getMemberName());
        info.put("nickname", member.getNickname());
        info.put("gender", member.getGender() == null ? null : member.getGender().name());
        info.put("status", member.getStatus() == null ? null : member.getStatus().name());
        info.put("registerSource", member.getRegisterSource());
        info.put("registerTime", member.getCreateTime());
        info.put("registerDays", daysSince(member.getCreateTime()));
        info.put("birthday", member.getBirthday());
        info.put("birthdayToday", isBirthdayToday(member.getBirthday()));
        info.put("inviteId", member.getInviteId());
        info.put("invited", member.getInviteId() != null);
        return info;
    }

    /**
     * 注册时间为空时返回 0 而不是 null：脚本里 {@code null <= 7} 会炸，
     * 而「注册时间没记上」不该让整个活动挂掉。0 天等于「今天刚注册」，
     * 落在任何「新人」判据的宽松侧，与「少发不如多发」的方向一致
     */
    private long daysSince(LocalDateTime registerTime) {
        return registerTime == null ? 0L : ChronoUnit.DAYS.between(registerTime.toLocalDate(), LocalDate.now());
    }

    /**
     * 会员等级。<b>统一成 long</b>，因为脚本里的数字字面量是 long ——
     * QLExpress 下 {@code m.grade >= 3} 两边类型不一致时的行为不值得赌。
     *
     * <p>查不到成长值行的会员是 0 级（他确实还没攒过任何成长值），
     * 这一步由 {@code MemberGrowthService.currentGrade} 负责，不在这里猜。
     */
    private long gradeOf(Long memberId) {
        Integer level = memberGrowthService.currentGrade(memberId);
        return level == null ? 0L : level.longValue();
    }

    private boolean isBirthdayToday(LocalDate birthday) {
        if (birthday == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return birthday.getMonthValue() == today.getMonthValue()
                && birthday.getDayOfMonth() == today.getDayOfMonth();
    }
}
