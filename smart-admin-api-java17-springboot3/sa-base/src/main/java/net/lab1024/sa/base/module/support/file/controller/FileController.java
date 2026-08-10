package net.lab1024.sa.base.module.support.file.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.lab1024.sa.base.common.controller.SupportBaseController;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartContentDispositionUtil;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.constant.SwaggerTagConst;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.module.support.file.domain.vo.FileUploadVO;
import net.lab1024.sa.base.module.support.file.service.FileAssetService;
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

    @Operation(summary = "文件上传 @author 胡克")
    @PostMapping("/file/upload")
    public ResponseDTO<FileUploadVO> upload(@RequestParam MultipartFile file, @RequestParam Integer folder) {
        RequestUser requestUser = SmartRequestUtil.getRequestUser();
        // folder 沿用原来的 folderType 取值：迁移脚本把内置分类的 ID 对齐成了同一个数字，
        // 所以这个接口的契约不用动
        FileEntity entity = fileAssetService.upload(file, Long.valueOf(folder), requestUser);
        return ResponseDTO.ok(toUploadVO(entity));
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
