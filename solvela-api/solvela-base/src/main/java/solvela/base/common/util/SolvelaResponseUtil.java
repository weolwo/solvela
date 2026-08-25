package solvela.base.common.util;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import solvela.base.common.domain.ResponseDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * 返回工具栏
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/11/25 18:51:32
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>，Since 2012
 */

@Slf4j
public class SolvelaResponseUtil {

    public static void write(HttpServletResponse response, ResponseDTO<?> responseDTO) {
        // 重置response
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(UTF_8.name());

        try {
            response.getWriter().write(JsonUtils.toJson(responseDTO));
            response.flushBuffer();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    public static void setDownloadFileHeader(HttpServletResponse response, String fileName) {
        setDownloadFileHeader(response, fileName, null);
    }

    public static void setDownloadFileHeader(HttpServletResponse response, String fileName, Long fileSize) {
        response.setCharacterEncoding(UTF_8.name());
        if (fileSize != null) {
            response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));
        }

        if (SolvelaStringUtil.isNotEmpty(fileName)) {
            MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
            // charset 只对文本类型有意义。给 image/png 挂 ;charset=utf-8 是无意义的噪音，
            // 个别下载器还会因此把二进制当文本处理
            response.setHeader(HttpHeaders.CONTENT_TYPE,
                    "text".equals(mediaType.getType()) ? mediaType + ";charset=utf-8" : mediaType.toString());
            // RFC 6266 双写法。旧实现用 URLEncoder.encode（那是 form 编码不是 percent-encoding），
            // 空格变 + 要打补丁，而分号逗号不转义可以直接把这个 header 撕成两截
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, SolvelaContentDispositionUtil.attachment(fileName));
            response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
        }
    }


}
