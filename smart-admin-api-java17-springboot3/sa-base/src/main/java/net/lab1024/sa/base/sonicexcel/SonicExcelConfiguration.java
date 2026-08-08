package net.lab1024.sa.base.sonicexcel;

import net.lab1024.sa.base.sonicexcel.converter.SonicConverterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

/**
 * SonicExcel 与 Spring 的唯一接线点。
 *
 * <p>刻意把动作放在构造器里而不是 {@code @Bean} 方法：StAX 隔离要尽可能早生效，
 * 而配置类的实例化早于绝大多数业务 Bean。
 *
 * @Date 2026-08-08
 */
@Configuration
public class SonicExcelConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SonicExcelConfiguration.class);

    /**
     * 这些 profile 下把"基本类型字段"从 WARN 升级为异常，让建模问题在 CI 阶段就红。
     */
    private static final List<String> STRICT_PROFILES = List.of("dev", "test", "local");

    public SonicExcelConfiguration(BeanFactory beanFactory, Environment environment) {
        SonicStaxIsolation.install();
        SonicConverterFactory.setBeanFactory(beanFactory);

        boolean strict = Arrays.stream(environment.getActiveProfiles()).anyMatch(STRICT_PROFILES::contains);
        SonicExcelSettings.setStrictMeta(strict);

        // 崩溃残留的兜底清理。正常路径由 SmartExcelUtil 的 finally 负责，
        // 这里覆盖的是 Pod 被 SIGKILL / OOMKilled 那种 finally 根本没机会跑的情况
        SonicTempFiles.sweepStale(SonicTempFiles.DEFAULT_STALE_AGE);

        log.info("[SonicExcel] 就绪，严格元数据模式={}，StAX 自检 {}", strict, SonicStaxIsolation.selfCheck());
    }
}
