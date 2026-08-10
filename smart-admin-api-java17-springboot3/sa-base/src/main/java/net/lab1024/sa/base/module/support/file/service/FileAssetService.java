package net.lab1024.sa.base.module.support.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import net.lab1024.sa.base.common.constant.StringConst;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.module.support.file.constant.FileStatusEnum;
import net.lab1024.sa.base.module.support.file.config.FileImageProperties;
import net.lab1024.sa.base.module.support.file.dao.FileCategoryDao;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.ImageVariant;
import net.lab1024.sa.base.module.support.file.domain.entity.FileCategoryEntity;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.module.support.file.domain.entity.FileRelationEntity;
import net.lab1024.sa.base.module.support.file.domain.form.FileQueryForm;
import net.lab1024.sa.base.module.support.file.domain.vo.FileDetailVO;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityFileService;
import net.lab1024.sa.base.storage.ByteRange;
import net.lab1024.sa.base.storage.ObjectMeta;
import net.lab1024.sa.base.storage.ObjectStorage;
import net.lab1024.sa.base.storage.StorageKey;
import net.lab1024.sa.base.storage.StorageKeyGenerator;
import net.lab1024.sa.base.storage.StoredObject;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    @Resource
    private FileImageProperties imageProperties;

    private final StorageKeyGenerator keyGenerator = new StorageKeyGenerator();

    /**
     * 单文件上限。容器的 {@code max-file-size} 是第一道（超了根本进不来），这里是第二道，
     * 且为将来的分类级配额留位置。
     */
    @Value("${file.storage.max-file-size-kb:10240}")
    private long maxFileSizeKb;

    /**
     * 对外 URL 前缀（免登录读取口 / 对象存储 / CDN 域名）。<b>不配则一律走后端下载接口</b> ——
     * 猜一个前缀拼出打不开的 URL，比多走一跳后端难排查得多。
     *
     * <p>本模块<b>没有「私有文件」这个概念</b>（v3.56.0 起）：它服务的是活动/任务配置，
     * 运营传的每一张图最终都要给匿名的 C 端用户看，「公开」是唯一有意义的状态。
     * 将来若开放 C 端用户上传，那是独立的一条链路 —— 要防的是上传本身（配额/审核），
     * 不是给文件挂一个可见性标记，两件事塞进一张表只会两边都做不干净。
     */
    @Value("${file.storage.public-url-prefix:}")
    private String publicUrlPrefix = "";

    /**
     * 后端流式下载接口，见 {@code FileController#download}。
     *
     * <p>🔴 <b>必须带 {@code /support} 前缀</b>：{@code FileController} 继承
     * {@code SupportBaseController}，类级 {@code @RequestMapping} 是 {@code /support}，
     * 真实路径是 {@code /support/file/download/{id}}。这里原先少了这一段，
     * 于是所有下发给前端的 {@code fileUrl} 都指向一个不存在的路径 ——
     * 素材库的缩略图、展示配置的预览图全是空的，而且不报错（{@code <img>} 加载失败是静默的）。
     */
    private static final String DOWNLOAD_PATH = "/support/file/download/";

    /**
     * 反查 fileId 时用来匹配的<b>短形态</b>，刻意与 {@link #DOWNLOAD_PATH} 分开。
     *
     * <p>匹配是 {@code indexOf} 子串查找，短形态同时命中新老两种 URL：
     * 修前生成的 {@code /file/download/12} 已经写进了历史富文本正文，
     * 若拿新的长前缀去匹配，这些引用会<b>静默失配</b> —— 保存活动规则时登记不上引用，
     * 图随后被孤儿清理任务删掉（设计文档红线 4）。
     */
    private static final String DOWNLOAD_PATH_MATCH = "/file/download/";

    // ------------------------------------------------------------------ 上传

    /**
     * 上传。产出的记录是 {@link FileStatusEnum#TEMP}，业务确认引用后才转 CONFIRMED。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：这个方法既写库又写对象存储，而对象存储不参与
     * 数据库事务。加了事务只会制造一种错觉 —— 真正的一致性靠下面的写入顺序和 TEMP 状态保证。
     */
    public FileEntity upload(MultipartFile file, String categoryCode, RequestUser user) {
        FileCategoryEntity category = requireCategory(categoryCode);
        FileImageProperties.Rule rule = imageProperties.ruleOf(category.getCategoryCode());

        // ── 第一关：大小。必须在读任何字节之前，否则类型嗅探本身就成了攻击面
        long size = file.getSize();
        if (size <= 0) {
            throw new BusinessException("上传文件不能为空");
        }
        if (size > maxFileSizeKb * 1024) {
            throw new BusinessException("上传文件最大为 " + (maxFileSizeKb / 1024) + " MB");
        }
        // 分类级上限比全局更严时以它为准
        if (rule != null && rule.getMaxSizeKb() != null && size > rule.getMaxSizeKb() * 1024L) {
            throw new BusinessException("「" + category.getCategoryName() + "」分类的文件最大为 "
                    + rule.getMaxSizeKb() + " KB");
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
        entity.setStatus(FileStatusEnum.TEMP.getValue());
        entity.setDeletedFlag(0);
        entity.setCreateBy(user == null ? null : user.getUserName());
        if (IMAGE_MIME_TYPES.contains(contentType)) {
            readImageSize(file, entity);
            // 尺寸不合规必须在上传时就拦下来。等运营把 banner 发布出去、页面变形了才发现，
            // 那时已经要走一遍下线-重传-重新发布的流程
            checkImageRule(entity, rule, category.getCategoryName());
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

    /**
     * 按分类 ID 上传。给旧的 {@code /file/upload?folder=N} 接口用 ——
     * 迁移脚本把内置分类的 ID 对齐成了原 {@code folderType} 的值，所以前端不用改。
     */
    public FileEntity upload(MultipartFile file, Long categoryId, RequestUser user) {
        FileCategoryEntity category = fileCategoryDao.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("文件分类不存在：" + categoryId);
        }
        return upload(file, category.getCategoryCode(), user);
    }

    // ------------------------------------------------------------------ 按 storageKey 查询

    /**
     * 按 storageKey 批量取文件信息。<b>一次查库</b>。
     *
     * <p>业务表里存的是 storageKey 字符串（如 {@code t_employee.avatar}、逗号拼接的
     * {@code attachment}），所以这条路径必须保留 —— 把它们全部改存 fileId 是另一次数据迁移。
     */
    public List<FileVO> listByStorageKeys(Collection<String> storageKeys) {
        if (storageKeys == null || storageKeys.isEmpty()) {
            return List.of();
        }
        List<FileVO> list = fileDao.selectByFileKeyList(new LinkedHashSet<>(storageKeys));
        Map<String, FileVO> byKey = list.stream()
                .collect(java.util.stream.Collectors.toMap(FileVO::getStorageKey, v -> v, (a, b) -> a));
        for (FileVO vo : list) {
            vo.setFileUrl(urlOfVo(vo));
        }
        // 按入参顺序返回，查不到的跳过。顺序有业务含义（附件展示顺序）
        List<FileVO> result = new ArrayList<>(storageKeys.size());
        for (String key : storageKeys) {
            FileVO vo = byKey.get(key);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 按 storageKey 取访问 URL，支持逗号分隔的多个 key。
     */
    public String urlByStorageKeys(String storageKeys) {
        if (storageKeys == null || storageKeys.isBlank()) {
            return "";
        }
        List<String> keys = Arrays.stream(storageKeys.split(StringConst.SEPARATOR))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .toList();
        return listByStorageKeys(keys).stream()
                .map(FileVO::getFileUrl)
                .collect(java.util.stream.Collectors.joining(StringConst.SEPARATOR));
    }

    /**
     * 把一批图片 URL 反查成 fileId。查不到的静默跳过（外链图片、已删除的文件都很正常）。
     *
     * <p><b>两种 URL 形态都要认</b>，因为它们都是本模块自己生成的：
     * <ul>
     *   <li>{@code /file/download/123} —— 私有文件或没配公开前缀时</li>
     *   <li>{@code https://cdn.x.com/banner/202608/10/abc.png?x-oss-process=...} —— 公开文件</li>
     * </ul>
     *
     * <p>从 URL 还原 storageKey 的做法是<b>取末尾若干段路径</b>而不是去剥前缀 ——
     * 前缀可能是 CDN 域名、可能带路径、可能配置改过，剥不干净；而 key 的段数是已知的
     * （新格式 4 段 {@code code/yyyyMM/dd/id.ext}，历史遗留 3 段 {@code private/common/xxx.png}），
     * 两种都试一次即可。宁可多试一次查询，也不能漏掉引用 —— 漏掉的后果是图被清理任务删掉。
     */
    public List<Long> resolveFileIds(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        Set<String> keyCandidates = new LinkedHashSet<>();
        for (String url : urls) {
            Long direct = fileIdFromDownloadPath(url);
            if (direct != null) {
                ids.add(direct);
                continue;
            }
            keyCandidates.addAll(storageKeyCandidates(url));
        }
        if (!keyCandidates.isEmpty()) {
            // 复用已有的 selectByFileKeyList 而不是再写一个 LambdaQueryWrapper：
            // 那条 XML 查询做的是同一件事、已经过滤了 deleted_flag，而且不依赖
            // MyBatis-Plus 的 TableInfo 缓存 —— 后者在没有 Spring 上下文的单测里根本不存在
            fileDao.selectByFileKeyList(keyCandidates).forEach(vo -> ids.add(vo.getFileId()));
        }
        return ids.stream().distinct().toList();
    }

    private static Long fileIdFromDownloadPath(String url) {
        int idx = url.indexOf(DOWNLOAD_PATH_MATCH);
        if (idx < 0) {
            return null;
        }
        String tail = url.substring(idx + DOWNLOAD_PATH_MATCH.length());
        int end = 0;
        while (end < tail.length() && Character.isDigit(tail.charAt(end))) {
            end++;
        }
        return end == 0 ? null : Long.valueOf(tail.substring(0, end));
    }

    private static Set<String> storageKeyCandidates(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int h = path.indexOf('#');
        if (h >= 0) {
            path = path.substring(0, h);
        }
        String[] segments = path.split("/");
        Set<String> candidates = new LinkedHashSet<>();
        for (int take : new int[]{4, 3}) {
            if (segments.length >= take) {
                candidates.add(String.join("/",
                        Arrays.copyOfRange(segments, segments.length - take, segments.length)));
            }
        }
        return candidates;
    }

    public FileEntity requireByStorageKey(String storageKey) {
        FileEntity entity = fileDao.selectOne(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getStorageKey, storageKey)
                .eq(FileEntity::getDeletedFlag, 0));
        if (entity == null) {
            throw new BusinessException("文件不存在");
        }
        return entity;
    }

    /**
     * 按 storageKey 找文件，<b>找不到返回 null 而不是抛异常</b>。
     *
     * <p>刻意与 {@link #requireByStorageKey} 分开：调用它的是免登录的读取口，
     * 那里要的是一个干净的 404，而 {@code BusinessException} 会被全局处理器翻译成
     * 200 + 业务错误码的 JSON —— 对一个 {@code <img>} 请求毫无意义。
     *
     * <p>这里<b>没有可见性判断</b>：本模块的文件一律公开（见 {@link #publicUrlPrefix} 的说明）。
     */
    public FileEntity findByStorageKey(String storageKey) {
        return fileDao.selectOne(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getStorageKey, storageKey)
                .eq(FileEntity::getDeletedFlag, 0));
    }

    public PageResult<FileVO> queryPage(FileQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<FileVO> list = fileDao.queryPage(page, queryForm);
        list.forEach(vo -> vo.setFileUrl(urlOfVo(vo)));
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * FileVO 走的是和 {@link #urlOf} 一样的规则。这里单独写一份是因为 VO 与 Entity
     * 没有共同父类，硬抽一个基类只会为了省十行代码引入一层继承。
     */
    private String urlOfVo(FileVO vo) {
        if (publicUrlPrefix.isBlank()) {
            return DOWNLOAD_PATH + vo.getFileId();
        }
        String prefix = publicUrlPrefix.endsWith("/") ? publicUrlPrefix : publicUrlPrefix + "/";
        return prefix + vo.getStorageKey();
    }

    // ------------------------------------------------------------------ 详情与维护

    /**
     * 文件详情 + <b>被谁引用着</b>。
     *
     * <p>引用列表是这个接口的主要价值：运营删图前看一眼「这张图正在 3 个活动里用」，
     * 比删完了才发现活动页变叉强得多。走 {@code idx_file} 索引。
     */
    public FileDetailVO detail(Long fileId) {
        FileEntity entity = requireFile(fileId);
        FileVO vo = toVo(entity);
        vo.setFileUrl(urlOf(entity, ImageVariant.ORIGINAL));

        List<FileDetailVO.Reference> references = fileRelationDao.listByFileIds(List.of(fileId)).stream()
                .map(r -> {
                    FileDetailVO.Reference ref = new FileDetailVO.Reference();
                    ref.setBizType(r.getBizType());
                    ref.setBizId(r.getBizId());
                    return ref;
                })
                .toList();

        FileDetailVO detail = new FileDetailVO();
        detail.setFile(vo);
        detail.setReferences(references);
        return detail;
    }

    /**
     * 改名 / 打标签。<b>只动展示层信息，storageKey 一个字符都不碰</b> ——
     * key 不可变是 CDN 能设 immutable、以及"换图不用刷缓存"的前提（设计文档 §7.6）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateMeta(Long fileId, String originalName, List<String> tags, RequestUser user) {
        FileEntity existing = requireFile(fileId);
        FileEntity update = new FileEntity();
        update.setFileId(existing.getFileId());
        if (originalName != null && !originalName.isBlank()) {
            if (originalName.length() > 200) {
                throw new BusinessException("文件名称最大长度为 200");
            }
            update.setOriginalName(originalName.trim());
        }
        if (tags != null) {
            update.setTags(normalizeTags(tags));
        }
        update.setUpdateBy(user == null ? null : user.getUserName());
        fileDao.updateById(update);
    }

    /**
     * 把标签列表拼成<b>前后各带一个逗号</b>的存储形式：{@code ,双十一,banner,}
     *
     * <p>这个形式不是为了好看，是为了让 {@code LIKE '%,618,%'} 能精确匹配 ——
     * 少了这两个逗号，搜「618」会命中「6180」、搜「11」会命中「双11」和「1111」，
     * 而且不报错，只是搜出一堆不相干的东西。
     *
     * <p>标签自身含逗号会破坏这个结构，所以直接丢弃而不是"尽力转义"。
     */
    static String normalizeTags(List<String> tags) {
        List<String> cleaned = tags.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(t -> !t.isEmpty() && t.indexOf(',') < 0 && t.indexOf('，') < 0)
                .distinct()
                .toList();
        return cleaned.isEmpty() ? null : "," + String.join(",", cleaned) + ",";
    }

    /**
     * 删除文件。<b>有任何业务在引用就拒绝</b>。
     *
     * <p>行标记为已删除，同时把对象从存储里真删掉。之所以不只做软删：
     * 孤儿清理任务还没落地（等定时任务模块重构后再做），只软删的话存储只增不减，
     * 而且没有任何机制会回来收它。<b>代价是删除不可恢复</b>，所以上面那道引用检查必须严。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long fileId, RequestUser user) {
        FileEntity existing = requireFile(fileId);
        List<FileRelationEntity> references = fileRelationDao.listByFileIds(List.of(fileId));
        if (!references.isEmpty()) {
            throw new BusinessException("该文件正被 " + references.size() + " 处业务引用，不能删除");
        }
        FileEntity update = new FileEntity();
        update.setFileId(fileId);
        update.setDeletedFlag(1);
        update.setUpdateBy(user == null ? null : user.getUserName());
        fileDao.updateById(update);
        // 放在最后：DB 事务回滚得了，删掉的字节回滚不了
        objectStorage.delete(new StorageKey(existing.getStorageKey()));
    }

    private FileVO toVo(FileEntity entity) {
        FileVO vo = new FileVO();
        vo.setFileId(entity.getFileId());
        vo.setCategoryId(entity.getCategoryId());
        vo.setOriginalName(entity.getOriginalName());
        vo.setStorageKey(entity.getStorageKey());
        vo.setStorageKind(entity.getStorageKind());
        vo.setExtension(entity.getExtension());
        vo.setContentType(entity.getContentType());
        vo.setFileSize(entity.getFileSize());
        vo.setStatus(entity.getStatus());
        vo.setTags(entity.getTags());
        vo.setCreateBy(entity.getCreateBy());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
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
        // 🔴 空集合不能提前返回。它的含义是「这个业务对象现在一张图都不引用了」，
        // 恰恰是最需要执行下面那句 deleteByBiz 的场景。
        // 原先在这里 return，导致「把最后一张图移除后保存」的引用永远解除不掉 ——
        // 实测：展示配置移除主视觉并保存，t_file_relation 里那行纹丝不动，
        // 于是那张图永远删不掉（删除守卫说"正被 1 处业务引用"），而实际上没有人在用它。
        // 3 张变 2 张是对的，1 张变 0 张是错的 —— 边界只差一个元素，行为完全相反。
        List<Long> ids = fileIds == null ? List.of() : fileIds;
        if (!ids.isEmpty()) {
            List<FileEntity> files = fileDao.selectByIds(ids);
            if (files.size() != ids.size()) {
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
        }
        // 先清后建，让 confirm 幂等：同一个业务对象重复提交表单不会累积出重复关系，
        // 也能正确反映「这次去掉了某个附件」（包括去掉最后一个）
        fileRelationDao.deleteByBiz(bizType, bizId);
        List<FileRelationEntity> relations = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            FileRelationEntity relation = new FileRelationEntity();
            relation.setFileId(ids.get(i));
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

    // ------------------------------------------------------------------ 读取

    /**
     * 取文件记录，不存在或已删除直接抛。
     */
    public FileEntity requireFile(Long fileId) {
        FileEntity entity = fileId == null ? null : fileDao.selectById(fileId);
        if (entity == null || (entity.getDeletedFlag() != null && entity.getDeletedFlag() == 1)) {
            throw new BusinessException("文件不存在");
        }
        return entity;
    }

    /**
     * 打开文件流。<b>调用方负责关闭</b>返回的 {@link StoredObject}。
     *
     * <p>返回流而不是 {@code byte[]} —— 旧的两个实现都是先全量读进堆再吐出去，
     * 名字叫"流式下载"，实际 100MB 文件就是 100MB 堆。
     */
    public StoredObject open(FileEntity file, ByteRange range) {
        return objectStorage.open(new StorageKey(file.getStorageKey()), range);
    }

    /**
     * 批量取访问 URL。<b>一次查库，没有 N+1。</b>
     *
     * <p>旧路径的 {@code FileService#getFileList} 已经批量查过库了，却又在循环里逐个调
     * {@code getFileUrl}，而云端那个实现每次还要查一次 Redis、缓存没命中再查一次 DB、
     * 再算一次 SigV4 签名 —— 100 个附件冷缓存就是 1 次批量查 + 100 次 Redis
     * + 100 次单条 DB + 100 次 HMAC。
     *
     * <p>URL 形态只由「配没配前缀」决定（v3.56.0 起没有可见性这个维度）：
     * <ul>
     *   <li>配了 → {@code publicUrlPrefix + storageKey}，可挂 CDN 且因为 key 不可变
     *       可以设 {@code immutable}</li>
     *   <li>没配 → 后端下载接口。这是刻意的保守默认：猜一个前缀拼出打不开的 URL，
     *       比多走一跳后端要难排查得多</li>
     * </ul>
     */
    public Map<Long, String> batchUrl(Collection<Long> fileIds) {
        return batchUrl(fileIds, ImageVariant.ORIGINAL);
    }

    public Map<Long, String> batchUrl(Collection<Long> fileIds, ImageVariant variant) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        List<FileEntity> files = fileDao.selectByIds(fileIds);
        Map<Long, String> urls = new LinkedHashMap<>(files.size());
        for (FileEntity file : files) {
            urls.put(file.getFileId(), urlOf(file, variant));
        }
        return urls;
    }

    public String url(FileEntity file, ImageVariant variant) {
        return urlOf(file, variant);
    }

    private String urlOf(FileEntity file, ImageVariant variant) {
        if (publicUrlPrefix.isBlank()) {
            // 前缀没配就走后端下载接口 —— 猜一个前缀拼出打不开的 URL，比多走一跳后端难排查得多
            return DOWNLOAD_PATH + file.getFileId();
        }
        String prefix = publicUrlPrefix.endsWith("/") ? publicUrlPrefix : publicUrlPrefix + "/";
        return prefix + file.getStorageKey() + processSuffix(file, variant);
    }

    /**
     * 云端图片处理参数。<b>没配模板就返回空串（原图）</b> ——
     * 通用 S3 协议没有图片处理能力，硬拼一个 {@code x-oss-process} 上去，
     * 在 MinIO / AWS S3 上只会换来一个 400。
     */
    private String processSuffix(FileEntity file, ImageVariant variant) {
        String template = imageProperties.getProcessTemplate();
        if (variant == null || variant.isOriginal()
                || template == null || template.isBlank()
                || !IMAGE_MIME_TYPES.contains(file.getContentType())) {
            return "";
        }
        return template.replace("{w}", String.valueOf(variant.width()))
                .replace("{h}", String.valueOf(variant.height()));
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

    /**
     * 分类级图片约束。<b>只校验配了的项</b>，没配的一律放过。
     *
     * <p>读不出宽高时（格式不认识、文件损坏）直接跳过校验而不是报错 ——
     * 那种情况用「禁止上传此文件类型」拦更准确，尺寸校验不该越权替它报错。
     */
    private static void checkImageRule(FileEntity entity, FileImageProperties.Rule rule, String categoryName) {
        if (rule == null || entity.getImageWidth() == null || entity.getImageHeight() == null) {
            return;
        }
        int w = entity.getImageWidth();
        int h = entity.getImageHeight();
        String prefix = "「" + categoryName + "」分类要求图片";

        if (rule.getWidth() != null && w != rule.getWidth()) {
            throw new BusinessException(prefix + "宽度为 " + rule.getWidth() + "px，当前 " + w + "px");
        }
        if (rule.getHeight() != null && h != rule.getHeight()) {
            throw new BusinessException(prefix + "高度为 " + rule.getHeight() + "px，当前 " + h + "px");
        }
        if (rule.getMinWidth() != null && w < rule.getMinWidth()) {
            throw new BusinessException(prefix + "宽度不小于 " + rule.getMinWidth() + "px，当前 " + w + "px");
        }
        if (rule.getMinHeight() != null && h < rule.getMinHeight()) {
            throw new BusinessException(prefix + "高度不小于 " + rule.getMinHeight() + "px，当前 " + h + "px");
        }
        checkRatio(rule.getRatio(), w, h, prefix);
    }

    /**
     * 宽高比用交叉相乘比较，<b>不做浮点除法</b> —— 16:9 的 1920×1080 用浮点算会出现
     * 1.7777777 与 1.7777778 不相等这种事，而它明明是精确匹配的。
     */
    private static void checkRatio(String ratio, int w, int h, String prefix) {
        if (ratio == null || ratio.isBlank()) {
            return;
        }
        String[] parts = ratio.split(":");
        if (parts.length != 2) {
            log.warn("[File] 图片宽高比配置格式错误，已忽略：{}", ratio);
            return;
        }
        try {
            long rw = Long.parseLong(parts[0].trim());
            long rh = Long.parseLong(parts[1].trim());
            if ((long) w * rh != (long) h * rw) {
                throw new BusinessException(prefix + "宽高比为 " + ratio + "，当前 " + w + "×" + h);
            }
        } catch (NumberFormatException e) {
            log.warn("[File] 图片宽高比配置无法解析，已忽略：{}", ratio);
        }
    }

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
