package solvela.base.module.support.jobspi.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个执行器参数，后台据此渲染表单。
 *
 * <p>🔴 <b>它替掉的是「运营在文本框里手填 JSON」</b>——那是「谁也不知道该填什么」的典型退化点：
 * 参数名拼错、类型填错、必填项漏掉，全都要等任务跑失败了才发现，
 * 而失败原因通常是一句 {@code NumberFormatException}，运营完全看不懂。
 *
 * <p>声明之后：后台按类型渲染控件（数字框 / 下拉 / 开关），必填带红星，
 * 提交时按声明校验。<b>结构可校验、可提示、可测试，自由文本不是</b> ——
 * 这正是「配置化」相对「脚本化」的核心优势所在。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Documented
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobParam {

    /**
     * 参数键名，对应 param JSON 里的 key
     */
    String key();

    /**
     * 中文说明，作为表单项的 label
     */
    String desc();

    Type type() default Type.STRING;

    boolean required() default false;

    /**
     * 默认值（字符串形式，由前端按 type 转换）
     */
    String defaultValue() default "";

    /**
     * {@link Type#ENUM} 时的候选项
     */
    String[] options() default {};

    enum Type {
        STRING,
        INT,
        BOOLEAN,
        DATE,
        ENUM,
    }
}
