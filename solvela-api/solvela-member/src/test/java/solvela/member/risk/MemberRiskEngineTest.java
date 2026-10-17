package solvela.member.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.json.JsonUtils;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskOutcomeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;
import solvela.member.Member;
import solvela.member.MemberRiskEvent;
import solvela.member.MemberRiskHit;
import solvela.member.api.MemberLoginType;
import solvela.member.risk.config.RiskRuleRegistry;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 风控引擎：跑规则、算结论、落一行流水。
 *
 * <h3>这里钉住的是「结论怎么算出来的」</h3>
 * 规则本身各有各的单测，这个类只管那套算法 —— 而它有几条不变量，错了不会报错、只会在某一天放过一个人
 * 或者误伤一片人：
 * <ol>
 *   <li><b>多条命中取最严</b>，并且<b>打分永远不会得出冷却 / 限流 / 拒绝</b>；</li>
 *   <li><b>同组取最大、组间相加</b> —— 否则一个出差的人仅凭「人在外地」被算两遍分；</li>
 *   <li><b>陪跑不影响生效结论</b>，但要能算出「切生效会变成什么」，否则校准阈值就无从下手；</li>
 *   <li><b>规则抛异常 = 放行 + 留痕</b> —— 风控挂了不能等于登录挂了；</li>
 *   <li><b>每一次尝试都有一行流水</b>，异常路径也有。</li>
 * </ol>
 *
 * @Date 2026-10-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("登录风控引擎")
class MemberRiskEngineTest {

    @Mock
    private RiskRuleRegistry registry;

    @Mock
    private RiskEventRecorder recorder;

    private MemberRiskProperties properties;

    private MemberRiskEngine engine;

    @BeforeEach
    void setUp() {
        properties = new MemberRiskProperties();
        engine = new MemberRiskEngine(registry, properties, recorder);
        when(registry.rules(any(), any())).thenReturn(List.of());
    }

    // ------------------------------------------------------------------ 取最严

    @Test
    @DisplayName("多条强规则同时命中，取最严的那一档")
    void strongestWins() {
        pre(direct("A", MemberRiskDecisionEnum.CAPTCHA, ctx -> RiskHit.decide(MemberRiskDecisionEnum.CAPTCHA, "a")),
                direct("B", MemberRiskDecisionEnum.LIMIT, ctx -> RiskHit.decide(MemberRiskDecisionEnum.LIMIT, 90, "b")),
                direct("C", MemberRiskDecisionEnum.OTP, ctx -> RiskHit.decide(MemberRiskDecisionEnum.OTP, "c")));

        RiskDecision decision = engine.evaluatePre(session());

        assertEquals(MemberRiskDecisionEnum.LIMIT, decision.decision());
        assertEquals(90L, decision.retryAfterSeconds(), "要等多久，取给出这一档的命中里最长的那个");
    }

    @Test
    @DisplayName("🔴 打分规则哪怕返回了处置也被丢掉：拒绝只能来自强规则")
    void scoreRuleCannotDeny() {
        pre(score("GREY", 999, null, ctx -> RiskHit.decide(MemberRiskDecisionEnum.DENY, "想拒绝")));

        RiskDecision decision = engine.evaluatePre(session());

        assertEquals(MemberRiskDecisionEnum.CAPTCHA, decision.decision(),
                "999 分只能换来滑块（前置最高那一档），换不来拒绝");
    }

    // ------------------------------------------------------------------ 打分

    @Test
    @DisplayName("🔴 同组取最大、组间相加")
    void groupTakesMax() {
        // 地理组 50 + 40 取 50，组外 30 照常加 —— 合计 80，够到换通道 OTP
        post(score("NEW_COUNTRY", 50, "GEO", ctx -> RiskHit.score("换国家")),
                score("IMPOSSIBLE_TRAVEL", 40, "GEO", ctx -> RiskHit.score("不可能旅行")),
                score("NEW_DEVICE", 30, null, ctx -> RiskHit.score("新设备")));

        RiskSession session = session();
        engine.evaluatePost(session);

        assertEquals(80, written(session).getPostScore(), "50（组内最大）+ 30（组外），不是 120");
        assertEquals(MemberRiskDecisionEnum.OTP_STRONG, session.current().decision());
    }

