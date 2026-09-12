package solvela.base.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import solvela.base.config.FileCloudStorageConfig;
import solvela.base.config.FileConfig;
import solvela.base.storage.impl.S3ObjectStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 把 <b>dev 那份真的 yaml</b> 接到<b>真的 MinIO</b> 上跑一遍。
 *
 * <h3>🔴 它补的是一条谁都没盯着的缝：配置项名字</h3>
 * {@code S3ObjectStorageMinioTest} 证明了<b>存储实现</b>是对的 —— 但它是自己 new 出来的，
 * 一行 yaml 都没读。而 {@link FileConfig} 是靠 {@code @Value("${file.storage.cloud.xxx}")}
 * 一个个字符串对上的：yaml 里写 {@code path-style-access}、代码里写 {@code pathStyleAccess}，
 * 编译器不会吭声，两个测试也都是绿的。
 *
 * <p>而那种错的表现极其不像配置问题：路径寻址没生效 → SDK 去解析
 * {@code solvela.127.0.0.1:9000} → {@code UnknownHostException}。
 * 看上去是网络坏了，能查一下午。
 *
 * <p>所以这里<b>读的是 solvela-base-core 里那份 dev/solvela-base.yaml 本体</b>
 * （base-file 依赖 base-core，它就在 classpath 上），只覆盖两样东西：
 * {@code mode=cloud} 与账号密码 —— 也就是真实使用时 {@code .env} 会给的那两样。
 * 别的全部走 yaml 的默认值。yaml 里少一个键、改错一个名字，这里当场红。
 *
 * <h3>没有 MinIO 时跳过</h3>
 * 与 {@code S3ObjectStorageMinioTest} 同一套 assume：CI 上没人会为跑测试起对象存储。
 *
 * <pre>
 *     docker compose up -d minio minio-init
 *     MINIO_ROOT_USER=... MINIO_ROOT_PASSWORD=... \
 *       mvn -pl solvela-base-file test -Dtest=FileCloudStorageWiringMinioTest
 * </pre>
 *
 * @Date 2026-09-12
 */
@DisplayName("云存储装配（dev yaml + 真 MinIO）")
class FileCloudStorageWiringMinioTest {

    private static final String ENDPOINT =
            System.getenv().getOrDefault("MINIO_ENDPOINT", "http://127.0.0.1:9000");

