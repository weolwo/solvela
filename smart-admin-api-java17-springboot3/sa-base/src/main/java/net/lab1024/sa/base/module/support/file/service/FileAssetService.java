package net.lab1024.sa.base.module.support.file.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.module.support.file.constant.FileStatusEnum;
import net.lab1024.sa.base.module.support.file.constant.FileVisibilityEnum;
import net.lab1024.sa.base.module.support.file.dao.FileCategoryDao;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.entity.FileCategoryEntity;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.module.support.file.domain.entity.FileRelationEntity;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityFileService;
import net.lab1024.sa.base.storage.ObjectMeta;
import net.lab1024.sa.base.storage.ObjectStorage;
import net.lab1024.sa.base.storage.StorageKey;
import net.lab1024.sa.base.storage.StorageKeyGenerator;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 素材库服务：文件的记账与生命周期。
 *
 * <p>与旧的 {@code FileService} <b>并存</b>，档⑤ 统一迁移调用方后删除旧的。
 * 命名用 Asset 而不是 Storage/Store，是为了和 {@code IFileStorageService} 彻底区分开 ——
 * 那个是即将被删的存储实现接口，这个是业务层。
 *
 * <p><b>本类不认识对象存储 SDK</b>，只通过 {@link ObjectStorage} 打交道；
 * <b>也不认识 HTTP 响应结构</b>，失败一律抛 {@link BusinessException}，
 * 翻译成 {@code ResponseDTO} 只在 Controller 层发生一次。
 *
 * @Date 2026-08-10
 */
@Slf4j
@Service
public class FileAssetService {

    /**
     * 允许上传的真实 MIME 白名单。<b>始终生效，不挂在任何开关后面</b> ——
     * 旧的 {@code SecurityFileService#checkFile} 把类型检测放在 {@code isFileDetectFlag()} 之后，
     * 关掉开关就等于没有这道关。
     *
     * <p>刻意不含 {@code image/svg+xml}：SVG 可以内嵌脚本，是公开可访问的素材库里最危险的格式。
     * 也不含任何 {@code text/*}：运营素材不需要，而 {@code text/html} 一旦被存成 {@code .html}
     * 并从静态目录暴露出去就是存储型 XSS。
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp",
            "application/pdf", "application/zip",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp");

    @Resource
    private ObjectStorage objectStorage;

    @Resource
    private FileDao fileDao;

    @Resource
    private FileCategoryDao fileCategoryDao;

    @Resource
    private FileRelationDao fileRelationDao;

    private final StorageKeyGenerator keyGenerator = new StorageKeyGenerator();

    /**
     * 单文件上限。容器的 {@code max-file-size} 是第一道（超了根本进不来），这里是第二道，
     * 且为将来的分类级配额留位置。
     */
    @Value("${file.storage.max-file-size-kb:10240}")
    private long maxFileSizeKb;

    // ------------------------------------------------------------------ 上传

    /**
     * 上传。产出的记录是 {@link FileStatusEnum#TEMP}，业务确认引用后才转 CONFIRMED。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：这个方法既写库又写对象存储，而对象存储不参与
     * 数据库事务。加了事务只会制造一种错觉 —— 真正的一致性靠下面的写入顺序和 TEMP 状态保证。
     */
    public FileEntity upload(MultipartFile file, String categoryCode, RequestUser user) {
        FileCategoryEntity category = requireCategory(categoryCode);

        // ── 第一关：大小。必须在读任何字节之前，否则类型嗅探本身就成了攻击面
        long size = file.getSize();
        if (size <= 0) {
            throw new BusinessException("上传文件不能为空");
        }
        if (size > maxFileSizeKb * 1024) {
            throw new BusinessException("上传文件最大为 " + (maxFileSizeKb / 1024) + " MB");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("上传文件名称不能为空");
        }
        if (originalName.length() > 200) {
            throw new BusinessException("文件名称最大长度为 200");
        }

        // ── 第二关：真实类型。信内容不信扩展名、不信 Content-Type
        String contentType = SecurityFileService.getFileMimeType(file);
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new BusinessException("禁止上传此文件类型：" + contentType);
        }

        // ── 第三关：扩展名从 MIME 反推，不从用户文件名取。
        //    用户传 evil.html 而内容是 PNG 时存成 .png —— 这一条直接掐死存储型 XSS
        String extension = extensionOf(contentType);
        StorageKey storageKey = keyGenerator.generate(category.getCategoryCode(), extension);

        FileEntity entity = new FileEntity();
        entity.setCategoryId(category.getCategoryId());
        entity.setStorageKey(storageKey.value());
        entity.setStorageKind(objectStorage.kind().name());
        entity.setOriginalName(originalName);
        entity.setExtension(extension);
        entity.setContentType(contentType);
        entity.setFileSize(size);
        entity.setVisibility(FileVisibilityEnum.PUBLIC.getValue());
        entity.setStatus(FileStatusEnum.TEMP.getValue());
        entity.setDeletedFlag(0);
        entity.setCreateBy(user == null ? null : user.getUserName());
        if (IMAGE_MIME_TYPES.contains(contentType)) {
            readImageSize(file, entity);
        }

