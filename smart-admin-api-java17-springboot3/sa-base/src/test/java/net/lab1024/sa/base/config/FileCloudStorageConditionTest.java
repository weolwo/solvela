package net.lab1024.sa.base.config;

import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.service.IFileStorageService;
import net.lab1024.sa.base.module.support.redis.RedisService;
import net.lab1024.sa.base.storage.ObjectStorage;
import net.lab1024.sa.base.storage.StorageKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 云存储依赖可选化之后的装配验证。
 *
 * <p>{@code software.amazon.awssdk:s3} 标成 optional 之后，运行时可能有、也可能没有。
 * 这个组合矩阵光靠肉眼读注解是看不准的 —— {@code @ConditionalOnClass} 究竟拦不拦得住类加载，
 * 只有真跑一遍才算数。用 {@link ApplicationContextRunner} 而不是 {@code @SpringBootTest}：
 * 不需要 MySQL / Redis，毫秒级。
 *
 * <p>{@link FilteredClassLoader} 用来模拟「jar 没打进来」，这正是默认构建的真实形态。
 *
 * @Date 2026-08-09
 */
class FileCloudStorageConditionTest {

    /**
     * local 模式会装配 {@code LocalFileStorage}，而它在构造时就 createDirectories ——
     * 指向真实路径的话单测会在文件系统上留下目录。用 TempDir 兜住，跑完自动清。
     */
    @TempDir
    static Path uploadRoot;

    private ApplicationContextRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ApplicationContextRunner()
                .withUserConfiguration(FileConfig.class, FileCloudStorageConfig.class, FileCloudStorageMissingGuard.class)
                .withPropertyValues(
                        "file.storage.cloud.region=cn-north-1",
                        "file.storage.cloud.endpoint=https://s3.example.com",
                        "file.storage.cloud.bucket-name=test-bucket",
                        "file.storage.cloud.access-key=ak",
                        "file.storage.cloud.secret-key=sk",
                        "file.storage.cloud.private-url-expire-seconds=600",
                        "file.storage.cloud.public-url-prefix=https://cdn.example.com/",
                        "file.storage.local.upload-path=" + uploadRoot.toAbsolutePath());
    }

    @Test
    @DisplayName("默认形态：mode=local + 依赖没打包 —— 必须能正常启动，不能碰任何 S3 类")
    void localModeWithoutSdk() {
        runner.withPropertyValues("file.storage.mode=local")
                .withClassLoader(new FilteredClassLoader(S3Client.class))
                .run(context -> {
                    // 这一条是本次改造的核心断言：依赖缺失时上下文照样起得来。
                    // 改造前 FileConfig 里挂着返回 S3Client 的 @Bean 方法，这里会 NoClassDefFoundError
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FileCloudStorageConfig.class);
                    assertThat(context).doesNotHaveBean(FileCloudStorageMissingGuard.class);
                    assertThat(context).hasSingleBean(IFileStorageService.class);
                    // 档① 的新存储层：local 模式装本地实现，且不能牵连任何 AWS 类
                    assertThat(context).hasSingleBean(ObjectStorage.class);
                    assertThat(context.getBean(ObjectStorage.class).kind()).isEqualTo(StorageKind.LOCAL);
                });
    }

    @Test
    @DisplayName("mode=local + 依赖在：同样不该装配云存储")
    void localModeWithSdk() {
        runner.withPropertyValues("file.storage.mode=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(S3Client.class);
                    assertThat(context).doesNotHaveBean(FileCloudStorageMissingGuard.class);
                });
    }

    @Test
    @DisplayName("mode=cloud + 依赖在：S3Client / S3Presigner / 云存储实现都要在")
    void cloudModeWithSdk() {
        // FileStorageCloudServiceImpl 用 @Resource 注入 RedisService / FileDao，这里只验证装配关系，
        // 不碰它们的行为。注意必须走 registerSingleton 而不是 withBean —— mock 出来的对象是
        // RedisService 的子类，继承了它那些带 @Resource 的字段，走正常 Bean 生命周期时
        // Spring 会接着去注入 mock 自己的依赖然后炸掉。registerSingleton 直接塞成品，跳过后置处理。
        runner.withPropertyValues("file.storage.mode=cloud")
                .withInitializer(context -> {
                    context.getBeanFactory().registerSingleton("redisService", mock(RedisService.class));
                    context.getBeanFactory().registerSingleton("fileDao", mock(FileDao.class));
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(S3Presigner.class);
                    assertThat(context).hasSingleBean(IFileStorageService.class);
                    assertThat(context).doesNotHaveBean(FileCloudStorageMissingGuard.class);
                    // 档① 的新存储层：cloud 模式装 S3 实现
                    assertThat(context).hasSingleBean(ObjectStorage.class);
                    assertThat(context.getBean(ObjectStorage.class).kind()).isEqualTo(StorageKind.S3);
                });
    }

    @Test
    @DisplayName("mode=cloud + 依赖没打包：启动就要红，且报错里得写清楚怎么办")
    void cloudModeWithoutSdkFailsLoudly() {
        runner.withPropertyValues("file.storage.mode=cloud")
                .withClassLoader(new FilteredClassLoader(S3Client.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    // 只断言"报错了"是不够的 —— 这个守卫存在的全部意义就是那段人话
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("file.storage.mode=cloud")
                            .hasMessageContaining("software.amazon.awssdk");
                });
    }
}
