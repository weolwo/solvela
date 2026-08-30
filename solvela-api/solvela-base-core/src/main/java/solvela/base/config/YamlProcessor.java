package solvela.base.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.List;

/**
 * 把 solvela-base 这类共享 jar 里的 solvela-*.yaml 加载进 Environment。
 * <p>
 * Boot 只会自动加载 application.yaml，公共配置放在 solvela-base.yaml 里它是不认的，
 * 所以需要这个 EnvironmentPostProcessor 兜一下。
 * <p>
 * ⚠️ 两个细节是承重墙，改之前先看懂：
 * <ul>
 *   <li>{@code addLast}：优先级排在最后，各服务自己的 application.yaml 才能覆盖公共配置。
 *       换成 addFirst 会把覆盖关系倒过来。</li>
 *   <li>{@code @Order(0)}：必须排在 {@link ConfigDataEnvironmentPostProcessor}
 *       （order = HIGHEST_PRECEDENCE + 10）之后执行，此时 application.yaml 已经在
 *       propertySources 里了，addLast 的相对位置才有意义。</li>
 * </ul>
 * 注册在 META-INF/spring.factories —— 这是 Boot 4 对 EnvironmentPostProcessor 仍然要求的方式，
 * 它早于容器启动，因此这个类不能也不需要是 @Configuration。
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2022-05-30 21:22:12
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Order(value = 0)
public class YamlProcessor implements EnvironmentPostProcessor {

    private static final String CONFIG_LOCATION_PATTERN = "classpath*:solvela-*.yaml";

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    /**
     * 此刻日志系统还没初始化，直接用 slf4j 打的日志会丢。
     * DeferredLogFactory 由 Boot 在构造时注入，日志会延迟到日志系统就绪后再输出。
     */
    private final Log log;

    public YamlProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(YamlProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        this.loadProperty(environment.getPropertySources());
    }

    private void loadProperty(MutablePropertySources propertySources) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(CONFIG_LOCATION_PATTERN);
            for (Resource resource : resources) {
                log.info("初始化系统配置：" + resource.getFilename());
                List<PropertySource<?>> load = loader.load(resource.getFilename(), resource);
                load.forEach(propertySources::addLast);
            }
        } catch (IOException e) {
            throw new IllegalStateException("加载公共配置失败：" + CONFIG_LOCATION_PATTERN, e);
        }
    }

}
