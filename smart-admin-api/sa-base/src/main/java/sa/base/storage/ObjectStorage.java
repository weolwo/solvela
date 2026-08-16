package sa.base.storage;

import java.io.InputStream;
import java.util.Collection;

/**
 * 对象存储：<b>只认 {@link StorageKey} 和字节流的纯基础设施层。</b>
 *
 * <p>这一层<b>不认识</b>：{@code MultipartFile}（HTTP 类型）、{@code ResponseDTO}（响应结构）、
 * 业务分类、权限、文件原名。旧的 {@code IFileStorageService} 三样全认识，
 * 后果是它只能被 Controller 调，也没法写单测。
 *
 * <p><b>接口设计是否正确的验收标准</b>：{@code InMemoryObjectStorage} 能不能用一个
 * {@code Map<StorageKey, byte[]>} 加二十行代码写出来。写不出来说明漏了抽象。
 *
 * @Date 2026-08-09
 */
public interface ObjectStorage {

    /**
     * 写入。<b>同 key 覆盖是允许的</b>。
     *
     * <p>"永不覆盖"是<b>业务层</b>的纪律（换图 = 传新文件 + 改引用，见设计文档 §7.6），
     * 靠 key 生成规则里的雪花 ID 保证唯一；在存储层禁止覆盖只会让失败重传变成死路。
     *
     * @param length 必须准确 —— S3 要用它算 Content-Length，给错了对象会被截断或请求挂起
     */
    void put(StorageKey key, InputStream in, long length, ObjectMeta meta);

    /**
     * 按区间读取。调用方负责关闭返回的 {@link StoredObject}。
     *
     * @throws StorageException 失败类型见 {@link StorageFailure}，对象不存在是 {@code NotFound}
     */
    StoredObject open(StorageKey key, ByteRange range);

    /**
     * 整读。
     */
    default StoredObject open(StorageKey key) {
        return open(key, ByteRange.all());
    }

    boolean exists(StorageKey key);

    /**
     * 删除。<b>对象不存在不算失败</b> —— 删除天然应该幂等，否则重试逻辑要处处判断。
     */
    void delete(StorageKey key);

    /**
     * 批量删除。
     *
     * <p><b>刻意只有批量删、没有批量写</b>：HTTP PUT 本来就是一个对象一个请求，S3 没有批量 PUT，
     * 做一个 {@code putAll} 只是把循环挪个位置，还顺手把"部分失败"的处理埋进了基础设施层。
     * 而 DELETE 有真正的批量 API（S3 的 {@code DeleteObjects} 一次 1000 个），值得覆写。
     */
    default void deleteAll(Collection<StorageKey> keys) {
        keys.forEach(this::delete);
    }

    StorageKind kind();
}
