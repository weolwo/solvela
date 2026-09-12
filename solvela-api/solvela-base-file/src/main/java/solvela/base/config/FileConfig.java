package solvela.base.config;

import lombok.Data;
import solvela.base.constant.SwaggerTagConst;
import solvela.base.storage.ObjectStorage;
import solvela.base.storage.impl.LocalFileStorage;
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
     * 免登录读取口的路径。这里<b>原先是一段把上传目录整个挂成静态资源的映射</b>（{@code /upload/**}），
     * 已换成 {@code FileController#publicAccess}。
     *
     * <p>换掉的理由不是权限（本模块的文件一律公开，v3.56.0 起没有可见性这个维度），
     * 而是那段映射<b>只在本地存储模式下成立</b>：切到对象存储时它什么都服务不了，
     * 而走控制器的话，本地与云端是同一条代码路径，Range、Content-Type、日志也都由一处产出。
     *
     * <p>代价是丢掉操作系统级的静态文件服务，换来每次请求一次主键查询；
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

    /**
     * 用<b>路径寻址</b>（{@code endpoint/bucket/key}）还是<b>虚拟主机寻址</b>
     *（{@code bucket.endpoint/key}）。
     *
     * <h3>🔴 MinIO / 本地对象存储必须开，公有云保持关</h3>
     * 虚拟主机寻址要求 {@code bucket.endpoint} 这个域名解析得出来。
     * 阿里云、AWS 这些厂商给每个桶都配了泛解析，所以关着是对的；
     * 而本地 MinIO 的 endpoint 是 {@code http://127.0.0.1:9000}，
     * {@code solvela.127.0.0.1:9000} 谁也解析不出来 —— 不开这一项，
     * <b>每一次上传都会卡在 DNS 上</b>，报的还是 UnknownHostException 这种
     * 看不出跟对象存储有关的错。
     *
     * <p>默认 false 是为了不改动现有的云厂商配置。
     */
    @Value("${file.storage.cloud.path-style-access:false}")
    private boolean cloudPathStyleAccess;

    @Value("${file.storage.local.upload-path}")
    private String localUploadPath;


    @Bean
    @ConditionalOnProperty(prefix = "file.storage", name = {"mode"}, havingValue = MODE_LOCAL)
    public ObjectStorage localObjectStorage() {
        return new LocalFileStorage(Path.of(localUploadPath));
    }

}