    @Test
    @DisplayName("前置的分带进后置：两边各自不够一档，叠起来够")
    void preScoreCarriesIntoPost() {
        pre(score("IP_DATACENTER", 30, null, ctx -> RiskHit.score("机房 IP")));
        post(score("NEW_DEVICE", 30, null, ctx -> RiskHit.score("新设备")));

        RiskSession session = session();
        RiskDecision preDecision = engine.evaluatePre(session);
        RiskDecision postDecision = engine.evaluatePost(session);

        assertEquals(MemberRiskDecisionEnum.CAPTCHA, preDecision.decision(), "前置 30 分：到滑块这一档");
        assertEquals(MemberRiskDecisionEnum.OTP, postDecision.decision(), "30 + 30 = 60，够到 OTP");
        MemberRiskEvent event = written(session);
        assertEquals(30, event.getPreScore());
        assertEquals(30, event.getPostScore(), "两列各记各的阶段，不互相覆盖");
    }

    @Test
    @DisplayName("阈值配成 0 不等于所有人都命中，而是这一档关掉了")
    void zeroThresholdDisablesTier() {
        properties.setPreCaptchaScore(0);
        pre(score("NO_DEVICE", 0, null, ctx -> RiskHit.score("没带设备令牌")));

        assertEquals(MemberRiskDecisionEnum.PASS, engine.evaluatePre(session()).decision());
    }

    // ------------------------------------------------------------------ 陪跑

    @Test
    @DisplayName("🔴 陪跑命中不影响生效结论，但要算出「切生效会变成什么」")
    void shadowDoesNotAffectEffective() {
        pre(shadow(direct("B", MemberRiskDecisionEnum.LIMIT,
                ctx -> RiskHit.decide(MemberRiskDecisionEnum.LIMIT, 60, "陪跑中"))));

        RiskSession session = session();
        RiskDecision decision = engine.evaluatePre(session);

        assertEquals(MemberRiskDecisionEnum.PASS, decision.decision(), "用户照常放行");
        MemberRiskEvent event = written(session);
        assertEquals(MemberRiskDecisionEnum.PASS, event.getDecision());
        assertEquals(MemberRiskDecisionEnum.LIMIT, event.getShadowDecision(), "切生效的话这个人会被限流");
    }

    @Test
    @DisplayName("总开关切到 shadow：生效规则降成陪跑，但 alwaysEnforce 的不降")
    void globalShadowSparesAlwaysEnforce() {
        properties.setMode(MemberRiskProperties.Mode.SHADOW);
        pre(direct("IP_FAIL_LIMIT", MemberRiskDecisionEnum.LIMIT,
                        ctx -> RiskHit.decide(MemberRiskDecisionEnum.LIMIT, 60, "普通规则")),
                alwaysEnforce(direct("STRONG_DENY", MemberRiskDecisionEnum.DENY,
                        ctx -> RiskHit.decide(MemberRiskDecisionEnum.DENY, "设备封禁"))));

        RiskSession session = session();
        RiskDecision decision = engine.evaluatePre(session);

        assertEquals(MemberRiskDecisionEnum.DENY, decision.decision(),
                "保命开关不该顺手把「已经确定要拦」的门也打开");
        List<MemberRiskHit> hits = writtenHits(session);
        assertEquals(MemberRiskModeEnum.SHADOW, byCode(hits, "IP_FAIL_LIMIT").getMode());
        assertEquals(MemberRiskModeEnum.ENFORCE, byCode(hits, "STRONG_DENY").getMode());
    }

    @Test
    @DisplayName("关闭的规则连特征都不取")
    void offRuleNeverRuns() {
        boolean[] ran = {false};
        pre(off(direct("A", MemberRiskDecisionEnum.LIMIT, ctx -> {
            ran[0] = true;
            return RiskHit.decide(MemberRiskDecisionEnum.LIMIT, "不该跑");
        })));

        engine.evaluatePre(session());

        assertFalse(ran[0]);
    }

    // ------------------------------------------------------------------ 可信设备

    @Test
    @DisplayName("可信设备跳过标了 bypass 的规则，不跳过没标的")
    void trustedDeviceBypasses() {
        post(bypass(score("NEW_COUNTRY", 50, "GEO", ctx -> RiskHit.score("换国家"))),
                score("IMPOSSIBLE_TRAVEL", 40, "GEO", ctx -> RiskHit.score("不可能旅行")));

        RiskContext context = context();
        context.deviceTrust(RiskDeviceTrust.VERIFIED);
        RiskSession session = engine.open(context);
        engine.evaluatePost(session);

        List<MemberRiskHit> hits = writtenHits(session);
        assertEquals(1, hits.size());
        assertEquals("IMPOSSIBLE_TRAVEL", hits.get(0).getRuleCode(),
                "设备令牌能被复制，所以不可能旅行对可信设备照样要判");
    }

