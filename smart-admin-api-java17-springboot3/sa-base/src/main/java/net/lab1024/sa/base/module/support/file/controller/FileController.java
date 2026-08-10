package net.lab1024.sa.base.module.support.file.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.lab1024.sa.base.common.controller.SupportBaseController;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.common.util.SmartContentDispositionUtil;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.config.FileConfig;
import net.lab1024.sa.base.constant.SwaggerTagConst;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.module.support.file.domain.vo.FileUploadVO;
import net.lab1024.sa.base.module.support.file.service.FileAssetService;
import net.lab1024.sa.base.storage.StorageKey;
import net.lab1024.sa.base.storage.StoredObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件服务。
 *
 * <p><b>这是整个文件模块唯一认识 {@code ResponseDTO} 和 HttpServletResponse 的地方</b>。
 * 业务层抛 {@code BusinessException}，翻译成响应结构只在这一层发生一次。
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2019年10月11日 15:34:47
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@RestController
@Tag(name = SwaggerTagConst.Support.FILE)
public class FileController extends SupportBaseController {

    @Resource
    private FileAssetService fileAssetService;

    /**
     * @param folder       旧契约：分类 ID。迁移脚本把四个内置分类的 ID 对齐成了原 folderType 的数字，
     *                     所以老调用方不用改
     * @param categoryCode 新契约：分类编码。<b>新增的分类必须走这个参数</b> —— 它们的 ID 是各环境
     *                     数据库各自生成的（dev 上「活动素材」是 5，prod 上可能是 9），
     *                     前端硬编码数字就是 v3.53.0 里写明的那个"枚举变表最经典的翻车点"
     */
    @Operation(summary = "文件上传（categoryCode 与 folder 二选一） @author 胡克")
    @PostMapping("/file/upload")
    public ResponseDTO<FileUploadVO> upload(@RequestParam MultipartFile file,
                                            @RequestParam(required = false) Integer folder,
                                            @RequestParam(required = false) String categoryCode) {
        RequestUser requestUser = SmartRequestUtil.getRequestUser();
        FileEntity entity = (categoryCode == null || categoryCode.isBlank())
                ? fileAssetService.upload(file, Long.valueOf(requireFolder(folder)), requestUser)
                : fileAssetService.upload(file, categoryCode, requestUser);
        return ResponseDTO.ok(toUploadVO(entity));
    }

    private static Integer requireFolder(Integer folder) {
        if (folder == null) {
            throw new BusinessException("上传必须指定 categoryCode 或 folder");
        }
        return folder;
    }

    @Operation(summary = "获取文件URL：根据storageKey，支持逗号分隔 @author 胡克")
    @GetMapping("/file/getFileUrl")
    public ResponseDTO<String> getUrl(@RequestParam String storageKey) {
        return ResponseDTO.ok(fileAssetService.urlByStorageKeys(storageKey));
    }

    /**
     * 按 storageKey 下载。保留是因为业务表里存的就是 storageKey 字符串
     * （{@code t_employee.avatar}、逗号拼接的 {@code attachment}），
     * 把它们全部改存 fileId 是另一次数据迁移。
     */
    @Operation(summary = "下载文件流（根据storageKey） @author 胡克")
    @GetMapping("/file/downLoad")
    public void downLoad(@RequestParam String storageKey, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        writeFile(fileAssetService.requireByStorageKey(storageKey), false, request, response);
    }

    /**
     * 按 fileId 下载。
     *
     * @param inline 图片 / PDF 在线预览传 true。同一个对象既能 attachment 又能 inline ——
     *               这正是 disposition 不该在上传时烧进对象元数据的原因
     */
    @Operation(summary = "下载文件（流式，支持Range） @author 1024")
    @GetMapping("/file/download/{fileId}")
    public void download(@PathVariable Long fileId,
                         @RequestParam(required = false, defaultValue = "false") boolean inline,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        writeFile(fileAssetService.requireFile(fileId), inline, request, response);
    }

