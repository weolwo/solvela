package net.lab1024.sa.base.config;

import lombok.Data;
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

    /**
     * 本地模式下公开文件的静态资源前缀。原先挂在 {@code FileStorageLocalServiceImpl} 上，
     * 那个类已随档⑤ 删除，常量搬到这里 —— 它本来就是「怎么对外暴露」的配置，
     * 属于配置类而不是某个存储实现。
     *
     * <p>⚠️ 这个路径上的文件<b>谁拿到 URL 谁就能下</b>。私有文件不走这条路，
     * 走 {@code /file/download/{fileId}} 的登录态鉴权，见 {@code FileVisibilityEnum}。
     */
    public static final String UPLOAD_MAPPING = "/upload";

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
    public ObjectStorage localObjectStorage() {
        return new LocalFileStorage(Path.of(localUploadPath));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (MODE_LOCAL.equals(mode)) {
            String path = localUploadPath.endsWith("/") ? localUploadPath : localUploadPath + "/";
            registry.addResourceHandler(UPLOAD_MAPPING + "/**").addResourceLocations("file:" + path);
        }
    }

}
