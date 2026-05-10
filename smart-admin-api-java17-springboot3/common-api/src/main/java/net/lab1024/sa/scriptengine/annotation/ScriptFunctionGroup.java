package net.lab1024.sa.base.module.support.scriptengine.annotation;

import java.lang.annotation.*;

/**
 * 脚本引擎专属：函数分类标签 (打在 Handler 类上)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScriptFunctionGroup {

    /**
     * @return
     */
    String value() default "默认通用工具";
}
