package net.lab1024.sa.base.module.support.scriptengine.annotation;

import java.lang.annotation.*;

/**
 * 智能规则函数注解 (贴在需要暴露给脚本的 Java 方法上)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScriptFunction {

    /**
     * 暴露给调用者的方法名 如 verifyUserSign()
     * @return
     */
    String name ();

    /**
     * 函数用途描述 (用于自动生成文档)
     */
    String description() default "该函数暂无描述";
}
