package net.lab1024.sa.base.module.support.job.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * 把 {@link SmartJobLogAppender} 挂到 root logger 上。
 *
 * <p>只在 WORKER 节点装配 —— ADMIN 节点不执行任务，挂上去纯属白耗。
 *
 * <p>⚠️ 用 {@code instanceof} 判断日志实现而不是直接强转：
 * 项目将来若换成 log4j2，这里应当<b>安静降级</b>（实时日志不可用）
 * 而不是让整个应用启动失败 —— 一个排查辅助功能不该有能力决定应用能不能起来。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SmartJobLogAppenderRegistrar {

    private SmartJobLogAppender appender;

    public SmartJobLogAppenderRegistrar(SmartJobLogCollector collector, String levelName) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext loggerContext)) {
            log.warn("==== SmartJob ==== 当前日志实现不是 Logback，实时执行日志功能不可用");
            return;
        }
        this.appender = new SmartJobLogAppender(collector, levelName);
        this.appender.setContext(loggerContext);
        this.appender.start();

        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);
        log.info("==== SmartJob ==== 执行日志采集器已挂载（仅采集任务线程，级别 >= {}）", levelName);
    }

    @PreDestroy
    public void destroy() {
        if (null == appender) {
            return;
        }
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext loggerContext) {
            loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        }
        appender.stop();
    }
}
