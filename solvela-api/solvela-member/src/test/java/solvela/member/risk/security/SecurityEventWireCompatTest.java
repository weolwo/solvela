package solvela.member.risk.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.base.json.JsonUtils;
import solvela.security.SecurityEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全事件的线上格式在「incident → event」改名前后都读得懂。
 *
 * <p>🔴 旧版生产方（发版间隙里还在跑的网关）写的是 {@code incidentType}。消费方不认这个名字的话，
 * {@code int eventType} 拿不到值，Jackson 3 默认（FAIL_ON_NULL_FOR_PRIMITIVES）直接抛异常，
 * 落库任务把它当坏事件丢弃 —— 发版间隙里的事件一条条丢掉，只剩 ERROR 日志。实测过：去掉别名这条用例就是这样红的。
 * 这里用落库任务真正用的那个解析入口（{@link JsonUtils#parseObject}）来验。
 */
@DisplayName("安全事件线上格式：改名前后兼容")
class SecurityEventWireCompatTest {

    @Test
    @DisplayName("🔴 旧版生产方的 incidentType 读成 eventType，不会被当坏事件丢掉")
    void 旧字段名读得懂() {
        String legacy = """
                {"incidentType":3,"ruleCode":"XSS_SCRIPT","action":2,"suppressReason":0,"clientIp":"203.0.113.9",
                 "observeDevice":false,"occurredAtEpochMilli":1795171601578}
                """;
        SecurityEvent e = JsonUtils.parseObject(legacy, SecurityEvent.class);
        assertEquals(SecurityEvent.KEYWORD, e.eventType());
        assertEquals("XSS_SCRIPT", e.ruleCode());
        assertEquals(SecurityEvent.ACTION_BLACK, e.action());
    }

    @Test
    @DisplayName("新版生产方只写 eventType，往返一致")
    void 新格式只用新名字() {
        SecurityEvent e = SecurityEvent.builder(SecurityEvent.HONEYPOT, "/.env").build();
        String json = JsonUtils.toJson(e);
        assertTrue(json.contains("\"eventType\":1"), json);
        assertFalse(json.contains("incidentType"), json);
        assertEquals(e, JsonUtils.parseObject(json, SecurityEvent.class));
    }
}
