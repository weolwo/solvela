package net.lab1024.sa.base.config;

import lombok.Data;
import net.lab1024.sa.base.module.support.file.service.FileStorageLocalServiceImpl;
import net.lab1024.sa.base.module.support.file.service.IFileStorageService;
import net.lab1024.sa.base.storage.ObjectStorage;
import net.lab1024.sa.base.storage.impl.LocalFileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 文件上传 配置。
 *
 * <p><b>本类不再引用任何 AWS SDK 类型</b>：{@code software.amazon.awssdk:s3} 已标为 optional，
 * 默认不进包（省 31 个 jar / 8.37MB）。云存储那几个 Bean 全部搬到了
 * {@link FileCloudStorageConfig}，由 {@code @ConditionalOnClass} 守着 ——
 * 配置类的 {@code @Bean} 方法返回类型在类被加载时就要解析，
 * 把它们留在这里会让「依赖缺失 + mode=local」这种完全正常的组合直接 NoClassDefFoundError。
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2019-09-02 23:21:10
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
@Configuration
public class FileConfig implements WebMvcConfigurer {

    /**
     * 包内可见：{@link FileCloudStorageConfig} 的条件注解要用同一个常量，
     * 两处各写一份字面量迟早会漂移。
     */
    static final String MODE_CLOUD = "cloud";

    private static final String MODE_LOCAL = "local";

    @Value("${file.storage.mode}")
    private String mode;

    @Value("${file.storage.cloud.region}")
    private String cloudRegion;

    @Value("${file.storage.cloud.endpoint}")
    private String cloudEndpoint;

    @Value("${file.storage.cloud.bucket-name}")
    private String cloudBucketName;

    @Value("${file.storage.cloud.access-key}")
    private String cloudAccessKey;

    @Value("${file.storage.cloud.secret-key}")
    private String cloudSecretKey;

    @Value("${file.storage.cloud.private-url-expire-seconds}")
    private Long cloudPrivateUrlExpireSeconds;

    @Value("${file.storage.cloud.public-url-prefix}")
    private String cloudPublicUrlPrefix;

    @Value("${file.storage.local.upload-path}")
    private String localUploadPath;


    @Bean
    @ConditionalOnProperty(prefix = "file.storage", name = {"mode"}, havingValue = MODE_LOCAL)
    public IFileStorageService initLocalFileService() {
        return new FileStorageLocalServiceImpl();
    }

    /**
     * 新存储层（档①）。与上面的 {@link IFileStorageService} <b>并存</b>，
     * 调用方在档⑤ 统一迁移过去，届时旧实现整体删除。
     */
    @Bean
    @ConditionalOnProperty(prefix = "file.storage", name = {"mode"}, havingValue = MODE_LOCAL)
    public ObjectStorage localObjectStorage() {
        return new LocalFileStorage(Path.of(localUploadPath));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (MODE_LOCAL.equals(mode)) {
            String path = localUploadPath.endsWith("/") ? localUploadPath : localUploadPath + "/";
            registry.addResourceHandler(FileStorageLocalServiceImpl.UPLOAD_MAPPING + "/**").addResourceLocations("file:" + path);
        }
    }

}
