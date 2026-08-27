package solvela.web.file;

import org.springframework.web.multipart.MultipartFile;
import solvela.base.module.file.domain.UploadSource;

import java.io.IOException;
import java.io.InputStream;

/**
 * 把 {@code MultipartFile} 适配成领域侧的 {@link UploadSource}。
 *
 * <p>这个类是 spring-web 与文件领域之间的<b>唯一接触点</b>。
 * {@code FileAssetService} 因此不再认识 servlet，定时任务、消息消费、C 端别的上传通道
 * 都能复用同一套大小校验、类型嗅探与 TEMP 状态流转。
 *
 * <p>{@code open()} 可以调多次 —— {@code MultipartFile.getInputStream()} 每次都返回
 * 一条能从头读的流（内存里的字节或磁盘临时文件），这正是 {@link UploadSource} 要求的。
 */
public record MultipartUploadSource(MultipartFile file) implements UploadSource {

    @Override
    public String originalName() {
        return file.getOriginalFilename();
    }

    @Override
    public long size() {
        return file.getSize();
    }

    @Override
    public String contentType() {
        return file.getContentType();
    }

    @Override
    public InputStream open() throws IOException {
        return file.getInputStream();
    }

    /** 便捷写法，端上一行完成适配。 */
    public static UploadSource of(MultipartFile file) {
        return new MultipartUploadSource(file);
    }
}