    @Test
    @DisplayName("前置还不知道设备信任，所以一条也不跳过")
    void preRunsEverythingBecauseTrustUnknown() {
        pre(bypass(score("X", 10, null, ctx -> RiskHit.score("跑了"))));

        RiskSession session = session();
        engine.evaluatePre(session);

        assertEquals(1, writtenHits(session).size(), "deviceTrust 为 null 时一律当不可信 —— 往严的方向偏");
    }

    // ------------------------------------------------------------------ 容错

    @Test
    @DisplayName("🔴 规则抛异常：按未命中处理、记一条 error、本次放行")
    void ruleFailureIsFailOpen() {
        pre(direct("BOOM", MemberRiskDecisionEnum.DENY, ctx -> {
            throw new IllegalStateException("redis 抖了");
        }));

        RiskSession session = session();
        RiskDecision decision = engine.evaluatePre(session);

        assertEquals(MemberRiskDecisionEnum.PASS, decision.decision());
        MemberRiskHit hit = writtenHits(session).get(0);
        assertEquals(1, hit.getError(), "要能回答「这条规则是不是其实一直没在工作」");
        assertNull(hit.getDecision());
    }

    @Test
    @DisplayName("规则表快照取不到：整个阶段放行，不往外抛")
    void registryFailureIsFailOpen() {
        when(registry.rules(MemberRiskEventTypeEnum.LOGIN, MemberRiskPhaseEnum.PRE)).thenThrow(new IllegalStateException("库挂了"));

        assertEquals(MemberRiskDecisionEnum.PASS, engine.evaluatePre(session()).decision());
    }

    @Test
    @DisplayName("时间预算用完：剩下的规则记 error 跳过，但 alwaysEnforce 的照跑")
    void budgetExhausted() {
        properties.setFeatureBudget(Duration.ofNanos(1));
        pre(direct("SLOW", MemberRiskDecisionEnum.CAPTCHA, ctx -> RiskHit.decide(MemberRiskDecisionEnum.CAPTCHA, "慢")),
                direct("SKIPPED", MemberRiskDecisionEnum.LIMIT,
                        ctx -> RiskHit.decide(MemberRiskDecisionEnum.LIMIT, "不该跑到")),
                alwaysEnforce(direct("STRONG_DENY", MemberRiskDecisionEnum.DENY,
                        ctx -> RiskHit.decide(MemberRiskDecisionEnum.DENY, "封禁"))));

        RiskSession session = session();
        RiskDecision decision = engine.evaluatePre(session);

        List<MemberRiskHit> hits = writtenHits(session);
        assertEquals(1, byCode(hits, "SKIPPED").getError(), "超了预算的规则按未命中处理");
        assertEquals(MemberRiskDecisionEnum.DENY, decision.decision(), "读本地缓存的强规则不受预算约束");
    }

    // ------------------------------------------------------------------ 动作

    @Test
    @DisplayName("只执行生效命中请求的动作；陪跑请求的动作一律丢掉")
    void shadowActionsAreDropped() {
        pre(direct("ENFORCED", MemberRiskDecisionEnum.LIMIT,
                        ctx -> RiskHit.decide(MemberRiskDecisionEnum.LIMIT, 60, "生效")
                                .withActions(RiskAction.RECORD_LIMIT)),
                shadow(direct("SHADOWED", MemberRiskDecisionEnum.LIMIT,
                        ctx -> RiskHit.decide(MemberRiskDecisionEnum.LIMIT, 60, "陪跑")
                                .withActions(RiskAction.OBSERVE_DEVICE))));

        RiskDecision decision = engine.evaluatePre(session());

        assertTrue(decision.has(RiskAction.RECORD_LIMIT));
        assertFalse(decision.has(RiskAction.OBSERVE_DEVICE), "陪跑规则不该改线上数据，否则「陪跑」名存实亡");
    }

