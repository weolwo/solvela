package solvela.biz.server;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.member.risk.dao.MemberRiskRuleDao;

/**
 * 给<b>直接调域</b>的集成测试让开两条规则，用完原样还回去。
 *
 * <pre>
 * &#64;ExtendWith(RelaxedRiskRules.class)
 * &#64;TestPropertySource(properties = "solvela.member.risk.cache-refresh=0s")
 * </pre>
 *
 * <h3>🔴 为什么非让不可</h3>
 * 这些用例都是 {@code memberAuthService.authenticate(...)} 直接调域，而且每次都造
 * <b>全新的 IP 与全新的设备号</b>（为了互不干扰）。于是每一次登录在风控眼里都是
 * 「新设备 + 陌生网络」—— 正是后置矩阵要求验一道码的那一格，{@code login().success()} 恒为 false。
 * 同理没有滑块通行票（那是网关核销的），全局滑块会在前置就把它们拦下。
 *
 * <p>所以让开的只有这三条：
 * <ul>
 *   <li>{@code GLOBAL_CAPTCHA} —— 直接调域拿不到通行票；</li>
 *   <li>{@code CAPTCHA_MATRIX} —— 全新 IP 永远不在「常用网段」里，于是也要票；</li>
 *   <li>{@code LOGIN_MATRIX} —— 测试环境天然落在最严那一格。</li>
 * </ul>
 * 其余规则一条不动：密码冷却、IP 闸、设备闸都照常生效，用例该撞上就撞上。
 *
 * <h3>⚠️ 它不是「把风控关掉」</h3>
 * 这两条本身的行为由 {@code MatrixRuleTest}、{@code CaptchaFlowTest} 和
 * {@code LoginIpGuardTest}（它自己改表调阈值）覆盖。在这里让开，是为了让
 * 「找回密码」「新设备提醒」「换绑邮箱」这些用例测的是它们自己那件事。
 *
 * @Date 2026-10-15
 */
public final class RelaxedRiskRules implements BeforeEachCallback, AfterEachCallback {

    private static final String KEY = "relaxedRiskRules";

    @Override
    public void beforeEach(ExtensionContext context) {
        MemberRiskRuleDao ruleDao = SpringExtension.getApplicationContext(context).getBean(MemberRiskRuleDao.class);
        RiskRuleFixture fixture = new RiskRuleFixture(ruleDao).off("GLOBAL_CAPTCHA", "CAPTCHA_MATRIX", "LOGIN_MATRIX");
        context.getStore(ExtensionContext.Namespace.create(RelaxedRiskRules.class)).put(KEY, fixture);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        RiskRuleFixture fixture = context
                .getStore(ExtensionContext.Namespace.create(RelaxedRiskRules.class))
                .remove(KEY, RiskRuleFixture.class);
        if (fixture != null) {
            // 🔴 一定要还：这些测试打的是真实开发库，改坏一行规则会让之后所有用例莫名其妙
            fixture.close();
        }
    }
}
