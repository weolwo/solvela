package solvela.base.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.IssueStatusEnum;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 钉住 {@code BaseEnum} 的三端契约。
 *
 * <p>这些断言不是「测 Jackson 有没有 bug」，而是钉住<b>接口出参的形状</b>：
 * 枚举在 JSON 里必须是那个数字，不是枚举名，也不是 {@code {value, desc}} 对象。
 * 一旦有人给 BaseEnum 加了别的 Jackson 注解、或把 @JsonValue 挪走，
 * 前端所有按数字比较的地方会静默失配 —— 那种故障在测试里比在线上便宜得多。
 *
 * <p>用裸的 JsonMapper 而不是 Spring 注入的那个：要验证的是注解本身生效，
 * 与 JsonConfig 里的定制无关。
 */
class BaseEnumJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 一个持有枚举字段的载体，模拟真实的 VO */
    record IssueVO(String issueNo, IssueStatusEnum status) {
    }

    @Test
    @DisplayName("序列化出去的是 value，不是枚举名")
    void 序列化为value() {
        assertEquals("0", mapper.writeValueAsString(IssueStatusEnum.WAIT));
        assertEquals("1", mapper.writeValueAsString(IssueStatusEnum.STAGED));
        assertEquals("2", mapper.writeValueAsString(IssueStatusEnum.OPENED));
    }

    @Test
    @DisplayName("作为对象字段时同样只出 value，前端拿到的还是数字")
    void 对象字段序列化() {
        String json = mapper.writeValueAsString(new IssueVO("20260829001", IssueStatusEnum.OPENED));
        assertEquals("{\"issueNo\":\"20260829001\",\"status\":2}", json);
    }

    @Test
    @DisplayName("desc 不进 JSON：前端已有 value→desc 映射，每条记录多带中文串是白费带宽")
    void desc不参与序列化() {
        String json = mapper.writeValueAsString(new IssueVO("20260829001", IssueStatusEnum.WAIT));
        assertEquals(-1, json.indexOf("待开奖"));
    }

    @Test
    @DisplayName("前端传数字能反序列化回枚举")
    void 反序列化() {
        assertEquals(IssueStatusEnum.STAGED, mapper.readValue("1", IssueStatusEnum.class));

        IssueVO vo = mapper.readValue("{\"issueNo\":\"X\",\"status\":2}", IssueVO.class);
        assertEquals(IssueStatusEnum.OPENED, vo.status());
    }

    @Test
    @DisplayName("传了表里没有的值必须抛错，而不是悄悄变成 null")
    void 非法值抛错() {
        assertThrows(JacksonException.class, () -> mapper.readValue("99", IssueStatusEnum.class));
        assertThrows(JacksonException.class,
                () -> mapper.readValue("{\"issueNo\":\"X\",\"status\":99}", IssueVO.class));
    }

    @Test
    @DisplayName("getValue() 的返回类型满足 MyBatis-Plus IEnum 的 Serializable 上界")
    void value是Serializable() {
        Serializable value = IssueStatusEnum.OPENED.getValue();
        assertInstanceOf(Integer.class, value);
        assertEquals(2, value);
    }
}
