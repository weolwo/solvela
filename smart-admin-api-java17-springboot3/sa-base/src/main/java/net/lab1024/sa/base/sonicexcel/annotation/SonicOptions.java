package net.lab1024.sa.base.sonicexcel.annotation;

import net.lab1024.sa.base.sonicexcel.option.SonicOptionProvider;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明这一列的可选值，<b>只在生成导入模板时使用</b> —— 模板里这一列会带下拉框。
 *
 * <p>下拉是"防脏数据"最划算的一招：用户根本填不出非法值，比事后报错强得多。
 *
 * <pre>{@code
 * @SonicTitle("商品状态")
 * @SonicOptions({"预约中", "售卖中", "售罄"})
 * private String goodsStatus;
 * }</pre>
 *
 * @Date 2026-08-08
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface SonicOptions {

    /**
     * 字面量选项。和 {@link #provider()} 二选一，都给时以字面量为准。
     */
    String[] value() default {};

    /**
     * 动态选项来源（枚举、字典……）。实例解析规则同转换器：Spring Bean 优先，回退无参构造。
     */
    Class<? extends SonicOptionProvider> provider() default SonicOptionProvider.None.class;
}
