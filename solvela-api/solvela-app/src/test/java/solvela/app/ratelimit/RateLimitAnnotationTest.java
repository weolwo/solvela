package solvela.app.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import solvela.security.GatewayRuleSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔴 「新增接口必须配限流」由这个测试保证（网关方案 §2.1）。
 *
 * <p>漏标的接口不会因为没配置而不限流（拦截器会按读 / 写落进默认场景并 WARN），
 * 但「它该归哪个场景」这个决定必须由写接口的人显式做出来 —— 一个兑换接口落进默认写场景，阈值宽了十倍。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
class RateLimitAnnotationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private RuleSnapshotHolder snapshotHolder;

    @Test
    @DisplayName("🔴 每一个接口都显式标了 @RateLimit")
    void everyEndpointIsAnnotated() {
        List<String> missing = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (ownController(method) && method.getMethodAnnotation(RateLimit.class) == null) {
                missing.add(describe(info, method));
            }
        });
        assertTrue(missing.isEmpty(), "这些接口没有标 @RateLimit（新接口要想清楚它归哪个限流场景）：\n"
                + String.join("\n", missing));
    }

    @Test
    @DisplayName("不限流的接口必须写明理由")
    void exemptHasReason() {
        List<String> noReason = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            RateLimit rl = method.getMethodAnnotation(RateLimit.class);
            if (rl != null && rl.value() == RateLimitScene.EXEMPT && rl.reason().isBlank()) {
                noReason.add(describe(info, method));
            }
        });
        assertTrue(noReason.isEmpty(), "EXEMPT 必须写 reason：\n" + String.join("\n", noReason));
    }

    @Test
    @DisplayName("每个场景在内置默认值里都有规则 —— 否则那个场景在 Redis 读不到快照时等于不限流")
    void everySceneHasBuiltinRules() {
        Set<String> withRules = snapshotHolder.current().rules().stream()
                .filter(r -> r.targetType() == 1)
                .map(GatewayRuleSnapshot.Rule::target)
                .collect(Collectors.toSet());
        List<String> without = Arrays.stream(RateLimitScene.values())
                .filter(s -> s != RateLimitScene.EXEMPT)
                .map(Enum::name)
                .filter(s -> !withRules.contains(s))
                .toList();
        assertTrue(without.isEmpty(), "这些场景没有任何规则行：" + without);
    }

    @Test
    @DisplayName("匿名场景的请求体能交出账号 —— 否则账号维度在登录、注册、发码、重置密码上从不计数")
    void anonymousScenesExposeAccount() {
        List<String> blind = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            RateLimit rl = method.getMethodAnnotation(RateLimit.class);
            if (rl == null || !rl.value().anonymous()) {
                return;
            }
            boolean hasBody = Arrays.stream(method.getMethodParameters())
                    .anyMatch(p -> p.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class));
            boolean exposes = Arrays.stream(method.getMethodParameters())
                    .anyMatch(p -> RateLimitAccount.class.isAssignableFrom(p.getParameterType()));
            // 凭票的二次验证（只有一张票，没有账号）是唯一刻意不交账号的：票本身已经绑在验对了密码的那次登录上
            boolean ticketOnly = Arrays.stream(method.getMethodParameters())
                    .anyMatch(p -> p.getParameterType().getSimpleName().equals("LoginChallengeRequest"));
            if (hasBody && !exposes && !ticketOnly) {
                blind.add(describe(info, method));
            }
        });
        assertTrue(blind.isEmpty(), "这些匿名接口的请求体没有实现 RateLimitAccount：\n" + String.join("\n", blind));
    }

    private static boolean ownController(HandlerMethod method) {
        return method.getBeanType().getPackageName().startsWith("solvela.app");
    }

    private static String describe(RequestMappingInfo info, HandlerMethod method) {
        return info.getPatternValues() + " " + info.getMethodsCondition().getMethods()
                + " → " + method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
    }
}
