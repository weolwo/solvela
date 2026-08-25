package solvela.base.storage.impl;

import solvela.base.storage.ByteRange;
import solvela.base.storage.ObjectMeta;
import solvela.base.storage.ObjectStorage;
import solvela.base.storage.PresignCapable;
import solvela.base.storage.StorageException;
import solvela.base.storage.StorageKey;
import solvela.base.storage.StorageKind;
import solvela.base.storage.StoredObject;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * S3 协议实现（AWS S3 / MinIO / OSS、COS 的 S3 兼容模式）。
 *
 * <p>⚠️ <b>本类会引用 AWS SDK 类型，而该依赖在 solvela-base 里是 {@code optional} 的</b>
 * （31 个 jar / 8.37MB，默认不进包）。所以它的 Bean 必须挂在带
 * {@code @ConditionalOnClass} 的配置类下，否则 {@code mode=local} + 依赖缺失这个
 * 完全正常的组合会在 Spring 解析 {@code @Bean} 返回类型时 NoClassDefFoundError。
 *
 * @Date 2026-08-09
 */
public final class S3ObjectStorage implements ObjectStorage, PresignCapable {

    /**
     * S3 {@code DeleteObjects} 单次上限。超了整个请求会被拒。
     */
    private static final int DELETE_BATCH_SIZE = 1000;

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public void put(StorageKey key, InputStream in, long length, ObjectMeta meta) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key.value())
                .contentType(meta.contentType())
                .contentLength(length)
                .metadata(meta.userMetadata())
                // 刻意不设 contentDisposition：设了就烧死在对象上，以后想 inline 预览得重传。
                // 需要时在 presignedGet 里按次覆盖，见 ObjectMeta 的说明
                .build();
        try {
            client.putObject(request, RequestBody.fromInputStream(in, length));
        } catch (SdkClientException e) {
            throw StorageException.unreachable(bucket, e);
        } catch (S3Exception e) {
            throw StorageException.corrupted("上传对象失败：" + key, e);
        }
    }

    @Override
    public StoredObject open(StorageKey key, ByteRange range) {
        GetObjectRequest.Builder builder = GetObjectRequest.builder().bucket(bucket).key(key.value());
        if (!range.isAll()) {
            builder.range(range.toHeaderValue());
        }
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(builder.build());
            GetObjectResponse response = stream.response();
            long length = response.contentLength() == null ? 0 : response.contentLength();
            return new StoredObject(stream, length, totalLength(response, length), response.contentType());
        } catch (NoSuchKeyException e) {
            throw StorageException.notFound(key, e);
        } catch (SdkClientException e) {
            throw StorageException.unreachable(bucket, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw StorageException.notFound(key, e);
            }
            throw StorageException.corrupted("读取对象失败：" + key, e);
        }
    }

    /**
     * Range 请求时对象总长只能从 {@code Content-Range: bytes 0-99/12345} 里取，
     * {@code contentLength} 是本次返回的片段长度。整读时没有这个头，两者相等。
     */
    private static long totalLength(GetObjectResponse response, long fallback) {
        String contentRange = response.contentRange();
        if (contentRange == null) {
            return fallback;
        }
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash == contentRange.length() - 1) {
            return fallback;
        }
        try {
            return Long.parseLong(contentRange.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            // 总长是 `*` 的合法情况（长度未知），退回片段长度而不是让下载失败
            return fallback;
        }
    }

    @Override
    public boolean exists(StorageKey key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key.value()).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkClientException e) {
            throw StorageException.unreachable(bucket, e);
        } catch (S3Exception e) {
            // headObject 在部分实现上是抛 404 的 S3Exception 而不是 NoSuchKeyException，两种都要认
            if (e.statusCode() == 404) {
                return false;
            }
            throw StorageException.corrupted("查询对象失败：" + key, e);
        }
    }

    @Override
    public void delete(StorageKey key) {
        try {
            // S3 的 DeleteObject 对不存在的 key 也返回成功，天然幂等
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key.value()).build());
        } catch (SdkClientException e) {
            throw StorageException.unreachable(bucket, e);
        } catch (S3Exception e) {
            throw StorageException.corrupted("删除对象失败：" + key, e);
        }
    }

    /**
     * 用 {@code DeleteObjects} 批量删，比 N 次 HTTP 快一个量级。这是唯一值得做批量的操作。
     */
    @Override
    public void deleteAll(Collection<StorageKey> keys) {
        List<ObjectIdentifier> batch = new ArrayList<>(DELETE_BATCH_SIZE);
        for (StorageKey key : keys) {
            batch.add(ObjectIdentifier.builder().key(key.value()).build());
            if (batch.size() == DELETE_BATCH_SIZE) {
                flushDelete(batch);
            }
        }
        if (!batch.isEmpty()) {
            flushDelete(batch);
        }
    }

    private void flushDelete(List<ObjectIdentifier> batch) {
        try {
            client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(batch).quiet(true).build())
                    .build());
        } catch (SdkClientException e) {
            throw StorageException.unreachable(bucket, e);
        } catch (S3Exception e) {
            throw StorageException.corrupted("批量删除对象失败（" + batch.size() + " 个）", e);
        } finally {
            batch.clear();
        }
    }

    @Override
    public URI presignedGet(StorageKey key, Duration ttl, String responseContentDisposition) {
        GetObjectRequest.Builder get = GetObjectRequest.builder().bucket(bucket).key(key.value());
        if (responseContentDisposition != null && !responseContentDisposition.isBlank()) {
            get.responseContentDisposition(responseContentDisposition);
        }
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(get.build())
                    .build()).url().toURI();
        } catch (Exception e) {
            throw StorageException.corrupted("生成预签名URL失败：" + key, e);
        }
    }

    @Override
    public StorageKind kind() {
        return StorageKind.S3;
    }
}
