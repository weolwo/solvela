package solvela.admin.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import solvela.admin.module.system.job.core.SolvelaJobHandlerRegistry;
import solvela.member.risk.security.SecurityEventDrainJob;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全事件落库任务在「incident → event」改名期间，新旧两个任务名都必须有处理器认。
 *
 * <p>🔴 调度器找不到 {@code handler_name} 对应的处理器，就把那一行标成 {@code handler_missing_flag = 1}，
 * 之后扫描再也不挑它 —— 处理器后来出现了也不会自己恢复。改名分两步落地（先发版、再跑脚本改库里的名字），
 * 中间库里是哪个名字都有可能。少注册一个，落库任务就永久静默停掉。
 *
 * <p>TEMP(安全事件改名-收口): 收口删掉 {@code LegacySecurityIncidentDrainJob} 时，把旧名那条断言反过来（必须不存在），
 * 或者整个测试一起删。见 docs/架构/安全事件改名-实施方案.md。
 */
@SpringBootTest
@DisplayName("安全事件落库任务：改名期间新旧任务名都能调度")
class SecurityEventDrainHandlerNameTest {

    @Autowired
    private SolvelaJobHandlerRegistry registry;

    @Test
    @DisplayName("🔴 新名 securityEventDrain 与旧名 securityIncidentDrain 都已注册")
    void 新旧两个名字都认() {
        assertTrue(registry.contains(SecurityEventDrainJob.HANDLER_NAME), "新名没注册：②发版后脚本一改库，任务就停");
        assertTrue(registry.contains("securityIncidentDrain"), "旧名没注册：发版后、②脚本执行前，任务就停");
    }
}
