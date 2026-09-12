package solvela.base.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.base.storage.impl.S3ObjectStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 拿<b>真的 MinIO</b> 跑一遍 {@link ObjectStorage} 的契约。
 *
 * <h3>为什么要有它</h3>
 * {@code ObjectStorageContractTest} 的类注释写着「全程不碰网络」—— 那是对的，
 * 它跑在每次构建上。代价是 {@link S3ObjectStorage} 这个<b>唯一会上生产的实现</b>
 * 从来没被那套契约验过：内存和本地实现全绿，而 S3 那条路一次都没跑通。
 *
 * <p>这个类补上那一格，条件是本机真的有一个 MinIO 在跑。
 *
 * <h3>🔴 它专门盯着「路径寻址」这一条</h3>
 * {@code pathStyleAccessEnabled} 配错时的表现<b>不像存储问题</b>：
 * 虚拟主机寻址会去解析 {@code solvela.127.0.0.1:9000}，报出来的是
 * UnknownHostException —— 看上去像网络坏了。而这一整套契约里，
 * 只要这一项错了，<b>第一个 put 就过不去</b>。
 *
 * <h3>没有 MinIO 时【跳过】，不是失败</h3>
 * CI 上没人会为了跑测试起一个对象存储。所以两道 assume：
 * 端口连得上、凭据给了。少一样就跳过，构建照常绿。
 *
 * <pre>
 * 本地怎么跑：
 *     docker compose up -d minio minio-init
 *     MINIO_ROOT_USER=... MINIO_ROOT_PASSWORD=... \
 *       mvn -pl solvela-base-file test -Dtest=S3ObjectStorageMinioTest
 * </pre>
 *
 * <h3>用临时桶，不碰业务那个</h3>
 * 契约里有「put 之前 exists 必须是 false」这种断言，而 MinIO 的桶是留存的 ——
 * 跑第二遍就会因为上一遍的残留而红。所以每次跑建一个随机名的桶，跑完删掉。
 *
 * @Date 2026-09-12
 */
@DisplayName("S3ObjectStorage（真 MinIO）")
class S3ObjectStorageMinioTest extends ObjectStorageContractTest.Contract {

    private static final String ENDPOINT =
            System.getenv().getOrDefault("MINIO_ENDPOINT", "http://127.0.0.1:9000");

    private static String bucket;
    private static S3Client client;
    private static S3Presigner presigner;
    private static S3ObjectStorage storage;

    @BeforeAll
    static void startUp() {
        String accessKey = System.getenv("MINIO_ROOT_USER");
        String secretKey = System.getenv("MINIO_ROOT_PASSWORD");
        assumeTrue(accessKey != null && secretKey != null,
                "没给 MINIO_ROOT_USER / MINIO_ROOT_PASSWORD，跳过");
        assumeTrue(reachable(ENDPOINT), "连不上 " + ENDPOINT + "，跳过（docker compose up -d minio）");

        URI uri = URI.create(ENDPOINT);
        StaticCredentialsProvider credentials =
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        /*
         * 🔴 与 FileCloudStorageConfig 里那份【必须一致】：路径寻址 + 不分块编码。
         *    这里照抄而不是复用那个 @Configuration，是因为它要整个 Spring 上下文，
         *    而这条测试只想验存储本身。两边漂了的话，这个测试就白跑了 ——
         *    所以那边抽了 storageConfiguration() 一个方法，改的时候两处一起看。
         */
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();
        client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(uri)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build();
        presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(uri)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build();

        bucket = "solvela-contract-" + UUID.randomUUID().toString().substring(0, 8);
        client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        storage = new S3ObjectStorage(client, presigner, bucket);
    }

    @AfterAll
    static void tearDown() {
        if (client == null || bucket == null) {
            return;
        }
        try {
            // 桶必须先清空才能删。契约测试留下的对象不多，一页就够
            List<ObjectIdentifier> objects = client
                    .listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build())
                    .contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                    .toList();
            if (!objects.isEmpty()) {
                client.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
                        .delete(Delete.builder().objects(objects).build()).build());
            }
            client.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build());
        } finally {
            client.close();
            presigner.close();
        }
    }

    @Override
    ObjectStorage storage() {
        return storage;
    }

    @Test
    @DisplayName("🔴 能签出一个可直接访问的 URL —— 私有桶下这是唯一的看图方式")
    void presign() {
        StorageKey key = new StorageKey("presign/demo.png");
        storage.put(key, new java.io.ByteArrayInputStream("x".getBytes()), 1,
                ObjectMeta.of("image/png"));

        String url = storage.presignedGet(key, java.time.Duration.ofMinutes(5), null).toString();

        assertThat(url).startsWith(ENDPOINT);
        /*
         * 🔴 路径寻址的证据：桶名出现在【路径】里而不是主机名里。
         * 配成虚拟主机寻址的话这里会是 http://solvela-contract-xxx.127.0.0.1:9000/...
         * —— 那个地址谁也解析不出来，而上传却是成功的，要等到有人点开图才发现。
         */
        assertThat(url).contains("/" + bucket + "/" + key.value());
        assertThat(url).contains("X-Amz-Signature");
    }

    /** 端口连得上就算可用。真发一次 S3 请求要凭据，而凭据判断在上面单独一道。 */
    private static boolean reachable(String endpoint) {
        URI uri = URI.create(endpoint);
        int port = uri.getPort() > 0 ? uri.getPort() : 9000;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), port), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
