package solvela.base.module.file;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 按<b>文件内容</b>探测 MIME 类型（Tika），而不是信任扩展名或请求里的 Content-Type。
 *
 * <h3>为什么不放在等保那一包里</h3>
 * 这个方法原先是 {@code SecurityFileService} 的静态成员，于是 solvela-base 的文件模块
 * 为了识别一下类型，就得依赖整个三级等保服务。但「这个字节流是什么格式」是<b>事实判断</b>，
 * 与「允不允许上传」这个<b>策略判断</b>无关 —— 后者才是等保该管的，且已随 securityprotect
 * 一起移交 solvela-admin。
 *
 * <p>⚠️ 探测结果只应该用来<b>做决定</b>（拦截、分类），不要直接回写给前端当 Content-Type ——
 * Tika 对畸形文件会给出 {@code application/octet-stream} 以外的猜测值，
 * 拿它当响应头等于让上传者部分控制浏览器的解析方式。
 */
@Slf4j
public final class FileMimeTypeUtil {

    private FileMimeTypeUtil() {
    }

    /**
     * 探测文件真实 MIME 类型；探测失败返回 {@code application/octet-stream}。
     *
     * <p>失败时刻意<b>不抛异常</b>：调用方多为上传链路，
     * 为一次类型探测失败让整个上传 500，比按「未知二进制」处理要糟。
     */
    public static String detect(MultipartFile file) {
        InputStream inputStream = null;
        try {
            inputStream = file.getInputStream();
            TikaConfig tika = new TikaConfig();
            Metadata metadata = new Metadata();
            // 带上原始文件名：Tika 在字节特征不足以区分时会参考它（如 csv 与 txt）
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
            TikaInputStream stream = TikaInputStream.get(inputStream);
            MediaType mimetype = tika.getDetector().detect(stream, metadata);
            return mimetype.toString();
        } catch (IOException | TikaException e) {
            log.error(e.getMessage(), e);
            return MimeTypes.OCTET_STREAM;
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    /**
     * MIME 类型是否匹配白名单项，支持 {@code audio/*} 这种通配写法
     */
    public static boolean matches(String fileType, String mimetype) {
        if (mimetype.endsWith("/*")) {
            String prefix = mimetype.substring(0, mimetype.length() - 1);
            return fileType.startsWith(prefix);
        }
        return fileType.equalsIgnoreCase(mimetype);
    }
}