    @Test
    @DisplayName("后置判到换通道 OTP 时，引擎自己加上「通知主人」")
    void notifyOwnerIsDecisionLevel() {
        post(direct("X", MemberRiskDecisionEnum.OTP_STRONG,
                ctx -> RiskHit.decide(MemberRiskDecisionEnum.OTP_STRONG, "很可疑")));

        RiskDecision decision = engine.evaluatePost(session());

        assertTrue(decision.has(RiskAction.NOTIFY_OWNER), "它不属于任何单条规则，是几条叠起来的结果");
    }

    // ------------------------------------------------------------------ 落库

    @Test
    @DisplayName("end() 当场落一行流水，并原样返回调用方要返回的东西")
    void endWritesOnce() {
        RiskSession session = session();
        Object payload = new Object();

        assertSame(payload, session.end(MemberRiskOutcomeEnum.SUCCESS, payload));

        MemberRiskEvent event = written(session);
        assertEquals(MemberRiskOutcomeEnum.SUCCESS, event.getOutcome());
        assertEquals(MemberRiskEventTypeEnum.LOGIN, event.getEventType());
        assertEquals(2, event.getLoginType(), "邮箱密码登录在库里是 2，不靠 ordinal");
    }

    @Test
    @DisplayName("🔴 异常路径也有流水：end 没被调用时 close 补一行「内部异常」")
    void closeBackstopsOnException() {
        RiskSession session = session();
        session.close();

        assertEquals(MemberRiskOutcomeEnum.INTERNAL_ERROR, written(session).getOutcome(),
                "登录报 500 时，风控判了什么、命中了哪几条规则，正是最想知道的");
    }

    @Test
    @DisplayName("end 之后再 close 不会写第二行")
    void closeAfterEndIsNoop() {
        RiskSession session = session();
        session.end(MemberRiskOutcomeEnum.SUCCESS, null);
        session.close();

        verify(recorder, times(1)).record(any(), anyList());
    }

    @Test
    @DisplayName("流水写失败也绝不影响登录")
    void recorderFailureNeverBreaksLogin() {
        when(recorder.record(any(), anyList())).thenThrow(new RuntimeException("库挂了"));
        RiskSession session = session();

        assertSame("ok", session.end(MemberRiskOutcomeEnum.SUCCESS, "ok"));
    }

    @Test
    @DisplayName("二次验证那一行指回签票的那次登录")
    void verifyEventPointsBack() {
        RiskSession session = engine.open(context(), new RiskEventRef(4242L, LocalDateTime.now()));
        session.end(MemberRiskOutcomeEnum.VERIFY_PASSED, null);

        assertEquals(4242L, written(session).getRefEventId());
    }

    @Test
    @DisplayName("命中明细不自己带时间：由 recorder 统一赋成流水的 create_time")
    void hitsLeaveTimeToRecorder() {
        pre(direct("A", MemberRiskDecisionEnum.CAPTCHA, ctx -> RiskHit.decide(MemberRiskDecisionEnum.CAPTCHA, "a")));
        RiskSession session = session();
        engine.evaluatePre(session);

        assertNull(writtenHits(session).get(0).getCreateTime(),
                "两表要按 (id, create_time) 关联，时间只能有一个来源");
    }

    @Test
    @DisplayName("一条命中都没有时不写明细表")
    void noHitsNoRows() {
        session().end(MemberRiskOutcomeEnum.SUCCESS, null);

        assertTrue(writtenHits().isEmpty());
    }

    // ------------------------------------------------------------------ 脚手架

