package sa.scriptengine.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 脚本引擎配置，对应 yaml 里的 {@code script.*}
 */
@Data
@ConfigurationProperties(prefix = "script")
public class ScriptEngineProperties {

    /** 底层引擎实现，目前仅 qlexpress */
    private String engine = "qlexpress";

    /**
     * 单次脚本执行超时（毫秒）。
     *
     * <p><b>注意这是「假超时」：</b>它只能中断脚本自身的指令循环，
     * 无法强行中断已经进入 Java 函数的 HTTP / DB 调用。
     * 耗时操作必须在 Java 侧自己做超时，不要指望这个配置。
     */
    private long timeoutMillis = 3000;

    /**
     * 是否用 BigDecimal 做小数运算。营销场景涉及金额与积分，默认开。
     */
    private boolean precise = true;

    /**
     * 是否允许脚本反射调用任意 Java 对象的属性和方法。
     *
     * <p><b>默认 false（隔离模式），生产环境不要打开</b> —— 打开等于把 RCE 面直接暴露给脚本内容。
     * 所有系统能力都应该通过 {@code @ScriptFunction} 显式白名单暴露。
     */
    private boolean allowJavaReflect = false;

    /**
     * 慢脚本日志阈值（毫秒），超过才打 WARN
     */
    private long slowLogMillis = 200;
}
