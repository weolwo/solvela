package solvela.admin.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.base.json.JsonUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 会话在 Redis 里是一段 JSON，进出必须严丝合缝。
 *
 * <p>这条测试看着像废话，挡的却是一类<b>只会在生产暴露</b>的故障：
 * {@link AdminSession} 是 record，反序列化走的是规范构造器。哪天有人给
 * {@code JsonUtils} 换了 mapper 配置、或者把 record 改成带 Lombok 的类却漏了无参构造，
 * 结果不是编译失败，而是<b>所有人都登不进后台</b> —— 令牌查得到、JSON 读得出、
 * 解析回来是个空对象，于是每一次请求都判成「令牌无效」。
 */
class AdminSessionJsonTest {

    @Test
    @DisplayName("会话 JSON 往返不丢字段")
    void 往返() {
        AdminSession origin = new AdminSession(1024L, true, "PC");

        AdminSession parsed = JsonUtils.parseObject(JsonUtils.toJson(origin), AdminSession.class);

        assertEquals(origin, parsed);
        assertEquals(1024L, parsed.employeeId());
        assertEquals(true, parsed.superFlag());
        assertEquals("PC", parsed.device());
    }

    @Test
    @DisplayName("device 为空也要能往返 —— NON_NULL 会把它整个略掉")
    void 空字段往返() {
        AdminSession parsed = JsonUtils.parseObject(
                JsonUtils.toJson(new AdminSession(1024L, false, null)), AdminSession.class);

        assertEquals(1024L, parsed.employeeId());
        assertEquals(false, parsed.superFlag());
    }
}
