package solvela.base.config;

import jakarta.annotation.Resource;
import solvela.base.storage.ObjectStorage;
import solvela.base.storage.impl.S3ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * 云存储（S3 协议）配置。从 {@link FileConfig} 拆出来的，原因是依赖本身可选。
 *
 * <p><b>{@code software.amazon.awssdk:s3} 在 solvela-base 里标了 {@code <optional>true</optional>}</b>：
 * 它是 31 个 jar / 8.37MB，而 dev / test / pre / prod 四套配置的 {@code file.storage.mode}
 * 全都是 {@code local} —— 默认构建没有任何理由背着它。要用云存储的人把那条依赖打开即可，
 * 见 solvela-base/pom.xml 里的说明。
 *
 * <p><b>为什么必须单独成类，而不是留在 FileConfig 里靠 {@code @ConditionalOnProperty} 挡</b>：
 * 那个条件只能决定 Bean 建不建，挡不住<b>类加载</b>。{@code @Configuration} 类一旦被解析，
 * 它的 {@code @Bean} 方法返回类型（{@code S3Client} 等）就要被解析，
 * 于是「依赖没打包 + mode=local」这种完全正常的组合会直接 NoClassDefFoundError。
 * {@code @ConditionalOnClass} 走的是 ASM 读元数据、不加载类，条件不满足时整个类根本不会被加载。
 *
 * @Date 2026-08-09
 */
@Configuration
@ConditionalOnClass(S3Client.class)
@ConditionalOnProperty(prefix = "file.storage", name = {"mode"}, havingValue = FileConfig.MODE_CLOUD)
public class FileCloudStorageConfig {

    @Resource
    private FileConfig fileConfig;

    /**
     * 初始化 s3 client 配置
     */
    @Bean
    public S3Client initS3Client() {
        return S3Client.builder()
                .region(Region.of(fileConfig.getCloudRegion()))
                .endpointOverride(URI.create(fileConfig.getCloudEndpoint()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(fileConfig.getCloudAccessKey(), fileConfig.getCloudSecretKey())))
                .serviceConfiguration(storageConfiguration())
                .build();
    }

    /**
     * 初始化 s3 预签名
     */
    @Bean
    public S3Presigner initS3Presigner() {
        return S3Presigner
                .builder()
                .region(Region.of(fileConfig.getCloudRegion()))
                .endpointOverride(URI.create(fileConfig.getCloudEndpoint()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(fileConfig.getCloudAccessKey(), fileConfig.getCloudSecretKey())))
                .serviceConfiguration(storageConfiguration())
                .build();
    }

    /**
     * 客户端与预签名器<b>必须用同一份配置</b>。
     *
     * <p>分开写两遍的后果很隐蔽：上传走客户端、访问走预签名 URL，
     * 两边寻址方式不一致时<b>上传是成功的</b>，只是生成出来的 URL 打不开 ——
     * 而那要等到有人点开一张图才发现。这也是 2026-09-12 把它抽成一个方法的理由：
     * 原先那两段一模一样的配置，正是「改一处忘另一处」的标准形状。
     */
    private S3Configuration storageConfiguration() {
        return S3Configuration.builder()
                // MinIO 等本地对象存储要 true，公有云保持 false。见 FileConfig 的说明
                .pathStyleAccessEnabled(fileConfig.isCloudPathStyleAccess())
                .chunkedEncodingEnabled(false)
                .build();
    }

    @Bean
    public ObjectStorage s3ObjectStorage(S3Client s3Client, S3Presigner s3Presigner) {
        return new S3ObjectStorage(s3Client, s3Presigner, fileConfig.getCloudBucketName());
    }
}
