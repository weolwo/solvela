package net.lab1024.sa.scriptengine.core;

import net.lab1024.sa.base.module.support.scriptengine.annotation.ScriptFunctionGroup;
import net.lab1024.sa.scriptengine.annotation.ScriptFunction;
import net.lab1024.sa.scriptengine.spi.ScriptEngineFunctionHandler;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 🚀 内置工具：日期时间处理器
 */
@ScriptFunctionGroup(value = "日期时间处理器")
@Component
public class DateTimeScriptHandler implements ScriptEngineFunctionHandler {


    @ScriptFunction(name = "getNow", description = "获取系统当前精确时间，返回格式：yyyy-MM-dd HH:mm:ss")
    public String now(int x) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @ScriptFunction(name = "_daysBetween", description = "计算两个日期(yyyy-MM-dd)之间相差的天数")
    public Long daysBetween(String date1, String date2) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate start = LocalDate.parse(date1, formatter);
        LocalDate end = LocalDate.parse(date2, formatter);
        return ChronoUnit.DAYS.between(start, end);
    }
}