    /**
     * 公开文件的免登录读取口。<b>C 端用户是匿名的，永远带不上 token</b>，
     * 所以活动展示图必须有这么一条路 —— 这不是为了省事，是 C 端能不能显示图的前提。
     *
     * <p>与被删掉的 {@code /upload/**} 静态映射的区别只有一条，但是决定性的：
     * <b>它查 visibility</b>。私有文件走到这里一律 404，而那个静态映射把整个上传目录
     * 无差别地端出去（实测不带 token 也能拿到意见反馈的附件）。
     *
     * <p>路径里是 storageKey 而不是 fileId：这样 URL 可以直接换成 CDN 域名 + 同一个 key
     * （见 {@code FileAssetService#urlOf}），本地与云端两种部署下前端拿到的形态是一致的。
     * key 由 {@link StorageKey} 的构造器校验，{@code ..} / 反斜杠 / 空路径段一概拒绝 ——
     * 这条路径直接拼磁盘路径，是最典型的目录穿越入口。
     *
     * <p>404 而不是 403：不告诉外面"这个 key 存在但你没权限"。
     *
     * <p>缓存头敢写 immutable，是因为 storageKey 不可变、永不覆盖（红线 1）。
     * 换图 = 换 key = 换 URL，所以浏览器和 CDN 缓存一年也不会拿到过期内容。
     */
    @Operation(summary = "读取公开文件（免登录，私有文件404） @author 1024")
    @GetMapping(FileConfig.PUBLIC_FILE_PATH + "/**")
    public void publicAccess(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();
        int idx = uri.indexOf(FileConfig.PUBLIC_FILE_MAPPING + "/");
        String rawKey = idx < 0 ? "" : uri.substring(idx + FileConfig.PUBLIC_FILE_MAPPING.length() + 1);
        String storageKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);

        FileEntity file;
        try {
            file = fileAssetService.findPublicByStorageKey(new StorageKey(storageKey).value());
        } catch (IllegalArgumentException e) {
            // 非法 key（穿越尝试等）与不存在同样处理，不给探测者任何区分信号
            file = null;
        }
        if (file == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        writeFile(file, true, request, response);
    }

    // ------------------------------------------------------------------

    /**
     * 真流式：边读边写，堆占用与文件大小无关。
     *
     * <p>旧实现是 {@code copyToByteArray} 先把整个文件读成 byte[] 再吐出去，
     * 名字叫"流式下载"，实际 100MB 文件就是 100MB 堆。
     */
    private void writeFile(FileEntity file, boolean inline,
                           HttpServletRequest request, HttpServletResponse response) throws IOException {
        long total = file.getFileSize() == null ? 0 : file.getFileSize();
        DownloadRangeResolver.Resolved resolved = DownloadRangeResolver.resolve(
                request.getHeader(HttpHeaders.RANGE), total);
        if (!resolved.satisfiable()) {
            // 416 必须带 Content-Range: bytes */total，否则客户端不知道该重试什么区间
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + total);
            response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
            return;
        }

        try (StoredObject object = fileAssetService.open(file, resolved.range())) {
            String fileName = file.getOriginalName();
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            response.setHeader(HttpHeaders.CONTENT_TYPE, file.getContentType());
            response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(object.length()));
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, inline
                    ? SmartContentDispositionUtil.inline(fileName)
                    : SmartContentDispositionUtil.attachment(fileName));
            // 跨域场景下前端要读文件名，必须显式暴露这个头
            response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);

            if (resolved.partial()) {
                long start = resolved.range().start();
                response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                response.setHeader(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + (start + object.length() - 1) + "/" + object.totalLength());
            }
            object.stream().transferTo(response.getOutputStream());
        }
    }

    private FileUploadVO toUploadVO(FileEntity entity) {
        FileUploadVO vo = new FileUploadVO();
        vo.setFileId(entity.getFileId());
        vo.setStorageKey(entity.getStorageKey());
        // 这里给的是用户上传时的原名，不是生成的存储名。旧的两个实现在这个字段上塞了
        // 相反的东西（local 塞生成名、cloud 塞原名），是同一份前端代码在两种部署下表现不同的根因
        vo.setOriginalName(entity.getOriginalName());
        vo.setExtension(entity.getExtension());
        vo.setFileSize(entity.getFileSize());
        vo.setFileUrl(fileAssetService.url(entity, null));
        return vo;
    }
}
