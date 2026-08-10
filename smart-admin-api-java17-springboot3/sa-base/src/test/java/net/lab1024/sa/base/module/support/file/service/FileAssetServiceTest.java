package net.lab1024.sa.base.module.support.file.service;

import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.module.support.file.constant.FileStatusEnum;
import net.lab1024.sa.base.module.support.file.config.FileImageProperties;
import net.lab1024.sa.base.module.support.file.constant.FileVisibilityEnum;
import net.lab1024.sa.base.module.support.file.domain.ImageVariant;
import net.lab1024.sa.base.module.support.file.dao.FileCategoryDao;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.entity.FileCategoryEntity;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.module.support.file.domain.entity.FileRelationEntity;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
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

    private FileImageProperties imageProperties;

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
        imageProperties = new FileImageProperties();
        ReflectionTestUtils.setField(service, "imageProperties", imageProperties);

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
    @DisplayName("可见性由分类决定：分类没配就按私有算，绝不默认公开")
    void visibilityComesFromCategory() {
        // BANNER 这个 fixture 没设 defaultVisibility，等价于「加分类的人没想过这件事」
        assertThat(service.upload(png("a.png"), "BANNER", null).getVisibility())
                .isEqualTo(FileVisibilityEnum.PRIVATE.getValue());

        FileCategoryEntity publicCategory = new FileCategoryEntity();
        publicCategory.setCategoryId(9L);
        publicCategory.setCategoryCode("ACTIVITY");
        publicCategory.setDefaultVisibility(FileVisibilityEnum.PUBLIC.getValue());
        when(fileCategoryDao.getByCode("ACTIVITY")).thenReturn(publicCategory);

        assertThat(service.upload(png("b.png"), "ACTIVITY", null).getVisibility())
                .isEqualTo(FileVisibilityEnum.PUBLIC.getValue());
    }

    @Test
    @DisplayName("confirm 收到空集合＝这个业务对象一张图都不引用了，必须照样清掉旧关系")
    void confirmWithEmptyListStillClearsRelations() {
        // 原先这里是 if (empty) return;，于是「把最后一张图移除后保存」永远解除不掉引用，
        // 那张图从此删不掉（守卫说"正被 1 处业务引用"），而实际上没有人在用它。
        // 3 张变 2 张是对的、1 张变 0 张是错的 —— 差一个元素，行为完全相反
        service.confirm(List.of(), "ACTIVITY_DISPLAY", 20L);

        verify(fileRelationDao).deleteByBiz("ACTIVITY_DISPLAY", 20L);
        verify(fileRelationDao, org.mockito.Mockito.never()).insert(any(FileRelationEntity.class));
        // 空集合不该去查文件表
        verify(fileDao, org.mockito.Mockito.never()).selectByIds(any());
    }

    @Test
    @DisplayName("免登录读取口只认公开文件：私有文件返回 null（调用方给 404，不给 403）")
    void publicLookupRejectsPrivateFile() {
        FileEntity privateFile = file(1L, "feedback/202608/10/a.jpg", FileVisibilityEnum.PRIVATE);
        when(fileDao.selectOne(any())).thenReturn(privateFile);
        assertThat(service.findPublicByStorageKey("feedback/202608/10/a.jpg")).isNull();

        FileEntity publicFile = file(2L, "activity/202608/10/b.jpg", FileVisibilityEnum.PUBLIC);
        when(fileDao.selectOne(any())).thenReturn(publicFile);
        assertThat(service.findPublicByStorageKey("activity/202608/10/b.jpg")).isSameAs(publicFile);
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
        // 私有文件不给静态 URL，走后端下载接口（走登录态鉴权）。
        // 路径必须带 /support —— 控制器挂在 SupportBaseController 下，少一段就是个 404
        assertThat(urls.get(2L)).isEqualTo("/support/file/download/2");
        verify(fileDao, org.mockito.Mockito.times(1)).selectByIds(any());
    }

    @Test
    @DisplayName("没配 publicUrlPrefix 时公开文件也走后端接口 —— 保守默认，不猜前缀")
    void fallsBackToBackendWhenPrefixMissing() {
        when(fileDao.selectByIds(any())).thenReturn(List.of(
                file(1L, "banner/202608/10/a.png", FileVisibilityEnum.PUBLIC)));

        assertThat(service.batchUrl(List.of(1L)).get(1L)).isEqualTo("/support/file/download/1");
    }

    @Test
    @DisplayName("分类级尺寸约束：banner 必须 1920×640，不合规在上传时就拦")
    void rejectsWrongImageSize() {
        FileImageProperties.Rule rule = new FileImageProperties.Rule();
        rule.setWidth(1920);
        rule.setHeight(640);
        imageProperties.getRules().put("BANNER", rule);

        assertThatThrownBy(() -> service.upload(png("a.png"), "BANNER", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宽度为 1920px");
        assertThat(storage.size()).isZero();
    }

    @Test
    @DisplayName("宽高比用交叉相乘比较：16:9 的 1920×1080 不能因为浮点误差被判不等")
    void ratioUsesCrossMultiplication() {
        FileImageProperties.Rule rule = new FileImageProperties.Rule();
        rule.setRatio("1:1");
        imageProperties.getRules().put("BANNER", rule);
        // 1×1 的图正好是 1:1，应当通过
        assertThat(service.upload(png("a.png"), "BANNER", null)).isNotNull();

        rule.setRatio("16:9");
        assertThatThrownBy(() -> service.upload(png("b.png"), "BANNER", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宽高比");
    }

    @Test
    @DisplayName("分类级大小上限比全局更严时以它为准")
    void categoryLevelSizeLimit() {
        FileImageProperties.Rule rule = new FileImageProperties.Rule();
        rule.setMaxSizeKb(0);
        imageProperties.getRules().put("BANNER", rule);

        assertThatThrownBy(() -> service.upload(png("a.png"), "BANNER", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类的文件最大为");
    }

    @Test
    @DisplayName("没配处理模板时变体一律返回原图URL —— 通用S3没有图片处理能力，硬拼参数只会换来400")
    void variantWithoutTemplateReturnsOriginal() {
        ReflectionTestUtils.setField(service, "publicUrlPrefix", "https://cdn.example.com");
        FileEntity image = file(1L, "banner/202608/10/a.png", FileVisibilityEnum.PUBLIC);
        image.setContentType("image/png");
        when(fileDao.selectByIds(any())).thenReturn(List.of(image));

        assertThat(service.batchUrl(List.of(1L), ImageVariant.THUMBNAIL).get(1L))
                .isEqualTo("https://cdn.example.com/banner/202608/10/a.png");
    }

    @Test
    @DisplayName("配了模板才拼处理参数，且占位符被替换")
    void variantAppliesTemplate() {
        ReflectionTestUtils.setField(service, "publicUrlPrefix", "https://cdn.example.com");
        imageProperties.setProcessTemplate("?x-oss-process=image/resize,w_{w}");
        FileEntity image = file(1L, "banner/202608/10/a.png", FileVisibilityEnum.PUBLIC);
        image.setContentType("image/png");
        when(fileDao.selectByIds(any())).thenReturn(List.of(image));

        assertThat(service.batchUrl(List.of(1L), ImageVariant.THUMBNAIL).get(1L))
                .isEqualTo("https://cdn.example.com/banner/202608/10/a.png?x-oss-process=image/resize,w_200");
        // 非图片不该被加上图片处理参数
        image.setContentType("application/pdf");
        assertThat(service.batchUrl(List.of(1L), ImageVariant.THUMBNAIL).get(1L))
                .doesNotContain("x-oss-process");
    }

    @Test
    @DisplayName("URL 反查 fileId：新老两种下载路径都要认，直接取 ID 不查库")
    void resolveFileIdsFromDownloadPath() {
        assertThat(service.resolveFileIds(List.of(
                // 修 /support 前缀之前生成的形态，已经写进历史富文本正文，必须继续认
                "/file/download/123",
                "https://admin.example.com/file/download/456?inline=true",
                // 现在生成的形态
                "/support/file/download/789")))
                .containsExactly(123L, 456L, 789L);
        // 走的是路径解析，一次库都不用查
        verify(fileDao, org.mockito.Mockito.never()).selectByFileKeyList(any());
    }

    @Test
    @DisplayName("URL 反查 fileId：CDN 地址取末尾路径段当 storageKey，查询参数不干扰")
    void resolveFileIdsFromCdnUrl() {
        when(fileDao.selectByFileKeyList(any())).thenReturn(List.of(
                vo(7L, "banner/202608/10/abc.png")));

        assertThat(service.resolveFileIds(List.of(
                "https://cdn.example.com/banner/202608/10/abc.png?x-oss-process=image/resize,w_200")))
                .containsExactly(7L);
    }

    @Test
    @DisplayName("查不到的 URL 静默跳过 —— 外链图片和已删文件都是正常情况")
    void resolveFileIdsIgnoresUnknown() {
        when(fileDao.selectByFileKeyList(any())).thenReturn(List.of());
        assertThat(service.resolveFileIds(List.of("https://other-site.com/x/y/z/foo.png"))).isEmpty();
        assertThat(service.resolveFileIds(List.of())).isEmpty();
        assertThat(service.resolveFileIds(null)).isEmpty();
    }

    @Test
    @DisplayName("标签拼成前后带逗号的形式 —— 少了这两个逗号，搜「618」会命中「6180」")
    void normalizeTags() {
        assertThat(FileAssetService.normalizeTags(List.of("双十一", "banner")))
                .isEqualTo(",双十一,banner,");
        // 含逗号的标签会破坏存储结构，直接丢弃而不是"尽力转义"
        assertThat(FileAssetService.normalizeTags(List.of("a,b", "ok", "全角，逗号")))
                .isEqualTo(",ok,");
        assertThat(FileAssetService.normalizeTags(List.of("  x  ", "x"))).isEqualTo(",x,");
        assertThat(FileAssetService.normalizeTags(List.of())).isNull();
        assertThat(FileAssetService.normalizeTags(List.of("  "))).isNull();
    }

    @Test
    @DisplayName("有引用的文件不能删，且字节一个都没动")
    void deleteRejectsReferencedFile() {
        FileEntity entity = file(5L, "banner/202608/10/a.png", FileVisibilityEnum.PUBLIC);
        when(fileDao.selectById(5L)).thenReturn(entity);
        FileRelationEntity relation = new FileRelationEntity();
        relation.setFileId(5L);
        relation.setBizType("ACTIVITY_DISPLAY");
        when(fileRelationDao.listByFileIds(any())).thenReturn(List.of(relation));

        assertThatThrownBy(() -> service.delete(5L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正被 1 处业务引用");
        verify(fileDao, never()).updateById(any(FileEntity.class));
    }

    @Test
    @DisplayName("无引用可删：先标记再删对象 —— DB 事务回滚得了，删掉的字节回滚不了")
    void deleteRemovesObjectWhenUnreferenced() {
        FileEntity saved = service.upload(png("a.png"), "BANNER", null);
        StorageKey key = new StorageKey(saved.getStorageKey());
        when(fileDao.selectById(1001L)).thenReturn(saved);
        when(fileRelationDao.listByFileIds(any())).thenReturn(List.of());

        service.delete(1001L, null);
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    @DisplayName("改名打标签不碰 storageKey —— key 不可变是 CDN 能设 immutable 的前提")
    void updateMetaNeverTouchesStorageKey() {
        when(fileDao.selectById(9L)).thenReturn(file(9L, "banner/202608/10/a.png", FileVisibilityEnum.PUBLIC));

        service.updateMeta(9L, "新名字.png", List.of("双十一"), null);

        org.mockito.ArgumentCaptor<FileEntity> captor = org.mockito.ArgumentCaptor.forClass(FileEntity.class);
        verify(fileDao).updateById(captor.capture());
        assertThat(captor.getValue().getStorageKey()).isNull();
        assertThat(captor.getValue().getOriginalName()).isEqualTo("新名字.png");
        assertThat(captor.getValue().getTags()).isEqualTo(",双十一,");
    }

    private static FileVO vo(Long id, String key) {
        FileVO v = new FileVO();
        v.setFileId(id);
        v.setStorageKey(key);
        return v;
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