        // 先落库再写对象存储。反过来的话，写完字节而落库失败会留下一份「没有任何记录指向它」
        // 的孤儿字节 —— 清理任务扫的是库，永远发现不了它。
        // 反之留下一条指向不存在对象的 TEMP 记录是无害的：到期清理会删掉它，
        // 而删除一个不存在的对象本身是幂等的。
        fileDao.insert(entity);
        try (InputStream in = file.getInputStream()) {
            objectStorage.put(storageKey, in, size, ObjectMeta.of(contentType));
        } catch (IOException | RuntimeException e) {
            fileDao.deleteById(entity.getFileId());
            throw new BusinessException("文件上传失败：" + e.getMessage());
        }
        return entity;
    }

    // ------------------------------------------------------------------ 生命周期

    /**
     * 业务确认引用：TEMP → CONFIRMED，并建立引用关系。
     *
     * <p>没有这一步的话，「用户在表单里选了文件然后关掉页面没提交」产生的文件永远没人引用、
     * 也永远不会被删。跑几年后存储里一半是垃圾，而且<b>你无法区分哪些是垃圾</b>。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(List<Long> fileIds, String bizType, Long bizId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        List<FileEntity> files = fileDao.selectByIds(fileIds);
        if (files.size() != fileIds.size()) {
            throw new BusinessException("部分文件不存在，无法确认引用");
        }
        for (FileEntity file : files) {
            if (!FileStatusEnum.CONFIRMED.equalsValue(file.getStatus())) {
                FileEntity update = new FileEntity();
                update.setFileId(file.getFileId());
                update.setStatus(FileStatusEnum.CONFIRMED.getValue());
                fileDao.updateById(update);
            }
        }
        // 先清后建，让 confirm 幂等：同一个业务对象重复提交表单不会累积出重复关系，
        // 也能正确反映「这次去掉了某个附件」
        fileRelationDao.deleteByBiz(bizType, bizId);
        List<FileRelationEntity> relations = new ArrayList<>(fileIds.size());
        for (int i = 0; i < fileIds.size(); i++) {
            FileRelationEntity relation = new FileRelationEntity();
            relation.setFileId(fileIds.get(i));
            relation.setBizType(bizType);
            relation.setBizId(bizId);
            // 顺序取自入参顺序 —— N 次并发上传的返回顺序是乱的，轮播图必须靠这个
            relation.setSort(i);
            relations.add(relation);
        }
        relations.forEach(fileRelationDao::insert);
    }

    /**
     * 业务对象被删除时解除其全部引用。<b>只删关系不删文件</b> ——
     * 文件可能被别处引用着，是否物理删除由引用计数说了算。
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseRelation(String bizType, Long bizId) {
        fileRelationDao.deleteByBiz(bizType, bizId);
    }

    /**
     * 这些文件里，哪些已经没有任何业务在引用。给清理任务用。
     */
    public List<Long> findUnreferenced(Collection<Long> fileIds) {
        Set<Long> referenced = fileRelationDao.listByFileIds(fileIds).stream()
                .map(FileRelationEntity::getFileId)
                .collect(java.util.stream.Collectors.toSet());
        return fileIds.stream().filter(id -> !referenced.contains(id)).toList();
    }

    // ------------------------------------------------------------------

    private FileCategoryEntity requireCategory(String categoryCode) {
        FileCategoryEntity category = fileCategoryDao.getByCode(categoryCode);
        if (category == null) {
            throw new BusinessException("文件分类不存在：" + categoryCode);
        }
        return category;
    }

    /**
     * MIME → 扩展名。走 Tika 自带的映射表，不再手写 if 链
     * （旧的 {@code IFileStorageService#getContentType} 那张手写表把 docx 映射成了
     * {@code application/msword}，而且兜底返回空串）。
     */
    private static String extensionOf(String contentType) {
        try {
            String ext = MimeTypes.getDefaultMimeTypes().forName(contentType).getExtension();
            return ext == null || ext.isEmpty() ? "" : ext.substring(1);
        } catch (MimeTypeException e) {
            log.warn("[File] 未知 MIME，无法反推扩展名：{}", contentType);
            return "";
        }
    }

    /**
     * 读图片宽高。
     *
     * <p><b>必须用 {@code ImageReader.getWidth(0)} 而不是 {@code ImageIO.read()}</b> ——
     * 后者会把整张图解码进堆，一张 6000×4000 的 JPEG 解码后约 96MB；前者只读文件头几十字节。
     *
     * <p>读不出来不是错误（格式不认识、文件损坏都可能），只记 debug 日志，宽高留 null。
     */
    private static void readImageSize(MultipartFile file, FileEntity entity) {
        try (InputStream in = file.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                return;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                entity.setImageWidth(reader.getWidth(0));
                entity.setImageHeight(reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log.debug("[File] 读取图片尺寸失败：{}", file.getOriginalFilename(), e);
        }
    }
}
