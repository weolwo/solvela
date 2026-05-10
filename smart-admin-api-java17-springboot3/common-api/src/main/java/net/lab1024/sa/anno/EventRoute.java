package net.lab1024.sa.anno;

import net.lab1024.sa.enums.EventCategoryEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EventRoute {

    EventCategoryEnum value();//路由大类
}