    private RiskContext context() {
        Member member = new Member();
        member.setMemberId(1000000001L);
        return new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_PASSWORD, "a@x.com",
                "ab".repeat(32), member, "dev-1", "H5", "1.2.3.4", false);
    }

    private RiskSession session() {
        return engine.open(context());
    }

    private void pre(RiskRuleRegistry.Bound... rules) {
        when(registry.rules(MemberRiskEventTypeEnum.LOGIN, MemberRiskPhaseEnum.PRE)).thenReturn(List.of(rules));
    }

    private void post(RiskRuleRegistry.Bound... rules) {
        when(registry.rules(MemberRiskEventTypeEnum.LOGIN, MemberRiskPhaseEnum.POST)).thenReturn(List.of(rules));
    }

    /**
     * 取落库的那一行。还没结束的会话就地结束掉 ——
     * 已经结束过的再调一次 end 是空操作（流水 append-only，不会写第二行）。
     */
    private MemberRiskEvent written(RiskSession session) {
        session.end(MemberRiskOutcomeEnum.SUCCESS, null);
        ArgumentCaptor<MemberRiskEvent> captor = ArgumentCaptor.forClass(MemberRiskEvent.class);
        verify(recorder).record(captor.capture(), anyList());
        return captor.getValue();
    }

    private List<MemberRiskHit> writtenHits(RiskSession session) {
        session.end(MemberRiskOutcomeEnum.SUCCESS, null);
        return writtenHits();
    }

    @SuppressWarnings("unchecked")
    private List<MemberRiskHit> writtenHits() {
        ArgumentCaptor<List<MemberRiskHit>> captor = ArgumentCaptor.forClass(List.class);
        verify(recorder).record(any(), captor.capture());
        return captor.getValue();
    }

    private static MemberRiskHit byCode(List<MemberRiskHit> hits, String code) {
        return hits.stream().filter(h -> code.equals(h.getRuleCode())).findFirst()
                .orElseThrow(() -> new AssertionError("没有 " + code + " 的命中：" + hits));
    }

    // ---------- 造规则 ----------

    private static RiskRuleRegistry.Bound direct(String code, MemberRiskDecisionEnum decision,
                                                 Function<RiskContext, RiskHit> body) {
        return bound(code, MemberRiskPhaseEnum.PRE, MemberRiskRuleTypeEnum.DIRECT, decision, 0, null,
                MemberRiskModeEnum.ENFORCE, false, false, body);
    }

    private static RiskRuleRegistry.Bound score(String code, int points, String group,
                                                Function<RiskContext, RiskHit> body) {
        return bound(code, MemberRiskPhaseEnum.PRE, MemberRiskRuleTypeEnum.SCORE, null, points, group,
                MemberRiskModeEnum.ENFORCE, false, false, body);
    }

    private static RiskRuleRegistry.Bound shadow(RiskRuleRegistry.Bound b) {
        return remode(b, MemberRiskModeEnum.SHADOW);
    }

    private static RiskRuleRegistry.Bound off(RiskRuleRegistry.Bound b) {
        return remode(b, MemberRiskModeEnum.OFF);
    }

    private static RiskRuleRegistry.Bound remode(RiskRuleRegistry.Bound b, MemberRiskModeEnum mode) {
        RiskRuleConfig c = b.config();
        return new RiskRuleRegistry.Bound(b.rule(), new RiskRuleConfig(c.code(), c.name(), c.eventType(), c.phase(), c.ruleType(),
                c.directDecision(), c.score(), c.group(), mode, c.bypassByTrust(), c.params()));
    }

    private static RiskRuleRegistry.Bound bypass(RiskRuleRegistry.Bound b) {
        RiskRuleConfig c = b.config();
        return new RiskRuleRegistry.Bound(b.rule(), new RiskRuleConfig(c.code(), c.name(), c.eventType(), c.phase(), c.ruleType(),
                c.directDecision(), c.score(), c.group(), c.mode(), true, c.params()));
    }

    private static RiskRuleRegistry.Bound alwaysEnforce(RiskRuleRegistry.Bound b) {
        RiskRule inner = b.rule();
        RiskRule wrapped = new RiskRule() {
            @Override
            public String code() {
                return inner.code();
            }

            @Override
            public MemberRiskPhaseEnum phase() {
                return inner.phase();
            }

            @Override
            public boolean alwaysEnforce() {
                return true;
            }

            @Override
            public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
                return inner.evaluate(ctx, config);
            }
        };
        return new RiskRuleRegistry.Bound(wrapped, b.config());
    }

    private static RiskRuleRegistry.Bound bound(String code, MemberRiskPhaseEnum phase,
                                                MemberRiskRuleTypeEnum type, MemberRiskDecisionEnum direct,
                                                int points, String group, MemberRiskModeEnum mode,
                                                boolean bypassByTrust, boolean alwaysEnforce,
                                                Function<RiskContext, RiskHit> body) {
        RiskRule impl = new RiskRule() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public MemberRiskPhaseEnum phase() {
                return phase;
            }

            @Override
            public boolean alwaysEnforce() {
                return alwaysEnforce;
            }

            @Override
            public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
                return body.apply(ctx);
            }
        };
        RiskRuleConfig config = new RiskRuleConfig(code, code, MemberRiskEventTypeEnum.LOGIN, phase, type, direct, points, group, mode,
                bypassByTrust, JsonUtils.getMapper().createObjectNode());
        return new RiskRuleRegistry.Bound(impl, config);
    }
}
