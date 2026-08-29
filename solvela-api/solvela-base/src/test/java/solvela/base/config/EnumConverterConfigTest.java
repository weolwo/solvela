package solvela.base.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import solvela.enums.BaseEnum;
import solvela.enums.IssueStatusEnum;
import solvela.enums.MemberStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 路径变量 / 查询参数按 <b>value</b> 转枚举。
 *
 * <p>Spring 对枚举的默认转换是 {@code Enum.valueOf(name)}，也就是按<b>常量名</b>匹配。
 * 而这个项目里路径上传的一律是落库的数字（{@code /updateStatus/{memberId}/{status}} 传的是 1）。
 * 不接管这层转换，接口会返回一个 400，错误信息还看不出是名字/值的差别。
 */
class EnumConverterConfigTest {

    private final EnumConverterConfig.BaseEnumConverterFactory factory =
            new EnumConverterConfig.BaseEnumConverterFactory();

    private <T extends BaseEnum> Converter<String, T> converter(Class<T> type) {
        return factory.getConverter(type);
    }

    @Test
    @DisplayName("按 value 转换：路径上传数字")
    void 按值转换() {
        assertEquals(MemberStatusEnum.NORMAL, converter(MemberStatusEnum.class).convert("1"));
        assertEquals(MemberStatusEnum.FROZEN, converter(MemberStatusEnum.class).convert("2"));
        assertEquals(MemberStatusEnum.CANCELLED, converter(MemberStatusEnum.class).convert("3"));
    }

    @Test
    @DisplayName("兜底按常量名转换：少数接口路径里传的就是枚举名")
    void 按名字兜底() {
        assertEquals(MemberStatusEnum.FROZEN, converter(MemberStatusEnum.class).convert("FROZEN"));
    }

    @Test
    @DisplayName("两端不冲突：值是 0/1/2 的枚举，按值查到的不会被名字兜底抢走")
    void 值优先于名字() {
        assertEquals(IssueStatusEnum.WAIT, converter(IssueStatusEnum.class).convert("0"));
        assertEquals(IssueStatusEnum.OPENED, converter(IssueStatusEnum.class).convert("2"));
    }

    @Test
    @DisplayName("空串按 null 处理，非法值抛出带人话的异常")
    void 边界() {
        assertNull(converter(MemberStatusEnum.class).convert(""));
        assertNull(converter(MemberStatusEnum.class).convert("   "));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> converter(MemberStatusEnum.class).convert("99"));
        assertEquals("「99」不是合法的 MemberStatusEnum 取值", e.getMessage());
    }
}
