package solvela.base.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.base.json.serializer.enumeration.EnumSerialize;
import solvela.enums.IssueStatusEnum;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code @EnumSerialize} 在「字段还是 Integer」和「字段已经换成枚举」两种形态下都要输出 desc。
 *
 * <p>枚举化改造是逐列推进的，所以这两种形态会<b>长期并存</b>。
 * 而它们走的是同一个序列化器 —— 少支持一种，那一列的 {@code xxxDesc} 就静默变 null，
 * 页面上是一列空白，接口照样 200，没有任何报错。
 */
class EnumSerializerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 改造前的形态：字段是 Integer */
    static class BeforeMigration {
        @EnumSerialize(IssueStatusEnum.class)
        public Integer status;
    }

    /** 改造后的形态：字段直接是枚举 */
    static class AfterMigration {
        @EnumSerialize(IssueStatusEnum.class)
        public IssueStatusEnum status;
    }

    @Test
    @DisplayName("字段是 Integer 时，输出 value + desc")
    void 整型字段() {
        BeforeMigration o = new BeforeMigration();
        o.status = 2;
        assertEquals("{\"status\":2,\"statusDesc\":\"已开奖\"}", mapper.writeValueAsString(o));
    }

    @Test
    @DisplayName("字段换成枚举后，输出必须和改造前一模一样")
    void 枚举字段() {
        AfterMigration o = new AfterMigration();
        o.status = IssueStatusEnum.OPENED;
        assertEquals("{\"status\":2,\"statusDesc\":\"已开奖\"}", mapper.writeValueAsString(o));
    }

    @Test
    @DisplayName("null 也要保持两种形态一致")
    void 空值() {
        assertEquals("{\"status\":null,\"statusDesc\":null}", mapper.writeValueAsString(new BeforeMigration()));
        assertEquals("{\"status\":null,\"statusDesc\":null}", mapper.writeValueAsString(new AfterMigration()));
    }
}
