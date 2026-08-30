package solvela.base.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 「配了 {@code mode: cloud}，但 AWS SDK 没打进包」时的启动期拦截。
 *
 * <p>没有这个类的话，这种组合的表现是 {@link FileCloudStorageConfig} 静默不生效，
 * 然后在某个注入 {@code IFileStorageService} 的地方抛一句
 * {@code NoSuchBeanDefinitionException} —— 现象离原因隔了十万八千里，
 * 而真正的原因（少了一条 pom 依赖）在报错信息里一个字都没有。
 *
 * <p>宁可启动就红，也不要让人去猜。
 *
 * @Date 2026-08-09
 */
@Configuration
@ConditionalOnProperty(prefix = "file.storage", name = {"mode"}, havingValue = FileConfig.MODE_CLOUD)
@ConditionalOnMissingClass("software.amazon.awssdk.services.s3.S3Client")
public class FileCloudStorageMissingGuard {

    public FileCloudStorageMissingGuard() {
        throw new IllegalStateException("""
                file.storage.mode=cloud 需要 AWS S3 SDK，但它不在运行时 classpath 上。
                该依赖默认是 optional（31 个 jar / 8.37MB，本地存储模式用不到），要用云存储请在业务模块的 pom 里显式引入：

                    <dependency>
                        <groupId>software.amazon.awssdk</groupId>
                        <artifactId>s3</artifactId>
                    </dependency>

                版本与 exclusions 已由 solvela-parent 的 dependencyManagement 统一管理，不用写 version。
                或者把 file.storage.mode 改回 local。""");
    }
}
