package net.lab1024.sa.base.module.support.file.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.lab1024.sa.base.common.constant.RequestHeaderConst;
import net.lab1024.sa.base.common.controller.SupportBaseController;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartContentDispositionUtil;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.common.util.SmartResponseUtil;
import net.lab1024.sa.base.constant.SwaggerTagConst;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.module.support.file.domain.vo.FileDownloadVO;
import net.lab1024.sa.base.module.support.file.domain.vo.FileUploadVO;
import net.lab1024.sa.base.module.support.file.service.FileAssetService;
import net.lab1024.sa.base.module.support.file.service.FileService;
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
 * 文件服务
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
    private FileService fileService;

    @Resource
    private FileAssetService fileAssetService;


    @Operation(summary = "文件上传 @author 胡克")
    @PostMapping("/file/upload")
    public ResponseDTO<FileUploadVO> upload(@RequestParam MultipartFile file, @RequestParam Integer folder) {
        RequestUser requestUser = SmartRequestUtil.getRequestUser();
        return fileService.fileUpload(file, folder, requestUser);
    }

    @Operation(summary = "获取文件URL：根据fileKey @author 胡克")
    @GetMapping("/file/getFileUrl")
    public ResponseDTO<String> getUrl(@RequestParam String fileKey) {
        return fileService.getFileUrl(fileKey);
    }

    @Operation(summary = "下载文件流（根据fileKey） @author 胡克")
    @GetMapping("/file/downLoad")
    public void downLoad(@RequestParam String fileKey, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String userAgent = request.getHeader(RequestHeaderConst.USER_AGENT);
        ResponseDTO<FileDownloadVO> downloadFileResult = fileService.getDownloadFile(fileKey, userAgent);
        if (!downloadFileResult.getOk()) {
            SmartResponseUtil.write(response, downloadFileResult);
            return;
        }
        // 下载文件信息
        FileDownloadVO fileDownloadVO = downloadFileResult.getData();
        // 设置下载消息头
        SmartResponseUtil.setDownloadFileHeader(response, fileDownloadVO.getMetadata().getFileName(), fileDownloadVO.getMetadata().getFileSize());
        // 下载
        response.getOutputStream().write(fileDownloadVO.getData());
    }

    /**
     * 档③ 新增的流式下载。与上面的 {@code /file/downLoad} 并存，档⑤ 迁移前端后删旧的。
     *
     * <p>与旧接口的三处区别：
     * <ul>
     *   <li><b>真流式</b>：边读边写，堆占用与文件大小无关；旧接口先把整个文件读成 byte[]</li>
     *   <li><b>支持 Range</b>：大图和 PDF 的分段加载、断点续传依赖它</li>
     *   <li><b>Content-Disposition 走 RFC 6266 双写法</b>，中文名在各浏览器都对，
     *       且文件名里的分号撕不开 header</li>
     * </ul>
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
        FileEntity file = fileAssetService.requireFile(fileId);
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
            // transferTo 走 8KB 缓冲逐段搬运，堆占用与文件大小无关
            object.stream().transferTo(response.getOutputStream());
        }
    }
}
