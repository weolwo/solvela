package solvela.base.storage.impl;

import solvela.base.storage.ByteRange;
import solvela.base.storage.ObjectMeta;
import solvela.base.storage.ObjectStorage;
import solvela.base.storage.StorageException;
import solvela.base.storage.StorageKey;
import solvela.base.storage.StorageKind;
import solvela.base.storage.StoredObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现，<b>仅供测试</b>。
 *
 * <p>这个类的真正价值不是"跑得快"，而是它<b>是接口设计的验收标准</b>：
 * 如果 {@link ObjectStorage} 的抽象是对的，内存实现就该是一个 Map 加二十来行代码。
 * 旧的 {@code IFileStorageService} 写不出内存实现 —— 它的入参是 {@code MultipartFile}，
 * 你得先伪造一个 HTTP 请求，这本身就说明抽象漏了。
 *
 * <p>有了它，文件模块的全部业务逻辑都能在毫秒级单测里跑完，不碰磁盘、不碰网络。
 *
 * @Date 2026-08-09
 */
public final class InMemoryObjectStorage implements ObjectStorage {

    private final Map<StorageKey, Entry> store = new ConcurrentHashMap<>();

    private record Entry(byte[] data, ObjectMeta meta) {
    }

    @Override
    public void put(StorageKey key, InputStream in, long length, ObjectMeta meta) {
        try {
            store.put(key, new Entry(in.readAllBytes(), meta));
        } catch (IOException e) {
            throw StorageException.corrupted("写入内存存储失败：" + key, e);
        }
    }

    @Override
    public StoredObject open(StorageKey key, ByteRange range) {
        Entry entry = store.get(key);
        if (entry == null) {
            throw StorageException.notFound(key);
        }
        long total = entry.data().length;
        long length = range.lengthWithin(total);
        InputStream stream = new ByteArrayInputStream(entry.data(), (int) Math.min(range.start(), total), (int) length);
        return new StoredObject(stream, length, total, entry.meta().contentType());
    }

    @Override
    public boolean exists(StorageKey key) {
        return store.containsKey(key);
    }

    @Override
    public void delete(StorageKey key) {
        store.remove(key);
    }

    @Override
    public StorageKind kind() {
        // 内存实现只在测试里出现，落库的 storage_kind 语义上仍是 LOCAL
        return StorageKind.LOCAL;
    }

    /**
     * 测试辅助：当前对象数。
     */
    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }
}
