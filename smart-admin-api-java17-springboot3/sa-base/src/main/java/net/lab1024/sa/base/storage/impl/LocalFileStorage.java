package net.lab1024.sa.base.storage.impl;

import net.lab1024.sa.base.storage.ByteRange;
import net.lab1024.sa.base.storage.ObjectMeta;
import net.lab1024.sa.base.storage.ObjectStorage;
import net.lab1024.sa.base.storage.StorageException;
import net.lab1024.sa.base.storage.StorageKey;
import net.lab1024.sa.base.storage.StorageKind;
import net.lab1024.sa.base.storage.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘实现。
 *
 * <p>⚠️ <b>多副本部署下这个实现是坏的</b>：上传打到 pod A，下载请求轮询到 pod B 就是 404，
 * 而且是间歇性的、极难复现的那种。只适合开发和私有化单机部署。
 * 这不是缺陷是定位 —— 真上 K8s 请切 {@link S3ObjectStorage} 或挂共享卷。
 *
 * <p>本实现<b>不提供任何 URL 拼接</b>。旧实现在这里拼了一个
 * {@code urlPrefix + key} 的永久静态 URL，并且对私有文件也照拼，
 * 导致"本地模式下私有文件根本不私有"。URL 怎么给是业务层的事，见设计文档 §7.4。
 *
 * @Date 2026-08-09
 */
public final class LocalFileStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw StorageException.unreachable("本地存储根目录 " + this.root, e);
        }
    }

    @Override
    public void put(StorageKey key, InputStream in, long length, ObjectMeta meta) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // REPLACE_EXISTING：同 key 覆盖在存储层是允许的，见 ObjectStorage#put 的说明
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw StorageException.corrupted("写入本地文件失败：" + key, e);
        }
    }

    @Override
    public StoredObject open(StorageKey key, ByteRange range) {
        Path target = resolve(key);
        long total;
        try {
            total = Files.size(target);
        } catch (NoSuchFileException e) {
            throw StorageException.notFound(key, e);
        } catch (IOException e) {
            throw StorageException.corrupted("读取本地文件失败：" + key, e);
        }

        long length = range.lengthWithin(total);
        InputStream in = null;
        try {
            in = Files.newInputStream(target);
            if (range.start() > 0) {
                // skipNBytes 而不是 skip：后者允许少跳，静默错位比抛异常难查得多
                in.skipNBytes(Math.min(range.start(), total));
            }
            InputStream body = range.isAll() ? in : new LimitedInputStream(in, length);
            return new StoredObject(body, length, total, meta(target));
        } catch (IOException e) {
            closeQuietly(in);
            throw StorageException.corrupted("读取本地文件失败：" + key, e);
        } catch (RuntimeException e) {
            closeQuietly(in);
            throw e;
        }
    }

    @Override
    public boolean exists(StorageKey key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(StorageKey key) {
        try {
            // deleteIfExists：删除必须幂等，否则每个重试路径都要先判断存在性
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw StorageException.corrupted("删除本地文件失败：" + key, e);
        }
    }

    @Override
    public StorageKind kind() {
        return StorageKind.LOCAL;
    }

    // ------------------------------------------------------------------

    /**
     * key → 绝对路径，并做纵深防御。
     *
     * <p>{@link StorageKey} 构造时已经拒掉了 {@code ..} 和反斜杠，这里再校验一次落地后的真实路径
     * 是否还在 root 之内。<b>两道防线不是冗余</b>：将来若有人放宽了 StorageKey 的字符集，
     * 或者 root 本身是个符号链接，这一道还能兜住。
     */
    private Path resolve(StorageKey key) {
        Path target = root.resolve(key.value()).normalize();
        if (!target.startsWith(root)) {
            throw StorageException.corrupted("路径逃逸出存储根目录：" + key, null);
        }
        return target;
    }

    /**
     * 本地存储不保存 contentType（没有元数据存放处），按扩展名兜底。
     * 真实 MIME 已经在上传时嗅探并落到 {@code t_file.content_type}，业务层用那个。
     */
    private static String meta(Path target) {
        try {
            String probed = Files.probeContentType(target);
            return probed == null ? ObjectMeta.DEFAULT_CONTENT_TYPE : probed;
        } catch (IOException e) {
            log.debug("[Storage] 探测 contentType 失败：{}", target, e);
            return ObjectMeta.DEFAULT_CONTENT_TYPE;
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException e) {
            log.debug("[Storage] 关闭文件流失败", e);
        }
    }
}
