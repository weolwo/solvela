package net.lab1024.sa.base.module.support.job.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Logback Appender：只捕获「当前线程正在执行某个定时任务」时打的日志。
 *
 * <p>判据是 {@link SmartJobLogContext#current()} 不为空 —— 也就是说
 * <b>它对全局日志几乎零影响</b>：非任务线程上的日志走到这里第一行就 return。
 *
 * <p>🔴 <b>它是代码里注册的，不写进 {@code logback-spring.xml}。</b>
 * 写进 xml 的话就需要 xml 能拿到 Spring 容器里的 {@code RedissonClient}，
 * 那要么用静态持有、要么上 {@code LoggerContext} 的属性注入，两条路都很别扭；
 * 而且 xml 一改，四套环境的配置都要同步 —— 少一次同步就少一处漂移。
 *
 * @author alaric
 * @date 2026-08-11
 */
public class SmartJobLogAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final SmartJobLogCollector collector;

    /**
     * 🔴 采集级别阈值。低于它的日志直接丢弃 ——
     * 不加这道过滤，框架的 DEBUG SQL 会把执行器自己打的关键信息挤出缓冲区
     */
    private final Level threshold;

    public SmartJobLogAppender(SmartJobLogCollector collector, String levelName) {
        this.collector = collector;
        // 解析不出来时按 INFO —— 配错一个字母不该导致「全采」这种最坏结果
        this.threshold = Level.toLevel(levelName, Level.INFO);
        this.setName("smartJobLogAppender");
    }

    @Override
    protected void append(ILoggingEvent event) {
        Long logId = SmartJobLogContext.current();
        if (null == logId) {
            // 绝大多数日志走这一条分支：一次 ThreadLocal 读取，代价可忽略
            return;
        }
        if (!event.getLevel().isGreaterOrEqual(threshold)) {
            return;
        }
        StringBuilder line = new StringBuilder()
                .append(TIME_FORMAT.format(Instant.ofEpochMilli(event.getTimeStamp())))
                .append(' ').append(event.getLevel())
                .append(" [").append(event.getThreadName()).append("] ")
                .append(event.getLoggerName()).append(" : ")
                .append(event.getFormattedMessage());

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (null != throwableProxy) {
            line.append(System.lineSeparator()).append(ThrowableProxyUtil.asString(throwableProxy));
        }
        collector.append(logId, line.toString());
    }
}
