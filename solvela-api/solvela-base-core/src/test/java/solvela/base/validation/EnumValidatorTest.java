package solvela.base.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.base.validation.enumeration.CheckEnum;
import solvela.enums.IssueStatusEnum;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code @CheckEnum} 在「字段还是 Integer」和「字段已经换成枚举」两种形态下都要放行合法值。
 *
 * <h3>为什么专门钉这个</h3>
 * 校验器内部是 {@code enumValList.contains(value)}，而 {@code enumValList} 装的是各常量的
 * <b>value</b>（Integer）。字段一旦换成枚举本身，{@code contains(枚举实例)} 恒为 false，
 * 于是<b>整个接口恒返回 400</b> —— 不是偶发，是百分之百。
 *
 * <p>这不是假想：{@code TicketStatusEnum} 的 javadoc 里就记着「曾让『按中奖状态筛选购彩记录』
 * 恒返回 400」。同样的症状，换个成因又会来一次。
 */
class EnumValidatorTest {

    private final Validator validator = buildValidator();

    private static Validator buildValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator();
        }
    }

    /** 改造前的形态 */
    static class BeforeMigration {
        @CheckEnum(value = IssueStatusEnum.class, message = "期号状态错误")
        public Integer status;
    }

    /** 改造后的形态 */
    static class AfterMigration {
        @CheckEnum(value = IssueStatusEnum.class, message = "期号状态错误")
        public IssueStatusEnum status;
    }

    static class ListForm {
        @CheckEnum(value = IssueStatusEnum.class, message = "期号状态错误")
        public List<IssueStatusEnum> statusList;
    }

    @Test
    @DisplayName("Integer 字段：合法值放行，非法值拦截")
    void 整型字段() {
        BeforeMigration ok = new BeforeMigration();
        ok.status = 2;
        assertTrue(validator.validate(ok).isEmpty());

        BeforeMigration bad = new BeforeMigration();
        bad.status = 99;
        assertEquals(1, validator.validate(bad).size(), "99 不是合法期号状态，应该被拦下");
    }

    @Test
    @DisplayName("枚举字段：必须放行，否则接口会恒 400")
    void 枚举字段() {
        AfterMigration form = new AfterMigration();
        form.status = IssueStatusEnum.OPENED;
        assertTrue(validator.validate(form).isEmpty(), "枚举字段被判非法 —— 接口会恒返回 400");
    }

    @Test
    @DisplayName("枚举集合字段同样放行")
    void 枚举集合字段() {
        ListForm form = new ListForm();
        form.statusList = List.of(IssueStatusEnum.WAIT, IssueStatusEnum.OPENED);
        assertTrue(validator.validate(form).isEmpty());
    }

    @Test
    @DisplayName("null 在非必填时放行（required 语义不受改造影响）")
    void 空值放行() {
        assertTrue(validator.validate(new AfterMigration()).isEmpty());
        assertTrue(validator.validate(new BeforeMigration()).isEmpty());
    }
}
