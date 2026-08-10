package net.lab1024.sa.base.config;

import lombok.Data;
import net.lab1024.sa.base.constant.SwaggerTagConst;
import net.lab1024.sa.base.storage.ObjectStorage;
import net.lab1024.sa.base.storage.impl.LocalFileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
public class FileConfig {

    /**
     * 包内可见：{@link FileCloudStorageConfig} 的条件注解要用同一个常量，
     * 两处各写一份字面量迟早会漂移。
     */
    static final String MODE_CLOUD = "cloud";

    private static final String MODE_LOCAL = "local";

    /**
     * 🔴 <b>这里原先有一段把整个上传目录挂成免登录静态资源的映射（{@code /upload/**}），已删除。</b>
     *
     * <p>它不看 {@code visibility}：实测不带任何 token 请求
     * {@code /upload/feedback/202608/10/xxx.jpg} 会返回 200 + 完整字节，而同一个文件走
     * {@code /support/file/download/{id}} 是要登录态的。也就是说，<b>档⑤ 声称修掉的
     * 「本地模式下私有文件根本不私有」，换了个地方原样活着</b> —— 只是当时所有文件的
     * visibility 都被上传处写死成公开，这个矛盾没有机会暴露。
     *
     * <p>替代方案是 {@code FileController#publicAccess}（{@code /support/file/public/**}）：
     * 同样免登录、同样可以被 CDN 回源，但<b>会查一次 visibility</b>，私有文件一律 404。
     * 之所以不改成"公私分目录、静态映射只挂公开那棵树"，是因为设计决策 D2 写得很清楚：
     * <b>权限进 DB 元数据，不进路径</b>。把可见性编回目录名，就是在重犯 {@code private/} 前缀那个错。
     *
     * <p>代价是丢掉了操作系统级的静态文件服务，换来每次请求一次主键查询；
     * 用 {@code Cache-Control: immutable} 把这一跳挡在浏览器和 CDN 之外
     * （storageKey 不可变、永不覆盖，所以这个缓存头是安全的）。
     */
    public static final String PUBLIC_FILE_PATH = "/file/public";

    /**
     * 带 {@code /support} 前缀的完整路径。<b>拼出来而不是再写一遍字面量</b> ——
     * 上一轮刚因为「以为不用带 /support」而让所有 fileUrl 变成死链，同一个错不犯第二次。
     * 免登录白名单与 URL 解析都用这个常量。
     */
    public static final String PUBLIC_FILE_MAPPING = SwaggerTagConst.Support.URL_PREFIX + PUBLIC_FILE_PATH;

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

}
