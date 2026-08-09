package net.lab1024.sa.base.module.support.file.service;

import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.module.support.file.constant.FileStatusEnum;
import net.lab1024.sa.base.module.support.file.constant.FileVisibilityEnum;
import net.lab1024.sa.base.module.support.file.dao.FileCategoryDao;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.entity.FileCategoryEntity;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.storage.ByteRange;
import net.lab1024.sa.base.storage.ObjectMeta;
import net.lab1024.sa.base.storage.ObjectStorage;
import net.lab1024.sa.base.storage.StorageKey;
import net.lab1024.sa.base.storage.StorageKind;
import net.lab1024.sa.base.storage.StoredObject;
import net.lab1024.sa.base.storage.impl.InMemoryObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileAssetService} 的校验三道关与写入顺序。
 *
 * <p>全程用 {@link InMemoryObjectStorage} + mock 的 DAO，不碰磁盘、不碰数据库、不碰网络。
 * 这正是当初把存储层抽象成 {@link ObjectStorage} 的目的 ——
 * 旧的 {@code IFileStorageService} 入参是 {@code MultipartFile}、返回 {@code ResponseDTO}，
 * 这类测试根本写不出来。
 *
 * @Date 2026-08-10
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileAssetServiceTest {

    /**
     * 1×1 的合法 PNG。用真图而不是随手几个字节，因为要同时喂给 Tika 嗅探和 ImageIO 读尺寸。
     */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Mock(answer = Answers.RETURNS_DEFAULTS)
    private FileDao fileDao;

    @Mock
    private FileCategoryDao fileCategoryDao;

    @Mock
    private FileRelationDao fileRelationDao;

    private InMemoryObjectStorage storage;

    private FileAssetService service;

    @BeforeEach
    void setUp() {
        storage = new InMemoryObjectStorage();
        service = new FileAssetService();
        ReflectionTestUtils.setField(service, "objectStorage", storage);
        ReflectionTestUtils.setField(service, "fileDao", fileDao);
        ReflectionTestUtils.setField(service, "fileCategoryDao", fileCategoryDao);
        ReflectionTestUtils.setField(service, "fileRelationDao", fileRelationDao);
        ReflectionTestUtils.setField(service, "maxFileSizeKb", 10240L);

        FileCategoryEntity category = new FileCategoryEntity();
        category.setCategoryId(1L);
        category.setCategoryCode("BANNER");
        when(fileCategoryDao.getByCode("BANNER")).thenReturn(category);

        // insert 时回填自增主键，模拟 MyBatis-Plus 的行为
        doAnswer(inv -> {
            inv.getArgument(0, FileEntity.class).setFileId(1001L);
            return 1;
        }).when(fileDao).insert(any(FileEntity.class));
    }

    private static MockMultipartFile png(String filename) {
        return new MockMultipartFile("file", filename, "image/png", PNG);
    }

    @Test
    @DisplayName("扩展名从嗅探MIME反推：内容是PNG但命名 evil.html，也存成 .png")
    void extensionComesFromSniffedMime() {
        FileEntity saved = service.upload(png("evil.html"), "BANNER", null);

        assertThat(saved.getExtension()).isEqualTo("png");
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getStorageKey()).endsWith(".png");
        // 原名照样完整保留，只是绝不参与 key 的构造
        assertThat(saved.getOriginalName()).isEqualTo("evil.html");
        assertThat(saved.getStorageKey()).doesNotContain("evil");
    }

    @Test
    @DisplayName("storageKey 形如 {code}/{yyyyMM}/{dd}/{id}.{ext}，且不含任何用户输入")
    void storageKeyShape() {
        FileEntity saved = service.upload(png("年会 合影;v2.png"), "BANNER", null);
        assertThat(saved.getStorageKey()).matches("banner/\\d{6}/\\d{2}/[0-9a-z]{16}\\.png");
    }

    @Test
    @DisplayName("第一关 大小：超限被拒，且一个字节都没写进存储")
    void rejectsOversize() {
        ReflectionTestUtils.setField(service, "maxFileSizeKb", 1L);
        MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png", new byte[2048]);

        assertThatThrownBy(() -> service.upload(big, "BANNER", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最大");
        assertThat(storage.size()).isZero();
        // any(FileEntity.class) 不能省：BaseMapper 同时有 insert(T) 和 insert(Collection<T>)，
        // 裸 any() 会在两者之间产生歧义编译不过
        verify(fileDao, never()).insert(any(FileEntity.class));
    }

    @Test
    @DisplayName("第二关 真实类型：内容不在白名单就拒，哪怕文件名和 Content-Type 都伪装成图片")
    void rejectsDisallowedRealType() {
        MockMultipartFile fake = new MockMultipartFile("file", "photo.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(fake, "BANNER", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("禁止上传");
        assertThat(storage.size()).isZero();
    }

    @Test
    @DisplayName("上传成功：字节确实进了存储，状态是 TEMP 而不是已确认")
    void uploadWritesBytesAndIsTemp() {
        FileEntity saved = service.upload(png("a.png"), "BANNER", null);

        assertThat(storage.exists(new StorageKey(saved.getStorageKey()))).isTrue();
        assertThat(saved.getStatus()).isEqualTo(FileStatusEnum.TEMP.getValue());
        assertThat(saved.getFileSize()).isEqualTo(PNG.length);
        assertThat(saved.getStorageKind()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("图片尺寸被读出来（走 ImageReader 读头，不解码整张图）")
    void readsImageSize() {
        FileEntity saved = service.upload(png("a.png"), "BANNER", null);
        assertThat(saved.getImageWidth()).isEqualTo(1);
        assertThat(saved.getImageHeight()).isEqualTo(1);
    }

    @Test
    @DisplayName("写存储失败时回滚那条记录，不留下指向不存在对象的脏数据")
    void deletesRecordWhenStorageFails() {
        // InMemoryObjectStorage 是 final（防止被当成生产实现继承改写），所以直接实现接口
        ObjectStorage failing = new ObjectStorage() {
            @Override
            public void put(StorageKey key, InputStream in, long length, ObjectMeta meta) {
                throw new IllegalStateException("模拟对象存储不可用");
            }

            @Override
            public StoredObject open(StorageKey key, ByteRange range) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean exists(StorageKey key) {
                return false;
            }

            @Override
            public void delete(StorageKey key) {
                // no-op
            }

            @Override
            public StorageKind kind() {
                return StorageKind.LOCAL;
            }
        };
        ReflectionTestUtils.setField(service, "objectStorage", failing);

        assertThatThrownBy(() -> service.upload(png("a.png"), "BANNER", null))
                .isInstanceOf(BusinessException.class);
        verify(fileDao).deleteById(1001L);
    }

    @Test
    @DisplayName("批量取URL只查一次库 —— 旧路径批量查完又在循环里逐个查 Redis+DB+算签名")
    void batchUrlQueriesOnce() {
        ReflectionTestUtils.setField(service, "publicUrlPrefix", "https://cdn.example.com");
        when(fileDao.selectByIds(any())).thenReturn(List.of(
                file(1L, "banner/202608/10/a.png", FileVisibilityEnum.PUBLIC),
                file(2L, "banner/202608/10/b.png", FileVisibilityEnum.PRIVATE)));

        Map<Long, String> urls = service.batchUrl(List.of(1L, 2L));

        assertThat(urls.get(1L)).isEqualTo("https://cdn.example.com/banner/202608/10/a.png");
        // 私有文件不给静态 URL，走后端下载接口（走登录态鉴权）
        assertThat(urls.get(2L)).isEqualTo("/file/download/2");
        verify(fileDao, org.mockito.Mockito.times(1)).selectByIds(any());
    }

    @Test
    @DisplayName("没配 publicUrlPrefix 时公开文件也走后端接口 —— 保守默认，不猜前缀")
    void fallsBackToBackendWhenPrefixMissing() {
        when(fileDao.selectByIds(any())).thenReturn(List.of(
                file(1L, "banner/202608/10/a.png", FileVisibilityEnum.PUBLIC)));

        assertThat(service.batchUrl(List.of(1L)).get(1L)).isEqualTo("/file/download/1");
    }

    private static FileEntity file(Long id, String key, FileVisibilityEnum visibility) {
        FileEntity entity = new FileEntity();
        entity.setFileId(id);
        entity.setStorageKey(key);
        entity.setVisibility(visibility.getValue());
        return entity;
    }

    @Test
    @DisplayName("分类不存在直接报错，而不是拿一个空 code 去拼 key")
    void rejectsUnknownCategory() {
        assertThatThrownBy(() -> service.upload(png("a.png"), "NOT_EXIST", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类不存在");
    }
}
