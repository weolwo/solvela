package net.lab1024.sa.base.storage;

import net.lab1024.sa.base.storage.impl.InMemoryObjectStorage;
import net.lab1024.sa.base.storage.impl.LocalFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ObjectStorage} 的契约测试：<b>一套断言，跑在所有实现上。</b>
 *
 * <p>这么组织不是为了少写代码，是为了保证"内存实现的行为 == 真实实现的行为"。
 * 否则用内存实现写的业务单测全部是自欺欺人 —— 这是伪造件（fake）最常见的失败方式。
 *
 * <p>全程不碰网络。Local 用 {@code @TempDir}，测试结束自动清理。
 *
 * @Date 2026-08-09
 */
class ObjectStorageContractTest {

    abstract static class Contract {

        private static final StorageKey KEY = new StorageKey("banner/202608/09/1954823001234567.png");
        private static final byte[] DATA = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);

        abstract ObjectStorage storage();

        private void put(StorageKey key, byte[] data) {
            storage().put(key, new ByteArrayInputStream(data), data.length, ObjectMeta.of("image/png"));
        }

        private byte[] readAll(StoredObject object) throws IOException {
            try (InputStream in = object.stream()) {
                return in.readAllBytes();
            }
        }

        @Test
        @DisplayName("写入后整读，字节完全一致")
        void putThenOpen() throws IOException {
            put(KEY, DATA);
            StoredObject object = storage().open(KEY);
            assertThat(readAll(object)).isEqualTo(DATA);
            assertThat(object.length()).isEqualTo(DATA.length);
            assertThat(object.totalLength()).isEqualTo(DATA.length);
            assertThat(object.isPartial()).isFalse();
        }

        @Test
        @DisplayName("读不存在的对象 → NotFound，不是别的什么异常")
        void openMissing() {
            assertThatThrownBy(() -> storage().open(new StorageKey("nope/missing.png")))
                    .isInstanceOf(StorageException.class)
                    .satisfies(e -> assertThat(((StorageException) e).failure())
                            .isInstanceOf(StorageFailure.NotFound.class));
        }

        @Test
        @DisplayName("exists 的真假")
        void exists() {
            assertThat(storage().exists(KEY)).isFalse();
            put(KEY, DATA);
            assertThat(storage().exists(KEY)).isTrue();
        }

        @Test
        @DisplayName("删除后不存在")
        void delete() {
            put(KEY, DATA);
            storage().delete(KEY);
            assertThat(storage().exists(KEY)).isFalse();
        }

        @Test
        @DisplayName("删除不存在的对象不算失败 —— 删除必须幂等，否则每条重试路径都要先判存在性")
        void deleteIsIdempotent() {
            assertThatCode(() -> storage().delete(new StorageKey("nope/missing.png")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("同 key 覆盖是允许的：存储层禁止覆盖会让失败重传变成死路")
        void overwriteAllowed() throws IOException {
            put(KEY, DATA);
            byte[] replaced = "XY".getBytes(StandardCharsets.UTF_8);
            put(KEY, replaced);
            assertThat(readAll(storage().open(KEY))).isEqualTo(replaced);
        }

        @Test
        @DisplayName("Range：取中间一段，totalLength 仍是对象总长")
        void rangeMiddle() throws IOException {
            put(KEY, DATA);
            StoredObject object = storage().open(KEY, ByteRange.of(4, 7));
            assertThat(readAll(object)).isEqualTo("4567".getBytes(StandardCharsets.UTF_8));
            assertThat(object.length()).isEqualTo(4);
            assertThat(object.totalLength()).isEqualTo(DATA.length);
            assertThat(object.isPartial()).isTrue();
        }

        @Test
        @DisplayName("Range：从某处到末尾")
        void rangeToEnd() throws IOException {
            put(KEY, DATA);
            StoredObject object = storage().open(KEY, ByteRange.from(10));
            assertThat(readAll(object)).isEqualTo("ABCDEF".getBytes(StandardCharsets.UTF_8));
            assertThat(object.totalLength()).isEqualTo(DATA.length);
        }

        @Test
        @DisplayName("Range：结束位置超出对象长度时按实际长度截断，不报错")
        void rangeBeyondEof() throws IOException {
            put(KEY, DATA);
            StoredObject object = storage().open(KEY, ByteRange.of(12, 9999));
            assertThat(readAll(object)).isEqualTo("CDEF".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("Range：起点整个落在对象之外 → 长度 0（调用方据此回 416）")
        void rangeStartBeyondEof() throws IOException {
            put(KEY, DATA);
            StoredObject object = storage().open(KEY, ByteRange.from(DATA.length + 5));
            assertThat(object.length()).isZero();
            assertThat(readAll(object)).isEmpty();
        }

        @Test
        @DisplayName("批量删除")
        void deleteAll() {
            StorageKey a = new StorageKey("x/a.png");
            StorageKey b = new StorageKey("x/b.png");
            put(a, DATA);
            put(b, DATA);
            storage().deleteAll(List.of(a, b));
            assertThat(storage().exists(a)).isFalse();
            assertThat(storage().exists(b)).isFalse();
        }

        @Test
        @DisplayName("多级目录的 key 能写进去（本地实现要自己建父目录）")
        void nestedKey() throws IOException {
            StorageKey deep = new StorageKey("a/b/c/d/e/f.png");
            put(deep, DATA);
            assertThat(readAll(storage().open(deep))).isEqualTo(DATA);
        }
    }

    @Nested
    @DisplayName("InMemoryObjectStorage")
    class InMemory extends Contract {

        private final InMemoryObjectStorage storage = new InMemoryObjectStorage();

        @Override
        ObjectStorage storage() {
            return storage;
        }
    }

    @Nested
    @DisplayName("LocalFileStorage")
    class Local extends Contract {

        @TempDir
        Path root;

        private LocalFileStorage storage;

        @BeforeEach
        void setUp() {
            storage = new LocalFileStorage(root);
        }

        @Override
        ObjectStorage storage() {
            return storage;
        }

        @Test
        @DisplayName("落盘位置在根目录之内，且目录结构与 key 一致")
        void writesUnderRoot() {
            StorageKey key = new StorageKey("banner/202608/09/x.png");
            storage.put(key, new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, ObjectMeta.of("image/png"));
            assertThat(root.resolve("banner/202608/09/x.png")).exists();
        }
    }
}