    @Test
    @DisplayName("🔴 mode=cloud 时，dev yaml 装出来的 ObjectStorage 能真的读写 MinIO")
    void wiresFromDevYaml() throws IOException {
        String accessKey = System.getenv("MINIO_ROOT_USER");
        String secretKey = System.getenv("MINIO_ROOT_PASSWORD");
        assumeTrue(accessKey != null && secretKey != null,
                "没给 MINIO_ROOT_USER / MINIO_ROOT_PASSWORD，跳过");
        assumeTrue(reachable(), "连不上 " + ENDPOINT + "，跳过（docker compose up -d minio）");

        List<PropertySource<?>> yaml = devYaml();
        new ApplicationContextRunner()
                .withInitializer(context -> yaml.forEach(
                        source -> context.getEnvironment().getPropertySources().addLast(source)))
                .withUserConfiguration(FileConfig.class, FileCloudStorageConfig.class)
                // 真实使用时这三样来自 .env，其余一律走 yaml 默认值 —— 那正是要验的部分
                .withPropertyValues(
                        "file.storage.mode=cloud",
                        "file.storage.cloud.access-key=" + accessKey,
                        "file.storage.cloud.secret-key=" + secretKey)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ObjectStorage storage = context.getBean(ObjectStorage.class);
                    assertThat(storage)
                            .as("mode=cloud 却装出了别的实现，说明 @ConditionalOnProperty 没匹配上")
                            .isInstanceOf(S3ObjectStorage.class);

                    /*
                     * 🔴 真写一次。只断言 Bean 类型是不够的 ——
                     * 寻址方式、endpoint、桶名全都要发一次请求才知道对不对，
                     * 而这几项恰恰是配错了也能把 Bean 建出来的。
                     */
                    StorageKey key = new StorageKey(
                            "wiring-probe/" + UUID.randomUUID() + ".txt");
                    byte[] payload = "wired".getBytes(StandardCharsets.UTF_8);
                    try {
                        storage.put(key, new ByteArrayInputStream(payload), payload.length,
                                ObjectMeta.of("text/plain"));
                        assertThat(storage.exists(key))
                                .as("桶名（file.storage.cloud.bucket-name）没对上时就是这里红")
                                .isTrue();
                        try (StoredObject object = storage.open(key, ByteRange.all())) {
                            assertThat(object.stream().readAllBytes()).isEqualTo(payload);
                        }
                    } finally {
                        storage.delete(key);
                    }
                });
    }

    @Test
    @DisplayName("🔴 endpoint 是【主机名】时也要通 —— 这一条才真正验到了路径寻址")
    void pathStyleSurvivesHostnameEndpoint() throws IOException {
        String accessKey = System.getenv("MINIO_ROOT_USER");
        String secretKey = System.getenv("MINIO_ROOT_PASSWORD");
        assumeTrue(accessKey != null && secretKey != null,
                "没给 MINIO_ROOT_USER / MINIO_ROOT_PASSWORD，跳过");
        assumeTrue(reachable(), "连不上 " + ENDPOINT + "，跳过（docker compose up -d minio）");

        /*
         * 🔴 为什么非要换成 localhost：
         *
         * yaml 里的默认 endpoint 是 http://127.0.0.1:9000 —— 一个【IP】。
         * 而虚拟主机寻址是把桶名当子域名拼上去的，IP 没有子域名，
         * 于是 SDK 在 endpoint 是 IP 时【无论怎么配都退回路径寻址】。
         *
         * 也就是说：拿默认 endpoint 跑的话，把 path-style-access 改成 false
         * 这个测试照样绿 —— 2026-09-12 实测过。它验不到那一项。
         *
         * 而 docker compose 里 endpoint 是 http://minio:9000，是个【主机名】。
         * 这正是「本机好好的，一进容器图全挂」的那种差异。
         * 所以这里换成 localhost（每台机器都解析得到，而 solvela.localhost 解析不到），
         * 让配错时当场报 UnknownHostException，而不是等部署到 Docker 里才发现。
         */
        List<PropertySource<?>> yaml = devYaml();
        new ApplicationContextRunner()
                .withInitializer(context -> yaml.forEach(
                        source -> context.getEnvironment().getPropertySources().addLast(source)))
                .withUserConfiguration(FileConfig.class, FileCloudStorageConfig.class)
                .withPropertyValues(
                        "file.storage.mode=cloud",
                        "file.storage.cloud.endpoint=http://localhost:" + port(),
                        "file.storage.cloud.access-key=" + accessKey,
                        "file.storage.cloud.secret-key=" + secretKey)
                .run(context -> {
                    ObjectStorage storage = context.getBean(ObjectStorage.class);
                    StorageKey key = new StorageKey("wiring-probe/" + UUID.randomUUID() + ".txt");
                    byte[] payload = "vhost".getBytes(StandardCharsets.UTF_8);
                    try {
                        storage.put(key, new ByteArrayInputStream(payload), payload.length,
                                ObjectMeta.of("text/plain"));
                        assertThat(storage.exists(key))
                                .as("path-style-access 没从 yaml 传进来时，这里会去解析 "
                                        + "<桶名>.localhost —— 报的是 UnknownHostException，"
                                        + "看着像网络坏了")
                                .isTrue();
                    } finally {
                        storage.delete(key);
                    }
                });
    }

    /**
     * 读源码树里 dev 那份 yaml 本体，<b>不走 classpath</b>。
     *
     * <p>构建时只有<b>当前激活 profile</b> 的那份会被拷进 {@code target/classes}，
     * 而且是拍平成 {@code solvela-base.yaml} 的。走 classpath 的话，
     * 谁用 {@code -Pprod} 构建一次，这个测试读到的就是 prod 的配置 ——
     * 那份没有 {@code path-style-access}（公有云不需要），于是它会以
     * 「连不上 MinIO」的样子红，而真正的原因是读错了文件。
     *
     * <p>相对路径的基准是模块目录（surefire 的工作目录就是它），确定的。
     * yaml 挪了位置这里会立刻报出来，而不是悄悄退化成「验我自己编的配置」。
     */
    private static List<PropertySource<?>> devYaml() throws IOException {
        FileSystemResource resource = new FileSystemResource(
                "../solvela-base-core/src/main/resources/dev/solvela-base.yaml");
        assertThat(resource.exists())
                .as("找不到 %s —— dev 配置挪过位置的话，这个测试要跟着改", resource.getPath())
                .isTrue();
        return new YamlPropertySourceLoader().load("dev-solvela-base", resource);
    }

    private static int port() {
        int port = URI.create(ENDPOINT).getPort();
        return port > 0 ? port : 9000;
    }

    private static boolean reachable() {
        URI uri = URI.create(ENDPOINT);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), port()), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
