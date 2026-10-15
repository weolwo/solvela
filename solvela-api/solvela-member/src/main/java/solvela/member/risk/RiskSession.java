package solvela.member.risk;

import lombok.extern.slf4j.Slf4j;
import solvela.base.trace.Trace;
import solvela.base.util.SolvelaIpUtil;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskOutcomeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.MemberRiskEvent;
import solvela.member.MemberRiskHit;
import solvela.member.api.MemberLoginType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次尝试的风控上下文与账本：累积命中、算出结论、最后落一行流水。
 *
 * <h3>用法：每条返回路径都走 end()，close() 只是兜底</h3>
 * <pre>
 * try (RiskSession risk = riskEngine.open(ctx)) {
 *     RiskDecision pre = riskEngine.evaluatePre(risk);
 *     ...
 *     return risk.end(MemberRiskOutcomeEnum.SUCCESS, completeLogin(member, cmd));
 * }
 * </pre>
 * {@link #end} 当场落库并返回调用方本来要返回的东西 —— 写成这样，<b>漏掉一条返回路径就漏掉一行流水</b>，
 * 而那是 code review 看得见的。{@link #close()} 在 end 没被调用时补一行
 * （{@link MemberRiskOutcomeEnum#INTERNAL_ERROR}），守住「每次尝试都有一行流水」。
 *
 * <h3>🔴 两份结论，都要落库</h3>
 * <ul>
 *   <li><b>生效结论</b>：只算 {@code ENFORCE} 的命中 —— 用户实际受到的处理，也是返回给调用方的那个；</li>
 *   <li><b>陪跑结论</b>：{@code SHADOW + ENFORCE} 全算 —— 假如所有规则都打开，这个人会受到的处理。</li>
 * </ul>
 * 「把这条规则切到生效会多打扰多少人」= 两者不一致的那部分（方案 §8.2）。
 * 陪跑结论<b>只落库、不返回</b>：调用方不该能拿它做任何决定。
 *
 * <h3>本类不是线程安全的</h3>
 * 一次尝试一个实例，全程在同一个请求线程里。
 *
 * @Date 2026-10-15
 */
@Slf4j
public final class RiskSession implements AutoCloseable {

    /** {@code t_member_risk_event.cost_ms} 是 smallint —— 超过它就按它记，不要溢出成负数 */
    private static final int MAX_COST_MS = Short.MAX_VALUE;

    private final RiskContext context;

    private final MemberRiskProperties properties;

    private final RiskEventRecorder recorder;

    /** 二次验证那一行指回签票的那次登录；普通登录为 null */
    private final RiskEventRef ref;

    /**
     * 流水与命中明细共用的时间戳，<b>秒级</b>。
     *
     * <p>列是 {@code datetime}（不带小数秒），所以这里先把纳秒抹掉 ——
     * 不抹的话 Java 这边的值与库里的值差一截，而命中明细要靠它和流水精确相等才能关联。
     */
    private final LocalDateTime createTime = LocalDateTime.now().withNano(0);

    private final List<Evaluated> hits = new ArrayList<>();

    private final Map<MemberRiskPhaseEnum, Integer> effectiveScores = new EnumMap<>(MemberRiskPhaseEnum.class);

    private final Map<MemberRiskPhaseEnum, Integer> shadowScores = new EnumMap<>(MemberRiskPhaseEnum.class);

    private final Set<RiskAction> actions = EnumSet.noneOf(RiskAction.class);

    private MemberRiskDecisionEnum decision = MemberRiskDecisionEnum.PASS;

    private MemberRiskDecisionEnum shadowDecision = MemberRiskDecisionEnum.PASS;

    private long retryAfterSeconds;

    private long engineNanos;

    private Integer verifyChannel;

    private MemberRiskOutcomeEnum outcome;

    private boolean written;

    private Long eventId;

    /**
     * 正常由 {@link MemberRiskEngine#open} 创建。公开是为了让认证主干的测试能用<b>真的</b>会话
     * 跑一遍落库与结论累积 —— 那比 mock 掉一个账本有意义得多。
     */
    public RiskSession(RiskContext context, MemberRiskProperties properties, RiskEventRecorder recorder,
                       RiskEventRef ref) {
        this.context = context;
        this.properties = properties;
        this.recorder = recorder;
        this.ref = ref;
    }

    /**
     * 一条规则执行完的结果，已经把「规则配置」折进来了（该用哪个分值、算哪一档处置）。
     *
     * @param error true = 特征取不到或超了时间预算，<b>按未命中处理</b>，只为了让人看见「这条规则其实没在工作」
     */
    record Evaluated(String code, MemberRiskModeEnum mode, MemberRiskDecisionEnum decision, int score,
                     String group, long retryAfterSeconds, String detail, boolean error,
                     Set<RiskAction> actions) {
    }

    public RiskContext context() {
        return context;
    }

    long engineNanos() {
        return engineNanos;
    }

    void addEngineNanos(long nanos) {
        engineNanos += nanos;
    }

    /** 核身路由选定的通道（1-邮箱, 2-短信），只为落库 */
    public void verifyChannel(Integer channel) {
        this.verifyChannel = channel;
    }

    /**
     * 收下一个阶段的全部命中，算出<b>到此刻为止</b>的生效结论。
     *
     * <p>返回的是累计结论而不是本阶段结论：前置放行、后置要 OTP 时，调用方要的就是 OTP。
     * 前置被挡下的路径本来就直接返回了，不会再走到后置。
     */
    RiskDecision settle(MemberRiskPhaseEnum phase, List<Evaluated> phaseHits) {
        hits.addAll(phaseHits);
        effectiveScores.put(phase, aggregateScore(phaseHits, true));
        shadowScores.put(phase, aggregateScore(phaseHits, false));

        MemberRiskDecisionEnum effective = phaseDecision(phase, phaseHits, true);
        MemberRiskDecisionEnum shadow = phaseDecision(phase, phaseHits, false);
        decision = MemberRiskDecisionEnum.max(decision, effective);
        shadowDecision = MemberRiskDecisionEnum.max(shadowDecision, shadow);

        retryAfterSeconds = Math.max(retryAfterSeconds, retryAfterFor(phaseHits, decision));

        for (Evaluated hit : phaseHits) {
            // 🔴 只收生效命中的动作：陪跑规则请求的动作一律丢掉，否则「陪跑」名存实亡
            if (hit.mode() == MemberRiskModeEnum.ENFORCE && !hit.error()) {
                actions.addAll(hit.actions());
            }
        }
        // 结论级动作：它不属于任何单条规则 —— 「可疑到该通知主人」是几条规则叠起来的结果
        if (phase == MemberRiskPhaseEnum.POST && context.memberExists()
                && (decision == MemberRiskDecisionEnum.OTP_STRONG || decision == MemberRiskDecisionEnum.DENY)) {
            actions.add(RiskAction.NOTIFY_OWNER);
        }
        return current();
    }

    /** 此刻的生效结论 */
    public RiskDecision current() {
        return new RiskDecision(decision, retryAfterSeconds, Set.copyOf(actions));
    }

    /**
     * 这次尝试到此结束：<b>当场落库</b>，并把调用方本来要返回的东西原样返回。
     */
    public <T> T end(MemberRiskOutcomeEnum result, T value) {
        end(result);
        return value;
    }

    /**
     * 这次尝试到此结束：当场落库，返回这一行流水的引用（给凭票带着）。
     *
     * <p>🔴 <b>在签凭票之前调用它</b>：凭票里要存 {@link RiskEventRef}，而 id 是插入之后才有的。
     * 写失败时返回的引用里 id 为 null —— 凭票照样能用，只是二次验证那一行的 {@code ref_event_id} 会空着。
     */
    public RiskEventRef end(MemberRiskOutcomeEnum result) {
        if (written) {
            // 一条路径上调了两次 end：第二次只记日志。不覆盖已经落库的那一行 ——
            // 流水是 append-only 的，而「同一次尝试两个结局」说明调用方写错了
            log.warn("【风控】同一次尝试重复结束, 已落库的结局: {}, 本次: {}", outcome, result);
            return new RiskEventRef(eventId, createTime);
        }
        outcome = result;
        write(result);
        return new RiskEventRef(eventId, createTime);
    }

    /**
     * 兜底：{@link #end} 没被调用过就补一行。
     *
     * <p>走到这里只有一种情况 —— 主干抛了未预料的异常（对用户是 500）。那一刻的流水尤其值钱：
     * 风控判了什么、命中了哪几条规则，全在这一行上。
     */
    @Override
    public void close() {
        if (written) {
            return;
        }
        write(MemberRiskOutcomeEnum.INTERNAL_ERROR);
    }

    private void write(MemberRiskOutcomeEnum result) {
        written = true;
        try {
            eventId = recorder.record(toEvent(result), toHitRows());
        } catch (Exception e) {
            // recorder 自己已经吞了异常，这里是最后一道：绝不让落库失败冒到登录链路上
            log.error("【风控】落库过程异常, traceId: {}", Trace.id(), e);
        }
    }

    private MemberRiskEvent toEvent(MemberRiskOutcomeEnum result) {
        MemberRiskEvent event = new MemberRiskEvent();
        event.setEventType(context.getEventType());
        event.setRefEventId(ref == null ? null : ref.eventId());
        event.setMemberId(context.memberId());
        event.setIdentityHashHex(context.getIdentityHashHex());
        event.setLoginType(loginTypeValue(context.getLoginType()));
        event.setDeviceId(context.getDeviceId());
        event.setDeviceTrust(context.getDeviceTrust() == null ? null : context.getDeviceTrust().value());
        event.setIpFamiliar(context.getIpFamiliar() == null ? null : context.getIpFamiliar().value());
        event.setDormant(context.getDormant() == null ? null : (context.getDormant() ? 1 : 0));
        event.setClientIp(context.getClientIp());
        // 归属地只为了让人看懂这一行，判断全程用的是重新解析出来的字段（方案 §4.4）
        event.setIpRegion(SolvelaIpUtil.getRegion(context.getClientIp()));
        event.setCaptchaPassed(context.isCaptchaPassed() ? 1 : 0);
        event.setPreScore(effectiveScores.getOrDefault(MemberRiskPhaseEnum.PRE, 0));
        event.setPostScore(effectiveScores.getOrDefault(MemberRiskPhaseEnum.POST, 0));
        event.setShadowScore(shadowScores.values().stream().mapToInt(Integer::intValue).sum());
        event.setDecision(decision);
        event.setShadowDecision(shadowDecision);
        event.setVerifyChannel(verifyChannel);
        event.setOutcome(result);
        event.setCostMs((int) Math.min(MAX_COST_MS, engineNanos / 1_000_000L));
        event.setTraceId(Trace.id());
        event.setCreateTime(createTime);
        return event;
    }

    private List<MemberRiskHit> toHitRows() {
        List<MemberRiskHit> rows = new ArrayList<>(hits.size());
        for (Evaluated hit : hits) {
            MemberRiskHit row = new MemberRiskHit();
            row.setRuleCode(hit.code());
            row.setMode(hit.mode());
            row.setDecision(hit.decision());
            row.setScore(hit.score());
            row.setDetail(hit.detail());
            row.setError(hit.error() ? 1 : 0);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 本阶段的结论 = 强规则里最严的那个，与打分换算出来的那一档，取更严的。
     *
     * <p>{@code error} 的命中<b>不参与</b>：它的语义就是「这条规则这次没判出来」。
     */
    private MemberRiskDecisionEnum phaseDecision(MemberRiskPhaseEnum phase, List<Evaluated> phaseHits,
                                                 boolean onlyEnforce) {
        MemberRiskDecisionEnum strongest = null;
        for (Evaluated hit : phaseHits) {
            if (hit.error() || hit.decision() == null) {
                continue;
            }
            if (onlyEnforce && hit.mode() != MemberRiskModeEnum.ENFORCE) {
                continue;
            }
            strongest = MemberRiskDecisionEnum.max(strongest, hit.decision());
        }
        return MemberRiskDecisionEnum.max(strongest, fromScore(phase, onlyEnforce));
    }

    /**
     * 打分换算成处置。<b>只会得出滑块 / OTP / 换通道 OTP</b> ——
     * 冷却、限流、拒绝那三档只来自强规则（方案 §3.4）：打分规则都是「像不像」的统计信号，没有一条是证据。
     */
    private MemberRiskDecisionEnum fromScore(MemberRiskPhaseEnum phase, boolean onlyEnforce) {
        Map<MemberRiskPhaseEnum, Integer> scores = onlyEnforce ? effectiveScores : shadowScores;
        int pre = scores.getOrDefault(MemberRiskPhaseEnum.PRE, 0);
        if (phase == MemberRiskPhaseEnum.PRE) {
            return reached(pre, properties.getPreCaptchaScore()) ? MemberRiskDecisionEnum.CAPTCHA : null;
        }
        // 🔴 前置的分带进后置：机房 IP（前置 +30）+ 新设备（后置 +30）在前置都不够一档，
        //    叠起来才够到 OTP —— 这正是打分比硬门槛强的地方
        int total = pre + scores.getOrDefault(MemberRiskPhaseEnum.POST, 0);
        if (reached(total, properties.getStrongOtpScore())) {
            return MemberRiskDecisionEnum.OTP_STRONG;
        }
        return reached(total, properties.getOtpScore()) ? MemberRiskDecisionEnum.OTP : null;
    }

    /** 阈值配成 0 或负数时当作「这一档关掉了」，而不是「所有人都命中」 */
    private static boolean reached(int score, int threshold) {
        return threshold > 0 && score >= threshold;
    }

    /**
     * 分数汇总：<b>同组取最大，组间相加</b>。
     *
     * <p>换国家必然换城市 —— 两条一起加，一个真出差的人仅凭「人在外地」这一件事就被算了两遍。
     * 组外的规则（新设备、机房 IP、失败爆发）是互相独立的证据，照常相加（方案 §3.3）。
     *
     * <p>⚠️ 分组在<b>阶段内</b>生效。跨阶段的组是设计错误：前置的分已经先汇总过一次了，
     * 到后置再和同组的后置命中比最大值，得到的不是任何人想要的数。首批规则里 GEO 组全在后置。
     */
    private static int aggregateScore(List<Evaluated> phaseHits, boolean onlyEnforce) {
        int sum = 0;
        Map<String, Integer> groupMax = new HashMap<>();
        for (Evaluated hit : phaseHits) {
            if (hit.error() || hit.score() <= 0) {
                continue;
            }
            if (onlyEnforce && hit.mode() != MemberRiskModeEnum.ENFORCE) {
                continue;
            }
            if (hit.group() == null) {
                sum += hit.score();
            } else {
                groupMax.merge(hit.group(), hit.score(), Math::max);
            }
        }
        for (int max : groupMax.values()) {
            sum += max;
        }
        return sum;
    }

    /** 给出这一档的生效命中里，要等得最久的那个 */
    private static long retryAfterFor(List<Evaluated> phaseHits, MemberRiskDecisionEnum target) {
        long longest = 0L;
        for (Evaluated hit : phaseHits) {
            if (hit.mode() == MemberRiskModeEnum.ENFORCE && !hit.error() && hit.decision() == target) {
                longest = Math.max(longest, hit.retryAfterSeconds());
            }
        }
        return longest;
    }

    /**
     * 登录方式 → 库里的序号。
     *
     * <p>用 switch 表达式而不是 {@code ordinal()}：新增一种登录方式时<b>编译不过</b>，
     * 而靠 ordinal 的话，只要有人调整了枚举顺序，历史流水的这一列就<b>静默</b>变成了另一种含义。
     */
    private static Integer loginTypeValue(MemberLoginType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case PHONE_PASSWORD -> 1;
            case EMAIL_PASSWORD -> 2;
            case EMAIL_CODE -> 3;
        };
    }
}
