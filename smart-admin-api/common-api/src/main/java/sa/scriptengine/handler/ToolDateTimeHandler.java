package sa.scriptengine.handler;

import org.springframework.stereotype.Component;
import sa.scriptengine.annotation.ScriptFunction;
import sa.scriptengine.spi.ScriptDomain;
import sa.scriptengine.spi.ScriptFunctionHandler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 内置工具：日期时间处理。注册后脚本里以 {@code tool_xxx} 调用。
 */
@Component
public class ToolDateTimeHandler implements ScriptFunctionHandler {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public ScriptDomain domain() {
        return ScriptDomain.TOOL;
    }

    @ScriptFunction(name = "now", description = "获取系统当前时间，格式 yyyy-MM-dd HH:mm:ss")
    public String now() {
        return LocalDateTime.now().format(DATE_TIME);
    }

    @ScriptFunction(name = "today", description = "获取系统当前日期，格式 yyyy-MM-dd")
    public String today() {
        return LocalDate.now().format(DATE);
    }

    @ScriptFunction(name = "daysBetween", description = "计算两个日期(yyyy-MM-dd)相差的天数，后者减前者")
    public Long daysBetween(String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate, DATE);
        LocalDate end = LocalDate.parse(endDate, DATE);
        return ChronoUnit.DAYS.between(start, end);
    }
}
